package org.chimeramc.client.ui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.launcher.controller.ControllerType;
import org.junit.Test;

import java.util.List;

/**
 * The illustration is drawn with Canvas calls, so a control placed outside its shell or on
 * top of another cannot be seen on a build machine. These tests check the geometry instead,
 * which is the part that keeps regressing: a rounded rectangle tested as a circle reported a
 * false containment failure, and a shell-wide bumper poke-through shipped twice.
 */
public class ControllerLayoutTest {

    private static final ControllerType[] PADS = {
            ControllerType.XBOX, ControllerType.DS4, ControllerType.DUAL_SENSE
    };

    private static boolean insideShell(ControllerType type, float dx, float dy) {
        float[] poly = ControllerLayout.shellPolygon(type, 32);
        int n = poly.length / 2;
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = poly[i * 2], yi = poly[i * 2 + 1];
            float xj = poly[j * 2], yj = poly[j * 2 + 1];
            if ((yi > dy) != (yj > dy)
                    && dx < (xj - xi) * (dy - yi) / (yj - yi) + xi) {
                in = !in;
            }
        }
        return in;
    }

    /**
     * Whether the region's drawn outline sits inside the shell.
     *
     * Triggers are excluded by the caller: their arc extends past the silhouette and is
     * clipped to it on draw, so they can never be fully contained.
     */
    private static boolean regionInsideShell(ControllerType type, ControllerLayout.Spec spec) {
        float dx = ControllerLayout.regionDx(spec);
        float dy = ControllerLayout.regionDy(spec);
        float hw = spec.halfWidth();
        float hh = spec.halfHeight();
        for (int i = 0; i < 32; i++) {
            double a = Math.PI * 2 * i / 32;
            float px = dx + (float) Math.cos(a) * hw;
            float py = dy + (float) Math.sin(a) * hh;
            if (!insideShell(type, px, py)) return false;
        }
        return true;
    }

    @Test
    public void everyRegionIsAPlausibleSize() {
        for (ControllerType type : PADS) {
            List<ControllerLayout.Spec> specs = ControllerLayout.regions(type);
            assertFalse(type + " has no regions", specs.isEmpty());
            for (ControllerLayout.Spec s : specs) {
                assertTrue(type + " " + s.id + " radius too small", s.radius > 0.005f);
                assertTrue(type + " " + s.id + " radius too large", s.radius < 0.12f);
                assertTrue(type + " " + s.id + " x off-grid", s.x > 0.1f && s.x < 0.9f);
                assertTrue(type + " " + s.id + " y off-grid", s.y > 0.05f && s.y < 0.95f);
            }
        }
    }

    @Test
    public void everyRegionSitsInsideTheShell() {
        for (ControllerType type : PADS) {
            List<ControllerLayout.Spec> specs = ControllerLayout.regions(type);
            for (ControllerLayout.Spec s : specs) {
                if (s.shape == ControllerLayout.Shape.TRIGGER) continue;
                assertTrue(type + " region '" + s.id + "' is drawn outside the shell",
                        regionInsideShell(type, s));
            }
        }
    }

    @Test
    public void noTwoControlsOverlap() {
        for (ControllerType type : PADS) {
            List<ControllerLayout.Spec> specs = ControllerLayout.regions(type);
            for (int i = 0; i < specs.size(); i++) {
                for (int j = i + 1; j < specs.size(); j++) {
                    ControllerLayout.Spec a = specs.get(i);
                    ControllerLayout.Spec b = specs.get(j);
                    // A stick and its own decorative glow ring are concentric by design.
                    if (a.id.startsWith(b.id) || b.id.startsWith(a.id)) continue;
                    // A bumper and the trigger beside it sit at different depths on the
                    // shoulder, so their top-down silhouettes are meant to touch.
                    if (isShoulder(a.shape) && isShoulder(b.shape)
                            && a.shape != b.shape) {
                        continue;
                    }
                    float adx = ControllerLayout.regionDx(a), ady = ControllerLayout.regionDy(a);
                    float bdx = ControllerLayout.regionDx(b), bdy = ControllerLayout.regionDy(b);
                    boolean clear;
                    if (isRound(a.shape) && isRound(b.shape)) {
                        // Round buttons are diagonally adjacent, so an axis-aligned gap test
                        // reports A and B as overlapping when they visibly are not.
                        float dist = (float) Math.hypot(adx - bdx, ady - bdy);
                        clear = dist > a.halfWidth() + b.halfWidth();
                    } else {
                        float gapX = Math.abs(adx - bdx) - a.halfWidth() - b.halfWidth();
                        float gapY = Math.abs(ady - bdy) - a.halfHeight() - b.halfHeight();
                        clear = gapX > 0f || gapY > 0f;
                    }
                    assertTrue(type + " regions '" + a.id + "' and '" + b.id + "' overlap", clear);
                }
            }
        }
    }

    @Test
    public void xboxIsAsymmetricAndPlayStationIsNot() {
        List<ControllerLayout.Spec> xbox = ControllerLayout.regions(ControllerType.XBOX);
        List<ControllerLayout.Spec> ds4 = ControllerLayout.regions(ControllerType.DS4);

        // Xbox swaps stick and d-pad positions between the two sides; the PlayStation pads
        // mirror. Getting this backwards is the giveaway that one pad's map was copied.
        assertTrue("Xbox sticks should sit at different heights",
                yOf(xbox, "ls") < yOf(xbox, "rs"));

        assertEquals(xOf(ds4, "ls"), 1f - xOf(ds4, "rs"), 0.001f);
        assertEquals(yOf(ds4, "ls"), yOf(ds4, "rs"), 0.001f);
    }

    @Test
    public void dualSenseAddsTheMuteBarAndDS4DoesNot() {
        List<ControllerLayout.Spec> ds4 = ControllerLayout.regions(ControllerType.DS4);
        List<ControllerLayout.Spec> dual = ControllerLayout.regions(ControllerType.DUAL_SENSE);
        assertTrue(hasId(dual, "mute"));
        assertFalse(hasId(ds4, "mute"));
        // The touchpad grows on the DualSense; that is the other visible difference.
        assertTrue(widthOf(dual, "touch") > widthOf(ds4, "touch"));
    }

    @Test
    public void triggersStayWithinReachOfTheShell() {
        // A trigger is clipped to the shell, so it only has to be close enough that the
        // visible sliver reads as part of the controller rather than a floating pill.
        for (ControllerType type : PADS) {
            float[] poly = ControllerLayout.shellPolygon(type, 32);
            float minY = Float.MAX_VALUE;
            for (int i = 1; i < poly.length; i += 2) minY = Math.min(minY, poly[i]);
            for (ControllerLayout.Spec s : ControllerLayout.regions(type)) {
                if (s.shape != ControllerLayout.Shape.TRIGGER) continue;
                float top = ControllerLayout.regionDy(s) - s.halfHeight();
                assertTrue(type + " trigger '" + s.id + "' sits clear of the shell",
                        top > minY - 0.30f);
                assertTrue(type + " trigger '" + s.id + "' sits buried in the shell",
                        top < minY + 0.20f);
            }
        }
    }

    @Test
    public void shellsFitInTheDrawableArea() {
        // The max extent of the shell must stay inside the scaled view box, otherwise the
        // grips are clipped at the sides.
        for (ControllerType type : PADS) {
            float[] poly = ControllerLayout.shellPolygon(type, 32);
            float maxAbs = 0f;
            for (float v : poly) maxAbs = Math.max(maxAbs, Math.abs(v));
            assertTrue(type + " shell is too wide for the view: " + maxAbs, maxAbs < 1.4f);
        }
    }

    private static boolean isShoulder(ControllerLayout.Shape shape) {
        return shape == ControllerLayout.Shape.BUMPER || shape == ControllerLayout.Shape.TRIGGER;
    }

    private static boolean isRound(ControllerLayout.Shape shape) {
        switch (shape) {
            case STICK:
            case STICK_RING:
            case DPAD:
            case FACE_XBOX:
            case FACE_DUAL:
            case CENTER_BUTTON:
            case PS_LOGO:
            case GUIDE:
                return true;
            default:
                return false;
        }
    }

    private static boolean hasId(List<ControllerLayout.Spec> specs, String id) {
        for (ControllerLayout.Spec s : specs) {
            if (id.equals(s.id)) return true;
        }
        return false;
    }

    private static float xOf(List<ControllerLayout.Spec> specs, String id) {
        for (ControllerLayout.Spec s : specs) {
            if (id.equals(s.id)) return s.x;
        }
        throw new AssertionError("no region " + id);
    }

    private static float yOf(List<ControllerLayout.Spec> specs, String id) {
        for (ControllerLayout.Spec s : specs) {
            if (id.equals(s.id)) return s.y;
        }
        throw new AssertionError("no region " + id);
    }

    private static float widthOf(List<ControllerLayout.Spec> specs, String id) {
        for (ControllerLayout.Spec s : specs) {
            if (id.equals(s.id)) return s.halfWidth();
        }
        throw new AssertionError("no region " + id);
    }
}
