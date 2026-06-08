package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public final class PlayerProgressView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long positionMs;
    private long durationMs;
    private boolean dragging;
    private OnSeekListener seekListener;

    public PlayerProgressView(Context context) {
        super(context);
        init();
    }

    public PlayerProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void setProgress(long positionMs, long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
        if (dragging) {
            invalidate();
            return;
        }
        if (this.durationMs > 0L) {
            this.positionMs = Math.max(0L, Math.min(this.durationMs, positionMs));
        } else {
            this.positionMs = Math.max(0L, positionMs);
        }
        invalidate();
    }

    void setOnSeekListener(OnSeekListener seekListener) {
        this.seekListener = seekListener;
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (durationMs <= 0L || seekListener == null) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
                updateFromTouch(event.getX());
                dragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                seekListener.onSeekRequested(positionMs);
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                invalidate();
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0f || height <= 0f) {
            return;
        }

        float centerY = height * 0.5f;
        float radius = dp(2.2f);
        float progress = durationMs <= 0L ? 0f : positionMs / (float) durationMs;
        float endX = Math.max(0f, Math.min(width, width * progress));

        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(3.6f));
        paint.setColor(Color.argb(132, 49, 50, 56));
        canvas.drawLine(0f, centerY, width, centerY, paint);

        paint.setColor(Color.rgb(246, 246, 248));
        canvas.drawLine(0f, centerY, endX, centerY, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(endX, centerY, Math.max(dp(4.5f), radius), paint);
    }

    private void updateFromTouch(float x) {
        float width = Math.max(1f, getWidth());
        float progress = Math.max(0f, Math.min(1f, x / width));
        positionMs = Math.round(durationMs * progress);
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    interface OnSeekListener {
        void onSeekRequested(long positionMs);
    }
}
