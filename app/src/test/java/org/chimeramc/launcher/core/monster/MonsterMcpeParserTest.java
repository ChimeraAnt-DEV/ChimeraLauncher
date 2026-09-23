package org.chimeramc.launcher.core.monster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.launcher.core.monster.MonsterMcpeParser.MonsterDownload;
import org.chimeramc.launcher.core.monster.MonsterMcpeParser.MonsterVersion;
import org.junit.Test;

import java.util.List;

/**
 * Pins the extraction rules against markup shaped like the live pages.
 *
 * These fixtures mirror the real structure (a listing card linking to
 * {@code /download-minecraft-pe/NNNN-slug.html}, and an article whose download block links to
 * {@code index.php?do=download&id=N} with the file name in the anchor text), so a change to
 * the parser that would break against the site fails here first.
 */
public class MonsterMcpeParserTest {

    private static final String LISTING_HTML = ""
            + "<div class=\"short2\">"
            + "  <a class=\"s2-title\" href=\"https://monster-mcpe.com/download-minecraft-pe/"
            + "4342-minecraft-265022-for-android.html\">Minecraft 26.50.22 for Android</a>"
            + "  <div class=\"s2-desc\">Developers have released a test version...</div>"
            + "</div>"
            + "<div class=\"short2\">"
            + "  <a class=\"s2-title\" href=\"/download-minecraft-pe/"
            + "4330-minecraft-12172-release.html\">Minecraft 1.21.72 [Release]</a>"
            + "</div>"
            + "<a class=\"side-item\" href=\"/mods/2750-time-stop-add-on.html\">"
            + "  <div class=\"side-title\">Time Stop Add-on 26.30+</div></a>"
            + "<a href=\"/download-minecraft-pe/\">Download Minecraft PE</a>";

    private static final String ARTICLE_HTML = ""
            + "<div class=\"f-dl-btm\" id=\"f-dl-btm\">"
            + "  <div class=\"dl-capt icon-l\">Minecraft 26.50.22 for Android</div>"
            + "  <div class=\"attach clr clearfix ignore-select\">"
            + "    <a class=\"dl-item icon-l\" "
            + "href=\"https://monster-mcpe.com/index.php?do=download&id=10114\">"
            + "      <div class=\"dl-title\"><span class=\"fa fa-download\"></span>"
            + "minecraft-26-50-22-arm64-v8a-xbox.apk</div>"
            + "      <div><span>Download free</span><span>[340.63 Mb]</span></div>"
            + "    </a>"
            + "  </div>"
            + "  <a class=\"dl-item dl-gp\" "
            + "href=\"https://play.google.com/store/apps/details?id=com.mojang.minecraftpe\">"
            + "Download from Google Play</a>"
            + "</div>";

    @Test
    public void extractsVersionsFromListingCards() {
        List<MonsterVersion> versions = MonsterMcpeParser.parseListing(LISTING_HTML);

        assertEquals(2, versions.size());
        assertEquals("Minecraft 26.50.22 for Android", versions.get(0).title);
        assertEquals("26.50.22", versions.get(0).versionCode);
        assertEquals("https://monster-mcpe.com/download-minecraft-pe/"
                + "4342-minecraft-265022-for-android.html", versions.get(0).pageUrl);
    }

    @Test
    public void relativeArticleLinksAreResolvedToTheSiteHost() {
        List<MonsterVersion> versions = MonsterMcpeParser.parseListing(LISTING_HTML);

        assertEquals("https://monster-mcpe.com/download-minecraft-pe/"
                + "4330-minecraft-12172-release.html", versions.get(1).pageUrl);
        assertTrue(versions.get(1).release);
        assertFalse(versions.get(0).release);
    }

    @Test
    public void navigationAndOtherSectionsAreIgnored() {
        List<MonsterVersion> versions = MonsterMcpeParser.parseListing(LISTING_HTML);

        for (MonsterVersion version : versions) {
            assertFalse(version.pageUrl.contains("/mods/"));
            assertTrue(version.pageUrl.contains("/download-minecraft-pe/"));
        }
        // The bare category link has no article number, so it is not a version.
        assertEquals(2, versions.size());
    }

    @Test
    public void duplicateLinksCollapseToOneEntry() {
        String html = LISTING_HTML
                + "<a href=\"/download-minecraft-pe/4342-minecraft-265022-for-android.html\">"
                + "Minecraft 26.50.22 for Android</a>";

        assertEquals(2, MonsterMcpeParser.parseListing(html).size());
    }

    @Test
    public void extractsTheDownloadTargetFromAnArticle() {
        List<MonsterDownload> downloads = MonsterMcpeParser.parseDownloadPage(ARTICLE_HTML);

        assertEquals(1, downloads.size());
        assertEquals("minecraft-26-50-22-arm64-v8a-xbox.apk", downloads.get(0).fileName);
        assertEquals("https://monster-mcpe.com/index.php?do=download&id=10114",
                downloads.get(0).url);
        assertTrue(downloads.get(0).isPackage());
    }

    @Test
    public void googlePlayLinkIsNotTreatedAsAPackageDownload() {
        List<MonsterDownload> downloads = MonsterMcpeParser.parseDownloadPage(ARTICLE_HTML);

        for (MonsterDownload download : downloads) {
            assertFalse(download.url.contains("play.google.com"));
        }
    }

    @Test
    public void firstPackagePrefersAnImportableArchive() {
        String html = "<a href=\"/index.php?do=download&id=1\">Read the changelog</a>"
                + ARTICLE_HTML;

        List<MonsterDownload> downloads = MonsterMcpeParser.parseDownloadPage(html);
        MonsterDownload first = MonsterMcpeParser.firstPackage(downloads);

        assertNotNull(first);
        assertEquals("minecraft-26-50-22-arm64-v8a-xbox.apk", first.fileName);
    }

    @Test
    public void firstPackageIsNullWhenNothingIsDownloadable() {
        assertNull(MonsterMcpeParser.firstPackage(
                MonsterMcpeParser.parseDownloadPage("<p>No downloads here</p>")));
        assertNull(MonsterMcpeParser.firstPackage(null));
    }

    @Test
    public void archiveSnapshotsAreUnwrappedToTheLiveUrl() {
        String html = "<a class=\"s2-title\" href=\"https://web.archive.org/web/20260730024936/"
                + "https://monster-mcpe.com/download-minecraft-pe/"
                + "4342-minecraft-265022-for-android.html\">Minecraft 26.50.22</a>";

        List<MonsterVersion> versions = MonsterMcpeParser.parseListing(html);

        assertEquals(1, versions.size());
        assertEquals("https://monster-mcpe.com/download-minecraft-pe/"
                + "4342-minecraft-265022-for-android.html", versions.get(0).pageUrl);
    }

    @Test
    public void recognisesEveryBedrockPackageExtension() {
        assertTrue(MonsterMcpeParser.isPackageFileName("minecraft.apk"));
        assertTrue(MonsterMcpeParser.isPackageFileName("minecraft.xapk"));
        assertTrue(MonsterMcpeParser.isPackageFileName("minecraft.apks"));
        assertTrue(MonsterMcpeParser.isPackageFileName("minecraft.apkm"));
        assertTrue(MonsterMcpeParser.isPackageFileName("MINECRAFT.APK"));
    }

    @Test
    public void rejectsNonPackagesAndMalformedNames() {
        assertFalse(MonsterMcpeParser.isPackageFileName("poster.png"));
        assertFalse(MonsterMcpeParser.isPackageFileName("minecraft.zip"));
        assertFalse(MonsterMcpeParser.isPackageFileName(null));
        assertFalse(MonsterMcpeParser.isPackageFileName(""));
    }

    @Test
    public void normalizeUrlHandlesTheShapesTheSiteEmits() {
        assertEquals("https://monster-mcpe.com/x.html",
                MonsterMcpeParser.normalizeUrl("/x.html"));
        assertEquals("https://monster-mcpe.com/x.html",
                MonsterMcpeParser.normalizeUrl("https://monster-mcpe.com/x.html"));
        assertEquals("https://monster-mcpe.com/x.html",
                MonsterMcpeParser.normalizeUrl("//monster-mcpe.com/x.html"));
        assertNull(MonsterMcpeParser.normalizeUrl("mailto:someone@example.com"));
        assertNull(MonsterMcpeParser.normalizeUrl("   "));
        assertNull(MonsterMcpeParser.normalizeUrl(null));
    }

    @Test
    public void versionNameUsesTheVersionNumberWhenTheNameCarriesOne() {
        assertEquals("26.50.22", MonsterMcpeParser.versionNameFrom("minecraft-26-50-22-arm64-v8a-xbox.apk"));
        assertEquals("1.21.72", MonsterMcpeParser.versionNameFrom("Minecraft 1.21.72 [Release]"));
    }

    @Test
    public void dashedFileNameDoesNotYieldTheArchitectureSuffix() {
        // "arm64-v8a" is two groups; the version run is three, so the version wins.
        assertEquals("26.50.22",
                MonsterMcpeParser.versionNameFrom("minecraft-26-50-22-arm64-v8a-xbox.apk"));
        assertFalse(MonsterMcpeParser.versionNameFrom("minecraft-26-50-22-arm64-v8a-xbox.apk")
                .contains("arm64"));
    }

    @Test
    public void versionNameIsSanitisedForADirectoryName() {
        // Instance directories accept only letters, digits, dot and underscore.
        String name = MonsterMcpeParser.versionNameFrom("Some Build (final)/v2");
        assertTrue(name.matches("[A-Za-z0-9._]+"));
        assertFalse(name.startsWith("."));
    }

    @Test
    public void versionNameNeverReturnsBlank() {
        assertEquals("unknown", MonsterMcpeParser.versionNameFrom("..."));
        assertEquals("unknown", MonsterMcpeParser.versionNameFrom(""));
        assertEquals("unknown", MonsterMcpeParser.versionNameFrom(null));
    }

    @Test
    public void stripTagsDecodesEntitiesAndCollapsesWhitespace() {
        assertEquals("Download free [340.63 Mb]",
                MonsterMcpeParser.stripTags("<span>Download free</span> <span>[340.63&nbsp;Mb]</span>"));
        assertEquals("a & b", MonsterMcpeParser.stripTags("a &amp; b"));
        assertEquals("", MonsterMcpeParser.stripTags(null));
    }

    @Test
    public void emptyInputYieldsNoResults() {
        assertTrue(MonsterMcpeParser.parseListing("").isEmpty());
        assertTrue(MonsterMcpeParser.parseListing(null).isEmpty());
        assertTrue(MonsterMcpeParser.parseDownloadPage("").isEmpty());
        assertTrue(MonsterMcpeParser.parseDownloadPage(null).isEmpty());
    }

    @Test
    public void displayLabelFallsBackWhenTheTitleIsMissing() {
        MonsterVersion version = new MonsterVersion("", "https://monster-mcpe.com/x.html", "1.21.72", false);
        assertEquals("Minecraft 1.21.72", version.displayLabel());
    }

    @Test
    public void challengeInterstitialIsNotMistakenForAPage() {
        String interstitial = "<html><head><title>Just a moment...</title></head><body>"
                + "<div>Enable JavaScript and cookies to continue</div>"
                + "<script>window._cf_chl_opt={};</script></body></html>";
        assertTrue(MonsterMcpeParser.looksLikeChallenge(interstitial));
        assertFalse(MonsterMcpeParser.looksLikeChallenge(
                "<html><body><a href=\"/download-minecraft-pe/1-minecraft-1-21-0.html\">"
                        + "Minecraft Bedrock 1.21.0</a></body></html>"));
        assertFalse(MonsterMcpeParser.looksLikeChallenge(""));
        assertFalse(MonsterMcpeParser.looksLikeChallenge(null));
    }

    @Test
    public void aRealListingPageIsNotFlaggedAsAChallenge() {
        String listing = "<a href=\"https://monster-mcpe.com/download-minecraft-pe/"
                + "1246-minecraft-pe-1-20-0.html\">Minecraft Bedrock 1.20.0</a>";
        assertFalse(MonsterMcpeParser.looksLikeChallenge(listing));
        assertEquals(1, MonsterMcpeParser.parseListing(listing).size());
    }
}
