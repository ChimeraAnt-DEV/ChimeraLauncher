package org.chimeramc.client.launcher.controller;

/**
 * Shape of the stick response curve, the mapping from physical stick travel to output.
 *
 * Every curve here satisfies three invariants, which is what makes them safe to drop into a
 * live input path without surprising the player:
 * <ul>
 *   <li>{@code f(0) = 0} and {@code f(1) = 1} — full deflection always reaches full output,
 *       so the curve changes feel near centre without shrinking the usable range.</li>
 *   <li>monotonic non-decreasing — pushing the stick further never produces less output,
 *       which would make fine aiming non-deterministic.</li>
 *   <li>bounded to 0..1 — an axis value outside the range is not a valid event value.</li>
 * </ul>
 *
 * This class holds only the arithmetic. {@link ControllerResponse} bakes it into a lookup
 * table once per profile change, because evaluating a fractional power per axis per event
 * would add {@code Math.pow} to the UI thread on every controller sample.
 */
public final class StickCurve {

    public enum Kind {
        /** Output equals input. No shaping. */
        LINEAR,
        /** {@code f(n) = n^e}: more precision near centre, the classic aim curve. */
        EXPONENTIAL,
        /** {@code f(n) = 1-(1-n)^e}: boosted response, faster turn for the same travel. */
        EASE_OUT,
        /**
         * {@code f(n) = n^e / (n^e + (1-n)^e)}: an S-curve. Suppresses small movements
         * hardest while still ramping steeply through the middle.
         */
        SIGMOID
    }

    public static final float MIN_EXPONENT = 0.5f;
    public static final float MAX_EXPONENT = 4f;

    /** Comfortable starting shapes, offered as one-tap presets in the editor. */
    public enum Preset {
        LINEAR("Linear", Kind.LINEAR, 1f),
        PRECISION("Precision", Kind.EXPONENTIAL, 1.6f),
        BALANCED("Balanced", Kind.EASE_OUT, 1.5f),
        AGGRESSIVE("Aggressive", Kind.SIGMOID, 2.2f),
        INSTANT("Instant", Kind.EASE_OUT, 3.5f);

        private final String displayName;
        private final Kind kind;
        private final float exponent;

        Preset(String displayName, Kind kind, float exponent) {
            this.displayName = displayName;
            this.kind = kind;
            this.exponent = exponent;
        }

        public String getDisplayName() {
            return displayName;
        }

        public StickCurve toCurve() {
            return new StickCurve(kind, exponent);
        }
    }

    private final Kind kind;
    private final float exponent;

    public StickCurve() {
        this(Kind.LINEAR, 1f);
    }

    public StickCurve(Kind kind, float exponent) {
        this.kind = kind == null ? Kind.LINEAR : kind;
        this.exponent = clampExponent(exponent);
    }

    public Kind getKind() {
        return kind;
    }

    public float getExponent() {
        return exponent;
    }

    public boolean isIdentity() {
        return kind == Kind.LINEAR || (exponent == 1f && kind != Kind.SIGMOID);
    }

    /**
     * Shapes one normalised axis value. Input is expected in 0..1; it is clamped rather than
     * rejected, because a value outside the range would otherwise propagate NaN or overshoot.
     */
    public float apply(float normalised) {
        float n = normalised;
        if (!(n > 0f)) return 0f; // also catches NaN
        if (n >= 1f) return 1f;
        switch (kind) {
            case EXPONENTIAL:
                return power(n);
            case EASE_OUT: {
                float rest = 1f - n;
                return 1f - power(rest);
            }
            case SIGMOID: {
                float forward = power(n);
                float backward = power(1f - n);
                float sum = forward + backward;
                return sum <= 0f ? n : forward / sum;
            }
            case LINEAR:
            default:
                return n;
        }
    }

    /**
     * {@code n} raised to this curve's exponent, with the integer cases unrolled so the
     * common presets never reach a general-purpose power routine.
     */
    private float power(float n) {
        if (exponent == 1f) return n;
        if (exponent == 2f) return n * n;
        if (exponent == 3f) return n * n * n;
        if (exponent == 4f) return n * n * n * n;
        if (exponent == 0.5f) return (float) Math.sqrt(n);
        return (float) Math.pow(n, exponent);
    }

    /** Distinct preset matching this curve, or null when it is a custom shape. */
    public Preset matchingPreset() {
        for (Preset preset : Preset.values()) {
            StickCurve candidate = preset.toCurve();
            if (candidate.kind == kind && Math.abs(candidate.exponent - exponent) < 0.001f) {
                return preset;
            }
        }
        return null;
    }

    public StickCurve copy() {
        return new StickCurve(kind, exponent);
    }

    public static float clampExponent(float value) {
        // Absent fields deserialise to 0f, which must read as the default curve rather than
        // being clamped up into a strong one.
        if (!(value > 0f)) return 1f;
        return Math.max(MIN_EXPONENT, Math.min(MAX_EXPONENT, value));
    }

    /**
     * Parses a curve from persisted text, tolerating the null/blank/garbage input that a
     * hand-edited backup file can contain.
     */
    public static StickCurve parse(String kindName, float exponent) {
        Kind parsed = Kind.LINEAR;
        if (kindName != null) {
            for (Kind candidate : Kind.values()) {
                if (candidate.name().equalsIgnoreCase(kindName.trim())) {
                    parsed = candidate;
                    break;
                }
            }
        }
        return new StickCurve(parsed, exponent);
    }

    @Override
    public String toString() {
        return kind.name() + "(" + exponent + ")";
    }
}
