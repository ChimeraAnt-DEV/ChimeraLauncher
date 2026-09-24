package org.chimeramc.client.core.mods.inbuilt.overlay;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;

/**
 * Armor durability HUD state and its game-data seam.
 *
 * <p>The HUD itself is pure rendering: it draws whatever {@link Snapshot} the current
 * {@link DataSource} publishes. Reading armor durability out of the running game is a native
 * concern (the preloader's pattern-scanned game hooks) and is deliberately not assumed here —
 * until a provider is installed, {@link #read()} reports "no data" and the overlay draws an
 * explicit placeholder rather than a fabricated or stale durability value.
 */
public final class ArmorHudMod {
    public static final int SLOT_HELMET = 0;
    public static final int SLOT_CHEST = 1;
    public static final int SLOT_LEGS = 2;
    public static final int SLOT_BOOTS = 3;
    public static final int SLOT_COUNT = 4;

    /** Icon glyphs for the armor enchantments worth showing at HUD scale, by id. */
    public static final int ENCHANT_UNKNOWN = 0;
    public static final int ENCHANT_PROTECTION = 1;
    public static final int ENCHANT_FIRE_PROTECTION = 2;
    public static final int ENCHANT_BLAST_PROTECTION = 3;
    public static final int ENCHANT_PROJECTILE_PROTECTION = 4;
    public static final int ENCHANT_THORNS = 5;
    public static final int ENCHANT_UNBREAKING = 6;
    public static final int ENCHANT_MENDING = 7;
    public static final int ENCHANT_RESPIRATION = 8;
    public static final int ENCHANT_AQUA_AFFINITY = 9;
    public static final int ENCHANT_FEATHER_FALLING = 10;
    public static final int ENCHANT_DEPTH_STRIDER = 11;
    public static final int ENCHANT_SOUL_SPEED = 12;
    public static final int ENCHANT_SWIFT_SNEAK = 13;

    private static final char[] ENCHANT_GLYPHS = {
            '?', 'P', 'F', 'B', 'R', 'T', 'U', 'M', 'W', 'A', 'D', 'S', 's', 'n'
    };

    private static volatile DataSource dataSource;
    private static volatile boolean active;
    private static volatile boolean showTarget;
    private static volatile boolean showEnchants;
    private static volatile boolean stacked;

    private ArmorHudMod() {}

    /** Supplies a durability snapshot each refresh. Returns {@link Snapshot#absent()} when it has none. */
    public interface DataSource {
        Snapshot read();
    }

    /** One armor piece. Immutable; {@link #present} false means the slot is empty. */
    public static final class Piece {
        public final boolean present;
        public final int durability;
        public final int maxDurability;
        public final int[] enchantIds;

        public Piece(boolean present, int durability, int maxDurability, int[] enchantIds) {
            this.present = present;
            this.durability = durability;
            this.maxDurability = maxDurability;
            this.enchantIds = enchantIds != null ? enchantIds : new int[0];
        }

        public static Piece empty() {
            return new Piece(false, 0, 0, null);
        }

        public static Piece of(int durability, int maxDurability, int... enchantIds) {
            return new Piece(true, durability, maxDurability, enchantIds);
        }

        /** Remaining durability as 0..1. A piece with no max reads as full, never as 0. */
        public float fraction() {
            if (!present || maxDurability <= 0) return 1f;
            return Math.max(0f, Math.min(1f, durability / (float) maxDurability));
        }

        public boolean isLow() {
            return present && fraction() <= 0.25f;
        }

        public boolean isMid() {
            return present && fraction() > 0.25f && fraction() <= 0.5f;
        }
    }

    /** A point-in-time view of self (and optionally target) armor. Immutable. */
    public static final class Snapshot {
        private static final Snapshot ABSENT = new Snapshot(false, null, null);

        public final boolean available;
        public final Piece[] self;
        public final Piece[] target;

        private Snapshot(boolean available, Piece[] self, Piece[] target) {
            this.available = available;
            this.self = self;
            this.target = target;
        }

        /** No game data yet — distinct from "wearing nothing". */
        public static Snapshot absent() {
            return ABSENT;
        }

        public static Snapshot of(Piece[] self, Piece[] target) {
            return new Snapshot(true, normalize(self), normalize(target));
        }

        private static Piece[] normalize(Piece[] pieces) {
            Piece[] out = new Piece[SLOT_COUNT];
            for (int i = 0; i < SLOT_COUNT; i++) {
                out[i] = pieces != null && i < pieces.length && pieces[i] != null
                        ? pieces[i]
                        : Piece.empty();
            }
            return out;
        }

        public boolean hasTarget() {
            if (target == null) return false;
            for (Piece piece : target) {
                if (piece.present) return true;
            }
            return false;
        }
    }

    public static void setDataSource(DataSource source) {
        dataSource = source;
    }

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            showTarget = manager.isArmorHudShowTarget();
            showEnchants = manager.isArmorHudShowEnchants();
            stacked = manager.isArmorHudStacked();
        }
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        showTarget = manager.isArmorHudShowTarget();
        showEnchants = manager.isArmorHudShowEnchants();
        stacked = manager.isArmorHudStacked();
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isShowTarget() {
        return active && showTarget;
    }

    public static boolean isShowEnchants() {
        return active && showEnchants;
    }

    public static boolean isStacked() {
        return stacked;
    }

    public static Snapshot read() {
        DataSource source = dataSource;
        if (source == null) return Snapshot.absent();
        try {
            Snapshot snapshot = source.read();
            return snapshot != null ? snapshot : Snapshot.absent();
        } catch (Throwable t) {
            // A misbehaving provider must not take the overlay down with it.
            return Snapshot.absent();
        }
    }

    /** Glyph for an enchantment id; '?' for ids the HUD has no icon for. */
    public static char glyphFor(int enchantId) {
        if (enchantId < 0 || enchantId >= ENCHANT_GLYPHS.length) return ENCHANT_GLYPHS[0];
        return ENCHANT_GLYPHS[enchantId];
    }

    /**
     * Convenience for native providers: builds a snapshot from flat per-slot arrays.
     * Durability of 0 with a max of 0 means "slot empty".
     */
    public static Snapshot build(int[] selfDur, int[] selfMax, int[][] selfEnch,
                                 int[] targetDur, int[] targetMax, int[][] targetEnch) {
        return Snapshot.of(pieces(selfDur, selfMax, selfEnch), pieces(targetDur, targetMax, targetEnch));
    }

    private static Piece[] pieces(int[] dur, int[] max, int[][] ench) {
        Piece[] out = new Piece[SLOT_COUNT];
        for (int i = 0; i < SLOT_COUNT; i++) {
            int d = dur != null && i < dur.length ? dur[i] : 0;
            int m = max != null && i < max.length ? max[i] : 0;
            out[i] = m > 0 ? Piece.of(d, m, ench != null && i < ench.length ? ench[i] : null) : Piece.empty();
        }
        return out;
    }
}
