package org.chimeramc.launcher.util;

/**
 * Maps the platform's thermal-pressure signal onto what the launcher should stop doing.
 *
 * Android reports seven levels, but treating them all as distinct would be over-precise:
 * what matters is whether the device is merely warm or genuinely struggling. A device that
 * is warm is normal under gameplay load, so stopping background work the moment the reading
 * leaves NONE would stall news and update checks for most of a session. Two thresholds is
 * the honest granularity.
 *
 * The platform values are duplicated as local constants rather than referenced from
 * {@link android.os.PowerManager} so this decision can be unit tested without depending on
 * the stubbed android.jar carrying the real values.
 */
public final class ThermalPolicy {

    public static final int STATUS_NONE = 0;
    public static final int STATUS_LIGHT = 1;
    public static final int STATUS_MODERATE = 2;
    public static final int STATUS_SEVERE = 3;
    public static final int STATUS_CRITICAL = 4;
    public static final int STATUS_EMERGENCY = 5;
    public static final int STATUS_SHUTDOWN = 6;

    /** Device is cool, or only mildly warm: nothing is held back. */
    public static final int SEVERITY_NONE = 0;
    /** Device is warming up: hold off speculative work, but keep user-initiated work. */
    public static final int SEVERITY_WARM = 1;
    /** Device is under real thermal pressure: pause all non-critical background work. */
    public static final int SEVERITY_HOT = 2;

    private ThermalPolicy() {
    }

    /**
     * Collapses a platform thermal status into a {@code SEVERITY_*} constant.
     * Unknown values are treated as {@link #SEVERITY_NONE} so a future platform level cannot
     * accidentally disable the launcher's background work.
     */
    public static int severityFor(int platformStatus) {
        switch (platformStatus) {
            case STATUS_LIGHT:
            case STATUS_MODERATE:
                return SEVERITY_WARM;
            case STATUS_SEVERE:
            case STATUS_CRITICAL:
            case STATUS_EMERGENCY:
            case STATUS_SHUTDOWN:
                return SEVERITY_HOT;
            default:
                return SEVERITY_NONE;
        }
    }

    /** Speculative work (DNS warm-up, cache refresh) is only worth it on a cool device. */
    public static boolean allowsSpeculativeWork(int severity) {
        return severity == SEVERITY_NONE;
    }

    /**
     * Non-critical background work (news polling, changelog fetch) is paused once the device
     * is hot, but not while merely warm: keeping that work alive on a warm device is why the
     * WARM level exists at all.
     */
    public static boolean allowsBackgroundWork(int severity) {
        return severity < SEVERITY_HOT;
    }

    /** User-initiated work (an explicit download) is never blocked by thermal state. */
    public static boolean allowsUserInitiatedWork(int severity) {
        return true;
    }
}
