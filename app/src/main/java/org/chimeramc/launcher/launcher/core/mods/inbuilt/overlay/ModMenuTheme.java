package org.chimeramc.launcher.core.mods.inbuilt.overlay;

import android.content.Context;
import android.graphics.Color;

import org.chimeramc.launcher.util.PersonalizationManager;

/**
 * Colour decisions for the in-game mod menu.
 *
 * The overlay draws itself, so it cannot inherit the launcher theme the way an Activity does.
 * This resolves the user's accent colour once (honouring PersonalizationManager) and derives a
 * stable per-group hue from the group id, which is what makes the list feel colour-coded
 * instead of uniformly green.
 */
final class ModMenuTheme {

    /** Fallback accent, matching the launcher's default primary. */
    static final int DEFAULT_ACCENT = 0xFF6236E8;

    private static final int[] GROUP_PALETTE = {
            0xFF6236E8, // brand violet
            0xFF6D4AFF, // indigo
            0xFFA82E9E, // magenta
            0xFFB4690E, // amber
            0xFFE070C0, // pink
            0xFF4A7CFF, // sky
            0xFF8E2E82, // plum
            0xFF3F2B8C  // deep violet
    };

    private final int accent;
    private final boolean glowEnabled;

    ModMenuTheme(Context context) {
        int resolved = DEFAULT_ACCENT;
        boolean glow = true;
        try {
            PersonalizationManager pm = new PersonalizationManager(context);
            if (pm.hasCustomAccent()) {
                resolved = pm.getAccentColor();
            }
            glow = pm.isEnableGlowEffects();
        } catch (Throwable ignored) {
        }
        this.accent = resolved;
        this.glowEnabled = glow;
    }

    int accent() {
        return accent;
    }

    boolean glowEnabled() {
        return glowEnabled;
    }

    /** Stable colour for a group, so a module's section keeps the same colour across sessions. */
    int groupColor(String groupId) {
        if (groupId == null || groupId.isEmpty()) return accent;
        int hash = 0;
        for (int i = 0; i < groupId.length(); i++) {
            hash = hash * 31 + groupId.charAt(i);
        }
        return GROUP_PALETTE[Math.abs(hash) % GROUP_PALETTE.length];
    }

    /** Slightly lifted card fill tinted toward the accent, for the enabled state. */
    int enabledCardColor() {
        return blend(0xFF24282C, accent, 0.14f);
    }

    int disabledCardColor() {
        return 0xFF24282C;
    }

    /** Translucent accent used for the enabled status pill's fill. */
    int accentFill(int alpha) {
        return Color.argb(alpha, Color.red(accent), Color.green(accent), Color.blue(accent));
    }

    /** Elevation for an enabled card; zero when the user turned glow effects off. */
    float enabledElevation() {
        return glowEnabled ? 6f : 0f;
    }

    float disabledElevation() {
        return glowEnabled ? 2f : 0f;
    }

    private static int blend(int base, int over, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        int r = (int) (Color.red(base) * (1 - a) + Color.red(over) * a);
        int g = (int) (Color.green(base) * (1 - a) + Color.green(over) * a);
        int b = (int) (Color.blue(base) * (1 - a) + Color.blue(over) * a);
        return Color.rgb(r, g, b);
    }
}