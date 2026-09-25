package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import android.os.Handler;
import android.os.Looper;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;

/**
 * Full-screen overlay that draws the Hitboxes module's projected frame.
 *
 * <p>Unlike the other overlays this one is not a draggable widget: the boxes are positioned in
 * world space, so the whole screen is the canvas and there is nothing to move. It is
 * deliberately non-touchable so it can never eat a tap or a look gesture.
 *
 * <p>Colour contract, kept in one place because the module's value is that the colours mean
 * something: entity boxes are white, a box the crosshair is on turns blue, the critical-hit line
 * is red (blue when aimed at), and the combo box is red (blue when aimed at).
 */
public final class HitboxOverlay {
    private static final int REFRESH_MS = 33;

    private static final int COLOR_ENTITY = 0xFFF2F2F2;
    private static final int COLOR_AIMED = 0xFF3DA9FC;
    private static final int COLOR_CRIT = 0xFFE74C3C;
    private static final int COLOR_COMBO = 0xFFE74C3C;

    private final Activity activity;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View overlayView;
    private WindowManager.LayoutParams wmParams;
    private boolean isShowing;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isShowing) return;
            if (overlayView != null) overlayView.invalidate();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    public HitboxOverlay(Activity activity) {
        this.activity = activity;
        this.windowManager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
    }

    public void show() {
        if (isShowing || activity.isFinishing() || activity.isDestroyed()) return;
        HitboxView view = new HitboxView(activity);
        try {
            wmParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            wmParams.gravity = Gravity.TOP | Gravity.START;
            wmParams.token = activity.getWindow().getDecorView().getWindowToken();
            windowManager.addView(view, wmParams);
            overlayView = view;
        } catch (Exception e) {
            wmParams = null;
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
            view.setClickable(false);
            view.setFocusable(false);
            root.addView(view, params);
            overlayView = view;
        }
        isShowing = true;
        handler.post(refreshRunnable);
    }

    public void hide() {
        if (!isShowing) return;
        isShowing = false;
        handler.removeCallbacks(refreshRunnable);
        try {
            if (wmParams != null && windowManager != null && overlayView != null) {
                windowManager.removeView(overlayView);
            } else if (overlayView != null) {
                ViewGroup root = activity.findViewById(android.R.id.content);
                if (root != null) root.removeView(overlayView);
            }
        } catch (Exception ignored) {
        }
        overlayView = null;
        wmParams = null;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void applyConfigurationChanges() {
        if (overlayView != null) overlayView.invalidate();
    }

    public void setHudEditorMode(boolean active) {
        // Nothing to position, so HUD-editor mode has no effect on this overlay.
    }

    public void setOverlayVisibility(int visibility) {
        if (overlayView != null && overlayView.getVisibility() != visibility) {
            overlayView.setVisibility(visibility);
        }
    }

    private final class HitboxView extends View {
        private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HitboxView(Context context) {
            super(context);
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(Math.max(1.5f, getResources().getDisplayMetrics().density));
            fillPaint.setStyle(Paint.Style.FILL);
            setClickable(false);
            setFocusable(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            HitboxProjector.Frame frame = HitboxMod.readFrame();
            if (frame == null) return;

            if (HitboxMod.isShowLookLine() && frame.lookLine != null) {
                boxPaint.setColor(COLOR_AIMED);
                boxPaint.setStrokeWidth(2f * getResources().getDisplayMetrics().density);
                canvas.drawLine(frame.lookLine.left, frame.lookLine.top,
                        frame.lookLine.right, frame.lookLine.bottom, boxPaint);
                boxPaint.setStrokeWidth(Math.max(1.5f, getResources().getDisplayMetrics().density));
            }

            for (HitboxProjector.Projected projected : frame.entities) {
                int color = projected.aimedAt ? COLOR_AIMED : COLOR_ENTITY;
                boxPaint.setColor(color);
                canvas.drawRect(projected.box.left, projected.box.top,
                        projected.box.right, projected.box.bottom, boxPaint);

                if (projected.kind == HitboxProjector.Kind.PLAYER) {
                    if (HitboxMod.isShowCritLine() && projected.critY != null) {
                        boxPaint.setColor(projected.aimedAt ? COLOR_AIMED : COLOR_CRIT);
                        canvas.drawLine(projected.box.left, projected.critY,
                                projected.box.right, projected.critY, boxPaint);
                    }
                    if (HitboxMod.isShowComboBox() && projected.comboBox != null) {
                        int comboColor = projected.aimedAt ? COLOR_AIMED : COLOR_COMBO;
                        boxPaint.setColor(comboColor);
                        HitboxProjector.Rect combo = projected.comboBox;
                        canvas.drawRect(combo.left, combo.top, combo.right, combo.bottom, boxPaint);
                        // A faint wash makes the best-combo target readable at a glance without
                        // hiding the entity behind it.
                        fillPaint.setColor((comboColor & 0x00FFFFFF) | 0x33000000);
                        canvas.drawRect(combo.left, combo.top, combo.right, combo.bottom, fillPaint);
                    }
                }
            }
        }
    }
}
