package kr.ivlis.ivlyricsandroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Choreographer;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import java.io.File;
import java.lang.reflect.Field;

/**
 * Debug-only stand-in for the standalone app's {@code MainActivity}: the shared player page
 * with the full lyrics page above it, not embedded, playing a synthetic MediaSession through
 * the controller path the Spotify module uses, so no notification listener and no Spotify
 * package take part. The page has no lyrics repository; this fixture hands it the synthetic
 * rows of {@link EmbeddedSmokeActivity}, so synthetic tracks never reach a provider.
 *
 * <p>Settings, secure values, caches and files live in a namespace of their own (this
 * Activity's storage and the application context it hands out are wrapped). Placeholder
 * Spotify credentials in that namespace satisfy the setup gate without touching this
 * application's own settings. The gate's other half, notification access, is the harness's
 * job: a placeholder entry naming this package in {@code enabled_notification_listeners},
 * never this app's real listener, which would publish its own (non-Spotify) state over the
 * synthetic session.
 *
 * <p>With the frame probe enabled it logs one {@code PAGE} line per drawn frame: the
 * Choreographer frame time, the lyrics page's translation, whether the lyrics page is open
 * (a closing page is no longer open while it slides away), whether the player page is
 * visible, its reveal clip height and the progress bar's position.
 */
public final class StandaloneSmokeActivity extends BaseLyricsActivity {
    /**
     * {@code am broadcast -a <action> -p <package> [--es track three|one] --ez play true
     * --el position 4000}: the synthetic session's track and playback state.
     */
    static final String ACTION_STANDALONE_PLAYBACK = "kr.ivlis.ivlyricsandroid.debug.STANDALONE_PLAYBACK";
    /**
     * As SheetSmokeActivity's touch, on this page: {@code --ef x 640 --ef y 156 --el hold 80
     * --ef dy 900 --el ms 280 [--ez release false] [--ez up true]}.
     */
    static final String ACTION_STANDALONE_TOUCH = "kr.ivlis.ivlyricsandroid.debug.STANDALONE_TOUCH";
    private static final String TAG = "IvLyricsSmoke";
    private static final String NAMESPACE = "standalone_smoke";
    /** Launch extras: a static page background (pixel comparisons), the first track and state. */
    private static final String EXTRA_REDUCE_MOTION = "reduceMotion";
    private static final String EXTRA_TRACK = "track";
    private static final String EXTRA_PLAY = "play";
    private static final String EXTRA_POSITION = "position";
    private static MediaSession session;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final StringBuilder probeLine = new StringBuilder(96);
    private final Rect probeClip = new Rect();
    private final ViewTreeObserver.OnDrawListener pageProbe = this::logPageFrame;
    private final Choreographer.FrameCallback probeClock = new Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            probeFrameNanos = frameTimeNanos;
            if (probeClockRunning) Choreographer.getInstance().postFrameCallback(this);
        }
    };
    private Context storage;
    private String sessionTrack = "";
    private int deliveryGeneration;
    private boolean probeEnabled;
    private boolean probeClockRunning;
    private long probeFrameNanos;
    private boolean receiversRegistered;
    private boolean destroyed;
    private TouchScript touch;
    private Field lyricsPageField;
    private Field lyricsPageOpenField;
    private Field mainPageField;
    private Field progressViewField;
    private Field progressPositionField;

    private final BroadcastReceiver playbackControl = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || session == null) return;
            boolean play = intent.getBooleanExtra(EXTRA_PLAY, false);
            long position = Math.max(0L, intent.getLongExtra(EXTRA_POSITION, 6000L));
            String track = intent.getStringExtra(EXTRA_TRACK);
            if (track != null && !track.equals(sessionTrack)) {
                setTrack(track, play, position);
            } else {
                setState(play, position);
            }
            Log.i(TAG, "STANDALONE_PLAYBACK " + sessionTrack + " " + (play ? "play" : "pause") + " " + position);
        }
    };
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

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        storage = new Storage(base.getApplicationContext());
    }

    /** Shared components keep their storage in the application context: hand out the namespace. */
    @Override public Context getApplicationContext() {
        return storage != null ? storage : super.getApplicationContext();
    }

    @Override public SharedPreferences getSharedPreferences(String name, int mode) {
        return super.getSharedPreferences(NAMESPACE + "_" + name, mode);
    }

    @Override public File getFilesDir() { return namespaced(super.getFilesDir()); }

    @Override public File getCacheDir() { return namespaced(super.getCacheDir()); }

    @Override protected void onCreate(Bundle state) {
        Intent launch = getIntent();
        launch.removeExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE);
        probeEnabled = LyricsFrameProbe.enabled(this);
        prepareSettings(launch.getBooleanExtra(EXTRA_REDUCE_MOTION, false));
        super.onCreate(state);
        try {
            // No provider lookups for synthetic tracks: rows are delivered by this fixture.
            declaredField(BaseLyricsActivity.class, "lyricsRepository").set(this, null);
        } catch (IllegalAccessException error) {
            throw new AssertionError(error);
        }
        if (!NowPlayingService.isNotificationAccessEnabled(this)) {
            Log.e(TAG, "CHECK_FAILED standalone fixture needs the harness's placeholder notification-access entry");
        }
        // adb sends the measurement broadcasts, so the receivers are exported (see EmbeddedSmokeActivity).
        registerReceiver(playbackControl, new IntentFilter(ACTION_STANDALONE_PLAYBACK), RECEIVER_EXPORTED);
        registerReceiver(touchControl, new IntentFilter(ACTION_STANDALONE_TOUCH), RECEIVER_EXPORTED);
        receiversRegistered = true;
        if (session != null) session.release();
        session = new MediaSession(super.getApplicationContext(), "ivlyrics-standalone-fixture");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onSeekTo(long position) { setState(isPlaying(), position); }
            @Override public void onPlay() { setState(true, currentPosition()); }
            @Override public void onPause() { setState(false, currentPosition()); }
        });
        session.setActive(true);
        String track = launch.getStringExtra(EXTRA_TRACK);
        setTrack(track == null || track.isEmpty() ? "three" : track,
                launch.getBooleanExtra(EXTRA_PLAY, false), Math.max(0L, launch.getLongExtra(EXTRA_POSITION, 2000L)));
        NowPlayingService.attachEmbeddedController(session.getController());
    }

    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (touch != null) touch.lift(SystemClock.uptimeMillis());
        if (receiversRegistered) {
            unregisterReceiver(playbackControl);
            unregisterReceiver(touchControl);
            receiversRegistered = false;
        }
        super.onDestroy();
    }

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (probeEnabled) getWindow().getDecorView().getViewTreeObserver().addOnDrawListener(pageProbe);
    }

    @Override public void onDetachedFromWindow() {
        ViewTreeObserver observer = getWindow().getDecorView().getViewTreeObserver();
        if (probeEnabled && observer.isAlive()) observer.removeOnDrawListener(pageProbe);
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

    /** This fixture's own settings namespace; nothing of the application's settings is read. */
    private void prepareSettings(boolean reduceMotion) {
        AiLyricsSettings settings = new AiLyricsSettings(this);
        settings.setSpotifyApiCredentials("standalone-fixture", "standalone-fixture");
        settings.markFirstLanguagePrompted("en");
        settings.setBackgroundReduceMotion(reduceMotion);
        settings.shutdown();
        // One-time hints and the automatic update check would add their own UI and requests.
        getSharedPreferences("ui_hints", MODE_PRIVATE).edit()
                .putBoolean("lyrics_meta_menu_tip_shown", true).commit();
        getSharedPreferences("app_updates", MODE_PRIVATE).edit()
                .putLong("last_auto_update_check_ms", System.currentTimeMillis()).commit();
    }

    private void setTrack(String track, boolean play, long position) {
        sessionTrack = track;
        int generation = ++deliveryGeneration;
        session.setMetadata(new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, EmbeddedSmokeActivity.fixtureUri(track))
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Integration fixture " + track)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "ivLyrics test")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Debug only")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, EmbeddedSmokeActivity.durationMs(track)).build());
        setState(play, position);
        long deadline = SystemClock.uptimeMillis() + 10_000L;
        main.post(new Runnable() {
            @Override public void run() {
                if (destroyed || generation != deliveryGeneration) return;
                TrackSnapshot current = NowPlayingService.getLatestSnapshot();
                String key = currentLyricsKey();
                if (current != null && EmbeddedSmokeActivity.fixtureUri(track).equals(current.mediaId)
                        && current.stableKey().equals(key)) {
                    onLyricsLoaded(key, EmbeddedSmokeActivity.fixtureLyrics(current, track));
                    Log.i(TAG, "STANDALONE_READY " + track + " " + position);
                } else if (SystemClock.uptimeMillis() > deadline) {
                    Log.e(TAG, "CHECK_FAILED standalone lyrics delivery timed out: " + track);
                } else {
                    main.postDelayed(this, 16L);
                }
            }
        });
    }

    private static void setState(boolean play, long position) {
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SEEK_TO)
                .setState(play ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                        position, 1f, SystemClock.elapsedRealtime()).build());
    }

    private static boolean isPlaying() {
        PlaybackState state = session == null ? null : session.getController().getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    private static long currentPosition() {
        PlaybackState state = session == null ? null : session.getController().getPlaybackState();
        if (state == null) return 0L;
        long elapsed = state.getState() == PlaybackState.STATE_PLAYING
                ? SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime() : 0L;
        return Math.max(0L, state.getPosition() + elapsed);
    }

    private String currentLyricsKey() {
        Object value = field(BaseLyricsActivity.class, this, "currentLyricsKey");
        return value == null ? "" : value.toString();
    }

    private void logPageFrame() {
        if (lyricsPageField == null) {
            lyricsPageField = declaredField(BaseLyricsActivity.class, "lyricsPage");
            lyricsPageOpenField = declaredField(BaseLyricsActivity.class, "lyricsPageVisible");
            mainPageField = declaredField(BaseLyricsActivity.class, "mainPage");
            progressViewField = declaredField(BaseLyricsActivity.class, "playerProgressView");
            progressPositionField = declaredField(PlayerProgressView.class, "positionMs");
        }
        View page = (View) read(lyricsPageField, this);
        View player = (View) read(mainPageField, this);
        Object progress = read(progressViewField, this);
        StringBuilder line = probeLine;
        line.setLength(0);
        line.append("PAGE frame=");
        LyricsFrameProbe.appendFixed(line, probeFrameNanos / 1_000_000d, 4);
        line.append(" ty=");
        LyricsFrameProbe.appendFixed(line, page == null || page.getVisibility() != View.VISIBLE
                ? -1d : page.getTranslationY(), 2);
        line.append(" open=").append((Boolean) read(lyricsPageOpenField, this) ? 1 : 0);
        line.append(" main=").append(player == null ? -1 : player.getVisibility() == View.VISIBLE ? 1 : 0);
        line.append(" clip=").append(player == null || !player.getClipBounds(probeClip) ? -1 : probeClip.height());
        line.append(" prog=").append(progress == null ? -1L : (Long) read(progressPositionField, progress));
        line.append(" draw=");
        LyricsFrameProbe.appendFixed(line, System.nanoTime() / 1_000_000d, 3);
        LyricsFrameProbe.log(line);
    }

    private static Object field(Class<?> type, Object instance, String name) {
        return read(declaredField(type, name), instance);
    }

    private static Field declaredField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static Object read(Field field, Object instance) {
        try {
            return field.get(instance);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static File namespaced(File parent) {
        File directory = new File(parent, NAMESPACE);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create fixture storage");
        }
        return directory;
    }

    /** The application context handed to shared components: same services, namespaced storage. */
    private static final class Storage extends ContextWrapper {
        Storage(Context application) { super(application); }
        @Override public Context getApplicationContext() { return this; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return super.getSharedPreferences(NAMESPACE + "_" + name, mode);
        }
        @Override public File getFilesDir() { return namespaced(super.getFilesDir()); }
        @Override public File getCacheDir() { return namespaced(super.getCacheDir()); }
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
