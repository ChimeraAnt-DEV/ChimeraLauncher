package org.chimeramc.launcher.core.mods;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Crash-loop guard for native mod loading.
 *
 * A native mod that crashes the process does it while the loader is still running, so no
 * in-memory bookkeeping survives to tell the next launch which mod was to blame. This store
 * keeps the answer on disk, updated as each mod loads:
 *
 * <ol>
 *   <li>Before loading starts, {@link #beginLaunch} records the enabled mods as the launch
 *       that is now in progress, with an empty "loaded" set.</li>
 *   <li>Each time a mod finishes loading, {@link #markModLoaded} adds it. The set on disk is
 *       therefore exactly "the mods that were live when the process died".</li>
 *   <li>Once the game runtime is actually up, {@link #completeLaunch} clears the marker. A
 *       marker still present at the start of the next process means the previous launch
 *       crashed.</li>
 * </ol>
 *
 * Writes are tiny (a handful of mod ids) and only happen on the launch path, so the cost is
 * not on the input hot path.
 */
public final class ModSafeMode {

    private static final String TAG = "ModSafeMode";
    private static final String PREFS_NAME = "mod_safe_mode";
    private static final String KEY_LAUNCH_IN_PROGRESS = "launch_in_progress";
    private static final String KEY_LOADED_MODS = "loaded_mods";
    private static final String KEY_LAST_CRASH_TIME = "last_crash_time";
    private static final String KEY_SAFE_MODE_ACTIVE = "safe_mode_active";
    private static final char SEPARATOR = '\n';

    private ModSafeMode() {
    }

    /** Marks the start of a launch that will load the given enabled mods. */
    public static void beginLaunch(Context context, Collection<String> enabledModIds) {
        if (context == null) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        Set<String> enabled = sanitize(enabledModIds);
        prefs.edit()
                .putBoolean(KEY_LAUNCH_IN_PROGRESS, !enabled.isEmpty())
                .putString(KEY_LOADED_MODS, join(Collections.<String>emptySet()))
                .apply();
    }

    /** Records that a mod finished loading and was live when the process might die. */
    public static void markModLoaded(Context context, String modId) {
        if (context == null || modId == null || modId.isEmpty()) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        if (!prefs.getBoolean(KEY_LAUNCH_IN_PROGRESS, false)) {
            return;
        }
        Set<String> loaded = readMods(prefs);
        if (!loaded.add(modId)) {
            return;
        }
        prefs.edit().putString(KEY_LOADED_MODS, join(loaded)).apply();
    }

    /** Clears the in-progress marker once the game runtime is confirmed up. */
    public static void completeLaunch(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit()
                .putBoolean(KEY_LAUNCH_IN_PROGRESS, false)
                .putString(KEY_LOADED_MODS, "")
                .apply();
    }

    /** True when the previous launch started loading mods and never finished starting up. */
    public static boolean hasCrashLoop(Context context) {
        if (context == null) {
            return false;
        }
        SharedPreferences prefs = prefs(context);
        return prefs.getBoolean(KEY_LAUNCH_IN_PROGRESS, false);
    }

    /** The mods that were already loaded when the previous launch crashed. Never null. */
    public static List<String> suspectModIds(Context context) {
        if (context == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(readMods(prefs(context)));
    }

    /** Records the user's decision so the same crash prompt is not shown again. */
    public static void acknowledgeCrash(Context context, boolean disabledSuspects) {
        if (context == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs(context).edit()
                .putBoolean(KEY_LAUNCH_IN_PROGRESS, false)
                .putString(KEY_LOADED_MODS, "")
                .putBoolean(KEY_SAFE_MODE_ACTIVE, disabledSuspects)
                .putLong(KEY_LAST_CRASH_TIME, System.currentTimeMillis());
        editor.apply();
    }

    /** True when the user chose to keep suspect mods disabled after a crash. */
    public static boolean isSafeModeActive(Context context) {
        if (context == null) {
            return false;
        }
        return prefs(context).getBoolean(KEY_SAFE_MODE_ACTIVE, false);
    }

    /** Leaves safe mode once the user has re-enabled mods deliberately. */
    public static void exitSafeMode(Context context) {
        if (context == null) {
            return;
        }
        prefs(context).edit().putBoolean(KEY_SAFE_MODE_ACTIVE, false).apply();
    }

    public static long getLastCrashTime(Context context) {
        if (context == null) {
            return 0L;
        }
        return prefs(context).getLong(KEY_LAST_CRASH_TIME, 0L);
    }

    private static Set<String> readMods(SharedPreferences prefs) {
        String raw = prefs.getString(KEY_LOADED_MODS, "");
        Set<String> mods = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) {
            return mods;
        }
        for (String part : raw.split(String.valueOf(SEPARATOR))) {
            if (!part.isEmpty()) {
                mods.add(part);
            }
        }
        return mods;
    }

    private static Set<String> sanitize(Collection<String> modIds) {
        Set<String> mods = new LinkedHashSet<>();
        if (modIds == null) {
            return mods;
        }
        for (String modId : modIds) {
            if (modId != null && !modId.isEmpty()) {
                mods.add(modId);
            }
        }
        return mods;
    }

    private static String join(Set<String> mods) {
        StringBuilder builder = new StringBuilder();
        for (String mod : mods) {
            if (builder.length() > 0) {
                builder.append(SEPARATOR);
            }
            builder.append(mod);
        }
        return builder.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
