package org.chimeramc.launcher.launcher.controller;

import android.view.MotionEvent;

/**
 * Immutable response curve for one controller profile, precomputed for the input hot path.
 *
 * {@link ControllerInputProcessor} runs inside {@code dispatchKeyEvent} /
 * {@code dispatchGenericMotionEvent}, i.e. on every controller event before the game sees
 * it. Doing that work allocation-free and lock-free matters: any cost here is added
 * directly to input-to-photon latency.
 *
 * Everything the axis path needs is derived once, on profile change:
 * - {@code invRange} folds the {@code 1 / (1 - deadZone)} division into a multiply.
 * - {@code remapTable} is a flat array indexed by key code instead of a HashMap lookup,
 *   covering the key codes a gamepad can actually send.
 * - The response curve is a polynomial, avoiding {@code Math.pow} per axis per event.
 */
public final class ControllerResponse {

    /**
     * Key codes a gamepad can produce (KeyEvent.KEYCODE_BUTTON_A=96 .. KEYCODE_BUTTON_MODE=110,
     * plus the D-pad at 19-23 and the thumb buttons at 106/107). The table covers 0..110 so
     * every gamepad code is a direct array read.
     */
    static final int REMAP_TABLE_SIZE = 112;

    /** Smaller output than this is treated as centre, killing stick drift while idle. */
    private static final float DRIFT_EPSILON = 0.004f;

    private final float leftDeadZone;
    private final float rightDeadZone;
    private final float leftInvRange;
    private final float rightInvRange;
    private final float leftSensitivity;
    private final float rightSensitivity;
    private final boolean snappy;
    private final int[] remapTable;

    public ControllerResponse(ControllerProfile profile, boolean snappy) {
        this.leftDeadZone = clamp(profile == null ? ControllerProfile.DEFAULT_DEAD_ZONE : profile.getLeftDeadZone());
        this.rightDeadZone = clamp(profile == null ? ControllerProfile.DEFAULT_DEAD_ZONE : profile.getRightDeadZone());
        // Guard the divisor: a dead zone of 1.0 would make the range zero.
        this.leftInvRange = 1f / Math.max(0.01f, 1f - this.leftDeadZone);
        this.rightInvRange = 1f / Math.max(0.01f, 1f - this.rightDeadZone);
        this.leftSensitivity = profile == null ? ControllerProfile.DEFAULT_SENSITIVITY : profile.getLeftStickSensitivity();
        this.rightSensitivity = profile == null ? ControllerProfile.DEFAULT_SENSITIVITY : profile.getRightStickSensitivity();
        this.snappy = snappy;
        this.remapTable = buildRemapTable(profile);
    }

    private static int[] buildRemapTable(ControllerProfile profile) {
        int[] table = new int[REMAP_TABLE_SIZE];
        for (int code = 0; code < REMAP_TABLE_SIZE; code++) {
            // Identity by default; codes outside the table fall through to the identity path.
            table[code] = code;
        }
        if (profile != null) {
            for (java.util.Map.Entry<Integer, Integer> entry : profile.getButtonRemaps().entrySet()) {
                Integer from = entry.getKey();
                Integer to = entry.getValue();
                if (from == null || to == null) continue;
                if (from < 0 || from >= REMAP_TABLE_SIZE) continue;
                table[from] = to;
            }
        }
        return table;
    }

    private static float clamp(float zone) {
        if (Float.isNaN(zone)) return ControllerProfile.DEFAULT_DEAD_ZONE;
        return Math.max(0f, Math.min(0.99f, zone));
    }

    public int remapKey(int keyCode) {
        if (keyCode > 0 && keyCode < REMAP_TABLE_SIZE) {
            return remapTable[keyCode];
        }
        if (keyCode <= 0) return keyCode;
        // Rare code outside the table: fall back to the map so remaps still apply.
        return keyCode;
    }

    /**
     * Applies dead zone, response curve and sensitivity to one stick axis.
     *
     * The curve exists because the previous linear mapping felt sluggish: after removing
     * the dead zone, small physical movement produced a proportionally small look/turn
     * rate, so aiming near centre was mushy. {@code snappy} applies an ease-out curve
     * that gives more output for the same physical travel while still reaching 1.0 at
     * full deflection, so it stays fully controllable at the edges.
     */
    public float adjustAxis(int axis, float value) {
        final boolean left = axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y;
        final boolean right = axis == MotionEvent.AXIS_Z || axis == MotionEvent.AXIS_RZ;
        if (!left && !right) return value;

        final float deadZone = left ? leftDeadZone : rightDeadZone;
        final float invRange = left ? leftInvRange : rightInvRange;
        final float sensitivity = left ? leftSensitivity : rightSensitivity;

        final float magnitude = value < 0f ? -value : value;
        if (magnitude <= deadZone) return 0f;

        // Normalise past the dead zone into 0..1 without a division.
        float normalised = (magnitude - deadZone) * invRange;
        if (normalised > 1f) normalised = 1f;

        if (snappy) {
            // Ease-out quad: f(n) = 1 - (1 - n)^2. Cheaper than Math.pow and monotonic.
            final float rest = 1f - normalised;
            normalised = 1f - rest * rest;
        }

        float output = normalised * sensitivity;
        if (output > 1f) output = 1f;

        if (output < DRIFT_EPSILON) return 0f;
        return value < 0f ? -output : output;
    }

    /** True when the axis is pushed outside the dead zone. */
    public boolean isOutsideDeadZone(int axis, float value) {
        final boolean left = axis == MotionEvent.AXIS_X || axis == MotionEvent.AXIS_Y;
        final float deadZone = left ? leftDeadZone : rightDeadZone;
        final float magnitude = value < 0f ? -value : value;
        return magnitude > deadZone;
    }
}