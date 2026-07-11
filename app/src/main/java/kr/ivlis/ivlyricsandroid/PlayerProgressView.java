package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

public final class PlayerProgressView extends View {
    private static final int TRACK_COLOR = 0x42FFFFFF;
    private static final int PROGRESS_COLOR = 0xD9FFFFFF;
    private static final int THUMB_COLOR = 0xF2FFFFFF;

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
        long nextDurationMs = Math.max(0L, durationMs);
        if (dragging) {
            return;
        }
        long nextPositionMs;
        if (nextDurationMs > 0L) {
            nextPositionMs = Math.max(0L, Math.min(nextDurationMs, positionMs));
        } else {
            nextPositionMs = Math.max(0L, positionMs);
        }
        if (this.durationMs == nextDurationMs && this.positionMs == nextPositionMs) {
            return;
        }
        this.durationMs = nextDurationMs;
        this.positionMs = nextPositionMs;
        invalidate();
    }

    void setOnSeekListener(OnSeekListener seekListener) {
        this.seekListener = seekListener;
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setDefaultFocusHighlightEnabled(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (durationMs <= 0L || seekListener == null) {
            return super.onKeyDown(keyCode, event);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            long delta = event != null && event.isShiftPressed() ? 30_000L : 5_000L;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                delta = -delta;
            }
            positionMs = Math.max(0L, Math.min(durationMs, positionMs + delta));
            seekListener.onSeekRequested(positionMs);
            invalidate();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            seekListener.onSeekRequested(positionMs);
            performClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
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
        float trackStroke = dp(3.6f);
        float thumbRadius = dp(5.0f);
        float horizontalInset = thumbRadius + Math.max(1f, trackStroke * 0.25f);
        float trackStart = Math.min(horizontalInset, width * 0.5f);
        float trackEnd = Math.max(trackStart, width - horizontalInset);
        float progress = durationMs <= 0L ? 0f : positionMs / (float) durationMs;
        float endX = trackStart + ((trackEnd - trackStart) * Math.max(0f, Math.min(1f, progress)));

        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(trackStroke);
        paint.setColor(TRACK_COLOR);
        canvas.drawLine(trackStart, centerY, trackEnd, centerY, paint);

        paint.setColor(PROGRESS_COLOR);
        canvas.drawLine(trackStart, centerY, endX, centerY, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(THUMB_COLOR);
        canvas.drawCircle(endX, centerY, thumbRadius, paint);
    }

    private void updateFromTouch(float x) {
        float width = Math.max(1f, getWidth());
        float trackStroke = dp(3.6f);
        float thumbRadius = dp(5.0f);
        float horizontalInset = thumbRadius + Math.max(1f, trackStroke * 0.25f);
        float trackStart = Math.min(horizontalInset, width * 0.5f);
        float trackEnd = Math.max(trackStart, width - horizontalInset);
        float progress = trackEnd <= trackStart
                ? 0f
                : Math.max(0f, Math.min(1f, (x - trackStart) / (trackEnd - trackStart)));
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
