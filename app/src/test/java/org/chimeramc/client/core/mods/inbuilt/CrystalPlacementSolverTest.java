package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.chimeramc.client.core.mods.inbuilt.overlay.CrystalPlacementSolver;
import org.chimeramc.client.core.mods.inbuilt.overlay.CrystalPlacementSolver.BaseBlock;
import org.chimeramc.client.core.mods.inbuilt.overlay.CrystalPlacementSolver.Candidate;

import java.util.ArrayList;
import java.util.List;

/**
 * Pins the crystal placement decision. These are pure geometry, so they run without a device
 * and without mocks.
 */
public class CrystalPlacementSolverTest {

    private static final float EPS = 0.001f;

    @Test
    public void blastDamageFallsOffWithDistance() {
        assertTrue(CrystalPlacementSolver.blastDamage(0f) > CrystalPlacementSolver.blastDamage(2f));
        assertTrue(CrystalPlacementSolver.blastDamage(2f) > CrystalPlacementSolver.blastDamage(5f));
        assertEquals(0f, CrystalPlacementSolver.blastDamage(CrystalPlacementSolver.MAX_EFFECTIVE_DISTANCE), EPS);
        assertEquals(0f, CrystalPlacementSolver.blastDamage(50f), EPS);
    }

    @Test
    public void blastDamageIsNeverNegative() {
        // A negative distance is nonsense input; it must clamp rather than blow up the score.
        assertTrue(CrystalPlacementSolver.blastDamage(-5f) > 0f);
    }

    @Test
    public void picksTheBlockNearestTheTarget() {
        List<BaseBlock> blocks = new ArrayList<>();
        // One block right beside the target, one far away.
        blocks.add(new BaseBlock(10, 4, 0));
        blocks.add(new BaseBlock(0, 4, 0));

        Candidate best = CrystalPlacementSolver.findBest(
                5f, 4f, 0f, 20f,      // player 5 blocks from the near block
                10f, 4f, 0f,          // target sits on the near block
                1f, 8f,
                blocks);

        assertNotNull(best);
        assertEquals(10.5f, best.crystalX, EPS);
        assertEquals(5f, best.crystalY, EPS);
        assertEquals(0.5f, best.crystalZ, EPS);
    }

    @Test
    public void rejectsASpotThePlayerCannotReach() {
        List<BaseBlock> blocks = new ArrayList<>();
        blocks.add(new BaseBlock(50, 4, 0));

        Candidate best = CrystalPlacementSolver.findBest(
                0f, 4f, 0f, 20f,
                50f, 4f, 0f,
                1f, 3f,   // max range 3 blocks
                blocks);

        assertNull(best);
    }

    @Test
    public void rejectsASpotThatWouldKillThePlayer() {
        List<BaseBlock> blocks = new ArrayList<>();
        // The player is standing right on the block, so the blast is point-blank on them.
        blocks.add(new BaseBlock(0, 4, 0));

        Candidate best = CrystalPlacementSolver.findBest(
                0.5f, 4f, 0.5f, 20f,
                1f, 4f, 0.5f,
                15f,   // must retain 15 half-hearts
                8f,
                blocks);

        assertNull(best);
    }

    @Test
    public void rejectsASpotThatCostsMoreThanItDeals() {
        List<BaseBlock> blocks = new ArrayList<>();
        // Player is closer to the blast than the target, so the trade is net-negative.
        blocks.add(new BaseBlock(0, 4, 0));

        Candidate best = CrystalPlacementSolver.findBest(
                0f, 4f, 0f, 20f,
                20f, 4f, 0f,   // target far outside the blast
                1f, 30f,
                blocks);

        assertNull(best);
    }

    @Test
    public void prefersTheHigherScoringOfTwoReachableSpots() {
        List<BaseBlock> blocks = new ArrayList<>();
        // Both reachable; the one nearer the target hits harder.
        blocks.add(new BaseBlock(10, 4, 0));
        blocks.add(new BaseBlock(14, 4, 0));

        Candidate best = CrystalPlacementSolver.findBest(
                12f, 4f, 0f, 20f,
                11f, 4f, 0f,
                1f, 10f,
                blocks);

        assertNotNull(best);
        assertTrue("expected the block closer to the target",
                best.distanceToTarget < 3f);
        assertTrue(best.score() > 0f);
    }

    @Test
    public void emptyOrNullBlocksYieldNoCandidate() {
        assertNull(CrystalPlacementSolver.findBest(0f, 0f, 0f, 20f, 1f, 0f, 0f, 1f, 5f, null));
        assertNull(CrystalPlacementSolver.findBest(0f, 0f, 0f, 20f, 1f, 0f, 0f, 1f, 5f, new ArrayList<>()));
    }

    @Test
    public void nullEntriesInTheBlockListAreSkipped() {
        List<BaseBlock> blocks = new ArrayList<>();
        blocks.add(null);
        blocks.add(new BaseBlock(10, 4, 0));

        Candidate best = CrystalPlacementSolver.findBest(
                0f, 4f, 0f, 20f, 10f, 4f, 0f, 1f, 20f, blocks);

        assertNotNull(best);
    }

    @Test
    public void horizontalNeighboursAreEightAndExcludeTheCentre() {
        List<BaseBlock> neighbours = CrystalPlacementSolver.horizontalNeighbours(0, 4, 0);
        assertEquals(8, neighbours.size());
        for (BaseBlock block : neighbours) {
            assertFalse("centre must not be a neighbour", block.x == 0 && block.z == 0);
            assertEquals(4f, block.y, EPS);
        }
    }

    @Test
    public void safeStandoffRequiresSeparation() {
        assertFalse(CrystalPlacementSolver.isSafeStandoff(0.5f));
        assertFalse(CrystalPlacementSolver.isSafeStandoff(1.9f));
        assertTrue(CrystalPlacementSolver.isSafeStandoff(2f));
        assertTrue(CrystalPlacementSolver.isSafeStandoff(5f));
    }
}
