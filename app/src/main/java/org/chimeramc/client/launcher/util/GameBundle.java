package org.chimeramc.client.util;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Pure helpers for the archive layouts Minecraft Bedrock is distributed in: a plain
 * {@code .apk}, or a bundle ({@code .apks} / {@code .xapk} / {@code .apkm}) wrapping one or
 * more APKs.
 *
 * Deliberately free of Android APIs so import preflight logic can be unit tested on the JVM.
 */
public final class GameBundle {

    private GameBundle() {
    }

    private static final String BASE_ENTRY = "base.apk";
    private static final String XAPK_BASE_ENTRY = "com.mojang.minecraftpe.apk";
    private static final String LIB_PREFIX = "lib/";
    private static final String LIB_SUFFIX = "/libminecraftpe.so";

    /** Archive extensions that wrap one or more APKs. */
    public static boolean isBundle(String fileName) {
        if (fileName == null) return false;
        String name = fileName.toLowerCase();
        return name.endsWith(".apks") || name.endsWith(".xapk") || name.endsWith(".apkm");
    }

    /** True for an archive entry that is itself an APK. */
    public static boolean isApkEntry(String entryName) {
        return entryName != null && entryName.toLowerCase().endsWith(".apk");
    }

    /**
     * True for the entry holding the Minecraft base APK.
     *
     * {@code .apks} bundles call it {@code base.apk}, while XAPK bundles name it after the
     * package. The native libraries that decide bitness live in this entry, so treating a
     * split as the base (or vice versa) makes every later ABI decision wrong.
     */
    public static boolean isBaseEntry(String entryName) {
        if (entryName == null) return false;
        String base = entryName.substring(entryName.lastIndexOf('/') + 1);
        return base.equalsIgnoreCase(BASE_ENTRY) || base.equalsIgnoreCase(XAPK_BASE_ENTRY);
    }

    /**
     * True when {@code apk} carries Minecraft's own native library.
     *
     * This identifies a Minecraft package when its manifest cannot be read, which some
     * older or re-signed builds do not allow.
     */
    public static boolean looksLikeMinecraftApk(File apk) {
        if (apk == null || !apk.isFile()) return false;
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(LIB_PREFIX) && name.endsWith(LIB_SUFFIX)) return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }

    /**
     * Best-effort version name from a file name, used when a package's manifest yields none.
     * Takes the first dotted-number run: {@code Minecraft_1.17.0.50.apk} -> {@code 1.17.0.50}.
     */
    public static String versionNameFromFileName(String fileName) {
        if (fileName == null) return "unknown";
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)+)").matcher(fileName);
        return matcher.find() ? matcher.group(1) : "unknown";
    }
}