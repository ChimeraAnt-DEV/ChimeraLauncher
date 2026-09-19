package org.chimeramc.pojavcontrols;

import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

import androidx.core.view.WindowInsetsCompat;

/**
 * Reads the unsafe edges of a view (display cutout, system gesture strip, status/navigation
 * bars) and folds them into a {@link ControlSafeZone}.
 *
 * The cutout and gesture insets are what actually matter here. The controls are drawn edge to
 * edge on purpose, so the ordinary system-bar insets are included too, but only when the bars
 * are visible: in fullscreen immersive mode those insets are zero and the controls may use the
 * whole screen, which is the behaviour players expect.
 *
 * Degrades to {@link ControlSafeZone#full} whenever the platform surface is unavailable, so a
 * device that cannot report insets keeps the previous free placement instead of losing controls
 * behind an assumed cutout.
 */
final class SafeZoneInsets {

    private SafeZoneInsets() {
    }

    static ControlSafeZone forView(View view) {
        if (view == null) return ControlSafeZone.full(0, 0);
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) {
            return ControlSafeZone.full(width, height);
        }
        WindowInsets insets = view.getRootWindowInsets();
        if (insets == null) {
            return ControlSafeZone.full(width, height);
        }
        try {
            int left = 0;
            int top = 0;
            int right = 0;
            int bottom = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets cutout = cutoutInsets(insets);
                Insets gesture = insets.getInsets(WindowInsets.Type.systemGestures());
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                left = Math.max(cutout.left, gesture.left);
                top = Math.max(cutout.top, gesture.top);
                right = Math.max(cutout.right, gesture.right);
                bottom = Math.max(cutout.bottom, gesture.bottom);
                left = Math.max(left, bars.left);
                top = Math.max(top, bars.top);
                right = Math.max(right, bars.right);
                bottom = Math.max(bottom, bars.bottom);
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
                    left = Math.max(left, insets.getDisplayCutout().getSafeInsetLeft());
                    top = Math.max(top, insets.getDisplayCutout().getSafeInsetTop());
                    right = Math.max(right, insets.getDisplayCutout().getSafeInsetRight());
                    bottom = Math.max(bottom, insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
            return ControlSafeZone.of(width, height, left, top, right, bottom);
        } catch (Throwable ignored) {
            return ControlSafeZone.full(width, height);
        }
    }

    /**
     * Cutout insets on API 30+, preferring the compat backport so devices whose vendor ROM
     * only reports the cutout through the compat path still clamp correctly.
     */
    private static Insets cutoutInsets(WindowInsets insets) {
        try {
            WindowInsetsCompat compat = WindowInsetsCompat.toWindowInsetsCompat(insets);
            androidx.core.graphics.Insets compatCutout =
                    compat.getInsets(WindowInsetsCompat.Type.displayCutout());
            return Insets.of(compatCutout.left, compatCutout.top, compatCutout.right, compatCutout.bottom);
        } catch (Throwable ignored) {
            Insets nativeCutout = insets.getInsets(WindowInsets.Type.displayCutout());
            return nativeCutout != null ? nativeCutout : Insets.NONE;
        }
    }
}
