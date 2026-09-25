package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.chimeramc.client.core.mods.inbuilt.manager.InbuiltModManager;
import org.chimeramc.client.core.mods.inbuilt.model.ModIds;

/**
 * Top-of-screen hit-timing indicator for the Select Hit module.
 *
 * <p>A deliberately small pill centred at the top of the screen: green while a hit will land,
 * red while the post-hit window is still open. It is sized so it does not cover the crosshair
 * area or the hotbar, and it carries the combo count beside it.
 *
 * <p>Draggable in HUD-editor mode only, matching the other overlays; position persists through
 * {@link InbuiltModManager#setOverlayPosition}.
 */
public final class HitTimingOverlay {
    private static final float DRAG_THRESHOLD = 10f;
    private static final int REFRESH_MS = 33;

    private final Activity activity;
    private final WindowManager windowManager;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private View overlayView;
    private WindowManager.LayoutParams wmParams;
    private boolean isShowing;
    private boolean isHudEditorMode;
    private boolean isLocked;

    private float initialX, initialY, initialTouchX, initialTouchY;
    private boolean isDragging;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isShowing) return;
            if (overlayView != null) overlayView.invalidate();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    public HitTimingOverlay(Activity activity) {
        this.activity = activity;
        this.windowManager = (WindowManager) activity.getSystemService(Activity.WINDOW_SERVICE);
    }

    public void show(int startX, int startY) {
        if (isShowing || activity.isFinishing() || activity.isDestroyed()) return;
        HitTimingView view = new HitTimingView(activity);
        try {
            wmParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            wmParams.gravity = Gravity.TOP | Gravity.START;
            OverlayBounds.Position position = OverlayBounds.clampPosition(activity, view, startX, startY);
            wmParams.x = position.x;
            wmParams.y = position.y;
            wmParams.token = activity.getWindow().getDecorView().getWindowToken();
            view.setOnTouchListener(this::handleTouch);
            windowManager.addView(view, wmParams);
            overlayView = view;
        } catch (Exception e) {
            wmParams = null;
            ViewGroup root = activity.findViewById(android.R.id.content);
            if (root == null) return;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.TOP | Gravity.START;
            OverlayBounds.Position position = OverlayBounds.clampPosition(activity, view, startX, startY);
            params.leftMargin = position.x;
            params.topMargin = position.y;
            view.setOnTouchListener(this::handleTouchFallback);
            root.addView(view, params);
            overlayView = view;
        }
        isShowing = true;
        isLocked = InbuiltModManager.getInstance(activity).isOverlayLocked(ModIds.HIT_TIMING);
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
        isLocked = InbuiltModManager.getInstance(activity).isOverlayLocked(ModIds.HIT_TIMING);
        if (overlayView != null) overlayView.invalidate();
    }

    public void setHudEditorMode(boolean active) {
        isHudEditorMode = active;
    }

    public void setOverlayVisibility(int visibility) {
        if (overlayView != null && overlayView.getVisibility() != visibility) {
            overlayView.setVisibility(visibility);
        }
    }

    public void updatePosition(int x, int y) {
        if (overlayView == null) return;
        OverlayBounds.Position position = OverlayBounds.clampPosition(activity, overlayView, x, y);
        if (wmParams != null && windowManager != null) {
            wmParams.x = position.x;
            wmParams.y = position.y;
            windowManager.updateViewLayout(overlayView, wmParams);
        } else if (overlayView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            params.leftMargin = position.x;
            params.topMargin = position.y;
            overlayView.setLayoutParams(params);
        }
    }

    private boolean handleTouch(View v, MotionEvent event) {
        return handleTouchInternal(event, wmParams != null ? wmParams.x : 0, wmParams != null ? wmParams.y : 0);
    }

    private boolean handleTouchFallback(View v, MotionEvent event) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
        return handleTouchInternal(event, params.leftMargin, params.topMargin);
    }

    private boolean handleTouchInternal(MotionEvent event, int baseX, int baseY) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isHudEditorMode) {
                    InbuiltOverlayManager manager = InbuiltOverlayManager.getInstance();
                    if (manager != null) manager.selectHudEditorDisplay(ModIds.HIT_TIMING);
                }
                initialX = baseX;
                initialY = baseY;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                isDragging = false;
                if (overlayView != null && overlayView.getParent() != null) {
                    overlayView.getParent().requestDisallowInterceptTouchEvent(isHudEditorMode);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - initialTouchX;
                float dy = event.getRawY() - initialTouchY;
                if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                    if (isHudEditorMode) isDragging = true;
                }
                if (isDragging && isHudEditorMode) {
                    updatePosition((int) (initialX + dx), (int) (initialY + dy));
                }
                return isHudEditorMode || !isDragging;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging && isHudEditorMode) savePosition();
                isDragging = false;
                if (overlayView != null && overlayView.getParent() != null) {
                    overlayView.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            default:
                return false;
        }
    }

    private void savePosition() {
        if (overlayView == null) return;
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        if (wmParams != null) {
            manager.setOverlayPosition(ModIds.HIT_TIMING, wmParams.x, wmParams.y);
        } else if (overlayView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) overlayView.getLayoutParams();
            manager.setOverlayPosition(ModIds.HIT_TIMING, params.leftMargin, params.topMargin);
        }
    }

    private final class HitTimingView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF pill = new RectF();
        private final RectF bar = new RectF();
        private final float density;

        HitTimingView(Context context) {
            super(context);
            density = getResources().getDisplayMetrics().density;
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setFakeBoldText(true);
            textPaint.setTextSize(11f * density);
            setAlpha(0.95f);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            float w = 96f * density;
            float h = 26f * density;
            setMeasuredDimension((int) w, (int) h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            HitTimingSolver.Decision decision =
                    HitTimingMod.evaluate(SystemClock.uptimeMillis());
            boolean green = decision.isGreen();
            int color = green ? 0xFF2ECC71 : 0xFFE74C3C;

            float w = getWidth();
            float h = getHeight();
            float radius = h / 2f;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99000000);
            pill.set(0f, 0f, w, h);
            canvas.drawRoundRect(pill, radius, radius, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            paint.setColor(color);
            canvas.drawRoundRect(pill, radius, radius, paint);

            // A slim progress bar along the bottom edge shows how much of the window has
            // elapsed, so the player can see the rhythm rather than only the binary state.
            if (HitTimingMod.isShowTimingBar()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(color);
                float barInset = 3f * density;
                float barHeight = 3f * density;
                bar.set(barInset, h - barInset - barHeight, barInset + (w - barInset * 2) * decision.progress,
                        h - barInset);
                canvas.drawRoundRect(bar, barHeight / 2f, barHeight / 2f, paint);
            }

            String label;
            if (green) {
                label = HitTimingMod.isShowCombo() && decision.combo > 0
                        ? "HIT  ·  " + decision.combo + "x"
                        : "HIT";
            } else {
                label = "WAIT " + decision.remainingMs;
            }
            textPaint.setColor(color);
            float baseline = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText(label, w / 2f, baseline, textPaint);
        }
    }
}
