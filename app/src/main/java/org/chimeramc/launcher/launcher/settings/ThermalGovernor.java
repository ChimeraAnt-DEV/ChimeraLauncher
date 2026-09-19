package org.chimeramc.launcher.settings;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import org.chimeramc.launcher.util.ThermalPolicy;

/**
 * Watches the device's thermal status and pauses non-critical background work while the
 * device is under real pressure, so CPU cycles stay with the game's frame loop.
 *
 * What this does and does not do is worth being precise about. It cannot cool a device, and
 * it has no channel into the game's own renderer. It reduces load the launcher itself owns:
 * news polling, changelog checks, DNS warm-up. User-initiated work (an explicit download)
 * is never paused, because a person waiting on a progress bar is not background work.
 *
 * The listener is only registered on API 29+, where the platform reports thermal status.
 * Below that, {@link #severity()} reads as cool and nothing is throttled.
 */
public final class ThermalGovernor {

    private static volatile Context sAppContext;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final PowerManager.OnThermalStatusChangedListener LISTENER =
            status -> { /* listener only serves to keep the callback wired on API 29+ */ };

    private static volatile int sSeverity = ThermalPolicy.SEVERITY_NONE;
    private static volatile boolean sRegistered;

    private ThermalGovernor() {
    }

    public static void init(Context context) {
        if (context == null) return;
        sAppContext = context.getApplicationContext();
        registerListener();
    }

    private static void registerListener() {
        if (sRegistered) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            PowerManager powerManager =
                    (PowerManager) sAppContext.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) return;
            powerManager.addThermalStatusListener(MAIN::post, LISTENER);
            sRegistered = true;
        } catch (Throwable ignored) {
            // No thermal reporting available; severity stays cool and nothing is held back.
        }
    }

    /**
     * Current thermal severity, re-read on each call.
     *
     * Re-reading rather than caching a pushed value means the launcher reacts even if it
     * missed a listener callback, which happens when the process is backgrounded during the
     * transition. The call is cheap.
     */
    public static int severity() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || sAppContext == null) {
            return ThermalPolicy.SEVERITY_NONE;
        }
        try {
            PowerManager powerManager =
                    (PowerManager) sAppContext.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) return ThermalPolicy.SEVERITY_NONE;
            int severity = ThermalPolicy.severityFor(powerManager.getCurrentThermalStatus());
            sSeverity = severity;
            return severity;
        } catch (Throwable ignored) {
            return sSeverity;
        }
    }

    /** True when speculative work such as DNS warm-up should be skipped. */
    public static boolean shouldPauseSpeculativeWork() {
        return !ThermalPolicy.allowsSpeculativeWork(severity());
    }

    /** True when non-critical background polling should be paused. */
    public static boolean shouldPauseBackgroundWork() {
        return !ThermalPolicy.allowsBackgroundWork(severity());
    }
}
