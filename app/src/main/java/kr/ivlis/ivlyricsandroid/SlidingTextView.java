package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

final class SlidingTextView extends TextView {
    private static final long EDGE_HOLD_MS = 1050L;
    private static final long MIN_MOVE_MS = 1800L;
    private static final long MAX_MOVE_MS = 5600L;
    private static final float SPEED_DP_PER_SECOND = 46f;
    private static final float EDGE_FADE_DP = 22f;

    private final Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long animationStartMs = SystemClock.uptimeMillis();

    SlidingTextView(Context context) {
        super(context);
        init();
    }

    SlidingTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setSingleLine(true);
        setEllipsize(null);
        setIncludeFontPadding(false);
        getPaint().setSubpixelText(true);
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        animationStartMs = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        animationStartMs = SystemClock.uptimeMillis();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        CharSequence value = getText();
        String text = value == null ? "" : value.toString();
        if (text.isEmpty()) {
            return;
        }

        Paint paint = getPaint();
        paint.setColor(getCurrentTextColor());
        float availableLeft = getPaddingLeft();
        float availableRight = getWidth() - getPaddingRight();
        float availableWidth = Math.max(1f, availableRight - availableLeft);
        float textWidth = paint.measureText(text);

        if (textWidth <= availableWidth) {
            drawStaticText(canvas, text, textWidth, availableLeft, availableRight);
            return;
        }

        int save = canvas.saveLayer(availableLeft, 0f, availableRight, getHeight(), null);
        canvas.clipRect(availableLeft, 0f, availableRight, getHeight());
        float fadeWidth = edgeFadeWidth(availableWidth);
        float startX = availableLeft + fadeWidth;
        float endX = availableRight - fadeWidth - textWidth;
        float x = startX + (endX - startX) * slideFraction(textWidth, availableWidth);
        canvas.drawText(text, x, baseline(), paint);
        applyEdgeFadeMask(canvas, availableLeft, availableWidth);
        canvas.restoreToCount(save);
        postInvalidateOnAnimation();
    }

    private void drawStaticText(Canvas canvas, String text, float textWidth, float left, float right) {
        Paint paint = getPaint();
        int horizontalGravity = getGravity() & Gravity.HORIZONTAL_GRAVITY_MASK;
        float x;
        if (horizontalGravity == Gravity.CENTER_HORIZONTAL) {
            x = left + (right - left - textWidth) * 0.5f;
        } else if (horizontalGravity == Gravity.RIGHT || horizontalGravity == Gravity.END) {
            x = right - textWidth;
        } else {
            x = left;
        }
        canvas.drawText(text, x, baseline(), paint);
    }

    private float slideFraction(float textWidth, float availableWidth) {
        float travel = Math.max(1f, textWidth - availableWidth + edgeFadeWidth(availableWidth) * 2f);
        long moveMs = Math.max(
                MIN_MOVE_MS,
                Math.min(MAX_MOVE_MS, Math.round(travel / dp(SPEED_DP_PER_SECOND) * 1000f))
        );
        long cycleMs = EDGE_HOLD_MS + moveMs + EDGE_HOLD_MS + moveMs;
        long elapsed = (SystemClock.uptimeMillis() - animationStartMs) % cycleMs;
        if (elapsed < EDGE_HOLD_MS) {
            return 0f;
        }
        elapsed -= EDGE_HOLD_MS;
        if (elapsed < moveMs) {
            return easeInOut(elapsed / (float) moveMs);
        }
        elapsed -= moveMs;
        if (elapsed < EDGE_HOLD_MS) {
            return 1f;
        }
        elapsed -= EDGE_HOLD_MS;
        return 1f - easeInOut(elapsed / (float) moveMs);
    }

    private float baseline() {
        Paint.FontMetrics metrics = getPaint().getFontMetrics();
        return (getHeight() - metrics.ascent - metrics.descent) * 0.5f;
    }

    private float edgeFadeWidth(float width) {
        return Math.min(dp(EDGE_FADE_DP), width * 0.22f);
    }

    private void applyEdgeFadeMask(Canvas canvas, float left, float width) {
        float fadeWidth = edgeFadeWidth(width);
        if (fadeWidth <= 0f) {
            return;
        }
        float fadeStop = clamp(fadeWidth / width);
        float rightFadeStart = Math.max(fadeStop, 1f - fadeStop);
        fadePaint.setShader(new LinearGradient(
                left,
                0f,
                left + width,
                0f,
                new int[] {
                        Color.TRANSPARENT,
                        Color.BLACK,
                        Color.BLACK,
                        Color.TRANSPARENT
                },
                new float[] {0f, fadeStop, rightFadeStart, 1f},
                Shader.TileMode.CLAMP
        ));
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawRect(left, 0f, left + width, getHeight(), fadePaint);
        fadePaint.setXfermode(null);
        fadePaint.setShader(null);
    }

    private float easeInOut(float value) {
        float t = clamp(value);
        return t * t * (3f - 2f * t);
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }
}
