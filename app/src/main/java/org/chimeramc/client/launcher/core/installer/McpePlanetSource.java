package org.chimeramc.client.core.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MCPE-Planet's Minecraft Bedrock download pages.
 *
 * The flow is two plain HTTP steps, neither of which needs JavaScript:
 *
 * <ol>
 *   <li>{@code GET /downloads/} lists version cards, each an {@code <article>} carrying ribbon
 *       spans (update name, version, {@code Release}/{@code Beta}) and linking to a page such as
 *       {@code /downloads/minecraft-pe-1-26/60-24/}.</li>
 *   <li>That page holds one {@code <form method="post">} per build. Unlike MCPEDL the form has
 *       an <em>empty</em> action, so it posts back to the page itself; the canonical link in the
 *       head is what supplies that URL. The submission answers HTTP 200 with the package URL in
 *       a {@code var furl = '...'} script, which {@link #parseResolvedUrl} reads.</li>
 * </ol>
 *
 * <p>The listing is read per {@code <article>} rather than per anchor. Every listing page repeats
 * the newest versions in a sidebar {@code g-tagmenu} menu outside the cards, so a document-wide
 * anchor scan would re-offer those on every page of the archive; scoping to the card keeps the
 * sidebar out without having to name a class that is a theme detail.
 */
public final class McpePlanetSource implements BedrockSource {

    public static final String HOST = "mcpe-planet.com";
    public static final String LISTING_URL = "https://" + HOST + "/downloads/";

    /**
     * Version pages sit under a series directory and end in a build slug:
     * {@code /downloads/minecraft-pe-1-26/60-24/}.
     *
     * Two path segments are required, which is what keeps the series index
     * ({@code /downloads/minecraft-pe-1-26/}) and the top-level index ({@code /downloads/})
     * from being offered as versions.
     */
    private static final Pattern VERSION_PATH = Pattern.compile(
            "^/downloads/(minecraft-pe-[a-z0-9\\-]+)/([a-z0-9\\-]+)/?$");
    /** The version ribbon on a card, e.g. {@code <span class="ribbon">1.26.60.28</span>}. */
    private static final Pattern RIBBON = Pattern.compile(
            "<span\\b[^>]*class=[\"'][^\"']*\\bribbon\\b[^\"']*[\"'][^>]*>(.*?)</span>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ARTICLE = Pattern.compile(
            "<article\\b.*?</article>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FORM = Pattern.compile(
            "<form\\b([^>]*)>(.*?)</form>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INPUT_TAG = Pattern.compile(
            "<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUTTON_TAG = Pattern.compile(
            "<button\\b([^>]*)>(.*?)</button>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR_NAME = Pattern.compile(
            "name=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_VALUE = Pattern.compile(
            "value=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_TYPE = Pattern.compile(
            "type=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR_ACTION = Pattern.compile(
            "action=[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);
    /** The canonical page address, which is also the form's post target. */
    private static final Pattern CANONICAL = Pattern.compile(
            "<link\\b[^>]*rel=[\"']canonical[\"'][^>]*href=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OG_URL = Pattern.compile(
            "<meta\\b[^>]*property=[\"']og:url[\"'][^>]*content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    /**
     * The package URL is assigned to a {@code furl} variable in the post response
     * ({@code var furl = 'https://.../minecraft-26-60-24.apk';}), beside a {@code bname}
     * holding the file name.
     */
    private static final Pattern JS_FURL = Pattern.compile(
            "\\bfurl\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_BNAME = Pattern.compile(
            "\\bbname\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_REDIRECT = Pattern.compile(
            "(?:window\\.)?location(?:\\.href)?\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    /** Where this site keeps the packages its version pages point at. */
    private static final String UPLOAD_DIRECTORY = "/wp-content/uploads/version/";

    @Override
    public String id() {
        return "mcpeplanet";
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
     * Extracts the version cards on a listing page.
     *
     * The card's own ribbon row carries the version the user recognises
     * ({@code 1.26.60.28}) and the release marker ({@code Release} vs {@code Beta}). The URL
     * slug is only a truncated form of the build ({@code 60-24}) and cannot be the whole
     * version, so the ribbon is authoritative and the slug is the fallback.
     */
    @Override
    public List<Version> parseListing(String html) {
        if (html == null || html.isEmpty()) return Collections.emptyList();

        Map<String, Version> byUrl = new LinkedHashMap<>();
        for (String card : cards(html)) {
            Matcher anchor = BedrockSource.ANCHOR.matcher(card);
            while (anchor.find()) {
                String href = anchor.group(1).trim();
                String path = pathOf(href);
                if (path == null) continue;
                Matcher version = VERSION_PATH.matcher(path);
                if (!version.matches()) continue;

                String url = "https://" + HOST + "/downloads/"
                        + version.group(1) + "/" + version.group(2) + "/";
                if (byUrl.containsKey(url)) continue;

                String slugCode = versionFromSlug(version.group(2));
                String label = versionRibbon(card);
                String versionCode = label != null ? label : slugCode;
                if (versionCode == null) versionCode = BedrockSource.extractVersionCode(path);
                if (versionCode == null) {
                    versionCode = BedrockSource.stripTags(anchor.group(2));
                }

                byUrl.put(url, new Version("Minecraft " + versionCode, url, versionCode,
                        isRelease(card)));
            }
        }
        return new ArrayList<>(byUrl.values());
    }

    /**
     * Extracts every download form on a version page.
     *
     * The form posts back to the page, so an empty {@code action} is resolved against the
     * page's own canonical address. The build's identity is in the submit button
     * ({@code name="app" value="40149"}) and its label names the variant and the ABI
     * ("Download minecraft-26-51-armeabi-v7a .apk xbox + servers (352.79 Mb)"), which is what
     * drives {@link BedrockSource#selectDownload}.
     */
    @Override
    public List<DownloadForm> parseVersionPage(String html) {
        if (html == null || html.isEmpty()) return Collections.emptyList();

        String pageUrl = canonicalUrl(html);
        List<DownloadForm> forms = new ArrayList<>();
        Matcher matcher = FORM.matcher(html);
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String body = matcher.group(2);

            List<Field> fields = downloadFields(body);
            if (fields.isEmpty()) continue;

            String action = BedrockSource.absolutize(actionOf(attributes), HOST);
            if (action == null) action = pageUrl;
            if (action == null) continue;

            String label = buttonLabel(body);
            forms.add(new DownloadForm(action, label, label, fields));
        }
        return forms;
    }

    /**
     * Finds the package link in the response to a form post.
     *
     * The response is HTTP 200 whose script assigns the real URL to {@code furl}, so that is
     * read first. A response that instead redirects is handled by the caller, which reads the
     * {@code Location} header, and a direct anchor is accepted as a last resort.
     */
    @Override
    public String parseResolvedUrl(String html) {
        if (html == null || html.isEmpty()) return null;

        Matcher furl = JS_FURL.matcher(html);
        if (furl.find()) {
            String url = furl.group(1).trim();
            if (isAbsolute(url)) return url;
        }

        // A response that only exposes the file name still identifies the package.
        Matcher bname = JS_BNAME.matcher(html);
        if (bname.find()) {
            String name = bname.group(1).trim();
            if (BedrockSource.isPackageFileName(name)) {
                return "https://" + HOST + UPLOAD_DIRECTORY + name;
            }
        }

        Matcher redirect = JS_REDIRECT.matcher(html);
        while (redirect.find()) {
            String url = redirect.group(1).trim();
            if (isAbsolute(url)) return url;
        }

        for (Anchor anchor : BedrockSource.anchors(html)) {
            String url = BedrockSource.absolutize(anchor.href, HOST);
            if (url != null && BedrockSource.isPackageFileName(BedrockSource.fileNameFromUrl(url))) {
                return url;
            }
        }
        return null;
    }

    /**
     * The next page of the archive, from the listing's pagination link.
     *
     * The index paginates dozens of pages deep and writes the link site-relative, so it is
     * resolved against {@link #HOST}.
     */
    @Override
    public String nextListingUrl(String html) {
        String href = BedrockSource.pageLink(html, true);
        return BedrockSource.absolutize(href, HOST);
    }

    /** Splits a listing page into its cards, so the repeated sidebar is never scanned. */
    private static List<String> cards(String html) {
        List<String> cards = new ArrayList<>();
        Matcher matcher = ARTICLE.matcher(html);
        while (matcher.find()) cards.add(matcher.group());
        // A page laid out without <article> still parses as one card, so the list is never empty.
        if (cards.isEmpty()) cards.add(html);
        return cards;
    }

    /**
     * The version a card's ribbon row advertises, or null when no ribbon holds a version.
     *
     * The first ribbon is the update name ("Wilderness Bound") and the last is the channel
     * ("Beta"), so the version is whichever ribbon matches a dotted number.
     */
    private static String versionRibbon(String card) {
        Matcher matcher = RIBBON.matcher(card);
        while (matcher.find()) {
            String text = BedrockSource.stripTags(matcher.group(1));
            if (BedrockSource.VERSION_CODE.matcher(text).matches()) return text;
        }
        return null;
    }

    /** True when a card carries the "Release" channel ribbon rather than "Beta". */
    private static boolean isRelease(String card) {
        Matcher matcher = RIBBON.matcher(card);
        while (matcher.find()) {
            String text = BedrockSource.stripTags(matcher.group(1));
            if ("release".equalsIgnoreCase(text)) return true;
        }
        return false;
    }

    /**
     * The version a build slug encodes.
     *
     * The slug is only the build part ({@code 60-24} for 1.26.60.24), so this is a fallback
     * used when a card carries no version ribbon.
     */
    static String versionFromSlug(String slug) {
        if (slug == null || slug.isEmpty()) return null;
        String dotted = slug.replace('-', '.');
        return BedrockSource.VERSION_CODE.matcher(dotted).matches() ? dotted : null;
    }

    /** The hidden inputs and the build's submit button, in document order. */
    private static List<Field> downloadFields(String formBody) {
        List<Field> fields = new ArrayList<>();
        Matcher input = INPUT_TAG.matcher(formBody);
        while (input.find()) {
            String tag = input.group(0);
            String name = match(ATTR_NAME, tag);
            if (name == null || name.isEmpty()) continue;
            String type = match(ATTR_TYPE, tag);
            if (type != null && !"hidden".equalsIgnoreCase(type)) continue;
            String value = match(ATTR_VALUE, tag);
            fields.add(new Field(name, value == null ? "" : value));
        }
        Matcher button = BUTTON_TAG.matcher(formBody);
        while (button.find()) {
            String attributes = button.group(1);
            String type = match(ATTR_TYPE, attributes);
            if (type != null && !"submit".equalsIgnoreCase(type)) continue;
            String name = match(ATTR_NAME, attributes);
            if (name == null || name.isEmpty()) continue;
            String value = match(ATTR_VALUE, attributes);
            fields.add(new Field(name, value == null ? "" : value));
        }
        return fields;
    }

    /** The text of the first button in a form, used as its variant label. */
    private static String buttonLabel(String formBody) {
        Matcher button = BUTTON_TAG.matcher(formBody);
        if (!button.find()) return "";
        String label = BedrockSource.stripTags(button.group(2));
        return label.length() > 120 ? label.substring(0, 120) : label;
    }

    /**
     * The page's own address, from its canonical link.
     *
     * This is what an empty form action resolves to. {@code og:url} is the fallback for a page
     * that omits the canonical link.
     */
    private static String canonicalUrl(String html) {
        String canonical = match(CANONICAL, html);
        if (canonical != null && !canonical.isEmpty()) return canonical.trim();
        String og = match(OG_URL, html);
        return og == null || og.isEmpty() ? null : og.trim();
    }

    private static String actionOf(String formAttributes) {
        Matcher matcher = ATTR_ACTION.matcher(formAttributes);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static boolean isAbsolute(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
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

    private static String match(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
