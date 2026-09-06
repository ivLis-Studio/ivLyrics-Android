package kr.ivlis.ivlyricsandroid;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Debug-only integration fixture; not included in either release application. */
public final class EmbeddedSmokeActivity extends Activity {
    private static final String TAG = "IvLyricsSmoke";
    private static final long WAIT_TIMEOUT_MS = 10000L;
    private static MediaSession session;
    private static long lastSeek = -1;
    private final Handler main = new Handler();
    private TextView status;
    private View card;
    private Button open;
    private boolean destroyed;
    private boolean failed;

    @Override public void onCreate(Bundle state) {
        getIntent().putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, true);
        super.onCreate(state);
        AiLyricsSettings settings = new AiLyricsSettings(this);
        settings.markFirstLanguagePrompted("ko");
        settings.shutdown();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 72, 24, 24);
        root.setBackgroundColor(Color.rgb(18, 18, 18));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setText("ivLyrics integration fixture");
        root.addView(status);
        open = new Button(this);
        open.setText("Open full lyrics");
        open.setEnabled(false);
        open.setOnClickListener(v -> IvLyricsBridge.openLyrics(this));
        root.addView(open);
        setContentView(root);

        try {
            // A repeated launch gets a fresh owner and fresh transport assertions.
            // The session remains active while the full page is open above this Activity.
            if (session != null) session.release();
            lastSeek = -1L;
            session = new MediaSession(getApplicationContext(), "ivlyrics-debug-fixture");
            session.setCallback(new MediaSession.Callback() {
                @Override public void onSeekTo(long pos) { lastSeek = pos; setState(false, pos); }
                @Override public void onPlay() { setState(true, 6000); }
                @Override public void onPause() { setState(false, 6000); }
            });
            session.setActive(true);

            // Use the existing manual-result cache contract, without changing provider
            // preferences. Even an already attached card/foreground page sees fixture
            // text if it requests this track before the shared result is published.
            primeCache("one");
            primeCache("two");
            setMetadata("one");
            setState(false, 6000);
            IvLyricsBridge.initialize(this);
            IvLyricsBridge.onMediaController(session.getController());
            await("initial MediaSession snapshot", () -> isCurrentTrack("one", 6000), () -> {
                TrackSnapshot first = NowPlayingService.getLatestSnapshot();
                seedLyrics(first, "one");
                card = IvLyricsBridge.createPreview(this);
                root.addView(card, 1, new LinearLayout.LayoutParams(-1, -2));
                await("card attachment and shared lyrics", () -> card.getHeight() > 0
                        && card.getWidth() > 0 && matchesFixture(IvLyricsBridge.cachedLyrics(first), "one"),
                        this::runChecks);
            });
        } catch (Throwable error) { fail(error); }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        intent.putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, true);
        setIntent(intent);
        if ("full".equals(intent.getStringExtra("action"))) IvLyricsBridge.openLyrics(this);
        if ("card-click".equals(intent.getStringExtra("action")) && card != null) card.performClick();
        if ("settings".equals(intent.getStringExtra("action")) && card != null) card.performLongClick();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private static void setMetadata(String suffix) {
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "ivlyrics:fixture:" + suffix)
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Integration fixture " + suffix)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "ivLyrics test")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Debug only")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 24000).build());
    }

    private static void setState(boolean play, long position) {
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SEEK_TO)
            .setState(play ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                position, 1f, SystemClock.elapsedRealtime()).build());
    }

    private TrackSnapshot expectedTrack(String suffix) {
        return new TrackSnapshot("Integration fixture " + suffix, "ivLyrics test", "Debug only",
                getPackageName(), "ivlyrics:fixture:" + suffix, "", 24000L, 6000L,
                SystemClock.elapsedRealtime(), 1f, false, null, "");
    }

    private void primeCache(String suffix) {
        TrackSnapshot track = expectedTrack(suffix);
        LyricsDiskCache cache = new LyricsDiskCache(this, "base_lyrics", 350);
        cache.put(track.stableKey(), fixtureLyrics(track, suffix));
        require(matchesFixture(cache.get(track.stableKey()), suffix), "synthetic cache ready: " + suffix);
    }

    private static LyricsResult fixtureLyrics(TrackSnapshot track, String suffix) {
        List<LyricsLine> lines = new ArrayList<>();
        String[] text = {"one".equals(suffix) ? "첫 번째 가사를 확인해요" : "새 트랙의 첫 번째 가사예요",
                "글자마다 빛이 채워져요", "다음 줄로 부드럽게 이동해요", "다시 누르면 여기로 돌아와요"};
        for (int i = 0; i < text.length; i++) {
            List<LyricsLine.Syllable> syllables = new ArrayList<>();
            for (int j = 0; j < text[i].length(); j++) {
                syllables.add(new LyricsLine.Syllable(text[i].substring(j, j + 1),
                    i * 6000L + j * 300L, i * 6000L + (j + 1) * 300L));
            }
            lines.add(new LyricsLine(i * 6000L, (i + 1) * 6000L, text[i], syllables));
        }
        return new LyricsResult(lines, "Integration fixture", "Synthetic text; debug only; " + suffix,
                true, track.isrc, track.trackId).withSelection("debug-fixture", "manual");
    }

    private static boolean matchesFixture(LyricsResult result, String suffix) {
        return result != null && result.lines.size() == 4
                && "Integration fixture".equals(result.providerLabel)
                && ("Synthetic text; debug only; " + suffix).equals(result.detail);
    }

    private static boolean isCurrentTrack(String suffix, long position) {
        TrackSnapshot current = NowPlayingService.getLatestSnapshot();
        return current != null && ("ivlyrics:fixture:" + suffix).equals(current.mediaId)
                && ("Integration fixture " + suffix).equals(current.title)
                && !current.playing && current.positionNow() == position;
    }

    private void seedLyrics(TrackSnapshot track, String suffix) {
        if (track == null || !("ivlyrics:fixture:" + suffix).equals(track.mediaId)) {
            throw new AssertionError("fixture lyrics require the delivered track: " + suffix);
        }
        IvLyricsBridge.publishLyrics(track, fixtureLyrics(track, suffix), "ko");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        Log.i(TAG, "PASS: " + message);
    }

    private void runChecks() {
        TrackSnapshot first = NowPlayingService.getLatestSnapshot();
        require(isCurrentTrack("one", 6000), "MediaSession metadata delivered");
        require(!first.playing && first.positionNow() == 6000, "paused position does not advance");
        require(card.getHeight() > 0 && card.getWidth() > 0, "card measured and attached");
        require(matchesFixture(IvLyricsBridge.cachedLyrics(first), "one"), "card result shared with full page");
        require(NowPlayingService.seekTo(12000), "seek command accepted by controller");
        await("seek callback and timeline", () -> lastSeek == 12000 && isCurrentTrack("one", 12000), () -> {
            require(lastSeek == 12000, "seek reaches owning MediaSession");
            require(NowPlayingService.getLatestSnapshot().positionNow() == 12000, "seek callback updates timeline");
            setMetadata("two");
            await("second track metadata", () -> isCurrentTrack("two", 12000), () -> {
                TrackSnapshot second = NowPlayingService.getLatestSnapshot();
                require(second.title.equals("Integration fixture two"), "track switch delivered");
                // A cache hit for track two may already have arrived. It must never be
                // track one's result; checking null alone races that valid cache hit.
                LyricsResult current = IvLyricsBridge.cachedLyrics(second);
                require(current == null || matchesFixture(current, "two"), "old track lyrics not reused");
                seedLyrics(second, "two");
                setState(false, 7400);
                await("final track position and shared result", () -> isCurrentTrack("two", 7400)
                        && matchesFixture(IvLyricsBridge.cachedLyrics(NowPlayingService.getLatestSnapshot()), "two"), () -> {
                    open.setEnabled(true);
                    status.setText("PASS: metadata, pause, seek, track switch, shared lyrics");
                    Log.i(TAG, "ALL_CHECKS_PASSED");
                });
            });
        });
    }

    /** Wait for the actual callback/layout state, with a timeout that names the failed phase. */
    private void await(String phase, BooleanSupplier ready, Runnable next) {
        long deadline = SystemClock.elapsedRealtime() + WAIT_TIMEOUT_MS;
        main.post(new Runnable() {
            @Override public void run() {
                if (destroyed || failed) return;
                try {
                    if (ready.getAsBoolean()) next.run();
                    else if (SystemClock.elapsedRealtime() >= deadline) {
                        throw new AssertionError("timed out waiting for " + phase);
                    } else main.postDelayed(this, 16L);
                } catch (Throwable error) { fail(error); }
            }
        });
    }

    private void fail(Throwable error) {
        failed = true;
        main.removeCallbacksAndMessages(null);
        status.setText("FAIL: " + error.getMessage());
        Log.e(TAG, "CHECK_FAILED", error);
    }
}
