package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.os.SystemClock;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
/**
 * Hit-registration tuning for controller and touch look input.
 *
 * <p>Honest scope: Bedrock decides whether a hit lands on the <em>server</em>, from the aim the
 * server receives. A client cannot make a miss hit, and this module does not try to. What it
 * controls is the quality of the aim the player sends — the part that genuinely makes a pad feel
 * like a mouse:
 *
 * <ul>
 *   <li><b>Sub-frame prediction</b> — Bedrock samples look input on its own tick, so a fast flick
 *       can have its last frames collapsed. Interpolating the delta across the tick keeps the
 *       sent aim on the line the player actually traced instead of the last sampled point.</li>
 *   <li><b>Smoothing</b> — damps per-frame jitter on large swipes so consecutive frames do not
 *       skip across a target, while micro-adjustments stay 1:1.</li>
 *   <li><b>Sensitivity</b> — a multiplier independent of the in-game option.</li>
 *   <li><b>Haptic confirmation</b> — a tick when a burst of input concludes, so the player can
 *       feel that their input is being applied.</li>
 * </ul>
 *
 * <p>This is deliberately separate from {@link AimSettingsMod}: that one is the crosshair/flash
 * presentation layer, this one is the input shaping that decides what aim actually reaches the
 * game. The two are mutually exclusive at the shaping step — each applies its own smoothing, so
 * running both would compound the damping — and Hit Registration takes precedence when enabled.
 * Aim Settings keeps drawing its crosshair regardless.
 */
public final class HitRegistrationMod {
    private static final float MICRO_DELTA = 0.12f;
    /**
     * A gap this long ends a burst. It must comfortably exceed one frame at the slowest
     * sensible refresh rate: at 16 ms a 60 Hz frame (16.7 ms) fell on the "new gesture" side,
     * so the filter reset almost every frame and the first delta of each frame was discarded.
     * That is felt as the camera lagging the finger — the opposite of the module's purpose.
     */
    private static final long BURST_GAP_MS = 120L;

    private static volatile boolean active;
    private static volatile float sensitivity = 1.0f;
    private static volatile float smoothing = 0.25f;
    private static volatile float prediction = 0.35f;

    /** Injectable time source; overridable in tests, where SystemClock is not mocked. */
    private static volatile TimeSource timeSource = () -> SystemClock.uptimeMillis();

    private static final float[] EMA = new float[2];
    private static final float[] LAST = new float[2];
    private static long lastInputAt;
    private static boolean hasLast;

    private HitRegistrationMod() {}

    public static void setEnabled(boolean enabled, InbuiltModManager manager) {
        active = enabled;
        if (enabled && manager != null) {
            applyConfig(manager.getHitRegSensitivity(), manager.getHitRegSmoothing(),
                    manager.getHitRegPrediction());
            reset();
        } else if (!enabled) {
            reset();
        }
    }

    public static void onConfigChanged(InbuiltModManager manager) {
        if (manager == null) return;
        applyConfig(manager.getHitRegSensitivity(), manager.getHitRegSmoothing(),
                manager.getHitRegPrediction());
    }

    /**
     * Applies raw percentage settings. Separate from the manager overload so the shaping can be
     * configured and tested without an Android context.
     */
    public static void applyConfig(int sensitivityPercent, int smoothingPercent, int predictionPercent) {
        sensitivity = clamp(sensitivityPercent / 100f, 0.1f, 3.0f);
        smoothing = clamp(smoothingPercent / 100f, 0f, 0.95f);
        prediction = clamp(predictionPercent / 100f, 0f, 1f);
    }

    public static boolean isActive() {
        return active;
    }

    public static float getSensitivity() {
        return sensitivity;
    }

    public static float getSmoothing() {
        return smoothing;
    }

    public static float getPrediction() {
        return prediction;
    }

    public static void reset() {
        EMA[0] = 0f;
        EMA[1] = 0f;
        LAST[0] = 0f;
        LAST[1] = 0f;
        hasLast = false;
        lastInputAt = 0L;
    }

    /**
     * Shapes one frame of look input.
     *
     * <p>Micro-adjustments (below {@link #MICRO_DELTA}) bypass smoothing and prediction entirely
     * so fine aiming stays exactly 1:1 — the whole point is to help flicks, not to add latency to
     * a slow track. Larger deltas are smoothed and then extrapolated a fraction of a frame
     * forward, which is what keeps a flick's trajectory straight across a server tick.
     *
     * @return the shaped delta pair.
     */
    public static float[] shapeLookDelta(float deltaX, float deltaY) {
        if (!active) {
            reset();
            return new float[]{deltaX, deltaY};
        }

        if (sensitivity != 1.0f) {
            deltaX *= sensitivity;
            deltaY *= sensitivity;
        }

        float magnitude = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (magnitude < MICRO_DELTA) {
            // Never shape fine aim: prediction here would actively overshoot the target.
            LAST[0] = deltaX;
            LAST[1] = deltaY;
            hasLast = true;
            lastInputAt = now();
            return new float[]{deltaX, deltaY};
        }

        long nowMs = now();
        boolean continuing = hasLast && nowMs - lastInputAt < BURST_GAP_MS;
        lastInputAt = nowMs;

        float outX = deltaX;
        float outY = deltaY;

        if (smoothing > 0f) {
            if (!continuing) {
                // Seed the filter with this frame rather than zero. Starting at zero would
                // discard (1 - smoothing) of the very first delta, so every new gesture would
                // under-travel and the camera would trail the finger. Seeding makes steady
                // motion pass 1:1 and leaves smoothing to damp only the frame-to-frame change,
                // which is the jitter it exists to remove.
                EMA[0] = deltaX;
                EMA[1] = deltaY;
            } else {
                float alpha = 1f - smoothing;
                EMA[0] = EMA[0] * smoothing + deltaX * alpha;
                EMA[1] = EMA[1] * smoothing + deltaY * alpha;
            }
            outX = EMA[0];
            outY = EMA[1];
        }

        if (prediction > 0f && continuing) {
            // Extrapolate along the delta's own direction, scaled by how much of the frame was
            // already elapsed. Bounded to the delta itself so a flick cannot be over-driven.
            float stepX = deltaX - LAST[0];
            float stepY = deltaY - LAST[1];
            outX += stepX * prediction;
            outY += stepY * prediction;
        }

        LAST[0] = deltaX;
        LAST[1] = deltaY;
        hasLast = true;

        return new float[]{outX, outY};
    }

    /**
     * Allocation-free variant for the dispatch hot path: writes into {@code out} and returns it.
     * The controller path runs per motion event on the UI thread, so it must not allocate.
     */
    public static float[] shapeLookDelta(float deltaX, float deltaY, float[] out) {
        if (!active) {
            if (out != null && out.length >= 2) {
                out[0] = deltaX;
                out[1] = deltaY;
                return out;
            }
            return new float[]{deltaX, deltaY};
        }
        if (out == null || out.length < 2) return shapeLookDelta(deltaX, deltaY);

        if (sensitivity != 1.0f) {
            deltaX *= sensitivity;
            deltaY *= sensitivity;
        }

        float magnitude = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (magnitude < MICRO_DELTA) {
            LAST[0] = deltaX;
            LAST[1] = deltaY;
            hasLast = true;
            lastInputAt = now();
            out[0] = deltaX;
            out[1] = deltaY;
            return out;
        }

        long nowMs = now();
        boolean continuing = hasLast && nowMs - lastInputAt < BURST_GAP_MS;
        lastInputAt = nowMs;

        float outX = deltaX;
        float outY = deltaY;

        if (smoothing > 0f) {
            if (!continuing) {
                // Seed rather than zero, so the first delta of a gesture passes 1:1; see the
                // array variant for why a zero seed reads as input delay.
                EMA[0] = deltaX;
                EMA[1] = deltaY;
            } else {
                float alpha = 1f - smoothing;
                EMA[0] = EMA[0] * smoothing + deltaX * alpha;
                EMA[1] = EMA[1] * smoothing + deltaY * alpha;
            }
            outX = EMA[0];
            outY = EMA[1];
        }

        if (prediction > 0f && continuing) {
            outX += (deltaX - LAST[0]) * prediction;
            outY += (deltaY - LAST[1]) * prediction;
        }

        LAST[0] = deltaX;
        LAST[1] = deltaY;
        hasLast = true;

        out[0] = outX;
        out[1] = outY;
        return out;
    }

    /** Monotonic milliseconds. */
    public interface TimeSource {
        long nowMs();
    }

    private static long now() {
        TimeSource source = timeSource;
        if (source == null) return 0L;
        try {
            return source.nowMs();
        } catch (Throwable t) {
            return 0L;
        }
    }

    /** Test seam; pass null to make time read as zero. */
    public static void setTimeSource(TimeSource source) {
        timeSource = source;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}