package org.chimeramc.client.util;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The launcher's private staging folder for game packages it downloaded itself.
 *
 * Packages land here before the import pipeline touches them and are removed once the
 * import succeeds, so the folder is a retry area rather than a permanent cache: a failed
 * import must leave the file behind, or the user has to download hundreds of megabytes
 * again to try once more.
 */
public final class DownloadedApksStore {

    public static final String DIRECTORY_NAME = "Downloaded_APKs";

    private final Context context;

    public DownloadedApksStore(Context context) {
        this.context = context.getApplicationContext();
    }

    /** The staging directory, created on first use. */
    public File directory() {
        File dir = new File(context.getFilesDir(), DIRECTORY_NAME);
        if (!dir.exists() && !dir.mkdirs()) {
            // Fall back to the cache dir rather than returning a path that cannot be written.
            File fallback = new File(context.getCacheDir(), DIRECTORY_NAME);
            fallback.mkdirs();
            return fallback;
        }
        return dir;
    }

    /** A destination for [fileName], never overwriting a package already staged. */
    public File newFile(String fileName) {
        File dir = directory();
        File candidate = new File(dir, sanitizeFileName(fileName));
        if (!candidate.exists()) return candidate;

        String name = candidate.getName();
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            File next = new File(dir, base + "_" + i + extension);
            if (!next.exists()) return next;
        }
        return candidate;
    }

    /** Packages currently waiting to be imported, newest first. */
    public List<File> listPackages() {
        File[] files = directory().listFiles();
        if (files == null) return new ArrayList<>();
        List<File> packages = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && isPackageFileName(file.getName())) {
                packages.add(file);
            }
        }
        packages.sort(Comparator.comparingLong(File::lastModified).reversed());
        return packages;
    }

    public boolean delete(File file) {
        return file != null && file.isFile() && file.delete();
    }

    /** True for the archive types the import pipeline can consume. */
    public static boolean isPackageFileName(String fileName) {
        if (fileName == null) return false;
        String name = fileName.toLowerCase(Locale.US);
        return name.endsWith(".apk") || name.endsWith(".xapk")
                || name.endsWith(".apks") || name.endsWith(".apkm");
    }

    /**
     * Reduces a remote file name to something safe to join onto a directory path.
     *
     * The name comes from a third-party page, so anything that could escape the staging
     * directory or upset the filesystem is replaced. An unusable name becomes
     * {@code package.apk} so a download is never silently dropped.
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null) return "package.apk";
        String trimmed = fileName.trim();
        int slash = Math.max(trimmed.lastIndexOf('/'), trimmed.lastIndexOf('\\'));
        if (slash >= 0) trimmed = trimmed.substring(slash + 1);

        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean safe = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            builder.append(safe ? c : '_');
        }

        String sanitized = builder.toString();
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.isEmpty()) return "package.apk";
        if (!isPackageFileName(sanitized)) {
            return sanitized + ".apk";
        }
        return sanitized;
    }

    /** Every staged package, used when the user asks to clear the folder. */
    public int deleteAll() {
        int deleted = 0;
        for (File file : listPackages()) {
            if (delete(file)) deleted++;
        }
        return deleted;
    }
}
