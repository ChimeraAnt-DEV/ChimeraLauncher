package org.chimeramc.client.launcher.controller;

/**
 * Decides whether a stick that has crossed the anti-drift threshold is really being pushed.
 *
 * A worn stick does not just sit still off-centre; it wobbles. The wobble spends most frames
 * inside the dead zone and occasionally crosses it for a frame or two before falling back.
 * A pure "is the magnitude above the threshold" test lets every one of those crossings through,
 * which the player sees as the camera twitching on its own — the ghost drift this filter
 * exists to remove.
 *
 * So a crossing has to persist before it counts. Requiring {@link #ENTRY_EVENTS} consecutive
 * above-threshold reports rejects a one-frame spike while costing a genuine push a single
 * frame of delay, which is below what a player can perceive. A large magnitude bypasses the
 * requirement entirely: a fast flick is unmistakably intentional, and delaying it is exactly
 * the responsiveness the filter must not cost.
 *
 * The gate is mutable and advances once per event. It is only ever touched from the UI thread
 * inside {@code dispatchGenericMotionEvent}, so it is deliberately not synchronised — a lock
 * here would be taken on every controller event on the input-to-photon path.
 */
final class StickDriftGate {

    /**
     * Consecutive above-threshold reports before movement is allowed through.
     *
     * Two is the smallest number that rejects a one-frame spike. At the rate a gamepad reports
     * motion events that is roughly 8-33ms depending on the pad, short enough to feel immediate
     * and long enough to swallow the single-frame crossing that noise produces.
     */
    static final int ENTRY_EVENTS = 2;

    /**
     * A magnitude this far above the threshold is a deliberate flick, not noise, and skips the
     * persistence requirement. Scaling with the threshold keeps it clear of a well-worn pad's
     * widened dead zone, and the absolute floor keeps a tight threshold from treating an
     * ordinary push as a flick.
     */
    static final float IMPULSE_FLOOR = 0.30f;

    /** Multiplier that puts the bypass above the threshold even on a heavily drifting stick. */
    static final float IMPULSE_FACTOR = 1.5f;

    /**
     * Longest a sustained deflection may be held back, in milliseconds.
     *
     * Counting events alone is not safe. Many pads only deliver a motion event when an axis
     * <em>changes</em>, so a stick pushed to a steady position that clears the threshold can sit
     * on a single crossing with no second event ever arriving — the streak never reaches
     * {@link #ENTRY_EVENTS}, and the deflection is held back for as long as the player holds it.
     * That is exactly the input delay this gate must never cause.
     *
     * So a crossing is also released once it has been sustained for this long since the first
     * sample. The window is on the order of a controller frame, which still rejects a one-frame
     * noise spike (its samples land microseconds apart) while guaranteeing a held stick always
     * comes through.
     */
    static final float SUSTAIN_MS = 40f;

    private int streak;
    private long lastEventTime = Long.MIN_VALUE;
    private long streakStartTime = Long.MIN_VALUE;
    private boolean lastAllowed;

    /**
     * Whether this event's deflection should reach the game.
     *
     * Evaluating the same event time twice returns the same answer without advancing the
     * streak, so the dead-zone check and the event-rewrite path can both consult the gate for
     * one event without it counting as two.
     *
     * @param magnitude combined stick magnitude, already computed by the caller
     * @param threshold the anti-drift threshold this stick is measured against
     * @param eventTime this event's timestamp in milliseconds
     */
    boolean allow(float magnitude, float threshold, long eventTime) {
        if (!(magnitude > threshold)) {
            // Back inside the dead zone: the run is broken, so the next crossing starts over.
            reset();
            return false;
        }
        if (magnitude >= impulseBypass(threshold)) {
            reset();
            return true;
        }
        if (eventTime == lastEventTime) {
            return lastAllowed;
        }
        if (lastEventTime == Long.MIN_VALUE || eventTime < lastEventTime) {
            streakStartTime = eventTime;
        }
        lastEventTime = eventTime;
        streak++;
        // A held stick that only reports on change would otherwise never reach ENTRY_EVENTS.
        // Releasing on elapsed time guarantees the deflection comes through.
        long sustained = eventTime - streakStartTime;
        lastAllowed = streak >= ENTRY_EVENTS
                || (streakStartTime != Long.MIN_VALUE && sustained >= SUSTAIN_MS);
        return lastAllowed;
    }

    /** Above this magnitude the persistence requirement is skipped. */
    static float impulseBypass(float threshold) {
        return Math.max(IMPULSE_FLOOR, threshold * IMPULSE_FACTOR);
    }

    /** Forgets any partial run. Used when the profile changes so staleness cannot leak across. */
    void reset() {
        streak = 0;
        lastEventTime = Long.MIN_VALUE;
        streakStartTime = Long.MIN_VALUE;
        lastAllowed = false;
    }
}
