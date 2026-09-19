package org.chimeramc.launcher.ui.animation;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * Haptic + tactile feedback for touch interactions.
 *
 * Deliberately views-contract: never turns into "every tap vibrates aggressively".
 * Light haptics only fire on press; the heavier confirmation ticks fire once per
 * click. All calls are safe to invoke from any thread.
 *
 * On API 29+ the distinct signals use {@link VibrationEffect#createPredefined(int)} so a
 * "tick" and a "buzz" are felt as different things rather than the same motor pulse at two
 * durations. Older devices, and any device that rejects a predefined effect, fall back to a
 * one-shot whose length carries the same intent.
 *
 * The whole layer is gated: haptics are an accessibility-relevant motor action, so
 * {@link #setEnabled(boolean)} lets the user turn every signal off at once, and a device
 * without a vibrator silently does nothing.
 */
public final class UiTouchFeedback {

    /** Predefined effects available from API 29; harmless to reference on older devices. */
    private static final int EFFECT_TICK = VibrationEffect.EFFECT_TICK;
    private static final int EFFECT_CLICK = VibrationEffect.EFFECT_CLICK;
    private static final int EFFECT_HEAVY_CLICK = VibrationEffect.EFFECT_HEAVY_CLICK;

    private static final long TICK_MS = 12;
    private static final long CLICK_MS = 26;
    private static final long REJECT_MS = 45;

    private static volatile boolean sEnabled = true;

    private UiTouchFeedback() {}

    /** Global on/off for every signal this class emits. */
    public static void setEnabled(boolean enabled) {
        sEnabled = enabled;
    }

    public static boolean isEnabled() {
        return sEnabled;
    }

    /** Tick-style haptic for card/row presses. */
    public static void press(Context context) {
        vibe(context, EFFECT_TICK, TICK_MS);
    }

    /** Firm confirmation tick for launches / toggles. */
    public static void confirm(Context context) {
        vibe(context, EFFECT_CLICK, CLICK_MS);
    }

    /**
     * Heavier buzz for a rejected or destructive action, so it cannot be mistaken for the
     * lighter {@link #confirm} tap that a successful one gives.
     */
    public static void reject(Context context) {
        vibe(context, EFFECT_HEAVY_CLICK, REJECT_MS);
    }

    /** Subtle tick for cycling discrete values, such as moving through a list. */
    public static void selection(Context context) {
        vibe(context, EFFECT_TICK, TICK_MS);
    }

    private static void vibe(Context context, int predefined, long fallbackMs) {
        if (!sEnabled) return;
        try {
            if (context == null) return;
            Vibrator vibrator = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                if (manager != null) vibrator = manager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator == null || !vibrator.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    vibrator.vibrate(VibrationEffect.createPredefined(predefined));
                    return;
                } catch (Throwable ignored) {
                    // Some OEM vibrators reject predefined effects; fall through to a one-shot.
                }
                vibrator.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(fallbackMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(fallbackMs);
            }
        } catch (Throwable ignored) {
            // Haptics are cosmetic; never crash on a broken vibrator.
        }
    }

    /** Fallback view-based haptics for views without a context reference. */
    public static void pressView(View view) {
        if (view == null || !sEnabled) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Throwable ignored) {
        }
    }
}