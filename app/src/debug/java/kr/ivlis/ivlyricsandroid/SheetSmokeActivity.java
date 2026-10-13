package kr.ivlis.ivlyricsandroid;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import dalvik.system.PathClassLoader;

/**
 * Debug-only stand-in for the Spotify module's SpotifyLyricsActivity: the shared lyrics page
 * opened as a second Activity above {@link EmbeddedSmokeActivity}, in the same task and
 * process, moved by the module's own {@code LyricsSheetTransition}. That class is loaded from
 * the assembled module APK with this application's class loader as its parent, so the
 * production sheet code drives this build's shared page and player surfaces unchanged.
 *
 * <p>With the frame probe enabled it logs one {@code SHEET} line per drawn sheet frame: the
 * Choreographer frame time (recorded by a frame callback while resumed, since a draw listener
 * has no public frame clock) and the page root's translation.
 */
public final class SheetSmokeActivity extends BaseLyricsActivity {
    static final String EXTRA_MODULE_APK = "moduleApk";
    private static final String TAG = "IvLyricsSmoke";
    private static final String TRANSITION = "dev.ivlyrics.spotify.LyricsSheetTransition";
    private final StringBuilder probeLine = new StringBuilder(64);
    private final ViewTreeObserver.OnDrawListener sheetProbe = this::logSheetFrame;
    private final Choreographer.FrameCallback probeClock = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            probeFrameNanos = frameTimeNanos;
            if (probeClockRunning) Choreographer.getInstance().postFrameCallback(this);
        }
    };
    private boolean probeEnabled;
    private boolean probeClockRunning;
    private long probeFrameNanos;

    @Override protected void onCreate(Bundle state) {
        getIntent().putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, true);
        getIntent().putExtra(EXTRA_OPEN_LYRICS_PAGE, true);
        // The window attaches after onResume, which starts the probe clock.
        probeEnabled = LyricsFrameProbe.enabled(this);
        super.onCreate(state);
    }

    @Override protected LyricsActivityHost createLyricsActivityHost(View root) {
        String apk = getIntent().getStringExtra(EXTRA_MODULE_APK);
        if (apk == null || apk.isEmpty()) {
            Log.e(TAG, "SHEET_HOST_FAILED missing " + EXTRA_MODULE_APK);
            return null;
        }
        try {
            ClassLoader loader = new PathClassLoader(apk, getClassLoader());
            Class<?> type = loader.loadClass(TRANSITION);
            LyricsActivityHost host = (LyricsActivityHost) type
                    .getConstructor(Activity.class, View.class).newInstance(this, root);
            Log.i(TAG, "SHEET_HOST " + type.getName() + " from " + apk);
            return host;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Log.e(TAG, "SHEET_HOST_FAILED", error);
            return null;
        }
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (probeEnabled) getWindow().getDecorView().getViewTreeObserver().addOnDrawListener(sheetProbe);
    }

    @Override public void onDetachedFromWindow() {
        ViewTreeObserver observer = getWindow().getDecorView().getViewTreeObserver();
        if (probeEnabled && observer.isAlive()) observer.removeOnDrawListener(sheetProbe);
        super.onDetachedFromWindow();
    }

    @Override protected void onResume() {
        super.onResume();
        if (probeEnabled && !probeClockRunning) {
            probeClockRunning = true;
            Choreographer.getInstance().postFrameCallback(probeClock);
        }
    }

    @Override protected void onPause() {
        probeClockRunning = false;
        Choreographer.getInstance().removeFrameCallback(probeClock);
        super.onPause();
    }

    private void logSheetFrame() {
        ViewGroup content = findViewById(android.R.id.content);
        View root = content == null || content.getChildCount() == 0 ? null : content.getChildAt(0);
        StringBuilder line = probeLine;
        line.setLength(0);
        line.append("SHEET frame=");
        LyricsFrameProbe.appendFixed(line, probeFrameNanos / 1_000_000d, 4);
        line.append(" ty=");
        LyricsFrameProbe.appendFixed(line, root == null ? -1d : root.getTranslationY(), 2);
        line.append(" draw=");
        LyricsFrameProbe.appendFixed(line, System.nanoTime() / 1_000_000d, 3);
        LyricsFrameProbe.log(line);
    }
}
