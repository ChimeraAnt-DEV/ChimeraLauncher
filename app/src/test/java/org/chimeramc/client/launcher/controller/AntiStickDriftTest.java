package org.chimeramc.client.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;

/**
 * Covers the anti stick drift path: the circular dead zone, the calibration maths, and the
 * hysteresis that stops a wobbling stick leaking movement.
 *
 * These exercise the real {@link ControllerResponse}/{@link StickCalibration} code with no
 * mocks. Android's MotionEvent axis constants are static final ints inlined by javac, so no
 * Android runtime is needed.
 */
public class AntiStickDriftTest {

    private static final float EPS = 0.0005f;

    private static ControllerProfile drifting(float deadZone, float noiseFloor) {
        ControllerProfile profile = new ControllerProfile("drift");
        profile.setLeftDeadZone(deadZone);
        profile.setRightDeadZone(deadZone);
        profile.setLeftStickSensitivity(1f);
        profile.setRightStickSensitivity(1f);
        profile.setAntiDriftEnabled(true);
        profile.setLeftStickNoiseFloor(noiseFloor);
        profile.setRightStickNoiseFloor(noiseFloor);
        return profile;
    }

    // --- Circular dead zone -----------------------------------------------------------------

    /**
     * The bug this whole change exists for: a stick resting off-centre on one axis used to be
     * reported as movement, because that axis alone sat above the dead zone while the other
     * read zero.
     */
    @Test
    public void singleAxisRestOffsetIsCentredByTheRadialCheck() {
        ControllerProfile profile = drifting(0.15f, 0f);
        ControllerResponse response = new ControllerResponse(profile, false);
        float[] out = new float[2];

        // 0.2 on x alone is outside the 0.15 per-axis dead zone, which is exactly the ghost
        // drift case. The radial test must still call this centred once the anti-drift floor
        // is wide enough, so verify the per-axis form disagrees while the radial one agrees.
        assertTrue("per-axis check is the old behaviour", response.isOutsideDeadZone(MotionEvent.AXIS_X, 0.2f));

        ControllerProfile calibrated = drifting(0.15f, 0.19f);
        ControllerResponse filtered = new ControllerResponse(calibrated, false);
        assertFalse(filtered.isPairOutsideThreshold(true, 0.2f, 0f));
        filtered.adjustStickPair(true, 0.2f, 0f, out);
        assertEquals(0f, out[0], EPS);
        assertEquals(0f, out[1], EPS);
    }

    /** The dead zone is a circle, so diagonal travel is not flattened harder than axial travel. */
    @Test
    public void diagonalAndAxialTravelReachTheSameThreshold() {
        ControllerProfile profile = drifting(0.2f, 0f);
        ControllerResponse response = new ControllerResponse(profile, false);

        float axial = 0.2f;
        float diagonal = (float) (0.2f / Math.sqrt(2.0));
        // Both sit exactly on the threshold: the radial check treats them the same, whereas a
        // square dead zone would let the diagonal through untouched and block the axial one.
        assertFalse(response.isPairOutsideThreshold(true, axial, 0f));
        assertFalse(response.isPairOutsideThreshold(true, diagonal, diagonal));
    }

    @Test
    public void radialShapingPreservesPushDirection() {
        ControllerProfile profile = drifting(0.1f, 0f);
        ControllerResponse response = new ControllerResponse(profile, false);
        float[] out = new float[2];
        response.adjustStickPair(true, 0.6f, 0.8f, out);

        // 0.6/0.8 is a 3-4-5 triangle, so the shaped pair must keep that ratio exactly.
        float ratio = out[1] / out[0];
        assertEquals(0.8f / 0.6f, ratio, 0.01f);
        assertTrue(out[0] > 0f);
        assertTrue(out[1] > 0f);
    }

    @Test
    public void radialShapingHandlesBothSignsAndBothSticks() {
        ControllerProfile profile = drifting(0.1f, 0f);
        ControllerResponse response = new ControllerResponse(profile, false);
        float[] out = new float[2];

        response.adjustStickPair(true, -0.5f, -0.5f, out);
        assertTrue(out[0] < 0f);
        assertTrue(out[1] < 0f);

        response.adjustStickPair(false, 0.5f, -0.5f, out);
        assertTrue(out[0] > 0f);
        assertTrue(out[1] < 0f);
    }

    @Test
    public void fullDeflectionStillReachesFullMagnitude() {
        ControllerProfile profile = drifting(0.15f, 0f);
        ControllerResponse response = new ControllerResponse(profile, false);
        float[] out = new float[2];
        response.adjustStickPair(true, 1f, 0f, out);
        assertEquals(1f, out[0], EPS);
        assertEquals(0f, out[1], EPS);

        response.adjustStickPair(false, 0f, -1f, out);
        assertEquals(0f, out[0], EPS);
        assertEquals(-1f, out[1], EPS);
    }

    @Test
    public void centrePairIsReportedAsUnchangedWhenAlreadyZero() {
        ControllerResponse response = new ControllerResponse(drifting(0.15f, 0f), false);
        float[] out = new float[2];
        assertFalse("an all-zero pair must not report a change",
                response.adjustStickPair(true, 0f, 0f, out));
        assertEquals(0f, out[0], EPS);
        assertEquals(0f, out[1], EPS);
    }

    @Test
    public void rightStickUsesItsOwnThreshold() {
        ControllerProfile profile = new ControllerProfile("split");
        profile.setLeftDeadZone(0.05f);
        profile.setRightDeadZone(0.4f);
        profile.setAntiDriftEnabled(true);
        ControllerResponse response = new ControllerResponse(profile, false);

        assertTrue(response.isPairOutsideThreshold(true, 0.3f, 0f));
        assertFalse(response.isPairOutsideThreshold(false, 0.3f, 0f));
    }

    // --- Calibration maths ------------------------------------------------------------------

    @Test
    public void accumulateKeepsTheLargestMagnitude() {
        float peak = 0f;
        peak = StickCalibration.accumulate(peak, 0.05f);
        peak = StickCalibration.accumulate(peak, -0.12f);
        peak = StickCalibration.accumulate(peak, 0.03f);
        assertEquals(0.12f, peak, EPS);
    }

    @Test
    public void accumulateIgnoresUnusableSamples() {
        assertEquals(0.1f, StickCalibration.accumulate(0.1f, Float.NaN), EPS);
        assertEquals(0.1f, StickCalibration.accumulate(0.1f, Float.POSITIVE_INFINITY), EPS);
        assertEquals(0f, StickCalibration.accumulate(0f, Float.NaN), EPS);
    }

    /** Anti-drift can only widen the dead zone; it must never shrink the user's setting. */
    @Test
    public void effectiveDeadZoneNeverShrinksBelowTheConfiguredValue() {
        assertEquals(0.3f, StickCalibration.effectiveDeadZone(0.3f, 0.01f), EPS);
        // A measured floor larger than the setting widens it, plus the sampling margin.
        assertEquals(0.12f + StickCalibration.MARGIN,
                StickCalibration.effectiveDeadZone(0.05f, 0.12f), EPS);
    }

    @Test
    public void effectiveDeadZoneIsBoundedSoAWornStickCannotSwallowInput() {
        float threshold = StickCalibration.effectiveDeadZone(0.1f, 0.9f);
        assertEquals(StickCalibration.MAX_FLOOR, threshold, EPS);
    }

    @Test
    public void uncalibratedProfileKeepsItsOwnDeadZone() {
        assertEquals(0.15f, StickCalibration.effectiveDeadZone(0.15f, 0f), EPS);
        assertFalse(StickCalibration.isCalibrated(0f));
        assertTrue(StickCalibration.isCalibrated(0.02f));
    }

    @Test
    public void antiDriftOffLeavesTheProfileDeadZoneAlone() {
        ControllerProfile profile = drifting(0.15f, 0.25f);
        profile.setAntiDriftEnabled(false);
        ControllerResponse response = new ControllerResponse(profile, false);
        assertFalse(response.isAntiDriftEnabled());
        assertEquals(0.15f, response.driftThreshold(true), EPS);
    }

    @Test
    public void antiDriftOnUsesTheCalibratedThreshold() {
        ControllerResponse response = new ControllerResponse(drifting(0.15f, 0.25f), false);
        assertTrue(response.isAntiDriftEnabled());
        assertEquals(0.25f + StickCalibration.MARGIN, response.driftThreshold(true), EPS);
    }

    // --- Hysteresis -------------------------------------------------------------------------

    @Test
    public void aSingleAboveThresholdFrameIsNotEnough() {
        StickDriftGate gate = new StickDriftGate();
        assertFalse("one noisy frame must not move the camera", gate.allow(0.2f, 0.15f, 1000L));
    }

    @Test
    public void sustainedMovementGetsThrough() {
        StickDriftGate gate = new StickDriftGate();
        assertFalse(gate.allow(0.2f, 0.15f, 1000L));
        assertTrue(gate.allow(0.2f, 0.15f, 1016L));
    }

    @Test
    public void aLargeFlickBypassesTheDelay() {
        StickDriftGate gate = new StickDriftGate();
        assertTrue("a deliberate flick must not be delayed", gate.allow(0.9f, 0.15f, 1000L));
    }

    @Test
    public void fallingBackInsideTheThresholdRestartsTheRun() {
        StickDriftGate gate = new StickDriftGate();
        assertFalse(gate.allow(0.2f, 0.15f, 1000L));
        assertFalse(gate.allow(0.1f, 0.15f, 1016L));
        // The earlier crossing must not count toward this one.
        assertFalse(gate.allow(0.2f, 0.15f, 1032L));
        assertTrue(gate.allow(0.2f, 0.15f, 1048L));
    }

    @Test
    public void evaluatingTheSameEventTwiceDoesNotAdvanceTheRun() {
        StickDriftGate gate = new StickDriftGate();
        assertFalse(gate.allow(0.2f, 0.15f, 1000L));
        // The dead-zone check and the rewrite both ask about one event; that must not count
        // as two frames, or the delay would collapse to a single frame.
        assertFalse(gate.allow(0.2f, 0.15f, 1000L));
        assertTrue(gate.allow(0.2f, 0.15f, 1016L));
    }

    @Test
    public void resetForgetsAPartialRun() {
        StickDriftGate gate = new StickDriftGate();
        assertFalse(gate.allow(0.2f, 0.15f, 1000L));
        gate.reset();
        assertFalse(gate.allow(0.2f, 0.15f, 1016L));
    }

    @Test
    public void impulseBypassScalesWithTheThreshold() {
        assertTrue(StickDriftGate.impulseBypass(0.05f) >= StickDriftGate.IMPULSE_FLOOR);
        assertTrue(StickDriftGate.impulseBypass(0.4f) > 0.4f);
    }

    // --- Calibration session ----------------------------------------------------------------

    @Test
    public void sessionCompletesAfterTheTargetSampleCount() {
        StickCalibrationSession session = new StickCalibrationSession();
        session.start();
        boolean complete = false;
        for (int i = 0; i < StickCalibration.SAMPLE_TARGET; i++) {
            complete = session.sample(0.04f, 0f, 0f, -0.02f);
        }
        assertTrue(complete);
        assertFalse(session.isActive());
        assertEquals(0.04f, session.leftFloor(), EPS);
        assertEquals(0.02f, session.rightFloor(), EPS);
    }

    @Test
    public void sessionRestartsWhenThePlayerMovesAStick() {
        StickCalibrationSession session = new StickCalibrationSession();
        session.start();
        for (int i = 0; i < StickCalibration.SAMPLE_TARGET - 1; i++) {
            session.sample(0.02f, 0f, 0f, 0f);
        }
        assertEquals(StickCalibration.SAMPLE_TARGET - 1, session.samplesTaken());

        // Brushing the stick must discard the run rather than bake in a false floor.
        assertFalse(session.sample(0.8f, 0f, 0f, 0f));
        assertTrue(session.wasLastSampleRejected());
        assertEquals(0, session.samplesTaken());
        assertEquals(0f, session.leftFloor(), EPS);
    }

    @Test
    public void sessionIgnoresSamplesBeforeStartAndAfterCancel() {
        StickCalibrationSession session = new StickCalibrationSession();
        assertFalse(session.sample(0.02f, 0f, 0f, 0f));
        session.start();
        session.cancel();
        assertFalse(session.isActive());
        assertFalse(session.sample(0.02f, 0f, 0f, 0f));
    }

    @Test
    public void sessionStartClearsAPreviousRun() {
        StickCalibrationSession session = new StickCalibrationSession();
        session.start();
        session.sample(0.5f, 0f, 0f, 0f);
        session.start();
        assertEquals(0, session.samplesTaken());
        assertEquals(0f, session.leftFloor(), EPS);
    }

    // --- Profile plumbing -------------------------------------------------------------------

    @Test
    public void profileDefaultsToAntiDriftOffAndUncalibrated() {
        ControllerProfile profile = new ControllerProfile("fresh");
        assertFalse(profile.isAntiDriftEnabled());
        assertEquals(0f, profile.getLeftStickNoiseFloor(), EPS);
        assertEquals(0f, profile.getRightStickNoiseFloor(), EPS);
    }

    @Test
    public void copyCarriesTheAntiDriftFields() {
        ControllerProfile original = drifting(0.2f, 0.08f);
        ControllerProfile copy = original.copy();
        assertTrue(copy.isAntiDriftEnabled());
        assertEquals(0.08f, copy.getLeftStickNoiseFloor(), EPS);
        assertEquals(0.08f, copy.getRightStickNoiseFloor(), EPS);
    }

    /** The copy must be independent, or editing a copy would silently edit the stored profile. */
    @Test
    public void copyDoesNotShareStateWithTheOriginal() {
        ControllerProfile original = drifting(0.2f, 0.08f);
        ControllerProfile copy = original.copy();
        copy.setAntiDriftEnabled(false);
        copy.setLeftStickNoiseFloor(0.5f);
        assertTrue(original.isAntiDriftEnabled());
        assertNotEquals(0.5f, original.getLeftStickNoiseFloor(), EPS);
    }

    @Test
    public void noiseFloorIsClampedToAUsableRange() {
        ControllerProfile profile = new ControllerProfile("clamp");
        profile.setLeftStickNoiseFloor(-1f);
        assertEquals(0f, profile.getLeftStickNoiseFloor(), EPS);
        profile.setLeftStickNoiseFloor(5f);
        assertEquals(1f, profile.getLeftStickNoiseFloor(), EPS);
        profile.setLeftStickNoiseFloor(Float.NaN);
        assertEquals(0f, profile.getLeftStickNoiseFloor(), EPS);
    }
}
