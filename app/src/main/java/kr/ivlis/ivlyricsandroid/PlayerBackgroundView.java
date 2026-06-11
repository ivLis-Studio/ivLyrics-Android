package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

public final class PlayerBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint noisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final Runnable animationTick = this::postInvalidateOnAnimation;

    private Bitmap sourceArtwork;
    private Bitmap blurredArtwork;
    private Bitmap noiseBitmap;
    private String sourceArtworkKey = "";
    private AiLyricsSettings.BackgroundSettings backgroundSettings =
            new AiLyricsSettings.BackgroundSettings(AiLyricsSettings.BACKGROUND_MODE_GRADIENT, 30, 20, false, false, "#1e3a8a", 100);
    private int paletteBackground = Color.rgb(20, 23, 32);
    private int palettePrimary = Color.rgb(72, 64, 124);
    private int paletteSecondary = Color.rgb(145, 83, 131);
    private int paletteAccent = Color.rgb(62, 94, 130);
    private float phaseX;
    private float phaseY;
    private float phaseZ;
    private float currentNx = 0.5f;
    private float currentNy = 0.5f;
    private long animationStartMs;
    private long lastFrameMs;
    private boolean motionInitialized;

    public PlayerBackgroundView(Context context) {
        super(context);
    }

    public PlayerBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    void setArtwork(Bitmap artwork) {
        setArtwork(artwork, "");
    }

    void setArtwork(Bitmap artwork, String artworkKey) {
        String safeArtworkKey = artworkKey == null ? "" : artworkKey;
        if (!safeArtworkKey.isEmpty() && safeArtworkKey.equals(sourceArtworkKey)) {
            return;
        }
        if (sourceArtwork == artwork) {
            if (!safeArtworkKey.isEmpty()) {
                sourceArtworkKey = safeArtworkKey;
            }
            return;
        }
        sourceArtworkKey = safeArtworkKey;
        sourceArtwork = artwork;
        blurredArtwork = artwork == null ? null : createBlurredArtwork(artwork);
        extractPalette(artwork);

        Random random = new Random(SystemClock.uptimeMillis());
        phaseX = random.nextFloat() * (float) Math.PI * 2f;
        phaseY = random.nextFloat() * (float) Math.PI * 2f;
        phaseZ = random.nextFloat() * (float) Math.PI * 2f;
        currentNx = 0.5f;
        currentNy = 0.5f;
        animationStartMs = SystemClock.uptimeMillis();
        lastFrameMs = 0L;
        motionInitialized = false;
        postInvalidateOnAnimation();
    }

    void setBackgroundSettings(AiLyricsSettings.BackgroundSettings settings) {
        AiLyricsSettings.BackgroundSettings safeSettings = settings == null
                ? new AiLyricsSettings.BackgroundSettings(AiLyricsSettings.BACKGROUND_MODE_GRADIENT, 30, 20, false, false, "#1e3a8a", 100)
                : settings;
        boolean blurChanged = backgroundSettings.blur != safeSettings.blur;
        backgroundSettings = safeSettings;
        if (blurChanged && sourceArtwork != null && !sourceArtwork.isRecycled()) {
            blurredArtwork = createBlurredArtwork(sourceArtwork);
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(animationTick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (animationStartMs == 0L) {
            animationStartMs = now;
        }
        double seconds = (now - animationStartMs) / 1000.0;

        String mode = backgroundSettings.mode;
        if (AiLyricsSettings.BACKGROUND_MODE_SOLID.equals(mode)) {
            drawSolid(canvas, width, height);
        } else if (AiLyricsSettings.BACKGROUND_MODE_BLUR_GRADIENT.equals(mode)) {
            drawBlurGradient(canvas, width, height, seconds);
        } else {
            drawAlbumGradient(canvas, width, height, seconds);
        }

        if (backgroundSettings.noise) {
            drawNoise(canvas, width, height);
        }

        if (shouldAnimate(mode)) {
            removeCallbacks(animationTick);
            postDelayed(animationTick, 33L);
        }
    }

    private void drawAlbumGradient(Canvas canvas, int width, int height, double seconds) {
        Bitmap artwork = blurredArtwork;
        if (artwork == null || artwork.isRecycled()) {
            drawBlurGradient(canvas, width, height, seconds);
            return;
        }

        float scale = Math.max(width / (float) artwork.getWidth(), height / (float) artwork.getHeight()) * 2.2f;
        if (backgroundSettings.blur >= 55) {
            scale *= 1.16f;
        }
        drawMovingArtwork(canvas, artwork, width, height, seconds, scale, 1f);
        drawDimmingGradient(canvas, width, height, 1f);
    }

    private void drawMovingArtwork(Canvas canvas, Bitmap artwork, int width, int height, double seconds, float scale, float alpha) {
        float drawWidth = artwork.getWidth() * scale;
        float drawHeight = artwork.getHeight() * scale;
        float spanX = Math.max(0f, drawWidth - width);
        float spanY = Math.max(0f, drawHeight - height);

        float targetNx = backgroundSettings.reduceMotion
                ? 0.5f
                : clamp01(0.5f
                        + 0.29f * sin(seconds * 0.034 + phaseX)
                        + 0.12f * sin(seconds * 0.016 + phaseZ)
                        + 0.05f * sin(seconds * 0.009 + phaseY));
        float targetNy = backgroundSettings.reduceMotion
                ? 0.5f
                : clamp01(0.5f
                        + 0.28f * sin(seconds * 0.030 + phaseY)
                        + 0.13f * sin(seconds * 0.014 + phaseX)
                        + 0.05f * sin(seconds * 0.008 + phaseZ));

        long now = SystemClock.uptimeMillis();
        long deltaMs = lastFrameMs == 0L ? 16L : Math.max(1L, Math.min(50L, now - lastFrameMs));
        lastFrameMs = now;
        float follow = 1f - (float) Math.exp(-deltaMs / 840f);
        if (!motionInitialized) {
            currentNx = targetNx;
            currentNy = targetNy;
            motionInitialized = true;
        } else {
            currentNx += (targetNx - currentNx) * follow;
            currentNy += (targetNy - currentNy) * follow;
        }

        float left = -spanX * currentNx;
        float top = -spanY * currentNy;
        dst.set(left, top, left + drawWidth, top + drawHeight);

        paint.setShader(null);
        paint.setAlpha(Math.round(255f * clamp01(alpha)));
        canvas.drawBitmap(artwork, null, dst, paint);
        paint.setAlpha(255);
    }

    private void drawDimmingGradient(Canvas canvas, int width, int height, float strength) {
        int dim = Math.round((214f - backgroundSettings.brightness * 1.28f) * clamp01(strength));
        paint.setShader(new LinearGradient(
                0f,
                0f,
                0f,
                height,
                new int[]{Color.argb(Math.max(50, dim - 52), 6, 8, 18), Color.argb(Math.max(68, dim - 24), 18, 13, 34), Color.argb(Math.max(86, dim), 7, 9, 20)},
                new float[]{0f, 0.52f, 1f},
                Shader.TileMode.CLAMP
        ));
        paint.setAlpha(255);
        canvas.drawRect(0f, 0f, width, height, paint);
        paint.setShader(null);
    }

    private void drawSolid(Canvas canvas, int width, int height) {
        paint.setShader(null);
        paint.setColor(parseHexColor(backgroundSettings.solidColor, Color.rgb(30, 58, 138)));
        paint.setAlpha(255);
        canvas.drawRect(0f, 0f, width, height, paint);
    }

    private void drawBlurGradient(Canvas canvas, int width, int height, double seconds) {
        paint.setShader(null);
        paint.setColor(adjustBrightness(paletteBackground, 0.42f + backgroundSettings.brightness / 240f));
        paint.setAlpha(255);
        canvas.drawRect(0f, 0f, width, height, paint);

        int[] colors = new int[]{palettePrimary, paletteSecondary, paletteAccent, palettePrimary, paletteSecondary, paletteAccent};
        float[] radii = new float[]{0.80f, 0.70f, 0.55f, 0.75f, 0.50f, 0.90f};
        for (int index = 0; index < 6; index++) {
            double speed = 0.010 + index * 0.0027;
            float cx = width * animatedValue(seconds, speed, phaseX + index * 0.83f, -0.18f, 1.18f);
            float cy = height * animatedValue(seconds, speed * 1.21, phaseY + index * 0.61f, -0.18f, 1.18f);
            int alpha = 88 - index * 5;
            drawBlob(canvas, cx, cy, Math.max(width, height) * radii[index], colors[index], Math.max(42, alpha));
        }
        drawDimmingGradient(canvas, width, height, 0.74f);
    }

    private void drawBlob(Canvas canvas, float cx, float cy, float radius, int color, int alpha) {
        paint.setShader(new RadialGradient(
                cx,
                cy,
                Math.max(1f, radius),
                new int[]{withAlpha(color, alpha), withAlpha(color, Math.round(alpha * 0.45f)), Color.TRANSPARENT},
                new float[]{0f, 0.48f, 1f},
                Shader.TileMode.CLAMP
        ));
        paint.setAlpha(255);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
    }

    private void drawNoise(Canvas canvas, int width, int height) {
        if (noiseBitmap == null || noiseBitmap.isRecycled()) {
            noiseBitmap = createNoiseBitmap();
        }
        noisePaint.setShader(new BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        noisePaint.setAlpha(34);
        canvas.drawRect(0f, 0f, width, height, noisePaint);
        noisePaint.setShader(null);
    }

    private Bitmap createBlurredArtwork(Bitmap source) {
        int width = Math.max(1, source.getWidth());
        int height = Math.max(1, source.getHeight());
        float scale = 220f / Math.max(width, height);
        int targetWidth = Math.max(48, Math.round(width * scale));
        int targetHeight = Math.max(48, Math.round(height * scale));
        Bitmap small = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
                .copy(Bitmap.Config.ARGB_8888, true);
        int radius = Math.max(4, Math.round(5f + backgroundSettings.blur * 0.12f));
        int passes = Math.max(2, Math.min(9, 2 + backgroundSettings.blur / 10));
        for (int pass = 0; pass < passes; pass++) {
            boxBlur(small, radius);
        }
        return small;
    }

    private Bitmap createNoiseBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[96 * 96];
        Random random = new Random(44L);
        for (int index = 0; index < pixels.length; index++) {
            int alpha = random.nextInt(42);
            int value = 210 + random.nextInt(46);
            pixels[index] = Color.argb(alpha, value, value, value);
        }
        bitmap.setPixels(pixels, 0, 96, 0, 0, 96, 96);
        return bitmap;
    }

    private void extractPalette(Bitmap artwork) {
        if (artwork == null || artwork.isRecycled()) {
            paletteBackground = Color.rgb(20, 23, 32);
            palettePrimary = Color.rgb(72, 64, 124);
            paletteSecondary = Color.rgb(145, 83, 131);
            paletteAccent = Color.rgb(62, 94, 130);
            return;
        }
        int width = artwork.getWidth();
        int height = artwork.getHeight();
        long r = 0L;
        long g = 0L;
        long b = 0L;
        long brightR = 0L;
        long brightG = 0L;
        long brightB = 0L;
        int count = 0;
        int brightCount = 0;
        int stepX = Math.max(1, width / 28);
        int stepY = Math.max(1, height / 28);
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int color = artwork.getPixel(x, y);
                int cr = Color.red(color);
                int cg = Color.green(color);
                int cb = Color.blue(color);
                r += cr;
                g += cg;
                b += cb;
                count++;
                if (cr + cg + cb > 210) {
                    brightR += cr;
                    brightG += cg;
                    brightB += cb;
                    brightCount++;
                }
            }
        }
        int avg = count <= 0 ? Color.rgb(35, 32, 48) : Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        int bright = brightCount <= 0 ? lighten(avg, 0.38f) : Color.rgb((int) (brightR / brightCount), (int) (brightG / brightCount), (int) (brightB / brightCount));
        paletteBackground = adjustBrightness(avg, 0.42f);
        palettePrimary = saturate(lighten(avg, 0.28f), 1.55f);
        paletteSecondary = saturate(bright, 1.35f);
        paletteAccent = rotateChannels(saturate(lighten(avg, 0.18f), 1.75f));
    }

    private void boxBlur(Bitmap bitmap, int radius) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] src = new int[width * height];
        int[] tmp = new int[width * height];
        bitmap.getPixels(src, 0, width, 0, 0, width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                int count = 0;
                for (int dx = -radius; dx <= radius; dx++) {
                    int px = Math.max(0, Math.min(width - 1, x + dx));
                    int color = src[y * width + px];
                    a += Color.alpha(color);
                    r += Color.red(color);
                    g += Color.green(color);
                    b += Color.blue(color);
                    count++;
                }
                tmp[y * width + x] = Color.argb(a / count, r / count, g / count, b / count);
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                int count = 0;
                for (int dy = -radius; dy <= radius; dy++) {
                    int py = Math.max(0, Math.min(height - 1, y + dy));
                    int color = tmp[py * width + x];
                    a += Color.alpha(color);
                    r += Color.red(color);
                    g += Color.green(color);
                    b += Color.blue(color);
                    count++;
                }
                src[y * width + x] = Color.argb(a / count, r / count, g / count, b / count);
            }
        }

        bitmap.setPixels(src, 0, width, 0, 0, width, height);
    }

    private float sin(double value) {
        return (float) Math.sin(value);
    }

    private float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private boolean shouldAnimate(String mode) {
        if (backgroundSettings.reduceMotion) {
            return false;
        }
        return AiLyricsSettings.BACKGROUND_MODE_GRADIENT.equals(mode)
                || AiLyricsSettings.BACKGROUND_MODE_BLUR_GRADIENT.equals(mode);
    }

    private float animatedValue(double seconds, double speed, float phase, float min, float max) {
        if (backgroundSettings.reduceMotion) {
            return (min + max) * 0.5f;
        }
        float wave = (float) ((Math.sin(seconds * speed + phase) + 1.0) * 0.5);
        return min + (max - min) * wave;
    }

    private int parseHexColor(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private int adjustBrightness(int color, float factor) {
        return Color.rgb(
                Math.max(0, Math.min(255, Math.round(Color.red(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.green(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor)))
        );
    }

    private int lighten(int color, float amount) {
        float t = clamp01(amount);
        return Color.rgb(
                Math.round(Color.red(color) * (1f - t) + 255f * t),
                Math.round(Color.green(color) * (1f - t) + 255f * t),
                Math.round(Color.blue(color) * (1f - t) + 255f * t)
        );
    }

    private int saturate(int color, float amount) {
        float grey = Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f;
        return Color.rgb(
                clampColor(Math.round(grey + (Color.red(color) - grey) * amount)),
                clampColor(Math.round(grey + (Color.green(color) - grey) * amount)),
                clampColor(Math.round(grey + (Color.blue(color) - grey) * amount))
        );
    }

    private int rotateChannels(int color) {
        return Color.rgb(Color.blue(color), Color.red(color), Color.green(color));
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

}
