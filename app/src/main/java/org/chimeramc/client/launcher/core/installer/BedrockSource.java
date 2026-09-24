package org.chimeramc.client.core.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A website that publishes Minecraft Bedrock packages the launcher can install.
 *
 * A source knows three things: where its version list lives, how to turn a version page into
 * the fields of a download form, and how to recognise the package link the form's response
 * contains. Everything else — fetching, staging, importing, cleanup — is shared, so adding a
 * mirror means implementing this interface and registering it, not touching the UI.
 *
 * Implementations must stay free of Android and network APIs. The pages are third-party HTML
 * that will change shape, so the extraction rules live in one place that unit tests can pin
 * against captured markup instead of only being exercised through a live download.
 */
public interface BedrockSource {

    /** Instance names are limited to this length elsewhere in the launcher. */
    int MAX_NAME_LENGTH = 40;

    Pattern VERSION_CODE = Pattern.compile("(\\d+(?:\\.\\d+)+)");
    /** These sites spell versions with dashes: {@code minecraft-26-50-22-arm64-v8a}. */
    Pattern DASHED_VERSION_CODE = Pattern.compile("(\\d+(?:-\\d+){2,})");
    Pattern WHITESPACE = Pattern.compile("\\s+");
    Pattern ANCHOR = Pattern.compile(
            "<a\\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** The opening tag of an anchor, so its attributes can be read without its body. */
    Pattern ANCHOR_TAG = Pattern.compile("<a\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    Pattern ANCHOR_HREF = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    Pattern ANCHOR_REL_NEXT = Pattern.compile("rel=[\"']next[\"']", Pattern.CASE_INSENSITIVE);
    Pattern ANCHOR_REL_PREV = Pattern.compile("rel=[\"']prev(?:ious)?[\"']", Pattern.CASE_INSENSITIVE);
    Pattern ANCHOR_CLASS_NEXT = Pattern.compile(
            "class=[\"'][^\"']*\\bnext\\b[^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    Pattern ANCHOR_CLASS_PREV = Pattern.compile(
            "class=[\"'][^\"']*\\bprev(?:ious)?\\b[^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    Pattern FILENAME_STAR = Pattern.compile(
            "filename\\*\\s*=\\s*(?:UTF-8''|utf-8'')?([^;\\r\\n]+)", Pattern.CASE_INSENSITIVE);
    Pattern FILENAME_PLAIN = Pattern.compile(
            "filename\\s*=\\s*\"([^\"]*)\"|filename\\s*=\\s*([^;\\r\\n]+)", Pattern.CASE_INSENSITIVE);

    /** Stable identifier used in preferences and logs; never shown to the user. */
    String id();

    /** Host shown in the "downloaded from X" warning, e.g. {@code mcpedl.org}. */
    String displayHost();

    /** The page that lists the versions this source publishes. */
    String listingUrl();

    /** Page sent as the Referer when fetching a package, so the CDN sees a normal request. */
    String refererUrl();

    /** Extracts the versions advertised on [html], in the order the page publishes them. */
    List<Version> parseListing(String html);

    /**
     * Extracts the download forms on a version page.
     *
     * A page can offer several builds (universal, +music, 32-bit), so all are returned in
     * document order and {@link #selectDownload} decides which one this device should take.
     */
    List<DownloadForm> parseVersionPage(String html);

    /**
     * Finds the package URL in the response to a form submission.
     *
     * Returns null when the response holds no recognisable link, which the caller reports as
     * a failed resolve rather than a failed download.
     */
    String parseResolvedUrl(String html);

    /**
     * The URL of the listing page after the one [html] represents, or null on the last page.
     *
     * Sources that paginate override this so the caller can walk the whole archive. Returning
     * null means "no further pages", which is also the default for a source that publishes its
     * whole list on one page.
     */
    default String nextListingUrl(String html) {
        return null;
    }

    /**
     * The href of a pagination link carrying {@code rel="next"} (or {@code rel="prev"} when
     * [next] is false).
     *
     * Pagination is read from the link's {@code rel} attribute rather than its label, because
     * the label is translated and the class name is a theme detail; {@code rel} is what tells
     * a crawler — and this parser — which direction the link points. A missing link on the
     * last page is the normal case and yields null.
     */
    static String pageLink(String html, boolean next) {
        if (html == null || html.isEmpty()) return null;
        Pattern rel = next ? ANCHOR_REL_NEXT : ANCHOR_REL_PREV;
        Matcher tag = ANCHOR_TAG.matcher(html);
        while (tag.find()) {
            String attributes = tag.group(1);
            if (!rel.matcher(attributes).find()) continue;
            Matcher href = ANCHOR_HREF.matcher(attributes);
            if (href.find()) return href.group(1).trim();
        }
        // Some themes put the direction in the class ("... next") without a rel attribute.
        Pattern cls = next ? ANCHOR_CLASS_NEXT : ANCHOR_CLASS_PREV;
        tag.reset();
        while (tag.find()) {
            String attributes = tag.group(1);
            if (!cls.matcher(attributes).find()) continue;
            Matcher href = ANCHOR_HREF.matcher(attributes);
            if (href.find()) return href.group(1).trim();
        }
        return null;
    }

    /** One Bedrock release advertised on a listing page. */
    final class Version {
        public final String title;
        public final String pageUrl;
        public final String versionCode;
        public final boolean release;

        public Version(String title, String pageUrl, String versionCode, boolean release) {
            this.title = title;
            this.pageUrl = pageUrl;
            this.versionCode = versionCode;
            this.release = release;
        }

        /** Label shown when a title is missing, so a row is never blank. */
        public String displayLabel() {
            if (title != null && !title.isEmpty()) return title;
            if (versionCode != null && !versionCode.isEmpty()) return "Minecraft " + versionCode;
            return pageUrl;
        }
    }

    /** The fields of one download button, ready to be submitted as a form post. */
    final class DownloadForm {
        public final String actionUrl;
        public final String label;
        public final String sizeLabel;
        public final List<Field> fields;

        public DownloadForm(String actionUrl, String label, String sizeLabel, List<Field> fields) {
            this.actionUrl = actionUrl;
            this.label = label;
            this.sizeLabel = sizeLabel;
            this.fields = fields == null ? Collections.emptyList() : fields;
        }

        /** True when this build targets 32-bit devices the launcher cannot run. */
        public boolean is32BitOnly() {
            return BedrockSource.is32BitOnly(label) || BedrockSource.is32BitOnly(sizeLabel);
        }
    }

    /** A hidden input of a download form. */
    final class Field {
        public final String name;
        public final String value;

        public Field(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    /** A package link resolved from a form submission. */
    final class ResolvedDownload {
        public final String url;
        public final String fileName;

        public ResolvedDownload(String url, String fileName) {
            this.url = url;
            this.fileName = fileName;
        }
    }

    /** A link on a page: its raw href and the markup between its tags. */
    final class Anchor {
        public final String href;
        public final String body;

        Anchor(String href, String body) {
            this.href = href;
            this.body = body;
        }

        public String text() {
            return stripTags(body);
        }
    }

    /**
     * Chooses which of a page's builds to download.
     *
     * The launcher ships arm64 only, so a build that cannot load must not be picked.
     *
     * The order matters. These sites now label ABI-specific builds ("arm64-v8a. Xbox+servers,
     * no music", "armv7a. ..."), and on those pages the <em>unlabelled</em> default is
     * sometimes the 32-bit build. Choosing "the first build that is not 32-bit-only" therefore
     * picked the unlabelled 32-bit package and failed the ABI preflight, even though a
     * correctly-labelled arm64 build was on the same page. So an explicit 64-bit label wins
     * first, and only then does an unlabelled build.
     */
    static DownloadForm selectDownload(List<DownloadForm> forms) {
        if (forms == null || forms.isEmpty()) return null;
        for (DownloadForm form : forms) {
            if (is64BitLabel(form.label)) return form;
        }
        for (DownloadForm form : forms) {
            if (!form.is32BitOnly()) return form;
        }
        // Only 32-bit builds were offered; returning one lets the caller explain why it will
        // not launch rather than claiming the version does not exist.
        return forms.get(0);
    }

    /**
     * True when a label names a 32-bit ARM build.
     *
     * {@code armv7a} is the 32-bit ABI name and is the token these sites use to separate the
     * builds; {@code arm64}/{@code v8a} must not match, which is why this looks for {@code v7}
     * rather than the {@code arm} prefix.
     */
    static boolean is32BitOnly(String label) {
        if (label == null) return false;
        String lower = label.toLowerCase(Locale.US);
        return lower.contains("armv7") || lower.contains("armeabi") || lower.contains("v7a");
    }

    /**
     * True when a label explicitly names a 64-bit ARM build.
     *
     * Used to prefer the build the site itself marks as arm64 over an unlabelled default that
     * may secretly be 32-bit. {@code arm64-v8a} contains {@code arm64}, and {@code aarch64} is
     * the same architecture under its other common name.
     */
    static boolean is64BitLabel(String label) {
        if (label == null) return false;
        String lower = label.toLowerCase(Locale.US);
        return lower.contains("arm64") || lower.contains("aarch64");
    }

    /**
     * Derives an instance name from a page title or a downloaded file name.
     *
     * Instance directories accept only letters, digits, dot and underscore, so a remote file
     * name cannot be used verbatim. The version number is preferred when present because it
     * is what the user recognises and what the instance list compares against.
     */
    static String versionNameFrom(String text) {
        if (text == null) return "unknown";
        String code = extractVersionCode(text);
        if (code != null) return code;

        // A dashed run of three or more numbers is a version, not an architecture suffix
        // like "arm64-v8a" (which only ever has two groups).
        Matcher dashed = DASHED_VERSION_CODE.matcher(text);
        if (dashed.find()) return dashed.group(1).replace('-', '.');

        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_';
            if (allowed) {
                builder.append(c);
            } else if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                builder.append('_');
            }
        }

        String name = builder.toString().replaceAll("^\\.+", "");
        while (name.endsWith("_")) {
            name = name.substring(0, name.length() - 1);
        }
        // Instance names are capped so a long remote file name cannot produce one the rest
        // of the launcher treats as invalid.
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        return name.isEmpty() ? "unknown" : name;
    }

    /**
     * The package file name implied by a download URL's last path segment.
     *
     * Used when the response carries no {@code Content-Disposition}; a URL with no usable
     * segment falls back to {@code package.apk} so a download is never dropped.
     */
    static String fileNameFromUrl(String url) {
        if (url == null) return "package.apk";
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        int fragment = path.indexOf('#');
        if (fragment >= 0) path = path.substring(0, fragment);
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isEmpty() ? "package.apk" : name;
    }

    /**
     * The file name from a {@code Content-Disposition} header, or null when it carries none.
     *
     * Prefers {@code filename*} (RFC 5987, percent-encoded, non-ASCII safe) and falls back to
     * the plain {@code filename}, which may be quoted.
     */
    static String fileNameFromContentDisposition(String header) {
        if (header == null || header.isEmpty()) return null;
        String extended = matchGroup(FILENAME_STAR, header);
        if (extended != null) return percentDecode(extended);
        String plain = matchGroup(FILENAME_PLAIN, header);
        return plain == null || plain.isEmpty() ? null : plain;
    }

    /** True for the archive types Minecraft Bedrock is distributed in. */
    static boolean isPackageFileName(String fileName) {
        if (fileName == null) return false;
        String name = fileName.toLowerCase(Locale.US);
        return name.endsWith(".apk") || name.endsWith(".xapk")
                || name.endsWith(".apks") || name.endsWith(".apkm");
    }

    /**
     * True when a response is a Cloudflare interstitial rather than the page that was asked for.
     *
     * Detecting it lets the caller say so plainly instead of parsing the interstitial and
     * reporting "no versions found", which reads like the site is empty.
     */
    static boolean looksLikeChallenge(String html) {
        if (html == null || html.isEmpty()) return false;
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("just a moment")
                || lower.contains("cf_chl_opt")
                || lower.contains("challenge-platform")
                || lower.contains("enable javascript and cookies to continue");
    }

    /** Drops tags and entities so an anchor's label can be used as a title or file name. */
    static String stripTags(String html) {
        if (html == null) return "";
        String withoutTags = html.replaceAll("<[^>]*>", " ");
        String decoded = withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&#039;", "'")
                .replace("&#8217;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return WHITESPACE.matcher(decoded).replaceAll(" ").trim();
    }

    static String extractVersionCode(String text) {
        if (text == null) return null;
        Matcher matcher = VERSION_CODE.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Every anchor on a page, in document order. */
    static List<Anchor> anchors(String html) {
        if (html == null || html.isEmpty()) return Collections.emptyList();
        List<Anchor> found = new ArrayList<>();
        Matcher matcher = ANCHOR.matcher(html);
        while (matcher.find()) {
            found.add(new Anchor(matcher.group(1), matcher.group(2)));
        }
        return found;
    }

    /** Resolves a page-relative link against a source's host, leaving absolute URLs alone. */
    static String absolutize(String href, String host) {
        if (href == null) return null;
        String value = href.trim();
        if (value.isEmpty()) return null;
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return "https://" + host + value;
        if (value.startsWith("http://") || value.startsWith("https://")) return value;
        return null;
    }

    private static String matchGroup(Pattern pattern, String header) {
        Matcher matcher = pattern.matcher(header);
        if (!matcher.find()) return null;
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null) return matcher.group(i).trim();
        }
        return null;
    }

    private static String percentDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
