package org.chimeramc.client.ui.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;

public final class SkinPreviewRenderer {
    private static final int TEX = 6;
    private static final int COLS =  16;
    private static final int ROWS =  32;

    private static final Rect HEAD_FRONT = new Rect(8,  8,  16,  16);
    private static final Rect HEAD_TOP = new Rect(8, 0, 16,  8);
    private static final Rect BODY_FRONT = new Rect(20,  20,  28,  32);
    private static final Rect RIGHT_ARM_FRONT = new Rect(44,  20,  48,  32);
    private static final Rect LEFT_ARM_FRONT = new Rect(36,  20,  40,  32);
    private static final Rect RIGHT_LEG_FRONT = new Rect(4,  20,  8,  32);
    private static final Rect LEFT_LEG_FRONT = new Rect(20,  52,  28,  64);
    private static final Rect HAT = new Rect(40,  8,  48,  16);
    private static final Rect JACKET = new Rect(20,  36,  28,  48);
    private static final Rect RIGHT_SLEEVE = new Rect(44,  36,  48,  48);
    private static final Rect LEFT_SLEEVE = new Rect(36,  36,  40,  48);

    private SkinPreviewRenderer() {}
    public static Bitmap render(Bitmap atlas) {
        Bitmap out = Bitmap.createBitmap(COLS * TEX, ROWS * TEX, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint();
        p.setFilterBitmap(false);
        float s = atlas.getWidth() / 64f;
        drawPart(c, atlas, scaleRect(BODY_FRONT, s),4,8,p);
        drawPart(c, atlas, scaleRect(RIGHT_ARM_FRONT, s),12,8,p);
        drawPart(c, atlas, scaleRect(LEFT_ARM_FRONT, s),0,8,p);
        drawPart(c, atlas, scaleRect(RIGHT_LEG_FRONT, s),4,20,p);
        drawPart(c, atlas, scaleRect(LEFT_LEG_FRONT, s),8,20,p);
        drawPart(c, atlas, scaleRect(HEAD_FRONT, s),4,0,p);
        drawOverlay(c, atlas, scaleRect(HAT, s),4,0,p);
        drawOverlay(c, atlas, scaleRect(JACKET, s),4,8,p);
        drawOverlay(c, atlas, scaleRect(RIGHT_SLEEVE, s),12,8,p);
        drawOverlay(c, atlas, scaleRect(LEFT_SLEEVE, s),0,8,p);
        return out;
    }

    private static Rect scaleRect(Rect r, float s) {
        return new Rect((int) (r.left * s), (int) (r.top * s),
                (int) (r.right * s), (int) (r.bottom * s));
    }

    private static void drawPart(Canvas c, Bitmap atlas, Rect src, int tx, int ty, Paint p) {
        Rect dst = new Rect(tx * TEX, ty * TEX,(tx + src.width()) * TEX,(ty + src.height()) * TEX);
        c.drawBitmap(atlas, src, dst, p);
    }

    private static void drawOverlay(Canvas c, Bitmap atlas, Rect src, int tx, int ty, Paint p) {
        if (isEmpty(atlas, src)) return;
        drawPart(c, atlas, src, tx, ty, p);
    }

    private static boolean isEmpty(Bitmap b, Rect r) {
        for (int y = r.top; y < r.bottom; y++) {
            for (int x = r.left; x < r.right; x++) {
                if (Color.alpha(b.getPixel(x, y)) >  8) return false;
            }
        }
        return true;
    }
}