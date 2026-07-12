package dev.marquinhhou.crsscheduler.widget;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Java/Canvas port of the web widget's buildRing() SVG dot-ring glyph.
 * RemoteViews can't render arbitrary SVG, so we rasterize the same 28-dot
 * ring to a Bitmap and drop it into an ImageView via setImageViewBitmap.
 */
public final class RingBitmapFactory {

    private static final int DOT_COUNT = 28;

    private RingBitmapFactory() {}

    public static Bitmap build(float progress, int sizePx, int litColor) {
        float p = Math.max(0f, Math.min(1f, progress));
        int litCount = Math.round(p * DOT_COUNT);

        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        float cx = sizePx / 2f;
        float cy = sizePx / 2f;
        float r = sizePx * 0.42f;
        float dotR = sizePx * 0.033f;

        Paint dim = new Paint(Paint.ANTI_ALIAS_FLAG);
        dim.setColor(Color.argb(31, 255, 255, 255)); // ~ rgba(255,255,255,0.12)

        Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
        lit.setColor(litColor);
        lit.setShadowLayer(sizePx * 0.06f, 0, 0, litColor);

        for (int i = 0; i < DOT_COUNT; i++) {
            double a = (i / (double) DOT_COUNT) * Math.PI * 2 - Math.PI / 2;
            float x = (float) (cx + r * Math.cos(a));
            float y = (float) (cy + r * Math.sin(a));
            canvas.drawCircle(x, y, dotR, i < litCount ? lit : dim);
        }

        return bmp;
    }
}
