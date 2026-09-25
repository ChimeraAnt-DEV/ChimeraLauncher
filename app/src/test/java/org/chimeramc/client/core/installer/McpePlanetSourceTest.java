package org.chimeramc.client.core.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.installer.BedrockSource.DownloadForm;
import org.chimeramc.client.core.installer.BedrockSource.Field;
import org.chimeramc.client.core.installer.BedrockSource.Version;
import org.junit.Test;

import java.util.List;

/**
 * Pins the MCPE-Planet extraction rules against markup shaped like the live pages.
 *
 * The fixtures mirror the real structure, read off the site: the index lists version cards as
 * {@code <article>} elements whose ribbon spans carry the update name, the version and the
 * {@code Release}/{@code Beta} channel, and a version page holds one {@code <form method="post">}
 * per build with an <em>empty</em> action and a submit button naming the variant. The form's
 * response is HTTP 200 whose script assigns the package URL to {@code furl}. A change to the
 * parser that would break against the site fails here first.
 */
public class McpePlanetSourceTest {

    private final McpePlanetSource source = new McpePlanetSource();

    private static final String LISTING_HTML = ""
            + "<div class=\"browse-view\"><div class=\"g-grid\">"
            + "<article class=\"g-block size-33-3\">"
            + "  <div class=\"post-type-post post-40311 app-product\" id=\"post-40311\">"
            + "    <div class=\"app-product-media-container\">"
            + "      <div class=\"ribbon-container ribbon-left ribbon-level-1 ribbon-2\">"
            + "        <span class=\"ribbon\">Wilderness Bound</span></div>"
            + "      <div class=\"ribbon-container ribbon-right ribbon-level-1 ribbon-1\">"
            + "        <span class=\"ribbon\">1.26.60.28</span></div>"
            + "      <div class=\"ribbon-container ribbon-right ribbon-level-2 ribbon-r\">"
            + "        <span class=\"ribbon\">Beta</span></div>"
            + "      <a title=\"Minecraft 1.26.60.28 APK\""
            + "         href=\"https://mcpe-planet.com/downloads/minecraft-pe-1-26/60-28/\">"
            + "        <img class=\"lozad\" alt=\"Minecraft 1.26.60.28 APK\"></a>"
            + "    </div>"
            + "    <div class=\"app-product-descr-container\">"
            + "      <a href=\"https://mcpe-planet.com/downloads/minecraft-pe-1-26/60-28/\">"
            + "        <div style=\"color:black\">Minecraft 1.26.60.28 APK</div></a>"
            + "    </div>"
            + "  </div></article>"
            + "<article class=\"g-block size-33-3\">"
            + "  <div class=\"post-type-post post-40132 app-product\" id=\"post-40132\">"
            + "    <div class=\"app-product-media-container\">"
            + "      <div class=\"ribbon-container ribbon-left ribbon-level-1 ribbon-2\">"
            + "        <span class=\"ribbon\">Wilderness Bound</span></div>"
            + "      <div class=\"ribbon-container ribbon-right ribbon-level-1 ribbon-1\">"
            + "        <span class=\"ribbon\">26.51</span></div>"
            + "      <div class=\"ribbon-container ribbon-right ribbon-level-2 ribbon-r\">"
            + "        <span class=\"ribbon\">Release</span></div>"
            + "      <a href=\"/downloads/minecraft-pe-1-26/26-51/\">"
            + "        <img alt=\"Minecraft 26.51\"></a>"
            + "    </div>"
            + "  </div></article>"
            + "</div></div>"
            // Sidebar version menu, repeated on every page: it must not become a row.
            + "<div class=\"g-tagmenu\">"
            + "  <a href=\"/downloads/minecraft-pe-1-26/26-50/\" title=\"1.26.50\">1.26.50</a>"
            + "</div>"
            // Series and root links, which are not version pages.
            + "<a href=\"/downloads/minecraft-pe-1-26/\">1.26</a>"
            + "<a href=\"/downloads/\">Download Minecraft</a>"
            + "<link rel=\"next\" href=\"https://mcpe-planet.com/downloads/page/2/\">";

    private static final String VERSION_HTML = ""
            + "<!DOCTYPE html><html><head>"
            + "<link rel=\"canonical\" href=\"https://mcpe-planet.com/downloads/minecraft-pe-1-26/60-24/\" />"
            + "</head><body>"
            + "<div id=\"app-download\" class=\"app-download\">"
            + "  <form action=\"\" method=\"post\">"
            + "    <input type=\"hidden\" name=\"sourcepost\" value=\"40099\">"
            + "    <button type=\"submit\" name=\"app\" value=\"40149\">"
            + "      Download minecraft-26-60-24 .apk xbox + servers (356.28 Mb)</button>"
            + "  </form>"
            + "  <form action=\"\" method=\"post\">"
            + "    <input type=\"hidden\" name=\"sourcepost\" value=\"40099\">"
            + "    <button type=\"submit\" name=\"app\" value=\"40150\">"
            + "      Download minecraft-26-60-24-music .apk xbox + servers + music (678.75 Mb)</button>"
            + "  </form>"
            + "  <form action=\"\" method=\"post\">"
            + "    <input type=\"hidden\" name=\"sourcepost\" value=\"40099\">"
            + "    <button type=\"submit\" name=\"app\" value=\"40143\">"
            + "      Download minecraft-26-60-24-armeabi-v7a .apk xbox + servers (356.28 Mb)</button>"
            + "  </form>"
            + "</div></body></html>";

    /** The shape of the response to posting the form: HTTP 200 with a scripted URL. */
    private static final String RESOLVED_HTML = ""
            + "<!DOCTYPE html><html><body><p>Download Safety Notes</p></body></html>"
            + "<script>"
            + "  var bname = 'minecraft-26-60-24.apk';"
            + "  var furl = 'https://mcpe-planet.com/wp-content/uploads/version/minecraft-26-60-24.apk';"
            + "  var t1 = 10;"
            + "</script>";

    @Test
    public void extractsVersionsFromTheIndex() {
        List<Version> versions = source.parseListing(LISTING_HTML);

        assertEquals(2, versions.size());
        assertEquals("https://mcpe-planet.com/downloads/minecraft-pe-1-26/60-28/",
                versions.get(0).pageUrl);
        assertEquals("1.26.60.28", versions.get(0).versionCode);
        assertEquals("https://mcpe-planet.com/downloads/minecraft-pe-1-26/26-51/",
                versions.get(1).pageUrl);
        assertEquals("26.51", versions.get(1).versionCode);
    }

    /**
     * The slug is only the build part ({@code 60-28} for 1.26.60.28), so the card's ribbon is
     * the version the user recognises; a parser that trusted the slug would label the row
     * "60.28" and break the Installed check against the real APK version name.
     */
    @Test
    public void prefersTheCardVersionRibbonOverTheSlug() {
        List<Version> versions = source.parseListing(LISTING_HTML);

        assertEquals("1.26.60.28", versions.get(0).versionCode);
        assertFalse(versions.get(0).versionCode.startsWith("60."));
    }

    @Test
    public void marksReleaseAndBetaCards() {
        List<Version> versions = source.parseListing(LISTING_HTML);

        assertFalse("a Beta card must not be flagged as a release", versions.get(0).release);
        assertTrue("a Release card must be flagged", versions.get(1).release);
    }

    @Test
    public void resolvesAbsoluteAndRelativeVersionLinks() {
        List<Version> versions = source.parseListing(LISTING_HTML);

        for (Version version : versions) {
            assertTrue(version.pageUrl.startsWith("https://mcpe-planet.com/downloads/minecraft-pe-"));
        }
    }

    /**
     * Every listing page repeats the newest versions in its sidebar menu. Those links are
     * outside the cards and belong to an earlier page, so counting them would re-offer the
     * newest releases while the user pages into the archive.
     */
    @Test
    public void ignoresTheSidebarVersionMenuAndSeriesLinks() {
        List<Version> versions = source.parseListing(LISTING_HTML);

        for (Version version : versions) {
            assertFalse("the sidebar 26.50 link must not be a row",
                    version.pageUrl.endsWith("/26-50/"));
            assertFalse(version.pageUrl.endsWith("/downloads/"));
            assertFalse(version.pageUrl.endsWith("/minecraft-pe-1-26/"));
        }
    }

    @Test
    public void extractsEveryBuildFormOnAVersionPage() {
        List<DownloadForm> forms = source.parseVersionPage(VERSION_HTML);

        assertEquals(3, forms.size());
        // The form posts back to the page, so the empty action resolves to the canonical URL.
        assertEquals("https://mcpe-planet.com/downloads/minecraft-pe-1-26/60-24/",
                forms.get(0).actionUrl);
        assertTrue(forms.get(0).label.contains("minecraft-26-60-24"));
    }

    @Test
    public void keepsTheHiddenFieldsAndTheBuildIdNeededToSubmit() {
        List<DownloadForm> forms = source.parseVersionPage(VERSION_HTML);

        DownloadForm first = forms.get(0);
        assertEquals("40099", field(first, "sourcepost"));
        // Without the submit button's name/value the post would download the wrong build.
        assertEquals("40149", field(first, "app"));
    }

    /**
     * The launcher ships arm64 only, and this site labels the ABI in the button, so an
     * explicitly 32-bit build must never be picked while an unlabelled one is available.
     */
    @Test
    public void avoidsA32BitLabelledBuild() {
        List<DownloadForm> forms = source.parseVersionPage(VERSION_HTML);
        DownloadForm chosen = BedrockSource.selectDownload(forms);

        assertNotNull(chosen);
        assertFalse(chosen.is32BitOnly());
        assertEquals("40149", field(chosen, "app"));
    }

    @Test
    public void prefersAnExplicitlyLabelledArm64Build() {
        String html = ""
                + "<link rel=\"canonical\" href=\"https://mcpe-planet.com/downloads/minecraft-pe-1-26/26-51/\" />"
                + "<form action=\"\" method=\"post\">"
                + "  <input type=\"hidden\" name=\"sourcepost\" value=\"40132\">"
                + "  <button type=\"submit\" name=\"app\" value=\"40141\">"
                + "    Download minecraft-26-51 .apk xbox + servers</button></form>"
                + "<form action=\"\" method=\"post\">"
                + "  <input type=\"hidden\" name=\"sourcepost\" value=\"40132\">"
                + "  <button type=\"submit\" name=\"app\" value=\"40143\">"
                + "    Download minecraft-26-51-armeabi-v7a .apk xbox + servers</button></form>"
                + "<form action=\"\" method=\"post\">"
                + "  <input type=\"hidden\" name=\"sourcepost\" value=\"40132\">"
                + "  <button type=\"submit\" name=\"app\" value=\"40144\">"
                + "    Download minecraft-26-51-arm64-v8a .apk xbox + servers</button></form>";
        DownloadForm chosen = BedrockSource.selectDownload(source.parseVersionPage(html));

        assertNotNull(chosen);
        assertEquals("40144", field(chosen, "app"));
    }

    @Test
    public void fallsBackToTheOnlyBuildWhenEveryBuildIs32Bit() {
        String html = VERSION_HTML.replace("minecraft-26-60-24 .apk", "minecraft-26-60-24-armeabi-v7a .apk")
                .replace("minecraft-26-60-24-music .apk", "minecraft-26-60-24-armeabi-v7a-music .apk")
                .replace("minecraft-26-60-24-armeabi-v7a .apk xbox + servers (356.28 Mb)",
                        "minecraft-26-60-24-armeabi-v7a .apk xbox + servers (356.28 Mb)");
        DownloadForm chosen = BedrockSource.selectDownload(source.parseVersionPage(html));

        assertNotNull("a 32-bit-only version still resolves, so the caller can explain", chosen);
        assertTrue(chosen.is32BitOnly());
    }

    /** The package URL is only revealed after the form is posted, inside a script. */
    @Test
    public void readsThePackageUrlFromTheFormResponse() {
        String url = source.parseResolvedUrl(RESOLVED_HTML);

        assertEquals("https://mcpe-planet.com/wp-content/uploads/version/minecraft-26-60-24.apk",
                url);
    }

    /** A response that exposes only the file name still identifies the package. */
    @Test
    public void fallsBackToTheUploadsDirectoryWhenOnlyTheFileNameIsExposed() {
        String html = "<script>var bname = 'minecraft-26-50.apk';</script>";

        assertEquals("https://mcpe-planet.com/wp-content/uploads/version/minecraft-26-50.apk",
                source.parseResolvedUrl(html));
    }

    @Test
    public void fallsBackToADirectLinkWhenTheResponseHasNoScript() {
        String html = "<html><body>"
                + "<a href=\"https://mcpe-planet.com/wp-content/uploads/version/minecraft-26-50.apk\">here</a>"
                + "</body></html>";

        assertEquals("https://mcpe-planet.com/wp-content/uploads/version/minecraft-26-50.apk",
                source.parseResolvedUrl(html));
    }

    @Test
    public void reportsNoUrlWhenTheResponseCarriesNone() {
        assertNull(source.parseResolvedUrl("<html><body>Nothing here</body></html>"));
        assertNull(source.parseResolvedUrl(""));
        assertNull(source.parseResolvedUrl(null));
    }

    @Test
    public void derivesTheFileNameFromTheResolvedUrl() {
        String url = source.parseResolvedUrl(RESOLVED_HTML);

        assertEquals("minecraft-26-60-24.apk", BedrockSource.fileNameFromUrl(url));
        assertTrue(BedrockSource.isPackageFileName(BedrockSource.fileNameFromUrl(url)));
    }

    @Test
    public void derivesAnInstanceNameFromTheResolvedPackage() {
        String url = source.parseResolvedUrl(RESOLVED_HTML);

        assertEquals("26.60.24", BedrockSource.versionNameFrom(BedrockSource.fileNameFromUrl(url)));
    }

    /**
     * The archive is dozens of pages deep. The next-page link is what lets the screen walk past
     * the newest handful and reach older releases.
     */
    @Test
    public void readsTheNextPageLinkFromPagination() {
        String html = "<nav>"
                + "<a href=\"https://mcpe-planet.com/downloads/\" class=\"prev\">Previous</a>"
                + "<a href=\"https://mcpe-planet.com/downloads/page/3/\" class=\"next\">Next</a>"
                + "</nav>";

        assertEquals("https://mcpe-planet.com/downloads/page/3/", source.nextListingUrl(html));
    }

    @Test
    public void reportsNoNextPageOnTheLastPage() {
        assertNull(source.nextListingUrl("<nav><a class=\"prev\" href=\"/downloads/page/39/\">Back</a></nav>"));
        assertNull(source.nextListingUrl(""));
        assertNull(source.nextListingUrl(null));
    }

    @Test
    public void resolvesARelativeNextPageLink() {
        String html = "<a class=\"next\" href=\"/downloads/page/8/\">Next</a>";

        assertEquals("https://mcpe-planet.com/downloads/page/8/", source.nextListingUrl(html));
    }

    @Test
    public void detectsABrowserChallengePage() {
        assertTrue(BedrockSource.looksLikeChallenge(
                "<html><head><title>Just a moment...</title></head></html>"));
        assertFalse(BedrockSource.looksLikeChallenge(LISTING_HTML));
    }

    @Test
    public void namesItsHostAndListingUrl() {
        assertEquals("mcpe-planet.com", source.displayHost());
        assertEquals("https://mcpe-planet.com/downloads/", source.listingUrl());
        assertEquals("mcpeplanet", source.id());
    }

    /**
     * The registry is what the UI reads. MCPE-Planet is the source the Installations tab uses,
     * and MCPEDL stays registered as the fallback mirror.
     */
    @Test
    public void registryExposesMcpePlanetAsActiveWithMcpedlAsFallback() {
        assertEquals("mcpeplanet", SourceRegistry.active().id());
        assertEquals("mcpeplanet", SourceRegistry.byId("mcpeplanet").id());
        assertEquals("mcpedl", SourceRegistry.byId("mcpedl").id());
        assertEquals("mcpeplanet", SourceRegistry.byId("no-such-source").id());
        assertEquals(2, SourceRegistry.all().size());
    }

    /** The slug is only the build part, so it must not be mistaken for the full version. */
    @Test
    public void slugFallbackYieldsTheBuildPartOnly() {
        assertEquals("60.24", McpePlanetSource.versionFromSlug("60-24"));
        assertNull(McpePlanetSource.versionFromSlug("arm64"));
        assertNull(McpePlanetSource.versionFromSlug(""));
        assertNull(McpePlanetSource.versionFromSlug(null));
    }

    private static String field(DownloadForm form, String name) {
        for (Field field : form.fields) {
            if (name.equals(field.name)) return field.value;
        }
        return null;
    }
}
