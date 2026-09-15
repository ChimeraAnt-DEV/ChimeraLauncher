package org.chimeramc.launcher.core.mods.inbuilt.overlay;

import android.os.SystemClock;

import org.chimeramc.launcher.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * Client-side hit-registration assist for Bedrock servers.
 *
 * Bedrock's authoritative hit detection runs server-side at a coarse tick cadence, so a
 * quick swipe can skip a target entirely on the server even though it visually crossed
 * the enemy. This module cannot rewind the server, but it reduces the damage by taming
 * the single most reliable cause of "hits not registering": a camera swipe so fast that
 * the server never sampled a frame on the target. It:
 *
 *  <ul>
 *    <li>Applies an exponential smoothing filter to camera look deltas while the player is
 *        swiping, so the client produces steadier frames that stay on target.</li>
 *    <li>Lets the player tune the smoothing strength (percent) from the mod settings.</li>
 *    <li>Provides a center-screen crosshair overlay so the player can keep the aim dot on
 *        the target while swiping.</li>
 *    <li>Provides haptic + visual flash feedback when a look-swiping streak concludes, as a
 *        lightweight confirm that input is being applied every frame.</li>
 *  </ul>
 */
public final class HitRegistrationMod {
    public static final String CFG_SMOOTHING = "hitreg_smoothing";
    public static final String CFG_CROSSHAIR = "hitreg_crosshair";
    public static final String CFG_FLASH = "hitreg_flash";

    private static volatile boolean active;
    private static volatile float smoothingFactor = 0.4f;
    private static volatile boolean crosshairEnabled = true;
    private static volatile boolean flashEnabled = true;

    private static final float[] EMA = new float[2];
    private static long lastInputAt;

    private HitRegistrationMod() {}

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            smoothingFactor = clamp(manager.getHitregSmoothing() / 100f, 0f, 0.95f);
            crosshairEnabled = manager.isHitregCrosshairEnabled();
            flashEnabled = manager.isHitregFlashEnabled();
            EMA[0] = 0f;
            EMA[1] = 0f;
        }
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (!active || manager == null) return;
        smoothingFactor = clamp(manager.getHitregSmoothing() / 100f, 0f, 0.95f);
        crosshairEnabled = manager.isHitregCrosshairEnabled();
        flashEnabled = manager.isHitregFlashEnabled();
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

    /**
     * Applies exponential smoothing to a look delta while hit-registration is active.
     * Small swipes (below 0.12 rad/s equivalent) pass through untouched to keep fine aim
     * precise; larger swipes are eased so the resulting frames don't skip past a target.
     *
     * @return the adjusted delta pair (flattened into a 2-element array).
     */
    public static float[] smoothLookDelta(float deltaX, float deltaY) {
        if (!active || smoothingFactor <= 0f) {
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