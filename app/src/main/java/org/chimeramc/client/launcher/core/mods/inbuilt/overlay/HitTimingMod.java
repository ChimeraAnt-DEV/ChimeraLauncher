package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * State holder for the Select Hit module.
 *
 * <p>The module teaches hit timing: it watches the player's own attack input and tells them
 * when the next click will actually land. See {@link HitTimingSolver} for the timing rules and
 * the honest scope note — this never clicks for the player and cannot make a miss hit.
 */
public final class HitTimingMod {

    private static volatile boolean active;
    private static volatile int cooldownMs = (int) HitTimingSolver.DEFAULT_COOLDOWN_MS;
    private static volatile boolean showCombo = true;
    private static volatile boolean showTimingBar = true;

    private static final HitTimingSolver SOLVER = new HitTimingSolver();

    private HitTimingMod() {}

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            applyConfig(manager.getHitTimingCooldownMs(), manager.isHitTimingShowCombo(),
                    manager.isHitTimingShowTimingBar());
        }
        SOLVER.reset();
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        applyConfig(manager.getHitTimingCooldownMs(), manager.isHitTimingShowCombo(),
                manager.isHitTimingShowTimingBar());
    }

    /** Separate from the manager overload so the timing can be tested without a Context. */
    public static void applyConfig(int cooldown, boolean combo, boolean timingBar) {
        cooldownMs = cooldown;
        showCombo = combo;
        showTimingBar = timingBar;
        SOLVER.setCooldownMs(cooldown);
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isShowCombo() {
        return active && showCombo;
    }

    public static boolean isShowTimingBar() {
        return active && showTimingBar;
    }

    public static int getCooldownMs() {
        return cooldownMs;
    }

    /** Records an attack the player made, at a monotonic timestamp in milliseconds. */
    public static void onAttack(long nowMs) {
        if (!active) return;
        SOLVER.onAttack(nowMs);
    }

    public static HitTimingSolver.Decision evaluate(long nowMs) {
        if (!active) {
            return new HitTimingSolver.Decision(HitTimingSolver.Advice.READY, 0L, 0, 1f);
        }
        return SOLVER.evaluate(nowMs);
    }

    public static int getCombo() {
        return active ? SOLVER.getCombo() : 0;
    }

    public static void reset() {
        SOLVER.reset();
    }
}
