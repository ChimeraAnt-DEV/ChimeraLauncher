package org.chimeramc.launcher.settings;

import android.content.Context;

import org.chimeramc.launcher.util.DisplayModePreference;

/**
 * One user-facing performance choice that sets several unrelated settings together.
 *
 * The launcher already had a thermal governor, a low-latency network toggle, a display mode
 * selector and an FPS overlay, but each was a separate switch, so "make this run faster" meant
 * knowing which combination of four toggles to flip. This maps a single Battery / Balanced /
 * Performance choice onto them.
 *
 * It is deliberately a thin coordinator and not a new source of truth. Every value it applies
 * is written through the existing {@link FeatureSettings} setters, so the toggles on the
 * Basic settings screen stay in sync and a user who prefers per-toggle control can still use
 * them. Applying a preset is idempotent: the same preset applied twice is the same state.
 *
 * What it will not claim: none of these toggles make the game render faster by themselves.
 * The display mode is the one that can add frames (by selecting a faster refresh mode at the
 * same resolution); the rest reduce work the launcher itself does, which is real but small.
 */
public final class PerformancePresetManager {

    public enum Preset {
        /** Favours battery and quiet: no speculative work, no fast-refresh request. */
        BATTERY,
        /** The default: normal background behaviour, no display mode change. */
        BALANCED,
        /** Favours throughput: chase the fastest same-resolution mode and cut input latency. */
        PERFORMANCE
    }

    private static final String PREFS_NAME = "performance_preset";
    private static final String KEY_PRESET = "preset";

    private PerformancePresetManager() {
    }

    /** Persists the selected preset; the individual settings are stored by FeatureSettings. */
    public static void apply(Context context, Preset preset) {
        if (context == null) return;
        Preset effective = preset == null ? Preset.BALANCED : preset;
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PRESET, effective.name())
                .apply();

        FeatureSettings settings = FeatureSettings.getInstance();
        if (settings == null) return;

        switch (effective) {
            case BATTERY:
                settings.setReduceNetworkLatencyEnabled(false);
                settings.setLowInputDelayEnabled(false);
                break;
            case PERFORMANCE:
                settings.setReduceNetworkLatencyEnabled(true);
                settings.setLowInputDelayEnabled(true);
                break;
            case BALANCED:
            default:
                settings.setLowInputDelayEnabled(false);
                break;
        }
    }

    /** Stored preset, or {@link Preset#BALANCED} when nothing has been chosen yet. */
    public static Preset current(Context context) {
        if (context == null) return Preset.BALANCED;
        String raw = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PRESET, null);
        if (raw == null) return Preset.BALANCED;
        for (Preset preset : Preset.values()) {
            if (preset.name().equals(raw)) return preset;
        }
        return Preset.BALANCED;
    }

    /**
     * Whether a preset should request a high-refresh display mode.
     *
     * Only Performance does. Battery explicitly should not, and Balanced leaves the panel
     * alone so a 60Hz device is not disturbed while a 120Hz device keeps its own choice.
     */
    public static boolean wantsHighRefreshMode(Preset preset) {
        return preset == Preset.PERFORMANCE;
    }

    /**
     * The mode id to request for this preset, or {@link DisplayModePreference#NO_MODE}.
     *
     * Kept separate from {@link DisplayModePreference} so the preset decision ("should we try
     * at all") is testable without a display, while the display decision (which mode is safe)
     * stays where it is.
     */
    public static int displayModeFor(Preset preset, java.util.List<DisplayModePreference.Mode> modes,
                                     int currentWidth, int currentHeight) {
        if (!wantsHighRefreshMode(preset)) return DisplayModePreference.NO_MODE;
        return DisplayModePreference.selectHighRefreshModeId(modes, currentWidth, currentHeight);
    }

    /** A one-line description of what the preset actually changes. */
    public static String describe(Preset preset) {
        switch (preset) {
            case BATTERY:
                return "Reduces launcher background work and leaves the display alone.";
            case PERFORMANCE:
                return "Cuts input delay, keeps launcher sockets warm, and requests the "
                        + "fastest same-resolution refresh mode.";
            case BALANCED:
            default:
                return "Default behaviour: normal background work, no display mode change.";
        }
    }
}
