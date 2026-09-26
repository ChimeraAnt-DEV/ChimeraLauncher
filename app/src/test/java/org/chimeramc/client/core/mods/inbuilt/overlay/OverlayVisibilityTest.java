package org.chimeramc.client.core.mods.inbuilt.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the overlay visibility rule.
 *
 * The regression this guards: reading an uninstalled native HUD hook's "false" as "not in
 * game" hid every HUD overlay once the Mod Menu closed. A missing signal must not delete a
 * mod's UI, while a real menu must still suppress it.
 */
public class OverlayVisibilityTest {

    @Test
    public void hudEditorAlwaysShows() {
        assertTrue("the HUD editor shows overlays even over a menu",
                OverlayVisibility.showGameOverlays(false, true, true, true, true, false));
        assertTrue("the HUD editor shows overlays with no session signal",
                OverlayVisibility.showGameOverlays(false, false, false, true, false, false));
    }

    @Test
    public void anOpenMenuAlwaysSuppressesOverlays() {
        assertFalse("pause menu must hide overlays",
                OverlayVisibility.showGameOverlays(true, true, false, false, true, true));
        assertFalse("showing a menu must hide overlays",
                OverlayVisibility.showGameOverlays(true, false, true, false, true, true));
    }

    @Test
    public void provenHudHookIsAuthoritative() {
        assertTrue(OverlayVisibility.showGameOverlays(true, false, false, false, true, true));
        assertFalse("hook proven and HUD closed means hidden",
                OverlayVisibility.showGameOverlays(false, false, false, false, true, true));
    }

    @Test
    public void unprovenHookFallsBackToTheSessionSoOverlaysDoNotVanish() {
        assertTrue("a running session with no HUD signal must still show overlays",
                OverlayVisibility.showGameOverlays(false, false, false, false, false, true));
        assertFalse("no session and no signal means nothing to show",
                OverlayVisibility.showGameOverlays(false, false, false, false, false, false));
    }

    @Test
    public void showEverywhereOverrideIsHonoured() {
        assertTrue(OverlayVisibility.showEverywhereOverride(true));
        assertFalse(OverlayVisibility.showEverywhereOverride(false));
    }
}
