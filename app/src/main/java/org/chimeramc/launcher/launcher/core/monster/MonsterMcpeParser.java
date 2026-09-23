package org.chimeramc.launcher.core.monster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the Monster MCPE download pages.
 *
 * Deliberately free of Android and network APIs: the pages are third-party HTML that will
 * change shape, so the extraction rules are kept in one place that can be pinned by unit
 * tests instead of only being exercised through a live download.
 *
 * The site publishes a listing page ({@code /download-minecraft-pe}) whose entries link to a
 * per-version article, and each article links to {@code index.php?do=download&id=N} — a
 * redirect that ends at the actual package file.
 */
public final class MonsterMcpeParser {

    public static final String SITE_HOST = "monster-mcpe.com";
    public static final String LISTING_URL = "https://" + SITE_HOST + "/download-minecraft-pe";

    /** Instance names are limited to this length elsewhere in the launcher. */
    public static final int MAX_NAME_LENGTH = 40;

    private static final Pattern ANCHOR = Pattern.compile(
            "<a\\b[^>]*href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ARTICLE_PATH = Pattern.compile(
            "/download-minecraft-pe/(?:[^/\"'?#]*/)?(\\d+)-([a-z0-9\\-]+)\\.html",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VERSION_CODE = Pattern.compile("(\\d+(?:\\.\\d+)+)");
    /** The site's file names spell versions with dashes: {@code minecraft-26-50-22-arm64-v8a}. */
    private static final Pattern DASHED_VERSION_CODE = Pattern.compile("(\\d+(?:-\\d+){2,})");
    private static final Pattern DOWNLOAD_QUERY = Pattern.compile("do=download", Pattern.CASE_INSENSITIVE);
    private static final Pattern PACKAGE_FILE = Pattern.compile(
            "([A-Za-z0-9._\\-]+\\.(?:apk|xapk|apks|apkm))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ARCHIVE_PREFIX = Pattern.compile(
            "^https?://web\\.archive\\.org/web/[^/]+/",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private MonsterMcpeParser() {
    }

    /** A Bedrock release advertised on the listing page. */
    public static final class MonsterVersion {
        public final String title;
        public final String pageUrl;
        public final String versionCode;
        public final boolean release;

        MonsterVersion(String title, String pageUrl, String versionCode, boolean release) {
            this.title = title;
            this.pageUrl = pageUrl;
            this.versionCode = versionCode;
            this.release = release;
        }

        /** Label shown when a title is missing, so the row is never blank. */
        public String displayLabel() {
            if (title != null && !title.isEmpty()) return title;
            if (versionCode != null && !versionCode.isEmpty()) return "Minecraft " + versionCode;
            return pageUrl;
        }
    }

    /** One downloadable package on a version page. */
    public static final class MonsterDownload {
        public final String url;
        public final String fileName;

        public MonsterDownload(String url, String fileName) {
            this.url = url;
            this.fileName = fileName;
        }

        /** True when the file is a Minecraft package this launcher can import. */
        public boolean isPackage() {
            return isPackageFileName(fileName);
        }
    }

    /**
     * Extracts the version entries from a listing page, newest first as published.
     *
     * Only entries whose link follows the article URL shape are considered, which keeps the
     * surrounding navigation ("Download Minecraft PE", category links) out of the result.
     */
    public static List<MonsterVersion> parseListing(String html) {
        if (html == null || html.isEmpty()) return Collections.emptyList();

        Map<String, MonsterVersion> byUrl = new LinkedHashMap<>();
        Matcher anchor = ANCHOR.matcher(html);
        while (anchor.find()) {
            String href = normalizeUrl(anchor.group(1));
            if (href == null) continue;

            Matcher article = ARTICLE_PATH.matcher(href);
            if (!article.find()) continue;

            String title = stripTags(anchor.group(2));
            String versionCode = extractVersionCode(title);
            if (versionCode == null) {
                versionCode = extractVersionCode(article.group(2));
            }
            boolean release = containsIgnoreCase(title, "[release]")
                    || containsIgnoreCase(article.group(2), "release");

            if (!byUrl.containsKey(href)) {
                byUrl.put(href, new MonsterVersion(title, href, versionCode, release));
            }
        }
        return new ArrayList<>(byUrl.values());
    }

    /**
     * Extracts the download targets from a version page.
     *
     * The site can list more than one build (for example a compressed and a full package),
     * so all are returned in document order and the caller decides which to use.
     */
    public static List<MonsterDownload> parseDownloadPage(String html) {
        if (html == null || html.isEmpty()) return Collections.emptyList();

        Map<String, MonsterDownload> byUrl = new LinkedHashMap<>();
        Matcher anchor = ANCHOR.matcher(html);
        while (anchor.find()) {
            String href = normalizeUrl(anchor.group(1));
            if (href == null || !DOWNLOAD_QUERY.matcher(href).find()) continue;

            String fileName = extractPackageFileName(stripTags(anchor.group(2)));
            if (fileName == null) continue;

            if (!byUrl.containsKey(href)) {
                byUrl.put(href, new MonsterDownload(href, fileName));
            }
        }
        return new ArrayList<>(byUrl.values());
    }

    /** The first downloadable package on a page, or null when the page offers none. */
    public static MonsterDownload firstPackage(List<MonsterDownload> downloads) {
        if (downloads == null) return null;
        for (MonsterDownload download : downloads) {
            if (download.isPackage()) return download;
        }
        return null;
    }

    /** True for the archive types Minecraft Bedrock is distributed in. */
    public static boolean isPackageFileName(String fileName) {
        if (fileName == null) return false;
        String name = fileName.toLowerCase(Locale.US);
        return name.endsWith(".apk") || name.endsWith(".xapk")
                || name.endsWith(".apks") || name.endsWith(".apkm");
    }

    /**
     * Resolves a page-relative link to an absolute URL and strips any Wayback Machine
     * wrapper, so the parser accepts both the live site and archived copies.
     */
    public static String normalizeUrl(String href) {
        if (href == null) return null;
        String value = href.trim();
        if (value.isEmpty()) return null;

        Matcher archive = ARCHIVE_PREFIX.matcher(value);
        if (archive.find()) {
            value = value.substring(archive.end());
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.startsWith("/")) {
            return "https://" + SITE_HOST + value;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return null;
    }

    /**
     * Derives an instance name from a page title or a downloaded file name.
     *
     * Instance directories accept only letters, digits, dot and underscore, so a remote file
     * name cannot be used verbatim. The version number is preferred when present because it
     * is what the user recognises and what the instance list compares against.
     */
    public static String versionNameFrom(String text) {
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
        // Instance names are capped so a long remote file name cannot produce one the
        // rest of the launcher treats as invalid.
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        return name.isEmpty() ? "unknown" : name;
    }

    /**
     * True when a response is Cloudflare's interstitial rather than the page that was asked for.
     *
     * The site is behind a browser challenge, so a plain HTTP fetch returns this instead of
     * the listing. Detecting it lets the caller say so plainly instead of parsing the
     * interstitial and reporting "no versions found", which reads like the site is empty.
     */
    public static boolean looksLikeChallenge(String html) {
        if (html == null || html.isEmpty()) return false;
        String lower = html.toLowerCase(Locale.ROOT);
        return lower.contains("just a moment")
                || lower.contains("cf_chl_opt")
                || lower.contains("challenge-platform")
                || lower.contains("enable javascript and cookies to continue");
    }

    private static String extractVersionCode(String text) {
        if (text == null) return null;
        Matcher matcher = VERSION_CODE.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String extractPackageFileName(String text) {
        if (text == null) return null;
        Matcher matcher = PACKAGE_FILE.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static boolean containsIgnoreCase(String text, String needle) {
        return text != null && text.toLowerCase(Locale.US).contains(needle.toLowerCase(Locale.US));
    }

    /** Drops tags and entities so an anchor's label can be used as a title or file name. */
    public static String stripTags(String html) {
        if (html == null) return "";
        String withoutTags = html.replaceAll("<[^>]*>", " ");
        String decoded = withoutTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&#039;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return WHITESPACE.matcher(decoded).replaceAll(" ").trim();
    }
}
