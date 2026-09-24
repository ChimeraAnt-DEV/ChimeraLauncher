package org.chimeramc.client.core.downloads;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds Minecraft content the user has already downloaded, so it can be imported without
 * hunting for it in a file picker.
 *
 * The scan is read-only and best-effort: on Android 10+ the public Downloads directory is
 * not a plain directory a normal app can enumerate, so {@link #scan} walks what it is
 * allowed to see and treats an unreadable or missing directory as "nothing found" rather
 * than an error.
 */
public final class DownloadsScanner {

    public static final String EXT_MCPACK = ".mcpack";
    public static final String EXT_MCADDON = ".mcaddon";
    public static final String EXT_MCWORLD = ".mcworld";
    public static final String EXT_MCTEMPLATE = ".mctemplate";
    public static final String EXT_ZIP = ".zip";
    public static final String EXT_APK = ".apk";
    public static final String EXT_XAPK = ".xapk";

    private static final String[] CONTENT_EXTENSIONS = {
            EXT_MCPACK, EXT_MCADDON, EXT_MCWORLD, EXT_MCTEMPLATE, EXT_ZIP
    };

    private static final String[] PACKAGE_EXTENSIONS = {EXT_APK, EXT_XAPK};

    /** How deep below the download root to look; archives are rarely nested further. */
    private static final int MAX_DEPTH = 3;
    private static final int MAX_RESULTS = 200;

    private DownloadsScanner() {
    }

    /**
     * A file the user could import. {@code importable} is false for a file the launcher
     * cannot make sense of, which the UI shows greyed out instead of hiding: a file the
     * user just downloaded being silently absent is worse than one plainly marked.
     */
    public static final class Found implements Comparable<Found> {
        public final File file;
        public final String name;
        public final long size;
        public final long modified;
        public final String extension;
        public final boolean importable;

        Found(File file, String extension, boolean importable) {
            this.file = file;
            this.name = file.getName();
            this.size = file.length();
            this.modified = file.lastModified();
            this.extension = extension;
            this.importable = importable;
        }

        private static final Comparator<Found> ORDER = new Comparator<Found>() {
            @Override
            public int compare(Found a, Found b) {
                if (a.importable != b.importable) {
                    return a.importable ? -1 : 1;
                }
                return Long.compare(b.modified, a.modified);
            }
        };

        @Override
        public int compareTo(Found other) {
            return ORDER.compare(this, other);
        }
    }

    /** The directories a scan looks at, most useful first. */
    public static List<File> scanRoots(Context context) {
        List<File> roots = new ArrayList<>();
        if (context != null) {
            File appDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (appDownloads != null) {
                roots.add(appDownloads);
            }
        }
        try {
            File publicDownloads =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (publicDownloads != null && !roots.contains(publicDownloads)) {
                roots.add(publicDownloads);
            }
        } catch (Throwable ignored) {
            // Some devices/API levels refuse to hand out a public dir path.
        }
        return roots;
    }

    /**
     * Walks every scan root and returns importable files, newest first.
     *
     * Never throws on I/O: a listing that fails simply contributes nothing.
     */
    public static List<Found> scan(Context context) {
        List<Found> results = new ArrayList<>();
        for (File root : scanRoots(context)) {
            collect(root, 0, results);
            if (results.size() >= MAX_RESULTS) {
                break;
            }
        }
        Collections.sort(results);
        if (results.size() > MAX_RESULTS) {
            return new ArrayList<>(results.subList(0, MAX_RESULTS));
        }
        return results;
    }

    /** Only the files the import pipeline can actually consume. */
    public static List<Found> scanImportable(Context context) {
        List<Found> all = scan(context);
        List<Found> filtered = new ArrayList<>();
        for (Found found : all) {
            if (found.importable) {
                filtered.add(found);
            }
        }
        return filtered;
    }

    private static void collect(File dir, int depth, List<Found> out) {
        if (dir == null || depth > MAX_DEPTH || out.size() >= MAX_RESULTS) {
            return;
        }
        File[] children;
        try {
            children = dir.listFiles();
        } catch (Throwable ignored) {
            return;
        }
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (out.size() >= MAX_RESULTS) {
                return;
            }
            String name = child.getName();
            if (name.startsWith(".")) {
                continue;
            }
            boolean isDir;
            try {
                isDir = child.isDirectory();
            } catch (Throwable ignored) {
                continue;
            }
            if (isDir) {
                collect(child, depth + 1, out);
                continue;
            }
            String ext = extensionOf(name);
            boolean importable = isImportableExtension(ext);
            boolean known = importable || isPackageExtension(ext);
            if (known) {
                out.add(new Found(child, ext, importable));
            }
        }
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot).toLowerCase(Locale.US);
    }

    /** True for the Bedrock content archives the import pipeline understands. */
    public static boolean isImportableExtension(String extension) {
        if (extension == null) {
            return false;
        }
        String lower = extension.toLowerCase(Locale.US);
        for (String candidate : CONTENT_EXTENSIONS) {
            if (candidate.equals(lower)) {
                return true;
            }
        }
        return false;
    }

    /** True for Minecraft packages; shown for context but not importable as mods. */
    public static boolean isPackageExtension(String extension) {
        if (extension == null) {
            return false;
        }
        String lower = extension.toLowerCase(Locale.US);
        for (String candidate : PACKAGE_EXTENSIONS) {
            if (candidate.equals(lower)) {
                return true;
            }
        }
        return false;
    }

    /** How the UI names an extension, e.g. "Resource pack" for .mcpack. */
    public static String describeExtension(String extension) {
        if (extension == null) {
            return "";
        }
        switch (extension.toLowerCase(Locale.US)) {
            case EXT_MCPACK:
                return "Resource pack";
            case EXT_MCADDON:
                return "Add-on";
            case EXT_MCWORLD:
                return "World";
            case EXT_MCTEMPLATE:
                return "World template";
            case EXT_ZIP:
                return "Zip archive";
            case EXT_APK:
            case EXT_XAPK:
                return "Minecraft package";
            default:
                return "File";
        }
    }
}
