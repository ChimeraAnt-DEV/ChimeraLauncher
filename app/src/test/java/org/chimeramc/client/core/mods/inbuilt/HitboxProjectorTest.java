package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector;
import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector.Camera;
import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector.Entity;
import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector.Frame;
import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector.Projected;
import org.chimeramc.client.core.mods.inbuilt.overlay.HitboxProjector.Scene;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Projection and guide placement for the Hitboxes module. No mocks: the projector is pure.
 *
 * <p>Camera convention under test: yaw 0 looks toward +Z, so an entity at +Z with yaw 0 is
 * straight ahead and projects to screen centre.
 */
public class HitboxProjectorTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    private static Camera cameraAt(float x, float y, float z, float yaw, float pitch) {
        return new Camera(x, y, z, yaw, pitch, 70f, WIDTH, HEIGHT);
    }

    private static Frame project(Camera camera, Entity... entities) {
        return HitboxProjector.project(new Scene(camera, Arrays.asList(entities)));
    }

    @Test
    public void anEntityStraightAheadProjectsAroundScreenCentre() {
        Camera camera = cameraAt(0f, 0f, 0f, 0f, 0f);
        Entity player = Entity.player(0f, 0f, 10f);
        Frame frame = project(camera, player);

        assertEquals(1, frame.entities.size());
        Projected projected = frame.entities.get(0);
        float centreX = (projected.box.left + projected.box.right) / 2f;
        assertEquals(WIDTH / 2f, centreX, 2f);
        assertTrue("a box ahead must be on screen", projected.box.width() > 0f);
        assertTrue(projected.box.height() > 0f);
    }

    @Test
    public void anEntityBehindTheCameraIsNotProjected() {
        Camera camera = cameraAt(0f, 0f, 0f, 0f, 0f);
        Frame frame = project(camera, Entity.player(0f, 0f, -10f));
        assertTrue("a box behind the camera has no screen rect", frame.entities.isEmpty());
    }

    @Test
    public void turningTheCameraMovesTheBoxOffCentre() {
        Entity player = Entity.player(0f, 0f, 10f);
        Frame ahead = project(cameraAt(0f, 0f, 0f, 0f, 0f), player);
        Frame turned = project(cameraAt(0f, 0f, 0f, 30f, 0f), player);
        float aheadCentre = (ahead.entities.get(0).box.left + ahead.entities.get(0).box.right) / 2f;
        float turnedCentre = (turned.entities.get(0).box.left + turned.entities.get(0).box.right) / 2f;
        assertTrue("turning right should push a +Z entity left", turnedCentre < aheadCentre);
    }

    @Test
    public void aNearerEntitySortsAfterAFartherOne() {
        Camera camera = cameraAt(0f, 0f, 0f, 0f, 0f);
        Frame frame = project(camera, Entity.player(0f, 0f, 20f), Entity.player(0f, 0f, 5f));
        assertEquals(2, frame.entities.size());
        assertTrue("far must sort before near so near paints on top",
                frame.entities.get(0).distance > frame.entities.get(1).distance);
    }

    /**
     * Screen Y grows downward, so a box whose base sits higher in the world must have a smaller
     * screen Y. Nothing else in the suite pins the up vector's sign.
     */
    @Test
    public void aHigherEntityProjectsHigherOnScreen() {
        Camera camera = cameraAt(0f, 0f, 0f, 0f, 0f);
        Frame low = project(camera, Entity.player(0f, 0f, 10f));
        Frame high = project(camera, Entity.player(0f, 5f, 10f));
        assertTrue("a box raised in the world must move up the screen",
                high.entities.get(0).box.bottom < low.entities.get(0).box.bottom);
    }

    /** Pitch up should move a fixed world point down the screen. */
    @Test
    public void pitchingUpMovesAFixedEntityDownTheScreen() {
        Entity player = Entity.player(0f, 0f, 10f);
        Frame level = project(cameraAt(0f, 0f, 0f, 0f, 0f), player);
        Frame up = project(cameraAt(0f, 0f, 0f, 0f, 20f), player);
        assertTrue("looking up must move the entity down the screen",
                up.entities.get(0).box.top > level.entities.get(0).box.top);
    }

    @Test
    public void aNearerEntityIsProjectedLarger() {
        Camera camera = cameraAt(0f, 0f, 0f, 0f, 0f);
        Frame near = project(camera, Entity.player(0f, 0f, 5f));
        Frame far = project(camera, Entity.player(0f, 0f, 20f));
        assertTrue("a nearer box must cover more pixels",
                near.entities.get(0).box.width() > far.entities.get(0).box.width());
    }

    @Test
    public void theCrosshairMarksTheEntityItPointsAt() {
        Camera camera = cameraAt(0f, 0f, 0f, 0f, 0f);
        Frame frame = project(camera, Entity.player(0f, 0f, 10f), Entity.player(10f, 0f, 10f));
        Projected straight = frame.entities.get(0).distance < frame.entities.get(1).distance
                ? frame.entities.get(1) : frame.entities.get(0);
        // Find each by centre to avoid depending on the sort.
        for (Projected projected : frame.entities) {
            boolean centred = Math.abs((projected.box.left + projected.box.right) / 2f - WIDTH / 2f) < 2f;
            assertEquals("only the entity under the crosshair is aimed at", centred, projected.aimedAt);
        }
        assertNotNull(straight);
    }

    @Test
    public void aPlayerGetsACritLineAndAComboBox() {
        Frame frame = project(cameraAt(0f, 0f, 0f, 0f, 0f), Entity.player(0f, 0f, 10f));
        Projected player = frame.entities.get(0);
        assertNotNull(player.critY);
        assertNotNull(player.comboBox);

        assertTrue("the crit line sits near the top of the box",
                player.critY > player.box.top && player.critY < player.box.top + player.box.height() * 0.25f);
        assertTrue("the combo box is inside the player box",
                player.comboBox.top >= player.box.top && player.comboBox.bottom <= player.box.bottom);
        assertTrue("the combo box is narrower than the full box",
                player.comboBox.width() < player.box.width());
        assertEquals("the combo box is horizontally centred",
                (player.box.left + player.box.right) / 2f,
                (player.comboBox.left + player.comboBox.right) / 2f, 0.01f);
    }

    @Test
    public void onlyPlayersGetCombatGuides() {
        Frame frame = project(cameraAt(0f, 0f, 0f, 0f, 0f),
                Entity.mob(0f, 0f, 10f, 0.9f, 1.4f),
                Entity.droppedItem(2f, 0f, 10f),
                Entity.projectile(-2f, 1f, 10f));
        assertEquals(3, frame.entities.size());
        for (Projected projected : frame.entities) {
            assertNull(projected.critY);
            assertNull(projected.comboBox);
        }
    }

    @Test
    public void theLookLineAlwaysRunsFromBelowCentreToTheCentre() {
        Frame frame = project(cameraAt(0f, 0f, 0f, 0f, 0f));
        assertNotNull(frame.lookLine);
        assertEquals(WIDTH / 2f, frame.lookLine.left, 0.01f);
        assertEquals(HEIGHT / 2f, frame.lookLine.top, 0.01f);
        assertEquals(WIDTH / 2f, frame.lookLine.right, 0.01f);
        assertTrue("the line hangs below the crosshair", frame.lookLine.bottom > frame.lookLine.top);
    }

    /** Looking straight up makes forward parallel to world up; the basis must not collapse. */
    @Test
    public void aDegenerateCameraBasisStillProjects() {
        Frame frame = project(cameraAt(0f, 0f, 0f, 0f, 89.9f), Entity.player(0f, 0f, 10f));
        assertNotNull("the frame must still be produced", frame);
    }

    @Test
    public void aNullSceneProducesAnEmptyFrameRatherThanCrashing() {
        Frame frame = HitboxProjector.project(null);
        assertNotNull(frame);
        assertTrue(frame.entities.isEmpty());
    }

    @Test
    public void crosshairHitsRejectsAnEntityOutsideTheRay() {
        Camera camera = cameraAt(0f, 0f, 0f, 0f, 0f);
        float[] forward = camera.forward();
        assertTrue(HitboxProjector.crosshairHits(Entity.player(0f, 0f, 10f), camera, forward));
        assertFalse(HitboxProjector.crosshairHits(Entity.player(50f, 0f, 10f), camera, forward));
    }

    @Test
    public void crosshairHitsRejectsAnEntityBehindTheCamera() {
        Camera camera = cameraAt(0f, 0f, 0f, 0f, 0f);
        assertFalse(HitboxProjector.crosshairHits(Entity.player(0f, 0f, -10f), camera, forward(camera)));
    }

    private static float[] forward(Camera camera) {
        return camera.forward();
    }

    @Test
    public void entityGeometryIsSane() {
        Entity player = Entity.player(1f, 2f, 3f);
        assertEquals(0.7f, player.minX(), 0.001f);
        assertEquals(1.3f, player.maxX(), 0.001f);
        assertEquals(2f, player.minY(), 0.001f);
        assertEquals(3.8f, player.maxY(), 0.001f);
    }

    @Test
    public void projectionHandlesAnEmptyEntityList() {
        Frame frame = HitboxProjector.project(new Scene(cameraAt(0f, 0f, 0f, 0f, 0f),
                Collections.emptyList()));
        assertTrue(frame.entities.isEmpty());
        assertNotNull(frame.lookLine);
    }

    @Test
    public void aLargeSceneProjectsEveryEntity() {
        Camera camera = cameraAt(0f, 0f, 0f, 0f, 0f);
        List<Entity> entities = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            entities.add(Entity.player((i % 8) - 4f, 0f, 8f + (i / 8) * 2f));
        }
        Frame frame = HitboxProjector.project(new Scene(camera, entities));
        assertEquals(40, frame.entities.size());
    }
}
