package org.chimeramc.client.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.MotionEvent;

import org.junit.Test;

/**
 * Covers {@link StickCurve}, {@link TriggerCurve} and the curve/trigger half of
 * {@link ControllerResponse}.
 *
 * The invariants these assert are the same ones the class docs claim: endpoints fixed,
 * monotonic, bounded. They matter because the curves run inside the input hot path, where a
 * non-monotonic or overshooting mapping would make aiming non-deterministic and a stray NaN
 * would reach the game.
 */
public class ControllerCurveTest {

    private static final float EPS = 0.005f;

    private static ControllerProfile profile() {
        ControllerProfile profile = new ControllerProfile("curve");
        // Remove the dead zone so the curve itself is what is being measured.
        profile.setLeftDeadZone(0f);
        profile.setRightDeadZone(0f);
        profile.setLeftStickSensitivity(1f);
        profile.setRightStickSensitivity(1f);
        return profile;
    }

    private static void assertCurveInvariants(StickCurve curve, String label) {
        assertEquals(label + ": f(0)", 0f, curve.apply(0f), 1e-6f);
        assertEquals(label + ": f(1)", 1f, curve.apply(1f), 1e-6f);
        float previous = -1f;
        for (float n = 0f; n <= 1.0001f; n += 0.002f) {
            float output = curve.apply(n);
            assertFalse(label + ": NaN at " + n, Float.isNaN(output));
            assertTrue(label + ": below 0 at " + n, output >= 0f);
            assertTrue(label + ": above 1 at " + n, output <= 1f);
            assertTrue(label + ": not monotonic at " + n, output >= previous - 1e-6f);
            previous = output;
        }
    }

    @Test
    public void everyPresetSatisfiesCurveInvariants() {
        for (StickCurve.Preset preset : StickCurve.Preset.values()) {
            assertCurveInvariants(preset.toCurve(), preset.getDisplayName());
        }
    }

    @Test
    public void everyCurveKindSatisfiesInvariantsAtExtremeExponents() {
        for (StickCurve.Kind kind : StickCurve.Kind.values()) {
            assertCurveInvariants(new StickCurve(kind, StickCurve.MIN_EXPONENT), kind + "-min");
            assertCurveInvariants(new StickCurve(kind, StickCurve.MAX_EXPONENT), kind + "-max");
        }
    }

    @Test
    public void linearCurveIsIdentity() {
        StickCurve curve = new StickCurve();
        assertTrue(curve.isIdentity());
        assertEquals(0.37f, curve.apply(0.37f), 1e-5f);
    }

    @Test
    public void exponentialCurveSuppressesSmallMovements() {
        StickCurve curve = new StickCurve(StickCurve.Kind.EXPONENTIAL, 2f);
        assertTrue(curve.apply(0.5f) < 0.5f);
        assertEquals(0.25f, curve.apply(0.5f), EPS);
    }

    @Test
    public void easeOutCurveBoostsSmallMovements() {
        StickCurve curve = new StickCurve(StickCurve.Kind.EASE_OUT, 2f);
        assertTrue(curve.apply(0.5f) > 0.5f);
        assertEquals(0.75f, curve.apply(0.5f), EPS);
    }

    @Test
    public void sigmoidIsSymmetricAroundMidpoint() {
        StickCurve curve = new StickCurve(StickCurve.Kind.SIGMOID, 2f);
        // A sigmoid crosses its midpoint at 0.5 for any symmetric exponent.
        assertEquals(0.5f, curve.apply(0.5f), EPS);
        float low = curve.apply(0.2f);
        float high = curve.apply(0.8f);
        assertEquals(1f, low + high, EPS);
    }

    @Test
    public void nanAndOutOfRangeInputsAreClampedNotPropagated() {
        for (StickCurve.Preset preset : StickCurve.Preset.values()) {
            StickCurve curve = preset.toCurve();
            assertEquals(0f, curve.apply(Float.NaN), 1e-6f);
            assertEquals(0f, curve.apply(-5f), 1e-6f);
            assertEquals(1f, curve.apply(5f), 1e-6f);
        }
    }

    @Test
    public void absentExponentDeserialisesToTheDefaultCurve() {
        // Gson leaves missing float fields at 0, which must not become a strong curve.
        StickCurve parsed = StickCurve.parse(null, 0f);
        assertTrue(parsed.isIdentity());
        assertNotNull(parsed.matchingPreset());
        assertEquals(StickCurve.Preset.LINEAR, parsed.matchingPreset());
    }

    @Test
    public void matchingPresetFindsExactPresets() {
        for (StickCurve.Preset preset : StickCurve.Preset.values()) {
            assertNotNull(preset.getDisplayName(), preset.toCurve().matchingPreset());
        }
        assertNull(new StickCurve(StickCurve.Kind.EXPONENTIAL, 2.9f).matchingPreset());
    }

    @Test
    public void responseAppliesTheProfileStickCurve() {
        ControllerProfile profile = profile();
        profile.setLeftCurve(new StickCurve(StickCurve.Kind.EASE_OUT, 2f));
        ControllerResponse response = new ControllerResponse(profile, false);
        // Ease-out quad at 0.5 is 0.75, so a half-pushed stick must report more than half.
        assertEquals(0.75f, response.adjustAxis(MotionEvent.AXIS_X, 0.5f), EPS);
        // The right stick is left linear, so it must not be shaped.
        assertEquals(0.5f, response.adjustAxis(MotionEvent.AXIS_Z, 0.5f), EPS);
    }

    @Test
    public void responseCurveStaysBoundedWithHighSensitivity() {
        ControllerProfile profile = profile();
        profile.setLeftCurve(StickCurve.Preset.INSTANT.toCurve());
        profile.setLeftStickSensitivity(ControllerProfile.MAX_SENSITIVITY);
        ControllerResponse response = new ControllerResponse(profile, false);
        for (float value = 0f; value <= 1.0001f; value += 0.005f) {
            float output = response.adjustAxis(MotionEvent.AXIS_X, value);
            assertTrue("out of range at " + value, output >= 0f && output <= 1f);
        }
        assertEquals(1f, response.adjustAxis(MotionEvent.AXIS_X, 1f), EPS);
    }

    @Test
    public void lowLatencyDoesNotOverrideAnExplicitProfileCurve() {
        ControllerProfile explicit = profile();
        explicit.setLeftCurve(new StickCurve(StickCurve.Kind.EXPONENTIAL, 2f));
        ControllerResponse snappy = new ControllerResponse(explicit, true);
        // The profile asked for a suppressing curve; Low Input Delay must respect it.
        assertEquals(0.25f, snappy.adjustAxis(MotionEvent.AXIS_X, 0.5f), EPS);
        assertEquals(1f, snappy.adjustAxis(MotionEvent.AXIS_X, 1f), EPS);
    }

    @Test
    public void lowLatencySuppliesABoostingCurveWhenProfileIsIdentical() {
        ControllerResponse snappy = new ControllerResponse(profile(), true);
        assertTrue(snappy.adjustAxis(MotionEvent.AXIS_X, 0.4f) > 0.4f);
    }

    @Test
    public void triggerCurveIsIdentityByDefault() {
        ControllerResponse response = new ControllerResponse(profile(), false);
        assertTrue(response.adjustTrigger(MotionEvent.AXIS_LTRIGGER, 0.42f) == 0.42f);
        assertTrue(response.adjustTrigger(MotionEvent.AXIS_RTRIGGER, 0.9f) == 0.9f);
    }

    @Test
    public void triggerDeadZoneSuppressesARestingFinger() {
        ControllerProfile profile = profile();
        profile.setLeftTriggerCurve(new TriggerCurve(0.2f, 1f));
        ControllerResponse response = new ControllerResponse(profile, false);
        // Below the floor: exactly zero, not merely small, so the game reads no press.
        assertEquals(0f, response.adjustTrigger(MotionEvent.AXIS_LTRIGGER, 0.1f), 1e-6f);
        // Above the floor the remaining travel is re-normalised so full pull still reaches 1.
        assertEquals(1f, response.adjustTrigger(MotionEvent.AXIS_LTRIGGER, 1f), EPS);
        assertTrue(response.adjustTrigger(MotionEvent.AXIS_LTRIGGER, 0.6f) > 0f);
    }

    @Test
    public void hairTriggerBoostsPartialPull() {
        ControllerProfile profile = profile();
        profile.setRightTriggerCurve(new TriggerCurve(0f, 0.5f));
        ControllerResponse response = new ControllerResponse(profile, false);
        assertEquals(0.5f, response.adjustTrigger(MotionEvent.AXIS_RTRIGGER, 0.25f), EPS);
    }

    @Test
    public void triggerCurvesAreBoundedAndMonotonic() {
        for (TriggerCurve.Preset preset : TriggerCurve.Preset.values()) {
            TriggerCurve curve = preset.toCurve();
            float previous = -1f;
            for (float v = 0f; v <= 1.0001f; v += 0.005f) {
                float output = curve.apply(v);
                assertTrue(preset + ": out of range at " + v, output >= 0f && output <= 1f);
                assertTrue(preset + ": not monotonic at " + v, output >= previous - 1e-6f);
                previous = output;
            }
        }
    }

    @Test
    public void allTriggerAxesAreRouted() {
        ControllerProfile profile = profile();
        profile.setLeftTriggerCurve(new TriggerCurve(0f, 2f));
        profile.setRightTriggerCurve(new TriggerCurve(0f, 2f));
        ControllerResponse response = new ControllerResponse(profile, false);
        assertEquals(0.25f, response.adjustTrigger(MotionEvent.AXIS_LTRIGGER, 0.5f), EPS);
        assertEquals(0.25f, response.adjustTrigger(MotionEvent.AXIS_BRAKE, 0.5f), EPS);
        assertEquals(0.25f, response.adjustTrigger(MotionEvent.AXIS_RTRIGGER, 0.5f), EPS);
        assertEquals(0.25f, response.adjustTrigger(MotionEvent.AXIS_GAS, 0.5f), EPS);
    }

    @Test
    public void axisRoutingSendsSticksAndTriggersToTheRightCurve() {
        ControllerProfile profile = profile();
        profile.setLeftCurve(new StickCurve(StickCurve.Kind.EASE_OUT, 2f));
        profile.setLeftTriggerCurve(new TriggerCurve(0f, 2f));
        ControllerResponse response = new ControllerResponse(profile, false);
        assertEquals(0.75f, response.adjustAxisOrTrigger(MotionEvent.AXIS_X, 0.5f), EPS);
        assertEquals(0.25f, response.adjustAxisOrTrigger(MotionEvent.AXIS_LTRIGGER, 0.5f), EPS);
        assertEquals(0.5f, response.adjustAxisOrTrigger(MotionEvent.AXIS_VSCROLL, 0.5f), EPS);
    }

    @Test
    public void responseTablesAreQuantisedButAccurate() {
        // A curve table lookup must match the analytic curve closely enough to be invisible.
        StickCurve curve = StickCurve.Preset.AGGRESSIVE.toCurve();
        ControllerProfile profile = profile();
        profile.setLeftCurve(curve);
        ControllerResponse response = new ControllerResponse(profile, false);
        for (float n = 0f; n <= 1.0001f; n += 0.01f) {
            float expected = curve.apply(n);
            float actual = ControllerResponse.evaluate(
                    buildTable(curve), n);
            assertEquals("table drift at " + n, expected, actual, 0.01f);
        }
    }

    private static float[] buildTable(StickCurve curve) {
        float[] table = new float[ControllerResponse.CURVE_STEPS + 1];
        for (int i = 0; i <= ControllerResponse.CURVE_STEPS; i++) {
            table[i] = curve.apply(i / (float) ControllerResponse.CURVE_STEPS);
        }
        table[0] = 0f;
        table[ControllerResponse.CURVE_STEPS] = 1f;
        return table;
    }

    @Test
    public void curvesSurviveAProfileCopy() {
        ControllerProfile original = profile();
        original.setLeftCurve(StickCurve.Preset.PRECISION.toCurve());
        original.setRightCurve(StickCurve.Preset.INSTANT.toCurve());
        original.setLeftTriggerCurve(new TriggerCurve(0.15f, 0.5f));
        original.setRightTriggerCurve(new TriggerCurve(0.05f, 1.5f));

        ControllerProfile copy = original.copy();
        assertEquals(original.getLeftCurve().getKind(), copy.getLeftCurve().getKind());
        assertEquals(original.getLeftCurve().getExponent(), copy.getLeftCurve().getExponent(), 1e-6f);
        assertEquals(original.getRightCurve().getKind(), copy.getRightCurve().getKind());
        assertEquals(original.getRightTriggerCurve().getDeadZone(),
                copy.getRightTriggerCurve().getDeadZone(), 1e-6f);
    }

    @Test
    public void profileWrittenByAnOlderBuildReadsAsIdentityCurves() {
        // Constructing from the default field values models a JSON profile with no curve keys.
        ControllerProfile legacy = new ControllerProfile("legacy");
        assertTrue(legacy.getLeftCurve().isIdentity());
        assertTrue(legacy.getRightCurve().isIdentity());
        assertTrue(legacy.getLeftTriggerCurve().isIdentity());
        assertTrue(legacy.getRightTriggerCurve().isIdentity());
    }
}
