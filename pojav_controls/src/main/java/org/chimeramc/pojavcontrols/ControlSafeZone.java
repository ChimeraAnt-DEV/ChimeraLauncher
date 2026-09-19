package org.chimeramc.pojavcontrols;

/**
 * The part of the screen where overlay controls can actually be touched, in the overlay's
 * own coordinate space.
 *
 * The launcher draws edge to edge and the theme sets
 * {@code windowLayoutInDisplayCutoutMode=shortEdges}, so a control at x=0 can end up under
 * the camera cutout or inside the edge-swipe strip the system claims for itself. Both cases
 * look fine in the editor and are unusable in game.
 *
 * This class is deliberately plain rectangle arithmetic with no Android types: reading the
 * insets out of {@link android.view.WindowInsets} stays in the calling view, so the clamping
 * rules here can be unit tested without a device.
 */
public final class ControlSafeZone {

    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    private final int fullWidth;
    private final int fullHeight;

    private ControlSafeZone(int left, int top, int right, int bottom, int fullWidth, int fullHeight) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.fullWidth = fullWidth;
        this.fullHeight = fullHeight;
    }

    /** The whole view, used before insets are known. */
    public static ControlSafeZone full(int width, int height) {
        int w = Math.max(0, width);
        int h = Math.max(0, height);
        return new ControlSafeZone(0, 0, w, h, w, h);
    }

    /**
     * Builds the usable area by stripping {@code inset*} from each edge.
     *
     * Insets are clamped to the bounds, and an inset pair that would consume the whole axis
     * is discarded instead of collapsing the area to nothing: a device reporting a nonsense
     * cutout must not leave the user unable to place a control at all.
     */
    public static ControlSafeZone of(int width, int height,
                                     int insetLeft, int insetTop, int insetRight, int insetBottom) {
        int w = Math.max(0, width);
        int h = Math.max(0, height);
        int l = clampInset(insetLeft, w);
        int t = clampInset(insetTop, h);
        int r = clampInset(insetRight, w);
        int b = clampInset(insetBottom, h);
        if (l + r >= w) {
            l = 0;
            r = 0;
        }
        if (t + b >= h) {
            t = 0;
            b = 0;
        }
        return new ControlSafeZone(l, t, w - r, h - b, w, h);
    }

    private static int clampInset(int inset, int extent) {
        if (inset <= 0) return 0;
        return Math.min(inset, extent);
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    /** True when no edge was inset, i.e. there is nothing to clamp against. */
    public boolean coversWholeView() {
        return left == 0 && top == 0 && right == fullWidth && bottom == fullHeight;
    }

    /**
     * Nudges a horizontal offset so {@code childWidth} fits inside the zone.
     * A control wider than the safe area is pinned to its left edge rather than pushed off
     * the other side.
     */
    public int clampX(int x, int childWidth) {
        int maxX = right - childWidth;
        if (maxX < left) return left;
        return Math.max(left, Math.min(x, maxX));
    }

    /** Nudges a vertical offset so {@code childHeight} fits inside the zone. */
    public int clampY(int y, int childHeight) {
        int maxY = bottom - childHeight;
        if (maxY < top) return top;
        return Math.max(top, Math.min(y, maxY));
    }
}
