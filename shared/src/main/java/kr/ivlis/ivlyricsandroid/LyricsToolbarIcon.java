package kr.ivlis.ivlyricsandroid;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Small vector icons with stable geometry independent of the selected lyric font. */
final class LyricsToolbarIcon extends Drawable {
    static final int CHEVRON_DOWN = 0;
    static final int MORE = 1;

    private final int type;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevron = new Path();

    LyricsToolbarIcon(int type) {
        this.type = type;
        paint.setColor(0xedffffff);
        paint.setStrokeWidth(2.2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        chevron.moveTo(5f, 9f);
        chevron.lineTo(12f, 16f);
        chevron.lineTo(19f, 9f);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        int saved = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f);
        if (type == MORE) {
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(5f, 12f, 1.7f, paint);
            canvas.drawCircle(12f, 12f, 1.7f, paint);
            canvas.drawCircle(19f, 12f, 1.7f, paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(chevron, paint);
        }
        canvas.restoreToCount(saved);
    }

    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
