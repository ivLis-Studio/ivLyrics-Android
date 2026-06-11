package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public final class TransportButtonView extends View {
    static final int TYPE_PREVIOUS = 0;
    static final int TYPE_PLAY_PAUSE = 1;
    static final int TYPE_NEXT = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private int type = TYPE_PLAY_PAUSE;
    private boolean primary = true;
    private boolean playing;

    public TransportButtonView(Context context) {
        super(context);
        init();
    }

    public TransportButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    TransportButtonView(Context context, int type, boolean primary) {
        super(context);
        this.type = type;
        this.primary = primary;
        init();
    }

    void setPlaying(boolean playing) {
        if (this.playing == playing) {
            return;
        }
        this.playing = playing;
        invalidate();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setDefaultFocusHighlightEnabled(true);
        setWillNotDraw(false);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float size = Math.min(width, height);
        if (size <= 0f) {
            return;
        }

        float cx = width * 0.5f;
        float cy = height * 0.5f;
        boolean pressed = isPressed();

        if (primary) {
            paint.setShader(null);
            paint.setColor(pressed ? Color.rgb(226, 226, 232) : Color.rgb(246, 246, 250));
            canvas.drawCircle(cx, cy, size * 0.48f, paint);
        } else if (pressed) {
            paint.setShader(null);
            paint.setColor(Color.argb(28, 255, 255, 255));
            canvas.drawCircle(cx, cy, size * 0.42f, paint);
        }

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(primary ? Color.rgb(14, 15, 20) : Color.argb(238, 255, 255, 255));
        paint.setAlpha(pressed ? 255 : (primary ? 255 : 232));

        if (type == TYPE_PREVIOUS) {
            drawSkip(canvas, cx, cy, size, false);
        } else if (type == TYPE_NEXT) {
            drawSkip(canvas, cx, cy, size, true);
        } else if (playing) {
            drawPause(canvas, cx, cy, size);
        } else {
            drawPlay(canvas, cx, cy, size);
        }
        paint.setAlpha(255);
    }

    private void drawPlay(Canvas canvas, float cx, float cy, float size) {
        float iconWidth = size * 0.26f;
        float iconHeight = size * 0.34f;
        float left = cx - iconWidth * 0.32f - size * 0.005f;
        path.reset();
        path.moveTo(left, cy - iconHeight * 0.5f);
        path.lineTo(left, cy + iconHeight * 0.5f);
        path.lineTo(left + iconWidth, cy);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawPause(Canvas canvas, float cx, float cy, float size) {
        float barWidth = size * 0.088f;
        float barHeight = size * 0.34f;
        float gap = size * 0.105f;
        float top = cy - barHeight * 0.5f;
        float bottom = cy + barHeight * 0.5f;
        float radius = barWidth * 0.42f;

        rect.set(cx - gap * 0.5f - barWidth, top, cx - gap * 0.5f, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);
        rect.set(cx + gap * 0.5f, top, cx + gap * 0.5f + barWidth, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    private void drawSkip(Canvas canvas, float cx, float cy, float size, boolean next) {
        float triangleWidth = size * 0.30f;
        float triangleHeight = size * 0.44f;
        float gap = size * 0.045f;
        float barWidth = Math.max(2.5f, size * 0.065f);
        float totalWidth = triangleWidth + gap + barWidth;
        float left = cx - totalWidth * 0.5f;
        float top = cy - triangleHeight * 0.5f;
        float bottom = cy + triangleHeight * 0.5f;
        float radius = barWidth * 0.5f;

        if (next) {
            drawRightTriangle(canvas, left, top, bottom, triangleWidth);
            float barLeft = left + triangleWidth + gap;
            rect.set(barLeft, top, barLeft + barWidth, bottom);
            canvas.drawRoundRect(rect, radius, radius, paint);
        } else {
            rect.set(left, top, left + barWidth, bottom);
            canvas.drawRoundRect(rect, radius, radius, paint);
            drawLeftTriangle(canvas, left + barWidth + gap, top, bottom, triangleWidth);
        }
    }

    private void drawRightTriangle(Canvas canvas, float left, float top, float bottom, float width) {
        path.reset();
        path.moveTo(left, top);
        path.lineTo(left, bottom);
        path.lineTo(left + width, (top + bottom) * 0.5f);
        path.close();
        canvas.drawPath(path, paint);
    }

    private void drawLeftTriangle(Canvas canvas, float left, float top, float bottom, float width) {
        path.reset();
        path.moveTo(left + width, top);
        path.lineTo(left + width, bottom);
        path.lineTo(left, (top + bottom) * 0.5f);
        path.close();
        canvas.drawPath(path, paint);
    }
}
