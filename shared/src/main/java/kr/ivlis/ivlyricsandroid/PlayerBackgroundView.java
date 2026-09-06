package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;
import java.lang.ref.WeakReference;

public final class PlayerBackgroundView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint noisePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final Runnable animationTick = this::postInvalidateOnAnimation;

    private long artworkGeneration;
    private Runnable cancelArtworkPreparation;
    private Bitmap sourceArtwork;
    private Bitmap blurredArtwork;
    private Bitmap noiseBitmap;
    private BitmapShader noiseShader;
    private BlobShaderCache blobShaderCache;
    private LinearGradient dimmingGradientShader;
    private int dimmingGradientHeight = -1;
    private int dimmingGradientDim = Integer.MIN_VALUE;
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
        if (artwork == null) blurredArtwork = null;
        requestBlurredArtwork();
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
            requestBlurredArtwork();
        }
        postInvalidateOnAnimation();
    }

    private void requestBlurredArtwork() {
        if (cancelArtworkPreparation != null) cancelArtworkPreparation.run();
        long generation = ++artworkGeneration;
        WeakReference<PlayerBackgroundView> reference = new WeakReference<>(this);
        cancelArtworkPreparation = BackgroundArtworkCache.request(sourceArtwork, sourceArtworkKey, backgroundSettings.blur, bitmap -> {
            PlayerBackgroundView view = reference.get();
            if (view == null || view.artworkGeneration != generation || bitmap == null) return;
            view.blurredArtwork = bitmap;
            view.postInvalidateOnAnimation();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestBlurredArtwork();
    }

    @Override
    protected void onDetachedFromWindow() {
        artworkGeneration++;
        if (cancelArtworkPreparation != null) cancelArtworkPreparation.run();
        cancelArtworkPreparation = null;
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
        if (dimmingGradientShader == null || dimmingGradientHeight != height || dimmingGradientDim != dim) {
            dimmingGradientShader = new LinearGradient(
                    0f,
                    0f,
                    0f,
                    height,
                    new int[]{Color.argb(Math.max(50, dim - 52), 6, 8, 18), Color.argb(Math.max(68, dim - 24), 18, 13, 34), Color.argb(Math.max(86, dim), 7, 9, 20)},
                    new float[]{0f, 0.52f, 1f},
                    Shader.TileMode.CLAMP
            );
            dimmingGradientHeight = height;
            dimmingGradientDim = dim;
        }
        paint.setShader(dimmingGradientShader);
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

        for (int index = 0; index < 6; index++) {
            double speed = 0.010 + index * 0.0027;
            float cx = width * animatedValue(seconds, speed, phaseX + index * 0.83f, -0.18f, 1.18f);
            float cy = height * animatedValue(seconds, speed * 1.21, phaseY + index * 0.61f, -0.18f, 1.18f);
            int alpha = 88 - index * 5;
            drawBlob(
                    canvas,
                    index,
                    cx,
                    cy,
                    Math.max(width, height) * blobRadiusFactor(index),
                    blobColor(index),
                    Math.max(42, alpha)
            );
        }
        drawDimmingGradient(canvas, width, height, 0.74f);
    }

    private void drawBlob(Canvas canvas, int index, float cx, float cy, float radius, int color, int alpha) {
        if (blobShaderCache == null) {
            blobShaderCache = new BlobShaderCache();
        }
        float shaderRadius = Math.max(1f, radius);
        RadialGradient shader = blobShaderCache.shaders[index];
        boolean rebuild = shader == null
                || Float.compare(blobShaderCache.radii[index], shaderRadius) != 0
                || blobShaderCache.colors[index] != color
                || blobShaderCache.alphas[index] != alpha;
        if (rebuild) {
            // Preserve the original center-bound shader for the first frame after a cache change.
            shader = new RadialGradient(
                    cx,
                    cy,
                    shaderRadius,
                    new int[]{withAlpha(color, alpha), withAlpha(color, Math.round(alpha * 0.45f)), Color.TRANSPARENT},
                    new float[]{0f, 0.48f, 1f},
                    Shader.TileMode.CLAMP
            );
            blobShaderCache.shaders[index] = shader;
            blobShaderCache.radii[index] = shaderRadius;
            blobShaderCache.colors[index] = color;
            blobShaderCache.alphas[index] = alpha;
            blobShaderCache.canonical[index] = false;
        } else {
            if (!blobShaderCache.canonical[index]) {
                // Switch once to an origin-bound shader so later frames only update its matrix.
                shader = new RadialGradient(
                        0f,
                        0f,
                        shaderRadius,
                        new int[]{withAlpha(color, alpha), withAlpha(color, Math.round(alpha * 0.45f)), Color.TRANSPARENT},
                        new float[]{0f, 0.48f, 1f},
                        Shader.TileMode.CLAMP
                );
                blobShaderCache.shaders[index] = shader;
                blobShaderCache.canonical[index] = true;
            }
            if (blobShaderCache.matrix == null) {
                blobShaderCache.matrix = new Matrix();
            }
            blobShaderCache.matrix.setTranslate(cx, cy);
            shader.setLocalMatrix(blobShaderCache.matrix);
        }
        paint.setShader(shader);
        paint.setAlpha(255);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
    }

    private float blobRadiusFactor(int index) {
        switch (index) {
            case 0:
                return 0.80f;
            case 1:
                return 0.70f;
            case 2:
                return 0.55f;
            case 3:
                return 0.75f;
            case 4:
                return 0.50f;
            default:
                return 0.90f;
        }
    }

    private int blobColor(int index) {
        switch (index % 3) {
            case 0:
                return palettePrimary;
            case 1:
                return paletteSecondary;
            default:
                return paletteAccent;
        }
    }

    private void drawNoise(Canvas canvas, int width, int height) {
        if (noiseBitmap == null || noiseBitmap.isRecycled()) {
            noiseBitmap = createNoiseBitmap();
            noiseShader = new BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        } else if (noiseShader == null) {
            noiseShader = new BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        }
        noisePaint.setShader(noiseShader);
        noisePaint.setAlpha(34);
        canvas.drawRect(0f, 0f, width, height, noisePaint);
        noisePaint.setShader(null);
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

    private static final class BlobShaderCache {
        final RadialGradient[] shaders = new RadialGradient[6];
        final float[] radii = new float[6];
        final int[] colors = new int[6];
        final int[] alphas = new int[6];
        final boolean[] canonical = new boolean[6];
        Matrix matrix;
    }

}
