package org.chimeramc.launcher.launcher.controller;

/**
 * Collects the idle samples that {@link StickCalibration} turns into a per-stick threshold.
 *
 * Calibration is explicit rather than automatic at connect. Sampling the moment a pad appears
 * would often catch a stick the player is already resting a thumb on or holding off-centre to
 * skip a menu, and that movement would be recorded as the noise floor — widening the dead zone
 * past the player's real input and making the pad feel dead. Asking for a deliberate button
 * press means the player knows to let go, so what gets measured is actually drift.
 *
 * Any frame above {@link StickCalibration#MAX_FLOOR} is treated as the player having moved the
 * stick rather than as noise, and restarts the run. Without that, brushing the stick mid-sample
 * would bake a large false floor into the profile.
 *
 * Sampling state is per-frame and mutable; it is advanced from the UI thread only.
 */
public final class StickCalibrationSession {

    private float leftPeak;
    private float rightPeak;
    private int samples;
    private boolean active;
    private boolean lastSampleRejected;

    /** Begins a fresh measurement, discarding any previous run. */
    public void start() {
        leftPeak = 0f;
        rightPeak = 0f;
        samples = 0;
        active = true;
        lastSampleRejected = false;
    }

    public void cancel() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public int samplesTaken() {
        return samples;
    }

    public boolean isComplete() {
        return samples >= StickCalibration.SAMPLE_TARGET;
    }

    public float leftFloor() {
        return leftPeak;
    }

    public float rightFloor() {
        return rightPeak;
    }

    /** True when the most recent sample was rejected as movement rather than drift. */
    public boolean wasLastSampleRejected() {
        return lastSampleRejected;
    }

    /**
     * Feeds one frame of stick data.
     *
     * @return true when the run completed on this frame
     */
    public boolean sample(float x, float y, float z, float rz) {
        if (!active) return false;
        float left = (float) Math.sqrt(x * x + y * y);
        float right = (float) Math.sqrt(z * z + rz * rz);
        if (left > StickCalibration.MAX_FLOOR || right > StickCalibration.MAX_FLOOR) {
            // Too far off centre to be drift: the player is on the stick. Start over rather
            // than record it.
            leftPeak = 0f;
            rightPeak = 0f;
            samples = 0;
            lastSampleRejected = true;
            return false;
        }
        leftPeak = StickCalibration.accumulate(leftPeak, left);
        rightPeak = StickCalibration.accumulate(rightPeak, right);
        samples++;
        if (isComplete()) {
            active = false;
            return true;
        }
        return false;
    }
}
