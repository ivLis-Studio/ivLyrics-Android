package kr.ivlis.ivlyricsandroid;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

final class LaunchMotionSplashView extends View {
    private static final long EXIT_DELAY_MS = 1180L;
    private static final long EXIT_DURATION_MS = 280L;
    private static final int BACKGROUND = Color.rgb(8, 10, 16);
    private static final int INK = Color.rgb(244, 246, 251);
    private static final int ACCENT = Color.rgb(101, 214, 173);
    private static final int VIOLET = Color.rgb(122, 91, 236);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final TimeInterpolator ease = new AccelerateDecelerateInterpolator();

    private long startUptimeMs;
    private boolean running;

    LaunchMotionSplashView(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    void playAndDismiss(Runnable endAction) {
        startUptimeMs = SystemClock.uptimeMillis();
        running = true;
        setAlpha(1f);
        animate()
                .alpha(0f)
                .setStartDelay(EXIT_DELAY_MS)
                .setDuration(EXIT_DURATION_MS)
                .setInterpolator(ease)
                .withEndAction(() -> {
                    running = false;
                    if (endAction != null) {
                        endAction.run();
                    }
                })
                .start();
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        animate().cancel();
        running = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        if (startUptimeMs == 0L) {
            startUptimeMs = now;
        }
        float elapsed = (now - startUptimeMs) / 1000f;
        float width = getWidth();
        float height = getHeight();
        float unit = Math.max(1f, Math.min(width, height) / 390f);
        float centerX = width * 0.5f;
        float centerY = height * 0.48f;

        drawBackground(canvas, width, height, elapsed);
        drawLyricSweep(canvas, centerX, centerY, unit, elapsed);
        drawWave(canvas, centerX, centerY, unit, elapsed);
        drawMeters(canvas, centerX, centerY, unit, elapsed);
        drawFloatingMarks(canvas, centerX, centerY, unit, elapsed);

        if (running) {
            postInvalidateOnAnimation();
        }
    }

    private void drawBackground(Canvas canvas, float width, float height, float elapsed) {
        canvas.drawColor(BACKGROUND);
        paint.setStyle(Paint.Style.FILL);
        drawGlow(canvas, width * 0.25f + wave(elapsed, 0f, width * 0.035f), height * 0.28f, width * 0.62f, VIOLET, 82);
        drawGlow(canvas, width * 0.77f + wave(elapsed, 1.8f, width * 0.03f), height * 0.66f, width * 0.58f, ACCENT, 46);
        drawGlow(canvas, width * 0.56f, height * 0.48f + wave(elapsed, 0.9f, height * 0.025f), width * 0.46f, Color.rgb(255, 106, 196), 38);
    }

    private void drawGlow(Canvas canvas, float cx, float cy, float radius, int color, int alpha) {
        int steps = 9;
        for (int index = steps; index >= 1; index--) {
            float fraction = index / (float) steps;
            paint.setColor(color);
            paint.setAlpha(Math.round(alpha * fraction * fraction));
            canvas.drawCircle(cx, cy, radius * (1.04f - fraction * 0.72f), paint);
        }
    }

    private void drawLyricSweep(Canvas canvas, float cx, float cy, float unit, float elapsed) {
        float left = cx - 112f * unit;
        float top = cy - 82f * unit;
        float[] widths = {170f, 224f, 142f};
        for (int index = 0; index < widths.length; index++) {
            float lineTop = top + index * 28f * unit;
            float lineWidth = widths[index] * unit;
            float height = 8f * unit;
            rect.set(left, lineTop, left + lineWidth, lineTop + height);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(INK);
            paint.setAlpha(34 + index * 9);
            canvas.drawRoundRect(rect, height * 0.5f, height * 0.5f, paint);

            float phase = (elapsed * 0.74f + index * 0.18f) % 1f;
            float fillWidth = lineWidth * smoothStep(Math.min(1f, phase * 1.22f));
            rect.set(left, lineTop, left + fillWidth, lineTop + height);
            paint.setColor(index == 1 ? ACCENT : INK);
            paint.setAlpha(index == 1 ? 215 : 132);
            canvas.drawRoundRect(rect, height * 0.5f, height * 0.5f, paint);
        }
    }

    private void drawWave(Canvas canvas, float cx, float cy, float unit, float elapsed) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (int line = 0; line < 3; line++) {
            float offset = (line - 1) * 18f * unit;
            float amplitude = (18f + line * 4f) * unit;
            float y = cy + 18f * unit + offset + wave(elapsed, line * 0.7f, 4f * unit);
            path.reset();
            path.moveTo(cx - 116f * unit, y);
            path.cubicTo(
                    cx - 78f * unit,
                    y - amplitude,
                    cx - 42f * unit,
                    y - amplitude,
                    cx,
                    y
            );
            path.cubicTo(
                    cx + 42f * unit,
                    y + amplitude,
                    cx + 78f * unit,
                    y + amplitude,
                    cx + 116f * unit,
                    y
            );
            paint.setStrokeWidth((line == 1 ? 4.2f : 2.4f) * unit);
            paint.setColor(line == 1 ? ACCENT : INK);
            paint.setAlpha(line == 1 ? 230 : 78);
            canvas.drawPath(path, paint);
        }
    }

    private void drawMeters(Canvas canvas, float cx, float cy, float unit, float elapsed) {
        paint.setStyle(Paint.Style.FILL);
        float baseY = cy + 92f * unit;
        float startX = cx - 74f * unit;
        for (int index = 0; index < 9; index++) {
            float barWidth = 8f * unit;
            float barHeight = (16f + 26f * (0.5f + 0.5f * (float) Math.sin(elapsed * 5.2f + index * 0.78f))) * unit;
            float left = startX + index * 18f * unit;
            rect.set(left, baseY - barHeight, left + barWidth, baseY);
            paint.setColor(index % 3 == 1 ? ACCENT : INK);
            paint.setAlpha(index % 3 == 1 ? 198 : 114);
            canvas.drawRoundRect(rect, barWidth * 0.5f, barWidth * 0.5f, paint);
        }
    }

    private void drawFloatingMarks(Canvas canvas, float cx, float cy, float unit, float elapsed) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ACCENT);
        paint.setAlpha(210);
        path.reset();
        float noteX = cx + 88f * unit;
        float noteY = cy - 84f * unit + wave(elapsed, 1.2f, 7f * unit);
        path.moveTo(noteX + 12f * unit, noteY - 26f * unit);
        path.lineTo(noteX + 12f * unit, noteY + 12f * unit);
        path.cubicTo(noteX + 12f * unit, noteY + 20f * unit, noteX + 5f * unit, noteY + 26f * unit, noteX - 4f * unit, noteY + 26f * unit);
        path.cubicTo(noteX - 13f * unit, noteY + 26f * unit, noteX - 20f * unit, noteY + 20f * unit, noteX - 20f * unit, noteY + 12f * unit);
        path.cubicTo(noteX - 20f * unit, noteY + 4f * unit, noteX - 13f * unit, noteY - 2f * unit, noteX - 4f * unit, noteY - 2f * unit);
        path.cubicTo(noteX + 0f * unit, noteY - 2f * unit, noteX + 4f * unit, noteY - 1f * unit, noteX + 7f * unit, noteY + 1f * unit);
        path.lineTo(noteX + 7f * unit, noteY - 22f * unit);
        path.lineTo(noteX - 18f * unit, noteY - 17f * unit);
        path.lineTo(noteX - 18f * unit, noteY - 25f * unit);
        path.close();
        canvas.drawPath(path, paint);

        paint.setColor(INK);
        paint.setAlpha(84);
        canvas.drawCircle(cx - 100f * unit, cy - 104f * unit + wave(elapsed, 2.1f, 5f * unit), 4.5f * unit, paint);
        paint.setAlpha(70);
        canvas.drawCircle(cx + 116f * unit, cy + 58f * unit + wave(elapsed, 0.4f, 4f * unit), 3.5f * unit, paint);
    }

    private float wave(float elapsed, float phase, float amount) {
        return (float) Math.sin(elapsed * 2.4f + phase) * amount;
    }

    private float smoothStep(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        return clamped * clamped * (3f - 2f * clamped);
    }
}
