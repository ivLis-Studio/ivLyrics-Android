package kr.ivlis.ivlyricsandroid;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.MotionEvent;
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
 *
 * <p>{@link #ACTION_SHEET_TOUCH} plays a synthetic touch on this page through the Activity's
 * own dispatch: down, an optional hold, then one move per display frame and up, so drag
 * measurements do not depend on the cadence of adb input injection.
 */
public final class SheetSmokeActivity extends BaseLyricsActivity {
    static final String EXTRA_MODULE_APK = "moduleApk";
    /**
     * {@code am broadcast -a <action> -p <package> --ef x 640 --ef y 156 --el hold 80 --ef dy 900
     * --el ms 280}: press at (x, y) in window pixels, hold, move down by dy over ms, then lift.
     * {@code --ez release false} keeps the pointer down at the end; {@code --ez up true} lifts it.
     */
    static final String ACTION_SHEET_TOUCH = "kr.ivlis.ivlyricsandroid.debug.SHEET_TOUCH";
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
    private TouchScript touch;
    private boolean touchControlRegistered;
    private final BroadcastReceiver touchControl = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (touch != null) touch.lift(SystemClock.uptimeMillis());
            if (intent.getBooleanExtra("up", false)) return;
            touch = new TouchScript(intent.getFloatExtra("x", 640f), intent.getFloatExtra("y", 156f),
                    intent.getLongExtra("hold", 80L), intent.getFloatExtra("dy", 0f),
                    intent.getLongExtra("ms", 280L), intent.getBooleanExtra("release", true));
            Choreographer.getInstance().postFrameCallback(touch);
        }
    };

    @Override protected void onCreate(Bundle state) {
        getIntent().putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, true);
        getIntent().putExtra(EXTRA_OPEN_LYRICS_PAGE, true);
        // The window attaches after onResume, which starts the probe clock.
        probeEnabled = LyricsFrameProbe.enabled(this);
        super.onCreate(state);
        // adb sends the measurement broadcast, so the receiver is exported (see EmbeddedSmokeActivity).
        registerReceiver(touchControl, new IntentFilter(ACTION_SHEET_TOUCH), RECEIVER_EXPORTED);
        touchControlRegistered = true;
    }

    @Override protected void onDestroy() {
        if (touch != null) touch.lift(SystemClock.uptimeMillis());
        if (touchControlRegistered) {
            unregisterReceiver(touchControl);
            touchControlRegistered = false;
        }
        super.onDestroy();
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

    /** One synthetic pointer: down, hold, one move per display frame, then up unless held. */
    private final class TouchScript implements Choreographer.FrameCallback {
        private final float x;
        private final float y;
        private final long hold;
        private final float dy;
        private final long duration;
        private final boolean release;
        private long downTime = -1L;
        private long moveStart = -1L;
        private float lastY;

        TouchScript(float x, float y, long hold, float dy, long duration, boolean release) {
            this.x = x;
            this.y = y;
            this.hold = Math.max(0L, hold);
            this.dy = dy;
            this.duration = Math.max(0L, duration);
            this.release = release;
        }

        @Override public void doFrame(long frameTimeNanos) {
            if (touch != this) return;
            long now = SystemClock.uptimeMillis();
            if (downTime < 0L) {
                downTime = now;
                lastY = y;
                send(MotionEvent.ACTION_DOWN, now, lastY);
            } else if (now - downTime >= hold) {
                if (moveStart < 0L) moveStart = now;
                float progress = duration == 0L ? 1f : Math.min(1f, (now - moveStart) / (float) duration);
                if (dy != 0f) {
                    lastY = y + dy * progress;
                    send(MotionEvent.ACTION_MOVE, now, lastY);
                }
                if (progress >= 1f) {
                    if (release) lift(now);
                    return;
                }
            }
            Choreographer.getInstance().postFrameCallback(this);
        }

        void lift(long now) {
            if (touch != this) return;
            touch = null;
            Choreographer.getInstance().removeFrameCallback(this);
            if (downTime >= 0L) send(MotionEvent.ACTION_UP, now, lastY);
        }

        private void send(int action, long eventTime, float pointerY) {
            MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, pointerY, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            if (probeEnabled) {
                StringBuilder line = new StringBuilder(64).append("TOUCH action=").append(action).append(" y=");
                LyricsFrameProbe.appendFixed(line, pointerY, 1).append(" draw=");
                LyricsFrameProbe.appendFixed(line, System.nanoTime() / 1_000_000d, 3);
                LyricsFrameProbe.log(line);
            }
            dispatchTouchEvent(event);
            event.recycle();
        }
    }
}
