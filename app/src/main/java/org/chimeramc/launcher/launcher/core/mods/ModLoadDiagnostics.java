package org.chimeramc.launcher.core.mods;

import android.content.Context;
import android.util.Log;

import org.chimeramc.launcher.util.LauncherStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Persistent record of why enabled native mods failed to load.
 *
 * The mod loader runs during launch preparation and writes to the launch log, a screen the
 * user has usually already scrolled past by the time the game fails. This store survives the
 * launch so the Mods tab can name the offending mod and the reason afterwards, even if the
 * process was restarted.
 *
 * Parsing and serialization are deliberately free of Android-only APIs (no
 * {@code android.util.JsonReader}, which throws in plain JVM unit tests) so the round trip and
 * the reason classifier can be tested without a device. The format is one escaped,
 * tab-separated record per line:
 * {@code modId \t modName \t version \t kind \t timestampMs \t reason}.
 */
public final class ModLoadDiagnostics {

    private static final String TAG = "ModLoadDiagnostics";
    private static final String RESOURCE_DIR = "resources/preloader";
    private static final String FILE_NAME = "mod_load_diagnostics.txt";
    private static final int MAX_RECORDS = 25;
    private static final char SEPARATOR = '\t';
    private static final int FIELD_COUNT = 6;

    public static final String KIND_INCOMPATIBLE = "incompatible";
    public static final String KIND_MISSING_DEPENDENCY = "missing_dependency";
    public static final String KIND_SYMBOL = "symbol";
    public static final String KIND_DLOPEN = "dlopen";
    public static final String KIND_UNKNOWN = "unknown";

    private ModLoadDiagnostics() {
    }

    /** One failed mod load. Immutable because records are shared with the UI thread. */
    public static final class Record {
        public final String modId;
        public final String modName;
        public final String version;
        public final String reason;
        public final String kind;
        public final long timestampMs;

        public Record(String modId, String modName, String version, String reason, String kind, long timestampMs) {
            this.modId = modId != null ? modId : "";
            this.modName = modName != null ? modName : "";
            this.version = version != null ? version : "";
            this.reason = reason != null ? reason : "";
            this.kind = kind != null ? kind : KIND_UNKNOWN;
            this.timestampMs = timestampMs;
        }
    }

    /** Replaces the recorded failures with the outcome of the launch that just finished. */
    public static void record(Context context, List<Record> records) {
        if (context == null) {
            return;
        }
        if (records == null || records.isEmpty()) {
            clear(context);
            return;
        }
        List<Record> trimmed = records.size() > MAX_RECORDS
                ? new ArrayList<>(records.subList(0, MAX_RECORDS))
                : new ArrayList<>(records);
        if (!org.chimeramc.launcher.util.JsonIOUtils.write(getFile(context), serialize(trimmed))) {
            Log.w(TAG, "Failed to persist mod load diagnostics");
        }
    }

    public static void clear(Context context) {
        if (context == null) {
            return;
        }
        File file = getFile(context);
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to clear mod load diagnostics: " + file.getAbsolutePath());
        }
    }

    public static List<Record> getAll(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }
        return parse(org.chimeramc.launcher.util.JsonIOUtils.read(getFile(context)));
    }

    /** The most recent failure for a mod, or null when it loaded cleanly last launch. */
    public static Record getForMod(Context context, String modId) {
        if (modId == null || modId.isEmpty()) {
            return null;
        }
        for (Record record : getAll(context)) {
            if (modId.equals(record.modId)) {
                return record;
            }
        }
        return null;
    }

    public static boolean hasFailures(Context context) {
        return !getAll(context).isEmpty();
    }

    /**
     * Turns a loader throwable into a short, user-facing reason and a coarse category.
     *
     * The category drives what the UI tells the user to do, so classification matters more
     * than the raw message: a missing symbol means the mod was built for a different game
     * build, a missing dependency means another mod has to be installed, and a dlopen failure
     * means the library could not be opened at all.
     */
    public static final class Classifier {

        private Classifier() {
        }

        public static String describe(Throwable error) {
            if (error == null) {
                return "Mod failed to load";
            }
            String message = error.getMessage();
            if (message == null || message.trim().isEmpty()) {
                return error.getClass().getSimpleName();
            }
            return message.trim();
        }

        public static String kindOf(Throwable error) {
            if (error == null) {
                return KIND_UNKNOWN;
            }
            Throwable root = rootCause(error);
            String message = root.getMessage() == null
                    ? ""
                    : root.getMessage().toLowerCase(Locale.ROOT);

            if (root instanceof UnsatisfiedLinkError) {
                // "symbol not found" also contains "not found", so the symbol test has to come
                // first or every missing-symbol error would be reported as a broken library.
                if (message.contains("symbol")) {
                    return KIND_SYMBOL;
                }
                if (message.contains("dlopen") || message.contains("cannot open")
                        || message.contains("not found")) {
                    return KIND_DLOPEN;
                }
                return KIND_SYMBOL;
            }
            if (message.contains("does not exist") || message.contains("failed to prepare")
                    || message.contains("entry not found") || message.contains("failed to list")) {
                return KIND_MISSING_DEPENDENCY;
            }
            return KIND_UNKNOWN;
        }

        private static Throwable rootCause(Throwable error) {
            Throwable current = error;
            while (current.getCause() != null && current.getCause() != current) {
                current = current.getCause();
            }
            return current;
        }
    }

    static String serialize(List<Record> records) {
        StringBuilder builder = new StringBuilder();
        if (records == null) {
            return "";
        }
        for (Record record : records) {
            builder.append(escape(record.modId)).append(SEPARATOR)
                    .append(escape(record.modName)).append(SEPARATOR)
                    .append(escape(record.version)).append(SEPARATOR)
                    .append(escape(record.kind)).append(SEPARATOR)
                    .append(record.timestampMs).append(SEPARATOR)
                    .append(escape(record.reason)).append('\n');
        }
        return builder.toString();
    }

    static List<Record> parse(String content) {
        List<Record> records = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return records;
        }
        for (String line : content.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            List<String> fields = splitFields(line);
            if (fields.size() < FIELD_COUNT) {
                continue;
            }
            long timestampMs;
            try {
                timestampMs = Long.parseLong(fields.get(4).trim());
            } catch (NumberFormatException e) {
                timestampMs = 0L;
            }
            records.add(new Record(
                    fields.get(0),
                    fields.get(1),
                    fields.get(2),
                    fields.get(5),
                    fields.get(3),
                    timestampMs));
        }
        return records;
    }

    private static List<String> splitFields(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                switch (c) {
                    case 't':
                        current.append('\t');
                        break;
                    case 'n':
                        current.append('\n');
                        break;
                    case 'r':
                        current.append('\r');
                        break;
                    case '\\':
                        current.append('\\');
                        break;
                    default:
                        current.append(c);
                        break;
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == SEPARATOR) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                default:
                    builder.append(c);
                    break;
            }
        }
        return builder.toString();
    }

    private static File getFile(Context context) {
        File dir = new File(LauncherStorage.getAppRoot(context), RESOURCE_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Failed to create diagnostics directory: " + dir.getAbsolutePath());
        }
        return new File(dir, FILE_NAME);
    }
}
