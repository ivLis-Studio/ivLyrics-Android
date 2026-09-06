package kr.ivlis.ivlyricsandroid;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.media.session.MediaSession;
import android.media.session.MediaController;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Shared entry points for in-process player cards, playback, and the full lyrics page. */
public final class IvLyricsBridge {
    public static final String EXTRA_EMBEDDED_MODE = "kr.ivlis.ivlyricsandroid.EMBEDDED_MODE";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String STANDALONE_PACKAGE = "kr.ivlis.ivlyricsandroid";
    private static Context application;
    private static Engine engine;
    private static boolean wordSegmenterInitialized;
    private static volatile IvLyricsHost host;
    private static TrackSnapshot sharedTrack;
    private static LyricsResult sharedResult;
    private static String sharedSourceLanguage = "auto";
    private static WeakReference<BaseLyricsActivity> foregroundPage = new WeakReference<>(null);
    private static final List<WeakReference<NativeCard>> CARDS = new ArrayList<>();
    private static final Runnable RELEASE = IvLyricsBridge::releaseIdleEngine;

    private static void releaseIdleEngine() {
        pruneCards();
        if (CARDS.isEmpty() && engine != null) {
            if (engine.requestInFlight && foregroundPage.get() != null) {
                MAIN.postDelayed(RELEASE, 5000L);
                return;
            }
            engine.release();
            engine = null;
        }
    }

    private IvLyricsBridge() {}

    /** Register an embedding adapter before initializing playback or creating a lyrics view. */
    public static void setHost(IvLyricsHost embeddingHost) {
        if (embeddingHost == null) throw new IllegalArgumentException("Missing ivLyrics host");
        host = embeddingHost;
    }

    private static Context hostContext(Context context) {
        IvLyricsHost currentHost = host;
        return currentHost == null ? context : currentHost.wrapContext(context);
    }

    static Intent lyricsIntent(Context context) {
        IvLyricsHost currentHost = host;
        return currentHost == null
                ? new Intent().setClassName(context.getPackageName(), STANDALONE_PACKAGE + ".MainActivity")
                : currentHost.createLyricsIntent(context);
    }

    public static boolean isEmbedded(Context context) {
        if (context == null) return false;
        // Embedding is an explicit runtime choice, independent of build namespace
        // or a standalone QA application's install package.
        if (host != null) return true;
        return context instanceof Activity
                && ((((Activity) context).getApplicationInfo().flags
                    & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                && ((Activity) context).getIntent() != null
                && ((Activity) context).getIntent().getBooleanExtra(EXTRA_EMBEDDED_MODE, false);
    }

    public static void initialize(Context context) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        application = hostContext(app == null ? context : app);
        if (!wordSegmenterInitialized) {
            LyricsWordSegmenter.initialize(application);
            wordSegmenterInitialized = true;
        }
    }

    public static View createPreview(Context context) {
        initialize(context);
        return new NativeCard(hostContext(context));
    }

    public static void openLyrics(Context context) {
        if (context == null) return;
        initialize(context);
        Intent intent = lyricsIntent(context);
        intent.putExtra(EXTRA_EMBEDDED_MODE, true);
        intent.putExtra(BaseLyricsActivity.EXTRA_OPEN_LYRICS_PAGE, true);
        Context launchContext = context;
        while (launchContext instanceof ContextWrapper && !(launchContext instanceof Activity)) {
            Context base = ((ContextWrapper) launchContext).getBaseContext();
            if (base == null || base == launchContext) break;
            launchContext = base;
        }
        if (!(launchContext instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        launchContext.startActivity(intent);
    }

    public static void onMediaSession(MediaSession session) {
        if (session != null) onMediaController(session.getController());
    }

    public static void onMediaController(MediaController controller) {
        NowPlayingService.attachEmbeddedController(controller);
    }

    public static void onPlayerState(String uri, String title, String artist, String album,
            long durationMs, long positionMs, boolean playing, float speed, String artworkUri) {
        onPlayerState(uri, title, artist, album, durationMs, positionMs, playing, speed, artworkUri, "");
    }

    public static void onPlayerState(String uri, String title, String artist, String album,
            long durationMs, long positionMs, boolean playing, float speed, String artworkUri, String isrc) {
        TrackSnapshot previous = NowPlayingService.getLatestSnapshot();
        boolean sameTrack = previous != null && previous.mediaId.equals(uri == null ? "" : uri);
        TrackSnapshot snapshot = new TrackSnapshot(title, artist, album,
                application == null ? "com.spotify.music" : application.getPackageName(), uri,
                isrc == null || isrc.isEmpty() ? (sameTrack ? previous.isrc : "") : isrc,
                durationMs, positionMs, SystemClock.elapsedRealtime(), speed, playing,
                sameTrack ? previous.artwork : null, artworkUri,
                sameTrack && previous.spotifyAutomix,
                sameTrack ? previous.automixFadeInStartMs : 0,
                sameTrack ? previous.automixFadeInCueMs : 0,
                sameTrack ? previous.automixFadeOverlapMs : 0);
        NowPlayingService.publishEmbeddedSnapshot(snapshot);
    }

    /** Shares manual selection and generated supplements back to the player card. */
    static void publishLyrics(TrackSnapshot track, LyricsResult result) {
        publishLyrics(track, result, "auto");
    }

    static void publishLyrics(TrackSnapshot track, LyricsResult result, String sourceLanguage) {
        if (track == null || result == null || result.lines.isEmpty()) return;
        sharedTrack = track;
        sharedResult = result;
        sharedSourceLanguage = sourceLanguage == null ? "auto" : sourceLanguage;
        if (engine != null) engine.acceptShared(track, result, sharedSourceLanguage);
    }

    static LyricsResult cachedLyrics(TrackSnapshot track) {
        if (track == null) return null;
        if (engine != null && track.stableKey().equals(engine.key)
                && metadataMatches(track, engine.result)) return engine.result;
        return sharedTrack != null && track.stableKey().equals(sharedTrack.stableKey())
                && metadataMatches(track, sharedResult) ? sharedResult : null;
    }

    private static boolean metadataMatches(TrackSnapshot track, LyricsResult result) {
        return result != null && !result.lines.isEmpty() && (track.isrc.isEmpty() || track.isrc.equals(result.isrc));
    }

    static void setLyricsPageForeground(BaseLyricsActivity activity, boolean visible) {
        if (visible) {
            foregroundPage = new WeakReference<>(activity);
        } else if (foregroundPage.get() == activity) {
            foregroundPage.clear();
            if (engine != null && !engine.requestInFlight
                    && (engine.result == null || engine.result.lines.isEmpty())) {
                engine.key = "";
                engine.onNowPlayingChanged(NowPlayingService.getLatestSnapshot());
            }
        }
    }

    static boolean awaitCardLyrics(TrackSnapshot track) {
        return engine != null && track != null && engine.requestInFlight && engine.key.equals(track.stableKey())
                && engine.track != null && track.isrc.equals(engine.track.isrc);
    }

    private static void pruneCards() {
        CARDS.removeIf(reference -> reference.get() == null || !reference.get().isAttachedToWindow());
    }

    private static void attach(NativeCard card) {
        MAIN.removeCallbacks(RELEASE);
        pruneCards();
        CARDS.add(new WeakReference<>(card));
        if (engine == null) engine = new Engine(application);
        engine.renderCards();
    }

    private static void detach(NativeCard card) {
        CARDS.removeIf(reference -> reference.get() == null || reference.get() == card);
        MAIN.removeCallbacks(RELEASE);
        MAIN.postDelayed(RELEASE, 5000L);
    }

    /** The card uses the same original Canvas renderer as the full lyrics page. */
    private static final class NativeCard extends FrameLayout {
        final PlayerBackgroundView background;
        final LyricsView lyrics;
        final TextView title;
        AiLyricsSettings.Snapshot appliedSettings;
        LyricsResult appliedResult;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (!isAttachedToWindow()) return;
                if (isShown() && getWindowVisibility() == View.VISIBLE && engine != null) {
                    lyrics.setPlaybackPosition(engine.position());
                }
                postDelayed(this, isShown() ? 32L : 250L);
            }
        };

        NativeCard(Context context) {
            super(context);
            setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            setMinimumHeight(dp(244));
            setClickable(true);
            setFocusable(true);
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
                }
            });
            background = new PlayerBackgroundView(context);
            addView(background, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(8), dp(14), dp(8), dp(14));
            addView(content, new FrameLayout.LayoutParams(-1, -1));
            title = text("ivLyrics", 13.5f);
            title.setTypeface(AppFonts.bold(context));
            title.setPadding(dp(8), 0, dp(8), 0);
            content.addView(title, new LinearLayout.LayoutParams(-1, dp(24)));
            lyrics = new LyricsView(context);
            lyrics.setSidePaddingSp(9f);
            lyrics.setTypographySizeMultiplier(0.78f);
            lyrics.setVerticalCenterBias(0.42f);
            content.addView(lyrics, new LinearLayout.LayoutParams(-1, 0, 1f));
            setOnClickListener(view -> openLyrics(getContext()));
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) { return true; }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attach(this);
            removeCallbacks(tick);
            post(tick);
        }
        @Override protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            detach(this);
            super.onDetachedFromWindow();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            // Spotify supplies the available card width; keep the preview square at every density.
            int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                    ? dp(320) : MeasureSpec.getSize(widthSpec);
            // A zero-height intrinsic probe must not also collapse the width.
            int height = resolveSize(width, heightSpec);
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        void render(Engine state) {
            AiLyricsSettings.Snapshot settings = state.settings.snapshot();
            if (appliedSettings != settings) {
                appliedSettings = settings;
                lyrics.setTypographySettings(settings.typography);
                lyrics.setLyricTextAlignment(settings.lyricsTextAlignment);
                lyrics.setSpeakerColorSettings(settings.speakerColors);
                lyrics.setUseCreatorSpeakerColors(settings.useSyncCreatorSpeakerColors);
                lyrics.setKaraokeDisplayGranularity(settings.karaokeDisplayGranularity);
                lyrics.setKaraokeBounceEffectEnabled(settings.karaokeBounceEffectEnabled);
                lyrics.setSyncedLyricsKaraokeAnimationEnabled(settings.syncedLyricsKaraokeAnimationEnabled);
                lyrics.setAutoInstrumentalBreakEnabled(settings.autoInstrumentalBreakEnabled);
                lyrics.setInterludeLabelsEnabled(settings.interludeLabelsEnabled);
                lyrics.setJapaneseFuriganaEnabled(settings.japaneseFuriganaEnabled);
                lyrics.setUiText(state.ui("status.lyrics_loading"), state.ui("lyrics.empty_none"),
                        state.ui("interlude.prelude"), state.ui("interlude.break"), state.ui("interlude.postlude"));
                background.setBackgroundSettings(settings.background);
                setContentDescription("ivLyrics, " + ("ko".equals(settings.uiLang) ? "전체 가사 열기" : "Open lyrics"));
            }
            if (appliedResult != state.result) {
                appliedResult = state.result;
                lyrics.setResult(state.result);
            }
            lyrics.setLyricsSegmentationLocale(state.sourceLanguage);
            lyrics.setLoadingState(state.loading);
            lyrics.setSupplementLoading(state.pronunciationLoading, state.translationLoading);
            lyrics.setTrackDuration(state.track == null ? 0 : state.track.durationMs);
            lyrics.setCulturalAnnotations(state.annotations, settings.culturalAnnotationsFontFamily,
                    settings.culturalAnnotationsFontSize, settings.culturalAnnotationsFontWeight,
                    settings.culturalAnnotationsOpacity);
            if (state.track != null) background.setArtwork(state.track.artwork, state.track.artworkKey());
            lyrics.setPlaybackPosition(state.position());
        }
        TextView text(String value, float size) {
            TextView view = new TextView(getContext());
            view.setText(value);
            view.setTextSize(size);
            view.setTextColor(Color.WHITE);
            view.setTypeface(AppFonts.regular(getContext()));
            return view;
        }
        int dp(int value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    }

    private static final class Engine implements NowPlayingService.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
        final AiLyricsSettings settings;
        final LyricsRepository repository;
        final AiLyricsRepository ai;
        final FuriganaRepository furigana;
        final SharedPreferences uiPreferences;
        final SharedPreferences providerPreferences;
        final AudioManager audioManager;
        final AudioDeviceCallback audioCallback = new AudioDeviceCallback() {
            @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { updateBluetoothOffset(); }
            @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { updateBluetoothOffset(); }
        };
        final SpotifyDjLyricsTimeline timeline = new SpotifyDjLyricsTimeline();
        TrackSnapshot track;
        LyricsResult base;
        LyricsResult result;
        LyricsResult furiganaResult;
        List<CulturalAnnotation> annotations = Collections.emptyList();
        String sourceLanguage = "auto";
        String key = "";
        long generation;
        long djOffset;
        int bluetoothOffset;
        boolean loading;
        boolean requestInFlight;
        boolean pronunciationLoading;
        boolean translationLoading;
        boolean released;
        boolean settingsDataReloadPending;
        final Runnable reloadSettings = this::reloadSettings;

        private void reloadSettings() {
            if (released) return;
            updateBluetoothOffset();
            if (!settingsDataReloadPending) {
                renderCards();
                return;
            }
            settingsDataReloadPending = false;
            if (foregroundPage.get() != null) {
                renderCards();
                return;
            }
            repository.invalidateProviderSelection();
            sharedTrack = null;
            sharedResult = null;
            key = "";
            onNowPlayingChanged(NowPlayingService.getLatestSnapshot());
        }

        Engine(Context context) {
            settings = new AiLyricsSettings(context);
            repository = new LyricsRepository(context);
            ai = new AiLyricsRepository(context);
            furigana = new FuriganaRepository(context);
            uiPreferences = context.getSharedPreferences(AiLyricsSettings.PREFS_NAME, Context.MODE_PRIVATE);
            providerPreferences = context.getSharedPreferences("lyrics_provider_settings", Context.MODE_PRIVATE);
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) audioManager.registerAudioDeviceCallback(audioCallback, MAIN);
            updateBluetoothOffset();
            uiPreferences.registerOnSharedPreferenceChangeListener(this);
            providerPreferences.registerOnSharedPreferenceChangeListener(this);
            result = LyricsResult.empty(ui("status.waiting_current_track"));
            NowPlayingService.register(this);
            onNowPlayingChanged(NowPlayingService.getLatestSnapshot());
        }

        @Override public void onSharedPreferenceChanged(SharedPreferences preferences, String changedKey) {
            settingsDataReloadPending |= preferences == providerPreferences || changesLyricsData(changedKey);
            MAIN.removeCallbacks(reloadSettings);
            MAIN.postDelayed(reloadSettings, 200L);
        }

        boolean changesLyricsData(String preference) {
            if (preference == null) return true;
            switch (preference) {
                case AiLyricsSettings.KEY_TRANSLATION_ENABLED:
                case AiLyricsSettings.KEY_PRONUNCIATION_ENABLED:
                case AiLyricsSettings.KEY_BING_TRANSLATE_ENABLED:
                case AiLyricsSettings.KEY_GOOGLE_TRANSLATE_ENABLED:
                case AiLyricsSettings.KEY_AI_PROVIDER_ORDER:
                case AiLyricsSettings.KEY_AI_PROVIDER_ENABLED:
                case AiLyricsSettings.KEY_AI_PROVIDER_PROFILES:
                case AiLyricsSettings.KEY_PROVIDER:
                case AiLyricsSettings.KEY_TARGET_LANG:
                case AiLyricsSettings.KEY_UI_LANG:
                case AiLyricsSettings.KEY_OUTPUT_LANG:
                case AiLyricsSettings.KEY_PRONUNCIATION_LANG:
                case AiLyricsSettings.KEY_PRONUNCIATION_NOTATION:
                case AiLyricsSettings.KEY_LANGUAGE_RULES:
                case AiLyricsSettings.KEY_API_KEYS:
                case AiLyricsSettings.KEY_POLLINATIONS_ACCESS_TOKEN:
                case AiLyricsSettings.KEY_MODEL:
                case AiLyricsSettings.KEY_BASE_URL:
                case AiLyricsSettings.KEY_MAX_TOKENS:
                case AiLyricsSettings.KEY_TEMPERATURE:
                case AiLyricsSettings.KEY_SPOTIFY_CLIENT_ID:
                case AiLyricsSettings.KEY_SPOTIFY_CLIENT_SECRET:
                case AiLyricsSettings.KEY_JAPANESE_FURIGANA_ENABLED:
                case AiLyricsSettings.KEY_CULTURAL_ANNOTATIONS_ENABLED:
                    return true;
                default:
                    return false;
            }
        }

        @Override public void onNowPlayingChanged(TrackSnapshot next) {
            if (released) return;
            boolean enriched = track != null && next != null && key.equals(next.stableKey())
                    && !next.isrc.isEmpty() && !next.isrc.equals(track.isrc);
            track = next;
            if (next == null || !next.hasUsableMetadata()) {
                if (!key.isEmpty()) {
                    generation++;
                    key = "";
                    base = null;
                    result = LyricsResult.empty(ui("status.waiting_current_track"));
                    loading = false;
                    requestInFlight = false;
                    renderCards();
                }
                return;
            }
            long nowPosition = next.positionNow();
            djOffset = Math.max(0, timeline.update(next.stableKey(), nowPosition, next.playing,
                    next.spotifyAutomix, next.isSpotifyDjSegment(), next.automixFadeInStartMs,
                    next.automixFadeInCueMs, next.automixFadeOverlapMs, SystemClock.elapsedRealtime()) - nowPosition);
            if (!key.equals(next.stableKey()) || enriched) {
                key = next.stableKey();
                long request = ++generation;
                if (!enriched) {
                    base = null;
                    furiganaResult = null;
                    annotations = Collections.emptyList();
                    result = LyricsResult.empty(ui("status.lyrics_loading"));
                }
                loading = result == null || result.lines.isEmpty();
                requestInFlight = false;
                pronunciationLoading = translationLoading = false;
                if (!enriched && sharedTrack != null && key.equals(sharedTrack.stableKey()) && metadataMatches(next, sharedResult)) {
                    base = result = sharedResult;
                    loading = false;
                    sourceLanguage = "auto".equals(sharedSourceLanguage) ? detectLanguage(result) : sharedSourceLanguage;
                    renderCards();
                    return;
                }
                if (foregroundPage.get() != null) {
                    renderCards();
                    return;
                }
                requestInFlight = true;
                repository.loadLyrics(next, new LyricsRepository.Callback() {
                    boolean current(String callbackKey) { return valid(request, callbackKey); }
                    @Override public void onLyricsLoaded(String callbackKey, LyricsResult loaded) {
                        if (!current(callbackKey)) return;
                        loading = false;
                        requestInFlight = false;
                        base = result = loaded;
                        furiganaResult = null;
                        sourceLanguage = detectLanguage(loaded);
                        renderCards();
                        BaseLyricsActivity page = foregroundPage.get();
                        if (page != null && page.acceptEmbeddedLyrics(callbackKey, loaded)) return;
                        loadSupplements(request);
                    }
                    @Override public void onLyricsError(String callbackKey, String message) {
                        if (!current(callbackKey)) return;
                        loading = false;
                        requestInFlight = false;
                        if (result == null || result.lines.isEmpty()) result = LyricsResult.empty(ui("status.lyrics_request_failed"));
                        renderCards();
                        BaseLyricsActivity page = foregroundPage.get();
                        if (page != null) {
                            if (result != null && !result.lines.isEmpty()) page.acceptEmbeddedLyrics(callbackKey, result);
                            else page.onLyricsError(callbackKey, message);
                        }
                    }
                    @Override public void onLyricsProviderLoading(String callbackKey, String provider) {}
                    @Override public void onLyricsLog(String callbackKey, String message) {}
                    @Override public void onLyricsArtworkLoaded(String callbackKey, Bitmap artwork, String artworkKey) {}
                    @Override public void onLyricsMetadataResolved(String callbackKey, String isrc, String spotifyTrackId) {}
                });
            }
            renderCards();
        }

        void acceptShared(TrackSnapshot shared, LyricsResult value, String language) {
            if (released || !key.equals(shared.stableKey())) return;
            generation++;
            base = result = value;
            sourceLanguage = "auto".equals(language) ? detectLanguage(value) : language;
            loading = pronunciationLoading = translationLoading = false;
            requestInFlight = false;
            renderCards();
        }

        void loadSupplements(long request) {
            if (base == null || base.lines.isEmpty() || track == null) return;
            AiLyricsSettings.Snapshot snapshot = settings.snapshot();
            AiLyricsSettings.LanguageRule rule = snapshot.ruleForSource(sourceLanguage);
            boolean ready = snapshot.hasApiKey() && snapshot.hasModel();
            pronunciationLoading = rule.pronunciationEnabled && ready;
            translationLoading = rule.translationEnabled
                    && !snapshot.shouldSkipTranslation(sourceLanguage, snapshot.resolveTargetLanguage(sourceLanguage))
                    && (ready || snapshot.hasKeylessTranslationProvider());
            LyricsResult requestBase = base;
            if (snapshot.enabled() && (pronunciationLoading || translationLoading)) {
                ai.loadSupplements(track, base, snapshot, sourceLanguage, false, new AiCallback(request, requestBase));
            }
            if (snapshot.culturalAnnotationsEnabled) {
                ai.loadCulturalAnnotations(track, base, snapshot, sourceLanguage, false, new AiCallback(request, requestBase));
            }
            if (snapshot.japaneseFuriganaEnabled && "ja".equals(sourceLanguage)) {
                furigana.loadFurigana(track, base, false, new FuriganaRepository.Callback() {
                    @Override public void onFuriganaLoaded(String callbackKey, LyricsResult value) {
                        if (!valid(request, callbackKey) || requestBase != base) return;
                        furiganaResult = value;
                        result = withFurigana(result, value);
                        renderCards();
                    }
                    @Override public void onFuriganaError(String callbackKey, String message) {}
                    @Override public void onFuriganaLog(String callbackKey, String message) {}
                });
            }
            renderCards();
        }

        boolean valid(long request, String callbackKey) { return !released && generation == request && key.equals(callbackKey); }
        long position() {
            if (track == null) return 0;
            long value = track.positionNow() + djOffset + settings.globalSyncOffsetMs() + settings.trackSyncOffsetMs(key) + bluetoothOffset;
            return Math.max(0, track.durationMs > 0 ? Math.min(track.durationMs + djOffset, value) : value);
        }
        String ui(String message) { return AppI18n.t(settings.snapshot().uiLang, message); }
        void renderCards() {
            if (released) return;
            if (track != null && result != null && !result.lines.isEmpty()) {
                sharedTrack = track;
                sharedResult = result;
                sharedSourceLanguage = sourceLanguage;
            }
            pruneCards();
            for (WeakReference<NativeCard> reference : CARDS) {
                NativeCard card = reference.get();
                if (card != null) card.render(this);
            }
        }
        void release() {
            released = true;
            generation++;
            MAIN.removeCallbacks(reloadSettings);
            NowPlayingService.unregister(this);
            uiPreferences.unregisterOnSharedPreferenceChangeListener(this);
            providerPreferences.unregisterOnSharedPreferenceChangeListener(this);
            if (audioManager != null) audioManager.unregisterAudioDeviceCallback(audioCallback);
            repository.shutdown();
            ai.shutdown();
            furigana.shutdown();
            settings.shutdown();
        }

        String detectLanguage(LyricsResult value) {
            StringBuilder text = new StringBuilder();
            for (LyricsLine line : value.lines) text.append(line.text).append('\n');
            return LyricsLanguageDetector.detect(text.toString());
        }

        void updateBluetoothOffset() {
            bluetoothOffset = 0;
            if (audioManager == null || released) return;
            AudioDeviceInfo selected = null;
            try {
                for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if (device == null || !device.isSink()) continue;
                    int type = device.getType();
                    boolean a2dp = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP;
                    boolean ble = Build.VERSION.SDK_INT >= 31 && (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER);
                    boolean hearingAid = Build.VERSION.SDK_INT >= 28 && type == AudioDeviceInfo.TYPE_HEARING_AID;
                    if (!a2dp && !ble && !hearingAid && type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) continue;
                    if (selected == null) selected = device;
                    if (a2dp || ble) { selected = device; break; }
                }
            } catch (RuntimeException ignored) { return; }
            if (selected == null) return;
            String name = selected.getProductName() == null ? "" : selected.getProductName().toString().trim();
            int type = selected.getType();
            if (name.isEmpty()) {
                if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) name = "Bluetooth A2DP";
                else if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) name = "Bluetooth SCO";
                else if (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET) name = "BLE headset";
                else if (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_SPEAKER) name = "BLE speaker";
                else name = "Hearing aid";
            }
            String deviceKey = "type:" + type + "|name:" + name.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            bluetoothOffset = settings.bluetoothSyncOffsetMs(deviceKey);
        }

        private final class AiCallback implements AiLyricsRepository.Callback {
            final long request;
            final LyricsResult requestBase;
            AiCallback(long request, LyricsResult requestBase) { this.request = request; this.requestBase = requestBase; }
            boolean current(String callbackKey) { return valid(request, callbackKey) && requestBase == base; }
            @Override public void onAiLyricsLoaded(String callbackKey, LyricsResult value) {
                if (!current(callbackKey)) return;
                result = withFurigana(value, furiganaResult);
                pronunciationLoading = translationLoading = false;
                renderCards();
            }
            @Override public void onAiLyricsPartialLoaded(String callbackKey, LyricsResult value,
                    boolean pronunciation, boolean translation, boolean finished, boolean hadError) {
                if (!current(callbackKey)) return;
                result = withFurigana(value, furiganaResult);
                pronunciationLoading = pronunciation;
                translationLoading = translation;
                renderCards();
            }
            @Override public void onAiLyricsError(String callbackKey, String message) {
                if (!current(callbackKey)) return;
                pronunciationLoading = translationLoading = false;
                renderCards();
            }
            @Override public void onAiLyricsTaskError(String callbackKey, String message,
                    boolean pronunciation, boolean translation, boolean finished) {
                if (!current(callbackKey)) return;
                pronunciationLoading = pronunciation;
                translationLoading = translation;
                renderCards();
            }
            @Override public void onAiCulturalAnnotationsLoaded(String callbackKey, String requestKey, List<CulturalAnnotation> value) {
                if (!current(callbackKey)) return;
                annotations = value == null ? Collections.emptyList() : value;
                renderCards();
            }
            @Override public void onAiLyricsLog(String key, String message) {}
            @Override public void onAiMetadataTranslationLoaded(String key, AiLyricsRepository.MetadataTranslation value) {}
            @Override public void onAiMetadataTranslationError(String key, String message) {}
            @Override public void onAiTmiLoaded(String key, AiLyricsRepository.TmiInfo value) {}
            @Override public void onAiTmiError(String key, String message) {}
            @Override public void onAiCulturalAnnotationsError(String key, String requestKey, String message) {}
        }
    }

    private static LyricsResult withFurigana(LyricsResult target, LyricsResult ruby) {
        if (target == null || ruby == null || target.lines.isEmpty()) return target;
        List<LyricsLine> lines = new ArrayList<>();
        for (int i = 0; i < target.lines.size(); i++) {
            LyricsLine line = target.lines.get(i);
            LyricsLine rubyLine = i < ruby.lines.size() ? ruby.lines.get(i) : null;
            List<LyricsLine.VocalPart> parts = new ArrayList<>();
            for (int j = 0; j < line.vocalParts.size(); j++) {
                LyricsLine.VocalPart part = line.vocalParts.get(j);
                LyricsLine.VocalPart rubyPart = rubyLine != null && j < rubyLine.vocalParts.size() ? rubyLine.vocalParts.get(j) : null;
                parts.add(part.withSupplements(part.pronunciationText, part.translationText,
                        rubyPart == null ? part.furiganaText : rubyPart.furiganaText));
            }
            lines.add(new LyricsLine(line.startTimeMs, line.endTimeMs, line.text, line.syllables,
                    line.speaker, line.speakerColor, line.speakerFallback, line.kind, parts,
                    line.pronunciationText, line.translationText, rubyLine == null ? line.furiganaText : rubyLine.furiganaText));
        }
        return new LyricsResult(lines, target.providerLabel, target.detail, target.karaoke, target.isrc,
                target.spotifyTrackId, target.contributors, target.providerId, target.selectionPolicyKey,
                target.syncType, target.syncPoints, target.pronunciationProviderLabel, target.translationProviderLabel);
    }
}
