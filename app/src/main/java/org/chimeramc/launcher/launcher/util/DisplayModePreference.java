package org.chimeramc.launcher.util;

import java.util.List;

/**
 * Chooses a display mode for gameplay on a high-refresh-rate panel.
 *
 * The naive version of this picks the fastest mode the panel advertises and assigns it to
 * {@code WindowManager.LayoutParams.preferredDisplayModeId}, which on many panels silently
 * also changes the resolution: phone panels expose high-refresh modes at reduced resolution,
 * so "unlocking 120Hz" can hand the player a blurrier picture. A frame-rate win that costs
 * sharpness is not a win, and the user has no way to tell that is what happened.
 *
 * So this only ever selects a mode that keeps the resolution the device is already using and
 * raises the refresh rate. If there is no such mode, it returns {@link #NO_MODE} and the
 * caller leaves the window alone.
 *
 * The logic is plain records and arithmetic, so it can be unit tested without a display.
 */
public final class DisplayModePreference {

    /** Returned when no mode should be applied. */
    public static final int NO_MODE = -1;

    /** Below this a mode is not worth requesting: 60Hz panels should keep their own mode. */
    public static final float MIN_REFRESH_RATE_HZ = 90f;

    private DisplayModePreference() {
    }

    /** A display mode reduced to the fields this decision needs. */
    public static final class Mode {
        public final int id;
        public final float refreshRate;
        public final int width;
        public final int height;

        public Mode(int id, float refreshRate, int width, int height) {
            this.id = id;
            this.refreshRate = refreshRate;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Picks the fastest same-resolution mode at or above {@link #MIN_REFRESH_RATE_HZ}.
     *
     * @param modes          every mode the display advertises; may be null or empty
     * @param currentWidth   width of the mode the device is currently using
     * @param currentHeight  height of the mode the device is currently using
     * @return the chosen mode id, or {@link #NO_MODE} to keep the current mode
     */
    public static int selectHighRefreshModeId(List<Mode> modes, int currentWidth, int currentHeight) {
        if (modes == null || modes.isEmpty()) return NO_MODE;
        if (currentWidth <= 0 || currentHeight <= 0) return NO_MODE;

        int bestId = NO_MODE;
        float bestRate = 0f;
        for (Mode mode : modes) {
            if (mode == null) continue;
            if (mode.width != currentWidth || mode.height != currentHeight) continue;
            if (mode.refreshRate < MIN_REFRESH_RATE_HZ) continue;
            if (mode.refreshRate > bestRate) {
                bestRate = mode.refreshRate;
                bestId = mode.id;
            }
        }
        return bestId;
    }
}
