package org.chimeramc.client.launcher.controller;

/**
 * Shape of the analogue trigger response, mapping trigger travel to output.
 *
 * Triggers differ from sticks in one important way: they are compared against a single press
 * threshold by the game, so the *curve* mostly changes how quickly the trigger reaches that
 * threshold and how controllable the partial-press range is. The two knobs here are:
 * <ul>
 *   <li>{@code deadZone} — travel before the trigger reports anything. Physical triggers rest
 *       slightly engaged and bounce, so a small floor prevents a resting finger from being
 *       read as a press. This is a real fix for "my gun fires when I am not touching it".</li>
 *   <li>{@code exponent} — shapes the remaining travel. Below 1 boosts (hair-trigger), above 1
 *       requires a deliberate pull.</li>
 * </ul>
 *
 * The mapping is bounded to 0..1 and monotonic, and it deliberately does <em>not</em> force
 * {@code f(1) = 1} through the dead zone divisor the way the stick path does — instead travel
 * at or below the dead zone yields exactly 0, and travel above it is re-normalised so full
 * pull still reaches full output. That keeps both ends correct.
 */
public final class TriggerCurve {

    public static final float DEFAULT_DEAD_ZONE = 0f;
    public static final float DEFAULT_EXPONENT = 1f;
    public static final float MIN_EXPONENT = 0.25f;
    public static final float MAX_EXPONENT = 4f;

    /** Comfortable starting shapes, offered as one-tap presets in the editor. */
    public enum Preset {
        LINEAR("Linear", 0f, 1f),
        SOFT("Soft", 0.02f, 0.75f),
        HAIR_TRIGGER("Hair Trigger", 0f, 0.5f),
        DELIBERATE("Deliberate", 0.12f, 1.8f);

        private final String displayName;
        private final float deadZone;
        private final float exponent;

        Preset(String displayName, float deadZone, float exponent) {
            this.displayName = displayName;
            this.deadZone = deadZone;
            this.exponent = exponent;
        }

        public String getDisplayName() {
            return displayName;
        }

        public TriggerCurve toCurve() {
            return new TriggerCurve(deadZone, exponent);
        }
    }

    private final float deadZone;
    private final float exponent;

    public TriggerCurve() {
        this(DEFAULT_DEAD_ZONE, DEFAULT_EXPONENT);
    }

    public TriggerCurve(float deadZone, float exponent) {
        this.deadZone = clampDeadZone(deadZone);
        this.exponent = clampExponent(exponent);
    }

    public float getDeadZone() {
        return deadZone;
    }

    public float getExponent() {
        return exponent;
    }

    public boolean isIdentity() {
        return deadZone <= 0f && exponent == 1f;
    }

    /** Shapes one raw trigger axis value (0..1 on Android). */
    public float apply(float value) {
        if (!(value > 0f)) return 0f; // also catches NaN
        float v = Math.min(1f, value);
        if (deadZone > 0f) {
            if (v <= deadZone) return 0f;
            v = (v - deadZone) / (1f - deadZone);
        }
        if (exponent == 1f) return v;
        if (exponent == 0.5f) return (float) Math.sqrt(v);
        if (exponent == 2f) return v * v;
        return (float) Math.pow(v, exponent);
    }

    /** Distinct preset matching this curve, or null when it is a custom shape. */
    public Preset matchingPreset() {
        for (Preset preset : Preset.values()) {
            TriggerCurve candidate = preset.toCurve();
            if (Math.abs(candidate.deadZone - deadZone) < 0.001f
                    && Math.abs(candidate.exponent - exponent) < 0.001f) {
                return preset;
            }
        }
        return null;
    }

    public TriggerCurve copy() {
        return new TriggerCurve(deadZone, exponent);
    }

    public static float clampDeadZone(float value) {
        if (Float.isNaN(value)) return DEFAULT_DEAD_ZONE;
        // A dead zone near 1 would leave no usable travel at all.
        return Math.max(0f, Math.min(0.5f, value));
    }

    public static float clampExponent(float value) {
        // Absent fields deserialise to 0f, which must read as the default curve rather than
        // being clamped up into a strong one.
        if (!(value > 0f)) return DEFAULT_EXPONENT;
        return Math.max(0.25f, Math.min(4f, value));
    }

    @Override
    public String toString() {
        return "Trigger(deadZone=" + deadZone + ", exp=" + exponent + ")";
    }
}
