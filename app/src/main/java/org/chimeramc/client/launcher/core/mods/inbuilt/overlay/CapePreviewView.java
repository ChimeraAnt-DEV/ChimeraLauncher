package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import org.chimeramc.client.core.cosmetics.CosmeticCatalog;
import org.chimeramc.client.ui.animation.DynamicAnim;

/**
 * A 2D Minecraft-style character wearing the equipped cape and accessory, drawn to scale in
 * flat blocky shapes rather than a full 3D render.
 *
 * The animated brand cape is the point: the Chimera mark crawls across the cloth, which means
 * this view redraws on a frame callback. That callback is only posted while the cape on show
 * is actually animated and the global "show animations" setting is on, so an unanimated cape
 * (or reduced-motion) costs nothing per frame.
 */
public class CapePreviewView extends View {

    /** Length of one crawl cycle in milliseconds. */
    private static final long CRAWL_PERIOD_MS = 3200L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private CosmeticCatalog.Cape cape;
    private CosmeticCatalog.Accessory accessory;

    private long animStartMs = 0L;
    private boolean animating = false;

    private final android.view.Choreographer.FrameCallback frameCallback = new android.view.Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!animating) return;
            invalidate();
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }
    };

    public CapePreviewView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void setCape(CosmeticCatalog.Cape cape) {
        this.cape = cape;
        syncAnimation();
        invalidate();
    }

    public void setAccessory(CosmeticCatalog.Accessory accessory) {
        this.accessory = accessory;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        syncAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            syncAnimation();
        } else {
            stopAnimation();
        }
    }

    /**
     * Runs the frame callback only when there is something to animate.
     *
     * A static cape or reduced-motion turns the callback off entirely rather than letting it
     * redraw identical frames forever.
     */
    private void syncAnimation() {
        boolean wantAnimate = cape != null && cape.animated && DynamicAnim.areAnimationsEnabled()
                && getVisibility() == VISIBLE && isAttachedToWindow();
        if (wantAnimate && !animating) {
            animating = true;
            animStartMs = android.os.SystemClock.uptimeMillis();
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback);
        } else if (!wantAnimate) {
            stopAnimation();
        }
    }

    private void stopAnimation() {
        animating = false;
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (cape == null) return;

        float w = getWidth();
        float h = getHeight();
        float unit = Math.min(w / 9f, h / 11f);
        if (unit <= 0f) return;

        float cx = w / 2f;
        float bodyTop = h * 0.30f;
        float bodyH = unit * 4.2f;
        float bodyW = unit * 2.4f;
        float headS = unit * 2.0f;

        // Cape sits behind the body, so it is drawn first.
        drawCape(canvas, cx, bodyTop, bodyW, bodyH, unit);

        // Legs
        paint.setColor(0xFF3B4A63);
        canvas.drawRect(cx - bodyW * 0.5f, bodyTop + bodyH, cx - bodyW * 0.08f,
                bodyTop + bodyH + unit * 2.6f, paint);
        canvas.drawRect(cx + bodyW * 0.08f, bodyTop + bodyH, cx + bodyW * 0.5f,
                bodyTop + bodyH + unit * 2.6f, paint);

        // Arms
        paint.setColor(0xFF6FA8D6);
        canvas.drawRect(cx - bodyW * 0.5f - unit * 0.9f, bodyTop + unit * 0.2f,
                cx - bodyW * 0.5f, bodyTop + bodyH * 0.85f, paint);
        canvas.drawRect(cx + bodyW * 0.5f, bodyTop + unit * 0.2f,
                cx + bodyW * 0.5f + unit * 0.9f, bodyTop + bodyH * 0.85f, paint);

        // Torso
        paint.setColor(0xFF57C1E8);
        canvas.drawRect(cx - bodyW * 0.5f, bodyTop, cx + bodyW * 0.5f, bodyTop + bodyH, paint);

        // Head + face
        float headTop = bodyTop - headS;
        paint.setColor(0xFF6FA8D6);
        canvas.drawRect(cx - headS * 0.5f, headTop, cx + headS * 0.5f, headTop + headS, paint);
        paint.setColor(0xFF20242B);
        canvas.drawRect(cx - headS * 0.30f, headTop + headS * 0.38f,
                cx - headS * 0.16f, headTop + headS * 0.52f, paint);
        canvas.drawRect(cx + headS * 0.16f, headTop + headS * 0.38f,
                cx + headS * 0.30f, headTop + headS * 0.52f, paint);

        drawAccessory(canvas, cx, headTop, headS, bodyTop, bodyH);
    }

    /** The cape cloth plus the crawling brand mark. */
    private void drawCape(Canvas canvas, float cx, float bodyTop, float bodyW, float bodyH, float unit) {
        float capeTop = bodyTop - unit * 0.15f;
        float capeBottom = bodyTop + bodyH + unit * 1.5f;
        // Trapezoid: narrow at the collar, wide at the hem, so it reads as hanging cloth.
        float collarHalf = bodyW * 0.62f;
        float hemHalf = bodyW * 1.05f;
        path.reset();
        path.moveTo(cx - collarHalf, capeTop);
        path.lineTo(cx + collarHalf, capeTop);
        path.lineTo(cx + hemHalf, capeBottom);
        path.lineTo(cx - hemHalf, capeBottom);
        path.close();

        paint.setShader(null);
        paint.setColor(cape.color);
        canvas.drawPath(path, paint);

        // Trim stroke around the cloth.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, unit * 0.12f));
        paint.setColor(cape.trimColor);
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        if (cape.branded) {
            drawCrawlingMark(canvas, cx, capeTop, capeBottom, hemHalf, unit);
        }
    }

    /**
     * The Chimera mark crawling across the cape.
     *
     * The mark is clipped to the cape cloth so the crawl reads as printed on the fabric rather
     * than sliding over the character. Its phase comes from wall-clock uptime, so the crawl is
     * the same speed regardless of frame rate.
     */
    private void drawCrawlingMark(Canvas canvas, float cx, float capeTop, float capeBottom,
                                  float hemHalf, float unit) {
        float phase = 0f;
        if (cape.animated && animating) {
            long elapsed = android.os.SystemClock.uptimeMillis() - animStartMs;
            phase = (elapsed % CRAWL_PERIOD_MS) / (float) CRAWL_PERIOD_MS;
        }
        float travel = hemHalf * 2f;
        float markX = cx - hemHalf + travel * phase;
        float markR = unit * 0.62f;
        float markY = capeTop + (capeBottom - capeTop) * 0.42f;

        int save = canvas.save();
        canvas.clipPath(path);
        paint.setShader(null);
        paint.setColor(cape.trimColor);
        // Ant silhouette: three stacked lobes and two antennae, matching the app mark's shape.
        canvas.drawCircle(markX, markY - markR * 0.75f, markR * 0.5f, paint);
        canvas.drawCircle(markX, markY, markR * 0.62f, paint);
        canvas.drawCircle(markX, markY + markR * 0.95f, markR * 0.85f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, unit * 0.10f));
        canvas.drawLine(markX - markR * 0.3f, markY - markR * 1.1f,
                markX - markR * 0.7f, markY - markR * 1.7f, paint);
        canvas.drawLine(markX + markR * 0.3f, markY - markR * 1.1f,
                markX + markR * 0.7f, markY - markR * 1.7f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(save);
    }

    private void drawAccessory(Canvas canvas, float cx, float headTop, float headS,
                               float bodyTop, float bodyH) {
        if (accessory == null || CosmeticCatalog.NONE.equals(accessory.id)) return;
        paint.setShader(null);
        paint.setColor(accessory.color);
        switch (accessory.id) {
            case "headphones":
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, headS * 0.14f));
                rect.set(cx - headS * 0.62f, headTop - headS * 0.10f,
                        cx + headS * 0.62f, headTop + headS * 0.95f);
                canvas.drawArc(rect, 180f, 180f, false, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(cx - headS * 0.66f, headTop + headS * 0.45f,
                        cx - headS * 0.50f, headTop + headS * 0.95f, paint);
                canvas.drawRect(cx + headS * 0.50f, headTop + headS * 0.45f,
                        cx + headS * 0.66f, headTop + headS * 0.95f, paint);
                break;
            case "halo":
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, headS * 0.12f));
                rect.set(cx - headS * 0.55f, headTop - headS * 0.45f,
                        cx + headS * 0.55f, headTop - headS * 0.18f);
                canvas.drawOval(rect, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            case "wings":
                float wingTop = bodyTop + bodyH * 0.05f;
                float wingBottom = bodyTop + bodyH * 0.75f;
                path.reset();
                path.moveTo(cx - bodyH * 0.10f, wingTop);
                path.lineTo(cx - bodyH * 0.62f, wingTop + bodyH * 0.18f);
                path.lineTo(cx - bodyH * 0.16f, wingBottom);
                path.close();
                canvas.drawPath(path, paint);
                path.reset();
                path.moveTo(cx + bodyH * 0.10f, wingTop);
                path.lineTo(cx + bodyH * 0.62f, wingTop + bodyH * 0.18f);
                path.lineTo(cx + bodyH * 0.16f, wingBottom);
                path.close();
                canvas.drawPath(path, paint);
                break;
            default:
                break;
        }
    }
}
