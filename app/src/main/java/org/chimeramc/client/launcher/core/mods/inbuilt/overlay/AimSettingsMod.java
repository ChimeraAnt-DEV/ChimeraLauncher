package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.os.SystemClock;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * Client-side aim tuning for touch look input.
 *
 * This is an input-shaping module, not a hit-detection one: Bedrock's hit registration is
 * authoritative on the server and cannot be influenced from the client. What this module
 * does control is the quality of the look deltas that reach the game every frame, which is
 * the part a player can actually feel and tune. It:
 *
 *  <ul>
 *    <li>Applies an exponential smoothing filter to camera look deltas, so fast swipes
 *        produce steadier, more readable camera motion instead of juddery skips.</li>
 *    <li>Scales look input by a user-set sensitivity, so a player can dial in their own
 *        feel independently of the in-game option.</li>
 *    <li>Draws a center-screen crosshair so the player has a fixed reference dot.</li>
 *    <li>Emits haptic + visual flash feedback when a swipe burst concludes, confirming
 *        that per-frame input is actually being applied.</li>
 *  </ul>
 */
public final class AimSettingsMod {
    public static final String CFG_SMOOTHING = "aim_smoothing";
    public static final String CFG_CROSSHAIR = "aim_crosshair";
    public static final String CFG_FLASH = "aim_flash";
    public static final String CFG_SENSITIVITY = "aim_sensitivity";
    public static final String CFG_CROSSHAIR_STYLE = "aim_crosshair_style";
    public static final String CFG_CROSSHAIR_COLOR = "aim_crosshair_color";

    /** Reticle shapes, indexed by the config choice value. */
    public static final int STYLE_DOT = 0;
    public static final int STYLE_CROSS = 1;
    public static final int STYLE_CIRCLE = 2;

    private static volatile boolean active;
    private static volatile float smoothingFactor = 0.4f;
    private static volatile float sensitivity = 1.0f;
    private static volatile boolean crosshairEnabled = true;
    private static volatile boolean flashEnabled = true;
    private static volatile int crosshairStyle = STYLE_CROSS;
    private static volatile int crosshairColor = 0xFF3DDC84;

    private static final float[] EMA = new float[2];
    private static long lastInputAt;

    private AimSettingsMod() {}

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            smoothingFactor = clamp(manager.getAimSmoothing() / 100f, 0f, 0.95f);
            sensitivity = clamp(manager.getAimSensitivity() / 100f, 0.1f, 3.0f);
            crosshairEnabled = manager.isAimCrosshairEnabled();
            flashEnabled = manager.isAimFlashEnabled();
            crosshairStyle = manager.getAimCrosshairStyle();
            crosshairColor = manager.getAimCrosshairColor();
            EMA[0] = 0f;
            EMA[1] = 0f;
        }
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (!active || manager == null) return;
        smoothingFactor = clamp(manager.getAimSmoothing() / 100f, 0f, 0.95f);
        sensitivity = clamp(manager.getAimSensitivity() / 100f, 0.1f, 3.0f);
        crosshairEnabled = manager.isAimCrosshairEnabled();
        flashEnabled = manager.isAimFlashEnabled();
        crosshairStyle = manager.getAimCrosshairStyle();
        crosshairColor = manager.getAimCrosshairColor();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isCrosshairEnabled() {
        return active && crosshairEnabled;
    }

    public static boolean isFlashEnabled() {
        return active && flashEnabled;
    }

    public static float getSmoothingFactor() {
        return smoothingFactor;
    }

    public static float getSensitivity() {
        return sensitivity;
    }

    public static int getCrosshairStyle() {
        return crosshairStyle;
    }

    public static int getCrosshairColor() {
        return crosshairColor;
    }

    /**
     * Applies sensitivity scaling and optional smoothing to a look delta.
     *
     * Small swipes (below 0.12 units/frame) skip smoothing entirely so fine aiming stays
     * 1:1; larger swipes are eased so consecutive frames don't skip across a target.
     *
     * @return the adjusted delta pair (flattened into a 2-element array).
     */
    public static float[] smoothLookDelta(float deltaX, float deltaY) {
        if (!active) {
            EMA[0] = 0f;
            EMA[1] = 0f;
            return new float[]{deltaX, deltaY};
        }
        if (sensitivity != 1.0f) {
            deltaX *= sensitivity;
            deltaY *= sensitivity;
        }
        if (smoothingFactor <= 0f) {
            EMA[0] = 0f;
            EMA[1] = 0f;
            return new float[]{deltaX, deltaY};
        }
        long now = SystemClock.uptimeMillis();
        boolean burst = now - lastInputAt < 16L;
        lastInputAt = now;

        float strength = smoothingFactor;
        if (!burst) {
            // A fresh swipe (gap between frames) — reset the filter so the first
            // delta isn't dampened against a stale value.
            EMA[0] = 0f;
            EMA[1] = 0f;
        }
        float magnitude = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (magnitude < 0.12f) {
            // Micro-adjustments stay 1:1 — smoothing must never hurt fine aiming.
            return new float[]{deltaX, deltaY};
        }
        float alpha = 1f - strength;
        EMA[0] = EMA[0] * strength + deltaX * alpha;
        EMA[1] = EMA[1] * strength + deltaY * alpha;
        return new float[]{EMA[0], EMA[1]};
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}