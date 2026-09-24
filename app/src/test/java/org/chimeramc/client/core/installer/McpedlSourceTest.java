package org.chimeramc.client.core.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.installer.BedrockSource.DownloadForm;
import org.chimeramc.client.core.installer.BedrockSource.ResolvedDownload;
import org.chimeramc.client.core.installer.BedrockSource.Version;
import org.junit.Test;

import java.util.List;

/**
 * Pins the MCPEDL extraction rules against markup shaped like the live pages.
 *
 * The fixtures mirror the real structure, which was read off the site: the index lists
 * version cards linking to {@code /minecraft-pe-26-60-24-apk/}, that page holds one
 * {@code <form action="/show_file.php">} per build with hidden {@code file_id} fields, and
 * the form's response is HTTP 200 whose button carries the package URL in an onclick. A
 * change to the parser that would break against the site fails here first.
 */
public class McpedlSourceTest {

    private final McpedlSource source = new McpedlSource();

    private static final String LISTING_HTML = ""
            + "<div class=\"card\">"
            + "  <span style=\"font-weight:900;\">MCPE 26.60.24"
            + "    <span style=\"color:#666;\"> (16.09.2026: Latest Beta)</span></span>"
            + "  <a href=\"/minecraft-pe-26-60-24-apk/\" style=\"color:#2563eb;\">Read More...</a>"
            + "  <form method=\"post\" action=\"/getfile/7549\">"
            + "    <input type=\"hidden\" name=\"file_id\" value=\"7549\"></form>"
            + "</div>"
            + "<div class=\"card\">"
            + "  <span style=\"font-weight:900;\">MCPE 26.51"
            + "    <span style=\"color:#666;\"> (16.09.2026: Latest Release)</span></span>"
            + "  <a href=\"/minecraft-pe-26-51-apk/\">Read More...</a>"
            + "</div>"
            // A category link, which must not be mistaken for a version page.
            + "<a href=\"/downloading/minecraft-pe-26/\">Minecraft PE 26</a>"
            + "<a href=\"/downloading/\">Downloading</a>"
            + "<a href=\"https://mcpedl.org/minecraft-pe-26-50-apk/\">Minecraft 26.50 APK</a>";

    private static final String VERSION_HTML = ""
            + "<section id=\"download-link\">"
            + "  <h2>Download Minecraft 26.60.24 for all phones</h2>"
            + "  <table><thead><tr><th>Options</th><th>File</th></tr></thead><tbody>"
            + "    <tr><td>Xbox+servers, no music</td><td>"
            + "      <form method=\"post\" action=\"/show_file.php\" style=\"display:inline-block;\">"
            + "        <input type=\"hidden\" name=\"post_title\" value=\"Minecraft 26.60.24 (1.26.60.24)\">"
            + "        <input type=\"hidden\" name=\"file_id\" value=\"7549\">"
            + "        <input type=\"hidden\" name=\"post_url\" value=\"https://mcpedl.org/minecraft-pe-26-60-24-apk/\">"
            + "        <button type=\"submit\" class=\"button\">Download(380 Mb)</button></form></td></tr>"
            + "    <tr><td>Xbox+servers, +music</td><td>"
            + "      <form method=\"post\" action=\"/show_file.php\">"
            + "        <input type=\"hidden\" name=\"post_title\" value=\"Minecraft 26.60.24 (1.26.60.24)\">"
            + "        <input type=\"hidden\" name=\"file_id\" value=\"7550\">"
            + "        <button type=\"submit\" class=\"button\">Download(750 Mb)</button></form></td></tr>"
            + "    <tr><td>armv7a. Xbox+servers, +music</td><td>"
            + "      <form method=\"post\" action=\"/show_file.php\">"
            + "        <input type=\"hidden\" name=\"file_id\" value=\"7551\">"
            + "        <button type=\"submit\" class=\"button\">Download(750 Mb)</button></form></td></tr>"
            + "  </tbody></table>"
            + "</section>";

    /** The shape of the response to posting the form: HTTP 200 with a scripted redirect. */
    private static final String RESOLVED_HTML = ""
            + "<!DOCTYPE html><html><head><title>Download File</title></head><body>"
            + "  <div class=\"link\" id=\"linkContainer\">"
            + "    <button type=\"button\" onclick=\"window.location.href="
            + "'https://file.mcpedl.org/uploads_files/16-09-2026/minecraft-26-60-24.apk'\">"
            + "      Download minecraft-26-60-24.apk</button>"
            + "  </div>"
            + "</body></html>";

    @Test
    public void extractsVersionsFromTheIndex() {
        List<Version> versions = source.parseListing(LISTING_HTML);

        assertEquals(3, versions.size());
        assertEquals("https://mcpedl.org/minecraft-pe-26-60-24-apk/", versions.get(0).pageUrl);
        assertEquals("26.60.24", versions.get(0).versionCode);
        assertEquals("https://mcpedl.org/minecraft-pe-26-51-apk/", versions.get(1).pageUrl);
        assertEquals("26.51", versions.get(1).versionCode);
    }

    /**
     * The index links its own categories beside the version cards. Treating
     * {@code /downloading/minecraft-pe-26/} as a version would offer the user a category
     * page whose "download" button is not a package at all.
     */
    @Test
    public void ignoresCategoryLinksOnTheIndex() {
        List<Version> versions = source.parseListing(LISTING_HTML);

        for (Version version : versions) {
            assertFalse(version.pageUrl.contains("/downloading/"));
        }
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
            assertTrue(version.pageUrl.startsWith("https://mcpedl.org/minecraft-pe-"));
        }
        assertTrue(versions.stream().anyMatch(v -> v.pageUrl.endsWith("/minecraft-pe-26-50-apk/")));
    }

    @Test
    public void extractsEveryBuildFormOnAVersionPage() {
        List<DownloadForm> forms = source.parseVersionPage(VERSION_HTML);

        assertEquals(3, forms.size());
        assertEquals("https://mcpedl.org/show_file.php", forms.get(0).actionUrl);
        assertEquals("Xbox+servers, no music", forms.get(0).label);
        assertEquals("Download(380 Mb)", forms.get(0).sizeLabel);
    }

    @Test
    public void keepsTheHiddenFieldsNeededToSubmit() {
        List<DownloadForm> forms = source.parseVersionPage(VERSION_HTML);

        DownloadForm first = forms.get(0);
        assertEquals("Minecraft 26.60.24 (1.26.60.24)", field(first, "post_title"));
        assertEquals("7549", field(first, "file_id"));
        assertEquals("https://mcpedl.org/minecraft-pe-26-60-24-apk/", field(first, "post_url"));
    }

    /**
     * The launcher ships arm64 only, so a 32-bit build cannot be loaded. Picking it would
     * hand the user a package that fails the ABI preflight at launch.
     */
    @Test
    public void prefersABuildThatIsNot32BitOnly() {
        List<DownloadForm> forms = source.parseVersionPage(VERSION_HTML);
        DownloadForm chosen = BedrockSource.selectDownload(forms);

        assertNotNull(chosen);
        assertEquals("7549", field(chosen, "file_id"));
        assertFalse(chosen.is32BitOnly());
    }

    /**
     * MCPEDL now labels the ABI-specific builds, and on those pages the unlabelled default can
     * be the 32-bit one. Picking "the first build that is not 32-bit-only" therefore chose the
     * unlabelled 32-bit package and failed the ABI preflight even though a correctly-labelled
     * arm64 build sat on the same page. An explicit arm64 label must win.
     */
    @Test
    public void prefersAnExplicitlyLabelledArm64Build() {
        String html = ""
                + "<table><tbody>"
                + "  <tr><td>Xbox+servers, no music</td><td>"
                + "    <form method=\"post\" action=\"/show_file.php\">"
                + "      <input type=\"hidden\" name=\"file_id\" value=\"7521\"></form></td></tr>"
                + "  <tr><td>arm64-v8a. Xbox+servers, no music</td><td>"
                + "    <form method=\"post\" action=\"/show_file.php\">"
                + "      <input type=\"hidden\" name=\"file_id\" value=\"7523\"></form></td></tr>"
                + "  <tr><td>armv7a. Xbox+servers, no music</td><td>"
                + "    <form method=\"post\" action=\"/show_file.php\">"
                + "      <input type=\"hidden\" name=\"file_id\" value=\"7522\"></form></td></tr>"
                + "</tbody></table>";
        DownloadForm chosen = BedrockSource.selectDownload(source.parseVersionPage(html));

        assertNotNull(chosen);
        assertEquals("7523", field(chosen, "file_id"));
        assertEquals("arm64-v8a. Xbox+servers, no music", chosen.label);
    }

    @Test
    public void recognisesA64BitBuildLabel() {
        assertTrue(BedrockSource.is64BitLabel("arm64-v8a. Xbox+servers, +music"));
        assertTrue(BedrockSource.is64BitLabel("aarch64 build"));
        assertFalse(BedrockSource.is64BitLabel("armv7a. Xbox+servers"));
        assertFalse(BedrockSource.is64BitLabel("Xbox+servers, no music"));
        assertFalse(BedrockSource.is64BitLabel(null));
    }

    /** An unlabelled default is still taken when no build is explicitly marked arm64. */
    @Test
    public void fallsBackToAnUnlabelledBuildWhenNoArm64LabelExists() {
        List<DownloadForm> forms = source.parseVersionPage(VERSION_HTML);
        DownloadForm chosen = BedrockSource.selectDownload(forms);

        assertEquals("7549", field(chosen, "file_id"));
        assertEquals("Xbox+servers, no music", chosen.label);
    }

    @Test
    public void recognisesA32BitBuildLabel() {
        List<DownloadForm> forms = source.parseVersionPage(VERSION_HTML);

        assertFalse(forms.get(0).is32BitOnly());
        assertFalse(forms.get(1).is32BitOnly());
        assertTrue(forms.get(2).is32BitOnly());
    }

    @Test
    public void fallsBackToTheOnlyBuildWhenEveryBuildIs32Bit() {
        String html = VERSION_HTML.replace("Xbox+servers, no music", "armv7a only")
                .replace("Xbox+servers, +music", "armv7a");
        DownloadForm chosen = BedrockSource.selectDownload(source.parseVersionPage(html));

        assertNotNull("a 32-bit-only version still resolves, so the caller can explain", chosen);
        assertTrue(chosen.is32BitOnly());
    }

    /** The package URL is only revealed after the form is posted, inside a script. */
    @Test
    public void readsThePackageUrlFromTheFormResponse() {
        String url = source.parseResolvedUrl(RESOLVED_HTML);

        assertEquals("https://file.mcpedl.org/uploads_files/16-09-2026/minecraft-26-60-24.apk", url);
    }

    @Test
    public void fallsBackToADirectLinkWhenTheResponseHasNoScript() {
        String html = "<html><body>"
                + "<a href=\"https://file.mcpedl.org/uploads_files/x/minecraft-26-50.apk\">here</a>"
                + "</body></html>";

        assertEquals("https://file.mcpedl.org/uploads_files/x/minecraft-26-50.apk",
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
        ResolvedDownload download = new ResolvedDownload(url, BedrockSource.fileNameFromUrl(url));

        assertEquals("minecraft-26-60-24.apk", download.fileName);
        assertTrue(BedrockSource.isPackageFileName(download.fileName));
    }

    /**
     * The CDN serves {@code application/octet-stream} with no {@code Content-Disposition},
     * so the URL segment is the only naming source; a header must win when one is present.
     */
    @Test
    public void prefersAContentDispositionNameOverTheUrl() {
        assertEquals("custom-name.apk",
                BedrockSource.fileNameFromContentDisposition("attachment; filename=\"custom-name.apk\""));
        assertEquals("custom-name.xapk",
                BedrockSource.fileNameFromContentDisposition("attachment; filename=custom-name.xapk"));
        assertNull(BedrockSource.fileNameFromContentDisposition(null));
        assertNull(BedrockSource.fileNameFromContentDisposition("attachment"));
    }

    @Test
    public void derivesAnInstanceNameFromAVersionCode() {
        assertEquals("26.60.24", BedrockSource.versionNameFrom("Minecraft 26.60.24 (1.26.60.24)"));
        assertEquals("26.60.24", BedrockSource.versionNameFrom("minecraft-26-60-24.apk"));
        assertEquals("unknown", BedrockSource.versionNameFrom(""));
        assertEquals("unknown", BedrockSource.versionNameFrom(null));
    }

    @Test
    public void detectsABrowserChallengePage() {
        assertTrue(BedrockSource.looksLikeChallenge(
                "<html><head><title>Just a moment...</title></head></html>"));
        assertTrue(BedrockSource.looksLikeChallenge("<div id=\"cf_chl_opt\"></div>"));
        assertFalse(BedrockSource.looksLikeChallenge(LISTING_HTML));
    }

    @Test
    public void namesItsHostAndListingUrl() {
        assertEquals("mcpedl.org", source.displayHost());
        assertEquals("https://mcpedl.org/downloading/", source.listingUrl());
        assertEquals("mcpedl", source.id());
    }

    /**
     * The archive is 43 pages deep and each page holds ten entries. The next-page link is what
     * lets the screen walk past the newest handful and reach older releases such as 1.21.132.
     */
    @Test
    public void readsTheNextPageLinkFromPagination() {
        String html = "<nav><a rel=\"prev\" href=\"https://mcpedl.org/downloading/page/1/\">"
                + "Previous</a>"
                + "<a rel=\"next\" href=\"https://mcpedl.org/downloading/page/3/\">Next</a></nav>";

        assertEquals("https://mcpedl.org/downloading/page/3/", source.nextListingUrl(html));
    }

    /** The last page has no next link, which must read as "stop", not as an error. */
    @Test
    public void reportsNoNextPageOnTheLastPage() {
        String html = "<nav><a rel=\"prev\" href=\"/downloading/page/42/\">Back</a></nav>";

        assertNull(source.nextListingUrl(html));
        assertNull(source.nextListingUrl(""));
        assertNull(source.nextListingUrl(null));
    }

    /** A site-relative next link must still be followed. */
    @Test
    public void resolvesARelativeNextPageLink() {
        String html = "<a class=\"next\" href=\"/downloading/page/8/\">Next</a>";

        assertEquals("https://mcpedl.org/downloading/page/8/", source.nextListingUrl(html));
    }

    /**
     * Every listing page repeats the newest handful of versions in its sidebar menu. Those
     * links belong to an earlier page, so counting them as entries would re-offer the newest
     * releases while the user pages into the archive, and they would never disappear.
     */
    @Test
    public void ignoresSidebarVersionLinksOnAListingPage() {
        String html = ""
                + "<div class=\"g-tagmenu menu4\">"
                + "  <a href=\"/minecraft-pe-26-60-28-apk/\" class=\"button g-tagmenu-item\">26.60.28</a>"
                + "  <a href=\"/minecraft-pe-26-51-apk/\" class=\"button g-tagmenu-item\">26.51</a>"
                + "</div>"
                + "<article><div class=\"entry-title\">"
                + "  <a href=\"/minecraft-pe-1-21-132-apk/\" title=\"Download Minecraft 1.21.132\">"
                + "    Download Minecraft 1.21.132</a></div></article>";

        List<Version> versions = source.parseListing(html);

        assertEquals(1, versions.size());
        assertEquals("https://mcpedl.org/minecraft-pe-1-21-132-apk/", versions.get(0).pageUrl);
    }

    /** The registry is what the UI reads, so it must resolve to the MCPEDL source. */
    @Test
    public void registryExposesTheMcpedlSourceAsActive() {
        assertEquals("mcpedl", SourceRegistry.active().id());
        assertEquals("mcpedl", SourceRegistry.byId("mcpedl").id());
        assertEquals("mcpedl", SourceRegistry.byId("no-such-source").id());
        assertTrue(SourceRegistry.all().size() >= 1);
    }

    private static String field(DownloadForm form, String name) {
        for (BedrockSource.Field field : form.fields) {
            if (name.equals(field.name)) return field.value;
        }
        return null;
    }
}
