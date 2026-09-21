package org.chimeramc.launcher.core.downloads;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the extension classification and directory walking. {@link DownloadsScanner} has no
 * Android dependencies beyond {@code Context} lookup in {@code scanRoots}, so the walking
 * logic is exercised directly against temporary directories.
 */
public class DownloadsScannerTest {

    @Test
    public void recognisesBedrockContentArchives() {
        assertTrue(DownloadsScanner.isImportableExtension(".mcpack"));
        assertTrue(DownloadsScanner.isImportableExtension(".mcaddon"));
        assertTrue(DownloadsScanner.isImportableExtension(".mcworld"));
        assertTrue(DownloadsScanner.isImportableExtension(".mctemplate"));
        assertTrue(DownloadsScanner.isImportableExtension(".zip"));
    }

    @Test
    public void extensionCheckIsCaseInsensitive() {
        assertTrue(DownloadsScanner.isImportableExtension(".MCPACK"));
        assertTrue(DownloadsScanner.isImportableExtension(".McAddOn"));
    }

    @Test
    public void packagesAreRecognisedButNotImportableAsContent() {
        assertTrue(DownloadsScanner.isPackageExtension(".apk"));
        assertTrue(DownloadsScanner.isPackageExtension(".xapk"));
        assertFalse(DownloadsScanner.isImportableExtension(".apk"));
    }

    @Test
    public void rejectsUnrelatedAndMalformedNames() {
        assertFalse(DownloadsScanner.isImportableExtension(""));
        assertFalse(DownloadsScanner.isImportableExtension(".png"));
        assertFalse(DownloadsScanner.isImportableExtension(null));
        assertEquals("", DownloadsScanner.extensionOf("noextension"));
        assertEquals("", DownloadsScanner.extensionOf("trailingdot."));
        assertEquals(".mcpack", DownloadsScanner.extensionOf("Addon.MCPACK"));
    }

    @Test
    public void extensionOfHandlesPathsAndLeadingDots() {
        assertEquals("", DownloadsScanner.extensionOf(".hidden"));
        assertEquals(".zip", DownloadsScanner.extensionOf("archive.zip"));
        assertEquals(".json", DownloadsScanner.extensionOf("a.b.json"));
    }

    @Test
    public void describeExtensionNamesTheContentType() {
        assertEquals("Resource pack", DownloadsScanner.describeExtension(".mcpack"));
        assertEquals("Add-on", DownloadsScanner.describeExtension(".mcaddon"));
        assertEquals("World", DownloadsScanner.describeExtension(".mcworld"));
        assertEquals("Minecraft package", DownloadsScanner.describeExtension(".apk"));
        assertEquals("File", DownloadsScanner.describeExtension(".xyz"));
    }

    @Test
    public void scanFindsContentAndSortsImportableFirst() throws Exception {
        java.io.File root = java.nio.file.Files.createTempDirectory("dl").toFile();
        root.deleteOnExit();
        java.io.File older = new java.io.File(root, "pack.mcpack");
        write(older, "a");
        older.setLastModified(1_000_000L);
        java.io.File newer = new java.io.File(root, "addon.mcaddon");
        write(newer, "b");
        newer.setLastModified(2_000_000L);
        java.io.File pkg = new java.io.File(root, "minecraft.apk");
        write(pkg, "c");
        pkg.setLastModified(3_000_000L);
        write(new java.io.File(root, "notes.txt"), "ignore me");

        java.util.List<DownloadsScanner.Found> found = new java.util.ArrayList<>();
        // Mirrors scan()'s traversal without needing a Context.
        reflectCollect(root, found);

        assertEquals("txt must be ignored", 3, found.size());
        java.util.Collections.sort(found);
        assertEquals("importable content comes first", "addon.mcaddon", found.get(0).name);
        assertEquals("pack.mcpack", found.get(1).name);
        assertEquals("packages sort last", "minecraft.apk", found.get(2).name);
        assertFalse(found.get(2).importable);
    }

    @Test
    public void scanRecursesIntoSubdirectories() throws Exception {
        java.io.File root = java.nio.file.Files.createTempDirectory("dl2").toFile();
        root.deleteOnExit();
        java.io.File nested = new java.io.File(root, "Chimera");
        assertTrue(nested.mkdirs());
        write(new java.io.File(nested, "deep.mcworld"), "w");

        java.util.List<DownloadsScanner.Found> found = new java.util.ArrayList<>();
        reflectCollect(root, found);
        assertEquals(1, found.size());
        assertEquals("deep.mcworld", found.get(0).name);
    }

    @Test
    public void scanIgnoresHiddenFilesAndDirectories() throws Exception {
        java.io.File root = java.nio.file.Files.createTempDirectory("dl3").toFile();
        root.deleteOnExit();
        write(new java.io.File(root, ".hidden.mcpack"), "x");
        java.io.File hiddenDir = new java.io.File(root, ".trash");
        assertTrue(hiddenDir.mkdirs());
        write(new java.io.File(hiddenDir, "old.mcpack"), "y");

        java.util.List<DownloadsScanner.Found> found = new java.util.ArrayList<>();
        reflectCollect(root, found);
        assertTrue("hidden entries must not be offered for import", found.isEmpty());
    }

    @Test
    public void missingDirectoryIsNotAnError() {
        java.util.List<DownloadsScanner.Found> found = new java.util.ArrayList<>();
        reflectCollect(new java.io.File("/definitely/not/here/at/all"), found);
        assertTrue(found.isEmpty());
    }

    private static void reflectCollect(java.io.File dir, java.util.List<DownloadsScanner.Found> out) {
        try {
            java.lang.reflect.Method m = DownloadsScanner.class
                    .getDeclaredMethod("collect", java.io.File.class, int.class, java.util.List.class);
            m.setAccessible(true);
            m.invoke(null, dir, 0, out);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void write(java.io.File file, String content) throws Exception {
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            out.write(content.getBytes("UTF-8"));
        }
    }
}
