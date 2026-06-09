package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

final class ColorPickerView extends View {
    interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF svRect = new RectF();
    private final RectF hueRect = new RectF();
    private final float[] hsv = new float[]{220f, 0.65f, 0.65f};
    private OnColorChangedListener listener;

    ColorPickerView(Context context) {
        super(context);
        init();
    }

    ColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
    }

    int getColor() {
        return Color.HSVToColor(hsv);
    }

    void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(2f));
        strokePaint.setColor(Color.WHITE);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(dp(320f));
        int desiredHeight = Math.round(dp(286f));
        setMeasuredDimension(
                resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        updateRects();
        drawSaturationValue(canvas);
        drawHue(canvas);
        drawHandles(canvas);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                && event.getActionMasked() != MotionEvent.ACTION_MOVE) {
            return true;
        }
        updateRects();
        float x = event.getX();
        float y = event.getY();
        if (svRect.contains(x, y)) {
            hsv[1] = clamp((x - svRect.left) / Math.max(1f, svRect.width()));
            hsv[2] = 1f - clamp((y - svRect.top) / Math.max(1f, svRect.height()));
            notifyChanged();
            return true;
        }
        if (y >= hueRect.top - dp(12f) && y <= hueRect.bottom + dp(12f)) {
            hsv[0] = clamp((x - hueRect.left) / Math.max(1f, hueRect.width())) * 360f;
            notifyChanged();
            return true;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateRects() {
        float padding = dp(8f);
        float gap = dp(16f);
        float hueHeight = dp(26f);
        float width = Math.max(1f, getWidth() - padding * 2f);
        float availableHeight = Math.max(1f, getHeight() - padding * 2f - gap - hueHeight);
        svRect.set(padding, padding, padding + width, padding + availableHeight);
        hueRect.set(padding, svRect.bottom + gap, padding + width, svRect.bottom + gap + hueHeight);
    }

    private void drawSaturationValue(Canvas canvas) {
        int hueColor = Color.HSVToColor(new float[]{hsv[0], 1f, 1f});
        paint.setShader(new LinearGradient(
                svRect.left,
                svRect.top,
                svRect.right,
                svRect.top,
                Color.WHITE,
                hueColor,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(svRect, dp(12f), dp(12f), paint);

        paint.setShader(new LinearGradient(
                svRect.left,
                svRect.top,
                svRect.left,
                svRect.bottom,
                Color.TRANSPARENT,
                Color.BLACK,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(svRect, dp(12f), dp(12f), paint);
        paint.setShader(null);
    }

    private void drawHue(Canvas canvas) {
        int[] colors = new int[]{
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED
        };
        paint.setShader(new LinearGradient(
                hueRect.left,
                hueRect.top,
                hueRect.right,
                hueRect.top,
                colors,
                null,
                Shader.TileMode.CLAMP
        ));
        canvas.drawRoundRect(hueRect, dp(13f), dp(13f), paint);
        paint.setShader(null);
    }

    private void drawHandles(Canvas canvas) {
        float svX = svRect.left + hsv[1] * svRect.width();
        float svY = svRect.top + (1f - hsv[2]) * svRect.height();
        strokePaint.setColor(Color.WHITE);
        strokePaint.setShadowLayer(dp(2f), 0f, dp(1f), Color.argb(140, 0, 0, 0));
        canvas.drawCircle(svX, svY, dp(8f), strokePaint);

        float hueX = hueRect.left + (hsv[0] / 360f) * hueRect.width();
        canvas.drawCircle(hueX, hueRect.centerY(), dp(8f), strokePaint);
        strokePaint.clearShadowLayer();
    }

    private void notifyChanged() {
        invalidate();
        if (listener != null) {
            listener.onColorChanged(getColor());
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
