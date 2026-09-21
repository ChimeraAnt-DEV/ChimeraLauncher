package org.chimeramc.launcher.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.chimeramc.launcher.launcher.controller.ControllerType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControllerIllustrationView extends View {
    private ControllerType type = ControllerType.XBOX;
    private final List<Region> regions = new ArrayList<>();
    private final Map<String, Region> regionById = new HashMap<>();
    private final Map<String, Float> glow = new HashMap<>();
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF scratch = new RectF();
    private int bodyColor = 0xFF262A32;
    private int bodyHighlightColor = 0xFF484E5A;
    private int wellColor = 0xFF14161C;
    private int accentColor = -1;
    private float cx;
    private float cy;
    private float scale;

    public ControllerIllustrationView(Context context) {
        super(context);
        init();
    }

    public ControllerIllustrationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        applyThemeColors();
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(3f);
        accentPaint.setStyle(Paint.Style.STROKE);
        accentPaint.setStrokeWidth(2.5f);
        glowPaint.setColor((int) 0xFF4AE0A0L);
        textPaint.setTextAlign(Paint.Align.CENTER);
        highlightPaint.setColor(0x38FFFFFF);
        symbolPaint.setStyle(Paint.Style.STROKE);
        symbolPaint.setStrokeCap(Paint.Cap.ROUND);
        symbolPaint.setStrokeJoin(Paint.Join.ROUND);
        rebuild();
    }

    private boolean isDarkMode() {
        int nightModeFlags = getContext().getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyThemeColors() {
        if (isDarkMode()) {
            bodyColor = 0xFF262A32;
            bodyHighlightColor = 0xFF3A404B;
            wellColor = 0xFF14161C;
            outlinePaint.setColor(Color.rgb(96, 104, 118));
            buttonPaint.setColor(Color.rgb(58, 64, 74));
            detailPaint.setColor(Color.rgb(30, 34, 42));
            textPaint.setColor(Color.rgb(210, 218, 228));
        } else {
            bodyColor = 0xFFE4E7EC;
            bodyHighlightColor = 0xFFF2F4F7;
            wellColor = 0xFFC7CCD3;
            outlinePaint.setColor(Color.rgb(140, 148, 158));
            buttonPaint.setColor(Color.rgb(206, 211, 217));
            detailPaint.setColor(Color.rgb(170, 176, 185));
            textPaint.setColor(Color.rgb(60, 64, 72));
        }
        basePaint.setColor(bodyColor);
    }

    public void setType(ControllerType type) {
        this.type = type;
        rebuild();
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;

        invalidate();
    }

    @Override
    protected void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyThemeColors();
        invalidate();
    }

    public void setRegionGlow(String id, boolean on) {
        glow.put(id, on ? 1f : 0f);
        invalidate();
    }

    private void rebuild() {
        regions.clear();
        regionById.clear();
        if (type == ControllerType.XBOX) {
            // Real Xbox layout: left stick upper-left, d-pad lower-left, ABXY upper-right,
            // right stick lower-centre-right.
            addRegion("ls",  0.320f, 0.400f, 0.052f, "LS", Shape.CIRCLE);
            addRegion("rs",  0.662f, 0.640f, 0.052f, "RS", Shape.CIRCLE);
            addRegion("lsRing",  0.320f, 0.400f, 0.062f, "", Shape.STICK_RING);
            addRegion("rsRing",  0.662f, 0.640f, 0.062f, "", Shape.STICK_RING);
            addRegion("dp",  0.278f, 0.665f, 0.046f, "", Shape.DPAD);
            addRegion("a",  0.702f, 0.400f, 0.045f, "A", Shape.FACE_XBOX);
            addRegion("b",  0.790f, 0.320f, 0.045f, "B", Shape.FACE_XBOX);
            addRegion("x",  0.614f, 0.320f, 0.045f, "X", Shape.FACE_XBOX);
            addRegion("y",  0.702f, 0.240f, 0.045f, "Y", Shape.FACE_XBOX);
            addRegion("lb",  0.32f, 0.155f, 0.030f, "LB", Shape.BUMPER);
            addRegion("rb",  0.68f, 0.155f, 0.030f, "RB", Shape.BUMPER);
            addRegion("lt",  0.19f, 0.145f, 0.032f, "", Shape.TRIGGER);
            addRegion("rt",  0.81f, 0.145f, 0.032f, "", Shape.TRIGGER);
            addRegion("menu",  0.575f, 0.430f, 0.022f, "", Shape.CENTER_BUTTON);
            addRegion("view",  0.425f, 0.430f, 0.022f, "", Shape.CENTER_BUTTON);
        } else if (type == ControllerType.DS4) {

            addRegion("ls",  0.312f,  0.735f,  0.052f, "LS", Shape.CIRCLE);
            addRegion("rs",  0.688f,  0.735f,  0.052f, "RS", Shape.CIRCLE);
            addRegion("lsRing",  0.312f,  0.735f,  0.062f, "", Shape.STICK_RING);
            addRegion("rsRing",  0.688f,  0.735f,  0.062f, "", Shape.STICK_RING);
            addRegion("dp",  0.288f,  0.510f,  0.044f, "", Shape.DPAD);
            addRegion("a",  0.760f,  0.500f,  0.041f, "", Shape.FACE_DUAL);
            addRegion("b",  0.706f,  0.560f,  0.041f, "", Shape.FACE_DUAL);
            addRegion("x",  0.654f,  0.500f,  0.041f, "", Shape.FACE_DUAL);
            addRegion("y",  0.706f,  0.440f,  0.041f, "", Shape.FACE_DUAL);
            addRegion("lb",  0.33f,  0.295f,  0.030f, "L1", Shape.BUMPER);
            addRegion("rb",  0.67f,  0.295f,  0.030f, "R1", Shape.BUMPER);
            addRegion("lt",  0.19f,  0.285f,  0.030f, "", Shape.TRIGGER);
            addRegion("rt",  0.81f,  0.285f,  0.030f, "", Shape.TRIGGER);
            addRegion("touch",  0.50f,  0.355f,  0.050f, "", Shape.TOUCHPAD);
            addRegion("share",  0.415f,  0.450f,  0.019f, "", Shape.CENTER_BUTTON);
            addRegion("options",  0.585f,  0.450f,  0.019f, "", Shape.CENTER_BUTTON);
            addRegion("ps",  0.50f,  0.470f,  0.022f, "", Shape.PS_LOGO);
        } else {

            addRegion("ls",  0.312f,  0.735f,  0.052f, "LS", Shape.CIRCLE);
            addRegion("rs",  0.688f,  0.735f,  0.052f, "RS", Shape.CIRCLE);
            addRegion("lsRing",  0.312f,  0.735f,  0.062f, "", Shape.STICK_RING);
            addRegion("rsRing",  0.688f,  0.735f,  0.062f, "", Shape.STICK_RING);
            addRegion("dp",  0.288f,  0.510f,  0.044f, "", Shape.DPAD);
            addRegion("a",  0.760f,  0.500f,  0.041f, "", Shape.FACE_DUAL);
            addRegion("b",  0.706f,  0.560f,  0.041f, "", Shape.FACE_DUAL);
            addRegion("x",  0.654f,  0.500f,  0.041f, "", Shape.FACE_DUAL);
            addRegion("y",  0.706f,  0.440f,  0.041f, "", Shape.FACE_DUAL);
            addRegion("lb",  0.33f,  0.295f,  0.030f, "L1", Shape.BUMPER);
            addRegion("rb",  0.67f,  0.295f,  0.030f, "R1", Shape.BUMPER);
            addRegion("lt",  0.19f,  0.285f,  0.030f, "", Shape.TRIGGER);
            addRegion("rt",  0.81f,  0.285f,  0.030f, "", Shape.TRIGGER);
            addRegion("touch",  0.50f,  0.355f,  0.050f, "", Shape.TOUCHPAD);
            addRegion("share",  0.415f,  0.470f,  0.019f, "", Shape.CENTER_BUTTON);
            addRegion("options",  0.585f,  0.470f,  0.019f, "", Shape.CENTER_BUTTON);
            addRegion("ps",  0.50f,  0.470f,  0.026f, "", Shape.PS_LOGO);
        }
    }
    private void addRegion(String id, float x, float y, float radius, String label, Shape shape) {
        Region region = new Region();
        region.id = id;
        region.x = x;
        region.y = y;
        region.radius = radius;
        region.label = label;
        region.shape = shape;
        regions.add(region);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        cx = getWidth() / 2f;
        cy = getHeight() / 2f;
        scale = Math.min(getWidth(), getHeight() * 0.45f);
        drawBody(canvas);
        for (Region r : regions) drawRegion(canvas, r);
    }
    private void drawBody(Canvas canvas) {
        if (type == ControllerType.XBOX) {
            drawBodyXbox(canvas);
        } else {
            drawBodyPlayStation(canvas);
        }
    }

    /**
     * Xbox silhouette: a wide arched shell that drops into two angled grips.
     *
     * Geometry is expressed against the same {@code scale} the regions use, so buttons sit
     * inside the shell by construction rather than by eyeballed offsets. The outline is built
     * from cubic segments so the shoulders read as curved plastic, and a clipped sheen along
     * the top edge gives it the look of a moulded surface instead of a flat blob.
     */
    private void drawBodyXbox(Canvas canvas) {
        float w = scale * 0.62f;
        float top = cy - scale * 0.74f;
        float gripBottom = cy + scale * 0.72f;
        float shellH = gripBottom - top;

        Path shell = new Path();
        shell.moveTo(cx - w * 0.80f, top);
        shell.cubicTo(cx - w * 1.05f, top + shellH * 0.01f,
                cx - w * 1.30f, top + shellH * 0.22f,
                cx - w * 1.20f, top + shellH * 0.50f);
        shell.cubicTo(cx - w * 1.10f, top + shellH * 0.78f,
                cx - w * 0.92f, top + shellH * 0.98f,
                cx - w * 0.70f, top + shellH * 1.00f);
        shell.cubicTo(cx - w * 0.44f, top + shellH * 1.02f,
                cx - w * 0.40f, top + shellH * 0.80f,
                cx - w * 0.28f, top + shellH * 0.66f);
        shell.cubicTo(cx - w * 0.16f, top + shellH * 0.56f,
                cx + w * 0.16f, top + shellH * 0.56f,
                cx + w * 0.28f, top + shellH * 0.66f);
        shell.cubicTo(cx + w * 0.40f, top + shellH * 0.80f,
                cx + w * 0.44f, top + shellH * 1.02f,
                cx + w * 0.70f, top + shellH * 1.00f);
        shell.cubicTo(cx + w * 0.92f, top + shellH * 0.98f,
                cx + w * 1.10f, top + shellH * 0.78f,
                cx + w * 1.20f, top + shellH * 0.50f);
        shell.cubicTo(cx + w * 1.30f, top + shellH * 0.22f,
                cx + w * 1.05f, top + shellH * 0.01f,
                cx + w * 0.80f, top);
        shell.close();

        canvas.drawPath(shell, basePaint);
        // Clip the sheen to the shell so it never spills past the outline.
        canvas.save();
        canvas.clipPath(shell);
        highlightPaint.setColor(isDarkMode() ? 0x22FFFFFF : 0x66FFFFFF);
        scratch.set(cx - w * 1.3f, top, cx + w * 1.3f, top + shellH * 0.28f);
        canvas.drawOval(scratch, highlightPaint);
        canvas.restore();
        canvas.drawPath(shell, outlinePaint);
    }

    /**
     * DualShock / DualSense silhouette: a rounded centre shell with a touchpad set into the
     * top face and two symmetric grips curving down, which is what separates it from the
     * Xbox pad. Geometry is shared with the region space, same as the Xbox body.
     */
    private void drawBodyPlayStation(Canvas canvas) {
        float w = scale * 0.60f;
        float top = cy - scale * 0.72f;
        float gripBottom = cy + scale * 0.70f;
        float shellH = gripBottom - top;

        Path shell = new Path();
        shell.moveTo(cx - w * 0.55f, top);
        shell.cubicTo(cx - w * 0.92f, top + shellH * 0.01f,
                cx - w * 1.22f, top + shellH * 0.26f,
                cx - w * 1.12f, top + shellH * 0.60f);
        shell.cubicTo(cx - w * 1.02f, top + shellH * 0.88f,
                cx - w * 0.86f, top + shellH * 1.06f,
                cx - w * 0.62f, top + shellH * 1.06f);
        shell.cubicTo(cx - w * 0.42f, top + shellH * 1.06f,
                cx - w * 0.38f, top + shellH * 0.82f,
                cx - w * 0.26f, top + shellH * 0.68f);
        shell.cubicTo(cx - w * 0.15f, top + shellH * 0.58f,
                cx + w * 0.15f, top + shellH * 0.58f,
                cx + w * 0.26f, top + shellH * 0.68f);
        shell.cubicTo(cx + w * 0.38f, top + shellH * 0.82f,
                cx + w * 0.42f, top + shellH * 1.06f,
                cx + w * 0.62f, top + shellH * 1.06f);
        shell.cubicTo(cx + w * 0.86f, top + shellH * 1.06f,
                cx + w * 1.02f, top + shellH * 0.88f,
                cx + w * 1.12f, top + shellH * 0.60f);
        shell.cubicTo(cx + w * 1.22f, top + shellH * 0.26f,
                cx + w * 0.92f, top + shellH * 0.01f,
                cx + w * 0.55f, top);
        shell.close();

        canvas.drawPath(shell, basePaint);

        canvas.save();
        canvas.clipPath(shell);
        highlightPaint.setColor(isDarkMode() ? 0x22FFFFFF : 0x66FFFFFF);
        scratch.set(cx - w * 1.25f, top, cx + w * 1.25f, top + shellH * 0.24f);
        canvas.drawOval(scratch, highlightPaint);

        // Signature light bar along the seam between the centre shell and the grips.
        glowPaint.setColor(accentColor != -1 ? accentColor : 0xFF3B82F6);
        glowPaint.setAlpha(isDarkMode() ? 90 : 130);
        scratch.set(cx - w * 1.00f, top + shellH * 0.40f, cx + w * 1.00f, top + shellH * 0.46f);
        canvas.drawRoundRect(scratch, shellH * 0.03f, shellH * 0.03f, glowPaint);
        canvas.restore();

        canvas.drawPath(shell, outlinePaint);
    }

    private void drawRegion(Canvas canvas, Region r) {
        // Region coordinates are normalised across the view: 0.5 is centre and the span
        // between 0.0 and 1.0 maps to twice the scale. The offset must stay inside the
        // parentheses, otherwise every button is drawn at centre minus scale instead of
        // spread around the centre, which piles the whole layout into the left half.
        float px = cx + (r.x - 0.5f) * 2f * scale;
        float py = cy + (r.y - 0.5f) * 2f * scale;
        float pr = r.radius * 2f * scale;
        Float strength = glow.get(r.id);
        float g = strength == null ? 0f : strength;
        if (g >  0.05f) {
            glowPaint.setColor(accentColor != -1 ? accentColor : (int) 0xFF4AE0A0L);
            glowPaint.setAlpha((int) (150 * g));
            drawShape(canvas, r, px, py, pr * (1f +  0.3f * g), true);
        }
        drawShape(canvas, r, px, py, pr, false);
        if (r.label != null && !r.label.isEmpty()) {
            textPaint.setTextSize(pr * 0.55f);
            float ty = py - (textPaint.getFontMetrics().ascent + textPaint.getFontMetrics().descent / 2f);
            canvas.drawText(r.label, px, ty, textPaint);
        }
    }
    private void drawShape(Canvas canvas, Region r, float px, float py, float pr, boolean glowMode) {
        switch (r.shape) {
            case CIRCLE: drawStick(canvas, px, py, pr, glowMode); break;
            case STICK_RING: drawRing(canvas, px, py, pr, glowMode); break;
            case DPAD: drawDPad(canvas, px, py, pr, glowMode); break;
            case FACE_XBOX: drawFaceXbox(canvas, r, px, py, pr, glowMode); break;
            case FACE_DUAL: drawFaceDual(canvas, r, px, py, pr, glowMode); break;
            case BUMPER: drawBumper(canvas, px, py, pr, glowMode); break;
            case TRIGGER: drawTrigger(canvas, px, py, pr, glowMode); break;
            case CENTER_BUTTON: drawCenterButton(canvas, px, py, pr, glowMode); break;
            case TOUCHPAD: drawTouchpad(canvas, px, py, pr, glowMode); break;
            case PS_LOGO: drawPsLogo(canvas, px, py, pr, glowMode); break;
        }
    }

    private void drawStick(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        // A real stick sits in a recessed well; the well is what makes it read as a stick
        // rather than a flat disc.
        wellPaint.setColor(wellColor);
        canvas.drawCircle(px, py, pr * 1.22f, wellPaint);
        if (glowMode) {
            canvas.drawCircle(px, py, pr, glowPaint);
            return;
        }
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(2f);
        ring.setColor(outlinePaint.getColor());
        canvas.drawCircle(px, py, pr * 1.22f, ring);

        // Concave cap: a base disc, a darker rim, then an off-centre highlight.
        buttonPaint.setColor(isDarkMode() ? 0xFF4A505C : 0xFFCDD2D9);
        canvas.drawCircle(px, py, pr, buttonPaint);
        buttonPaint.setColor(isDarkMode() ? 0xFF2E333D : 0xFFB4BAC3);
        canvas.drawCircle(px, py, pr * 0.86f, buttonPaint);
        highlightPaint.setColor(isDarkMode() ? 0x33FFFFFF : 0x99FFFFFF);
        canvas.drawCircle(px - pr * 0.22f, py - pr * 0.26f, pr * 0.42f, highlightPaint);
    }

    private void drawRing(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        // Decorative outer ring for stick wells; only drawn as a glow halo.
        if (!glowMode) return;
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(pr * 0.18f);
        canvas.drawCircle(px, py, pr, glowPaint);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    private void drawDPad(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        Paint p = glowMode ? glowPaint : buttonPaint;
        if (!glowMode) {
            buttonPaint.setColor(isDarkMode() ? 0xFF3C424C : 0xFFC2C8D0);
        }
        float arm = pr * 0.92f;
        float half = pr * 0.30f;
        Path cross = new Path();
        cross.addRoundRect(new RectF(px - half, py - arm, px + half, py + arm), pr * 0.10f, pr * 0.10f,
                Path.Direction.CW);
        cross.addRoundRect(new RectF(px - arm, py - half, px + arm, py + half), pr * 0.10f, pr * 0.10f,
                Path.Direction.CW);
        canvas.drawPath(cross, p);
        if (!glowMode) {
            outlinePaint.setStrokeWidth(2f);
            canvas.drawPath(cross, outlinePaint);
            outlinePaint.setStrokeWidth(3f);
            // Centre dimple.
            detailPaint.setColor(isDarkMode() ? 0xFF262A32 : 0xFFAEB4BD);
            canvas.drawCircle(px, py, pr * 0.16f, detailPaint);
        }
    }

    private void drawFaceXbox(Canvas canvas, Region r, float px, float py, float pr, boolean glowMode) {
        if (glowMode) {
            canvas.drawCircle(px, py, pr, glowPaint);
            return;
        }
        // Xbox face buttons are coloured letters on dark caps.
        int tint = faceColor(r.id);
        buttonPaint.setColor(isDarkMode() ? 0xFF23272F : 0xFFE1E4E9);
        canvas.drawCircle(px, py, pr, buttonPaint);
        Paint letter = new Paint(Paint.ANTI_ALIAS_FLAG);
        letter.setColor(tint);
        letter.setTextAlign(Paint.Align.CENTER);
        letter.setFakeBoldText(true);
        letter.setTextSize(pr * 1.15f);
        Paint.FontMetrics fm = letter.getFontMetrics();
        canvas.drawText(r.label, px, py - (fm.ascent + fm.descent) / 2f, letter);
        outlinePaint.setStrokeWidth(2f);
        canvas.drawCircle(px, py, pr, outlinePaint);
        outlinePaint.setStrokeWidth(3f);
    }

    private void drawFaceDual(Canvas canvas, Region r, float px, float py, float pr, boolean glowMode) {
        if (glowMode) {
            canvas.drawCircle(px, py, pr, glowPaint);
            return;
        }
        int tint = faceColor(r.id);
        buttonPaint.setColor(isDarkMode() ? 0xFF2A2F37 : 0xFFE7EAEE);
        canvas.drawCircle(px, py, pr, buttonPaint);
        symbolPaint.setColor(tint);
        symbolPaint.setStrokeWidth(pr * 0.22f);
        drawDualSymbol(canvas, r.id, px, py, pr * 0.52f);
        outlinePaint.setStrokeWidth(2f);
        canvas.drawCircle(px, py, pr, outlinePaint);
        outlinePaint.setStrokeWidth(3f);
    }

    private int faceColor(String id) {
        if (id == null) return (int) 0xFF7D8590L;
        switch (id) {
            case "y": return (int) 0xFFE6B422L; // triangle - amber
            case "b": return (int) 0xFFE0455AL; // circle - red
            case "a": return (int) 0xFF3FA9F5L; // cross - blue
            case "x": return (int) 0xFF57C84DL; // square - green
            default: return (int) 0xFF7D8590L;
        }
    }

    private void drawDualSymbol(Canvas canvas, String id, float px, float py, float pr) {
        Paint p = symbolPaint;
        if ("a".equals(id)) {
            canvas.drawLine(px - pr, py - pr, px + pr, py + pr, p);
            canvas.drawLine(px - pr, py + pr, px + pr, py - pr, p);
        } else if ("b".equals(id)) {
            Paint fill = new Paint(p);
            fill.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(px, py, pr, fill);
        } else if ("x".equals(id)) {
            Paint fill = new Paint(p);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeWidth(p.getStrokeWidth() * 0.75f);
            canvas.drawRect(px - pr, py - pr, px + pr, py + pr, fill);
        } else if ("y".equals(id)) {
            Paint fill = new Paint(p);
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeJoin(Paint.Join.ROUND);
            Path t = new Path();
            t.moveTo(px, py - pr);
            t.lineTo(px + pr, py + pr);
            t.lineTo(px - pr, py + pr);
            t.close();
            canvas.drawPath(t, fill);
        }
    }

    private void drawBumper(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        Paint p = glowMode ? glowPaint : buttonPaint;
        if (!glowMode) {
            buttonPaint.setColor(isDarkMode() ? 0xFF3A404B : 0xFFC8CED6);
        }
        // A bumper is a wide, low pill that wraps the shoulder.
        scratch.set(px - pr * 2.4f, py - pr * 0.55f, px + pr * 2.4f, py + pr * 0.55f);
        canvas.drawRoundRect(scratch, pr * 0.5f, pr * 0.5f, p);
        if (!glowMode) {
            outlinePaint.setStrokeWidth(2f);
            canvas.drawRoundRect(scratch, pr * 0.5f, pr * 0.5f, outlinePaint);
            outlinePaint.setStrokeWidth(3f);
            detailPaint.setColor(isDarkMode() ? 0xFF262A32 : 0xFFAEB4BD);
            scratch.set(px - pr * 1.9f, py + pr * 0.30f, px + pr * 1.9f, py + pr * 0.42f);
            canvas.drawRoundRect(scratch, pr * 0.06f, pr * 0.06f, detailPaint);
        }
    }

    private void drawTrigger(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        Paint p = glowMode ? glowPaint : buttonPaint;
        if (!glowMode) {
            buttonPaint.setColor(isDarkMode() ? 0xFF333944 : 0xFFBFC5CD);
        }
        // Triggers curve back over the shoulder; a short arc reads better than a pill.
        scratch.set(px - pr * 1.7f, py - pr * 0.95f, px + pr * 1.7f, py + pr * 0.75f);
        canvas.drawRoundRect(scratch, pr * 0.7f, pr * 0.7f, p);
        if (!glowMode) {
            outlinePaint.setStrokeWidth(2f);
            canvas.drawRoundRect(scratch, pr * 0.7f, pr * 0.7f, outlinePaint);
            outlinePaint.setStrokeWidth(3f);
        }
    }

    private void drawCenterButton(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        Paint p = glowMode ? glowPaint : buttonPaint;
        if (!glowMode) {
            buttonPaint.setColor(isDarkMode() ? 0xFF4A505C : 0xFFCED3DA);
        }
        canvas.drawCircle(px, py, pr, p);
        if (!glowMode) {
            outlinePaint.setStrokeWidth(2f);
            canvas.drawCircle(px, py, pr, outlinePaint);
            outlinePaint.setStrokeWidth(3f);
        }
    }

    private void drawTouchpad(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        Paint p = glowMode ? glowPaint : detailPaint;
        if (!glowMode) {
            detailPaint.setColor(isDarkMode() ? 0xFF1C2027 : 0xFFD3D8DE);
        }
        scratch.set(px - pr * 2.2f, py - pr * 0.75f, px + pr * 2.2f, py + pr * 0.75f);
        canvas.drawRoundRect(scratch, pr * 0.28f, pr * 0.28f, p);
        if (!glowMode) {
            outlinePaint.setStrokeWidth(2f);
            canvas.drawRoundRect(scratch, pr * 0.28f, pr * 0.28f, outlinePaint);
            outlinePaint.setStrokeWidth(3f);
        }
    }

    private void drawPsLogo(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        Paint p = glowMode ? glowPaint : symbolPaint;
        if (glowMode) {
            canvas.drawCircle(px, py, pr, p);
            return;
        }
        buttonPaint.setColor(isDarkMode() ? 0xFF23272F : 0xFFE1E4E9);
        canvas.drawCircle(px, py, pr, buttonPaint);
        // Stylised PlayStation mark: a slanted stroke crossed by a horizontal bar.
        symbolPaint.setColor(accentColor != -1 ? accentColor : 0xFF4A90E2);
        symbolPaint.setStrokeWidth(pr * 0.20f);
        canvas.drawLine(px - pr * 0.28f, py + pr * 0.62f, px + pr * 0.38f, py - pr * 0.62f, symbolPaint);
        canvas.drawLine(px - pr * 0.55f, py + pr * 0.05f, px + pr * 0.62f, py + pr * 0.05f, symbolPaint);
        outlinePaint.setStrokeWidth(2f);
        canvas.drawCircle(px, py, pr, outlinePaint);
        outlinePaint.setStrokeWidth(3f);
    }

    public void handleKeyEvent(int keyCode, boolean down) {
        String id = mapKey(keyCode);
        if (id != null) setRegionGlow(id, down);
    }

    public void handleMotionEvent(MotionEvent event) {
        float lx = event.getAxisValue(MotionEvent.AXIS_X);
        float ly = event.getAxisValue(MotionEvent.AXIS_Y);
        float rx = event.getAxisValue(MotionEvent.AXIS_Z);
        float rz = event.getAxisValue(MotionEvent.AXIS_RZ);
        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);

        // Motion events stream continuously, so each axis is reported lit or unlit on every
        // event. Only ever setting the glow left sticks and the d-pad highlighted forever
        // once they had been touched once.
        setRegionGlow("ls", Math.abs(lx) > 0.35f || Math.abs(ly) > 0.35f);
        setRegionGlow("rs", Math.abs(rx) > 0.35f || Math.abs(rz) > 0.35f);
        setRegionGlow("dp", Math.abs(hatX) > 0.4f || Math.abs(hatY) > 0.4f);
    }
    private String mapKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "a";
            case KeyEvent.KEYCODE_BUTTON_B: return "b";
            case KeyEvent.KEYCODE_BUTTON_X: return "x";
            case KeyEvent.KEYCODE_BUTTON_Y: return "y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "lb";
            case KeyEvent.KEYCODE_BUTTON_R1: return "rb";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "ls";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "rs";
            case KeyEvent.KEYCODE_DPAD_UP: return "dp";
            case KeyEvent.KEYCODE_DPAD_DOWN: return "dp";
            case KeyEvent.KEYCODE_DPAD_LEFT: return "dp";
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "dp";
            default: return null;
        }
    }

    private enum Shape {
        CIRCLE, STICK_RING, DPAD, FACE_XBOX, FACE_DUAL, BUMPER, TRIGGER, CENTER_BUTTON, TOUCHPAD, PS_LOGO;
    }
    private static final class Region {
        String id;
        float x;
        float y;
        float radius;
        String label;
        Shape shape;
    }
}
