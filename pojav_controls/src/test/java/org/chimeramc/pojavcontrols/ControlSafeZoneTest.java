package org.chimeramc.pojavcontrols;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Clamping rules for keeping touch controls out from under the display cutout and the
 * system gesture edges. These run without an Android runtime, which is the point of keeping
 * the arithmetic in {@link ControlSafeZone} instead of the views.
 */
public class ControlSafeZoneTest {

    @Test
    public void fullZoneKeepsWholeViewAndNeedsNoClamping() {
        ControlSafeZone zone = ControlSafeZone.full(1920, 1080);
        assertTrue(zone.coversWholeView());
        assertEquals(0, zone.left);
        assertEquals(0, zone.top);
        assertEquals(1920, zone.right);
        assertEquals(1080, zone.bottom);
        assertEquals(1920, zone.width());
        assertEquals(1080, zone.height());
    }

    @Test
    public void negativeAndZeroInsetsAreTreatedAsNone() {
        ControlSafeZone zone = ControlSafeZone.of(1000, 500, -10, 0, -3, 0);
        assertTrue(zone.coversWholeView());
    }

    @Test
    public void cutoutInsetsShrinkTheUsableRectangle() {
        ControlSafeZone zone = ControlSafeZone.of(1920, 1080, 90, 0, 40, 0);
        assertEquals(90, zone.left);
        assertEquals(0, zone.top);
        assertEquals(1880, zone.right);
        assertEquals(1080, zone.bottom);
        assertEquals(1790, zone.width());
    }

    @Test
    public void clampXPinsToTheLeftCutout() {
        ControlSafeZone zone = ControlSafeZone.of(1920, 1080, 90, 0, 40, 0);
        assertEquals(90, zone.clampX(0, 100));
        assertEquals(90, zone.clampX(-500, 100));
        assertEquals(500, zone.clampX(500, 100));
    }

    @Test
    public void clampXPinsToTheRightEdgeAllowingForChildWidth() {
        ControlSafeZone zone = ControlSafeZone.of(1920, 1080, 90, 0, 40, 0);
        // Right inset is 40, so the child's right edge cannot pass x = 1880.
        assertEquals(1780, zone.clampX(1900, 100));
        assertEquals(1780, zone.clampX(1780, 100));
        assertEquals(1700, zone.clampX(1700, 100));
    }

    @Test
    public void clampYPinsToTopAndBottomInsets() {
        ControlSafeZone zone = ControlSafeZone.of(1920, 1080, 0, 60, 0, 30);
        assertEquals(60, zone.clampY(0, 100));
        assertEquals(60, zone.clampY(-20, 100));
        assertEquals(950, zone.clampY(1000, 100));
        assertEquals(500, zone.clampY(500, 100));
    }

    @Test
    public void childWiderThanSafeAreaIsPinnedToItsLeftEdge() {
        ControlSafeZone zone = ControlSafeZone.of(200, 400, 90, 0, 90, 0);
        assertEquals(20, zone.width());
        assertEquals(90, zone.clampX(1000, 500));
        assertEquals(90, zone.clampX(-100, 500));
    }

    @Test
    public void nonsenseInsetsThatWouldConsumeTheAxisAreDiscarded() {
        // A device reporting cutout insets wider than the screen must not collapse the
        // usable area to zero, which would make a control impossible to place at all.
        ControlSafeZone zone = ControlSafeZone.of(100, 100, 80, 80, 80, 80);
        assertTrue(zone.coversWholeView());
    }

    @Test
    public void asymmetricNonsenseInsetsOnlyResetTheOffendingAxis() {
        // X is fully consumed by nonsense insets so it resets, but the valid top inset must
        // survive: a bogus horizontal reading must not throw away a real vertical one.
        ControlSafeZone zone = ControlSafeZone.of(1000, 500, 600, 40, 600, 20);
        assertEquals(0, zone.left);
        assertEquals(1000, zone.right);
        assertEquals(40, zone.top);
        assertEquals(480, zone.bottom);
        assertFalse(zone.coversWholeView());
        assertEquals(40, zone.clampY(0, 50));
    }

    @Test
    public void insetsAreClampedToTheViewExtent() {
        ControlSafeZone zone = ControlSafeZone.of(1000, 500, 0, 0, 999, 0);
        assertEquals(1, zone.right);
        assertEquals(1, zone.width());
    }

    @Test
    public void zeroSizedViewYieldsEmptyZoneWithoutCrashing() {
        ControlSafeZone zone = ControlSafeZone.of(0, 0, 10, 10, 10, 10);
        assertEquals(0, zone.width());
        assertEquals(0, zone.height());
        assertEquals(0, zone.clampX(50, 10));
        assertEquals(0, zone.clampY(50, 10));
    }
}
