package kr.ivlis.ivlyricsandroid;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Debug-only integration fixture; not included in either release application. */
public final class EmbeddedSmokeActivity extends Activity {
    private static final String TAG = "IvLyricsSmoke";
    private static final long WAIT_TIMEOUT_MS = 10000L;
    /**
     * Measurement control for the synthetic session while the full page covers this
     * fixture: {@code am broadcast -a <action> -p <package> --ez play true --el position 4000}.
     */
    static final String ACTION_FIXTURE_PLAYBACK = "kr.ivlis.ivlyricsandroid.debug.FIXTURE_PLAYBACK";
    /** Measurement-only track with many long timed rows; not part of the automated checks. */
    private static final String DENSE = "three";
    private static final int DENSE_LINES = 40;
    private static final long DENSE_DURATION_MS = 140_000L;
    private static MediaSession session;
    private static long lastSeek = -1;
    private final Handler main = new Handler();
    private final BroadcastReceiver playbackControl = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (session == null || intent == null) return;
            boolean play = intent.getBooleanExtra("play", false);
            long position = Math.max(0L, intent.getLongExtra("position", 6000L));
            if (DENSE.equals(intent.getStringExtra("track"))) {
                switchToDenseTrack(position);
                return;
            }
            setState(play, position);
            Log.i(TAG, "FIXTURE_PLAYBACK " + (play ? "play" : "pause") + " " + position);
        }
    };
    private boolean playbackControlRegistered;
    private TextView status;
    private View card;
    private View inline;
    private MainLyricPreviewView inlinePreview;
    private Context fixtureContext;
    private AiLyricsSettings fixtureSettings;
    private Button open;
    private boolean destroyed;
    private boolean failed;

    @Override public void onCreate(Bundle state) {
        getIntent().putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, true);
        super.onCreate(state);
        Context fixtureApplication = new FixtureContext(getApplicationContext(), null);
        fixtureContext = new FixtureContext(this, fixtureApplication);
        // Only this fixture namespace is reset; installed user preferences and caches
        // are never read, cleared, or edited by the synthetic test.
        fixtureApplication.getSharedPreferences(AiLyricsSettings.PREFS_NAME, MODE_PRIVATE).edit().clear().commit();
        fixtureSettings = new AiLyricsSettings(fixtureContext);
        fixtureSettings.setUiLang("ko");
        fixtureSettings.markFirstLanguagePrompted("en");
        fixtureSettings.setLanguageRule("en", true, true, "ko");
        fixtureSettings.setPreviewItems(AiLyricsSettings.PREVIEW_ITEM_ORIGINAL);
        fixtureSettings.setLyricsTextAlignment(AiLyricsSettings.LYRICS_ALIGN_LEFT);
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
        open.setOnClickListener(v -> IvLyricsBridge.openLyrics(fixtureContext));
        root.addView(open);
        setContentView(root);

        try {
            require(!fixtureSettings.snapshot().metadataTranslationEnabled, "new title translation defaults off");
            require(!fixtureSettings.snapshot().syncedLyricsKaraokeAnimationEnabled, "new virtual karaoke defaults off");
            fixtureSettings.setMetadataTranslationEnabled(true);
            fixtureSettings.setSyncedLyricsKaraokeAnimationEnabled(true);
            require(fixtureSettings.snapshot().metadataTranslationEnabled, "explicit title translation on is preserved");
            require(fixtureSettings.snapshot().syncedLyricsKaraokeAnimationEnabled, "explicit virtual karaoke on is preserved");
            fixtureApplication.getSharedPreferences(AiLyricsSettings.PREFS_NAME, MODE_PRIVATE).edit()
                    .remove(AiLyricsSettings.KEY_METADATA_TRANSLATION_ENABLED)
                    .remove(AiLyricsSettings.KEY_SYNCED_LYRICS_KARAOKE_ANIMATION).commit();
            require(!fixtureSettings.snapshot().metadataTranslationEnabled, "reset title translation defaults off");
            require(!fixtureSettings.snapshot().syncedLyricsKaraokeAnimationEnabled, "reset virtual karaoke defaults off");
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
            IntentFilter playbackFilter = new IntentFilter(ACTION_FIXTURE_PLAYBACK);
            // adb sends the measurement broadcast, so the receiver is exported. API 26-32
            // ignore the flag; the three-argument overload exists since API 26.
            registerReceiver(playbackControl, playbackFilter, RECEIVER_EXPORTED);
            playbackControlRegistered = true;

            // Publish before delivering each synthetic track so the shared engine
            // takes its in-memory result path without contacting a lyrics provider.
            primeCache("one");
            primeCache("two");
            seedLyrics(expectedTrack("one"), "one");
            setMetadata("one");
            setState(false, 6000);
            IvLyricsBridge.initialize(fixtureContext);
            IvLyricsBridge.onMediaController(session.getController());
            await("initial MediaSession snapshot", () -> isCurrentTrack("one", 6000), () -> {
                TrackSnapshot first = NowPlayingService.getLatestSnapshot();
                seedLyrics(first, "one");
                inline = IvLyricsBridge.createInlinePreview(fixtureContext);
                inlinePreview = (MainLyricPreviewView) ((ViewGroup) inline).getChildAt(0);
                root.addView(inline, 1, new LinearLayout.LayoutParams(-1, -2));
                card = IvLyricsBridge.createPreview(fixtureContext);
                root.addView(card, 2, new LinearLayout.LayoutParams(-1, -2));
                await("card and inline attachment with shared lyrics", () -> card.getHeight() > 0
                        && card.getWidth() > 0 && matchesFixture(IvLyricsBridge.cachedLyrics(first), "one"),
                        () -> await("initial inline rows", () -> inlineReady("one", 1, 6000L, 2), this::runChecks));
            });
        } catch (Throwable error) { fail(error); }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        intent.putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, true);
        setIntent(intent);
        if ("full".equals(intent.getStringExtra("action"))) IvLyricsBridge.openLyrics(fixtureContext);
        if ("card-click".equals(intent.getStringExtra("action")) && card != null) card.performClick();
        if ("inline-click".equals(intent.getStringExtra("action")) && inline != null) inline.performClick();
        if ("settings".equals(intent.getStringExtra("action")) && card != null) card.performLongClick();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        if (playbackControlRegistered) {
            unregisterReceiver(playbackControl);
            playbackControlRegistered = false;
        }
        if (fixtureSettings != null) fixtureSettings.shutdown();
        super.onDestroy();
    }

    private static void setMetadata(String suffix) {
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, fixtureUri(suffix))
            .putString(MediaMetadata.METADATA_KEY_TITLE, "Integration fixture " + suffix)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "ivLyrics test")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Debug only")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs(suffix)).build());
    }

    private static long durationMs(String suffix) {
        return DENSE.equals(suffix) ? DENSE_DURATION_MS : 24000L;
    }

    private static void setState(boolean play, long position) {
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SEEK_TO)
            .setState(play ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                position, 1f, SystemClock.elapsedRealtime()).build());
    }

    private TrackSnapshot expectedTrack(String suffix) {
        return new TrackSnapshot("Integration fixture " + suffix, "ivLyrics test", "Debug only",
                getPackageName(), fixtureUri(suffix), "", durationMs(suffix), 6000L,
                SystemClock.elapsedRealtime(), 1f, false, null, "");
    }

    private void primeCache(String suffix) {
        TrackSnapshot track = expectedTrack(suffix);
        LyricsDiskCache cache = new LyricsDiskCache(fixtureContext, "base_lyrics", 350);
        cache.put(track.stableKey(), fixtureLyrics(track, suffix));
        require(matchesFixture(cache.get(track.stableKey()), suffix), "synthetic cache ready: " + suffix);
    }

    private static LyricsResult fixtureLyrics(TrackSnapshot track, String suffix) {
        if (DENSE.equals(suffix)) return denseLyrics(track);
        List<LyricsLine> lines = new ArrayList<>();
        String[] text = {"First line for " + suffix, "lead " + suffix,
                "Next line for " + suffix, "Return to this line for " + suffix};
        for (int i = 0; i < text.length; i++) {
            List<LyricsLine.Syllable> syllables = new ArrayList<>();
            for (int j = 0; j < text[i].length(); j++) {
                syllables.add(new LyricsLine.Syllable(text[i].substring(j, j + 1),
                    i * 6000L + j * 300L, i * 6000L + (j + 1) * 300L));
            }
            List<LyricsLine.VocalPart> vocals = Collections.emptyList();
            if (i == 1) {
                vocals = Arrays.asList(
                        new LyricsLine.VocalPart("lead", "lead", "", "vocal", text[i],
                                Collections.singletonList(new LyricsLine.Syllable(text[i], 6000L, 10000L))),
                        new LyricsLine.VocalPart("echo", "background", "", "vocal", "echo " + suffix,
                                Collections.singletonList(new LyricsLine.Syllable("echo " + suffix, 7200L, 11000L))));
            }
            lines.add(new LyricsLine(i * 6000L, (i + 1) * 6000L, text[i], syllables,
                    "", "vocal", vocals).withSupplements("발음 " + suffix + " " + i, translation(suffix, i)));
        }
        return new LyricsResult(lines, "Integration fixture", "Synthetic text; debug only; " + suffix,
                true, track.isrc, track.trackId).withSelection("debug-fixture", "manual");
    }

    /**
     * Deterministic synthetic rows for frame measurements: 26-40 characters per row,
     * one timed syllable per character, supplements on every row, a rapid passage
     * (rows 19-22 every 450 ms) and one pair only 60 ms apart (rows 27-28).
     */
    private static LyricsResult denseLyrics(TrackSnapshot track) {
        String[] words = {"bright", "city", "moving", "tonight", "under", "silver", "rain", "we",
                "keep", "the", "rhythm", "close", "and", "never", "let", "it", "go", "running",
                "through", "neon", "streets"};
        List<LyricsLine> lines = new ArrayList<>();
        long start = 2000L;
        for (int i = 0; i < DENSE_LINES; i++) {
            StringBuilder text = new StringBuilder("Line ").append(i + 1);
            int word = i * 7;
            while (text.length() < 26 + (i * 5) % 15) text.append(' ').append(words[word++ % words.length]);
            String value = text.toString();
            long gap = i >= 18 && i < 22 ? 450L : i == 26 ? 60L : 3200L;
            long available = gap >= 1000L ? gap - 200L : gap - 10L;
            long charMs = Math.max(1L, Math.min(150L, available / value.length()));
            List<LyricsLine.Syllable> syllables = new ArrayList<>();
            for (int j = 0; j < value.length(); j++) {
                syllables.add(new LyricsLine.Syllable(value.substring(j, j + 1),
                        start + j * charMs, start + (j + 1) * charMs));
            }
            lines.add(new LyricsLine(start, start + gap, value, syllables, "", "vocal",
                    Collections.<LyricsLine.VocalPart>emptyList())
                    .withSupplements("발음 " + (i + 1), "번역 줄 " + (i + 1)));
            start += gap;
        }
        return new LyricsResult(lines, "Integration fixture", "Synthetic text; debug only; " + DENSE,
                true, track.isrc, track.trackId).withSelection("debug-fixture", "manual");
    }

    /** Switches the synthetic session to the dense track, paused at the requested position. */
    private void switchToDenseTrack(long position) {
        try {
            seedLyrics(expectedTrack(DENSE), DENSE);
            setMetadata(DENSE);
            setState(false, position);
            await("dense track metadata", () -> isCurrentTrack(DENSE, position), () -> {
                seedLyrics(NowPlayingService.getLatestSnapshot(), DENSE);
                await("dense shared result", () -> matchesFixture(
                        IvLyricsBridge.cachedLyrics(NowPlayingService.getLatestSnapshot()), DENSE),
                        () -> Log.i(TAG, "DENSE_READY " + position));
            });
        } catch (Throwable error) { fail(error); }
    }

    private static boolean matchesFixture(LyricsResult result, String suffix) {
        return result != null && result.lines.size() == (DENSE.equals(suffix) ? DENSE_LINES : 4)
                && "Integration fixture".equals(result.providerLabel)
                && ("Synthetic text; debug only; " + suffix).equals(result.detail);
    }

    private static boolean isCurrentTrack(String suffix, long position) {
        TrackSnapshot current = NowPlayingService.getLatestSnapshot();
        return current != null && fixtureUri(suffix).equals(current.mediaId)
                && ("Integration fixture " + suffix).equals(current.title)
                && !current.playing && current.positionNow() == position;
    }

    private void seedLyrics(TrackSnapshot track, String suffix) {
        if (track == null || !fixtureUri(suffix).equals(track.mediaId)) {
            throw new AssertionError("fixture lyrics require the delivered track: " + suffix);
        }
        IvLyricsBridge.publishLyrics(track, fixtureLyrics(track, suffix), "en");
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
        require(inline.isAttachedToWindow() && inline.getVisibility() == View.VISIBLE
                && inline.getHeight() > 0 && inlinePreview.getWidth() > 0, "inline mounted and measured");
        require((Boolean) field(inlinePreview, "compactLayout")
                && (Boolean) field(inlinePreview, "externallyDriven"), "inline uses compact externally driven renderer");
        require(inlineRows().get(0).syllables.stream().anyMatch(value -> value.startTimeMs == 6000L)
                && inlineRows().get(0).syllables.stream().anyMatch(value -> value.startTimeMs == 7200L),
                "mounted multi-vocal row preserves both timelines");
        require(inlineRows().size() == 2 && translation("one", 1).equals(inlineRows().get(1).text),
                "default original selection also renders enabled available translation");
        require(fixtureSettings.snapshot().previewItems == AiLyricsSettings.PREVIEW_ITEM_ORIGINAL,
                "translation does not mutate preview selection");
        assertAlignment(AiLyricsSettings.LYRICS_ALIGN_LEFT, 10f);
        fixtureSettings.setPreviewItems(AiLyricsSettings.PREVIEW_ITEM_ORIGINAL | AiLyricsSettings.PREVIEW_ITEM_PRONUNCIATION);
        fixtureSettings.setLyricsTextAlignment(AiLyricsSettings.LYRICS_ALIGN_RIGHT);
        await("selected pronunciation and right alignment", () -> inlineReady("one", 1, 6000L, 3)
                && AiLyricsSettings.LYRICS_ALIGN_RIGHT.equals(field(inlinePreview, "lyricTextAlignment")), () -> {
            require("발음 one 1".equals(inlineRows().get(1).text), "selected pronunciation renders between original and translation");
            assertAlignment(AiLyricsSettings.LYRICS_ALIGN_RIGHT, 210f);
            fixtureSettings.setLyricsTextAlignment(AiLyricsSettings.LYRICS_ALIGN_CENTER);
            await("center alignment", () -> AiLyricsSettings.LYRICS_ALIGN_CENTER.equals(field(inlinePreview, "lyricTextAlignment")), () -> {
                assertAlignment(AiLyricsSettings.LYRICS_ALIGN_CENTER, 110f);
                seekAndSwitch();
            });
        });
    }

    private void seekAndSwitch() {
        require(NowPlayingService.seekTo(12000), "seek command accepted by controller");
        await("seek callback and mounted inline timeline", () -> lastSeek == 12000 && isCurrentTrack("one", 12000)
                && inlineReady("one", 2, 12000L, 3), () -> {
            require(lastSeek == 12000, "seek reaches owning MediaSession");
            require(NowPlayingService.getLatestSnapshot().positionNow() == 12000, "seek callback updates timeline");
            require("Next line for one".equals(inlineRows().get(0).text), "seek changes mounted inline row");
            seedLyrics(expectedTrack("two"), "two");
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
                        && matchesFixture(IvLyricsBridge.cachedLyrics(NowPlayingService.getLatestSnapshot()), "two")
                        && inlineReady("two", 1, 7400L, 3), () -> {
                    require("lead two echo two".equals(inlineRows().get(0).text), "track switch replaces actual inline multi-vocal row");
                    require((Long) field(inlinePreview, "lineStartMs") == 6000L,
                            "backward timeline change restores source line start");
                    fixtureSettings.setPreviewItems(AiLyricsSettings.PREVIEW_ITEM_NONE);
                    await("NONE hides inline", () -> inline.getVisibility() == View.GONE, () -> {
                        require(inlineRows().isEmpty(), "hidden inline clears mounted rows");
                        fixtureSettings.setPreviewItems(AiLyricsSettings.PREVIEW_ITEM_ORIGINAL);
                        await("inline returns after NONE", () -> inlineReady("two", 1, 7400L, 2), () -> {
                            open.setEnabled(true);
                            status.setText("PASS: inline, translation, vocals, alignment, seek, track switch");
                            Log.i(TAG, "ALL_CHECKS_PASSED");
                        });
                    });
                });
            });
        });
    }

    private static String fixtureUri(String suffix) {
        // Canonical 22-character IDs satisfy the production song guard. These
        // synthetic zero IDs are never resolved by a network provider.
        return "spotify:track:" + ("one".equals(suffix) ? "0000000000000000000001"
                : DENSE.equals(suffix) ? "0000000000000000000003" : "0000000000000000000002");
    }

    private static String translation(String suffix, int line) { return "번역 " + suffix + " " + line; }

    @SuppressWarnings("unchecked")
    private List<MainLyricPreviewView.PreviewLine> inlineRows() {
        return (List<MainLyricPreviewView.PreviewLine>) field(inlinePreview, "lines");
    }

    private boolean inlineReady(String suffix, int line, long position, int rowCount) {
        if (inline == null || inline.getVisibility() != View.VISIBLE || inline.getHeight() == 0) return false;
        List<MainLyricPreviewView.PreviewLine> rows = inlineRows();
        return rows.size() == rowCount && translation(suffix, line).equals(rows.get(rowCount - 1).text)
                && (Long) field(inlinePreview, "basePositionMs") == position;
    }

    private void assertAlignment(String expected, float shortTextStart) {
        require(expected.equals(field(inlinePreview, "lyricTextAlignment")), "mounted preview follows " + expected + " setting");
        require(Math.abs(textX(100f, 300f, 10f, 0.5f) - shortTextStart) < 0.01f,
                expected + " short row drawing origin");
        float start = textX(500f, 300f, 10f, 0f);
        float end = textX(500f, 300f, 10f, 1f);
        require(start > end && Math.abs(textX(500f, 300f, 10f, 0.2f) - start) < 0.01f
                && Math.abs(textX(500f, 300f, 10f, 0.5f) - (start + end) / 2f) < 0.01f,
                expected + " overflow retains time-driven start hold and horizontal motion");
    }

    private float textX(float textWidth, float width, float left, float progress) {
        try {
            Method method = MainLyricPreviewView.class.getDeclaredMethod("xForText", float.class, float.class, float.class, float.class);
            method.setAccessible(true);
            return (Float) method.invoke(inlinePreview, textWidth, width, left, progress);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private static Object field(Object instance, String name) {
        try {
            Field field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    /** Retain Android services/resources but keep synthetic state in its own storage. */
    private static final class FixtureContext extends ContextWrapper {
        private final Context application;
        FixtureContext(Context base, Context application) { super(base); this.application = application == null ? this : application; }
        @Override public Context getApplicationContext() { return application; }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return super.getSharedPreferences("embedded_smoke_" + name, mode);
        }
        @Override public File getFilesDir() { return directory(super.getFilesDir()); }
        @Override public File getCacheDir() { return directory(super.getCacheDir()); }
        private File directory(File parent) {
            File directory = new File(parent, "embedded_smoke");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create fixture storage");
            return directory;
        }
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
