package org.chimeramc.client.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the file-name handling for the Downloaded_APKs staging folder.
 *
 * The names come from a third-party page, so the sanitising rules are the part worth pinning:
 * a name that escapes the staging directory or upsets the filesystem must never reach a path
 * the downloader writes to.
 */
public class DownloadedApksStoreTest {

    @Test
    public void recognisesImportablePackageNames() {
        assertTrue(DownloadedApksStore.isPackageFileName("minecraft.apk"));
        assertTrue(DownloadedApksStore.isPackageFileName("minecraft.xapk"));
        assertTrue(DownloadedApksStore.isPackageFileName("minecraft.apks"));
        assertTrue(DownloadedApksStore.isPackageFileName("minecraft.apkm"));
        assertTrue(DownloadedApksStore.isPackageFileName("MINECRAFT.XAPK"));
    }

    @Test
    public void rejectsNonPackages() {
        assertFalse(DownloadedApksStore.isPackageFileName("poster.png"));
        assertFalse(DownloadedApksStore.isPackageFileName("minecraft.zip"));
        assertFalse(DownloadedApksStore.isPackageFileName(null));
        assertFalse(DownloadedApksStore.isPackageFileName(""));
    }

    @Test
    public void sanitizeKeepsAnOrdinaryFileName() {
        assertEquals("minecraft-26-50-22-arm64-v8a-xbox.apk",
                DownloadedApksStore.sanitizeFileName("minecraft-26-50-22-arm64-v8a-xbox.apk"));
    }

    @Test
    public void sanitizeStripsAnyDirectoryComponent() {
        // A name that tried to climb out of the staging folder must not survive.
        assertEquals("evil.apk", DownloadedApksStore.sanitizeFileName("../../evil.apk"));
        assertEquals("evil.apk", DownloadedApksStore.sanitizeFileName("..\\..\\evil.apk"));
        assertEquals("evil.apk", DownloadedApksStore.sanitizeFileName("/etc/evil.apk"));
    }

    @Test
    public void sanitizeReplacesUnsafeCharacters() {
        String sanitized = DownloadedApksStore.sanitizeFileName("my file (1)*?.apk");
        assertTrue(sanitized.matches("[A-Za-z0-9._-]+"));
        assertTrue(sanitized.endsWith(".apk"));
    }

    @Test
    public void sanitizeNeverProducesALeadingDot() {
        // A leading dot would make the file hidden and the listing would miss it.
        assertFalse(DownloadedApksStore.sanitizeFileName(".hidden.apk").startsWith("."));
        assertFalse(DownloadedApksStore.sanitizeFileName("..").startsWith("."));
    }

    @Test
    public void sanitizeNeverReturnsBlank() {
        assertEquals("package.apk", DownloadedApksStore.sanitizeFileName(null));
        assertEquals("package.apk", DownloadedApksStore.sanitizeFileName(""));
        assertEquals("package.apk", DownloadedApksStore.sanitizeFileName("///"));
    }

    @Test
    public void sanitizeAppendsAnExtensionWhenOneIsMissing() {
        assertEquals("minecraft.apk", DownloadedApksStore.sanitizeFileName("minecraft"));
    }
}
