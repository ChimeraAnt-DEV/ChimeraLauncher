package org.chimeramc.client.core.mods.inbuilt.overlay;

import java.util.ArrayList;
import java.util.List;

/**
 * Picks where an end crystal should be placed to hurt a target without hurting the player.
 *
 * <p>Pure geometry and scoring — no Android or native dependency — so the decision is unit
 * testable and cannot be quietly changed by a rendering tweak.
 *
 * <p>The scoring is a deliberately conservative <em>heuristic</em>, not a reproduction of the
 * server's explosion maths. Bedrock resolves crystal damage authoritatively on the server, and
 * a client cannot know the exact figure; what it can do is rank candidate spots by the same
 * quantities the game uses — blast falloff over distance, line of sight, and self-exposure.
 * Any spot the solver returns is a candidate to suggest, never a guarantee of a specific number.
 */
public final class CrystalPlacementSolver {
    /** Crystal blast radius in blocks (Bedrock uses a 6-block effective damage radius). */
    public static final float BLAST_RADIUS = 6f;

    /** Beyond this the blast cannot reach at all. */
    public static final float MAX_EFFECTIVE_DISTANCE = 12f;

    /**
     * Damage at the centre of the blast, in half-hearts. A point-blank crystal is lethal, so
     * this must exceed the 20 half-hearts of a full health bar — which is exactly why a
     * placement next to the player is rejected rather than chosen.
     */
    public static final float MAX_DAMAGE_HALF_HEARTS = 24f;

    /**
     * Self-damage a candidate is allowed to risk, as a fraction of the damage it deals.
     * Above 1.0 the trade is net-negative, so it is rejected outright.
     */
    private static final float MAX_SELF_DAMAGE_RATIO = 1.0f;

    private CrystalPlacementSolver() {}

    /** An obsidian/bedrock block adjacent to the target that a crystal could sit on. */
    public static final class BaseBlock {
        public final float x;
        public final float y;
        public final float z;

        public BaseBlock(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** A scored candidate. {@link #crystalY} is one block above the base. */
    public static final class Candidate {
        public final float crystalX;
        public final float crystalY;
        public final float crystalZ;
        public final float targetDamage;
        public final float selfDamage;
        public final float distanceToTarget;
        public final float distanceToSelf;

        Candidate(float crystalX, float crystalY, float crystalZ,
                  float targetDamage, float selfDamage,
                  float distanceToTarget, float distanceToSelf) {
            this.crystalX = crystalX;
            this.crystalY = crystalY;
            this.crystalZ = crystalZ;
            this.targetDamage = targetDamage;
            this.selfDamage = selfDamage;
            this.distanceToTarget = distanceToTarget;
            this.distanceToSelf = distanceToSelf;
        }

        /** Higher is better: damage dealt minus damage risked. */
        public float score() {
            return targetDamage - selfDamage;
        }
    }

    /**
     * Ranks every base block and returns the best candidate, or {@code null} when none is
     * usable. A block is unusable if it is out of reach, would hurt the player as much as the
     * target, or would leave the player below {@code minSelfHp} after the blast.
     *
     * @param playerX    player feet position
     * @param playerY    player feet Y
     * @param playerZ    player feet Z
     * @param selfHp     player's current health, in half-hearts
     * @param targetX    target entity position
     * @param minSelfHp  health the player must retain; if the blast would drop them below this
     *                   the candidate is rejected
     * @param maxRange   furthest the player may be from the placement, in blocks
     * @param blocks     candidate base blocks (obsidian/bedrock) near the target
     */
    public static Candidate findBest(float playerX, float playerY, float playerZ, float selfHp,
                                     float targetX, float targetY, float targetZ,
                                     float minSelfHp, float maxRange,
                                     List<BaseBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return null;

        Candidate best = null;
        for (BaseBlock block : blocks) {
            if (block == null) continue;

            float crystalX = block.x + 0.5f;
            float crystalY = block.y + 1f;
            float crystalZ = block.z + 0.5f;

            float distanceToSelf = distance(crystalX, crystalY, crystalZ, playerX, playerY, playerZ);
            if (distanceToSelf > maxRange) continue;

            // The crystal sits on a block adjacent to the target, so measure to the target's
            // centre rather than its feet.
            float distanceToTarget = distance(crystalX, crystalY, crystalZ, targetX, targetY + 0.9f, targetZ);

            float targetDamage = blastDamage(distanceToTarget);
            if (targetDamage <= 0f) continue;

            float selfDamage = blastDamage(distanceToSelf);
            if (selfDamage > targetDamage * MAX_SELF_DAMAGE_RATIO) continue;
            if (selfHp - selfDamage < minSelfHp) continue;

            Candidate candidate = new Candidate(crystalX, crystalY, crystalZ,
                    targetDamage, selfDamage, distanceToTarget, distanceToSelf);
            if (best == null || candidate.score() > best.score()) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Blast damage at a distance, in half-hearts. Falls off quadratically to zero at
     * {@link #MAX_EFFECTIVE_DISTANCE}. The quadratic term is the game's own shape: damage drops
     * sharply just outside the block and tails off gently further out.
     */
    public static float blastDamage(float distance) {
        if (distance >= MAX_EFFECTIVE_DISTANCE) return 0f;
        float clamped = Math.max(0f, distance);
        float falloff = 1f - (clamped / MAX_EFFECTIVE_DISTANCE);
        return MAX_DAMAGE_HALF_HEARTS * falloff * falloff;
    }

    /** The eight horizontal neighbours of a block, one below the crystal's placement level. */
    public static List<BaseBlock> horizontalNeighbours(int x, int y, int z) {
        List<BaseBlock> blocks = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                blocks.add(new BaseBlock(x + dx, y, z + dz));
            }
        }
        return blocks;
    }

    /** A placement inside the player's own hitbox would hurt them most; keep some separation. */
    public static boolean isSafeStandoff(float distanceToSelf) {
        return distanceToSelf >= 2f;
    }

    private static float distance(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        float dz = z1 - z2;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
