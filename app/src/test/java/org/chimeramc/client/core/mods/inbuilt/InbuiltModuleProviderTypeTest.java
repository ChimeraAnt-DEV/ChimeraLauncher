package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The config dialog builds its control from the schema node type alone, so the mapping from a
 * config entry's type to a node type has to be faithful. It used to collapse everything that was
 * not a toggle into {@code slider_int}, which rendered the Crystal keybind as a 0..100 slider and
 * wrote a small integer over the stored key code, and dropped the Aim Settings style options.
 */
public class InbuiltModuleProviderTypeTest {

    @Test
    public void everyConfigTypeMapsToItsOwnNodeType() {
        assertEquals("toggle", InbuiltModuleProvider.schemaTypeFor(UnifiedMod.ConfigType.TOGGLE));
        assertEquals("slider_int", InbuiltModuleProvider.schemaTypeFor(UnifiedMod.ConfigType.SLIDER_INT));
        assertEquals("slider_float", InbuiltModuleProvider.schemaTypeFor(UnifiedMod.ConfigType.SLIDER_FLOAT));
        assertEquals("choice", InbuiltModuleProvider.schemaTypeFor(UnifiedMod.ConfigType.RADIO));
        assertEquals("keybind", InbuiltModuleProvider.schemaTypeFor(UnifiedMod.ConfigType.KEYBIND));
        assertEquals("text", InbuiltModuleProvider.schemaTypeFor(UnifiedMod.ConfigType.TEXT));
        assertEquals("button", InbuiltModuleProvider.schemaTypeFor(UnifiedMod.ConfigType.BUTTON));
    }

    /**
     * The schema has no colour node type yet. Falling back to a slider keeps the setting present
     * and editable rather than dropping it from the dialog.
     */
    @Test
    public void theColourTypeFallsBackToASlider() {
        assertEquals("slider_int", InbuiltModuleProvider.schemaTypeFor(UnifiedMod.ConfigType.COLOR));
    }

    /** A keybind must never be rendered as a numeric slider — that is the data-loss case. */
    @Test
    public void keybindIsNotCollapsedToASlider() {
        assertEquals(false, "slider_int".equals(
                InbuiltModuleProvider.schemaTypeFor(UnifiedMod.ConfigType.KEYBIND)));
    }
}
