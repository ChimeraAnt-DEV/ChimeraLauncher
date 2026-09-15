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
 */
public final class UiTouchFeedback {
    private UiTouchFeedback() {}

    /** Tick-style haptic for card/row presses. */
    public static void press(Context context) {
        vibe(context, 12);
    }

    /** Firm confirmation tick for launches / toggles. */
    public static void confirm(Context context) {
        vibe(context, 26);
    }

    private static void vibe(Context context, int ms) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Throwable ignored) {
            // Haptics are cosmetic; never crash on a broken vibrator.
        }
    }

    /** Fallback view-based haptics for views without a context reference. */
    public static void pressView(View view) {
        if (view == null) return;
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Throwable ignored) {
        }
    }
}