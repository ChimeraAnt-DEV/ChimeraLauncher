package org.chimeramc.launcher.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GameBundleTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File writeZip(String name, String... entries) throws Exception {
        File file = new File(tmp.getRoot(), name);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) {
                zos.putNextEntry(new ZipEntry(entry));
                if (entry.endsWith(".so") || entry.endsWith(".apk")) {
                    zos.write(new byte[] {1, 2, 3});
                }
                zos.closeEntry();
            }
        }
        return file;
    }

    @Test
    public void bundleExtensionsAreRecognised() {
        assertTrue(GameBundle.isBundle("Minecraft_1.20.80.apks"));
        assertTrue(GameBundle.isBundle("Minecraft_1.20.80.xapk"));
        assertTrue(GameBundle.isBundle("Minecraft_1.20.80.XAPK"));
        assertTrue(GameBundle.isBundle("Minecraft_1.20.80.apkm"));
        assertFalse(GameBundle.isBundle("Minecraft_1.20.80.apk"));
        assertFalse(GameBundle.isBundle("Minecraft_1.20.80.zip"));
        assertFalse(GameBundle.isBundle(null));
    }

    @Test
    public void apkEntriesAreRecognised() {
        assertTrue(GameBundle.isApkEntry("base.apk"));
        assertTrue(GameBundle.isApkEntry("splits/config.arm64_v8a.apk"));
        assertFalse(GameBundle.isApkEntry("manifest.json"));
        assertFalse(GameBundle.isApkEntry("icon.png"));
    }

    /**
     * The regression that made XAPK imports fail: bundles name the base APK after the
     * package, not {@code base.apk}. Missing it meant no base was found, and the native
     * libraries that decide bitness were never seen.
     */
    @Test
    public void baseEntryCoversApksAndXapkNaming() {
        assertTrue(GameBundle.isBaseEntry("base.apk"));
        assertTrue(GameBundle.isBaseEntry("com.mojang.minecraftpe.apk"));
        assertTrue(GameBundle.isBaseEntry("splits/com.mojang.minecraftpe.apk"));
        assertFalse(GameBundle.isBaseEntry("splits/config.arm64_v8a.apk"));
        assertFalse(GameBundle.isBaseEntry("manifest.json"));
    }

    @Test
    public void minecraftApkIsDetectedByItsNativeLibrary() throws Exception {
        File mc = writeZip("mc.apk", "AndroidManifest.xml", "lib/arm64-v8a/libminecraftpe.so");
        File other = writeZip("other.apk", "AndroidManifest.xml", "lib/arm64-v8a/libother.so");
        assertTrue(GameBundle.looksLikeMinecraftApk(mc));
        assertFalse(GameBundle.looksLikeMinecraftApk(other));
        assertFalse(GameBundle.looksLikeMinecraftApk(new File(tmp.getRoot(), "missing.apk")));
    }

    @Test
    public void versionNameFallsBackToFileName() {
        assertEquals("1.17.0.50", GameBundle.versionNameFromFileName("Minecraft_1.17.0.50.apk"));
        assertEquals("1.20.80", GameBundle.versionNameFromFileName("Minecraft-1.20.80.apks"));
        assertEquals("1.21.0.1", GameBundle.versionNameFromFileName("com.mojang.minecraftpe_1.21.0.1.apk"));
        assertEquals("unknown", GameBundle.versionNameFromFileName("minecraft.apk"));
        assertEquals("unknown", GameBundle.versionNameFromFileName(null));
    }
}