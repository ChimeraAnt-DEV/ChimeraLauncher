package org.chimeramc.client.launcher.controller;

/**
 * The noise floor of one physical stick, measured rather than guessed.
 *
 * Stick drift is a property of the individual pad, not of the controller model: two Xbox pads
 * of the same revision can idle with very different off-centre values, and the value also
 * changes as the potentiometers wear. A fixed dead zone therefore has to be set wide enough
 * for the worst pad the author happens to own, which deadens the small intentional movements
 * on every better one. Measuring the resting magnitude lets the anti-drift threshold sit just
 * above this pad's actual noise.
 *
 * This type holds the measurement and the derived threshold. It is deliberately pure
 * arithmetic with no Android dependency, so the sampling maths and the clamping are testable
 * without a device.
 */
public final class StickCalibration {

    /**
     * Samples taken per stick. At the ~60-125Hz a gamepad reports motion events, 30 samples is
     * a fraction of a second of idle. Enough to see the oscillation a worn stick produces,
     * short enough that asking the player to let go for a moment is not a chore.
     */
    public static final int SAMPLE_TARGET = 30;

    /**
     * Added to the measured floor to form the threshold. Sampling cannot see the true peak of
     * an oscillating stick (it only sees the frames it happened to catch), and drift wanders
     * with temperature and wear, so the threshold sits above the observed peak rather than on
     * it. Wide enough to absorb both, narrow enough that small intentional movement survives.
     */
    public static final float MARGIN = 0.02f;

    /**
     * Upper bound on the derived threshold. A stick whose resting noise exceeds this is worn
     * past the point where the filter can separate drift from input — suppressing travel that
     * large would swallow real movement — so calibration stops being the answer for that pad
     * and the bound keeps the filter honest about it.
     */
    public static final float MAX_FLOOR = 0.30f;

    private StickCalibration() {
    }

    /**
     * Accumulates one idle sample and returns the new peak.
     *
     * Returns the input unchanged for nothing usable, so a caller can fold this over a stream
     * of events without special-casing an unavailable axis.
     *
     * @param currentPeak peak magnitude observed so far
     * @param value       one axis reading, already taken while the stick was at rest
     */
    public static float accumulate(float currentPeak, float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return sanitizePeak(currentPeak);
        float magnitude = Math.abs(value);
        float peak = sanitizePeak(currentPeak);
        return magnitude > peak ? magnitude : peak;
    }

    /**
     * The dead zone to apply for a measured floor: the larger of the profile's own dead zone
     * and the calibrated floor plus {@link #MARGIN}, capped at {@link #MAX_FLOOR}.
     *
     * Taking the larger means anti-drift can only ever widen the dead zone. Shrinking it would
     * let the user's explicit setting be silently overridden by a measurement, which is the
     * opposite of what "fix my drift" should do.
     *
     * @param configuredDeadZone the dead zone from the profile
     * @param noiseFloor         measured resting magnitude, or 0 when never calibrated
     */
    public static float effectiveDeadZone(float configuredDeadZone, float noiseFloor) {
        float configured = clampConfigured(configuredDeadZone);
        float measured = sanitizePeak(noiseFloor);
        if (measured <= 0f) return configured;
        float threshold = measured + MARGIN;
        if (threshold > MAX_FLOOR) threshold = MAX_FLOOR;
        return Math.max(configured, threshold);
    }

    /** True when a meaningful floor has been recorded. */
    public static boolean isCalibrated(float noiseFloor) {
        return sanitizePeak(noiseFloor) > 0f;
    }

    private static float sanitizePeak(float peak) {
        if (Float.isNaN(peak) || peak <= 0f) return 0f;
        if (peak > 1f) return 1f;
        return peak;
    }

    private static float clampConfigured(float zone) {
        if (Float.isNaN(zone)) return ControllerProfile.DEFAULT_DEAD_ZONE;
        return Math.max(0f, Math.min(0.9f, zone));
    }
}
