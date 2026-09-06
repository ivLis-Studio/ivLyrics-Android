package kr.ivlis.ivlyricsandroid;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-local prepared backgrounds shared by the main page and PiP. */
final class BackgroundArtworkCache {
    interface Callback { void onReady(Bitmap bitmap); }
    private record Key(Object artwork, int blur) {}
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(1);
    private static final PreparedValueCache<Key, Bitmap> CACHE = new PreparedValueCache<>(8, WORKER, MAIN::post);

    private BackgroundArtworkCache() {}

    static Runnable request(Bitmap source, String artworkKey, int blur, Callback callback) {
        if (source == null || source.isRecycled()) return () -> {};
        Key key = new Key(artworkKey == null || artworkKey.isEmpty() ? source : artworkKey, blur);
        return CACHE.request(key, () -> prepare(source, blur), callback::onReady);
    }

    static Bitmap prepare(Bitmap source, int blur) {
        int width = Math.max(1, source.getWidth());
        int height = Math.max(1, source.getHeight());
        float scale = 220f / Math.max(width, height);
        int targetWidth = Math.max(48, Math.round(width * scale));
        int targetHeight = Math.max(48, Math.round(height * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);
        Bitmap result = scaled.copy(Bitmap.Config.ARGB_8888, true);
        if (scaled != source) scaled.recycle();
        int[] pixels = new int[targetWidth * targetHeight];
        int[] scratch = new int[pixels.length];
        int radius = Math.max(4, Math.round(5f + blur * .12f));
        int passes = Math.max(2, Math.min(9, 2 + blur / 10));
        for (int pass = 0; pass < passes; pass++) {
            // Keep the old Bitmap round-trip at every pass: translucent artwork
            // is quantized through Android's premultiplied storage here.
            result.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
            ExactBoxBlur.pass(pixels, scratch, targetWidth, targetHeight, radius);
            result.setPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight);
        }
        return result;
    }
}
