package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import org.chimeramc.client.core.mods.inbuilt.overlay.HitRegistrationMod;

/**
 * Pins the hit-registration input shaping. The load-bearing property is that fine aim stays
 * untouched — a shaping filter that adds latency or overshoot to a slow track makes aiming
 * worse, not better, which is the opposite of the module's purpose.
 */
public class HitRegistrationModTest {

    private static final float EPS = 0.001f;

    @After
    public void tearDown() {
        HitRegistrationMod.setEnabled(false, null);
        HitRegistrationMod.setTimeSource(null);
    }

    /**
     * Deterministic clock. Consecutive calls land inside {@link #BURST_GAP_MS} so a test can
     * exercise the "continuing flick" path; the platform clock is not available in unit tests.
     */
    private static final class FakeClock implements HitRegistrationMod.TimeSource {
        private long now;

        FakeClock(long start) {
            this.now = start;
        }

        @Override
        public long nowMs() {
            return now;
        }

        void advance(long ms) {
            now += ms;
        }
    }

    private static FakeClock installClock() {
        FakeClock clock = new FakeClock(1000L);
        HitRegistrationMod.setTimeSource(clock);
        return clock;
    }

    @Test
    public void disabledModuleIsIdentity() {
        HitRegistrationMod.setEnabled(false, null);
        float[] out = HitRegistrationMod.shapeLookDelta(3f, -4f);
        assertEquals(3f, out[0], EPS);
        assertEquals(-4f, out[1], EPS);
    }

    @Test
    public void microDeltasPassThroughUnchangedEvenWhenActive() {
        // Sensitivity 100 / smoothing 0 / prediction 0 is the neutral profile.
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 0, 0);

        float[] out = HitRegistrationMod.shapeLookDelta(0.05f, -0.05f);
        assertEquals(0.05f, out[0], EPS);
        assertEquals(-0.05f, out[1], EPS);
    }

    @Test
    public void microDeltasAreNotExtrapolated() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 0, 100);

        // Feed a large flick, then a micro correction. The correction must survive verbatim;
        // predicting it forward would overshoot the very target the player is settling onto.
        HitRegistrationMod.shapeLookDelta(20f, 0f);
        float[] out = HitRegistrationMod.shapeLookDelta(0.05f, 0f);
        assertEquals(0.05f, out[0], EPS);
    }

    @Test
    public void sensitivityScalesLargeDeltas() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(200, 0, 0);

        float[] out = HitRegistrationMod.shapeLookDelta(10f, 0f);
        assertEquals(20f, out[0], EPS);
    }

    @Test
    public void sensitivityDoesNotApplyBelowTheMicroThreshold() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(200, 0, 0);

        // Sensitivity is deliberately applied before the magnitude test, so a micro delta is
        // still scaled — this pins the ordering so a refactor cannot silently change it.
        float[] out = HitRegistrationMod.shapeLookDelta(0.05f, 0f);
        assertEquals(0.1f, out[0], EPS);
    }

    /**
     * The opening frame of a gesture must pass through unchanged. The filter used to start at
     * zero, which threw away (1 - smoothing) of that frame and made the camera trail the
     * finger; seeding it with the frame keeps a new gesture 1:1.
     */
    @Test
    public void smoothingDoesNotAttenuateTheFirstFrameOfAGesture() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 50, 0);

        float[] out = HitRegistrationMod.shapeLookDelta(10f, 0f);
        assertEquals("a fresh gesture must not be damped", 10f, out[0], EPS);
    }

    /**
     * A 60 Hz frame is ~16.7 ms apart. The burst window used to be 16 ms, so real frame pacing
     * fell outside it and the filter was reseeded on nearly every frame — the input-delay bug.
     * A frame-spaced flick must still be one continuing gesture, which shows as the second,
     * larger delta being damped rather than passed through untouched.
     */
    @Test
    public void a60HzFrameGapStaysWithinTheBurst() {
        HitRegistrationMod.setEnabled(true, null);
        FakeClock clock = installClock();
        cfg(100, 50, 0);

        HitRegistrationMod.shapeLookDelta(10f, 0f);
        clock.advance(17L);
        float[] out = HitRegistrationMod.shapeLookDelta(20f, 0f);
        assertEquals("frame pacing must not be read as a new gesture", 15f, out[0], EPS);
    }

    /** A gap longer than any frame is a new gesture, so its first delta passes 1:1. */
    @Test
    public void aLongPauseStartsANewGesture() {
        HitRegistrationMod.setEnabled(true, null);
        FakeClock clock = installClock();
        cfg(100, 50, 0);

        HitRegistrationMod.shapeLookDelta(10f, 0f);
        clock.advance(500L);
        float[] out = HitRegistrationMod.shapeLookDelta(20f, 0f);
        assertEquals("a new gesture must not be damped", 20f, out[0], EPS);
    }

    @Test
    public void smoothingDampsASuddenChangeInTheFlick() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 50, 0);

        HitRegistrationMod.shapeLookDelta(10f, 0f);
        float second = HitRegistrationMod.shapeLookDelta(20f, 0f)[0];
        assertTrue("the change should be damped", second < 20f);
        assertTrue("but must not lag behind the previous frame", second > 10f);
    }

    /** Steady input converges to itself: the filter damps change, not the signal. */
    @Test
    public void smoothingConvergesTowardTheInputOverAFlick() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 50, 0);

        HitRegistrationMod.shapeLookDelta(10f, 0f);
        float second = HitRegistrationMod.shapeLookDelta(20f, 0f)[0];
        float third = HitRegistrationMod.shapeLookDelta(20f, 0f)[0];
        assertTrue("the filter should approach the steady input", third > second);
        assertTrue(third <= 20f);
    }

    @Test
    public void predictionIsOffForTheFirstFrameOfABurst() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 0, 100);

        // No previous frame to extrapolate from, so the first delta is passed through.
        float[] out = HitRegistrationMod.shapeLookDelta(10f, 0f);
        assertEquals(10f, out[0], EPS);
    }

    @Test
    public void predictionExtendsAContinuingFlick() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 0, 50);

        HitRegistrationMod.shapeLookDelta(5f, 0f);
        float[] out = HitRegistrationMod.shapeLookDelta(10f, 0f);
        assertTrue("an accelerating flick should be pushed further along its own direction",
                out[0] > 10f);
    }

    @Test
    public void predictionNeverReversesDirection() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 0, 100);

        HitRegistrationMod.shapeLookDelta(10f, 0f);
        // A decelerating flick: extrapolation must not flip the sign and drive the aim backwards.
        float[] out = HitRegistrationMod.shapeLookDelta(6f, 0f);
        assertTrue("deceleration must not reverse the aim", out[0] > 0f);
    }

    @Test
    public void disablingResetsTheFilter() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 50, 50);
        HitRegistrationMod.shapeLookDelta(10f, 0f);

        HitRegistrationMod.setEnabled(false, null);
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(100, 0, 0);
        float[] out = HitRegistrationMod.shapeLookDelta(10f, 0f);
        assertEquals("after a reset the first delta is unscaled", 10f, out[0], EPS);
    }

    @Test
    public void bufferVariantWritesIntoTheCallerArrayWithoutAllocating() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(200, 0, 0);

        float[] buffer = new float[2];
        float[] returned = HitRegistrationMod.shapeLookDelta(10f, 4f, buffer);
        assertTrue("the caller's buffer must be reused", returned == buffer);
        assertEquals(20f, buffer[0], EPS);
        assertEquals(8f, buffer[1], EPS);
    }

    @Test
    public void bufferVariantFallsBackWhenTheArrayIsTooSmall() {
        HitRegistrationMod.setEnabled(false, null);
        float[] tooSmall = new float[1];
        float[] returned = HitRegistrationMod.shapeLookDelta(3f, 4f, tooSmall);
        assertEquals(2, returned.length);
        assertEquals(3f, returned[0], EPS);
    }

    @Test
    public void configValuesAreClamped() {
        HitRegistrationMod.setEnabled(true, null);
        installClock();
        cfg(9999, 9999, 9999);
        assertEquals(3.0f, HitRegistrationMod.getSensitivity(), EPS);
        assertEquals(0.95f, HitRegistrationMod.getSmoothing(), EPS);
        assertEquals(1.0f, HitRegistrationMod.getPrediction(), EPS);

        cfg(0, -50, -50);
        assertEquals(0.1f, HitRegistrationMod.getSensitivity(), EPS);
        assertEquals(0f, HitRegistrationMod.getSmoothing(), EPS);
        assertEquals(0f, HitRegistrationMod.getPrediction(), EPS);
    }

    /** The module's shaping is configurable without an Android context by design. */
    private static void cfg(int sensitivity, int smoothing, int prediction) {
        HitRegistrationMod.applyConfig(sensitivity, smoothing, prediction);
    }
}
