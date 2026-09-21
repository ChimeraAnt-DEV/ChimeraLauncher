package org.chimeramc.launcher.launcher.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Round-trips {@link ControllerProfileCodec} and checks that import cannot introduce a value
 * the input path would choke on.
 *
 * Import is the one place a profile can come from outside the app, so the clamps are what
 * keep a hand-edited file from producing an out-of-range dead zone or a 900-entry remap table.
 */
public class ControllerProfileCodecTest {

    private static ControllerProfile sample(String name) {
        ControllerProfile profile = new ControllerProfile(name);
        profile.setLeftDeadZone(0.2f);
        profile.setRightDeadZone(0.35f);
        profile.setLeftStickSensitivity(1.5f);
        profile.setRightStickSensitivity(0.5f);
        profile.setVibrationEnabled(false);
        profile.setLeftCurve(new StickCurve(StickCurve.Kind.EASE_OUT, 1.5f));
        profile.setRightCurve(StickCurve.Preset.PRECISION.toCurve());
        profile.setLeftTriggerCurve(new TriggerCurve(0.1f, 0.75f));
        profile.setRightTriggerCurve(new TriggerCurve(0.02f, 2.2f));
        profile.setRemap(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B);
        return profile;
    }

    @Test
    public void roundTripPreservesEverything() {
        List<ControllerProfile> original = new ArrayList<>();
        original.add(sample("Aim"));
        original.add(sample("Racing"));

        String json = ControllerProfileCodec.export(ControllerType.DS4, original);
        ControllerProfileCodec.ImportResult result = ControllerProfileCodec.importFrom(json);

        assertEquals(ControllerType.DS4, ControllerProfileCodec.typeOf(json));
        assertEquals(2, result.profiles.size());
        assertEquals(0, result.skipped);

        ControllerProfile first = result.profiles.get(0);
        assertEquals("Aim", first.getName());
        assertEquals(0.2f, first.getLeftDeadZone(), 1e-4f);
        assertEquals(0.35f, first.getRightDeadZone(), 1e-4f);
        assertEquals(1.5f, first.getLeftStickSensitivity(), 1e-4f);
        assertEquals(0.5f, first.getRightStickSensitivity(), 1e-4f);
        assertFalse(first.isVibrationEnabled());
        assertEquals(StickCurve.Kind.EASE_OUT, first.getLeftCurve().getKind());
        assertEquals(1.5f, first.getLeftCurve().getExponent(), 1e-4f);
        assertEquals(StickCurve.Preset.PRECISION, first.getRightCurve().matchingPreset());
        assertEquals(0.1f, first.getLeftTriggerCurve().getDeadZone(), 1e-4f);
        assertEquals(2.2f, first.getRightTriggerCurve().getExponent(), 1e-4f);
        assertEquals(Integer.valueOf(KeyEvent.KEYCODE_BUTTON_B), first.getButtonRemaps().get(KeyEvent.KEYCODE_BUTTON_A));
    }

    @Test
    public void exportedJsonCarriesFormatAndSchemaIdentifiers() {
        String json = ControllerProfileCodec.export(ControllerType.XBOX, new ArrayList<>());
        assertTrue(json.contains(ControllerProfileCodec.FORMAT_ID));
        assertTrue(json.contains("\"schemaVersion\""));
    }

    @Test
    public void emptyAndBlankInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ControllerProfileCodec.importFrom(null));
        assertThrows(IllegalArgumentException.class, () -> ControllerProfileCodec.importFrom("   "));
    }

    @Test
    public void malformedJsonIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ControllerProfileCodec.importFrom("{not json at all"));
    }

    @Test
    public void foreignFormatIsRejectedRatherThanSilentlyImported() {
        String json = "{\"format\":\"some.other.tool\",\"profiles\":[{\"name\":\"x\"}]}";
        assertThrows(IllegalArgumentException.class, () -> ControllerProfileCodec.importFrom(json));
    }

    @Test
    public void bundleWithNoProfilesIsRejected() {
        String json = "{\"format\":\"" + ControllerProfileCodec.FORMAT_ID + "\",\"profiles\":[]}";
        assertThrows(IllegalArgumentException.class, () -> ControllerProfileCodec.importFrom(json));
    }

    @Test
    public void outOfRangeValuesAreClampedOnImport() {
        // A hand-edited file can carry anything; the imported profile must still be usable.
        String json = "{\"format\":\"" + ControllerProfileCodec.FORMAT_ID + "\",\"profiles\":[{"
                + "\"name\":\"Hostile\","
                + "\"leftDeadZone\":5.0,"
                + "\"rightDeadZone\":-3.0,"
                + "\"leftStickSensitivity\":99.0,"
                + "\"rightStickSensitivity\":-99.0,"
                + "\"leftCurveKind\":\"EASE_OUT\","
                + "\"leftCurveExponent\":500.0,"
                + "\"leftTriggerDeadZone\":400.0,"
                + "\"leftTriggerExponent\":-8.0"
                + "}]}";
        ControllerProfileCodec.ImportResult result = ControllerProfileCodec.importFrom(json);
        ControllerProfile imported = result.profiles.get(0);

        assertEquals(0.9f, imported.getLeftDeadZone(), 1e-4f);
        assertEquals(0f, imported.getRightDeadZone(), 1e-4f);
        assertEquals(ControllerProfile.MAX_SENSITIVITY, imported.getLeftStickSensitivity(), 1e-4f);
        assertEquals(ControllerProfile.MIN_SENSITIVITY, imported.getRightStickSensitivity(), 1e-4f);
        assertEquals(StickCurve.MAX_EXPONENT, imported.getLeftCurve().getExponent(), 1e-4f);
        assertEquals(0.5f, imported.getLeftTriggerCurve().getDeadZone(), 1e-4f);
        assertEquals(TriggerCurve.DEFAULT_EXPONENT, imported.getLeftTriggerCurve().getExponent(), 1e-4f);
    }

    @Test
    public void importedProfileNeverProducesAnInvalidResponse() {
        String json = "{\"format\":\"" + ControllerProfileCodec.FORMAT_ID + "\",\"profiles\":[{"
                + "\"name\":\"Hostile\",\"leftDeadZone\":5.0,\"leftCurveExponent\":500.0}]}";
        ControllerProfile imported = ControllerProfileCodec.importFrom(json).profiles.get(0);
        ControllerResponse response = new ControllerResponse(imported, true);
        for (float value = 0f; value <= 1.0001f; value += 0.01f) {
            float output = response.adjustAxis(android.view.MotionEvent.AXIS_X, value);
            assertFalse(Float.isNaN(output));
            assertTrue(output >= 0f && output <= 1f);
        }
    }

    @Test
    public void profilesBeyondSlotLimitAreDroppedNotTruncated() {
        List<ControllerProfile> many = new ArrayList<>();
        for (int i = 0; i < ControllerProfile.MAX_SLOTS + 3; i++) {
            many.add(sample("P" + i));
        }
        String json = ControllerProfileCodec.export(ControllerType.XBOX, many);
        ControllerProfileCodec.ImportResult result = ControllerProfileCodec.importFrom(json);
        assertEquals(ControllerProfile.MAX_SLOTS, result.profiles.size());
        assertEquals(3, result.skipped);
    }

    @Test
    public void missingNameGetsAUsableDefault() {
        String json = "{\"format\":\"" + ControllerProfileCodec.FORMAT_ID + "\",\"profiles\":[{}]}";
        ControllerProfile imported = ControllerProfileCodec.importFrom(json).profiles.get(0);
        assertNotNull(imported.getName());
        assertFalse(imported.getName().trim().isEmpty());
    }

    @Test
    public void unknownControllerTypeReadsAsNullRatherThanThrowing() {
        String json = "{\"format\":\"" + ControllerProfileCodec.FORMAT_ID
                + "\",\"controllerType\":\"STEAM_DECK\",\"profiles\":[{\"name\":\"x\"}]}";
        assertNotNull(ControllerProfileCodec.importFrom(json));
        assertEquals(null, ControllerProfileCodec.typeOf(json));
    }
}
