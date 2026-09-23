package org.chimeramc.launcher.core.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCPEDL's Minecraft PE download pages.
 *
 * The flow is three plain HTTP steps, none of which needs JavaScript:
 *
 * <ol>
 *   <li>{@code GET /downloading/} lists the newest releases, each linking to a page such as
 *       {@code /minecraft-pe-26-60-24-apk/}.</li>
 *   <li>That page holds one {@code <form method="post" action="/show_file.php">} per build,
 *       each carrying hidden {@code post_title}/{@code file_id}/{@code post_url} fields.</li>
 *   <li>Posting the form returns <em>HTTP 200 with an HTML body</em>, not a redirect, and the
 *       package link sits in that body as {@code window.location.href='...'} on the button.</li>
 * </ol>
 *
 * The third step is why this source needs both {@link #parseVersionPage} and
 * {@link #parseResolvedUrl}: the URL is not discoverable until the form has been submitted.
 */
public final class McpedlSource implements BedrockSource {

    public static final String HOST = "mcpedl.org";
    public static final String LISTING_URL = "https://" + HOST + "/downloading/";

    /** Version pages live at the site root and always end in {@code -apk/}. */
    private static final Pattern VERSION_PATH = Pattern.compile("^/(minecraft-pe-[a-z0-9\\-]+-apk)/?$");
    /** The card label the index prints above each link: {@code MCPE 26.60.24}. */
    private static final Pattern MCPE_LABEL = Pattern.compile("\\bMCPE\\s*(\\d[\\d.]*)", Pattern.CASE_INSENSITIVE);
    /**
     * How far above a link to look for its card's label.
     *
     * The label sits a short distance above the link, but a window wide enough to always
     * reach it can also contain the previous card; the label is therefore taken as the
     * <em>last</em> match in the window, which is the nearest one and so the card's own.
     */
    private static final int CARD_CONTEXT_CHARS = 2000;
    private static final Pattern FORM = Pattern.compile(
            "<form\\b[^>]*action=[\"']([^\"']*show_file\\.php)[\"'][^>]*>(.*?)</form>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HIDDEN_INPUT = Pattern.compile(
            "<input\\b[^>]*type=[\"']hidden[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_NAME = Pattern.compile(
            "name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern INPUT_VALUE = Pattern.compile(
            "value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUTTON_LABEL = Pattern.compile(
            "<button\\b[^>]*>(.*?)</button>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** The resolved link is assigned to {@code window.location.href} inside an onclick. */
    private static final Pattern JS_REDIRECT = Pattern.compile(
            "(?:window\\.)?location(?:\\.href)?\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    /** The variant name sits in the table cell beside the form. */
    private static final Pattern TABLE_CELL = Pattern.compile(
            "<td\\b[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Override
    public String id() {
        return "mcpedl";
    }

    @Override
    public String displayHost() {
        return HOST;
    }

    @Override
    public String listingUrl() {
        return LISTING_URL;
    }

    @Override
    public String refererUrl() {
        return LISTING_URL;
    }

    /**
     * Extracts the version pages linked from the download index.
     *
     * Only links whose path is a version page at the site root are accepted. The index also
     * links its own categories ({@code /downloading/minecraft-pe-26/}) and a long tail of
     * articles; requiring the path to start at {@code /} right after the host keeps those
     * out, which a bare "contains minecraft-pe" test would not.
     *
     * The label the user recognises is the card heading above the link ({@code MCPE 26.60.24
     * (16.09.2026: Latest Beta)}), not the anchor text — that is only "Read More...". So the
     * card's own markup is searched for the version and the release marker, and the URL slug
     * is the fallback when a card is laid out differently.
     */
    @Override
    public List<Version> parseListing(String html) {
        if (html == null || html.isEmpty()) return Collections.emptyList();

        Map<String, Version> byUrl = new LinkedHashMap<>();
        Matcher anchor = ANCHOR.matcher(html);
        while (anchor.find()) {
            String href = anchor.group(1).trim();
            String path = pathOf(href);
            if (path == null) continue;
            Matcher version = VERSION_PATH.matcher(path);
            if (!version.matches()) continue;

            String url = "https://" + HOST + "/" + version.group(1) + "/";
            if (byUrl.containsKey(url)) continue;

            // The slug is authoritative: "minecraft-pe-26-60-24-apk" is the version, and it
            // cannot be confused by a neighbouring card the way page text can.
            String slugCode = versionFromSlug(version.group(1));

            String card = cardContext(html, anchor.start());
            String label = lastMatch(MCPE_LABEL, card);

            // The label is only trusted when it agrees with the slug. The window needed to
            // reach a label can also contain the previous card's, and adopting that would
            // silently rename this row after a different release.
            boolean labelAgrees = label != null && label.equals(slugCode);
            String versionCode = slugCode != null ? slugCode : label;
            if (versionCode == null) versionCode = BedrockSource.extractVersionCode(version.group(1));
            String title = versionCode == null
                    ? BedrockSource.stripTags(anchor.group(2))
                    : "Minecraft " + versionCode;

            // The marker sits beside the label ("(16.09.2026: Latest Beta)"), so it is only
            // read from that label onward, and only when the label is this card's own.
            boolean release = false;
            if (labelAgrees) {
                int from = card.toLowerCase(Locale.US).lastIndexOf("mcpe");
                release = card.toLowerCase(Locale.US)
                        .substring(Math.max(0, from)).contains("latest release");
            }

            byUrl.put(url, new Version(title, url, versionCode, release));
        }
        return new ArrayList<>(byUrl.values());
    }

    /**
     * The markup of the card a link sits in.
     *
     * The index prints the label a short distance above the link inside the same card, so a
     * bounded slice ending at the link is enough and cannot run into the previous card's
     * text the way a whole-document search would.
     */
    private static String cardContext(String html, int anchorStart) {
        int start = Math.max(0, anchorStart - CARD_CONTEXT_CHARS);
        return html.substring(start, anchorStart);
    }

    /**
     * The version a page slug encodes.
     *
     * {@code minecraft-pe-26-60-24-apk} is the release 26.60.24: the leading
     * {@code minecraft-pe-} and trailing {@code -apk} are stripped and the remaining dashes
     * become dots. A slug with no numeric run yields null so the caller can fall back.
     */
    static String versionFromSlug(String slug) {
        if (slug == null) return null;
        String value = slug;
        if (value.startsWith("minecraft-pe-")) value = value.substring("minecraft-pe-".length());
        if (value.endsWith("-apk")) value = value.substring(0, value.length() - "-apk".length());
        return BedrockSource.extractVersionCode(value.replace('-', '.'));
    }

    /** The last match of a pattern in a window, i.e. the one nearest the link. */
    private static String lastMatch(Pattern pattern, String text) {
        if (text == null) return null;
        Matcher matcher = pattern.matcher(text);
        String last = null;
        while (matcher.find()) {
            last = matcher.group(1);
        }
        return last;
    }

    /**
     * Extracts every download form on a version page.
     *
     * The variant name is the table cell before the form ("Xbox+servers, no music") and the
     * button text carries the size ("Download(380 Mb)"), so both are kept: the label drives
     * the 32-bit check and the size gives the user a sense of what they are about to fetch.
     * The package file name is not on this page at all — the site only reveals it after the
     * form is posted, which is why {@link #parseResolvedUrl} exists.
     */
    @Override
    public List<DownloadForm> parseVersionPage(String html) {
        if (html == null || html.isEmpty()) return Collections.emptyList();

        List<DownloadForm> forms = new ArrayList<>();
        Matcher matcher = FORM.matcher(html);
        while (matcher.find()) {
            String action = BedrockSource.absolutize(matcher.group(1).trim(), HOST);
            if (action == null) continue;

            List<Field> fields = hiddenFields(matcher.group(2));
            if (fields.isEmpty()) continue;

            String label = variantLabel(html, matcher.start());
            String button = buttonText(matcher.group(2));
            forms.add(new DownloadForm(action, label, button, fields));
        }
        return forms;
    }

    /**
     * Finds the package link in the response to a form post.
     *
     * The response is a "Download File" page whose button carries the real URL in an onclick
     * assignment, so the link is read from that script rather than from an anchor. A response
     * that instead redirects is handled by the caller, which reads the {@code Location} header.
     */
    @Override
    public String parseResolvedUrl(String html) {
        if (html == null || html.isEmpty()) return null;

        Matcher redirect = JS_REDIRECT.matcher(html);
        while (redirect.find()) {
            String url = redirect.group(1).trim();
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
        }

        // Some responses link the file directly instead of scripting the redirect.
        for (Anchor anchor : BedrockSource.anchors(html)) {
            String url = BedrockSource.absolutize(anchor.href, HOST);
            if (url != null && BedrockSource.isPackageFileName(BedrockSource.fileNameFromUrl(url))) {
                return url;
            }
        }
        return null;
    }

    /** The variant label is the table cell immediately before the form. */
    private static String variantLabel(String html, int formStart) {
        String before = html.substring(0, formStart);
        Matcher cells = TABLE_CELL.matcher(before);
        String last = null;
        while (cells.find()) {
            last = cells.group(1);
        }
        String label = BedrockSource.stripTags(last);
        // The size in the button is useful context but is not part of the variant name.
        return label.length() > 80 ? label.substring(0, 80) : label;
    }

    private static String buttonText(String formBody) {
        Matcher button = BUTTON_LABEL.matcher(formBody);
        return button.find() ? BedrockSource.stripTags(button.group(1)) : "";
    }

    private static List<Field> hiddenFields(String formBody) {
        List<Field> fields = new ArrayList<>();
        Matcher input = HIDDEN_INPUT.matcher(formBody);
        while (input.find()) {
            String tag = input.group(0);
            String name = matchGroup(INPUT_NAME, tag);
            if (name == null || name.isEmpty()) continue;
            String value = matchGroup(INPUT_VALUE, tag);
            fields.add(new Field(name, value == null ? "" : value));
        }
        return fields;
    }

    /** The path of an href on this host, or null when the link points elsewhere. */
    private static String pathOf(String href) {
        String value = href.trim();
        if (value.startsWith("/")) {
            int query = value.indexOf('?');
            return query >= 0 ? value.substring(0, query) : value;
        }
        String prefix = "https://" + HOST;
        String insecure = "http://" + HOST;
        if (value.startsWith(prefix)) value = value.substring(prefix.length());
        else if (value.startsWith(insecure)) value = value.substring(insecure.length());
        else return null;

        if (!value.startsWith("/")) return null;
        int query = value.indexOf('?');
        return query >= 0 ? value.substring(0, query) : value;
    }

    private static String matchGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
