package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import org.chimeramc.client.core.mods.inbuilt.overlay.ArmorHudMod;
import org.chimeramc.client.core.mods.inbuilt.overlay.ArmorHudMod.Piece;
import org.chimeramc.client.core.mods.inbuilt.overlay.ArmorHudMod.Snapshot;

/**
 * Pins the armor HUD's data handling — in particular that "no data" is never rendered as
 * "full durability", which would read as a healthy armor set when nothing is known at all.
 */
public class ArmorHudModTest {

    private static final float EPS = 0.001f;

    @Test
    public void absentSnapshotIsNotAvailableAndHasNoTarget() {
        Snapshot absent = Snapshot.absent();
        assertFalse(absent.available);
        assertFalse(absent.hasTarget());
    }

    @Test
    public void absentIsDistinctFromWearingNothing() {
        Snapshot empty = Snapshot.of(new Piece[0], null);
        assertTrue("an empty-but-known loadout is available", empty.available);
        assertFalse("but it carries no target armor", empty.hasTarget());
    }

    @Test
    public void pieceFractionHandlesEmptyAndZeroMax() {
        assertEquals(1f, Piece.empty().fraction(), EPS);
        assertEquals(1f, Piece.of(0, 0).fraction(), EPS);
        assertEquals(0.5f, Piece.of(50, 100).fraction(), EPS);
    }

    @Test
    public void pieceFractionClampsToUnitRange() {
        assertEquals(1f, Piece.of(200, 100).fraction(), EPS);
        assertEquals(0f, Piece.of(-5, 100).fraction(), EPS);
    }

    @Test
    public void lowAndMidThresholdsMatchTheHudColours() {
        assertTrue(Piece.of(25, 100).isLow());
        assertFalse(Piece.of(25, 100).isMid());
        assertTrue(Piece.of(40, 100).isMid());
        assertFalse(Piece.of(40, 100).isLow());
        assertFalse(Piece.of(90, 100).isLow());
        assertFalse(Piece.of(90, 100).isMid());
        // An empty piece is not "low" — it is absent, and the HUD draws it differently.
        assertFalse(Piece.empty().isLow());
    }

    @Test
    public void snapshotNormalisesToFourSlots() {
        Snapshot snapshot = Snapshot.of(
                new Piece[]{Piece.of(1, 2)},
                null);
        assertEquals(ArmorHudMod.SLOT_COUNT, snapshot.self.length);
        assertEquals(ArmorHudMod.SLOT_COUNT, snapshot.target.length);
        assertTrue(snapshot.self[0].present);
        assertFalse(snapshot.self[1].present);
        assertFalse(snapshot.target[0].present);
    }

    @Test
    public void hasTargetOnlyWhenSomeTargetPieceIsPresent() {
        Snapshot withTarget = Snapshot.of(null, new Piece[]{Piece.empty(), Piece.of(5, 10)});
        assertTrue(withTarget.hasTarget());

        Snapshot withoutTarget = Snapshot.of(null, new Piece[]{Piece.empty(), Piece.empty()});
        assertFalse(withoutTarget.hasTarget());
    }

    @Test
    public void buildTreatsZeroMaxAsAnEmptySlot() {
        Snapshot snapshot = ArmorHudMod.build(
                new int[]{100, 0, 0, 0},
                new int[]{200, 0, 0, 0},
                null,
                null, null, null);

        assertTrue(snapshot.self[0].present);
        assertEquals(0.5f, snapshot.self[0].fraction(), EPS);
        assertFalse(snapshot.self[1].present);
    }

    @Test
    public void buildToleratesShortArrays() {
        Snapshot snapshot = ArmorHudMod.build(
                new int[]{100}, new int[]{200}, null, null, null, null);
        assertTrue(snapshot.self[0].present);
        for (int i = 1; i < ArmorHudMod.SLOT_COUNT; i++) {
            assertFalse(snapshot.self[i].present);
        }
    }

    @Test
    public void enchantGlyphsAreStableAndUnknownIdsFallBack() {
        assertEquals('P', ArmorHudMod.glyphFor(ArmorHudMod.ENCHANT_PROTECTION));
        assertEquals('U', ArmorHudMod.glyphFor(ArmorHudMod.ENCHANT_UNBREAKING));
        assertEquals('?', ArmorHudMod.glyphFor(ArmorHudMod.ENCHANT_UNKNOWN));
        assertEquals('?', ArmorHudMod.glyphFor(999));
        assertEquals('?', ArmorHudMod.glyphFor(-1));
    }

    @Test
    public void readWithoutADataSourceReportsAbsent() {
        ArmorHudMod.setDataSource(null);
        assertFalse(ArmorHudMod.read().available);
    }

    @Test
    public void aThrowingDataSourceDoesNotEscape() {
        ArmorHudMod.setDataSource(() -> {
            throw new IllegalStateException("native reader exploded");
        });
        try {
            assertFalse("a broken provider must degrade to absent, not crash",
                    ArmorHudMod.read().available);
        } finally {
            ArmorHudMod.setDataSource(null);
        }
    }

    @Test
    public void aNullReturningDataSourceReportsAbsent() {
        ArmorHudMod.setDataSource(() -> null);
        try {
            assertFalse(ArmorHudMod.read().available);
        } finally {
            ArmorHudMod.setDataSource(null);
        }
    }

    @Test
    public void aRealDataSourceIsPassedThrough() {
        ArmorHudMod.setDataSource(() -> ArmorHudMod.build(
                new int[]{150, 0, 0, 0}, new int[]{300, 0, 0, 0}, null, null, null, null));
        try {
            Snapshot snapshot = ArmorHudMod.read();
            assertTrue(snapshot.available);
            assertTrue(snapshot.self[0].present);
        } finally {
            ArmorHudMod.setDataSource(null);
        }
    }
}
