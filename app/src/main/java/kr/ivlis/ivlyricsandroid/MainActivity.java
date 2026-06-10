package kr.ivlis.ivlyricsandroid;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityManager;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity implements
        NowPlayingService.Listener,
        LyricsRepository.Callback,
        LyricsRepository.ManualLrclibCallback,
        AiLyricsRepository.Callback,
        FuriganaRepository.Callback,
        YouTubeBackgroundRepository.Callback {
    static final String EXTRA_OPEN_LYRICS_PAGE = "kr.ivlis.ivlyricsandroid.OPEN_LYRICS_PAGE";
    private static final String ACTION_UPDATE_INSTALL_RESULT = "kr.ivlis.ivlyricsandroid.UPDATE_INSTALL_RESULT";
    private static final int MAX_LOG_LINES = 180;
    private static final long PREVIEW_INTERLUDE_MIN_DURATION_MS = 500L;
    private static final long PREVIEW_TRAILING_INTERLUDE_DELAY_MS = 3_500L;
    private static final String SETTINGS_TAB_LYRICS = "lyrics";
    private static final String SETTINGS_TAB_DISPLAY = "display";
    private static final String SETTINGS_TAB_AI = "ai";
    private static final String SETTINGS_TAB_TOOLS = "tools";
    private static final String LYRICS_POPUP_TAB_LANGUAGE = "language";
    private static final String LYRICS_POPUP_TAB_SYNC = "sync";
    private static final String LYRICS_POPUP_TAB_VIDEO = "video";
    private static final String LYRICS_POPUP_TAB_LRCLIB = "lrclib";
    private static final String CREATOR_PROFILE_ENDPOINT = "https://lyrics.api.ivl.is/user/creator-profile";
    private static final String SYNC_DATA_SPOTIFY_ORIGIN = "https://xpui.app.spotify.com";
    private static final String SYNC_DATA_SPOTIFY_REFERER = "https://xpui.app.spotify.com/";
    private static final String UI_HINTS_PREFS = "ui_hints";
    private static final String UPDATE_PREFS = "app_updates";
    private static final String KEY_LYRICS_META_MENU_TIP_SHOWN = "lyrics_meta_menu_tip_shown";
    private static final String KEY_LAST_AUTO_UPDATE_CHECK_MS = "last_auto_update_check_ms";
    private static final long AUTO_UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final int ONBOARDING_STEP_COUNT = 3;
    private static final int LYRICS_PAGE_TOP_PADDING_EXPANDED_DP = 46;
    private static final int LYRICS_PAGE_TOP_PADDING_COMPACT_DP = 22;
    private static final int LYRICS_PAGE_TOP_PADDING_SHRINK_DISTANCE_DP = 120;
    private static final String[] ONBOARDING_WELCOME_MESSAGES = {
            "ivLyrics에 오신 것을 환영합니다",
            "Welcome to ivLyrics",
            "ivLyricsへようこそ",
            "欢迎使用 ivLyrics",
            "Bienvenue dans ivLyrics",
            "Bienvenido a ivLyrics"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable onboardingWelcomeTicker = new Runnable() {
        @Override
        public void run() {
            updateOnboardingWelcomeText(true);
            handler.postDelayed(this, 1850L);
        }
    };
    private final List<String> logLines = new ArrayList<>();
    private final Map<String, String> creatorProfileUrlCache = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, TextView> speakerColorValueViews = new LinkedHashMap<>();
    private final Map<String, View> speakerColorSwatches = new LinkedHashMap<>();
    private final ExecutorService seekExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private LyricsRepository lyricsRepository;
    private AiLyricsRepository aiLyricsRepository;
    private FuriganaRepository furiganaRepository;
    private YouTubeBackgroundRepository youtubeBackgroundRepository;
    private UpdateChecker updateChecker;
    private AiLyricsSettings aiLyricsSettings;

    private LyricsView lyricsView;
    private LyricsView landscapeLyricsView;
    private PlayerProgressView playerProgressView;
    private PlayerBackgroundView backgroundView;
    private PlayerBackgroundView lyricsBackgroundView;
    private YouTubeBackgroundView youtubeBackgroundView;
    private FrameLayout rootView;
    private FrameLayout mainPage;
    private FrameLayout lyricsPage;
    private FrameLayout inAppBrowserPage;
    private FrameLayout inAppBrowserSheet;
    private FrameLayout inAppBrowserLoadingView;
    private FrameLayout inAppBrowserHandleTouchTarget;
    private View inAppBrowserHandleView;
    private FrameLayout settingsPanel;
    private FrameLayout spotifySetupPanel;
    private ScrollView spotifySetupScrollView;
    private ImageView artworkView;
    private ImageView lyricsArtworkView;
    private TextView titleView;
    private TextView artistView;
    private TextView lyricsTitleView;
    private TextView lyricsArtistView;
    private TextView lyricsContributorView;
    private WebView inAppBrowserWebView;
    private MainLyricPreviewView lyricPreviewView;
    private TextView sourceView;
    private TextView statusView;
    private TextView debugProgressView;
    private TextView elapsedView;
    private TextView remainingView;
    private TextView overlayPermissionButton;
    private TextView spotifyDetectionPermissionButton;
    private TextView logView;
    private TextView aiSettingsStatusView;
    private TextView providerSummaryView;
    private TextView selectedLanguageRuleView;
    private TextView lyricsSyncOffsetValueView;
    private TextView lyricsSyncOffsetDescriptionView;
    private TextView videoSyncOffsetValueView;
    private TextView videoSyncOffsetDescriptionView;
    private TextView lyricsLanguageButton;
    private TextView permissionButton;
    private PopupWindow lyricsMetaTipPopup;
    private TransportButtonView playPauseButton;
    private View landscapeControlsContainer;
    private ImageButton landscapeMenuButton;
    private LinearLayout debugPanel;
    private LinearLayout lyricsLanguageSettingsPanel;
    private LinearLayout lyricsPopupTabButtonsContainer;
    private LinearLayout lyricsLanguageSettingsContent;
    private LinearLayout lyricsSyncSettingsContent;
    private LinearLayout videoSyncSettingsContent;
    private LinearLayout lyricsManualSearchContent;
    private LinearLayout lyricsManualSearchResultsContainer;
    private LinearLayout lyricsSupplementLoadingIndicator;
    private LinearLayout landscapeLyricsSupplementLoadingIndicator;
    private LinearLayout lyricsPageContent;
    private LinearLayout lyricPreviewContainer;
    private LinearLayout landscapeHeroContainer;
    private LinearLayout landscapeMetaContainer;
    private LinearLayout settingsTabButtonsContainer;
    private LinearLayout settingsLyricsPage;
    private LinearLayout settingsDisplayPage;
    private LinearLayout settingsAiPage;
    private LinearLayout settingsToolsPage;
    private LinearLayout previewModeButtonsContainer;
    private LinearLayout backgroundModeButtonsContainer;
    private LinearLayout providerButtonsContainer;
    private TextView uiLanguageSelectButton;
    private TextView outputLanguageSelectButton;
    private LinearLayout sourceLanguageButtonsContainer;
    private ScrollView settingsScrollView;
    private ScrollView logScrollView;
    private Switch languageTranslationSwitch;
    private Switch languagePronunciationSwitch;
    private Switch metadataTranslationSwitch;
    private Switch japaneseFuriganaSwitch;
    private Switch autoInstrumentalBreakSwitch;
    private Switch interludeLabelsSwitch;
    private Switch syncedLyricsKaraokeSwitch;
    private Switch karaokeBounceSwitch;
    private Switch landscapeAutoHideControlsSwitch;
    private Switch keepScreenOnSwitch;
    private Switch backgroundNoiseSwitch;
    private Switch backgroundReduceMotionSwitch;
    private SeekBar backgroundBrightnessSeekBar;
    private SeekBar backgroundBlurSeekBar;
    private TextView backgroundBrightnessValueView;
    private TextView backgroundBlurValueView;
    private EditText apiKeysInput;
    private EditText modelInput;
    private EditText baseUrlInput;
    private EditText maxTokensInput;
    private EditText temperatureInput;
    private TextView backgroundSolidColorValueView;
    private View backgroundSolidColorSwatch;
    private EditText spotifyClientIdInput;
    private EditText spotifyClientSecretInput;
    private EditText spotifySetupClientIdInput;
    private EditText spotifySetupClientSecretInput;
    private EditText lyricsManualSearchTitleInput;
    private EditText lyricsManualSearchArtistInput;
    private TextView spotifySetupStatusView;
    private TextView lyricsManualSearchStatusView;
    private TextView updateStatusView;
    private TextView onboardingWelcomeText;
    private TextView onboardingStepLabel;
    private TextView onboardingBackButton;
    private TextView onboardingNextButton;
    private TextView onboardingUiLanguageSelectButton;
    private TextView onboardingPermissionStatusView;
    private LinearLayout onboardingBody;

    private TrackSnapshot currentTrack;
    private LyricsResult currentLyricsResult = LyricsResult.empty("");
    private LyricsResult currentBaseLyricsResult = LyricsResult.empty("");
    private LyricsResult currentFuriganaResult;
    private YouTubeBackgroundRepository.VideoInfo currentYouTubeBackgroundInfo;
    private boolean currentYouTubeBackgroundLoading;
    private String currentFuriganaKey = "";
    private String currentLyricsKey = "";
    private String currentArtworkKey = "";
    private String currentYouTubeBackgroundRequestKey = "";
    private String currentResolvedIsrc = "";
    private String currentResolvedSpotifyTrackId = "";
    private Bitmap currentArtworkBitmap;
    private boolean currentArtworkFromSpotify;
    private String translatedTrackTitle = "";
    private String translatedTrackArtist = "";
    private boolean lyricsPageVisible;
    private boolean inAppBrowserVisible;
    private String inAppBrowserInitialUrl = "";
    private long lastBackPressElapsedMs;
    private float pageDragStartX;
    private float pageDragStartY;
    private float pageDragStartTranslationY;
    private boolean pageDragging;
    private int lyricsPageCornerRadiusDp = -1;
    private int lyricsPageContentTopPaddingPx = -1;
    private ValueAnimator lyricsPageContentPaddingAnimator;
    private ValueAnimator inAppBrowserSkeletonAnimator;
    private final List<View> inAppBrowserSkeletonPulseViews = new ArrayList<>();
    private VelocityTracker pageVelocityTracker;
    private float artworkSwipeStartX;
    private float artworkSwipeStartY;
    private boolean artworkSwipeDragging;
    private VelocityTracker artworkVelocityTracker;
    private long lastProgressUiUpdateMs;
    private long pendingSeekPositionMs = -1L;
    private long pendingSeekUptimeMs;
    private long lastSeekCommandUptimeMs;
    private long lastSeekCommandPositionMs = -1L;
    private String detectedLyricsSourceLang = "en";
    private String selectedRuleSourceLang = "auto";
    private String activeLyricsPopupTab = LYRICS_POPUP_TAB_LANGUAGE;
    private int currentTrackSyncOffsetMs;
    private int currentVideoSyncOffsetMs;
    private boolean lyricsLanguageSettingsVisible;
    private boolean suppressLanguageRuleEvents;
    private boolean suppressSettingsEvents;
    private boolean aiLyricsGenerating;
    private boolean lyricsSupplementPronunciationLoading;
    private boolean lyricsSupplementTranslationLoading;
    private boolean lyricsSupplementFuriganaLoading;
    private boolean spotifyCredentialsValidationInFlight;
    private boolean updateCheckInFlight;
    private boolean updateDownloadInFlight;
    private boolean automaticUpdateCheckStarted;
    private boolean spotifySetupRequired;
    private boolean manualLrclibSearchInFlight;
    private int onboardingStep;
    private int onboardingWelcomeIndex = -1;
    private String activeSettingsTab = SETTINGS_TAB_LYRICS;
    private boolean landscapeControlsVisible = true;
    private boolean consumeLandscapeRevealGesture;
    private boolean pendingOpenLyricsPageFromIntent;
    private boolean lyricsMetaLongPressTriggered;
    private Runnable lyricsMetaLongPressRunnable;
    private UpdateChecker.UpdateInfo pendingUpdateInfo;
    private final Runnable landscapeControlsAutoHideRunnable = () -> setLandscapeControlsVisible(false, true);

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updatePlaybackUi();
            handler.postDelayed(this, 16L);
        }
    };

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        aiLyricsSettings = new AiLyricsSettings(this);
        aiLyricsRepository = new AiLyricsRepository(this);
        furiganaRepository = new FuriganaRepository(this);
        lyricsRepository = new LyricsRepository(this);
        youtubeBackgroundRepository = new YouTubeBackgroundRepository(this);
        updateChecker = new UpdateChecker(this);
        Window window = getWindow();
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.rgb(17, 18, 22));
        setContentView(buildContentView());
        applySystemBarsForOrientation();
        AiLyricsSettings.Snapshot settingsSnapshot = aiLyricsSettings.snapshot();
        applyKeepScreenOnSetting(settingsSnapshot);
        applyBackgroundSettings(settingsSnapshot);
        applyTypographySettings(settingsSnapshot);
        applySpeakerColorSettings(settingsSnapshot);
        updateSpotifySetupGate(false);
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
        consumeOpenLyricsPageRequest();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SpotifyShortcutOverlayController.setIvLyricsForeground(true);
        NowPlayingService.register(this);
        updatePermissionState();
        NowPlayingService.requestRefresh(this);
        onNowPlayingChanged(NowPlayingService.getLatestSnapshot());
        updateSpotifySetupGate(false);
        updateOnboardingPermissionState();
        applySystemBarsForOrientation();
        applyKeepScreenOnSetting(aiLyricsSettings.snapshot());
        applyLandscapeControlsAutoHideSetting();
        handler.post(ticker);
        consumeOpenLyricsPageRequest();
        maybeStartAutomaticUpdateCheck();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applySystemBarsForOrientation();
            applyLandscapeControlsAutoHideSetting();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (youtubeBackgroundView != null) {
            youtubeBackgroundView.suppressHardSyncFor(900L);
        }
        rebuildContentViewAfterConfigurationChange();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean shouldConsumeReveal = handleLandscapeControlTouch(event);
        if (shouldConsumeReveal) {
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onPause() {
        SpotifyShortcutOverlayController.setIvLyricsForeground(false);
        NowPlayingService.requestRefresh(this);
        NowPlayingService.unregister(this);
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(landscapeControlsAutoHideRunnable);
        cancelLyricsMetaLongPress();
        handler.removeCallbacks(onboardingWelcomeTicker);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        dismissLyricsMetaTip();
        if (lyricsRepository != null) {
            lyricsRepository.shutdown();
        }
        if (aiLyricsRepository != null) {
            aiLyricsRepository.shutdown();
        }
        if (furiganaRepository != null) {
            furiganaRepository.shutdown();
        }
        if (youtubeBackgroundRepository != null) {
            youtubeBackgroundRepository.shutdown();
        }
        if (updateChecker != null) {
            updateChecker.shutdown();
        }
        destroyInAppBrowserWebView();
        destroyYouTubeBackgroundView();
        seekExecutor.shutdownNow();
        updateExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (isInAppBrowserVisible()) {
            lastBackPressElapsedMs = 0L;
            showInAppBrowser(false);
            return;
        }
        if (isSpotifySetupPanelVisible()) {
            lastBackPressElapsedMs = 0L;
            if (onboardingStep > 0) {
                showOnboardingStep(onboardingStep - 1);
                return;
            }
            Toast.makeText(this, ui("toast.setup_required"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (isSettingsPanelVisible()) {
            showSettingsPanel(false);
            lastBackPressElapsedMs = 0L;
            return;
        }
        if (lyricsPageVisible) {
            showLyricsPage(false);
            lastBackPressElapsedMs = 0L;
            return;
        }

        long now = SystemClock.uptimeMillis();
        if (now - lastBackPressElapsedMs <= 1800L) {
            super.onBackPressed();
            return;
        }
        lastBackPressElapsedMs = now;
        Toast.makeText(this, ui("toast.back_exit"), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onNowPlayingChanged(TrackSnapshot snapshot) {
        currentTrack = snapshot;
        updatePermissionState();
        if (!isSpotifyApiConfigured()) {
            updateSpotifySetupGate(false);
            setSpotifySetupRequiredState(snapshot);
            return;
        }
        spotifySetupRequired = false;

        if (snapshot == null || !snapshot.hasUsableMetadata()) {
            titleView.setText("ivLyrics");
            artistView.setText(ui("status.waiting_spotify"));
            applyNowPlayingTextColors();
            lyricsTitleView.setText("ivLyrics");
            lyricsArtistView.setText(ui("status.waiting_spotify"));
            translatedTrackTitle = "";
            translatedTrackArtist = "";
            updateArtwork(null, "");
            updateProgressViews(0L, 0L);
            playPauseButton.setPlaying(false);
            sourceView.setText("");
            statusView.setText(NowPlayingService.isNotificationAccessEnabled(this)
                    ? ui("status.detecting_media")
                    : ui("status.permission_required"));
            debugProgressView.setText("0:00 / 0:00");
            pendingSeekPositionMs = -1L;
            resetLogs("waiting for current track");
            currentLyricsResult = LyricsResult.empty(ui("status.waiting_current_track"));
            currentBaseLyricsResult = currentLyricsResult;
            currentFuriganaResult = null;
            currentFuriganaKey = "";
            setLyricsTrackDurationOnViews(0L);
            setLyricsResultOnViews(currentLyricsResult);
            setLyricsSupplementLoading(false, false, false);
            updateLyricPreview(0L);
            currentLyricsKey = "";
            currentArtworkKey = "";
            currentResolvedIsrc = "";
            currentResolvedSpotifyTrackId = "";
            currentArtworkFromSpotify = false;
            currentTrackSyncOffsetMs = 0;
            currentVideoSyncOffsetMs = 0;
            aiLyricsGenerating = false;
            detectedLyricsSourceLang = "en";
            selectedRuleSourceLang = "auto";
            updateLyricsLanguageSettingsUi();
            resetManualLrclibSearchForTrack(null);
            resetYouTubeBackgroundForTrack();
            return;
        }

        String nextKey = snapshot.stableKey();
        boolean trackChanged = !nextKey.equals(currentLyricsKey);
        if (trackChanged) {
            currentArtworkFromSpotify = false;
            translatedTrackTitle = "";
            translatedTrackArtist = "";
            currentResolvedIsrc = snapshot.isrc;
            currentResolvedSpotifyTrackId = snapshot.trackId;
            currentTrackSyncOffsetMs = aiLyricsSettings == null ? 0 : aiLyricsSettings.trackSyncOffsetMs(nextKey);
            currentVideoSyncOffsetMs = aiLyricsSettings == null ? 0 : aiLyricsSettings.trackVideoSyncOffsetMs(nextKey);
        }
        updateTrackMetadataTextViews(snapshot);
        String artworkKey = snapshot.artworkKey();
        if (trackChanged || (!currentArtworkFromSpotify && !artworkKey.equals(currentArtworkKey))) {
            currentArtworkKey = artworkKey;
            updateArtwork(snapshot.artwork, artworkKey);
        }
        long playerPosition = currentPlaybackPosition(snapshot);
        updateProgressViews(playerPosition, snapshot.durationMs);
        setLyricsPlaybackPositionOnViews(lyricsPlaybackPosition(playerPosition, snapshot.durationMs));
        setLyricsTrackDurationOnViews(snapshot.durationMs);
        playPauseButton.setPlaying(snapshot.playing);

        if (trackChanged) {
            currentLyricsKey = nextKey;
            currentTrackSyncOffsetMs = aiLyricsSettings == null ? 0 : aiLyricsSettings.trackSyncOffsetMs(currentLyricsKey);
            currentVideoSyncOffsetMs = aiLyricsSettings == null ? 0 : aiLyricsSettings.trackVideoSyncOffsetMs(currentLyricsKey);
            aiLyricsGenerating = false;
            detectedLyricsSourceLang = "en";
            selectedRuleSourceLang = "auto";
            updateLyricsLanguageSettingsUi();
            resetManualLrclibSearchForTrack(snapshot);
            pendingSeekPositionMs = -1L;
            sourceView.setText(ui("status.lyrics_loading"));
            statusView.setText(snapshot.isrc.isEmpty()
                    ? ui("status.lyrics_lookup_spotify")
                    : ui("status.lyrics_lookup_player"));
            resetLogs("new media track");
            appendLog("media session snapshot: "
                    + "id=" + snapshot.trackId
                    + " / title=\"" + snapshot.title + "\""
                    + " / artist=\"" + snapshot.artist + "\""
                    + " / artwork=" + artworkDebug(snapshot)
                    + packageSuffix(snapshot.packageName));
            currentLyricsResult = LyricsResult.empty(ui("status.lyrics_loading"));
            currentBaseLyricsResult = currentLyricsResult;
            currentFuriganaResult = null;
            currentFuriganaKey = "";
            resetYouTubeBackgroundForTrack();
            if (!currentResolvedIsrc.isEmpty()) {
                syncYouTubeBackgroundState();
            }
            setLyricsTrackDurationOnViews(snapshot.durationMs);
            setLyricsResultOnViews(currentLyricsResult);
            setLyricsSupplementLoading(false, false);
            updateLyricPreview(0L);
            if (lyricsRepository != null) {
                lyricsRepository.loadLyrics(snapshot, this);
            }
        }
        updateYouTubeBackgroundPlaybackState();
    }

    private void setSpotifySetupRequiredState(TrackSnapshot snapshot) {
        if (!spotifySetupRequired) {
            resetLogs("spotify api setup required");
            appendLog("spotify api: client id/secret required before Spotify Web API lookup");
        }
        spotifySetupRequired = true;
        titleView.setText(ui("status.spotify_required_title"));
        artistView.setText(ui("status.spotify_required_subtitle"));
        applyNowPlayingTextColors();
        lyricsTitleView.setText(ui("status.spotify_required_title"));
        lyricsArtistView.setText(ui("status.spotify_required_subtitle"));
        translatedTrackTitle = "";
        translatedTrackArtist = "";
        updateArtwork(null, "");
        updateProgressViews(0L, snapshot == null ? 0L : snapshot.durationMs);
        playPauseButton.setPlaying(false);
        sourceView.setText(ui("status.spotify_required_title"));
        statusView.setText(ui("status.spotify_required_detail"));
        debugProgressView.setText("0:00 / 0:00");
        pendingSeekPositionMs = -1L;
        currentLyricsResult = LyricsResult.empty(ui("status.spotify_required_plain"));
        currentBaseLyricsResult = currentLyricsResult;
        currentFuriganaResult = null;
        currentFuriganaKey = "";
        setLyricsTrackDurationOnViews(0L);
        setLyricsResultOnViews(currentLyricsResult);
        setLyricsSupplementLoading(false, false);
        updateLyricPreview(0L);
        currentLyricsKey = "";
        currentArtworkKey = "";
        currentResolvedIsrc = "";
        currentResolvedSpotifyTrackId = "";
        currentArtworkFromSpotify = false;
        currentTrackSyncOffsetMs = 0;
        currentVideoSyncOffsetMs = 0;
        aiLyricsGenerating = false;
        detectedLyricsSourceLang = "en";
        selectedRuleSourceLang = "auto";
        updateLyricsLanguageSettingsUi();
        resetManualLrclibSearchForTrack(null);
        resetYouTubeBackgroundForTrack();
    }

    @Override
    public void onLyricsLoaded(String trackKey, LyricsResult result) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        aiLyricsGenerating = false;
        currentBaseLyricsResult = result;
        currentLyricsResult = result;
        if (result != null) {
            currentResolvedIsrc = nonEmpty(result.isrc, currentResolvedIsrc);
            currentResolvedSpotifyTrackId = nonEmpty(result.spotifyTrackId, currentResolvedSpotifyTrackId);
        }
        currentFuriganaResult = null;
        currentFuriganaKey = "";
        setLyricsResultOnViews(result);
        setLyricsSupplementLoading(false, false);
        updateLyricPreview(currentTrack == null ? 0L : currentLyricsPlaybackPosition(currentTrack));
        sourceView.setText(result.providerLabel);
        statusView.setText(result.detail);
        updateDetectedLyricsSourceLanguage(result);
        updateLyricsLanguageSettingsUi();
        requestMetadataTranslation(false);
        requestAiLyrics(false);
        syncYouTubeBackgroundState();
    }

    @Override
    public void onLyricsError(String trackKey, String message) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        currentLyricsResult = LyricsResult.empty(ui("status.lyrics_request_failed"));
        currentBaseLyricsResult = currentLyricsResult;
        currentFuriganaResult = null;
        currentFuriganaKey = "";
        setLyricsResultOnViews(currentLyricsResult);
        setLyricsSupplementLoading(false, false);
        updateLyricPreview(0L);
        sourceView.setText("");
        statusView.setText(message);
        updateDetectedLyricsSourceLanguage(null);
        updateLyricsLanguageSettingsUi();
        requestMetadataTranslation(false);
        resetYouTubeBackgroundForTrack();
    }

    @Override
    public void onLyricsLog(String trackKey, String message) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        appendLog(message);
    }

    @Override
    public void onLyricsArtworkLoaded(String trackKey, Bitmap artwork, String artworkKey) {
        if (!trackKey.equals(currentLyricsKey) || artwork == null) {
            return;
        }
        currentArtworkKey = artworkKey == null ? "" : artworkKey;
        currentArtworkFromSpotify = true;
        updateArtwork(artwork, currentArtworkKey);
        appendLog("spotify artwork applied: " + artwork.getWidth() + "x" + artwork.getHeight());
    }

    @Override
    public void onLyricsMetadataResolved(String trackKey, String isrc, String spotifyTrackId) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        String normalizedIsrc = TrackSnapshot.normalizeIsrc(isrc);
        String safeSpotifyTrackId = spotifyTrackId == null ? "" : spotifyTrackId.trim();
        boolean changed = false;
        if (!normalizedIsrc.isEmpty() && !normalizedIsrc.equals(currentResolvedIsrc)) {
            currentResolvedIsrc = normalizedIsrc;
            changed = true;
        }
        if (!safeSpotifyTrackId.isEmpty() && !safeSpotifyTrackId.equals(currentResolvedSpotifyTrackId)) {
            currentResolvedSpotifyTrackId = safeSpotifyTrackId;
            changed = true;
        }
        if (changed && !currentResolvedIsrc.isEmpty()) {
            appendLog("youtube background: metadata ready, preloading video isrc="
                    + currentResolvedIsrc
                    + (currentResolvedSpotifyTrackId.isEmpty() ? "" : " / trackId=" + currentResolvedSpotifyTrackId));
            syncYouTubeBackgroundState();
        }
    }

    @Override
    public void onManualLrclibSearchResults(String trackKey, List<LyricsRepository.ManualLrclibCandidate> candidates) {
        if (!isCurrentManualLrclibTrack(trackKey)) {
            return;
        }
        manualLrclibSearchInFlight = false;
        renderManualLrclibCandidates(candidates);
    }

    @Override
    public void onManualLrclibLyricsLoaded(String trackKey, LyricsResult result) {
        if (!isCurrentManualLrclibTrack(trackKey)) {
            return;
        }
        manualLrclibSearchInFlight = false;
        aiLyricsGenerating = false;
        currentBaseLyricsResult = result;
        currentLyricsResult = result;
        currentFuriganaResult = null;
        currentFuriganaKey = "";
        setLyricsResultOnViews(result);
        setLyricsSupplementLoading(false, false);
        updateLyricPreview(currentTrack == null ? 0L : currentLyricsPlaybackPosition(currentTrack));
        sourceView.setText(result.providerLabel);
        statusView.setText(result.detail);
        updateDetectedLyricsSourceLanguage(result);
        updateLyricsLanguageSettingsUi();
        setManualLrclibStatus(ui("lyrics.lrclib_search.loaded"));
        showSavedToast(ui("lyrics.lrclib_search.loaded"));
        requestMetadataTranslation(false);
        requestAiLyrics(false);
        syncYouTubeBackgroundState();
    }

    @Override
    public void onManualLrclibError(String trackKey, String message) {
        if (!isCurrentManualLrclibTrack(trackKey)) {
            return;
        }
        manualLrclibSearchInFlight = false;
        String detail = message == null || message.trim().isEmpty()
                ? ui("repo.lyrics_not_found")
                : message.trim();
        setManualLrclibStatus(uiFormat("lyrics.lrclib_search.error_format", detail));
        showSavedToast(detail);
    }

    @Override
    public void onManualLrclibLog(String trackKey, String message) {
        if (!isCurrentManualLrclibTrack(trackKey)) {
            return;
        }
        appendLog(message);
    }

    @Override
    public void onYouTubeBackgroundLoaded(String requestKey, YouTubeBackgroundRepository.VideoInfo info, boolean fromCache) {
        if (!isCurrentYouTubeBackgroundRequest(requestKey)) {
            return;
        }
        currentYouTubeBackgroundLoading = false;
        currentYouTubeBackgroundInfo = info;
        appendLog("youtube background: "
                + (fromCache ? "cache" : "loaded")
                + " / videoId=" + info.youtubeVideoId
                + (info.youtubeTitle.isEmpty() ? "" : " / title=\"" + info.youtubeTitle + "\"")
                + (info.hasCaptionStartTime ? " / captionStart=" + info.captionStartTimeSeconds + "s" : "")
                + (info.autoGenerated ? " / auto" : ""));
        if (youtubeBackgroundView != null && isVideoBackgroundMode()) {
            youtubeBackgroundView.loadVideo(info);
            updateYouTubeBackgroundPlaybackState();
        }
    }

    @Override
    public void onYouTubeBackgroundError(String requestKey, String message) {
        if (!isCurrentYouTubeBackgroundRequest(requestKey)) {
            return;
        }
        currentYouTubeBackgroundLoading = false;
        currentYouTubeBackgroundInfo = null;
        appendLog(message == null || message.trim().isEmpty()
                ? "youtube background: unavailable"
                : message.trim());
        if (youtubeBackgroundView != null) {
            youtubeBackgroundView.clearVideo();
        }
    }

    @Override
    public void onYouTubeBackgroundLog(String requestKey, String message) {
        if (!isCurrentYouTubeBackgroundRequest(requestKey)
                || message == null
                || message.trim().isEmpty()) {
            return;
        }
        appendLog(message.trim());
    }

    @Override
    public void onAiLyricsLoaded(String trackKey, LyricsResult result) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        aiLyricsGenerating = false;
        currentLyricsResult = mergeCurrentFuriganaInto(result);
        setLyricsResultOnViews(currentLyricsResult);
        setLyricsSupplementLoading(false, false, lyricsSupplementFuriganaLoading);
        updateLyricPreview(currentTrack == null ? 0L : currentLyricsPlaybackPosition(currentTrack));
        statusView.setText(currentLyricsResult.detail);
        if (aiSettingsStatusView != null) {
            aiSettingsStatusView.setText(ui("status.ai_applied"));
        }
    }

    @Override
    public void onAiLyricsError(String trackKey, String message) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        aiLyricsGenerating = false;
        setLyricsSupplementLoading(false, false, lyricsSupplementFuriganaLoading);
        updateLyricPreview(currentTrack == null ? 0L : currentLyricsPlaybackPosition(currentTrack));
        if (aiSettingsStatusView != null) {
            aiSettingsStatusView.setText(uiFormat("status.ai_failed_format", message));
        }
    }

    @Override
    public void onAiLyricsLog(String trackKey, String message) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        appendLog(message);
    }

    @Override
    public void onFuriganaLoaded(String trackKey, LyricsResult result) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        currentFuriganaKey = trackKey;
        currentFuriganaResult = result;
        currentLyricsResult = mergeFuriganaIntoResult(currentLyricsResult, result);
        setLyricsResultOnViews(currentLyricsResult);
        setLyricsSupplementLoading(lyricsSupplementPronunciationLoading, lyricsSupplementTranslationLoading, false);
        updateLyricPreview(currentTrack == null ? 0L : currentLyricsPlaybackPosition(currentTrack));
        statusView.setText(currentLyricsResult.detail);
    }

    @Override
    public void onFuriganaError(String trackKey, String message) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        setLyricsSupplementLoading(lyricsSupplementPronunciationLoading, lyricsSupplementTranslationLoading, false);
        updateLyricPreview(currentTrack == null ? 0L : currentLyricsPlaybackPosition(currentTrack));
        appendLog("furigana js error: " + message);
    }

    @Override
    public void onFuriganaLog(String trackKey, String message) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        appendLog(message);
    }

    @Override
    public void onAiMetadataTranslationLoaded(String trackKey, AiLyricsRepository.MetadataTranslation translation) {
        if (!trackKey.equals(currentLyricsKey) || translation == null) {
            return;
        }
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings == null ? null : aiLyricsSettings.snapshot();
        String source = effectiveSelectedSourceLang();
        String target = snapshot == null ? "" : snapshot.resolveTargetLanguage(source);
        String normalizedSource = AiLyricsSettings.normalizeLanguageCode(source);
        if (snapshot == null
                || !snapshot.metadataTranslationEnabled
                || AiLyricsSettings.isSameLanguage(source, target)
                || !normalizedSource.equalsIgnoreCase(translation.sourceLang)
                || !AiLyricsSettings.normalizeLanguageCode(target).equalsIgnoreCase(translation.targetLang)) {
            return;
        }
        translatedTrackTitle = translation.title;
        translatedTrackArtist = translation.artist;
        updateTrackMetadataTextViews(currentTrack);
    }

    @Override
    public void onAiMetadataTranslationError(String trackKey, String message) {
        if (!trackKey.equals(currentLyricsKey)) {
            return;
        }
        appendLog("ai metadata failed: " + message);
    }

    private View buildContentView() {
        FrameLayout root = new FrameLayout(this);
        rootView = root;
        backgroundView = new PlayerBackgroundView(this);
        root.addView(backgroundView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        youtubeBackgroundView = reusableYouTubeBackgroundView();
        root.addView(youtubeBackgroundView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        mainPage = buildMainPage();
        root.addView(mainPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        lyricsPage = buildLyricsPage();
        lyricsPage.setVisibility(View.GONE);
        FrameLayout.LayoutParams lyricsPageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        root.addView(lyricsPage, lyricsPageParams);

        debugPanel = buildDebugPanel();
        root.addView(debugPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        settingsPanel = buildSettingsPanel();
        root.addView(settingsPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        spotifySetupPanel = buildSpotifySetupPanel();
        root.addView(spotifySetupPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        inAppBrowserPage = buildInAppBrowserPage();
        inAppBrowserPage.setVisibility(View.GONE);
        root.addView(inAppBrowserPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return root;
    }

    private YouTubeBackgroundView reusableYouTubeBackgroundView() {
        if (youtubeBackgroundView == null) {
            return new YouTubeBackgroundView(this);
        }
        detachFromParent(youtubeBackgroundView);
        return youtubeBackgroundView;
    }

    private void detachFromParent(View view) {
        if (view == null) {
            return;
        }
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }
    }

    private FrameLayout buildMainPage() {
        if (isLandscapeLayout()) {
            return buildLandscapeMainPage();
        }

        FrameLayout page = new FrameLayout(this);
        page.setPadding(dp(24), dp(20), dp(24), dp(26));
        page.setClipChildren(false);
        page.setClipToPadding(false);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setClipChildren(false);
        main.setClipToPadding(false);
        page.addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout top = new FrameLayout(this);
        main.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
        ));

        ImageButton menuButton = iconButton(R.drawable.ic_more_horizontal, 44, 18, Color.WHITE, Color.TRANSPARENT, ui("settings.title"));
        menuButton.setOnClickListener(view -> showSettingsPanel(true));
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.RIGHT | Gravity.TOP);
        menuParams.topMargin = dp(8);
        top.addView(menuButton, menuParams);

        artworkView = new ImageView(this);
        artworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkView.setAdjustViewBounds(false);
        artworkView.setCropToPadding(false);
        artworkView.setBackground(albumFallbackDrawable());
        attachArtworkSwipe(artworkView);
        clipRound(artworkView, 24);
        main.addView(flexSpacer(0.55f), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.55f
        ));

        int artworkSize = Math.min(
                getResources().getDisplayMetrics().widthPixels - dp(32),
                Math.round(getResources().getDisplayMetrics().heightPixels * 0.45f)
        );
        LinearLayout.LayoutParams artworkParams = new LinearLayout.LayoutParams(artworkSize, artworkSize);
        artworkParams.gravity = Gravity.CENTER_HORIZONTAL;
        artworkParams.bottomMargin = dp(8);
        main.addView(artworkView, artworkParams);

        main.addView(flexSpacer(0.45f), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                0.45f
        ));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.HORIZONTAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        main.addView(info, infoParams);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        info.addView(meta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        titleView = slidingLabel("ivLyrics", 28f, Color.WHITE, AppFonts.bold(this));
        titleView.setMaxLines(1);
        attachSpotifyMetaTap(titleView);
        meta.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        artistView = slidingLabel(ui("status.waiting_spotify"), 18f, Color.argb(190, 255, 255, 255), AppFonts.regular(this));
        artistView.setSingleLine(true);
        attachSpotifyMetaTap(artistView);
        LinearLayout.LayoutParams artistParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        artistParams.topMargin = dp(7);
        meta.addView(artistView, artistParams);

        playerProgressView = new PlayerProgressView(this);
        playerProgressView.setOnSeekListener(this::seekPlayerToPosition);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)
        );
        progressParams.leftMargin = dp(2);
        progressParams.rightMargin = dp(2);
        progressParams.topMargin = dp(26);
        main.addView(playerProgressView, progressParams);

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        elapsedView = label("0:00", 12f, Color.argb(204, 255, 255, 255), AppFonts.regular(this));
        remainingView = label("-0:00", 12f, Color.argb(174, 255, 255, 255), AppFonts.regular(this));
        remainingView.setGravity(Gravity.RIGHT);
        times.addView(elapsedView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        times.addView(remainingView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        main.addView(times, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(76)
        );
        controlsParams.topMargin = dp(8);
        main.addView(controls, controlsParams);

        TransportButtonView previousButton = new TransportButtonView(this, TransportButtonView.TYPE_PREVIOUS, false);
        previousButton.setContentDescription(ui("button.prev_track"));
        previousButton.setOnClickListener(view -> runTransportCommand(() -> NowPlayingService.skipToPrevious()));
        controls.addView(previousButton, fixedControlParams(62, 12));

        playPauseButton = new TransportButtonView(this, TransportButtonView.TYPE_PLAY_PAUSE, true);
        playPauseButton.setContentDescription(ui("debug.play_pause"));
        playPauseButton.setOnClickListener(view -> runTransportCommand(() -> NowPlayingService.togglePlayback()));
        controls.addView(playPauseButton, fixedControlParams(72, 18));

        TransportButtonView nextButton = new TransportButtonView(this, TransportButtonView.TYPE_NEXT, false);
        nextButton.setContentDescription(ui("button.next_track"));
        nextButton.setOnClickListener(view -> runTransportCommand(() -> NowPlayingService.skipToNext()));
        controls.addView(nextButton, fixedControlParams(62, 12));

        main.addView(flexSpacer(1.0f), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
        ));

        lyricPreviewContainer = new LinearLayout(this);
        lyricPreviewContainer.setOrientation(LinearLayout.VERTICAL);
        lyricPreviewContainer.setGravity(Gravity.CENTER);
        lyricPreviewContainer.setPadding(dp(12), dp(8), dp(12), dp(8));
        lyricPreviewContainer.setOnClickListener(view -> showLyricsPage(true));
        attachPageSwipe(lyricPreviewContainer, true, true);
        lyricPreviewView = new MainLyricPreviewView(this);
        lyricPreviewView.setKaraokeBounceEffectEnabled(aiLyricsSettings.snapshot().karaokeBounceEffectEnabled);
        lyricPreviewView.setTypographySettings(aiLyricsSettings.snapshot().typography);
        lyricPreviewContainer.addView(lyricPreviewView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        main.addView(lyricPreviewContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        attachPageSwipe(page, true, false);
        return page;
    }

    private FrameLayout buildLandscapeMainPage() {
        FrameLayout page = new FrameLayout(this);
        page.setPadding(dp(22), dp(16), dp(22), dp(16));
        page.setClipChildren(false);
        page.setClipToPadding(false);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        main.setGravity(Gravity.CENTER_VERTICAL);
        main.setClipChildren(false);
        main.setClipToPadding(false);
        page.addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout player = new LinearLayout(this);
        player.setOrientation(LinearLayout.VERTICAL);
        player.setGravity(Gravity.CENTER_HORIZONTAL);
        player.setClipChildren(false);
        player.setClipToPadding(false);
        main.addView(player, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                0.88f
        ));

        artworkView = new ImageView(this);
        artworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        artworkView.setAdjustViewBounds(false);
        artworkView.setCropToPadding(false);
        artworkView.setBackground(albumFallbackDrawable());
        attachArtworkSwipe(artworkView);
        clipRound(artworkView, 20);

        int artworkSize = landscapeArtworkSize();
        LinearLayout.LayoutParams artworkParams = new LinearLayout.LayoutParams(artworkSize, artworkSize);
        artworkParams.gravity = Gravity.CENTER_HORIZONTAL;
        player.addView(flexSpacer(landscapeTopSpacerWeight()), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                landscapeTopSpacerWeight()
        ));

        landscapeHeroContainer = new LinearLayout(this);
        landscapeHeroContainer.setOrientation(LinearLayout.VERTICAL);
        landscapeHeroContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        landscapeHeroContainer.setClipChildren(false);
        landscapeHeroContainer.setClipToPadding(false);
        player.addView(landscapeHeroContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        landscapeHeroContainer.addView(artworkView, artworkParams);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        meta.setGravity(Gravity.CENTER_HORIZONTAL);
        landscapeMetaContainer = meta;
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        metaParams.gravity = Gravity.CENTER_HORIZONTAL;
        metaParams.topMargin = landscapeMetadataTopMargin(true);
        landscapeHeroContainer.addView(meta, metaParams);

        titleView = label("ivLyrics", 23f, Color.WHITE, AppFonts.bold(this));
        titleView.setGravity(Gravity.CENTER);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setMinHeight(dp(30));
        titleView.setIncludeFontPadding(true);
        titleView.setShadowLayer(dp(3), 0f, dp(1), Color.argb(150, 0, 0, 0));
        titleView.setTextColor(Color.WHITE);
        attachSpotifyMetaTap(titleView);
        LinearLayout.LayoutParams landscapeTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        landscapeTitleParams.gravity = Gravity.CENTER_HORIZONTAL;
        meta.addView(titleView, landscapeTitleParams);

        artistView = label(ui("status.waiting_spotify"), 15f, Color.argb(190, 255, 255, 255), AppFonts.regular(this));
        artistView.setGravity(Gravity.CENTER);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        artistView.setMinHeight(dp(22));
        artistView.setIncludeFontPadding(true);
        artistView.setShadowLayer(dp(2), 0f, dp(1), Color.argb(130, 0, 0, 0));
        artistView.setTextColor(Color.argb(224, 255, 255, 255));
        attachSpotifyMetaTap(artistView);
        LinearLayout.LayoutParams artistParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        artistParams.gravity = Gravity.CENTER_HORIZONTAL;
        artistParams.topMargin = dp(4);
        meta.addView(artistView, artistParams);

        LinearLayout landscapeControls = new LinearLayout(this);
        landscapeControls.setOrientation(LinearLayout.VERTICAL);
        landscapeControls.setGravity(Gravity.CENTER_HORIZONTAL);
        landscapeControlsContainer = landscapeControls;
        player.addView(landscapeControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        playerProgressView = new PlayerProgressView(this);
        playerProgressView.setOnSeekListener(this::seekPlayerToPosition);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(22)
        );
        progressParams.leftMargin = dp(10);
        progressParams.rightMargin = dp(10);
        progressParams.topMargin = dp(12);
        landscapeControls.addView(playerProgressView, progressParams);

        LinearLayout times = new LinearLayout(this);
        times.setOrientation(LinearLayout.HORIZONTAL);
        times.setGravity(Gravity.CENTER_VERTICAL);
        elapsedView = label("0:00", 11f, Color.argb(204, 255, 255, 255), AppFonts.regular(this));
        remainingView = label("-0:00", 11f, Color.argb(174, 255, 255, 255), AppFonts.regular(this));
        remainingView.setGravity(Gravity.RIGHT);
        times.addView(elapsedView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        times.addView(remainingView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams timesParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timesParams.leftMargin = dp(12);
        timesParams.rightMargin = dp(12);
        landscapeControls.addView(times, timesParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(62)
        );
        controlsParams.topMargin = dp(4);
        landscapeControls.addView(controls, controlsParams);

        TransportButtonView previousButton = new TransportButtonView(this, TransportButtonView.TYPE_PREVIOUS, false);
        previousButton.setContentDescription(ui("button.prev_track"));
        previousButton.setOnClickListener(view -> runTransportCommand(() -> NowPlayingService.skipToPrevious()));
        controls.addView(previousButton, fixedControlParams(54, 10));

        playPauseButton = new TransportButtonView(this, TransportButtonView.TYPE_PLAY_PAUSE, true);
        playPauseButton.setContentDescription(ui("debug.play_pause"));
        playPauseButton.setOnClickListener(view -> runTransportCommand(() -> NowPlayingService.togglePlayback()));
        controls.addView(playPauseButton, fixedControlParams(62, 14));

        TransportButtonView nextButton = new TransportButtonView(this, TransportButtonView.TYPE_NEXT, false);
        nextButton.setContentDescription(ui("button.next_track"));
        nextButton.setOnClickListener(view -> runTransportCommand(() -> NowPlayingService.skipToNext()));
        controls.addView(nextButton, fixedControlParams(54, 10));

        player.addView(flexSpacer(landscapeBottomSpacerWeight()), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                landscapeBottomSpacerWeight()
        ));

        FrameLayout lyricsPane = new FrameLayout(this);
        LinearLayout.LayoutParams lyricsPaneParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1.12f
        );
        lyricsPaneParams.leftMargin = dp(18);
        main.addView(lyricsPane, lyricsPaneParams);

        landscapeLyricsView = new LyricsView(this);
        configureLyricsViewUiText(landscapeLyricsView);
        landscapeLyricsView.setVerticalCenterBias(0.50f);
        landscapeLyricsView.setAutoInstrumentalBreakEnabled(aiLyricsSettings.snapshot().autoInstrumentalBreakEnabled);
        landscapeLyricsView.setInterludeLabelsEnabled(aiLyricsSettings.snapshot().interludeLabelsEnabled);
        landscapeLyricsView.setSyncedLyricsKaraokeAnimationEnabled(aiLyricsSettings.snapshot().syncedLyricsKaraokeAnimationEnabled);
        landscapeLyricsView.setKaraokeBounceEffectEnabled(aiLyricsSettings.snapshot().karaokeBounceEffectEnabled);
        landscapeLyricsView.setJapaneseFuriganaEnabled(aiLyricsSettings.snapshot().japaneseFuriganaEnabled);
        landscapeLyricsView.setTypographySettings(aiLyricsSettings.snapshot().typography);
        landscapeLyricsView.setOnSeekListener(this::seekToPosition);
        landscapeLyricsView.setTrackDuration(currentTrack == null ? 0L : currentTrack.durationMs);
        landscapeLyricsView.setResult(currentLyricsResult);
        landscapeLyricsView.setSupplementLoading(lyricsSupplementPronunciationLoading, lyricsSupplementTranslationLoading);
        FrameLayout.LayoutParams landscapeLyricsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        landscapeLyricsParams.leftMargin = dp(2);
        landscapeLyricsParams.rightMargin = dp(10);
        lyricsPane.addView(landscapeLyricsView, landscapeLyricsParams);

        landscapeLyricsSupplementLoadingIndicator = createLyricsSupplementLoadingIndicator();
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(28),
                Gravity.RIGHT | Gravity.TOP
        );
        loadingParams.topMargin = dp(8);
        loadingParams.rightMargin = dp(18);
        lyricsPane.addView(landscapeLyricsSupplementLoadingIndicator, loadingParams);
        setLoadingIndicatorVisible(
                landscapeLyricsSupplementLoadingIndicator,
                lyricsSupplementPronunciationLoading || lyricsSupplementTranslationLoading || lyricsSupplementFuriganaLoading,
                false
        );

        ImageButton menuButton = iconButton(R.drawable.ic_more_horizontal, 44, 18, Color.WHITE, Color.TRANSPARENT, ui("settings.title"));
        landscapeMenuButton = menuButton;
        menuButton.setOnClickListener(view -> showSettingsPanel(true));
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.RIGHT | Gravity.TOP);
        menuParams.topMargin = dp(8);
        page.addView(menuButton, menuParams);

        applyLandscapeControlsAutoHideSetting();
        return page;
    }

    private boolean isLandscapeLayout() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private boolean isLandscapeTabletLayout() {
        Configuration configuration = getResources().getConfiguration();
        return isLandscapeLayout() && configuration.smallestScreenWidthDp >= 600;
    }

    private int landscapeArtworkSize() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean tablet = isLandscapeTabletLayout();
        float heightFraction = tablet ? 0.53f : 0.45f;
        float widthFraction = tablet ? 0.28f : 0.23f;
        int size = Math.min(
                Math.round(metrics.heightPixels * heightFraction),
                Math.round(metrics.widthPixels * widthFraction)
        );
        return Math.max(dp(tablet ? 190 : 132), size);
    }

    @SuppressWarnings("deprecation")
    private void applySystemBarsForOrientation() {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        boolean landscape = isLandscapeLayout();
        View decorView = window.getDecorView();
        if (landscape) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars());
                }
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = window.getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars());
                }
            }
        }
    }

    private boolean landscapeControlsAutoHideEnabled() {
        return isLandscapeLayout()
                && aiLyricsSettings != null
                && aiLyricsSettings.snapshot().landscapeAutoHideControls;
    }

    private void applyLandscapeControlsAutoHideSetting() {
        handler.removeCallbacks(landscapeControlsAutoHideRunnable);
        if (!landscapeControlsAutoHideEnabled() || isSettingsPanelVisible()) {
            setLandscapeControlsVisible(true, false);
            return;
        }
        setLandscapeControlsVisible(true, false);
        scheduleLandscapeControlsAutoHide();
    }

    private boolean handleLandscapeControlTouch(MotionEvent event) {
        if (!landscapeControlsAutoHideEnabled()) {
            consumeLandscapeRevealGesture = false;
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            boolean wasHidden = !landscapeControlsVisible;
            boolean hitHiddenControl = wasHidden && isTouchInsideLandscapeHiddenControls(event);
            setLandscapeControlsVisible(true, true);
            handler.removeCallbacks(landscapeControlsAutoHideRunnable);
            consumeLandscapeRevealGesture = hitHiddenControl;
            return hitHiddenControl;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            scheduleLandscapeControlsAutoHide();
            boolean consume = consumeLandscapeRevealGesture;
            consumeLandscapeRevealGesture = false;
            return consume;
        }
        return consumeLandscapeRevealGesture;
    }

    private void scheduleLandscapeControlsAutoHide() {
        handler.removeCallbacks(landscapeControlsAutoHideRunnable);
        if (!landscapeControlsAutoHideEnabled() || isSettingsPanelVisible()) {
            return;
        }
        handler.postDelayed(landscapeControlsAutoHideRunnable, 2800L);
    }

    private boolean isTouchInsideLandscapeHiddenControls(MotionEvent event) {
        return isTouchInsideView(event, landscapeControlsContainer)
                || isTouchInsideView(event, landscapeMenuButton);
    }

    private boolean isTouchInsideView(MotionEvent event, View view) {
        if (view == null) {
            return false;
        }
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        return rawX >= location[0]
                && rawX <= location[0] + view.getWidth()
                && rawY >= location[1]
                && rawY <= location[1] + view.getHeight();
    }

    private void setLandscapeControlsVisible(boolean visible, boolean animate) {
        landscapeControlsVisible = visible;
        setAutoHideViewVisible(landscapeControlsContainer, visible, animate);
        setAutoHideViewVisible(landscapeMenuButton, visible, animate);
        updateLandscapeHeroForControls(visible, animate);
    }

    private void updateLandscapeHeroForControls(boolean controlsVisible, boolean animate) {
        if (!isLandscapeLayout()) {
            return;
        }
        float heroTranslationY = controlsVisible ? 0f : dp(42);
        float artworkScale = controlsVisible ? 1f : 1.08f;
        long duration = controlsVisible ? 180L : 260L;
        updateLandscapeMetadataGap(controlsVisible);

        if (landscapeHeroContainer != null) {
            landscapeHeroContainer.animate().cancel();
            if (animate) {
                landscapeHeroContainer.animate()
                        .translationY(heroTranslationY)
                        .setDuration(duration)
                        .start();
            } else {
                landscapeHeroContainer.setTranslationY(heroTranslationY);
            }
        }
        if (artworkView != null) {
            artworkView.animate().cancel();
            if (animate) {
                artworkView.animate()
                        .scaleX(artworkScale)
                        .scaleY(artworkScale)
                        .setDuration(duration)
                        .start();
            } else {
                artworkView.setScaleX(artworkScale);
                artworkView.setScaleY(artworkScale);
            }
        }
    }

    private int landscapeMetadataTopMargin(boolean controlsVisible) {
        if (isLandscapeTabletLayout()) {
            return dp(controlsVisible ? 24 : 34);
        }
        return dp(controlsVisible ? 12 : 24);
    }

    private float landscapeTopSpacerWeight() {
        return isLandscapeTabletLayout() ? 0.42f : 0.38f;
    }

    private float landscapeBottomSpacerWeight() {
        return isLandscapeTabletLayout() ? 0.26f : 0.24f;
    }

    private void updateLandscapeMetadataGap(boolean controlsVisible) {
        if (landscapeMetaContainer == null) {
            return;
        }
        ViewGroup.LayoutParams rawParams = landscapeMetaContainer.getLayoutParams();
        if (!(rawParams instanceof LinearLayout.LayoutParams)) {
            return;
        }
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
        int nextTopMargin = landscapeMetadataTopMargin(controlsVisible);
        if (params.topMargin == nextTopMargin) {
            return;
        }
        params.topMargin = nextTopMargin;
        params.gravity = Gravity.CENTER_HORIZONTAL;
        landscapeMetaContainer.setLayoutParams(params);
    }

    private void setAutoHideViewVisible(View view, boolean visible, boolean animate) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setEnabled(visible);
        if (visible) {
            view.setVisibility(View.VISIBLE);
            if (animate) {
                view.animate().alpha(1f).setDuration(150L).start();
            } else {
                view.setAlpha(1f);
            }
            return;
        }
        if (animate) {
            view.animate()
                    .alpha(0f)
                    .setDuration(230L)
                    .withEndAction(() -> {
                        if (!landscapeControlsVisible) {
                            view.setVisibility(View.INVISIBLE);
                        }
                    })
                    .start();
        } else {
            view.setAlpha(0f);
            view.setVisibility(View.INVISIBLE);
        }
    }

    private FrameLayout buildLyricsPage() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.rgb(6, 7, 12));

        lyricsBackgroundView = new PlayerBackgroundView(this);
        lyricsBackgroundView.setAlpha(1f);
        page.addView(lyricsBackgroundView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        View lyricsShade = new View(this);
        lyricsShade.setBackgroundColor(Color.argb(54, 6, 7, 12));
        page.addView(lyricsShade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        lyricsPageContent = content;
        lyricsPageContentTopPaddingPx = dp(LYRICS_PAGE_TOP_PADDING_EXPANDED_DP);
        content.setPadding(dp(24), lyricsPageContentTopPaddingPx, dp(24), dp(22));
        page.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        FrameLayout header = new FrameLayout(this);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66)
        ));

        View handle = new View(this);
        handle.setBackground(roundDrawable(Color.argb(82, 255, 255, 255), dp(1.5f)));
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(42), dp(3), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        header.addView(handle, handleParams);

        LinearLayout metaRow = new LinearLayout(this);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams metaRowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54),
                Gravity.BOTTOM
        );
        header.addView(metaRow, metaRowParams);

        LinearLayout lyricsMeta = new LinearLayout(this);
        lyricsMeta.setOrientation(LinearLayout.VERTICAL);
        lyricsMeta.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.addView(lyricsMeta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        lyricsTitleView = label("ivLyrics", 19f, Color.WHITE, AppFonts.bold(this));
        lyricsTitleView.setSingleLine(true);
        lyricsTitleView.setEllipsize(TextUtils.TruncateAt.END);
        lyricsTitleView.setIncludeFontPadding(true);
        LinearLayout.LayoutParams lyricsTitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lyricsTitleParams.leftMargin = dp(5);
        lyricsMeta.addView(lyricsTitleView, lyricsTitleParams);

        LinearLayout lyricsSubtitleRow = new LinearLayout(this);
        lyricsSubtitleRow.setOrientation(LinearLayout.HORIZONTAL);
        lyricsSubtitleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams subtitleRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleRowParams.topMargin = dp(3);
        subtitleRowParams.leftMargin = dp(5);
        lyricsMeta.addView(lyricsSubtitleRow, subtitleRowParams);

        lyricsArtistView = label(ui("status.waiting_spotify"), 14f, Color.argb(190, 255, 255, 255), AppFonts.regular(this));
        lyricsArtistView.setSingleLine(true);
        lyricsArtistView.setEllipsize(TextUtils.TruncateAt.END);
        lyricsArtistView.setIncludeFontPadding(true);
        lyricsSubtitleRow.addView(lyricsArtistView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        lyricsContributorView = label("", 9f, Color.argb(118, 255, 255, 255), AppFonts.regular(this));
        lyricsContributorView.setSingleLine(true);
        lyricsContributorView.setEllipsize(TextUtils.TruncateAt.END);
        lyricsContributorView.setIncludeFontPadding(true);
        lyricsContributorView.setVisibility(View.GONE);
        LinearLayout.LayoutParams lyricsContributorParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lyricsContributorParams.leftMargin = dp(8);
        lyricsSubtitleRow.addView(lyricsContributorView, lyricsContributorParams);
        attachLyricsMetaSwipe(lyricsTitleView);
        attachLyricsMetaSwipe(lyricsArtistView);

        lyricsSupplementLoadingIndicator = createLyricsSupplementLoadingIndicator();
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(28)
        );
        loadingParams.leftMargin = dp(10);
        metaRow.addView(lyricsSupplementLoadingIndicator, loadingParams);
        setLoadingIndicatorVisible(
                lyricsSupplementLoadingIndicator,
                lyricsSupplementPronunciationLoading || lyricsSupplementTranslationLoading || lyricsSupplementFuriganaLoading,
                false
        );

        lyricsLanguageSettingsPanel = buildLyricsLanguageSettingsPanel();
        lyricsLanguageSettingsPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams languagePanelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        languagePanelParams.topMargin = dp(10);
        content.addView(lyricsLanguageSettingsPanel, languagePanelParams);

        lyricsView = new LyricsView(this);
        configureLyricsViewUiText(lyricsView);
        lyricsView.setVerticalCenterBias(0.42f);
        lyricsView.setAutoInstrumentalBreakEnabled(aiLyricsSettings.snapshot().autoInstrumentalBreakEnabled);
        lyricsView.setInterludeLabelsEnabled(aiLyricsSettings.snapshot().interludeLabelsEnabled);
        lyricsView.setSyncedLyricsKaraokeAnimationEnabled(aiLyricsSettings.snapshot().syncedLyricsKaraokeAnimationEnabled);
        lyricsView.setKaraokeBounceEffectEnabled(aiLyricsSettings.snapshot().karaokeBounceEffectEnabled);
        lyricsView.setJapaneseFuriganaEnabled(aiLyricsSettings.snapshot().japaneseFuriganaEnabled);
        lyricsView.setTypographySettings(aiLyricsSettings.snapshot().typography);
        lyricsView.setOnSeekListener(this::seekToPosition);
        LinearLayout.LayoutParams lyricsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        lyricsParams.topMargin = dp(16);
        content.addView(lyricsView, lyricsParams);

        attachPageSwipe(header, false, false);
        return page;
    }

    private FrameLayout buildInAppBrowserPage() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.TRANSPARENT);
        page.setClickable(true);

        inAppBrowserSheet = new FrameLayout(this);
        inAppBrowserSheet.setBackground(topRoundDrawable(inAppBrowserBackgroundColor(), dp(24)));
        clipTopRoundView(inAppBrowserSheet, 24);
        FrameLayout.LayoutParams sheetParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        sheetParams.topMargin = inAppBrowserSheetTopMarginPx();
        page.addView(inAppBrowserSheet, sheetParams);

        inAppBrowserWebView = new WebView(this);
        inAppBrowserWebView.setBackgroundColor(inAppBrowserBackgroundColor());
        inAppBrowserWebView.setHapticFeedbackEnabled(false);
        inAppBrowserWebView.setOnLongClickListener(view -> true);
        WebSettings settings = inAppBrowserWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        attachInAppBrowserContentSwipe(inAppBrowserWebView);
        inAppBrowserWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null || !request.isForMainFrame()) {
                    return false;
                }
                return shouldOpenBrowserNavigationExternally(request.getUrl().toString());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return shouldOpenBrowserNavigationExternally(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                showInAppBrowserLoading(true);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectInAppBrowserProfileCss(view, url);
                handler.postDelayed(() -> showInAppBrowserLoading(false), 80L);
            }
        });
        inAppBrowserSheet.addView(inAppBrowserWebView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        inAppBrowserLoadingView = buildInAppBrowserLoadingView();
        inAppBrowserLoadingView.setVisibility(View.GONE);
        inAppBrowserSheet.addView(inAppBrowserLoadingView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        inAppBrowserHandleTouchTarget = new FrameLayout(this);
        inAppBrowserHandleTouchTarget.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams handleTargetParams = new FrameLayout.LayoutParams(
                dp(110),
                dp(34),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL
        );
        inAppBrowserSheet.addView(inAppBrowserHandleTouchTarget, handleTargetParams);
        attachInAppBrowserSwipe(inAppBrowserHandleTouchTarget);

        inAppBrowserHandleView = new View(this);
        inAppBrowserHandleView.setBackground(roundDrawable(inAppBrowserHandleColor(), dp(1.5f)));
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(dp(42), dp(3), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        handleParams.topMargin = dp(12);
        inAppBrowserHandleTouchTarget.addView(inAppBrowserHandleView, handleParams);
        return page;
    }

    private LinearLayout buildLyricsLanguageSettingsPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        panel.setBackground(roundDrawable(Color.argb(38, 255, 255, 255), dp(14)));

        lyricsPopupTabButtonsContainer = new LinearLayout(this);
        lyricsPopupTabButtonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        lyricsPopupTabButtonsContainer.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(lyricsPopupTabButtonsContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(38)
        ));
        addLyricsPopupTabButton(LYRICS_POPUP_TAB_LANGUAGE, ui("lyrics.tab.language"));
        addLyricsPopupTabButton(LYRICS_POPUP_TAB_SYNC, ui("lyrics.tab.sync"));
        addLyricsPopupTabButton(LYRICS_POPUP_TAB_VIDEO, ui("lyrics.tab.video"));
        addLyricsPopupTabButton(LYRICS_POPUP_TAB_LRCLIB, "LRCLIB");

        lyricsLanguageSettingsContent = new LinearLayout(this);
        lyricsLanguageSettingsContent.setOrientation(LinearLayout.VERTICAL);
        panel.addView(lyricsLanguageSettingsContent, topMargin(matchWrap(), dp(10)));

        selectedLanguageRuleView = label("", 12f, Color.argb(210, 255, 255, 255), AppFonts.semiBold(this));
        selectedLanguageRuleView.setLineSpacing(dp(2), 1f);
        lyricsLanguageSettingsContent.addView(selectedLanguageRuleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ScrollView choicesScroll = new ScrollView(this);
        choicesScroll.setFillViewport(false);
        choicesScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        LinearLayout choicesContent = new LinearLayout(this);
        choicesContent.setOrientation(LinearLayout.VERTICAL);
        choicesScroll.addView(choicesContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams choicesScrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(300)
        );
        choicesScrollParams.topMargin = dp(10);
        lyricsLanguageSettingsContent.addView(choicesScroll, choicesScrollParams);

        sourceLanguageButtonsContainer = new LinearLayout(this);
        sourceLanguageButtonsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        choicesContent.addView(sourceLanguageButtonsContainer, sourceParams);

        LinearLayout switchRow = new LinearLayout(this);
        switchRow.setOrientation(LinearLayout.HORIZONTAL);
        switchRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams switchRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        switchRowParams.topMargin = dp(10);
        choicesContent.addView(switchRow, switchRowParams);

        languageTranslationSwitch = settingSwitch(ui("lyrics.translation"), "");
        languageTranslationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateSelectedLanguageRuleStatusFromUi();
            saveLyricsLanguageRuleAndRefresh();
        });
        LinearLayout.LayoutParams translationParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        translationParams.rightMargin = dp(4);
        switchRow.addView(languageTranslationSwitch, translationParams);

        languagePronunciationSwitch = settingSwitch(ui("lyrics.pronunciation"), "");
        languagePronunciationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateSelectedLanguageRuleStatusFromUi();
            saveLyricsLanguageRuleAndRefresh();
        });
        LinearLayout.LayoutParams pronunciationParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        pronunciationParams.leftMargin = dp(4);
        switchRow.addView(languagePronunciationSwitch, pronunciationParams);

        lyricsSyncSettingsContent = buildLyricsSyncSettingsContent();
        panel.addView(lyricsSyncSettingsContent, topMargin(matchWrap(), dp(10)));

        videoSyncSettingsContent = buildVideoSyncSettingsContent();
        panel.addView(videoSyncSettingsContent, topMargin(matchWrap(), dp(10)));

        lyricsManualSearchContent = buildLyricsManualSearchContent();
        panel.addView(lyricsManualSearchContent, topMargin(matchWrap(), dp(10)));
        switchLyricsPopupTab(activeLyricsPopupTab);
        return panel;
    }

    private LinearLayout buildLyricsSyncSettingsContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = label(ui("lyrics.sync.title"), 14f, Color.WHITE, AppFonts.bold(this));
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        lyricsSyncOffsetDescriptionView = label("", 11f, Color.argb(168, 255, 255, 255), AppFonts.regular(this));
        lyricsSyncOffsetDescriptionView.setLineSpacing(dp(2), 1f);
        content.addView(lyricsSyncOffsetDescriptionView, topMargin(matchWrap(), dp(5)));

        lyricsSyncOffsetValueView = label("", 25f, Color.WHITE, AppFonts.bold(this));
        lyricsSyncOffsetValueView.setGravity(Gravity.CENTER);
        lyricsSyncOffsetValueView.setBackground(roundDrawable(Color.argb(34, 255, 255, 255), dp(14)));
        content.addView(lyricsSyncOffsetValueView, topMargin(matchWrap(), dp(12)));

        content.addView(buildOffsetButtonRow(-100, -50, -10), topMargin(matchWrap(), dp(10)));
        content.addView(buildOffsetButtonRow(10, 50, 100), topMargin(matchWrap(), dp(8)));

        TextView resetButton = languageButton(ui("lyrics.sync.reset"), false);
        resetButton.setOnClickListener(view -> setCurrentTrackSyncOffset(0, true));
        content.addView(resetButton, topMargin(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        ), dp(8)));
        return content;
    }

    private LinearLayout buildVideoSyncSettingsContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = label(ui("lyrics.video_sync.title"), 14f, Color.WHITE, AppFonts.bold(this));
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        videoSyncOffsetDescriptionView = label("", 11f, Color.argb(168, 255, 255, 255), AppFonts.regular(this));
        videoSyncOffsetDescriptionView.setLineSpacing(dp(2), 1f);
        content.addView(videoSyncOffsetDescriptionView, topMargin(matchWrap(), dp(5)));

        videoSyncOffsetValueView = label("", 25f, Color.WHITE, AppFonts.bold(this));
        videoSyncOffsetValueView.setGravity(Gravity.CENTER);
        videoSyncOffsetValueView.setBackground(roundDrawable(Color.argb(34, 255, 255, 255), dp(14)));
        content.addView(videoSyncOffsetValueView, topMargin(matchWrap(), dp(12)));

        content.addView(buildVideoOffsetButtonRow(-100, -50, -10), topMargin(matchWrap(), dp(10)));
        content.addView(buildVideoOffsetButtonRow(10, 50, 100), topMargin(matchWrap(), dp(8)));

        TextView resetButton = languageButton(ui("lyrics.video_sync.reset"), false);
        resetButton.setOnClickListener(view -> setCurrentVideoSyncOffset(0, true));
        content.addView(resetButton, topMargin(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        ), dp(8)));
        return content;
    }

    private LinearLayout buildLyricsManualSearchContent() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView title = label(ui("lyrics.lrclib_search.title"), 14f, Color.WHITE, AppFonts.bold(this));
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView description = label(ui("lyrics.lrclib_search.desc"), 11f, Color.argb(168, 255, 255, 255), AppFonts.regular(this));
        description.setLineSpacing(dp(2), 1f);
        content.addView(description, topMargin(matchWrap(), dp(5)));

        lyricsManualSearchTitleInput = settingEditText(ui("lyrics.lrclib_search.title_hint"), false, false);
        lyricsManualSearchArtistInput = settingEditText(ui("lyrics.lrclib_search.artist_hint"), false, false);
        content.addView(settingField(ui("lyrics.lrclib_search.field_title"), "", lyricsManualSearchTitleInput), topMargin(matchWrap(), dp(10)));
        content.addView(settingField(ui("lyrics.lrclib_search.field_artist"), "", lyricsManualSearchArtistInput), topMargin(matchWrap(), dp(8)));

        TextView searchButton = primaryButton(ui("lyrics.lrclib_search.button"));
        searchButton.setOnClickListener(view -> performManualLrclibSearch());
        content.addView(searchButton, topMargin(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        ), dp(10)));

        lyricsManualSearchStatusView = label("", 11f, Color.argb(170, 255, 255, 255), AppFonts.semiBold(this));
        lyricsManualSearchStatusView.setLineSpacing(dp(2), 1f);
        content.addView(lyricsManualSearchStatusView, topMargin(matchWrap(), dp(9)));

        ScrollView resultsScroll = new ScrollView(this);
        resultsScroll.setFillViewport(false);
        resultsScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        resultsScroll.setBackground(roundDrawable(Color.argb(22, 255, 255, 255), dp(12)));
        lyricsManualSearchResultsContainer = new LinearLayout(this);
        lyricsManualSearchResultsContainer.setOrientation(LinearLayout.VERTICAL);
        lyricsManualSearchResultsContainer.setPadding(dp(8), dp(8), dp(8), dp(8));
        resultsScroll.addView(lyricsManualSearchResultsContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        content.addView(resultsScroll, topMargin(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220)
        ), dp(8)));

        populateManualLrclibSearchDefaults(false);
        setManualLrclibStatus(ui("lyrics.lrclib_search.ready"));
        return content;
    }

    private LinearLayout buildOffsetButtonRow(int first, int second, int third) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int[] values = {first, second, third};
        for (int index = 0; index < values.length; index++) {
            int delta = values[index];
            TextView button = languageButton(offsetDeltaLabel(delta), false);
            button.setOnClickListener(view -> adjustCurrentTrackSyncOffset(delta));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (index > 0) {
                params.leftMargin = dp(8);
            }
            row.addView(button, params);
        }
        return row;
    }

    private LinearLayout buildVideoOffsetButtonRow(int first, int second, int third) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int[] values = {first, second, third};
        for (int index = 0; index < values.length; index++) {
            int delta = values[index];
            TextView button = languageButton(offsetDeltaLabel(delta), false);
            button.setOnClickListener(view -> adjustCurrentVideoSyncOffset(delta));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (index > 0) {
                params.leftMargin = dp(8);
            }
            row.addView(button, params);
        }
        return row;
    }

    private LinearLayout buildDebugPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(28), dp(22), dp(22));
        panel.setBackground(roundDrawable(Color.argb(238, 15, 18, 31), 0));
        panel.setVisibility(View.GONE);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = label(ui("debug.title"), 24f, Color.WHITE, AppFonts.bold(this));
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView closeButton = pillButton(ui("button.close"));
        closeButton.setOnClickListener(view -> toggleDebugPanel());
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(92), dp(42)));

        sourceView = label("", 13f, Color.rgb(142, 236, 198), AppFonts.semiBold(this));
        panel.addView(sourceView, topMargin(matchWrap(), dp(18)));

        statusView = label("", 12f, Color.argb(206, 255, 255, 255), AppFonts.regular(this));
        statusView.setLineSpacing(dp(2), 1f);
        panel.addView(statusView, topMargin(matchWrap(), dp(8)));

        debugProgressView = label("0:00 / 0:00", 13f, Color.WHITE, AppFonts.semiBold(this));
        panel.addView(debugProgressView, topMargin(matchWrap(), dp(12)));

        permissionButton = debugButton(ui("debug.permission"));
        permissionButton.setOnClickListener(view -> openMediaPermissionSettings());
        panel.addView(permissionButton, topMargin(matchWrap(), dp(14)));

        LinearLayout debugControls = new LinearLayout(this);
        debugControls.setOrientation(LinearLayout.HORIZONTAL);
        debugControls.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(debugControls, topMargin(matchWrap(), dp(10)));

        TextView prev = debugButton(ui("debug.previous"));
        prev.setOnClickListener(view -> runTransportCommand(() -> NowPlayingService.skipToPrevious()));
        debugControls.addView(prev, weightedButtonParams(1f, dp(4)));

        TextView pause = debugButton(ui("debug.play_pause"));
        pause.setOnClickListener(view -> runTransportCommand(() -> NowPlayingService.togglePlayback()));
        debugControls.addView(pause, weightedButtonParams(1.25f, dp(4)));

        TextView next = debugButton(ui("debug.next"));
        next.setOnClickListener(view -> runTransportCommand(() -> NowPlayingService.skipToNext()));
        debugControls.addView(next, weightedButtonParams(1f, dp(4)));

        TextView refresh = debugButton(ui("debug.refresh"));
        refresh.setOnClickListener(view -> NowPlayingService.requestRefresh(this));
        panel.addView(refresh, topMargin(matchWrap(), dp(10)));

        TextView logTitle = label(ui("debug.log"), 14f, Color.WHITE, AppFonts.semiBold(this));
        panel.addView(logTitle, topMargin(matchWrap(), dp(18)));

        logScrollView = new ScrollView(this);
        logScrollView.setFillViewport(false);
        logScrollView.setBackground(roundDrawable(Color.argb(118, 0, 0, 0), dp(10)));
        logScrollView.setPadding(dp(12), dp(10), dp(12), dp(10));

        logView = label(ui("debug.log_waiting"), 11f, Color.argb(212, 255, 255, 255), Typeface.MONOSPACE);
        logView.setLineSpacing(dp(2), 1f);
        logView.setTextIsSelectable(true);
        logScrollView.addView(logView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        logParams.topMargin = dp(8);
        panel.addView(logScrollView, logParams);
        return panel;
    }

    private FrameLayout buildSettingsPanel() {
        FrameLayout panel = new FrameLayout(this);
        panel.setVisibility(View.GONE);
        panel.setBackground(roundDrawable(Color.rgb(12, 13, 17), 0));

        settingsScrollView = new ScrollView(this);
        settingsScrollView.setFillViewport(false);
        panel.addView(settingsScrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(62), dp(22), dp(30));
        settingsScrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout headerText = new LinearLayout(this);
        headerText.setOrientation(LinearLayout.VERTICAL);
        header.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = label(ui("settings.title"), 24f, Color.WHITE, AppFonts.bold(this));
        headerText.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = label(ui("settings.subtitle"), 13f, Color.argb(170, 255, 255, 255), AppFonts.regular(this));
        headerText.addView(subtitle, topMargin(matchWrap(), dp(6)));

        TextView closeButton = pillButton(ui("button.close"));
        closeButton.setOnClickListener(view -> showSettingsPanel(false));
        header.addView(closeButton, new LinearLayout.LayoutParams(dp(88), dp(42)));

        aiSettingsStatusView = label("", 13f, Color.argb(215, 255, 255, 255), AppFonts.semiBold(this));
        aiSettingsStatusView.setLineSpacing(dp(2), 1f);
        content.addView(aiSettingsStatusView, topMargin(matchWrap(), dp(20)));

        settingsTabButtonsContainer = new LinearLayout(this);
        settingsTabButtonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        settingsTabButtonsContainer.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(settingsTabButtonsContainer, topMargin(matchWrap(), dp(18)));
        buildSettingsTabs();

        settingsLyricsPage = settingsPage();
        settingsDisplayPage = settingsPage();
        settingsAiPage = settingsPage();
        settingsToolsPage = settingsPage();
        content.addView(settingsLyricsPage, topMargin(matchWrap(), dp(18)));
        content.addView(settingsDisplayPage, topMargin(matchWrap(), dp(18)));
        content.addView(settingsAiPage, topMargin(matchWrap(), dp(18)));
        content.addView(settingsToolsPage, topMargin(matchWrap(), dp(18)));

        settingsLyricsPage.addView(sectionTitle(ui("section.language")));
        settingsLyricsPage.addView(sectionDescription(ui("section.language_desc")), topMargin(matchWrap(), dp(8)));

        uiLanguageSelectButton = settingsSelectButton("");
        uiLanguageSelectButton.setOnClickListener(view -> showSettingsUiLanguagePopup(uiLanguageSelectButton));
        settingsLyricsPage.addView(settingGroup(
                ui("setting.ui_language"),
                ui("setting.ui_language_desc"),
                uiLanguageSelectButton
        ), topMargin(matchWrap(), dp(12)));

        outputLanguageSelectButton = settingsSelectButton("");
        outputLanguageSelectButton.setOnClickListener(view -> showSettingsOutputLanguagePopup(outputLanguageSelectButton));
        settingsLyricsPage.addView(settingGroup(
                ui("setting.pronunciation_language"),
                ui("setting.pronunciation_language_desc"),
                outputLanguageSelectButton
        ), topMargin(matchWrap(), dp(12)));

        metadataTranslationSwitch = settingSwitch(
                ui("setting.metadata_translation"),
                ui("setting.metadata_translation_desc")
        );
        metadataTranslationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setMetadataTranslationEnabled(isChecked);
            translatedTrackTitle = "";
            translatedTrackArtist = "";
            updateTrackMetadataTextViews(currentTrack);
            if (isChecked) {
                requestMetadataTranslation(true);
            }
            showSavedToast(isChecked ? ui("toast.metadata_translation_on") : ui("toast.metadata_translation_off"));
        });
        settingsLyricsPage.addView(metadataTranslationSwitch, topMargin(matchWrap(), dp(12)));

        japaneseFuriganaSwitch = settingSwitch(
                ui("setting.japanese_furigana"),
                ui("setting.japanese_furigana_desc")
        );
        japaneseFuriganaSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setJapaneseFuriganaEnabled(isChecked);
            setJapaneseFuriganaOnViews(isChecked);
            if (isChecked) {
                requestJapaneseFurigana(false);
            } else {
                setLyricsSupplementLoading(
                        lyricsSupplementPronunciationLoading,
                        lyricsSupplementTranslationLoading,
                        false
                );
                updateLyricPreview(currentTrack == null ? 0L : currentLyricsPlaybackPosition(currentTrack));
            }
            showSavedToast(isChecked ? ui("toast.furigana_on") : ui("toast.furigana_off"));
        });
        settingsLyricsPage.addView(japaneseFuriganaSwitch, topMargin(matchWrap(), dp(12)));

        previewModeButtonsContainer = new LinearLayout(this);
        previewModeButtonsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsLyricsPage.addView(settingGroup(
                ui("setting.main_preview"),
                ui("setting.main_preview_desc"),
                previewModeButtonsContainer
        ), topMargin(matchWrap(), dp(12)));

        autoInstrumentalBreakSwitch = settingSwitch(
                ui("setting.auto_interlude"),
                ui("setting.auto_interlude_desc")
        );
        autoInstrumentalBreakSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setAutoInstrumentalBreakEnabled(isChecked);
            setAutoInstrumentalBreakOnViews(isChecked);
            showSavedToast(isChecked ? ui("toast.auto_interlude_on") : ui("toast.auto_interlude_off"));
        });
        settingsLyricsPage.addView(autoInstrumentalBreakSwitch, topMargin(matchWrap(), dp(12)));

        interludeLabelsSwitch = settingSwitch(
                ui("setting.interlude_labels"),
                ui("setting.interlude_labels_desc")
        );
        interludeLabelsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setInterludeLabelsEnabled(isChecked);
            setInterludeLabelsOnViews(isChecked);
            updateLyricPreview(currentTrack == null ? 0L : currentLyricsPlaybackPosition(currentTrack));
            showSavedToast(ui("toast.settings_saved"));
        });
        settingsLyricsPage.addView(interludeLabelsSwitch, topMargin(matchWrap(), dp(12)));

        syncedLyricsKaraokeSwitch = settingSwitch(
                ui("setting.synced_karaoke_animation"),
                ui("setting.synced_karaoke_animation_desc")
        );
        syncedLyricsKaraokeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setSyncedLyricsKaraokeAnimationEnabled(isChecked);
            setSyncedLyricsKaraokeAnimationOnViews(isChecked);
            showSavedToast(ui("toast.settings_saved"));
        });
        settingsLyricsPage.addView(syncedLyricsKaraokeSwitch, topMargin(matchWrap(), dp(12)));

        karaokeBounceSwitch = settingSwitch(
                ui("setting.karaoke_bounce_effect"),
                ui("setting.karaoke_bounce_effect_desc")
        );
        karaokeBounceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setKaraokeBounceEffectEnabled(isChecked);
            setKaraokeBounceEffectOnViews(isChecked);
            showSavedToast(ui("toast.settings_saved"));
        });
        settingsLyricsPage.addView(karaokeBounceSwitch, topMargin(matchWrap(), dp(12)));

        settingsDisplayPage.addView(sectionTitle(ui("section.player")));
        settingsDisplayPage.addView(sectionDescription(ui("section.player_desc")), topMargin(matchWrap(), dp(8)));

        keepScreenOnSwitch = settingSwitch(
                ui("setting.keep_screen_on"),
                ui("setting.keep_screen_on_desc")
        );
        keepScreenOnSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setKeepScreenOn(isChecked);
            applyKeepScreenOnSetting(aiLyricsSettings.snapshot());
            showSavedToast(isChecked ? ui("toast.keep_screen_on_on") : ui("toast.keep_screen_on_off"));
        });
        settingsDisplayPage.addView(keepScreenOnSwitch, topMargin(matchWrap(), dp(12)));

        landscapeAutoHideControlsSwitch = settingSwitch(
                ui("setting.landscape_auto_hide"),
                ui("setting.landscape_auto_hide_desc")
        );
        landscapeAutoHideControlsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setLandscapeAutoHideControls(isChecked);
            applyLandscapeControlsAutoHideSetting();
            showSavedToast(isChecked ? ui("toast.landscape_auto_hide_on") : ui("toast.landscape_auto_hide_off"));
        });
        settingsDisplayPage.addView(landscapeAutoHideControlsSwitch, topMargin(matchWrap(), dp(12)));

        settingsDisplayPage.addView(sectionTitle(ui("section.typography")), topMargin(matchWrap(), dp(24)));
        settingsDisplayPage.addView(sectionDescription(ui("section.typography_desc")), topMargin(matchWrap(), dp(8)));
        settingsDisplayPage.addView(buildTypographySettingsList(), topMargin(matchWrap(), dp(12)));

        settingsDisplayPage.addView(sectionTitle(ui("section.speaker_colors")), topMargin(matchWrap(), dp(24)));
        settingsDisplayPage.addView(sectionDescription(ui("section.speaker_colors_desc")), topMargin(matchWrap(), dp(8)));
        settingsDisplayPage.addView(buildSpeakerColorSettingsList(), topMargin(matchWrap(), dp(12)));

        settingsDisplayPage.addView(sectionTitle(ui("section.background")), topMargin(matchWrap(), dp(24)));
        settingsDisplayPage.addView(sectionDescription(ui("section.background_desc")), topMargin(matchWrap(), dp(8)));

        backgroundModeButtonsContainer = new LinearLayout(this);
        backgroundModeButtonsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsDisplayPage.addView(settingGroup(
                ui("setting.background_mode"),
                ui("setting.background_mode_desc"),
                backgroundModeButtonsContainer
        ), topMargin(matchWrap(), dp(12)));

        backgroundBrightnessSeekBar = new SeekBar(this);
        backgroundBrightnessSeekBar.setMax(100);
        backgroundBrightnessValueView = label("", 12f, Color.argb(180, 255, 255, 255), AppFonts.semiBold(this));
        backgroundBrightnessSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || suppressSettingsEvents || aiLyricsSettings == null) {
                    return;
                }
                aiLyricsSettings.setBackgroundBrightness(progress);
                updateBackgroundSettingsUi(aiLyricsSettings.snapshot(), false);
                applyBackgroundSettings(aiLyricsSettings.snapshot());
            }
        });
        settingsDisplayPage.addView(settingGroup(ui("setting.brightness"), ui("setting.brightness_desc"), buildSliderRow(backgroundBrightnessSeekBar, backgroundBrightnessValueView)), topMargin(matchWrap(), dp(12)));

        backgroundBlurSeekBar = new SeekBar(this);
        backgroundBlurSeekBar.setMax(100);
        backgroundBlurValueView = label("", 12f, Color.argb(180, 255, 255, 255), AppFonts.semiBold(this));
        backgroundBlurSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || suppressSettingsEvents || aiLyricsSettings == null) {
                    return;
                }
                aiLyricsSettings.setBackgroundBlur(progress);
                updateBackgroundSettingsUi(aiLyricsSettings.snapshot(), false);
                applyBackgroundSettings(aiLyricsSettings.snapshot());
            }
        });
        settingsDisplayPage.addView(settingGroup(ui("setting.blur"), ui("setting.blur_desc"), buildSliderRow(backgroundBlurSeekBar, backgroundBlurValueView)), topMargin(matchWrap(), dp(12)));

        backgroundNoiseSwitch = settingSwitch(ui("setting.noise"), ui("setting.noise_desc"));
        backgroundNoiseSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setBackgroundNoise(isChecked);
            applyBackgroundSettings(aiLyricsSettings.snapshot());
            showSavedToast(isChecked ? ui("toast.background_noise_on") : ui("toast.background_noise_off"));
        });
        settingsDisplayPage.addView(backgroundNoiseSwitch, topMargin(matchWrap(), dp(12)));

        backgroundReduceMotionSwitch = settingSwitch(ui("setting.reduce_motion"), ui("setting.reduce_motion_desc"));
        backgroundReduceMotionSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressSettingsEvents || aiLyricsSettings == null) {
                return;
            }
            aiLyricsSettings.setBackgroundReduceMotion(isChecked);
            applyBackgroundSettings(aiLyricsSettings.snapshot());
            showSavedToast(isChecked ? ui("toast.reduce_motion_on") : ui("toast.reduce_motion_off"));
        });
        settingsDisplayPage.addView(backgroundReduceMotionSwitch, topMargin(matchWrap(), dp(12)));

        settingsDisplayPage.addView(settingGroup(
                ui("field.solid_color"),
                ui("field.solid_color_desc"),
                buildBackgroundSolidColorControl()
        ), topMargin(matchWrap(), dp(12)));

        settingsAiPage.addView(sectionTitle(ui("section.ai_lyrics")));
        settingsAiPage.addView(sectionDescription(ui("section.ai_lyrics_desc")), topMargin(matchWrap(), dp(8)));
        settingsAiPage.addView(sectionTitle(ui("section.provider")), topMargin(matchWrap(), dp(22)));
        providerSummaryView = label("", 12f, Color.argb(170, 255, 255, 255), AppFonts.regular(this));
        providerSummaryView.setLineSpacing(dp(2), 1f);
        settingsAiPage.addView(providerSummaryView, topMargin(matchWrap(), dp(8)));

        providerButtonsContainer = new LinearLayout(this);
        providerButtonsContainer.setOrientation(LinearLayout.VERTICAL);
        settingsAiPage.addView(providerButtonsContainer, topMargin(matchWrap(), dp(12)));
        buildProviderButtons();

        apiKeysInput = settingEditText("", true, true);
        settingsAiPage.addView(settingField(ui("field.api_key"), ui("field.api_key_desc"), apiKeysInput), topMargin(matchWrap(), dp(18)));

        modelInput = settingEditText("", false, false);
        settingsAiPage.addView(settingField(ui("field.model"), ui("field.model_desc"), modelInput), topMargin(matchWrap(), dp(12)));

        baseUrlInput = settingEditText("", false, false);
        settingsAiPage.addView(settingField(ui("field.base_url"), ui("field.base_url_desc"), baseUrlInput), topMargin(matchWrap(), dp(12)));

        LinearLayout advancedRow = new LinearLayout(this);
        advancedRow.setOrientation(LinearLayout.HORIZONTAL);
        advancedRow.setGravity(Gravity.CENTER_VERTICAL);
        maxTokensInput = settingEditText("", false, false);
        temperatureInput = settingEditText("", false, false);
        advancedRow.addView(settingField(ui("field.max_tokens"), "", maxTokensInput), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams tempParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tempParams.leftMargin = dp(10);
        advancedRow.addView(settingField(ui("field.temperature"), "", temperatureInput), tempParams);
        settingsAiPage.addView(advancedRow, topMargin(matchWrap(), dp(12)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        settingsAiPage.addView(actionRow, topMargin(matchWrap(), dp(18)));

        TextView applyButton = primaryButton(ui("button.save_regenerate"));
        applyButton.setOnClickListener(view -> {
            applyAiSettingsFromUi();
            translatedTrackTitle = "";
            translatedTrackArtist = "";
            updateTrackMetadataTextViews(currentTrack);
            requestMetadataTranslation(true);
            requestAiLyrics(true);
        });
        actionRow.addView(applyButton, weightedButtonParams(1.4f, dp(4)));

        TextView keyButton = debugButton(ui("button.get_key"));
        keyButton.setOnClickListener(view -> {
            AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(snapshot.provider.apiKeyUrl)));
        });
        actionRow.addView(keyButton, weightedButtonParams(1f, dp(4)));

        settingsToolsPage.addView(sectionTitle(ui("section.tools")));
        settingsToolsPage.addView(sectionDescription(ui("section.tools_desc")), topMargin(matchWrap(), dp(8)));

        updateStatusView = label(ui("update.status_idle"), 12f, Color.argb(180, 255, 255, 255), AppFonts.regular(this));
        updateStatusView.setLineSpacing(dp(2), 1f);

        LinearLayout updateActions = new LinearLayout(this);
        updateActions.setOrientation(LinearLayout.VERTICAL);
        updateActions.addView(updateStatusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout updateButtonRow = new LinearLayout(this);
        updateButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        updateButtonRow.setGravity(Gravity.CENTER_VERTICAL);
        updateActions.addView(updateButtonRow, topMargin(matchWrap(), dp(10)));

        TextView checkUpdatesButton = primaryButton(ui("button.check_updates"));
        checkUpdatesButton.setOnClickListener(view -> checkForUpdates(true));
        updateButtonRow.addView(checkUpdatesButton, weightedButtonParams(1.2f, dp(4)));

        TextView releasePageButton = debugButton(ui("button.open_release_page"));
        releasePageButton.setOnClickListener(view -> openExternalUrl("https://github.com/ivLis-Studio/ivLyrics-Android/releases"));
        updateButtonRow.addView(releasePageButton, weightedButtonParams(1f, dp(4)));

        settingsToolsPage.addView(settingGroup(
                ui("section.app_update"),
                ui("section.app_update_desc"),
                updateActions
        ), topMargin(matchWrap(), dp(16)));

        LinearLayout spotifyShortcutPermissions = new LinearLayout(this);
        spotifyShortcutPermissions.setOrientation(LinearLayout.VERTICAL);

        spotifyDetectionPermissionButton = debugButton("");
        spotifyDetectionPermissionButton.setOnClickListener(view -> openSpotifyDetectionPermissionSettings());
        spotifyShortcutPermissions.addView(spotifyDetectionPermissionButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        overlayPermissionButton = debugButton("");
        overlayPermissionButton.setOnClickListener(view -> openOverlayPermissionSettings());
        LinearLayout.LayoutParams overlayParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        overlayParams.topMargin = dp(8);
        spotifyShortcutPermissions.addView(overlayPermissionButton, overlayParams);
        updateOverlayPermissionButton();
        settingsToolsPage.addView(settingGroup(
                ui("section.spotify_shortcut"),
                ui("section.spotify_shortcut_desc"),
                spotifyShortcutPermissions
        ), topMargin(matchWrap(), dp(16)));

        settingsToolsPage.addView(sectionTitle(ui("section.spotify_api")), topMargin(matchWrap(), dp(24)));
        settingsToolsPage.addView(sectionDescription(ui("section.spotify_api_desc")), topMargin(matchWrap(), dp(8)));
        settingsToolsPage.addView(buildSpotifyApiSetupInstructions(), topMargin(matchWrap(), dp(12)));

        spotifyClientIdInput = settingEditText("", false, false);
        settingsToolsPage.addView(settingField("Client ID", ui("field.spotify_client_id_desc"), spotifyClientIdInput), topMargin(matchWrap(), dp(12)));

        spotifyClientSecretInput = settingEditText("", false, true);
        settingsToolsPage.addView(settingField("Client Secret", ui("field.spotify_client_secret_desc"), spotifyClientSecretInput), topMargin(matchWrap(), dp(12)));

        LinearLayout spotifyActionRow = new LinearLayout(this);
        spotifyActionRow.setOrientation(LinearLayout.HORIZONTAL);
        spotifyActionRow.setGravity(Gravity.CENTER_VERTICAL);
        settingsToolsPage.addView(spotifyActionRow, topMargin(matchWrap(), dp(12)));

        TextView spotifySaveButton = primaryButton(ui("button.spotify_save"));
        spotifySaveButton.setOnClickListener(view -> applySpotifySettingsFromUi());
        spotifyActionRow.addView(spotifySaveButton, weightedButtonParams(1f, dp(4)));

        settingsToolsPage.addView(sectionTitle(ui("section.lyrics_cache")), topMargin(matchWrap(), dp(24)));
        settingsToolsPage.addView(sectionDescription(ui("section.lyrics_cache_desc")), topMargin(matchWrap(), dp(8)));

        LinearLayout lyricsCacheRow = new LinearLayout(this);
        lyricsCacheRow.setOrientation(LinearLayout.HORIZONTAL);
        lyricsCacheRow.setGravity(Gravity.CENTER_VERTICAL);
        settingsToolsPage.addView(lyricsCacheRow, topMargin(matchWrap(), dp(12)));

        TextView clearCurrentLyricsCache = debugButton(ui("button.clear_current"));
        clearCurrentLyricsCache.setOnClickListener(view -> clearCurrentLyricsCacheFromSettings());
        lyricsCacheRow.addView(clearCurrentLyricsCache, weightedButtonParams(1f, dp(4)));

        TextView clearAllLyricsCache = debugButton(ui("button.clear_all"));
        clearAllLyricsCache.setOnClickListener(view -> clearAllLyricsCacheFromSettings());
        lyricsCacheRow.addView(clearAllLyricsCache, weightedButtonParams(1f, dp(4)));

        LinearLayout utilityRow = new LinearLayout(this);
        utilityRow.setOrientation(LinearLayout.HORIZONTAL);
        utilityRow.setGravity(Gravity.CENTER_VERTICAL);
        settingsToolsPage.addView(utilityRow, topMargin(matchWrap(), dp(12)));

        TextView clearCache = debugButton(ui("button.ai_cache_clear"));
        clearCache.setOnClickListener(view -> {
            if (aiLyricsRepository != null) {
                aiLyricsRepository.clearCache();
            }
            if (furiganaRepository != null) {
                furiganaRepository.clearCache();
            }
            aiSettingsStatusView.setText(ui("status.ai_cache_cleared"));
            showSavedToast(ui("toast.ai_cache_cleared"));
        });
        utilityRow.addView(clearCache, weightedButtonParams(1.15f, dp(4)));

        TextView debugButton = debugButton(ui("button.debug_log"));
        debugButton.setOnClickListener(view -> {
            showSettingsPanel(false);
            toggleDebugPanel();
        });
        utilityRow.addView(debugButton, weightedButtonParams(1f, dp(4)));

        switchSettingsTab(activeSettingsTab);
        populateAiSettingsUi();
        return panel;
    }

    private FrameLayout buildSpotifySetupPanel() {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {
                        Color.rgb(33, 35, 52),
                        Color.rgb(13, 14, 20)
                }
        ));
        panel.setVisibility(isInitialSetupComplete() ? View.GONE : View.VISIBLE);

        ScrollView scrollView = new ScrollView(this);
        spotifySetupScrollView = scrollView;
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        panel.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER_VERTICAL);
        outer.setPadding(dp(24), dp(48), dp(24), dp(34));
        scrollView.addView(outer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        ImageView appLogo = new ImageView(this);
        appLogo.setImageResource(R.drawable.ivlyrics_logo);
        appLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        clipRound(appLogo, 20);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(86), dp(86));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        outer.addView(appLogo, logoParams);

        TextView brand = label("ivLyrics", 15f, Color.argb(170, 255, 255, 255), AppFonts.semiBold(this));
        brand.setGravity(Gravity.CENTER);
        outer.addView(brand, topMargin(matchWrap(), dp(12)));

        onboardingWelcomeText = label("", 30f, Color.WHITE, AppFonts.bold(this));
        onboardingWelcomeText.setGravity(Gravity.CENTER);
        onboardingWelcomeText.setSingleLine(false);
        onboardingWelcomeText.setMaxLines(2);
        onboardingWelcomeText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams welcomeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(78)
        );
        welcomeParams.topMargin = dp(18);
        outer.addView(onboardingWelcomeText, welcomeParams);

        TextView subtitle = label(ui("onboarding.subtitle"), 13f, Color.argb(180, 255, 255, 255), AppFonts.regular(this));
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(2), 1f);
        outer.addView(subtitle, topMargin(matchWrap(), dp(10)));

        onboardingStepLabel = label("", 11f, Color.argb(145, 255, 255, 255), AppFonts.semiBold(this));
        onboardingStepLabel.setGravity(Gravity.CENTER);
        outer.addView(onboardingStepLabel, topMargin(matchWrap(), dp(30)));

        onboardingBody = new LinearLayout(this);
        onboardingBody.setOrientation(LinearLayout.VERTICAL);
        onboardingBody.setPadding(dp(2), 0, dp(2), 0);
        outer.addView(onboardingBody, topMargin(matchWrap(), dp(14)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        outer.addView(actionRow, topMargin(matchWrap(), dp(18)));

        onboardingBackButton = debugButton(ui("button.previous"));
        onboardingBackButton.setOnClickListener(view -> showOnboardingStep(onboardingStep - 1));
        actionRow.addView(onboardingBackButton, weightedButtonParams(1f, dp(4)));

        onboardingNextButton = primaryButton(ui("button.next"));
        onboardingNextButton.setOnClickListener(view -> handleOnboardingNextAction());
        actionRow.addView(onboardingNextButton, weightedButtonParams(1.2f, dp(4)));

        showOnboardingStep(initialOnboardingStep());
        updateOnboardingWelcomeText(false);
        populateSpotifyCredentialInputs(aiLyricsSettings == null ? null : aiLyricsSettings.snapshot());
        return panel;
    }

    private int initialOnboardingStep() {
        if (isSpotifyApiConfigured() && !NowPlayingService.isNotificationAccessEnabled(this)) {
            return 1;
        }
        return 0;
    }

    private void showOnboardingStep(int step) {
        onboardingStep = Math.max(0, Math.min(ONBOARDING_STEP_COUNT - 1, step));
        if (onboardingBody == null) {
            return;
        }
        onboardingBody.removeAllViews();
        if (onboardingStepLabel != null) {
            onboardingStepLabel.setText(uiFormat("onboarding.step_format", onboardingStep + 1, ONBOARDING_STEP_COUNT));
        }
        if (onboardingBackButton != null) {
            boolean canGoBack = onboardingStep > 0;
            onboardingBackButton.setEnabled(canGoBack);
            onboardingBackButton.setAlpha(canGoBack ? 1f : 0.45f);
        }
        if (onboardingNextButton != null) {
            boolean canGoNext = onboardingStep < ONBOARDING_STEP_COUNT - 1;
            onboardingNextButton.setEnabled(canGoNext);
            onboardingNextButton.setAlpha(canGoNext ? 1f : 0.42f);
            onboardingNextButton.setText(onboardingNextButtonText());
        }

        onboardingUiLanguageSelectButton = null;
        onboardingPermissionStatusView = null;
        if (onboardingStep == 0) {
            buildOnboardingWelcomeStep(onboardingBody);
        } else if (onboardingStep == 1) {
            buildOnboardingPermissionStep(onboardingBody);
        } else {
            buildOnboardingSpotifyStep(onboardingBody);
        }
    }

    private void handleOnboardingNextAction() {
        if (onboardingStep == 1 && !NowPlayingService.isNotificationAccessEnabled(this)) {
            openMediaPermissionSettings();
            return;
        }
        showOnboardingStep(onboardingStep + 1);
    }

    private String onboardingNextButtonText() {
        if (onboardingStep == 1) {
            return NowPlayingService.isNotificationAccessEnabled(this)
                    ? ui("button.spotify_setup")
                    : ui("button.open_permission");
        }
        if (onboardingStep >= ONBOARDING_STEP_COUNT - 1) {
            return ui("button.save_start");
        }
        return ui("button.next");
    }

    private void buildOnboardingWelcomeStep(LinearLayout body) {
        TextView title = sectionTitle(ui("onboarding.welcome_title"));
        title.setGravity(Gravity.CENTER);
        body.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView description = sectionDescription(ui("onboarding.welcome_desc"));
        description.setGravity(Gravity.CENTER);
        description.setTextColor(Color.argb(180, 255, 255, 255));
        body.addView(description, topMargin(matchWrap(), dp(10)));

        body.addView(buildOnboardingUiLanguageSelect(), topMargin(matchWrap(), dp(16)));

        LinearLayout preview = new LinearLayout(this);
        preview.setOrientation(LinearLayout.VERTICAL);
        preview.setPadding(dp(14), dp(14), dp(14), dp(14));
        preview.setBackground(roundDrawable(Color.argb(28, 255, 255, 255), dp(16)));
        body.addView(preview, topMargin(matchWrap(), dp(18)));

        TextView line1 = label(ui("onboarding.preview.line1"), 22f, Color.WHITE, AppFonts.bold(this));
        preview.addView(line1, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        TextView line2 = label(ui("onboarding.preview.line2"), 19f, Color.argb(110, 255, 255, 255), AppFonts.bold(this));
        preview.addView(line2, topMargin(matchWrap(), dp(14)));
        TextView line3 = label(ui("onboarding.preview.line3"), 15f, Color.argb(76, 255, 255, 255), AppFonts.semiBold(this));
        preview.addView(line3, topMargin(matchWrap(), dp(12)));
        TextView line4 = label(ui("onboarding.preview.line4"), 13f, Color.argb(128, 255, 255, 255), AppFonts.semiBold(this));
        line4.setLineSpacing(dp(2), 1f);
        preview.addView(line4, topMargin(matchWrap(), dp(10)));
    }

    private LinearLayout buildOnboardingUiLanguageSelect() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(12), dp(10));
        row.setBackground(roundDrawable(Color.argb(28, 255, 255, 255), dp(16)));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = label(ui("onboarding.app_language_en"), 12f, Color.WHITE, AppFonts.semiBold(this));
        labels.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = label(ui("onboarding.app_language_native"), 11f, Color.argb(140, 255, 255, 255), AppFonts.regular(this));
        labels.addView(subtitle, topMargin(matchWrap(), dp(3)));

        onboardingUiLanguageSelectButton = label("", 13f, Color.WHITE, AppFonts.semiBold(this));
        onboardingUiLanguageSelectButton.setGravity(Gravity.CENTER);
        onboardingUiLanguageSelectButton.setSingleLine(true);
        onboardingUiLanguageSelectButton.setEllipsize(TextUtils.TruncateAt.END);
        onboardingUiLanguageSelectButton.setPadding(dp(12), 0, dp(12), 0);
        onboardingUiLanguageSelectButton.setBackground(roundDrawable(Color.argb(44, 255, 255, 255), dp(12)));
        onboardingUiLanguageSelectButton.setOnClickListener(view -> showOnboardingUiLanguagePopup(onboardingUiLanguageSelectButton));
        row.addView(onboardingUiLanguageSelectButton, new LinearLayout.LayoutParams(dp(172), dp(42)));

        updateOnboardingUiLanguageSelect();
        return row;
    }

    private void buildOnboardingPermissionStep(LinearLayout body) {
        TextView title = sectionTitle(ui("onboarding.permission_title"));
        title.setGravity(Gravity.CENTER);
        body.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView description = sectionDescription(ui("onboarding.permission_desc"));
        description.setGravity(Gravity.CENTER);
        description.setTextColor(Color.argb(180, 255, 255, 255));
        body.addView(description, topMargin(matchWrap(), dp(10)));

        onboardingPermissionStatusView = label("", 13f, Color.WHITE, AppFonts.semiBold(this));
        onboardingPermissionStatusView.setGravity(Gravity.CENTER);
        onboardingPermissionStatusView.setLineSpacing(dp(2), 1f);
        onboardingPermissionStatusView.setPadding(dp(14), dp(13), dp(14), dp(13));
        onboardingPermissionStatusView.setBackground(roundDrawable(Color.argb(30, 255, 255, 255), dp(14)));
        body.addView(onboardingPermissionStatusView, topMargin(matchWrap(), dp(16)));

        TextView openButton = primaryButton(ui("button.open_permission"));
        openButton.setOnClickListener(view -> openMediaPermissionSettings());
        body.addView(openButton, topMargin(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ), dp(12)));

        TextView hint = sectionDescription(ui("onboarding.permission_hint"));
        hint.setGravity(Gravity.CENTER);
        body.addView(hint, topMargin(matchWrap(), dp(10)));
        updateOnboardingPermissionState();
    }

    private void buildOnboardingSpotifyStep(LinearLayout body) {
        TextView title = sectionTitle(ui("onboarding.spotify_title"));
        body.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView description = sectionDescription(ui("onboarding.spotify_desc"));
        body.addView(description, topMargin(matchWrap(), dp(8)));

        spotifySetupStatusView = label("", 12f, Color.argb(210, 255, 255, 255), AppFonts.semiBold(this));
        spotifySetupStatusView.setLineSpacing(dp(2), 1f);
        body.addView(spotifySetupStatusView, topMargin(matchWrap(), dp(14)));
        body.addView(buildSpotifyApiSetupInstructions(), topMargin(matchWrap(), dp(12)));

        spotifySetupClientIdInput = settingEditText("", false, false);
        attachSpotifySetupKeyboardScroll(spotifySetupClientIdInput);
        body.addView(settingField("Client ID", ui("field.spotify_client_id_desc"), spotifySetupClientIdInput), topMargin(matchWrap(), dp(14)));

        spotifySetupClientSecretInput = settingEditText("", false, true);
        attachSpotifySetupKeyboardScroll(spotifySetupClientSecretInput);
        body.addView(settingField("Client Secret", ui("field.spotify_client_secret_desc"), spotifySetupClientSecretInput), topMargin(matchWrap(), dp(12)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(actionRow, topMargin(matchWrap(), dp(18)));

        TextView saveButton = primaryButton(ui("button.save_start"));
        saveButton.setOnClickListener(view -> applySpotifySetupFromRequiredPanel());
        actionRow.addView(saveButton, weightedButtonParams(1f, dp(4)));

        populateSpotifyCredentialInputs(aiLyricsSettings == null ? null : aiLyricsSettings.snapshot());
    }

    private void attachSpotifySetupKeyboardScroll(EditText input) {
        if (input == null) {
            return;
        }
        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                scrollSpotifySetupInputIntoView(view);
            }
        });
        input.setOnClickListener(this::scrollSpotifySetupInputIntoView);
    }

    private void scrollSpotifySetupInputIntoView(View target) {
        if (spotifySetupScrollView == null || target == null) {
            return;
        }
        target.postDelayed(() -> scrollSpotifySetupInputIntoViewNow(target), 180L);
        target.postDelayed(() -> scrollSpotifySetupInputIntoViewNow(target), 420L);
    }

    private void scrollSpotifySetupInputIntoViewNow(View target) {
        if (spotifySetupScrollView == null || target == null || target.getWindowToken() == null) {
            return;
        }

        int scrollY = spotifySetupScrollView.getScrollY();
        int targetTop = verticalOffsetInSpotifySetupScroll(target);
        int targetBottom = targetTop + target.getHeight();
        int visibleBottom = scrollY + spotifySetupScrollView.getHeight() - spotifySetupScrollView.getPaddingBottom();
        int topTarget = Math.max(0, targetTop - dp(28));
        int bottomTarget = targetBottom + dp(110);

        if (bottomTarget > visibleBottom) {
            spotifySetupScrollView.smoothScrollTo(0, Math.max(0, bottomTarget - spotifySetupScrollView.getHeight()));
        } else if (topTarget < scrollY) {
            spotifySetupScrollView.smoothScrollTo(0, topTarget);
        }
    }

    private int verticalOffsetInSpotifySetupScroll(View target) {
        int top = 0;
        View current = target;
        while (current != null && current != spotifySetupScrollView) {
            top += current.getTop();
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return top;
    }

    private void updateOnboardingUiLanguageSelect() {
        if (onboardingUiLanguageSelectButton == null || aiLyricsSettings == null) {
            return;
        }
        String lang = aiLyricsSettings.snapshot().uiLang;
        onboardingUiLanguageSelectButton.setText(AppI18n.label(lang) + "  v");
    }

    private void showOnboardingUiLanguagePopup(View anchor) {
        if (anchor == null || aiLyricsSettings == null) {
            return;
        }
        String selected = aiLyricsSettings.snapshot().uiLang;
        showLanguageSelectPopup(anchor, uiLanguageChoices(), selected, code -> {
            aiLyricsSettings.setUiLang(code);
            applyUiLanguageChange();
            showSavedToast(ui("toast.ui_language_saved"));
        });
    }

    private void showLanguageSelectPopup(
            View anchor,
            List<LanguageChoice> choices,
            String selected,
            ChoiceHandler handler
    ) {
        if (anchor == null || choices == null || choices.isEmpty() || handler == null) {
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), dp(8), dp(8), dp(8));
        content.setBackground(roundDrawable(Color.rgb(30, 32, 42), dp(14)));

        int visibleCount = Math.min(7, choices.size());
        PopupWindow popup = new PopupWindow(
                content,
                Math.max(anchor.getWidth(), dp(220)),
                Math.min(dp(320), dp(44) * visibleCount + dp(16)),
                true
        );
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(roundDrawable(Color.rgb(30, 32, 42), dp(14)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup.setElevation(dp(10));
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        for (LanguageChoice choice : choices) {
            boolean active = sameChoice(choice.code, selected);
            TextView item = label(choice.label, 13f,
                    active ? Color.rgb(12, 13, 17) : Color.WHITE,
                    AppFonts.semiBold(this));
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setSingleLine(true);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setPadding(dp(12), 0, dp(12), 0);
            item.setBackground(roundDrawable(
                    active ? Color.argb(238, 255, 255, 255) : Color.TRANSPARENT,
                    dp(10)
            ));
            item.setOnClickListener(view -> {
                popup.dismiss();
                handler.onChoice(choice.code);
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(42)
            );
            if (list.getChildCount() > 0) {
                params.topMargin = dp(4);
            }
            list.addView(item, params);
        }

        popup.showAsDropDown(anchor, 0, dp(6));
    }

    private void applyUiLanguageChange() {
        boolean wasSettingsVisible = isSettingsPanelVisible();
        boolean wasDebugVisible = debugPanel != null && debugPanel.getVisibility() == View.VISIBLE;
        boolean wasLyricsVisible = lyricsPageVisible;
        String previousSettingsTab = activeSettingsTab;
        int previousOnboardingStep = onboardingStep;
        TrackSnapshot snapshot = currentTrack;
        LyricsResult lyricsResult = currentLyricsResult;
        Bitmap artwork = currentArtworkBitmap;
        String artworkKey = currentArtworkKey;
        boolean artworkFromSpotify = currentArtworkFromSpotify;

        destroyInAppBrowserWebView();
        inAppBrowserVisible = false;
        inAppBrowserInitialUrl = "";
        setContentView(buildContentView());
        activeSettingsTab = normalizeSettingsTab(previousSettingsTab);
        switchSettingsTab(activeSettingsTab);
        applySystemBarsForOrientation();
        AiLyricsSettings.Snapshot settingsSnapshot = aiLyricsSettings.snapshot();
        applyKeepScreenOnSetting(settingsSnapshot);
        applyBackgroundSettings(settingsSnapshot);
        applyTypographySettings(settingsSnapshot);
        applySpeakerColorSettings(settingsSnapshot);
        updatePermissionState();

        currentTrack = snapshot;
        currentLyricsResult = lyricsResult == null ? LyricsResult.empty(ui("status.lyrics_waiting")) : lyricsResult;
        currentArtworkKey = artworkKey == null ? "" : artworkKey;
        currentArtworkFromSpotify = artworkFromSpotify;
        updateArtwork(artwork, currentArtworkKey);
        restoreNowPlayingViewsAfterUiLanguageChange();
        syncYouTubeBackgroundState();

        if (!isInitialSetupComplete()) {
            onboardingStep = Math.max(0, Math.min(ONBOARDING_STEP_COUNT - 1, previousOnboardingStep));
            showOnboardingStep(onboardingStep);
            updateSpotifySetupGate(false);
        } else {
            updateSpotifySetupGate(false);
            if (wasSettingsVisible) {
                showSettingsPanel(true);
            }
            if (wasDebugVisible && debugPanel != null) {
                debugPanel.setVisibility(View.VISIBLE);
            }
            if (wasLyricsVisible) {
                showLyricsPage(true);
            }
        }
        applyLandscapeControlsAutoHideSetting();
    }

    private void rebuildContentViewAfterConfigurationChange() {
        boolean wasSettingsVisible = isSettingsPanelVisible();
        boolean wasDebugVisible = debugPanel != null && debugPanel.getVisibility() == View.VISIBLE;
        boolean wasLyricsVisible = lyricsPageVisible;
        int previousOnboardingStep = onboardingStep;
        dismissLyricsMetaTip();
        cancelLyricsMetaLongPress();

        rebuildOrientationSensitivePages();
        applySystemBarsForOrientation();
        AiLyricsSettings.Snapshot settingsSnapshot = aiLyricsSettings.snapshot();
        applyKeepScreenOnSetting(settingsSnapshot);
        applyBackgroundSettings(settingsSnapshot);
        applyTypographySettings(settingsSnapshot);
        applySpeakerColorSettings(settingsSnapshot);
        updatePermissionState();
        updateArtwork(currentArtworkBitmap, currentArtworkKey);
        restoreNowPlayingViewsAfterUiLanguageChange();
        if (currentLyricsResult != null && currentTrack != null && currentTrack.hasUsableMetadata()) {
            sourceView.setText(currentLyricsResult.providerLabel);
            statusView.setText(currentLyricsResult.detail);
        }
        syncYouTubeBackgroundState();

        if (!isInitialSetupComplete()) {
            onboardingStep = Math.max(0, Math.min(ONBOARDING_STEP_COUNT - 1, previousOnboardingStep));
            showOnboardingStep(onboardingStep);
            updateSpotifySetupGate(false);
        } else {
            updateSpotifySetupGate(false);
            if (wasSettingsVisible) {
                showSettingsPanel(true);
            }
            if (wasDebugVisible && debugPanel != null) {
                debugPanel.setVisibility(View.VISIBLE);
            }
            if (wasLyricsVisible && !isLandscapeLayout()) {
                showLyricsPage(true);
            } else if (isLandscapeLayout()) {
                lyricsPageVisible = false;
            }
        }
        applyLandscapeControlsAutoHideSetting();
    }

    private void rebuildOrientationSensitivePages() {
        if (rootView == null || mainPage == null || lyricsPage == null) {
            setContentView(buildContentView());
            return;
        }
        int mainIndex = rootView.indexOfChild(mainPage);
        if (mainIndex < 0) {
            mainIndex = Math.min(2, rootView.getChildCount());
        }
        rootView.removeView(mainPage);
        rootView.removeView(lyricsPage);

        mainPage = buildMainPage();
        rootView.addView(mainPage, Math.min(mainIndex, rootView.getChildCount()), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        lyricsPage = buildLyricsPage();
        lyricsPage.setVisibility(View.GONE);
        lyricsPageVisible = false;
        rootView.addView(lyricsPage, Math.min(mainIndex + 1, rootView.getChildCount()), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private void restoreNowPlayingViewsAfterUiLanguageChange() {
        if (currentTrack == null || !currentTrack.hasUsableMetadata()) {
            titleView.setText("ivLyrics");
            artistView.setText(ui("status.waiting_spotify"));
            lyricsTitleView.setText("ivLyrics");
            lyricsArtistView.setText(ui("status.waiting_spotify"));
            sourceView.setText("");
            statusView.setText("");
            debugProgressView.setText("0:00 / 0:00");
            setLyricsTrackDurationOnViews(0L);
            setLyricsResultOnViews(currentLyricsResult);
            setLyricsSupplementLoading(false, false);
            updateLyricPreview(0L);
            return;
        }
        updateTrackMetadataTextViews(currentTrack);
        long playerPosition = currentPlaybackPosition(currentTrack);
        updateProgressViews(playerPosition, currentTrack.durationMs);
        long lyricsPosition = lyricsPlaybackPosition(playerPosition, currentTrack.durationMs);
        setLyricsTrackDurationOnViews(currentTrack.durationMs);
        setLyricsPlaybackPositionOnViews(lyricsPosition);
        setLyricsResultOnViews(currentLyricsResult);
        setLyricsSupplementLoading(lyricsSupplementPronunciationLoading, lyricsSupplementTranslationLoading, lyricsSupplementFuriganaLoading);
        updateLyricPreview(lyricsPosition);
        playPauseButton.setPlaying(currentTrack.playing);
        updateLyricsLanguageSettingsUi();
        updateLyricsSyncSettingsUi();
        updateVideoSyncSettingsUi();
    }

    private List<LanguageChoice> languageChoices(boolean includeAuto) {
        List<LanguageChoice> choices = new ArrayList<>();
        if (includeAuto) {
            choices.add(new LanguageChoice("auto", ui("label.auto")));
        }
        for (AiLyricsSettings.Language language : AiLyricsSettings.SUPPORTED_LANGUAGES) {
            choices.add(new LanguageChoice(language.code, language.nativeName));
        }
        return choices;
    }

    private List<LanguageChoice> uiLanguageChoices() {
        List<LanguageChoice> choices = new ArrayList<>();
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            choices.add(new LanguageChoice(language.code, language.nativeName + " · " + language.name));
        }
        return choices;
    }

    private void startOnboardingWelcomeRotation() {
        handler.removeCallbacks(onboardingWelcomeTicker);
        if (onboardingWelcomeText == null || !isSpotifySetupPanelVisible()) {
            return;
        }
        if (onboardingWelcomeIndex < 0) {
            updateOnboardingWelcomeText(false);
        }
        handler.postDelayed(onboardingWelcomeTicker, 1850L);
    }

    private void stopOnboardingWelcomeRotation() {
        handler.removeCallbacks(onboardingWelcomeTicker);
    }

    private void updateOnboardingWelcomeText(boolean animate) {
        if (onboardingWelcomeText == null) {
            return;
        }
        onboardingWelcomeIndex = (onboardingWelcomeIndex + 1) % ONBOARDING_WELCOME_MESSAGES.length;
        String nextText = ONBOARDING_WELCOME_MESSAGES[onboardingWelcomeIndex];
        if (!animate) {
            onboardingWelcomeText.animate().cancel();
            onboardingWelcomeText.setAlpha(1f);
            onboardingWelcomeText.setTranslationY(0f);
            onboardingWelcomeText.setText(nextText);
            return;
        }
        onboardingWelcomeText.animate().cancel();
        onboardingWelcomeText.animate()
                .alpha(0f)
                .translationY(-dp(6))
                .setDuration(160L)
                .withEndAction(() -> {
                    onboardingWelcomeText.setText(nextText);
                    onboardingWelcomeText.setTranslationY(dp(8));
                    onboardingWelcomeText.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(240L)
                            .start();
                })
                .start();
    }

    private LinearLayout settingsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setVisibility(View.GONE);
        return page;
    }

    private void buildSettingsTabs() {
        if (settingsTabButtonsContainer == null) {
            return;
        }
        settingsTabButtonsContainer.removeAllViews();
        addSettingsTabButton(SETTINGS_TAB_LYRICS, ui("tab.lyrics"));
        addSettingsTabButton(SETTINGS_TAB_DISPLAY, ui("tab.display"));
        addSettingsTabButton(SETTINGS_TAB_AI, ui("tab.ai"));
        addSettingsTabButton(SETTINGS_TAB_TOOLS, ui("tab.tools"));
        updateSettingsTabButtons();
    }

    private void addSettingsTabButton(String tabId, String text) {
        TextView button = label(text, 12f, Color.WHITE, AppFonts.semiBold(this));
        button.setTag(tabId);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setOnClickListener(view -> switchSettingsTab(tabId));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        if (settingsTabButtonsContainer.getChildCount() > 0) {
            params.leftMargin = dp(8);
        }
        settingsTabButtonsContainer.addView(button, params);
    }

    private void switchSettingsTab(String tabId) {
        String next = normalizeSettingsTab(tabId);
        boolean changed = !next.equals(activeSettingsTab);
        activeSettingsTab = next;
        setSettingsPageVisibility(settingsLyricsPage, SETTINGS_TAB_LYRICS.equals(next));
        setSettingsPageVisibility(settingsDisplayPage, SETTINGS_TAB_DISPLAY.equals(next));
        setSettingsPageVisibility(settingsAiPage, SETTINGS_TAB_AI.equals(next));
        setSettingsPageVisibility(settingsToolsPage, SETTINGS_TAB_TOOLS.equals(next));
        updateSettingsTabButtons();
        if (changed && settingsScrollView != null) {
            settingsScrollView.scrollTo(0, 0);
        }
    }

    private void setSettingsPageVisibility(View page, boolean visible) {
        if (page != null) {
            page.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void updateSettingsTabButtons() {
        if (settingsTabButtonsContainer == null) {
            return;
        }
        for (int index = 0; index < settingsTabButtonsContainer.getChildCount(); index++) {
            View child = settingsTabButtonsContainer.getChildAt(index);
            if (!(child instanceof TextView)) {
                continue;
            }
            TextView button = (TextView) child;
            boolean selected = activeSettingsTab.equals(child.getTag());
            button.setTextColor(selected ? Color.rgb(12, 13, 17) : Color.WHITE);
            button.setBackground(roundDrawable(
                    selected ? Color.argb(238, 255, 255, 255) : Color.argb(34, 255, 255, 255),
                    dp(12)
            ));
        }
    }

    private String normalizeSettingsTab(String tabId) {
        if (SETTINGS_TAB_DISPLAY.equals(tabId)
                || SETTINGS_TAB_AI.equals(tabId)
                || SETTINGS_TAB_TOOLS.equals(tabId)) {
            return tabId;
        }
        return SETTINGS_TAB_LYRICS;
    }

    private TextView sectionTitle(String text) {
        return label(text, 17f, Color.WHITE, AppFonts.bold(this));
    }

    private TextView sectionDescription(String text) {
        TextView view = label(text, 12f, Color.argb(160, 255, 255, 255), AppFonts.regular(this));
        view.setLineSpacing(dp(2), 1f);
        return view;
    }

    private LinearLayout buildSpotifyApiSetupInstructions() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(14), dp(12), dp(14), dp(12));
        group.setBackground(roundDrawable(Color.argb(30, 255, 255, 255), dp(12)));

        TextView stepCounter = label("", 11f, Color.argb(150, 255, 255, 255), AppFonts.semiBold(this));
        group.addView(stepCounter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = label("", 15f, Color.WHITE, AppFonts.bold(this));
        group.addView(title, topMargin(matchWrap(), dp(6)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        group.addView(body, topMargin(matchWrap(), dp(10)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        group.addView(nav, topMargin(matchWrap(), dp(12)));

        TextView previousButton = debugButton(ui("button.previous"));
        nav.addView(previousButton, weightedButtonParams(1f, dp(4)));

        TextView nextButton = primaryButton(ui("button.next"));
        nav.addView(nextButton, weightedButtonParams(1f, dp(4)));

        final int stepCount = 6;
        final int[] currentStep = {0};
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            body.removeAllViews();
            int step = currentStep[0];
            stepCounter.setText(uiFormat("onboarding.step_format", step + 1, stepCount));
            switch (step) {
                case 0:
                    title.setText(ui("spotify.step0.title"));
                    addSpotifyInstructionText(body, ui("spotify.step0.desc"));
                    body.addView(copyableInstructionRow("Dashboard URL", "https://developer.spotify.com/dashboard"), topMargin(matchWrap(), dp(10)));
                    TextView openButton = debugButton(ui("button.open_browser"));
                    openButton.setOnClickListener(view -> startActivity(new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://developer.spotify.com/dashboard")
                    )));
                    body.addView(openButton, topMargin(matchWrap(), dp(8)));
                    break;
                case 1:
                    title.setText(ui("spotify.step1.title"));
                    addSpotifyInstructionText(body, ui("spotify.step1.desc"));
                    body.addView(copyableInstructionRow("App name", "trackinfo"), topMargin(matchWrap(), dp(10)));
                    break;
                case 2:
                    title.setText(ui("spotify.step2.title"));
                    addSpotifyInstructionText(body, ui("spotify.step2.desc"));
                    body.addView(copyableInstructionRow("App description", "trackinfo"), topMargin(matchWrap(), dp(10)));
                    break;
                case 3:
                    title.setText(ui("spotify.step3.title"));
                    addSpotifyInstructionText(body, ui("spotify.step3.desc"));
                    body.addView(copyableInstructionRow("Redirect URIs", "https://localhost/"), topMargin(matchWrap(), dp(10)));
                    break;
                case 4:
                    title.setText(ui("spotify.step4.title"));
                    addSpotifyInstructionText(body, ui("spotify.step4.desc"));
                    break;
                default:
                    title.setText(ui("spotify.step5.title"));
                    addSpotifyInstructionText(body, ui("spotify.step5.desc"));
                    break;
            }
            previousButton.setEnabled(step > 0);
            previousButton.setAlpha(step > 0 ? 1f : 0.45f);
            nextButton.setText(step == stepCount - 1 ? ui("button.restart") : ui("button.next"));
        };
        previousButton.setOnClickListener(view -> {
            if (currentStep[0] > 0) {
                currentStep[0]--;
                refresh[0].run();
            }
        });
        nextButton.setOnClickListener(view -> {
            currentStep[0] = currentStep[0] >= stepCount - 1 ? 0 : currentStep[0] + 1;
            refresh[0].run();
        });
        refresh[0].run();
        return group;
    }

    private void addSpotifyInstructionText(LinearLayout parent, String text) {
        TextView view = label(text, 11f, Color.argb(170, 255, 255, 255), AppFonts.regular(this));
        view.setLineSpacing(dp(2), 1f);
        parent.addView(view, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private LinearLayout copyableInstructionRow(String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textColumn = new LinearLayout(this);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleView = label(title, 11f, Color.argb(145, 255, 255, 255), AppFonts.regular(this));
        textColumn.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView valueView = label(value, 13f, Color.WHITE, AppFonts.semiBold(this));
        valueView.setTextIsSelectable(true);
        textColumn.addView(valueView, topMargin(matchWrap(), dp(4)));

        TextView copyButton = debugButton(ui("button.copy"));
        copyButton.setTextSize(12f);
        copyButton.setOnClickListener(view -> copyTextToClipboard(title, value));
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dp(62), dp(36));
        copyParams.leftMargin = dp(8);
        row.addView(copyButton, copyParams);
        return row;
    }

    private void copyTextToClipboard(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            showSavedToast(uiFormat("toast.copied_format", value));
        }
    }

    private Switch settingSwitch(String title, String subtitle) {
        Switch view = new Switch(this);
        view.setText(subtitle == null || subtitle.trim().isEmpty()
                ? title
                : title + "\n" + subtitle);
        view.setTextColor(Color.WHITE);
        view.setTextSize(14f);
        view.setTypeface(AppFonts.semiBold(this));
        view.setPadding(dp(14), dp(12), dp(14), dp(12));
        view.setBackground(roundDrawable(Color.argb(34, 255, 255, 255), dp(12)));
        view.setLineSpacing(dp(3), 1f);
        return view;
    }

    private LinearLayout settingField(String title, String subtitle, EditText input) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        field.setBackground(roundDrawable(Color.argb(30, 255, 255, 255), dp(12)));

        TextView label = label(title, 13f, Color.WHITE, AppFonts.semiBold(this));
        field.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView helper = label(subtitle, 11f, Color.argb(150, 255, 255, 255), AppFonts.regular(this));
            helper.setLineSpacing(dp(2), 1f);
            field.addView(helper, topMargin(matchWrap(), dp(5)));
        }

        field.addView(input, topMargin(matchWrap(), dp(9)));
        return field;
    }

    private LinearLayout settingGroup(String title, String subtitle, View body) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(dp(14), dp(12), dp(14), dp(12));
        field.setBackground(roundDrawable(Color.argb(30, 255, 255, 255), dp(12)));

        TextView label = label(title, 13f, Color.WHITE, AppFonts.semiBold(this));
        field.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView helper = label(subtitle, 11f, Color.argb(150, 255, 255, 255), AppFonts.regular(this));
            helper.setLineSpacing(dp(2), 1f);
            field.addView(helper, topMargin(matchWrap(), dp(5)));
        }

        field.addView(body, topMargin(matchWrap(), dp(10)));
        return field;
    }

    private LinearLayout buildSliderRow(SeekBar seekBar, TextView valueView) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(seekBar, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = dp(8);
        container.addView(valueView, valueParams);
        return container;
    }

    private LinearLayout buildTypographySettingsList() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (AiLyricsSettings.TypographySlot slot : AiLyricsSettings.TYPOGRAPHY_SLOTS) {
            View control = buildTypographySlotControl(slot);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (list.getChildCount() > 0) {
                params.topMargin = dp(10);
            }
            list.addView(control, params);
        }
        return list;
    }

    private LinearLayout buildSpeakerColorSettingsList() {
        speakerColorValueViews.clear();
        speakerColorSwatches.clear();

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackground(roundDrawable(Color.argb(30, 255, 255, 255), dp(12)));
        body.setPadding(dp(12), dp(12), dp(12), dp(12));

        AiLyricsSettings.SpeakerColorSettings settings = aiLyricsSettings == null
                ? AiLyricsSettings.SpeakerColorSettings.defaults()
                : aiLyricsSettings.snapshot().speakerColors;
        for (AiLyricsSettings.SpeakerColorSlot slot : AiLyricsSettings.SPEAKER_COLOR_SLOTS) {
            LinearLayout row = buildSpeakerColorRow(slot, settings.hex(slot.id));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (body.getChildCount() > 0) {
                params.topMargin = dp(9);
            }
            body.addView(row, params);
        }

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView resetButton = debugButton(ui("button.reset_colors"));
        resetButton.setOnClickListener(view -> resetSpeakerColorSettingsFromUi());
        actionRow.addView(resetButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        body.addView(actionRow, topMargin(matchWrap(), dp(14)));

        return body;
    }

    private LinearLayout buildSpeakerColorRow(AiLyricsSettings.SpeakerColorSlot slot, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        View swatch = new View(this);
        swatch.setBackground(roundDrawable(parseColor(value, slot.defaultColorInt()), dp(10)));
        row.addView(swatch, new LinearLayout.LayoutParams(dp(36), dp(36)));
        speakerColorSwatches.put(slot.id, swatch);

        TextView title = label(speakerColorSlotLabel(slot), 13f, Color.WHITE, AppFonts.semiBold(this));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(10);
        row.addView(title, titleParams);

        TextView valueView = colorValueButton(value);
        row.addView(valueView, new LinearLayout.LayoutParams(dp(104), dp(42)));
        speakerColorValueViews.put(slot.id, valueView);

        View.OnClickListener pickerListener = view -> showSpeakerColorPicker(slot);
        row.setOnClickListener(pickerListener);
        swatch.setOnClickListener(pickerListener);
        title.setOnClickListener(pickerListener);
        valueView.setOnClickListener(pickerListener);
        return row;
    }

    private LinearLayout buildBackgroundSolidColorControl() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(2), 0, 0);

        String color = aiLyricsSettings == null
                ? "#1e3a8a"
                : aiLyricsSettings.snapshot().background.solidColor;
        backgroundSolidColorSwatch = new View(this);
        backgroundSolidColorSwatch.setBackground(roundDrawable(parseColor(color, Color.rgb(30, 58, 138)), dp(10)));
        row.addView(backgroundSolidColorSwatch, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView label = label(ui("speaker_color.hex_hint"), 12f, Color.argb(160, 255, 255, 255), AppFonts.regular(this));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = dp(10);
        row.addView(label, labelParams);

        backgroundSolidColorValueView = colorValueButton(color);
        row.addView(backgroundSolidColorValueView, new LinearLayout.LayoutParams(dp(112), dp(42)));

        View.OnClickListener pickerListener = view -> showBackgroundSolidColorPicker();
        row.setOnClickListener(pickerListener);
        backgroundSolidColorSwatch.setOnClickListener(pickerListener);
        label.setOnClickListener(pickerListener);
        backgroundSolidColorValueView.setOnClickListener(pickerListener);
        return row;
    }

    private TextView colorValueButton(String color) {
        TextView value = label(color, 12f, Color.WHITE, AppFonts.semiBold(this));
        value.setGravity(Gravity.CENTER);
        value.setMinHeight(dp(42));
        value.setBackground(roundDrawable(Color.argb(38, 255, 255, 255), dp(9)));
        value.setPadding(dp(8), 0, dp(8), 0);
        return value;
    }

    private void showSpeakerColorPicker(AiLyricsSettings.SpeakerColorSlot slot) {
        if (aiLyricsSettings == null || slot == null) {
            return;
        }
        String color = aiLyricsSettings.snapshot().speakerColors.hex(slot.id);
        showColorPickerDialog(
                speakerColorSlotLabel(slot),
                color,
                slot.defaultColorInt(),
                selectedColor -> saveSpeakerColor(slot, hexColor(selectedColor), true)
        );
    }

    private void showBackgroundSolidColorPicker() {
        if (aiLyricsSettings == null) {
            return;
        }
        String color = aiLyricsSettings.snapshot().background.solidColor;
        showColorPickerDialog(
                ui("field.solid_color"),
                color,
                Color.rgb(30, 58, 138),
                selectedColor -> {
                    aiLyricsSettings.setBackgroundSolidColor(hexColor(selectedColor));
                    AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
                    updateBackgroundSettingsUi(snapshot, false);
                    applyBackgroundSettings(snapshot);
                    showSavedToast(ui("toast.background_saved"));
                }
        );
    }

    private void showColorPickerDialog(String title, String initialHex, int fallbackColor, ColorPickedCallback callback) {
        int initialColor = parseColor(initialHex, fallbackColor);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(14), dp(18), dp(6));

        ColorPickerView picker = new ColorPickerView(this);
        picker.setColor(initialColor);
        content.addView(picker, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(286)));

        LinearLayout previewRow = new LinearLayout(this);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);
        View swatch = new View(this);
        swatch.setBackground(roundDrawable(initialColor, dp(10)));
        previewRow.addView(swatch, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView value = colorValueButton(hexColor(initialColor));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        valueParams.leftMargin = dp(10);
        previewRow.addView(value, valueParams);
        content.addView(previewRow, topMargin(matchWrap(), dp(12)));

        picker.setOnColorChangedListener(color -> {
            swatch.setBackground(roundDrawable(color, dp(10)));
            value.setText(hexColor(color));
        });

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setView(content)
                .setNegativeButton(ui("button.close"), null)
                .setPositiveButton(ui("button.apply_colors"), (dialogInterface, which) -> {
                    if (callback != null) {
                        callback.onColorPicked(picker.getColor());
                    }
                })
                .create();
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(Color.rgb(37, 99, 235));
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.rgb(84, 91, 110));
        });
        dialog.show();
    }

    private interface ColorPickedCallback {
        void onColorPicked(int color);
    }

    private void saveSpeakerColor(AiLyricsSettings.SpeakerColorSlot changedSlot, String color, boolean showToast) {
        if (aiLyricsSettings == null || changedSlot == null) {
            return;
        }
        AiLyricsSettings.Snapshot current = aiLyricsSettings.snapshot();
        Map<String, String> colors = new LinkedHashMap<>();
        for (AiLyricsSettings.SpeakerColorSlot slot : AiLyricsSettings.SPEAKER_COLOR_SLOTS) {
            String value = slot.id.equals(changedSlot.id) ? color : current.speakerColors.hex(slot.id);
            colors.put(slot.id, AiLyricsSettings.isHexColor(value) ? value : slot.defaultColor);
        }
        aiLyricsSettings.setSpeakerColors(colors);
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
        updateSpeakerColorSettingsUi(snapshot);
        applySpeakerColorSettings(snapshot);
        if (showToast) {
            showSavedToast(ui("toast.speaker_colors_saved"));
        }
    }

    private void resetSpeakerColorSettingsFromUi() {
        if (aiLyricsSettings == null) {
            return;
        }
        aiLyricsSettings.resetSpeakerColors();
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
        updateSpeakerColorSettingsUi(snapshot);
        applySpeakerColorSettings(snapshot);
        showSavedToast(ui("toast.speaker_colors_reset"));
    }

    private void updateSpeakerColorSettingsUi(AiLyricsSettings.Snapshot snapshot) {
        AiLyricsSettings.SpeakerColorSettings settings = snapshot == null
                ? AiLyricsSettings.SpeakerColorSettings.defaults()
                : snapshot.speakerColors;
        for (AiLyricsSettings.SpeakerColorSlot slot : AiLyricsSettings.SPEAKER_COLOR_SLOTS) {
            String color = settings.hex(slot.id);
            TextView valueView = speakerColorValueViews.get(slot.id);
            if (valueView != null) {
                valueView.setText(color);
            }
            updateSpeakerColorSwatch(slot.id, color);
        }
    }

    private void updateSpeakerColorSwatch(String slotId, String color) {
        View swatch = speakerColorSwatches.get(slotId);
        if (swatch == null) {
            return;
        }
        AiLyricsSettings.SpeakerColorSlot slot = AiLyricsSettings.speakerColorSlotById(slotId);
        int parsed = parseColor(
                AiLyricsSettings.isHexColor(color) ? color : slot.defaultColor,
                slot.defaultColorInt()
        );
        swatch.setBackground(roundDrawable(parsed, dp(10)));
    }

    private String speakerColorSlotLabel(AiLyricsSettings.SpeakerColorSlot slot) {
        if (slot == null) {
            return "";
        }
        if (AiLyricsSettings.SPEAKER_COLOR_NORMAL.equals(slot.id)) {
            return ui(slot.titleKey);
        }
        int number = trailingNumber(slot.id);
        return number > 0 ? ui(slot.titleKey) + " " + number : ui(slot.titleKey);
    }

    private int trailingNumber(String value) {
        String text = value == null ? "" : value.trim();
        int end = text.length();
        int start = end;
        while (start > 0 && Character.isDigit(text.charAt(start - 1))) {
            start--;
        }
        if (start >= end) {
            return -1;
        }
        try {
            return Integer.parseInt(text.substring(start, end));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private int parseColor(String color, int fallback) {
        try {
            return Color.parseColor(color);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String hexColor(int color) {
        return String.format(Locale.ROOT, "#%06x", color & 0x00ffffff);
    }

    private LinearLayout buildTypographySlotControl(AiLyricsSettings.TypographySlot slot) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView sizeValue = label("", 12f, Color.argb(180, 255, 255, 255), AppFonts.semiBold(this));
        SeekBar sizeSeekBar = new SeekBar(this);
        sizeSeekBar.setMax(90);
        AiLyricsSettings.TypographyStyle initial = aiLyricsSettings.snapshot().typography.style(slot.id);
        sizeSeekBar.setProgress(initial.sizePercent - 70);
        sizeValue.setText(initial.sizePercent + "%");
        sizeSeekBar.setOnSeekBarChangeListener(new SimpleSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || suppressSettingsEvents || aiLyricsSettings == null) {
                    return;
                }
                AiLyricsSettings.TypographyStyle current = aiLyricsSettings.snapshot().typography.style(slot.id);
                int sizePercent = progress + 70;
                aiLyricsSettings.setTypographyStyle(slot.id, sizePercent, current.weight);
                sizeValue.setText(sizePercent + "%");
                applyTypographySettings(aiLyricsSettings.snapshot());
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                showSavedToast(ui("toast.typography_saved"));
            }
        });

        body.addView(settingSubLabel(ui("typography.size")), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        body.addView(buildSliderRow(sizeSeekBar, sizeValue), topMargin(matchWrap(), dp(4)));

        TextView weightLabel = settingSubLabel(ui("typography.weight"));
        body.addView(weightLabel, topMargin(matchWrap(), dp(10)));
        LinearLayout weightButtons = new LinearLayout(this);
        weightButtons.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(weightButtons, topMargin(matchWrap(), dp(7)));
        rebuildTypographyWeightButtons(weightButtons, slot);

        return settingGroup(ui(slot.titleKey), ui(slot.descriptionKey), body);
    }

    private TextView settingSubLabel(String text) {
        return label(text, 11f, Color.argb(155, 255, 255, 255), AppFonts.semiBold(this));
    }

    private void rebuildTypographyWeightButtons(LinearLayout container, AiLyricsSettings.TypographySlot slot) {
        if (container == null || aiLyricsSettings == null) {
            return;
        }
        container.removeAllViews();
        String selected = aiLyricsSettings.snapshot().typography.style(slot.id).weight;
        String[] weights = {
                AiLyricsSettings.TYPO_WEIGHT_REGULAR,
                AiLyricsSettings.TYPO_WEIGHT_SEMIBOLD,
                AiLyricsSettings.TYPO_WEIGHT_BOLD
        };
        for (int index = 0; index < weights.length; index++) {
            String weight = weights[index];
            TextView button = languageButton(typographyWeightLabel(weight), weight.equals(selected));
            button.setOnClickListener(view -> {
                AiLyricsSettings.TypographyStyle current = aiLyricsSettings.snapshot().typography.style(slot.id);
                aiLyricsSettings.setTypographyStyle(slot.id, current.sizePercent, weight);
                applyTypographySettings(aiLyricsSettings.snapshot());
                rebuildTypographyWeightButtons(container, slot);
                showSavedToast(ui("toast.typography_saved"));
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
            if (index > 0) {
                params.leftMargin = dp(7);
            }
            container.addView(button, params);
        }
    }

    private String typographyWeightLabel(String weight) {
        String normalized = AiLyricsSettings.normalizeTypographyWeight(weight);
        if (AiLyricsSettings.TYPO_WEIGHT_REGULAR.equals(normalized)) {
            return ui("typography.weight.regular");
        }
        if (AiLyricsSettings.TYPO_WEIGHT_BOLD.equals(normalized)) {
            return ui("typography.weight.bold");
        }
        return ui("typography.weight.semibold");
    }

    private EditText settingEditText(String hint, boolean multiLine, boolean secret) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(Color.argb(110, 255, 255, 255));
        input.setTextColor(Color.WHITE);
        input.setTextSize(13f);
        input.setTypeface(AppFonts.regular(this));
        input.setSingleLine(!multiLine);
        input.setMinHeight(dp(multiLine ? 72 : 42));
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(roundDrawable(Color.argb(38, 255, 255, 255), dp(9)));
        int type = InputType.TYPE_CLASS_TEXT;
        if (multiLine) {
            type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            input.setGravity(Gravity.TOP | Gravity.LEFT);
            input.setMinLines(2);
            input.setMaxLines(4);
            input.setPadding(dp(12), dp(10), dp(12), dp(10));
        }
        if (secret) {
            type |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
        }
        input.setInputType(type);
        return input;
    }

    private TextView primaryButton(String label) {
        TextView view = label(label, 13f, Color.rgb(12, 13, 17), AppFonts.bold(this));
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundDrawable(Color.argb(238, 255, 255, 255), dp(12)));
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setMinHeight(dp(42));
        return view;
    }

    private void buildProviderButtons() {
        if (providerButtonsContainer == null) {
            return;
        }
        providerButtonsContainer.removeAllViews();
        LinearLayout row = null;
        for (int index = 0; index < AiLyricsSettings.PROVIDERS.size(); index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                if (providerButtonsContainer.getChildCount() > 0) {
                    rowParams.topMargin = dp(8);
                }
                providerButtonsContainer.addView(row, rowParams);
            }
            AiLyricsSettings.Provider provider = AiLyricsSettings.PROVIDERS.get(index);
            TextView button = providerButton(provider);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1f);
            params.leftMargin = index % 2 == 0 ? 0 : dp(8);
            row.addView(button, params);
        }
        updateProviderButtons();
    }

    private TextView providerButton(AiLyricsSettings.Provider provider) {
        TextView button = label(provider.label, 12f, Color.WHITE, AppFonts.semiBold(this));
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setContentDescription(providerDescription(provider));
        button.setOnClickListener(view -> {
            applyAiSettingsFromUi(false);
            aiLyricsSettings.setProvider(provider.id);
            populateAiSettingsUi();
            updateProviderButtons();
            showSavedToast(ui("toast.provider_saved"));
        });
        return button;
    }

    private void updateProviderButtons() {
        if (providerButtonsContainer == null || aiLyricsSettings == null) {
            return;
        }
        String selectedId = aiLyricsSettings.snapshot().provider.id;
        for (int rowIndex = 0; rowIndex < providerButtonsContainer.getChildCount(); rowIndex++) {
            View rowView = providerButtonsContainer.getChildAt(rowIndex);
            if (!(rowView instanceof LinearLayout)) {
                continue;
            }
            LinearLayout row = (LinearLayout) rowView;
            for (int index = 0; index < row.getChildCount(); index++) {
                View child = row.getChildAt(index);
                if (!(child instanceof TextView)) {
                    continue;
                }
                TextView button = (TextView) child;
                String label = button.getText().toString();
                boolean selected = false;
                for (AiLyricsSettings.Provider provider : AiLyricsSettings.PROVIDERS) {
                    if (provider.label.equals(label) && provider.id.equals(selectedId)) {
                        selected = true;
                        break;
                    }
                }
                button.setTextColor(selected ? Color.rgb(12, 13, 17) : Color.WHITE);
                button.setBackground(roundDrawable(
                        selected ? Color.argb(238, 255, 255, 255) : Color.argb(34, 255, 255, 255),
                        dp(12)
                ));
            }
        }
    }

    private String providerDescription(AiLyricsSettings.Provider provider) {
        return provider == null ? "" : ui("provider.desc." + provider.id);
    }

    private String backgroundModeLabel(String modeId) {
        String normalized = AiLyricsSettings.normalizeBackgroundMode(modeId);
        if (AiLyricsSettings.BACKGROUND_MODE_BLUR_GRADIENT.equals(normalized)) {
            return ui("background.mode.blur_gradient");
        }
        if (AiLyricsSettings.BACKGROUND_MODE_VIDEO.equals(normalized)) {
            return ui("background.mode.video");
        }
        if (AiLyricsSettings.BACKGROUND_MODE_SOLID.equals(normalized)) {
            return ui("background.mode.solid");
        }
        return ui("background.mode.gradient");
    }

    private String backgroundModeDescription(String modeId) {
        String normalized = AiLyricsSettings.normalizeBackgroundMode(modeId);
        if (AiLyricsSettings.BACKGROUND_MODE_BLUR_GRADIENT.equals(normalized)) {
            return ui("background.mode.blur_gradient_desc");
        }
        if (AiLyricsSettings.BACKGROUND_MODE_VIDEO.equals(normalized)) {
            return ui("background.mode.video_desc");
        }
        if (AiLyricsSettings.BACKGROUND_MODE_SOLID.equals(normalized)) {
            return ui("background.mode.solid_desc");
        }
        return ui("background.mode.gradient_desc");
    }

    private void rebuildLanguageSettingsUi(AiLyricsSettings.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        updateUiLanguageSelect(snapshot.uiLang);
        updateOutputLanguageSelect(snapshot.outputLang);
        rebuildPreviewModeButtons(snapshot.previewItems);
        rebuildSourceLanguageButtons();
        populateSelectedLanguageRule(snapshot);
    }

    private void updateUiLanguageSelect(String selectedLang) {
        if (uiLanguageSelectButton == null) {
            return;
        }
        uiLanguageSelectButton.setText(AppI18n.label(selectedLang) + "  v");
    }

    private void showSettingsUiLanguagePopup(View anchor) {
        if (anchor == null || aiLyricsSettings == null) {
            return;
        }
        showLanguageSelectPopup(anchor, uiLanguageChoices(), aiLyricsSettings.snapshot().uiLang, code -> {
            aiLyricsSettings.setUiLang(code);
            applyUiLanguageChange();
            AiLyricsSettings.Snapshot nextSnapshot = aiLyricsSettings.snapshot();
            if (AiLyricsSettings.OUTPUT_LANG_SAME_UI.equalsIgnoreCase(nextSnapshot.outputLang)) {
                translatedTrackTitle = "";
                translatedTrackArtist = "";
                updateTrackMetadataTextViews(currentTrack);
                requestMetadataTranslation(true);
                requestAiLyrics(true);
            }
            showSavedToast(ui("toast.ui_language_saved"));
        });
    }

    private void updateOutputLanguageSelect(String selectedLang) {
        if (outputLanguageSelectButton == null) {
            return;
        }
        outputLanguageSelectButton.setText(outputLanguageSelectLabel(selectedLang) + "  v");
    }

    private void showSettingsOutputLanguagePopup(View anchor) {
        if (anchor == null || aiLyricsSettings == null) {
            return;
        }
        showLanguageSelectPopup(anchor, outputLanguageChoices(), aiLyricsSettings.snapshot().outputLang, code -> {
            aiLyricsSettings.setOutputLang(code);
            rebuildLanguageSettingsUi(aiLyricsSettings.snapshot());
            translatedTrackTitle = "";
            translatedTrackArtist = "";
            updateTrackMetadataTextViews(currentTrack);
            requestMetadataTranslation(true);
            requestAiLyrics(true);
            showSavedToast(ui("toast.pronunciation_language_saved"));
        });
    }

    private List<LanguageChoice> outputLanguageChoices() {
        List<LanguageChoice> choices = new ArrayList<>();
        choices.add(new LanguageChoice(AiLyricsSettings.OUTPUT_LANG_SAME_UI, ui("label.same_as_ui_language")));
        for (AiLyricsSettings.Language language : AiLyricsSettings.SUPPORTED_LANGUAGES) {
            choices.add(new LanguageChoice(language.code, language.nativeName + " · " + language.name));
        }
        return choices;
    }

    private String outputLanguageSelectLabel(String selectedLang) {
        if (AiLyricsSettings.OUTPUT_LANG_SAME_UI.equalsIgnoreCase(selectedLang)) {
            return ui("label.same_as_ui_language");
        }
        return AiLyricsSettings.languageLabel(selectedLang);
    }

    private void rebuildPreviewModeButtons(int selectedItems) {
        if (previewModeButtonsContainer == null) {
            return;
        }
        int normalized = AiLyricsSettings.normalizePreviewItems(selectedItems);
        previewModeButtonsContainer.removeAllViews();
        List<PreviewChoice> choices = new ArrayList<>();
        choices.add(new PreviewChoice(ui("preview.none"), AiLyricsSettings.PREVIEW_ITEM_NONE));
        choices.add(new PreviewChoice(ui("preview.original"), AiLyricsSettings.PREVIEW_ITEM_ORIGINAL));
        choices.add(new PreviewChoice(ui("preview.pronunciation"), AiLyricsSettings.PREVIEW_ITEM_PRONUNCIATION));
        choices.add(new PreviewChoice(ui("preview.translation"), AiLyricsSettings.PREVIEW_ITEM_TRANSLATION));
        LinearLayout row = null;
        for (int index = 0; index < choices.size(); index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                if (previewModeButtonsContainer.getChildCount() > 0) {
                    rowParams.topMargin = dp(8);
                }
                previewModeButtonsContainer.addView(row, rowParams);
            }
            PreviewChoice choice = choices.get(index);
            boolean selected = choice.item == AiLyricsSettings.PREVIEW_ITEM_NONE
                    ? normalized == AiLyricsSettings.PREVIEW_ITEM_NONE
                    : AiLyricsSettings.previewItemEnabled(normalized, choice.item);
            TextView button = languageButton(choice.label, selected);
            button.setOnClickListener(view -> {
                int current = aiLyricsSettings.snapshot().previewItems;
                int next;
                if (choice.item == AiLyricsSettings.PREVIEW_ITEM_NONE) {
                    next = AiLyricsSettings.PREVIEW_ITEM_NONE;
                } else {
                    next = current ^ choice.item;
                    next = AiLyricsSettings.normalizePreviewItems(next);
                }
                aiLyricsSettings.setPreviewItems(next);
                rebuildPreviewModeButtons(aiLyricsSettings.snapshot().previewItems);
                updateLyricPreview(currentTrack == null ? 0L : currentLyricsPlaybackPosition(currentTrack));
                showSavedToast(ui("toast.preview_saved"));
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
            params.leftMargin = index % 2 == 0 ? 0 : dp(8);
            row.addView(button, params);
        }
    }

    private void rebuildBackgroundModeButtons(String selectedMode) {
        if (backgroundModeButtonsContainer == null || aiLyricsSettings == null) {
            return;
        }
        String normalized = AiLyricsSettings.normalizeBackgroundMode(selectedMode);
        backgroundModeButtonsContainer.removeAllViews();
        LinearLayout row = null;
        for (int index = 0; index < AiLyricsSettings.BACKGROUND_MODES.size(); index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                if (backgroundModeButtonsContainer.getChildCount() > 0) {
                    rowParams.topMargin = dp(8);
                }
                backgroundModeButtonsContainer.addView(row, rowParams);
            }
            AiLyricsSettings.BackgroundMode mode = AiLyricsSettings.BACKGROUND_MODES.get(index);
            TextView button = languageButton(backgroundModeLabel(mode.id), mode.id.equals(normalized));
            button.setContentDescription(backgroundModeDescription(mode.id));
            button.setOnClickListener(view -> {
                aiLyricsSettings.setBackgroundMode(mode.id);
                AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
                updateBackgroundSettingsUi(snapshot, true);
                applyBackgroundSettings(snapshot);
                showSavedToast(ui("toast.background_saved"));
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
            params.leftMargin = index % 2 == 0 ? 0 : dp(8);
            row.addView(button, params);
        }
    }

    private void updateBackgroundSettingsUi(AiLyricsSettings.Snapshot snapshot, boolean rebuildModes) {
        if (snapshot == null) {
            return;
        }
        AiLyricsSettings.BackgroundSettings background = snapshot.background;
        if (rebuildModes) {
            rebuildBackgroundModeButtons(background.mode);
        }
        suppressSettingsEvents = true;
        if (backgroundBrightnessSeekBar != null) {
            backgroundBrightnessSeekBar.setProgress(background.brightness);
        }
        if (backgroundBlurSeekBar != null) {
            backgroundBlurSeekBar.setProgress(background.blur);
        }
        if (backgroundNoiseSwitch != null) {
            backgroundNoiseSwitch.setChecked(background.noise);
        }
        if (backgroundReduceMotionSwitch != null) {
            backgroundReduceMotionSwitch.setChecked(background.reduceMotion);
        }
        if (backgroundSolidColorValueView != null) {
            backgroundSolidColorValueView.setText(background.solidColor);
        }
        if (backgroundSolidColorSwatch != null) {
            backgroundSolidColorSwatch.setBackground(roundDrawable(parseColor(background.solidColor, Color.rgb(30, 58, 138)), dp(10)));
        }
        suppressSettingsEvents = false;
        if (backgroundBrightnessValueView != null) {
            backgroundBrightnessValueView.setText(background.brightness + "%");
        }
        if (backgroundBlurValueView != null) {
            backgroundBlurValueView.setText(background.blur + "%");
        }
    }

    private void applyBackgroundSettings(AiLyricsSettings.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        AiLyricsSettings.BackgroundSettings settings = snapshot.background;
        if (backgroundView != null) {
            backgroundView.setBackgroundSettings(settings);
        }
        boolean videoMode = AiLyricsSettings.BACKGROUND_MODE_VIDEO.equals(settings.mode);
        if (lyricsBackgroundView != null) {
            lyricsBackgroundView.setBackgroundSettings(settings);
            lyricsBackgroundView.setVisibility(videoMode ? View.GONE : View.VISIBLE);
        }
        if (youtubeBackgroundView != null) {
            youtubeBackgroundView.setBackgroundSettings(settings);
        }
        syncYouTubeBackgroundState();
    }

    private boolean isVideoBackgroundMode() {
        return aiLyricsSettings != null
                && AiLyricsSettings.BACKGROUND_MODE_VIDEO.equals(aiLyricsSettings.snapshot().background.mode);
    }

    private void syncYouTubeBackgroundState() {
        if (youtubeBackgroundView == null) {
            return;
        }
        boolean videoMode = isVideoBackgroundMode();
        youtubeBackgroundView.setVideoBackgroundEnabled(videoMode);
        if (!videoMode) {
            return;
        }
        if (currentYouTubeBackgroundInfo != null) {
            youtubeBackgroundView.loadVideo(currentYouTubeBackgroundInfo);
        } else {
            requestYouTubeBackgroundIfNeeded();
        }
        updateYouTubeBackgroundPlaybackState();
    }

    private void requestYouTubeBackgroundIfNeeded() {
        if (!isVideoBackgroundMode()
                || youtubeBackgroundRepository == null
                || currentTrack == null
                || !currentTrack.hasUsableMetadata()) {
            return;
        }
        LyricsResult lyricsResult = currentBaseLyricsResult == null ? LyricsResult.empty("") : currentBaseLyricsResult;
        String isrc = nonEmpty(lyricsResult.isrc, nonEmpty(currentResolvedIsrc, currentTrack.isrc));
        if (isrc.isEmpty()) {
            appendLog("youtube background: waiting for ISRC");
            return;
        }
        String trackId = nonEmpty(lyricsResult.spotifyTrackId, nonEmpty(currentResolvedSpotifyTrackId, currentTrack.trackId));
        String requestKey = "isrc:" + isrc;
        if (requestKey.equals(currentYouTubeBackgroundRequestKey)
                && (currentYouTubeBackgroundLoading || currentYouTubeBackgroundInfo != null)) {
            return;
        }
        currentYouTubeBackgroundRequestKey = requestKey;
        currentYouTubeBackgroundLoading = true;
        youtubeBackgroundRepository.load(requestKey, currentTrack, youtubeMetadataResult(lyricsResult, isrc, trackId), this);
    }

    private LyricsResult youtubeMetadataResult(LyricsResult source, String isrc, String spotifyTrackId) {
        LyricsResult safeSource = source == null ? LyricsResult.empty("") : source;
        String normalizedIsrc = TrackSnapshot.normalizeIsrc(isrc);
        String safeSpotifyTrackId = spotifyTrackId == null ? "" : spotifyTrackId.trim();
        if (normalizedIsrc.equals(safeSource.isrc)
                && safeSpotifyTrackId.equals(safeSource.spotifyTrackId)) {
            return safeSource;
        }
        return new LyricsResult(
                safeSource.lines,
                safeSource.providerLabel,
                safeSource.detail,
                safeSource.karaoke,
                normalizedIsrc,
                safeSpotifyTrackId,
                safeSource.contributors
        );
    }

    private boolean isCurrentYouTubeBackgroundRequest(String requestKey) {
        return requestKey != null && requestKey.equals(currentYouTubeBackgroundRequestKey);
    }

    private void resetYouTubeBackgroundForTrack() {
        currentYouTubeBackgroundInfo = null;
        currentYouTubeBackgroundLoading = false;
        currentYouTubeBackgroundRequestKey = "";
        if (youtubeBackgroundView != null) {
            youtubeBackgroundView.clearVideo();
            youtubeBackgroundView.setVideoBackgroundEnabled(isVideoBackgroundMode());
        }
    }

    private void updateYouTubeBackgroundPlaybackState() {
        if (youtubeBackgroundView == null
                || !isVideoBackgroundMode()
                || currentTrack == null
                || !currentTrack.hasUsableMetadata()) {
            return;
        }
        long position = currentPlaybackPosition(currentTrack);
        youtubeBackgroundView.setPlaybackState(
                position,
                currentTrack.playing,
                firstLyricTimeMs(currentBaseLyricsResult),
                currentTrackSyncOffsetMs + currentVideoSyncOffsetMs
        );
    }

    private long firstLyricTimeMs(LyricsResult result) {
        if (result == null || result.lines == null || result.lines.isEmpty()) {
            return 0L;
        }
        long best = Long.MAX_VALUE;
        for (LyricsLine line : result.lines) {
            if (line == null) {
                continue;
            }
            if (line.vocalParts != null && !line.vocalParts.isEmpty()) {
                for (LyricsLine.VocalPart part : line.vocalParts) {
                    if (part != null && part.startTimeMs >= 0L) {
                        best = Math.min(best, part.startTimeMs);
                    }
                }
            } else if (line.isTimed()) {
                best = Math.min(best, line.startTimeMs);
            }
        }
        return best == Long.MAX_VALUE ? 0L : best;
    }

    private void destroyYouTubeBackgroundView() {
        if (youtubeBackgroundView != null) {
            youtubeBackgroundView.destroy();
            youtubeBackgroundView = null;
        }
    }

    private void applyTypographySettings(AiLyricsSettings.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        AiLyricsSettings.TypographySettings typography = snapshot.typography;
        boolean landscape = isLandscapeLayout();
        applyTypographyToTextView(titleView, typography, AiLyricsSettings.TYPO_MAIN_TITLE, landscape ? 23f : 28f);
        applyTypographyToTextView(artistView, typography, AiLyricsSettings.TYPO_MAIN_ARTIST, landscape ? 15f : 18f);
        applyTypographyToTextView(lyricsTitleView, typography, AiLyricsSettings.TYPO_LYRICS_HEADER_TITLE, 19f);
        applyTypographyToTextView(lyricsArtistView, typography, AiLyricsSettings.TYPO_LYRICS_HEADER_ARTIST, 14f);
        if (lyricPreviewView != null) {
            lyricPreviewView.setTypographySettings(typography);
        }
        if (lyricsView != null) {
            lyricsView.setTypographySettings(typography);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setTypographySettings(typography);
        }
    }

    private void applySpeakerColorSettings(AiLyricsSettings.Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        AiLyricsSettings.SpeakerColorSettings speakerColors = snapshot.speakerColors;
        if (lyricsView != null) {
            lyricsView.setSpeakerColorSettings(speakerColors);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setSpeakerColorSettings(speakerColors);
        }
    }

    private void applyTypographyToTextView(
            TextView view,
            AiLyricsSettings.TypographySettings typography,
            String slotId,
            float baseSizeSp
    ) {
        if (view == null) {
            return;
        }
        AiLyricsSettings.TypographyStyle style = typography == null
                ? AiLyricsSettings.TypographySettings.defaults().style(slotId)
                : typography.style(slotId);
        view.setTextSize(Math.max(8f, baseSizeSp * style.scale()));
        view.setTypeface(AppFonts.byWeight(this, style.weight));
        view.invalidate();
    }

    private void applyKeepScreenOnSetting(AiLyricsSettings.Snapshot snapshot) {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        if (snapshot != null && snapshot.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void addLyricsPopupTabButton(String tabId, String text) {
        if (lyricsPopupTabButtonsContainer == null) {
            return;
        }
        TextView button = label(text, 12f, Color.WHITE, AppFonts.semiBold(this));
        button.setTag(tabId);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setOnClickListener(view -> switchLyricsPopupTab(tabId));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
        if (lyricsPopupTabButtonsContainer.getChildCount() > 0) {
            params.leftMargin = dp(8);
        }
        lyricsPopupTabButtonsContainer.addView(button, params);
    }

    private void switchLyricsPopupTab(String tabId) {
        activeLyricsPopupTab = normalizeLyricsPopupTab(tabId);
        if (lyricsLanguageSettingsContent != null) {
            lyricsLanguageSettingsContent.setVisibility(
                    LYRICS_POPUP_TAB_LANGUAGE.equals(activeLyricsPopupTab) ? View.VISIBLE : View.GONE
            );
        }
        if (lyricsSyncSettingsContent != null) {
            lyricsSyncSettingsContent.setVisibility(
                    LYRICS_POPUP_TAB_SYNC.equals(activeLyricsPopupTab) ? View.VISIBLE : View.GONE
            );
        }
        if (videoSyncSettingsContent != null) {
            videoSyncSettingsContent.setVisibility(
                    LYRICS_POPUP_TAB_VIDEO.equals(activeLyricsPopupTab) ? View.VISIBLE : View.GONE
            );
        }
        if (lyricsManualSearchContent != null) {
            lyricsManualSearchContent.setVisibility(
                    LYRICS_POPUP_TAB_LRCLIB.equals(activeLyricsPopupTab) ? View.VISIBLE : View.GONE
            );
            if (LYRICS_POPUP_TAB_LRCLIB.equals(activeLyricsPopupTab)) {
                populateManualLrclibSearchDefaults(false);
            }
        }
        updateLyricsPopupTabButtons();
        updateLyricsSyncSettingsUi();
        updateVideoSyncSettingsUi();
    }

    private void updateLyricsPopupTabButtons() {
        if (lyricsPopupTabButtonsContainer == null) {
            return;
        }
        for (int index = 0; index < lyricsPopupTabButtonsContainer.getChildCount(); index++) {
            View child = lyricsPopupTabButtonsContainer.getChildAt(index);
            if (!(child instanceof TextView)) {
                continue;
            }
            TextView button = (TextView) child;
            boolean selected = activeLyricsPopupTab.equals(child.getTag());
            button.setTextColor(selected ? Color.rgb(12, 13, 17) : Color.WHITE);
            button.setBackground(roundDrawable(
                    selected ? Color.argb(238, 255, 255, 255) : Color.argb(34, 255, 255, 255),
                    dp(12)
            ));
        }
    }

    private String normalizeLyricsPopupTab(String tabId) {
        if (LYRICS_POPUP_TAB_SYNC.equals(tabId)) {
            return LYRICS_POPUP_TAB_SYNC;
        }
        if (LYRICS_POPUP_TAB_VIDEO.equals(tabId)) {
            return LYRICS_POPUP_TAB_VIDEO;
        }
        if (LYRICS_POPUP_TAB_LRCLIB.equals(tabId)) {
            return LYRICS_POPUP_TAB_LRCLIB;
        }
        return LYRICS_POPUP_TAB_LANGUAGE;
    }

    private void updateLyricsSyncSettingsUi() {
        if (lyricsSyncOffsetValueView != null) {
            lyricsSyncOffsetValueView.setText(formatSignedMs(currentTrackSyncOffsetMs));
        }
        if (lyricsSyncOffsetDescriptionView != null) {
            String trackText = currentTrack == null || !currentTrack.hasUsableMetadata()
                    ? ui("lyrics.sync.no_track")
                    : uiFormat("lyrics.sync.track_scope", currentTrack.title);
            lyricsSyncOffsetDescriptionView.setText(trackText
                    + "\n" + ui("lyrics.sync.help"));
        }
    }

    private void updateVideoSyncSettingsUi() {
        if (videoSyncOffsetValueView != null) {
            videoSyncOffsetValueView.setText(formatSignedMs(currentVideoSyncOffsetMs));
        }
        if (videoSyncOffsetDescriptionView != null) {
            String trackText = currentTrack == null || !currentTrack.hasUsableMetadata()
                    ? ui("lyrics.video_sync.no_track")
                    : uiFormat("lyrics.video_sync.track_scope", currentTrack.title);
            videoSyncOffsetDescriptionView.setText(trackText
                    + "\n" + ui("lyrics.video_sync.help"));
        }
    }

    private void populateManualLrclibSearchDefaults(boolean overwrite) {
        if (currentTrack == null || !currentTrack.hasUsableMetadata()) {
            return;
        }
        if (lyricsManualSearchTitleInput != null && (overwrite || textOf(lyricsManualSearchTitleInput).isEmpty())) {
            lyricsManualSearchTitleInput.setText(currentTrack.title);
        }
        if (lyricsManualSearchArtistInput != null && (overwrite || textOf(lyricsManualSearchArtistInput).isEmpty())) {
            lyricsManualSearchArtistInput.setText(currentTrack.artist);
        }
    }

    private void resetManualLrclibSearchForTrack(TrackSnapshot snapshot) {
        manualLrclibSearchInFlight = false;
        if (lyricsManualSearchResultsContainer != null) {
            lyricsManualSearchResultsContainer.removeAllViews();
        }
        if (snapshot != null && snapshot.hasUsableMetadata()) {
            if (lyricsManualSearchTitleInput != null) {
                lyricsManualSearchTitleInput.setText(snapshot.title);
            }
            if (lyricsManualSearchArtistInput != null) {
                lyricsManualSearchArtistInput.setText(snapshot.artist);
            }
        } else {
            if (lyricsManualSearchTitleInput != null) {
                lyricsManualSearchTitleInput.setText("");
            }
            if (lyricsManualSearchArtistInput != null) {
                lyricsManualSearchArtistInput.setText("");
            }
        }
        setManualLrclibStatus(ui("lyrics.lrclib_search.ready"));
    }

    private void performManualLrclibSearch() {
        if (manualLrclibSearchInFlight) {
            return;
        }
        if (lyricsRepository == null) {
            setManualLrclibStatus(ui("spotify.error.repository_unavailable"));
            return;
        }
        populateManualLrclibSearchDefaults(false);
        String title = textOf(lyricsManualSearchTitleInput);
        String artist = textOf(lyricsManualSearchArtistInput);
        if (title.isEmpty()) {
            setManualLrclibStatus(ui("lyrics.lrclib_search.empty_title"));
            return;
        }
        manualLrclibSearchInFlight = true;
        setManualLrclibStatus(ui("lyrics.lrclib_search.loading"));
        if (lyricsManualSearchResultsContainer != null) {
            lyricsManualSearchResultsContainer.removeAllViews();
        }
        lyricsRepository.searchManualLrclib(currentTrack, title, artist, this);
    }

    private void renderManualLrclibCandidates(List<LyricsRepository.ManualLrclibCandidate> candidates) {
        if (lyricsManualSearchResultsContainer == null) {
            return;
        }
        lyricsManualSearchResultsContainer.removeAllViews();
        if (candidates == null || candidates.isEmpty()) {
            setManualLrclibStatus(ui("lyrics.lrclib_search.no_results"));
            return;
        }
        setManualLrclibStatus(uiFormat("lyrics.lrclib_search.result_count_format", candidates.size()));
        for (LyricsRepository.ManualLrclibCandidate candidate : candidates) {
            View row = manualLrclibCandidateRow(candidate);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            if (lyricsManualSearchResultsContainer.getChildCount() > 0) {
                params.topMargin = dp(8);
            }
            lyricsManualSearchResultsContainer.addView(row, params);
        }
    }

    private View manualLrclibCandidateRow(LyricsRepository.ManualLrclibCandidate candidate) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(11), dp(10), dp(11), dp(10));
        row.setBackground(roundDrawable(Color.argb(38, 255, 255, 255), dp(10)));
        row.setClickable(true);
        row.setOnClickListener(view -> selectManualLrclibCandidate(candidate));

        TextView title = label(
                candidate.trackName.isEmpty() ? "LRCLIB #" + candidate.id : candidate.trackName,
                13f,
                Color.WHITE,
                AppFonts.bold(this)
        );
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        String artistAlbum = manualLrclibArtistAlbumText(candidate);
        if (!artistAlbum.isEmpty()) {
            TextView artist = label(artistAlbum, 11f, Color.argb(168, 255, 255, 255), AppFonts.regular(this));
            artist.setSingleLine(true);
            artist.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(artist, topMargin(matchWrap(), dp(4)));
        }

        TextView meta = label(manualLrclibMetaText(candidate), 10f, Color.argb(134, 255, 255, 255), AppFonts.semiBold(this));
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(meta, topMargin(matchWrap(), dp(6)));
        return row;
    }

    private String manualLrclibArtistAlbumText(LyricsRepository.ManualLrclibCandidate candidate) {
        if (candidate.albumName.isEmpty()) {
            return candidate.artistName;
        }
        if (candidate.artistName.isEmpty()) {
            return candidate.albumName;
        }
        return candidate.artistName + " · " + candidate.albumName;
    }

    private String manualLrclibMetaText(LyricsRepository.ManualLrclibCandidate candidate) {
        List<String> pieces = new ArrayList<>();
        pieces.add(manualLrclibKindLabel(candidate));
        if (candidate.durationSeconds > 0.0) {
            pieces.add(formatDurationSeconds(candidate.durationSeconds));
        }
        if (!candidate.isrc.isEmpty()) {
            pieces.add(candidate.isrc);
        }
        if (candidate.id > 0L) {
            pieces.add("#" + candidate.id);
        }
        return TextUtils.join(" · ", pieces);
    }

    private String manualLrclibKindLabel(LyricsRepository.ManualLrclibCandidate candidate) {
        if (candidate.instrumental) {
            return ui("lyrics.lrclib_search.instrumental");
        }
        if (candidate.synced) {
            return ui("lyrics.lrclib_search.synced");
        }
        return ui("lyrics.lrclib_search.plain");
    }

    private String formatDurationSeconds(double seconds) {
        return formatTime(Math.round(Math.max(0.0, seconds) * 1000.0));
    }

    private void selectManualLrclibCandidate(LyricsRepository.ManualLrclibCandidate candidate) {
        if (lyricsRepository == null || candidate == null) {
            return;
        }
        setManualLrclibStatus(ui("lyrics.lrclib_search.selecting"));
        lyricsRepository.loadManualLrclibCandidate(currentTrack, candidate, this);
    }

    private void setManualLrclibStatus(String message) {
        if (lyricsManualSearchStatusView != null) {
            lyricsManualSearchStatusView.setText(message == null ? "" : message);
        }
    }

    private boolean isCurrentManualLrclibTrack(String trackKey) {
        String safeKey = trackKey == null ? "" : trackKey;
        if (!currentLyricsKey.trim().isEmpty()) {
            return currentLyricsKey.equals(safeKey);
        }
        String currentKey = currentTrack == null || !currentTrack.hasUsableMetadata()
                ? ""
                : currentTrack.stableKey();
        return currentKey.equals(safeKey);
    }

    private void adjustCurrentTrackSyncOffset(int deltaMs) {
        setCurrentTrackSyncOffset(currentTrackSyncOffsetMs + deltaMs, true);
    }

    private void adjustCurrentVideoSyncOffset(int deltaMs) {
        setCurrentVideoSyncOffset(currentVideoSyncOffsetMs + deltaMs, true);
    }

    private void setCurrentTrackSyncOffset(int offsetMs, boolean notify) {
        int nextOffset = clampSyncOffset(offsetMs);
        currentTrackSyncOffsetMs = nextOffset;
        String key = currentLyricsKey == null || currentLyricsKey.trim().isEmpty()
                ? currentTrack == null ? "" : currentTrack.stableKey()
                : currentLyricsKey;
        if (aiLyricsSettings != null && !key.trim().isEmpty()) {
            aiLyricsSettings.setTrackSyncOffsetMs(key, nextOffset);
        }
        updateLyricsSyncSettingsUi();
        updateLyricsOffsetSensitiveViews();
        if (notify) {
            showSavedToast(uiFormat("toast.sync_offset_format", formatSignedMs(nextOffset)));
        }
    }

    private void setCurrentVideoSyncOffset(int offsetMs, boolean notify) {
        int nextOffset = clampSyncOffset(offsetMs);
        currentVideoSyncOffsetMs = nextOffset;
        String key = currentLyricsKey == null || currentLyricsKey.trim().isEmpty()
                ? currentTrack == null ? "" : currentTrack.stableKey()
                : currentLyricsKey;
        if (aiLyricsSettings != null && !key.trim().isEmpty()) {
            aiLyricsSettings.setTrackVideoSyncOffsetMs(key, nextOffset);
        }
        updateVideoSyncSettingsUi();
        updateYouTubeBackgroundPlaybackState();
        if (notify) {
            showSavedToast(uiFormat("toast.video_sync_offset_format", formatSignedMs(nextOffset)));
        }
    }

    private void updateLyricsOffsetSensitiveViews() {
        if (currentTrack == null || !currentTrack.hasUsableMetadata()) {
            return;
        }
        long position = currentPlaybackPosition(currentTrack);
        long lyricsPosition = lyricsPlaybackPosition(position, currentTrack.durationMs);
        setLyricsPlaybackPositionOnViews(lyricsPosition);
        updateLyricPreview(lyricsPosition);
        updateYouTubeBackgroundPlaybackState();
    }

    private long lyricsPlaybackPosition(long playerPositionMs, long durationMs) {
        long adjusted = playerPositionMs + currentTrackSyncOffsetMs;
        return durationMs > 0L
                ? Math.max(0L, Math.min(durationMs, adjusted))
                : Math.max(0L, adjusted);
    }

    private long playerPositionForLyricsTime(long lyricsTimeMs, long durationMs) {
        long target = lyricsTimeMs - currentTrackSyncOffsetMs;
        return durationMs > 0L
                ? Math.max(0L, Math.min(durationMs, target))
                : Math.max(0L, target);
    }

    private int clampSyncOffset(int offsetMs) {
        return Math.max(-10000, Math.min(10000, offsetMs));
    }

    private String offsetDeltaLabel(int deltaMs) {
        return (deltaMs > 0 ? "+" : "") + deltaMs + "ms";
    }

    private String formatSignedMs(int offsetMs) {
        return offsetMs > 0 ? "+" + offsetMs + "ms" : offsetMs + "ms";
    }

    private void rebuildSourceLanguageButtons() {
        if (sourceLanguageButtonsContainer == null) {
            return;
        }
        List<LanguageChoice> choices = new ArrayList<>();
        choices.add(new LanguageChoice("auto", autoSourceLanguageLabel()));
        for (AiLyricsSettings.Language language : AiLyricsSettings.SUPPORTED_LANGUAGES) {
            choices.add(new LanguageChoice(language.code, language.code + " · " + language.nativeName));
        }
        rebuildChoiceButtons(sourceLanguageButtonsContainer, choices, selectedRuleSourceLang, code -> {
            selectedRuleSourceLang = "auto".equalsIgnoreCase(code)
                    ? "auto"
                    : AiLyricsSettings.normalizeSourceLanguageKey(code);
            populateSelectedLanguageRule(aiLyricsSettings.snapshot());
            rebuildSourceLanguageButtons();
            requestAiLyrics(true);
        });
    }

    private void rebuildChoiceButtons(LinearLayout container, List<LanguageChoice> choices, String selectedCode, ChoiceHandler handler) {
        container.removeAllViews();
        LinearLayout row = null;
        for (int index = 0; index < choices.size(); index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
                if (container.getChildCount() > 0) {
                    rowParams.topMargin = dp(8);
                }
                container.addView(row, rowParams);
            }
            LanguageChoice choice = choices.get(index);
            boolean selected = sameChoice(choice.code, selectedCode);
            TextView button = languageButton(choice.label, selected);
            button.setOnClickListener(view -> handler.onChoice(choice.code));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
            params.leftMargin = index % 2 == 0 ? 0 : dp(8);
            row.addView(button, params);
        }
    }

    private TextView languageButton(String text, boolean selected) {
        TextView button = label(text, 12f, selected ? Color.rgb(12, 13, 17) : Color.WHITE, AppFonts.semiBold(this));
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(roundDrawable(
                selected ? Color.argb(238, 255, 255, 255) : Color.argb(34, 255, 255, 255),
                dp(11)
        ));
        return button;
    }

    private TextView settingsSelectButton(String text) {
        TextView button = label(text, 13f, Color.WHITE, AppFonts.semiBold(this));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setMinHeight(dp(42));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(roundDrawable(Color.argb(44, 255, 255, 255), dp(12)));
        return button;
    }

    private boolean sameChoice(String left, String right) {
        String a = "auto".equalsIgnoreCase(left)
                ? "auto"
                : AiLyricsSettings.OUTPUT_LANG_SAME_UI.equalsIgnoreCase(left)
                ? AiLyricsSettings.OUTPUT_LANG_SAME_UI
                : AiLyricsSettings.normalizeSourceLanguageKey(left);
        String b = "auto".equalsIgnoreCase(right)
                ? "auto"
                : AiLyricsSettings.OUTPUT_LANG_SAME_UI.equalsIgnoreCase(right)
                ? AiLyricsSettings.OUTPUT_LANG_SAME_UI
                : AiLyricsSettings.normalizeSourceLanguageKey(right);
        return a.equalsIgnoreCase(b);
    }

    private void populateSelectedLanguageRule(AiLyricsSettings.Snapshot snapshot) {
        if (snapshot == null || languageTranslationSwitch == null || languagePronunciationSwitch == null) {
            return;
        }
        AiLyricsSettings.LanguageRule rule = snapshot.ruleForSource(effectiveSelectedSourceLang());
        suppressLanguageRuleEvents = true;
        languageTranslationSwitch.setChecked(rule.translationEnabled);
        languagePronunciationSwitch.setChecked(rule.pronunciationEnabled);
        suppressLanguageRuleEvents = false;
        updateSelectedLanguageRuleStatusFromUi();
    }

    private void updateSelectedLanguageRuleStatusFromUi() {
        if (selectedLanguageRuleView == null || languageTranslationSwitch == null || languagePronunciationSwitch == null) {
            return;
        }
        selectedLanguageRuleView.setText(ui("lyrics.rule.track_language") + ": " + sourceLanguageLabel(selectedRuleSourceLang)
                + ("auto".equalsIgnoreCase(selectedRuleSourceLang)
                ? "\n" + ui("lyrics.rule.save_target") + ": " + AiLyricsSettings.languageLabel(effectiveSelectedSourceLang())
                : "")
                + "\n" + ui("lyrics.translation") + ": " + onOff(languageTranslationSwitch.isChecked())
                + " · " + ui("lyrics.pronunciation") + ": " + onOff(languagePronunciationSwitch.isChecked()));
    }

    private void applySelectedLanguageRuleFromUi(boolean refreshRuleUi) {
        if (suppressLanguageRuleEvents || aiLyricsSettings == null || languageTranslationSwitch == null || languagePronunciationSwitch == null) {
            return;
        }
        aiLyricsSettings.setLanguageRule(
                effectiveSelectedSourceLang(),
                languageTranslationSwitch.isChecked(),
                languagePronunciationSwitch.isChecked(),
                aiLyricsSettings.snapshot().defaultRule.targetLang
        );
        if (refreshRuleUi) {
            populateSelectedLanguageRule(aiLyricsSettings.snapshot());
        }
    }

    private String sourceLanguageLabel(String lang) {
        if ("auto".equalsIgnoreCase(lang)) {
            return autoSourceLanguageLabel();
        }
        return AiLyricsSettings.languageLabel(lang);
    }

    private String autoSourceLanguageLabel() {
        return "auto(" + effectiveDetectedSourceLang() + ")";
    }

    private String effectiveDetectedSourceLang() {
        String normalized = AiLyricsSettings.normalizeLanguageCode(detectedLyricsSourceLang);
        return normalized.isEmpty() ? "en" : normalized;
    }

    private String effectiveSelectedSourceLang() {
        return "auto".equalsIgnoreCase(selectedRuleSourceLang)
                ? effectiveDetectedSourceLang()
                : AiLyricsSettings.normalizeSourceLanguageKey(selectedRuleSourceLang);
    }

    private String onOff(boolean enabled) {
        return enabled ? ui("label.on") : ui("label.off");
    }

    private void attachSpotifyMetaTap(View view) {
        if (view == null) {
            return;
        }
        view.setClickable(true);
        view.setOnClickListener(target -> openSpotifyForCurrentTrack());
    }

    private void handleLyricsMetaTap() {
        dismissLyricsMetaTip();
        openSpotifyForCurrentTrack();
    }

    private void handleLyricsMetaLongPress(View target) {
        dismissLyricsMetaTip();
        if (target != null) {
            target.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
        toggleLyricsLanguageSettings();
    }

    private void scheduleLyricsMetaLongPress(View target) {
        cancelLyricsMetaLongPress();
        lyricsMetaLongPressTriggered = false;
        lyricsMetaLongPressRunnable = () -> {
            lyricsMetaLongPressRunnable = null;
            lyricsMetaLongPressTriggered = true;
            handleLyricsMetaLongPress(target);
        };
        handler.postDelayed(lyricsMetaLongPressRunnable, ViewConfiguration.getLongPressTimeout());
    }

    private void cancelLyricsMetaLongPress() {
        if (lyricsMetaLongPressRunnable != null) {
            handler.removeCallbacks(lyricsMetaLongPressRunnable);
            lyricsMetaLongPressRunnable = null;
        }
    }

    private void toggleLyricsLanguageSettings() {
        if (lyricsLanguageSettingsPanel == null) {
            return;
        }
        lyricsLanguageSettingsVisible = !lyricsLanguageSettingsVisible;
        if (lyricsLanguageSettingsVisible) {
            updateLyricsLanguageSettingsUi();
            lyricsLanguageSettingsPanel.setVisibility(View.VISIBLE);
            lyricsLanguageSettingsPanel.setAlpha(0f);
            lyricsLanguageSettingsPanel.setTranslationY(-dp(8));
            lyricsLanguageSettingsPanel.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(160L)
                    .start();
        } else {
            lyricsLanguageSettingsPanel.animate()
                    .alpha(0f)
                    .translationY(-dp(8))
                    .setDuration(130L)
                    .withEndAction(() -> {
                        lyricsLanguageSettingsPanel.setVisibility(View.GONE);
                        lyricsLanguageSettingsPanel.setAlpha(1f);
                        lyricsLanguageSettingsPanel.setTranslationY(0f);
                    })
                    .start();
        }
        updateLyricsLanguageButtonState();
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (ACTION_UPDATE_INSTALL_RESULT.equals(intent.getAction())) {
            handleUpdateInstallResult(intent);
        }
        if (intent.getBooleanExtra(EXTRA_OPEN_LYRICS_PAGE, false)) {
            pendingOpenLyricsPageFromIntent = true;
        }
    }

    private void consumeOpenLyricsPageRequest() {
        if (!pendingOpenLyricsPageFromIntent) {
            return;
        }
        pendingOpenLyricsPageFromIntent = false;
        if (!isInitialSetupComplete()) {
            return;
        }
        handler.postDelayed(() -> {
            if (!isLandscapeLayout()) {
                showLyricsPage(true);
            }
        }, 90L);
    }

    private void openSpotifyForCurrentTrack() {
        TrackSnapshot snapshot = currentTrack != null ? currentTrack : NowPlayingService.getLatestSnapshot();
        String packageName = snapshot != null && isSpotifyPackage(snapshot.packageName)
                ? snapshot.packageName
                : "com.spotify.music";
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launchIntent == null && !"com.spotify.music".equals(packageName)) {
            launchIntent = getPackageManager().getLaunchIntentForPackage("com.spotify.music");
        }
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (tryStartActivity(launchIntent)) {
                return;
            }
        }
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launcherIntent.setPackage(packageName);
        launcherIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (tryStartActivity(launcherIntent)) {
            return;
        }
        showSavedToast(ui("toast.spotify_open_failed"));
    }

    private boolean tryStartActivity(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean isSpotifyPackage(String packageName) {
        String value = packageName == null ? "" : packageName.trim().toLowerCase(Locale.ROOT);
        return value.startsWith("com.spotify.");
    }

    private void saveLyricsLanguageRuleAndRefresh() {
        if (suppressLanguageRuleEvents) {
            return;
        }
        applySelectedLanguageRuleFromUi(false);
        updateLyricsLanguageButtonState();
        if (aiSettingsStatusView != null) {
            aiSettingsStatusView.setText(ui("toast.language_rule_saved"));
        }
        showSavedToast(ui("toast.language_rule_saved"));
        translatedTrackTitle = "";
        translatedTrackArtist = "";
        updateTrackMetadataTextViews(currentTrack);
        requestMetadataTranslation(true);
        requestAiLyrics(true);
    }

    private void updateLyricsLanguageSettingsUi() {
        if (aiLyricsSettings == null) {
            return;
        }
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
        rebuildSourceLanguageButtons();
        populateSelectedLanguageRule(snapshot);
        updateLyricsLanguageButtonState();
        switchLyricsPopupTab(activeLyricsPopupTab);
        updateLyricsSyncSettingsUi();
        updateVideoSyncSettingsUi();
    }

    private void updateLyricsLanguageButtonState() {
        if (lyricsLanguageButton == null || aiLyricsSettings == null) {
            return;
        }
        AiLyricsSettings.LanguageRule rule = aiLyricsSettings.snapshot().ruleForSource(effectiveSelectedSourceLang());
        boolean active = rule.enabled();
        String label = active ? ui("lyrics.button.translation_on") : ui("lyrics.translation");
        if (rule.pronunciationEnabled && !rule.translationEnabled) {
            label = ui("lyrics.button.pronunciation_on");
        } else if (rule.pronunciationEnabled) {
            label = ui("lyrics.button.translation_plus");
        }
        lyricsLanguageButton.setText(label);
        lyricsLanguageButton.setTextColor(active || lyricsLanguageSettingsVisible ? Color.rgb(12, 13, 17) : Color.WHITE);
        lyricsLanguageButton.setBackground(roundDrawable(
                active || lyricsLanguageSettingsVisible ? Color.argb(238, 255, 255, 255) : Color.argb(34, 255, 255, 255),
                dp(14)
        ));
    }

    private void updateDetectedLyricsSourceLanguage(LyricsResult result) {
        if (result == null || result.lines == null || result.lines.isEmpty()) {
            detectedLyricsSourceLang = detectCurrentTrackMetadataLanguage();
            return;
        }
        String detected = AiLyricsRepository.detectLanguage(AiLyricsRepository.buildPayloadText(result.lines));
        detectedLyricsSourceLang = AiLyricsSettings.normalizeLanguageCode(detected);
        if (detectedLyricsSourceLang == null || detectedLyricsSourceLang.trim().isEmpty()) {
            detectedLyricsSourceLang = detectCurrentTrackMetadataLanguage();
        }
    }

    private String detectCurrentTrackMetadataLanguage() {
        if (currentTrack == null || !currentTrack.hasUsableMetadata()) {
            return "en";
        }
        String detected = AiLyricsRepository.detectLanguage(currentTrack.title + "\n" + currentTrack.artist);
        String normalized = AiLyricsSettings.normalizeLanguageCode(detected);
        return normalized.isEmpty() ? "en" : normalized;
    }

    private void updateTrackMetadataTextViews(TrackSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasUsableMetadata()) {
            return;
        }
        String title = translatedTrackTitle == null || translatedTrackTitle.trim().isEmpty()
                ? snapshot.title
                : translatedTrackTitle.trim();
        String artist = translatedTrackArtist == null || translatedTrackArtist.trim().isEmpty()
                ? snapshot.artist
                : translatedTrackArtist.trim();
        titleView.setText(title);
        artistView.setText(artist);
        applyNowPlayingTextColors();
        lyricsTitleView.setText(title);
        lyricsArtistView.setText(artist);
    }

    private void applyNowPlayingTextColors() {
        if (titleView != null) {
            titleView.setTextColor(Color.WHITE);
        }
        if (artistView != null) {
            artistView.setTextColor(Color.argb(220, 255, 255, 255));
        }
    }

    private void populateAiSettingsUi() {
        if (aiLyricsSettings == null) {
            return;
        }
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
        rebuildLanguageSettingsUi(snapshot);
        if (apiKeysInput != null) {
            apiKeysInput.setText(snapshot.apiKeys);
        }
        if (modelInput != null) {
            modelInput.setText(snapshot.model);
        }
        if (baseUrlInput != null) {
            baseUrlInput.setText(snapshot.baseUrl);
        }
        if (maxTokensInput != null) {
            maxTokensInput.setText(String.valueOf(snapshot.maxTokens));
        }
        if (temperatureInput != null) {
            temperatureInput.setText(String.format(Locale.ROOT, "%.2f", snapshot.temperature));
        }
        populateSpotifyCredentialInputs(snapshot);
        if (metadataTranslationSwitch != null) {
            suppressSettingsEvents = true;
            metadataTranslationSwitch.setChecked(snapshot.metadataTranslationEnabled);
            suppressSettingsEvents = false;
        }
        if (japaneseFuriganaSwitch != null) {
            suppressSettingsEvents = true;
            japaneseFuriganaSwitch.setChecked(snapshot.japaneseFuriganaEnabled);
            suppressSettingsEvents = false;
        }
        if (autoInstrumentalBreakSwitch != null) {
            suppressSettingsEvents = true;
            autoInstrumentalBreakSwitch.setChecked(snapshot.autoInstrumentalBreakEnabled);
            suppressSettingsEvents = false;
        }
        if (interludeLabelsSwitch != null) {
            suppressSettingsEvents = true;
            interludeLabelsSwitch.setChecked(snapshot.interludeLabelsEnabled);
            suppressSettingsEvents = false;
        }
        if (syncedLyricsKaraokeSwitch != null) {
            suppressSettingsEvents = true;
            syncedLyricsKaraokeSwitch.setChecked(snapshot.syncedLyricsKaraokeAnimationEnabled);
            suppressSettingsEvents = false;
        }
        if (karaokeBounceSwitch != null) {
            suppressSettingsEvents = true;
            karaokeBounceSwitch.setChecked(snapshot.karaokeBounceEffectEnabled);
            suppressSettingsEvents = false;
        }
        if (landscapeAutoHideControlsSwitch != null) {
            suppressSettingsEvents = true;
            landscapeAutoHideControlsSwitch.setChecked(snapshot.landscapeAutoHideControls);
            suppressSettingsEvents = false;
        }
        if (keepScreenOnSwitch != null) {
            suppressSettingsEvents = true;
            keepScreenOnSwitch.setChecked(snapshot.keepScreenOn);
            suppressSettingsEvents = false;
        }
        updateBackgroundSettingsUi(snapshot, true);
        applyTypographySettings(snapshot);
        updateSpeakerColorSettingsUi(snapshot);
        applySpeakerColorSettings(snapshot);
        if (providerSummaryView != null) {
            providerSummaryView.setText(snapshot.provider.label + " · " + providerDescription(snapshot.provider)
                    + "\n" + snapshot.provider.defaultBaseUrl);
        }
        if (aiSettingsStatusView != null) {
            aiSettingsStatusView.setText(snapshot.enabled()
                    ? (snapshot.hasApiKey() ? ui("status.ai_lyrics_active") : ui("status.ai_key_needed"))
                    : ui("status.ai_disabled"));
        }
        updateProviderButtons();
    }

    private void applyAiSettingsFromUi() {
        applyAiSettingsFromUi(true);
    }

    private void applyAiSettingsFromUi(boolean updateStatus) {
        if (aiLyricsSettings == null) {
            return;
        }
        aiLyricsSettings.setApiKeys(textOf(apiKeysInput));
        aiLyricsSettings.setModel(textOf(modelInput));
        aiLyricsSettings.setBaseUrl(textOf(baseUrlInput));
        aiLyricsSettings.setMaxTokens(parseInt(textOf(maxTokensInput), 16000));
        aiLyricsSettings.setTemperature(parseFloat(textOf(temperatureInput), 0.3f));
        applyBackgroundSettings(aiLyricsSettings.snapshot());
        if (updateStatus && aiSettingsStatusView != null) {
            aiSettingsStatusView.setText(ui("toast.settings_saved"));
        }
        if (updateStatus) {
            showSavedToast(ui("toast.settings_saved"));
        }
    }

    private void applySpotifySettingsFromUi() {
        saveSpotifyCredentials(textOf(spotifyClientIdInput), textOf(spotifyClientSecretInput), true);
    }

    private void applySpotifySetupFromRequiredPanel() {
        saveSpotifyCredentials(textOf(spotifySetupClientIdInput), textOf(spotifySetupClientSecretInput), true);
    }

    private boolean saveSpotifyCredentials(String nextClientId, String nextClientSecret, boolean reloadOnChange) {
        if (aiLyricsSettings == null) {
            return false;
        }
        if (spotifyCredentialsValidationInFlight) {
            showSavedToast(ui("toast.spotify_checking"));
            return false;
        }
        AiLyricsSettings.Snapshot before = aiLyricsSettings.snapshot();
        String clientId = nextClientId == null ? "" : nextClientId.trim();
        String clientSecret = nextClientSecret == null ? "" : nextClientSecret.trim();
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            String message = ui("toast.spotify_missing");
            setSpotifyValidationStatus(message);
            showSavedToast(message);
            return false;
        }
        if (lyricsRepository == null) {
            setSpotifyValidationStatus(uiFormat("spotify.status_invalid_format", ui("spotify.error.repository_unavailable")));
            showSavedToast(ui("toast.spotify_invalid"));
            return false;
        }

        spotifyCredentialsValidationInFlight = true;
        setSpotifyValidationStatus(ui("spotify.status_checking"));
        showSavedToast(ui("toast.spotify_checking"));
        lyricsRepository.validateSpotifyCredentials(
                clientId,
                clientSecret,
                new LyricsRepository.SpotifyTokenValidationCallback() {
                    @Override
                    public void onSpotifyTokenValidated(long expiresInSeconds) {
                        finishSpotifyCredentialsSave(before, clientId, clientSecret, reloadOnChange);
                    }

                    @Override
                    public void onSpotifyTokenValidationFailed(String message) {
                        spotifyCredentialsValidationInFlight = false;
                        String detail = message == null || message.trim().isEmpty()
                                ? "unknown error"
                                : message.trim();
                        setSpotifyValidationStatus(uiFormat("spotify.status_invalid_format", detail));
                        showSavedToast(ui("toast.spotify_invalid"));
                    }

                    @Override
                    public void onSpotifyTokenValidationLog(String message) {
                        appendLog(message);
                    }
                }
        );
        return false;
    }

    private void finishSpotifyCredentialsSave(
            AiLyricsSettings.Snapshot before,
            String clientId,
            String clientSecret,
            boolean reloadOnChange
    ) {
        spotifyCredentialsValidationInFlight = false;
        boolean changed = before == null
                || !before.spotifyClientId.equals(clientId)
                || !before.spotifyClientSecret.equals(clientSecret);
        aiLyricsSettings.setSpotifyApiCredentials(clientId, clientSecret);
        AiLyricsSettings.Snapshot after = aiLyricsSettings.snapshot();
        if (lyricsRepository != null && changed) {
            lyricsRepository.clearCache();
        }
        populateSpotifyCredentialInputs(after);
        setSpotifyValidationStatus(ui("spotify.status_configured"));
        updateSpotifySetupGate(true);
        if (changed && reloadOnChange) {
            appendLog("spotify api settings changed: token verified, credentials saved, lyrics cache cleared");
            reloadCurrentLyricsFromSettings();
        }
        showSavedToast(ui("toast.spotify_saved"));
    }

    private void setSpotifyValidationStatus(String message) {
        String value = message == null ? "" : message;
        if (spotifySetupStatusView != null) {
            spotifySetupStatusView.setText(value);
        }
        if (aiSettingsStatusView != null) {
            aiSettingsStatusView.setText(value);
        }
    }

    private void populateSpotifyCredentialInputs(AiLyricsSettings.Snapshot snapshot) {
        String clientId = snapshot == null ? "" : snapshot.spotifyClientId;
        String clientSecret = snapshot == null ? "" : snapshot.spotifyClientSecret;
        if (spotifyClientIdInput != null) {
            spotifyClientIdInput.setText(clientId);
        }
        if (spotifyClientSecretInput != null) {
            spotifyClientSecretInput.setText(clientSecret);
        }
        if (spotifySetupClientIdInput != null) {
            spotifySetupClientIdInput.setText(clientId);
        }
        if (spotifySetupClientSecretInput != null) {
            spotifySetupClientSecretInput.setText(clientSecret);
        }
        if (spotifySetupStatusView != null) {
            spotifySetupStatusView.setText(snapshot != null && snapshot.hasSpotifyApiCredentials()
                    ? ui("spotify.status_configured")
                    : ui("spotify.status_required"));
        }
    }

    private void reloadCurrentLyricsFromSettings() {
        TrackSnapshot snapshot = currentTrack;
        if (snapshot == null || !snapshot.hasUsableMetadata() || lyricsRepository == null) {
            showCurrentTrackReloadLoading(null);
            NowPlayingService.requestRefresh(this);
            return;
        }
        showCurrentTrackReloadLoading(snapshot);
        currentLyricsKey = "";
        currentArtworkKey = "";
        currentArtworkFromSpotify = false;
        onNowPlayingChanged(snapshot);
        NowPlayingService.requestRefresh(this);
    }

    private void clearCurrentLyricsCacheFromSettings() {
        TrackSnapshot snapshot = currentTrack;
        if (snapshot == null || !snapshot.hasUsableMetadata()) {
            showSavedToast(ui("toast.current_track_missing"));
            return;
        }
        String key = snapshot.stableKey();
        if (lyricsRepository != null) {
            lyricsRepository.clearCacheForTrack(key);
            lyricsRepository.clearSyncDataCacheForIsrc(nonEmpty(
                    currentBaseLyricsResult == null ? "" : currentBaseLyricsResult.isrc,
                    nonEmpty(currentResolvedIsrc, snapshot.isrc)
            ));
        }
        if (youtubeBackgroundRepository != null) {
            youtubeBackgroundRepository.clearCacheForIsrc(nonEmpty(
                    currentBaseLyricsResult == null ? "" : currentBaseLyricsResult.isrc,
                    nonEmpty(currentResolvedIsrc, snapshot.isrc)
            ));
        }
        if (aiLyricsRepository != null) {
            aiLyricsRepository.clearTrackCache(key);
        }
        if (furiganaRepository != null) {
            furiganaRepository.clearTrackCache(key);
        }
        translatedTrackTitle = "";
        translatedTrackArtist = "";
        appendLog("lyrics cache cleared: current track / title=\"" + snapshot.title + "\" / artist=\"" + snapshot.artist + "\"");
        if (aiSettingsStatusView != null) {
            aiSettingsStatusView.setText(ui("toast.current_cache_cleared"));
        }
        showSavedToast(ui("toast.current_cache_cleared"));
        reloadCurrentLyricsFromSettings();
    }

    private void clearAllLyricsCacheFromSettings() {
        if (lyricsRepository != null) {
            lyricsRepository.clearCache();
        }
        if (aiLyricsRepository != null) {
            aiLyricsRepository.clearCache();
        }
        if (furiganaRepository != null) {
            furiganaRepository.clearCache();
        }
        if (youtubeBackgroundRepository != null) {
            youtubeBackgroundRepository.clearCache();
        }
        translatedTrackTitle = "";
        translatedTrackArtist = "";
        appendLog("lyrics cache cleared: all tracks");
        if (aiSettingsStatusView != null) {
            aiSettingsStatusView.setText(ui("toast.all_cache_cleared"));
        }
        showSavedToast(ui("toast.all_cache_cleared"));
        reloadCurrentLyricsFromSettings();
    }

    private void showCurrentTrackReloadLoading(TrackSnapshot snapshot) {
        spotifySetupRequired = false;
        aiLyricsGenerating = false;
        pendingSeekPositionMs = -1L;
        currentLyricsResult = LyricsResult.empty(ui("status.lyrics_loading"));
        currentBaseLyricsResult = currentLyricsResult;
        currentFuriganaResult = null;
        currentFuriganaKey = "";
        currentTrackSyncOffsetMs = snapshot == null || aiLyricsSettings == null
                ? 0
                : aiLyricsSettings.trackSyncOffsetMs(snapshot.stableKey());
        currentVideoSyncOffsetMs = snapshot == null || aiLyricsSettings == null
                ? 0
                : aiLyricsSettings.trackVideoSyncOffsetMs(snapshot.stableKey());
        sourceView.setText(ui("status.lyrics_loading"));
        statusView.setText(ui("status.reload_after_spotify"));
        setLyricsTrackDurationOnViews(snapshot == null ? 0L : snapshot.durationMs);
        setLyricsResultOnViews(currentLyricsResult);
        setLyricsSupplementLoading(false, false);
        updateLyricPreview(snapshot == null ? 0L : currentLyricsPlaybackPosition(snapshot));
    }

    private void showSavedToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void maybeStartAutomaticUpdateCheck() {
        if (automaticUpdateCheckStarted || !isInitialSetupComplete()) {
            return;
        }
        SharedPreferences prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST_AUTO_UPDATE_CHECK_MS, 0L);
        if (now - last < AUTO_UPDATE_CHECK_INTERVAL_MS) {
            return;
        }
        automaticUpdateCheckStarted = true;
        prefs.edit().putLong(KEY_LAST_AUTO_UPDATE_CHECK_MS, now).apply();
        handler.postDelayed(() -> checkForUpdates(false), 1_600L);
    }

    private void checkForUpdates(boolean manual) {
        if (updateChecker == null) {
            setUpdateStatus(uiFormat("update.status_failed_format", ui("spotify.error.repository_unavailable")));
            return;
        }
        if (updateCheckInFlight) {
            if (manual) {
                showSavedToast(ui("toast.update_checking"));
            }
            return;
        }
        updateCheckInFlight = true;
        setUpdateStatus(ui("update.status_checking"));
        if (manual) {
            showSavedToast(ui("toast.update_checking"));
        }
        updateChecker.checkLatest(new UpdateChecker.Callback() {
            @Override
            public void onUpdateChecked(UpdateChecker.UpdateInfo info) {
                updateCheckInFlight = false;
                if (info == null) {
                    onUpdateCheckFailed("empty response");
                    return;
                }
                pendingUpdateInfo = info;
                if (info.updateAvailable) {
                    String version = info.latestDisplayVersion();
                    setUpdateStatus(uiFormat("update.status_available_format", version));
                    if (manual) {
                        showSavedToast(uiFormat("toast.update_available_format", version));
                    }
                    showUpdateAvailableDialog(info);
                    return;
                }
                setUpdateStatus(uiFormat("update.status_latest_format", info.currentVersionName));
                if (manual) {
                    showSavedToast(ui("toast.update_latest"));
                }
            }

            @Override
            public void onUpdateCheckFailed(String message) {
                updateCheckInFlight = false;
                String detail = message == null || message.trim().isEmpty() ? "unknown error" : message.trim();
                setUpdateStatus(uiFormat("update.status_failed_format", detail));
                appendLog("update check failed: " + detail);
                if (manual) {
                    showSavedToast(ui("toast.update_failed"));
                }
            }
        });
    }

    private void setUpdateStatus(String message) {
        if (updateStatusView != null) {
            updateStatusView.setText(message == null ? "" : message);
        }
        if (aiSettingsStatusView != null && message != null && !message.trim().isEmpty()) {
            aiSettingsStatusView.setText(message);
        }
    }

    private void showUpdateAvailableDialog(UpdateChecker.UpdateInfo info) {
        if (isFinishing() || info == null) {
            return;
        }
        String version = info.latestDisplayVersion();
        String notes = compactReleaseNotes(info.releaseNotes);
        if (notes.isEmpty()) {
            notes = ui("update.dialog_message_no_notes");
        }
        String message = uiFormat(
                "update.dialog_message_format",
                info.currentVersionName,
                info.currentVersionCode,
                version,
                info.latestVersionCode,
                notes
        );
        new AlertDialog.Builder(this)
                .setTitle(ui("update.dialog_title"))
                .setMessage(message)
                .setPositiveButton(ui("update.download"), (dialog, which) -> downloadUpdateApk(info))
                .setNegativeButton(ui("update.later"), null)
                .setNeutralButton(ui("update.open_release"), (dialog, which) -> openUpdateReleasePage(info))
                .show();
    }

    private String compactReleaseNotes(String notes) {
        if (notes == null) {
            return "";
        }
        String value = notes.trim();
        if (value.length() <= 700) {
            return value;
        }
        return value.substring(0, 700).trim() + "\n...";
    }

    private void downloadUpdateApk(UpdateChecker.UpdateInfo info) {
        if (info == null || info.apkDownloadUrl.isEmpty()) {
            openUpdateReleasePage(info);
            return;
        }
        if (updateDownloadInFlight) {
            return;
        }
        if (!canRequestPackageInstalls()) {
            pendingUpdateInfo = info;
            setUpdateStatus(ui("update.install_failed"));
            openInstallPermissionSettings();
            return;
        }
        updateDownloadInFlight = true;
        pendingUpdateInfo = info;
        String fileName = info.apkName.isEmpty()
                ? "ivLyrics-Android-" + info.latestDisplayVersion() + ".apk"
                : info.apkName;
        setUpdateStatus(uiFormat("update.download_started_format", fileName));
        showSavedToast(uiFormat("update.download_started_format", fileName));
        updateExecutor.execute(() -> downloadAndInstallUpdate(info, fileName));
    }

    private void downloadAndInstallUpdate(UpdateChecker.UpdateInfo info, String fileName) {
        File apkFile = null;
        try {
            File updatesDir = new File(getCacheDir(), "updates");
            if (!updatesDir.exists() && !updatesDir.mkdirs()) {
                throw new IOException("Could not create update cache");
            }
            deleteOldUpdateApks(updatesDir);
            apkFile = new File(updatesDir, sanitizeApkFileName(fileName));
            downloadUpdateToFile(info.apkDownloadUrl, apkFile, fileName);
            postUpdateStatus(ui("update.download_complete"));
            stageUpdateInstall(apkFile, info);
        } catch (Exception error) {
            postAppendLog("update download/install failed: " + error.getMessage());
            handler.post(() -> {
                setUpdateStatus(ui("update.install_failed"));
                showSavedToast(ui("update.install_failed"));
            });
        } finally {
            deleteQuietly(apkFile);
            handler.post(() -> updateDownloadInFlight = false);
        }
    }

    private void downloadUpdateToFile(String url, File target, String displayName) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive,*/*");
            connection.setRequestProperty("User-Agent", "ivLyrics-Android/" + currentAppVersionName());
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            long total = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    ? connection.getContentLengthLong()
                    : connection.getContentLength();
            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[32 * 1024];
                long written = 0L;
                int lastPercent = -1;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    written += read;
                    if (total > 0L) {
                        int percent = (int) Math.min(100L, (written * 100L) / total);
                        if (percent != lastPercent && (percent == 100 || percent - lastPercent >= 4)) {
                            lastPercent = percent;
                            postUpdateStatus(uiFormat("update.download_started_format", displayName) + " · " + percent + "%");
                        }
                    }
                }
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void stageUpdateInstall(File apkFile, UpdateChecker.UpdateInfo info) throws IOException {
        if (apkFile == null || !apkFile.exists() || apkFile.length() <= 0L) {
            throw new IOException("Downloaded APK is empty");
        }
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
        );
        params.setAppPackageName(getPackageName());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId);
             InputStream input = new FileInputStream(apkFile);
             OutputStream output = session.openWrite(apkFile.getName(), 0L, apkFile.length())) {
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            session.fsync(output);

            Intent callback = new Intent(this, MainActivity.class);
            callback.setAction(ACTION_UPDATE_INSTALL_RESULT);
            callback.putExtra("version", info == null ? "" : info.latestDisplayVersion());
            callback.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(this, sessionId, callback, flags);
            committed = true;
            session.commit(pendingIntent.getIntentSender());
        } finally {
            if (!committed) {
                installer.abandonSession(sessionId);
            }
        }
    }

    private boolean canRequestPackageInstalls() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
    }

    private void openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            try {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }

    private void handleUpdateInstallResult(Intent intent) {
        if (intent == null || !ACTION_UPDATE_INSTALL_RESULT.equals(intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmationIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            } else {
                confirmationIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            }
            if (confirmationIntent != null) {
                try {
                    setUpdateStatus(ui("update.download_complete"));
                    startActivity(confirmationIntent);
                    return;
                } catch (ActivityNotFoundException ignored) {
                }
            }
        }
        if (status == PackageInstaller.STATUS_SUCCESS) {
            setUpdateStatus(ui("update.download_complete"));
            return;
        }
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        appendLog("update install result: status=" + status + " / message=" + (message == null ? "" : message));
        setUpdateStatus(ui("update.install_failed"));
    }

    private void postUpdateStatus(String message) {
        handler.post(() -> setUpdateStatus(message));
    }

    private void postAppendLog(String message) {
        handler.post(() -> appendLog(message));
    }

    private String sanitizeApkFileName(String fileName) {
        String value = fileName == null ? "" : fileName.trim();
        if (value.isEmpty()) {
            value = "ivLyrics-update.apk";
        }
        value = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return value.toLowerCase(Locale.ROOT).endsWith(".apk") ? value : value + ".apk";
    }

    private void deleteOldUpdateApks(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file != null && file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".apk")) {
                deleteQuietly(file);
            }
        }
    }

    private void deleteQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            if (!file.delete()) {
                postAppendLog("update cache cleanup skipped: " + file.getName());
            }
        } catch (SecurityException ignored) {
        }
    }

    private String currentAppVersionName() {
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return versionName == null ? "" : versionName;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void openUpdateReleasePage(UpdateChecker.UpdateInfo info) {
        String url = info == null || info.releaseUrl.isEmpty()
                ? "https://github.com/ivLis-Studio/ivLyrics-Android/releases"
                : info.releaseUrl;
        openExternalUrl(url);
    }

    private void openExternalUrl(String url) {
        String safeUrl = url == null ? "" : url.trim();
        if (safeUrl.isEmpty()) {
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)));
        } catch (ActivityNotFoundException error) {
            showSavedToast(ui("update.install_failed"));
        }
    }

    private void maybeShowLyricsMetaTip() {
        if (isLandscapeLayout()
                || !lyricsPageVisible
                || lyricsTitleView == null
                || lyricsArtistView == null
                || lyricsMetaTipAlreadyShown()
                || lyricsLanguageSettingsVisible) {
            return;
        }
        handler.postDelayed(this::showLyricsMetaTipIfNeeded, 220L);
    }

    private void showLyricsMetaTipIfNeeded() {
        if (isLandscapeLayout()
                || !lyricsPageVisible
                || lyricsArtistView == null
                || !lyricsArtistView.isShown()
                || lyricsMetaTipAlreadyShown()
                || lyricsLanguageSettingsVisible
                || (lyricsMetaTipPopup != null && lyricsMetaTipPopup.isShowing())) {
            return;
        }

        TextView tip = label(ui("lyrics.menu_tip"), 12f, Color.WHITE, AppFonts.semiBold(this));
        tip.setLineSpacing(dp(2), 1f);
        tip.setPadding(dp(13), dp(10), dp(13), dp(10));
        tip.setBackground(roundDrawable(Color.argb(232, 18, 20, 30), dp(13)));
        tip.setOnClickListener(view -> dismissLyricsMetaTip());

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int popupWidth = Math.max(dp(210), Math.min(dp(278), screenWidth - dp(48)));
        PopupWindow popup = new PopupWindow(
                tip,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false
        );
        popup.setOutsideTouchable(false);
        popup.setTouchable(true);
        popup.setClippingEnabled(true);
        popup.setBackgroundDrawable(roundDrawable(Color.TRANSPARENT, 0f));
        popup.setOnDismissListener(() -> {
            if (lyricsMetaTipPopup == popup) {
                lyricsMetaTipPopup = null;
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popup.setElevation(dp(10));
        }

        try {
            lyricsMetaTipPopup = popup;
            popup.showAsDropDown(lyricsArtistView, 0, dp(7), Gravity.START);
            markLyricsMetaTipShown();
            handler.postDelayed(() -> {
                if (lyricsMetaTipPopup == popup && popup.isShowing()) {
                    popup.dismiss();
                }
            }, 5_200L);
        } catch (RuntimeException ignored) {
            lyricsMetaTipPopup = null;
        }
    }

    private boolean lyricsMetaTipAlreadyShown() {
        return getSharedPreferences(UI_HINTS_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_LYRICS_META_MENU_TIP_SHOWN, false);
    }

    private void markLyricsMetaTipShown() {
        SharedPreferences prefs = getSharedPreferences(UI_HINTS_PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_LYRICS_META_MENU_TIP_SHOWN, true).apply();
    }

    private void dismissLyricsMetaTip() {
        if (lyricsMetaTipPopup != null) {
            lyricsMetaTipPopup.dismiss();
            lyricsMetaTipPopup = null;
        }
    }

    private boolean isSettingsPanelVisible() {
        return settingsPanel != null && settingsPanel.getVisibility() == View.VISIBLE;
    }

    private void showSettingsPanel(boolean show) {
        if (settingsPanel == null) {
            return;
        }
        if (show && !isInitialSetupComplete()) {
            updateSpotifySetupGate(true);
            Toast.makeText(this, ui("toast.setup_required"), Toast.LENGTH_SHORT).show();
            return;
        }
        lastBackPressElapsedMs = 0L;
        settingsPanel.animate().cancel();
        if (show) {
            handler.removeCallbacks(landscapeControlsAutoHideRunnable);
            setLandscapeControlsVisible(true, true);
            populateAiSettingsUi();
            settingsPanel.setVisibility(View.VISIBLE);
            settingsPanel.setAlpha(0f);
            settingsPanel.bringToFront();
            settingsPanel.animate().alpha(1f).setDuration(180L).start();
        } else {
            settingsPanel.animate()
                    .alpha(0f)
                    .setDuration(160L)
                    .withEndAction(() -> {
                        settingsPanel.setVisibility(View.GONE);
                        settingsPanel.setAlpha(1f);
                        applyLandscapeControlsAutoHideSetting();
                    })
                    .start();
        }
    }

    private boolean isSpotifyApiConfigured() {
        return aiLyricsSettings != null && aiLyricsSettings.snapshot().hasSpotifyApiCredentials();
    }

    private boolean isInitialSetupComplete() {
        return isSpotifyApiConfigured() && NowPlayingService.isNotificationAccessEnabled(this);
    }

    private boolean isSpotifySetupPanelVisible() {
        return spotifySetupPanel != null && spotifySetupPanel.getVisibility() == View.VISIBLE;
    }

    private void updateSpotifySetupGate(boolean animate) {
        if (spotifySetupPanel == null) {
            return;
        }
        boolean configured = isInitialSetupComplete();
        spotifySetupPanel.animate().cancel();
        if (configured) {
            spotifySetupRequired = false;
            stopOnboardingWelcomeRotation();
            if (spotifySetupPanel.getVisibility() != View.VISIBLE) {
                return;
            }
            if (animate) {
                spotifySetupPanel.animate()
                        .alpha(0f)
                        .setDuration(180L)
                        .withEndAction(() -> {
                            spotifySetupPanel.setVisibility(View.GONE);
                            spotifySetupPanel.setAlpha(1f);
                        })
                        .start();
            } else {
                spotifySetupPanel.setVisibility(View.GONE);
                spotifySetupPanel.setAlpha(1f);
            }
            return;
        }

        if (settingsPanel != null) {
            settingsPanel.animate().cancel();
            settingsPanel.setVisibility(View.GONE);
            settingsPanel.setAlpha(1f);
        }
        if (debugPanel != null) {
            debugPanel.setVisibility(View.GONE);
        }
        if (lyricsPage != null) {
            lyricsPage.animate().cancel();
            lyricsPage.setVisibility(View.GONE);
            lyricsPage.setTranslationY(0f);
            lyricsPage.setAlpha(1f);
            lyricsPageVisible = false;
            setLyricsPageCornerRadius(0);
        }
        if (mainPage != null) {
            mainPage.animate().cancel();
            mainPage.setAlpha(1f);
        }
        if (spotifySetupPanel.getVisibility() != View.VISIBLE) {
            populateSpotifyCredentialInputs(aiLyricsSettings == null ? null : aiLyricsSettings.snapshot());
            if (isSpotifyApiConfigured() && !NowPlayingService.isNotificationAccessEnabled(this)) {
                onboardingStep = 1;
            }
            showOnboardingStep(onboardingStep);
            spotifySetupPanel.setVisibility(View.VISIBLE);
            spotifySetupPanel.setAlpha(animate ? 0f : 1f);
        }
        spotifySetupPanel.bringToFront();
        startOnboardingWelcomeRotation();
        if (animate) {
            spotifySetupPanel.animate().alpha(1f).setDuration(180L).start();
        }
    }

    private void requestMetadataTranslation(boolean clearCache) {
        if (currentTrack == null || !currentTrack.hasUsableMetadata() || aiLyricsRepository == null || aiLyricsSettings == null) {
            return;
        }
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
        String source = effectiveSelectedSourceLang();
        String target = snapshot.resolveTargetLanguage(source);
        if (!snapshot.metadataTranslationEnabled
                || AiLyricsSettings.isSameLanguage(source, target)
                || !snapshot.hasApiKey()) {
            translatedTrackTitle = "";
            translatedTrackArtist = "";
            updateTrackMetadataTextViews(currentTrack);
            return;
        }
        aiLyricsRepository.loadMetadataTranslation(currentTrack, snapshot, source, clearCache, this);
    }

    private void requestAiLyrics(boolean clearCache) {
        if (currentTrack == null || currentBaseLyricsResult == null || currentBaseLyricsResult.lines.isEmpty()) {
            aiLyricsGenerating = false;
            setLyricsSupplementLoading(false, false, false);
            if (aiSettingsStatusView != null) {
                aiSettingsStatusView.setText(ui("status.no_lyrics_to_apply"));
            }
            return;
        }
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
        String source = effectiveSelectedSourceLang();
        AiLyricsSettings.LanguageRule rule = snapshot.ruleForSource(source);
        String target = snapshot.resolveTargetLanguage(source);
        boolean translationSkipped = snapshot.shouldSkipTranslation(source, target);
        boolean wantsAiTask = rule.pronunciationEnabled || (rule.translationEnabled && !translationSkipped);
        if (!snapshot.enabled()) {
            aiLyricsGenerating = false;
            currentLyricsResult = currentBaseLyricsResult;
            setLyricsResultOnViews(currentLyricsResult);
            setLyricsSupplementLoading(false, false, false);
            updateLyricPreview(currentLyricsPlaybackPosition(currentTrack));
            if (aiSettingsStatusView != null) {
                aiSettingsStatusView.setText(ui("status.ai_disabled"));
            }
            requestJapaneseFurigana(clearCache);
            return;
        }
        if (!wantsAiTask) {
            aiLyricsGenerating = false;
            currentLyricsResult = currentBaseLyricsResult;
            setLyricsResultOnViews(currentLyricsResult);
            setLyricsSupplementLoading(false, false, false);
            updateLyricPreview(currentLyricsPlaybackPosition(currentTrack));
            if (aiSettingsStatusView != null) {
                aiSettingsStatusView.setText(ui("status.ai_applied"));
            }
            requestJapaneseFurigana(clearCache);
            return;
        }
        if (!snapshot.hasApiKey()) {
            aiLyricsGenerating = false;
            currentLyricsResult = currentBaseLyricsResult;
            setLyricsResultOnViews(currentLyricsResult);
            setLyricsSupplementLoading(false, false, false);
            updateLyricPreview(currentLyricsPlaybackPosition(currentTrack));
            if (aiSettingsStatusView != null) {
                aiSettingsStatusView.setText(ui("status.ai_key_needed"));
            }
            requestJapaneseFurigana(clearCache);
            return;
        }
        if (clearCache) {
            aiLyricsRepository.clearMemoryCache();
            if (furiganaRepository != null) {
                furiganaRepository.clearMemoryCache();
            }
        }
        if (aiSettingsStatusView != null) {
            aiSettingsStatusView.setText(ui("status.ai_generating"));
        }
        aiLyricsGenerating = true;
        setLyricsSupplementLoading(
                rule.pronunciationEnabled,
                rule.translationEnabled && !translationSkipped,
                shouldGenerateJapaneseFurigana(snapshot, source)
        );
        updateLyricPreview(currentLyricsPlaybackPosition(currentTrack));
        requestJapaneseFurigana(clearCache);
        aiLyricsRepository.loadSupplements(currentTrack, currentBaseLyricsResult, snapshot, source, clearCache, this);
    }

    private void requestJapaneseFurigana(boolean clearCache) {
        if (currentTrack == null
                || currentBaseLyricsResult == null
                || currentBaseLyricsResult.lines.isEmpty()
                || furiganaRepository == null
                || aiLyricsSettings == null) {
            setLyricsSupplementLoading(
                    lyricsSupplementPronunciationLoading,
                    lyricsSupplementTranslationLoading,
                    false
            );
            return;
        }
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
        String source = effectiveSelectedSourceLang();
        if (!shouldGenerateJapaneseFurigana(snapshot, source)) {
            setLyricsSupplementLoading(
                    lyricsSupplementPronunciationLoading,
                    lyricsSupplementTranslationLoading,
                    false
            );
            return;
        }
        if (clearCache) {
            currentFuriganaResult = null;
            currentFuriganaKey = "";
            furiganaRepository.clearMemoryCache();
        }
        setLyricsSupplementLoading(
                lyricsSupplementPronunciationLoading,
                lyricsSupplementTranslationLoading,
                true
        );
        furiganaRepository.loadFurigana(currentTrack, currentBaseLyricsResult, clearCache, this);
    }

    private LyricsResult mergeCurrentFuriganaInto(LyricsResult target) {
        if (target == null) {
            return null;
        }
        if (currentFuriganaResult == null || !currentLyricsKey.equals(currentFuriganaKey)) {
            return target;
        }
        return mergeFuriganaIntoResult(target, currentFuriganaResult);
    }

    private LyricsResult mergeFuriganaIntoResult(LyricsResult target, LyricsResult furiganaSource) {
        if (target == null || furiganaSource == null || target.lines.isEmpty()) {
            return target;
        }
        List<LyricsLine> lines = new ArrayList<>();
        int count = target.lines.size();
        for (int index = 0; index < count; index++) {
            LyricsLine targetLine = target.lines.get(index);
            LyricsLine furiganaLine = index < furiganaSource.lines.size()
                    ? furiganaSource.lines.get(index)
                    : null;
            lines.add(mergeFuriganaIntoLine(targetLine, furiganaLine));
        }
        return new LyricsResult(
                lines,
                target.providerLabel,
                target.detail,
                target.karaoke,
                target.isrc,
                target.spotifyTrackId,
                target.contributors
        );
    }

    private LyricsLine mergeFuriganaIntoLine(LyricsLine target, LyricsLine furiganaSource) {
        if (target == null) {
            return null;
        }
        String lineFurigana = nonEmpty(
                furiganaSource == null ? "" : furiganaSource.furiganaText,
                target.furiganaText
        );
        if (target.vocalParts == null || target.vocalParts.isEmpty()) {
            return target.withSupplements(target.pronunciationText, target.translationText, lineFurigana);
        }
        List<LyricsLine.VocalPart> parts = new ArrayList<>();
        for (int index = 0; index < target.vocalParts.size(); index++) {
            LyricsLine.VocalPart targetPart = target.vocalParts.get(index);
            LyricsLine.VocalPart sourcePart = furiganaSource != null
                    && furiganaSource.vocalParts != null
                    && index < furiganaSource.vocalParts.size()
                    ? furiganaSource.vocalParts.get(index)
                    : null;
            String partFurigana = nonEmpty(
                    sourcePart == null ? "" : sourcePart.furiganaText,
                    targetPart.furiganaText
            );
            if (partFurigana.isEmpty() && target.vocalParts.size() == 1) {
                partFurigana = lineFurigana;
            }
            parts.add(targetPart.withSupplements(
                    targetPart.pronunciationText,
                    targetPart.translationText,
                    partFurigana
            ));
        }
        return new LyricsLine(
                target.startTimeMs,
                target.endTimeMs,
                target.text,
                target.syllables,
                target.speaker,
                target.kind,
                parts,
                target.pronunciationText,
                target.translationText,
                lineFurigana
        );
    }

    private String nonEmpty(String preferred, String fallback) {
        String value = preferred == null ? "" : preferred.trim();
        if (!value.isEmpty()) {
            return value;
        }
        return fallback == null ? "" : fallback.trim();
    }

    private void setLyricsSupplementLoading(boolean pronunciation, boolean translation) {
        setLyricsSupplementLoading(pronunciation, translation, false);
    }

    private void setLyricsSupplementLoading(boolean pronunciation, boolean translation, boolean furigana) {
        lyricsSupplementPronunciationLoading = pronunciation;
        lyricsSupplementTranslationLoading = translation;
        lyricsSupplementFuriganaLoading = furigana;
        if (lyricsView != null) {
            lyricsView.setSupplementLoading(pronunciation, translation);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setSupplementLoading(pronunciation, translation);
        }
        updateLyricsSupplementLoadingIndicator(pronunciation || translation || furigana);
    }

    private void updateLyricsSupplementLoadingIndicator(boolean visible) {
        setLoadingIndicatorVisible(lyricsSupplementLoadingIndicator, visible, true);
        setLoadingIndicatorVisible(landscapeLyricsSupplementLoadingIndicator, visible, true);
    }

    private void setLoadingIndicatorVisible(View indicator, boolean visible, boolean animate) {
        if (indicator == null) {
            return;
        }
        indicator.animate().cancel();
        if (visible) {
            if (indicator.getVisibility() != View.VISIBLE) {
                indicator.setAlpha(animate ? 0f : 1f);
                indicator.setScaleX(animate ? 0.96f : 1f);
                indicator.setScaleY(animate ? 0.96f : 1f);
                indicator.setVisibility(View.VISIBLE);
            }
            if (animate) {
                indicator.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160L)
                        .start();
            }
            return;
        }
        if (indicator.getVisibility() == View.VISIBLE) {
            if (!animate) {
                indicator.setVisibility(View.GONE);
                indicator.setAlpha(1f);
                indicator.setScaleX(1f);
                indicator.setScaleY(1f);
                return;
            }
            indicator.animate()
                    .alpha(0f)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(140L)
                    .withEndAction(() -> {
                        indicator.setVisibility(View.GONE);
                        indicator.setAlpha(1f);
                        indicator.setScaleX(1f);
                        indicator.setScaleY(1f);
                    })
                    .start();
        }
    }

    private void setLyricsResultOnViews(LyricsResult result) {
        if (lyricsView != null) {
            configureLyricsViewUiText(lyricsView);
            lyricsView.setResult(result);
        }
        if (landscapeLyricsView != null) {
            configureLyricsViewUiText(landscapeLyricsView);
            landscapeLyricsView.setResult(result);
        }
        updateLyricsContributorCredit(result);
    }

    private void updateLyricsContributorCredit(LyricsResult result) {
        if (lyricsContributorView == null) {
            return;
        }
        List<LyricsResult.SyncContributor> contributors = result == null
                ? Collections.emptyList()
                : result.contributors;
        if (contributors.isEmpty()) {
            lyricsContributorView.setVisibility(View.GONE);
            lyricsContributorView.setText("");
            lyricsContributorView.setOnClickListener(null);
            lyricsContributorView.setClickable(false);
            lyricsContributorView.setLinksClickable(false);
            lyricsContributorView.setMovementMethod(null);
            return;
        }

        int visibleContributorLimit = 3;
        boolean hasLinkedContributor = hasLinkedContributor(contributors, visibleContributorLimit);
        lyricsContributorView.setText(contributorCreditText(contributors, visibleContributorLimit, hasLinkedContributor));
        lyricsContributorView.setVisibility(View.VISIBLE);
        lyricsContributorView.setOnClickListener(null);
        if (!hasLinkedContributor) {
            lyricsContributorView.setMovementMethod(null);
            lyricsContributorView.setClickable(false);
            lyricsContributorView.setLinksClickable(false);
            lyricsContributorView.setTextColor(Color.argb(92, 255, 255, 255));
            return;
        }
        lyricsContributorView.setMovementMethod(LinkMovementMethod.getInstance());
        lyricsContributorView.setHighlightColor(Color.TRANSPARENT);
        lyricsContributorView.setLinksClickable(true);
        lyricsContributorView.setTextColor(Color.argb(118, 255, 255, 255));
        lyricsContributorView.setClickable(true);
    }

    private SpannableString contributorCreditText(
            List<LyricsResult.SyncContributor> contributors,
            int limit,
            boolean linked
    ) {
        String names = contributorNames(contributors, limit);
        SpannableString text = new SpannableString(uiFormat("lyrics.credit_sync_by_format", names));
        if (!linked || contributors == null || contributors.isEmpty()) {
            return text;
        }
        int namesStart = text.toString().indexOf(names);
        if (namesStart < 0) {
            return text;
        }
        int count = Math.min(Math.max(1, limit), contributors.size());
        int searchFrom = namesStart;
        for (int index = 0; index < count; index++) {
            LyricsResult.SyncContributor contributor = contributors.get(index);
            if (contributor == null || contributor.name.isEmpty()) {
                continue;
            }
            int start = text.toString().indexOf(contributor.name, searchFrom);
            if (start < 0) {
                continue;
            }
            int end = start + contributor.name.length();
            if (contributor.profileAvailable && !contributor.userHash.isEmpty()) {
                text.setSpan(new ClickableSpan() {
                    @Override
                    public void onClick(View widget) {
                        openSyncContributorProfile(contributor);
                    }

                    @Override
                    public void updateDrawState(TextPaint ds) {
                        super.updateDrawState(ds);
                        ds.setColor(Color.argb(150, 255, 255, 255));
                        ds.setUnderlineText(false);
                    }
                }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            searchFrom = end;
        }
        return text;
    }

    private String contributorNames(List<LyricsResult.SyncContributor> contributors, int limit) {
        if (contributors == null || contributors.isEmpty()) {
            return "";
        }
        int count = Math.min(Math.max(1, limit), contributors.size());
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < count; index++) {
            LyricsResult.SyncContributor contributor = contributors.get(index);
            if (contributor == null || contributor.name.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(contributor.name);
        }
        if (contributors.size() > count) {
            builder.append(" +").append(contributors.size() - count);
        }
        return builder.toString();
    }

    private boolean hasLinkedContributor(List<LyricsResult.SyncContributor> contributors, int limit) {
        if (contributors == null) {
            return false;
        }
        int count = Math.min(Math.max(1, limit), contributors.size());
        for (int index = 0; index < count; index++) {
            LyricsResult.SyncContributor contributor = contributors.get(index);
            if (contributor != null && contributor.profileAvailable && !contributor.userHash.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void openSyncContributorProfile(LyricsResult.SyncContributor contributor) {
        if (contributor == null || contributor.userHash.isEmpty()) {
            return;
        }
        String fallbackUrl = syncContributorProfileUrl(contributor.userHash);
        String cachedUrl = creatorProfileUrlCache.get(contributor.userHash);
        if (cachedUrl != null && !cachedUrl.isEmpty()) {
            openContributorProfileUrl(cachedUrl);
            return;
        }
        seekExecutor.execute(() -> {
            String url = fallbackUrl;
            try {
                url = fetchSyncContributorProfileUrl(contributor.userHash, fallbackUrl);
                creatorProfileUrlCache.put(contributor.userHash, url);
            } catch (Exception error) {
                String message = "sync creator profile lookup failed: " + error.getMessage();
                handler.post(() -> appendLog(message));
            }
            String finalUrl = url;
            handler.post(() -> openContributorProfileUrl(finalUrl));
        });
    }

    private void openContributorProfileUrl(String url) {
        openInAppBrowser(url);
    }

    private String fetchSyncContributorProfileUrl(String userHash, String fallbackUrl) throws Exception {
        URL endpoint = new URL(CREATOR_PROFILE_ENDPOINT + "?userHash=" + Uri.encode(userHash));
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        connection.setConnectTimeout(8_000);
        connection.setReadTimeout(12_000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android/0.1");
        connection.setRequestProperty("Origin", SYNC_DATA_SPOTIFY_ORIGIN);
        connection.setRequestProperty("Referer", SYNC_DATA_SPOTIFY_REFERER);
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
        connection.setRequestProperty("Pragma", "no-cache");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            JSONObject root = new JSONObject(readSyncContributorProfileBody(connection.getInputStream()));
            JSONObject data = root.optJSONObject("data");
            if (!root.optBoolean("success", false) || data == null) {
                return fallbackUrl;
            }
            String identifier = "";
            JSONObject account = data.optJSONObject("account");
            if (account != null) {
                identifier = account.optString("username", "").trim();
            }
            if (identifier.isEmpty()) {
                identifier = data.optString("nickname", "").trim();
            }
            if (identifier.isEmpty()) {
                identifier = data.optString("userHash", "").trim();
            }
            if (identifier.isEmpty()) {
                return fallbackUrl;
            }
            return syncContributorProfileUrl(identifier);
        } finally {
            connection.disconnect();
        }
    }

    private String readSyncContributorProfileBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }

    private String syncContributorProfileUrl(String identifier) {
        String safeIdentifier = identifier == null ? "" : identifier.replaceFirst("^@+", "").trim();
        if (safeIdentifier.isEmpty()) {
            return "https://lyrics.ivl.is";
        }
        return "https://lyrics.ivl.is/@" + Uri.encode(safeIdentifier);
    }

    private void configureLyricsViewUiText(LyricsView view) {
        if (view == null) {
            return;
        }
        view.setUiText(
                ui("status.lyrics_loading"),
                ui("lyrics.empty_none"),
                ui("interlude.prelude"),
                ui("interlude.break"),
                ui("interlude.postlude")
        );
    }

    private void setLyricsTrackDurationOnViews(long durationMs) {
        if (lyricsView != null) {
            lyricsView.setTrackDuration(durationMs);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setTrackDuration(durationMs);
        }
    }

    private void setLyricsPlaybackPositionOnViews(long positionMs) {
        if (lyricsView != null) {
            lyricsView.setPlaybackPosition(positionMs);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setPlaybackPosition(positionMs);
        }
    }

    private void setAutoInstrumentalBreakOnViews(boolean enabled) {
        if (lyricsView != null) {
            lyricsView.setAutoInstrumentalBreakEnabled(enabled);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setAutoInstrumentalBreakEnabled(enabled);
        }
    }

    private void setInterludeLabelsOnViews(boolean enabled) {
        if (lyricsView != null) {
            lyricsView.setInterludeLabelsEnabled(enabled);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setInterludeLabelsEnabled(enabled);
        }
    }

    private void setSyncedLyricsKaraokeAnimationOnViews(boolean enabled) {
        if (lyricsView != null) {
            lyricsView.setSyncedLyricsKaraokeAnimationEnabled(enabled);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setSyncedLyricsKaraokeAnimationEnabled(enabled);
        }
    }

    private void setKaraokeBounceEffectOnViews(boolean enabled) {
        if (lyricsView != null) {
            lyricsView.setKaraokeBounceEffectEnabled(enabled);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setKaraokeBounceEffectEnabled(enabled);
        }
        if (lyricPreviewView != null) {
            lyricPreviewView.setKaraokeBounceEffectEnabled(enabled);
        }
    }

    private void setJapaneseFuriganaOnViews(boolean enabled) {
        if (lyricsView != null) {
            lyricsView.setJapaneseFuriganaEnabled(enabled);
        }
        if (landscapeLyricsView != null) {
            landscapeLyricsView.setJapaneseFuriganaEnabled(enabled);
        }
    }

    private String textOf(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void updatePermissionState() {
        if (permissionButton == null) {
            updateOnboardingPermissionState();
            updateOverlayPermissionButton();
            return;
        }
        boolean enabled = NowPlayingService.isNotificationAccessEnabled(this);
        permissionButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
        updateOnboardingPermissionState();
        updateOverlayPermissionButton();
    }

    private void updateOnboardingPermissionState() {
        boolean enabled = NowPlayingService.isNotificationAccessEnabled(this);
        if (onboardingPermissionStatusView != null) {
            onboardingPermissionStatusView.setText(enabled
                    ? ui("onboarding.permission_status_enabled")
                    : ui("onboarding.permission_status_required"));
            onboardingPermissionStatusView.setTextColor(enabled
                    ? Color.rgb(142, 236, 198)
                    : Color.WHITE);
        }
        if (onboardingNextButton != null && onboardingStep == 1) {
            onboardingNextButton.setText(onboardingNextButtonText());
            onboardingNextButton.setEnabled(true);
            onboardingNextButton.setAlpha(1f);
        }
    }

    private void openMediaPermissionSettings() {
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void updateOverlayPermissionButton() {
        if (spotifyDetectionPermissionButton != null) {
            boolean enabled = isSpotifyDetectionAccessEnabled();
            spotifyDetectionPermissionButton.setText(enabled
                    ? ui("button.accessibility_permission_enabled")
                    : ui("button.open_accessibility_permission"));
            spotifyDetectionPermissionButton.setAlpha(enabled ? 0.72f : 1f);
        }
        if (overlayPermissionButton != null) {
            boolean enabled = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
            overlayPermissionButton.setText(enabled
                    ? ui("button.overlay_permission_enabled")
                    : ui("button.open_overlay_permission"));
            overlayPermissionButton.setAlpha(enabled ? 0.72f : 1f);
        }
    }

    private boolean isSpotifyDetectionAccessEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (manager == null) {
            return false;
        }
        ComponentName expected = new ComponentName(this, SpotifyForegroundAccessibilityService.class);
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC
        );
        if (services == null) {
            return false;
        }
        for (AccessibilityServiceInfo service : services) {
            if (service == null || service.getId() == null) {
                continue;
            }
            ComponentName componentName = ComponentName.unflattenFromString(service.getId());
            if (expected.equals(componentName)) {
                return true;
            }
        }
        return false;
    }

    private void openOverlayPermissionSettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        if (!tryStartActivity(intent)) {
            showSavedToast(ui("toast.overlay_permission_needed"));
        }
    }

    private void openSpotifyDetectionPermissionSettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        if (!tryStartActivity(intent)) {
            showSavedToast(ui("toast.accessibility_permission_needed"));
        }
    }

    private void updatePlaybackUi() {
        TrackSnapshot snapshot = currentTrack != null ? currentTrack : NowPlayingService.getLatestSnapshot();
        if (snapshot == null || !snapshot.hasUsableMetadata()) {
            return;
        }
        long position = currentPlaybackPosition(snapshot);
        long lyricsPosition = lyricsPlaybackPosition(position, snapshot.durationMs);
        setLyricsPlaybackPositionOnViews(lyricsPosition);
        playerProgressView.setProgress(position, snapshot.durationMs);
        updateYouTubeBackgroundPlaybackState();

        long now = SystemClock.uptimeMillis();
        if (now - lastProgressUiUpdateMs >= 250L) {
            lastProgressUiUpdateMs = now;
            updateProgressViews(position, snapshot.durationMs);
            updateLyricPreview(lyricsPosition);
            playPauseButton.setPlaying(snapshot.playing);
        }
    }

    private void updateProgressViews(long position, long duration) {
        if (playerProgressView != null) {
            playerProgressView.setProgress(position, duration);
        }
        if (elapsedView != null) {
            elapsedView.setText(formatTime(position));
        }
        if (remainingView != null) {
            remainingView.setText(formatRemaining(position, duration));
        }
        if (debugProgressView != null) {
            debugProgressView.setText(formatTime(position) + " / " + formatTime(duration));
        }
    }

    private void updateLyricPreview(long positionMs) {
        if (lyricPreviewView == null) {
            return;
        }
        int previewItems = aiLyricsSettings == null
                ? AiLyricsSettings.PREVIEW_ITEM_ORIGINAL
                : aiLyricsSettings.snapshot().previewItems;
        if (previewItems == AiLyricsSettings.PREVIEW_ITEM_NONE) {
            if (lyricPreviewContainer != null) {
                lyricPreviewContainer.setVisibility(View.GONE);
            }
            lyricPreviewView.clear();
            return;
        }
        if (lyricPreviewContainer != null) {
            lyricPreviewContainer.setVisibility(View.VISIBLE);
        }
        if (currentLyricsResult == null || currentLyricsResult.lines.isEmpty()) {
            String detail = currentLyricsResult == null ? "" : currentLyricsResult.detail;
            List<MainLyricPreviewView.PreviewLine> rows = new ArrayList<>();
            rows.add(emptyPreviewLine(detail));
            lyricPreviewView.setPreview(rows, positionMs, 0L, 0L, isLoadingLyricsPreview(detail));
            return;
        }
        PreviewEntry entry = previewEntryAt(positionMs);
        if (entry == null) {
            List<MainLyricPreviewView.PreviewLine> rows = new ArrayList<>();
            rows.add(new MainLyricPreviewView.PreviewLine(ui("status.lyrics_waiting"), true));
            lyricPreviewView.setPreview(rows, positionMs, 0L, 0L, false);
            return;
        }
        if (entry.isInterlude()) {
            List<MainLyricPreviewView.PreviewLine> rows = new ArrayList<>();
            rows.add(MainLyricPreviewView.PreviewLine.interlude(interludePreviewLabel(entry.interludeKind)));
            lyricPreviewView.setPreview(
                    rows,
                    positionMs,
                    entry.startTimeMs,
                    entry.endTimeMs,
                    currentTrack != null && currentTrack.playing
            );
            return;
        }
        LyricsLine line = entry.line;
        List<MainLyricPreviewView.PreviewLine> rows = previewLines(line, previewItems);
        lyricPreviewView.setPreview(
                rows,
                positionMs,
                line.startTimeMs,
                line.endTimeMs,
                currentTrack != null && currentTrack.playing
        );
    }

    private MainLyricPreviewView.PreviewLine emptyPreviewLine(String detail) {
        if (isLoadingLyricsPreview(detail)) {
            return MainLyricPreviewView.PreviewLine.loading(ui("status.lyrics_loading"));
        }
        String text = detail == null || detail.isEmpty() ? ui("status.lyrics_waiting") : detail;
        return new MainLyricPreviewView.PreviewLine(text, true);
    }

    private boolean isLoadingLyricsPreview(String detail) {
        String value = detail == null ? "" : detail.trim().toLowerCase(Locale.ROOT);
        return value.contains("loading") || value.contains("불러");
    }

    private PreviewEntry previewEntryAt(long positionMs) {
        List<LyricsLine> lines = currentLyricsResult.lines;
        int lineCount = lines.size();
        for (int index = 0; index < lineCount; index++) {
            LyricsLine line = lines.get(index);
            if (line == null || !line.isTimed()) {
                continue;
            }
            PreviewEntry markerEntry = markerInterludeEntry(line, index, lineCount);
            if (markerEntry != null && markerEntry.contains(positionMs)) {
                return markerEntry;
            }
        }

        for (int index = 0; index < lineCount; index++) {
            LyricsLine line = lines.get(index);
            if (line == null) {
                continue;
            }
            if (!line.isTimed() && !isPreviewInterludeMarkerText(previewInterludeCandidateText(line))) {
                return PreviewEntry.line(line);
            }
        }

        for (int index = 0; index < lineCount; index++) {
            LyricsLine line = lines.get(index);
            if (line == null || !line.isTimed() || isPreviewInterludeMarkerText(previewInterludeCandidateText(line))) {
                continue;
            }
            if (positionMs >= line.startTimeMs && positionMs < line.endTimeMs) {
                return PreviewEntry.line(line);
            }
        }

        PreviewEntry prelude = preludeEntry(positionMs);
        if (prelude != null) {
            return prelude;
        }

        PreviewEntry trailingInterlude = trailingInterludeEntry(positionMs);
        if (trailingInterlude != null) {
            return trailingInterlude;
        }

        PreviewEntry fallback = null;
        for (LyricsLine line : lines) {
            if (line == null || !line.isTimed() || isPreviewInterludeMarkerText(previewInterludeCandidateText(line))) {
                continue;
            }
            if (positionMs >= line.startTimeMs) {
                fallback = PreviewEntry.line(line);
            }
        }
        return fallback;
    }

    private PreviewEntry markerInterludeEntry(LyricsLine line, int lineIndex, int lineCount) {
        if (line == null || !line.isTimed() || !isPreviewInterludeMarkerText(previewInterludeCandidateText(line))) {
            return null;
        }
        long endTimeMs = Math.max(line.endTimeMs, nextPreviewRenderableLineStartAfter(lineIndex));
        long durationMs = endTimeMs > line.startTimeMs ? endTimeMs - line.startTimeMs : 0L;
        if (durationMs <= PREVIEW_INTERLUDE_MIN_DURATION_MS) {
            return null;
        }
        return PreviewEntry.interlude(line.startTimeMs, endTimeMs, previewInstrumentalKind(lineIndex, lineCount));
    }

    private PreviewEntry preludeEntry(long positionMs) {
        int firstIndex = firstPreviewRenderableLineIndex();
        if (firstIndex < 0) {
            return null;
        }
        LyricsLine firstLine = currentLyricsResult.lines.get(firstIndex);
        if (firstLine == null || !firstLine.isTimed() || positionMs >= firstLine.startTimeMs) {
            return null;
        }
        long startTimeMs = 0L;
        long endTimeMs = firstLine.startTimeMs;
        if (endTimeMs - startTimeMs <= PREVIEW_INTERLUDE_MIN_DURATION_MS) {
            return null;
        }
        return PreviewEntry.interlude(startTimeMs, endTimeMs, "prelude");
    }

    private PreviewEntry trailingInterludeEntry(long positionMs) {
        if (!previewAutoInstrumentalBreakEnabled()) {
            return null;
        }
        List<LyricsLine> lines = currentLyricsResult.lines;
        int lineCount = lines.size();
        for (int index = 0; index < lineCount; index++) {
            LyricsLine line = lines.get(index);
            if (line == null || !line.isTimed() || isPreviewInterludeMarkerText(previewInterludeCandidateText(line))) {
                continue;
            }
            long lyricEndTime = previewLastLyricEndTime(line);
            if (lyricEndTime < 0L) {
                continue;
            }
            long startTimeMs = lyricEndTime + PREVIEW_TRAILING_INTERLUDE_DELAY_MS;
            long nextLyricStartTime = nextPreviewRenderableLineStartAfter(index);
            long endTimeMs = nextLyricStartTime > startTimeMs
                    ? nextLyricStartTime
                    : (index >= Math.max(0, lineCount - 1) ? previewTrackDurationMs() : 0L);
            long durationMs = endTimeMs > startTimeMs ? endTimeMs - startTimeMs : 0L;
            if (durationMs <= PREVIEW_INTERLUDE_MIN_DURATION_MS) {
                continue;
            }
            if (positionMs >= startTimeMs && positionMs < endTimeMs) {
                return PreviewEntry.interlude(startTimeMs, endTimeMs, nextLyricStartTime > 0L ? "break" : "postlude");
            }
        }
        return null;
    }

    private int firstPreviewRenderableLineIndex() {
        List<LyricsLine> lines = currentLyricsResult.lines;
        for (int index = 0; index < lines.size(); index++) {
            LyricsLine line = lines.get(index);
            if (line == null || !line.isTimed()) {
                continue;
            }
            if (!isPreviewInterludeMarkerText(previewInterludeCandidateText(line))) {
                return index;
            }
        }
        return -1;
    }

    private long nextPreviewRenderableLineStartAfter(int lineIndex) {
        List<LyricsLine> lines = currentLyricsResult.lines;
        for (int index = Math.max(0, lineIndex + 1); index < lines.size(); index++) {
            LyricsLine candidate = lines.get(index);
            if (candidate == null || !candidate.isTimed()) {
                continue;
            }
            if (isPreviewInterludeMarkerText(previewInterludeCandidateText(candidate))) {
                continue;
            }
            return candidate.startTimeMs;
        }
        return 0L;
    }

    private long previewLastLyricEndTime(LyricsLine line) {
        if (line == null) {
            return -1L;
        }
        long lastEnd = previewMaxSyllableEnd(line.syllables, line.endTimeMs);
        if (line.vocalParts != null) {
            for (LyricsLine.VocalPart part : line.vocalParts) {
                lastEnd = Math.max(lastEnd, previewMaxSyllableEnd(part.syllables, line.endTimeMs));
            }
        }
        if (lastEnd >= 0L) {
            return lastEnd;
        }
        return line.endTimeMs > line.startTimeMs ? line.endTimeMs : -1L;
    }

    private long previewMaxSyllableEnd(List<LyricsLine.Syllable> syllables, long fallbackLineEndMs) {
        if (syllables == null || syllables.isEmpty()) {
            return -1L;
        }
        long lastEnd = -1L;
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null) {
                continue;
            }
            long endTime = syllable.endTimeMs > syllable.startTimeMs ? syllable.endTimeMs : fallbackLineEndMs;
            if (endTime >= syllable.startTimeMs) {
                lastEnd = Math.max(lastEnd, endTime);
            }
        }
        return lastEnd;
    }

    private String previewInstrumentalKind(int lineIndex, int lineCount) {
        if (lineIndex == 0) {
            return "prelude";
        }
        if (lineIndex == Math.max(0, lineCount - 1)) {
            return "postlude";
        }
        return "break";
    }

    private String interludePreviewLabel(String kind) {
        if (!interludeLabelsEnabled()) {
            return "";
        }
        if ("prelude".equals(kind)) {
            return ui("interlude.prelude");
        }
        if ("postlude".equals(kind)) {
            return ui("interlude.postlude");
        }
        return ui("interlude.break");
    }

    private boolean interludeLabelsEnabled() {
        return aiLyricsSettings == null || aiLyricsSettings.snapshot().interludeLabelsEnabled;
    }

    private String previewInterludeCandidateText(LyricsLine line) {
        if (line == null) {
            return "";
        }
        String text = line.text == null ? "" : line.text;
        if (!text.trim().isEmpty()) {
            return text;
        }
        StringBuilder builder = new StringBuilder();
        if (line.vocalParts != null) {
            for (LyricsLine.VocalPart part : line.vocalParts) {
                if (part != null && part.text != null) {
                    builder.append(part.text);
                }
            }
        }
        return builder.toString();
    }

    private boolean isPreviewInterludeMarkerText(String text) {
        String normalized = text == null ? "" : text
                .replace("&nbsp;", " ")
                .replace("&NBSP;", " ")
                .trim();
        if (normalized.isEmpty()) {
            return true;
        }
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            if (!isPreviewInterludeMarkerCodePoint(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private boolean isPreviewInterludeMarkerCodePoint(int codePoint) {
        return Character.isWhitespace(codePoint)
                || codePoint == 0x00A0
                || (codePoint >= 0x200B && codePoint <= 0x200D)
                || codePoint == 0xFEFF
                || (codePoint >= 0x2669 && codePoint <= 0x266C);
    }

    private boolean previewAutoInstrumentalBreakEnabled() {
        return aiLyricsSettings == null || aiLyricsSettings.snapshot().autoInstrumentalBreakEnabled;
    }

    private long previewTrackDurationMs() {
        return currentTrack == null ? 0L : currentTrack.durationMs;
    }

    private List<MainLyricPreviewView.PreviewLine> previewLines(LyricsLine line, int previewItems) {
        List<MainLyricPreviewView.PreviewLine> rows = new ArrayList<>();
        PreviewText original = originalPreviewText(line);
        if (AiLyricsSettings.previewItemEnabled(previewItems, AiLyricsSettings.PREVIEW_ITEM_ORIGINAL)) {
            addPreviewRow(rows, original.text, original.rubyText, original.syllables, original.kind, AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL);
        }
        if (AiLyricsSettings.previewItemEnabled(previewItems, AiLyricsSettings.PREVIEW_ITEM_PRONUNCIATION)) {
            addSupplementPreviewRow(
                    rows,
                    line.pronunciationText,
                    ui("loading.pronunciation"),
                    original.text,
                    original.rubyText,
                    original.syllables,
                    original.kind,
                    isPreviewSupplementGenerating(AiLyricsSettings.PREVIEW_ITEM_PRONUNCIATION),
                    AiLyricsSettings.TYPO_MAIN_PREVIEW_PRONUNCIATION
            );
        }
        if (AiLyricsSettings.previewItemEnabled(previewItems, AiLyricsSettings.PREVIEW_ITEM_TRANSLATION)) {
            addSupplementPreviewRow(
                    rows,
                    line.translationText,
                    ui("loading.translation"),
                    original.text,
                    original.rubyText,
                    original.syllables,
                    original.kind,
                    isPreviewSupplementGenerating(AiLyricsSettings.PREVIEW_ITEM_TRANSLATION),
                    AiLyricsSettings.TYPO_MAIN_PREVIEW_TRANSLATION
            );
        }
        if (rows.isEmpty()) {
            addPreviewRow(rows, original.text, original.rubyText, original.syllables, original.kind, AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL);
        }
        return rows;
    }

    private void addSupplementPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            String generatingText,
            String fallback,
            String fallbackRubyText,
            List<LyricsLine.Syllable> fallbackSyllables,
            String fallbackKind,
            boolean generating,
            String slotId
    ) {
        String value = text == null ? "" : text.trim();
        String rubyText = "";
        List<LyricsLine.Syllable> syllables = Collections.emptyList();
        String kind = "vocal";
        if (value.isEmpty()) {
            if (generating) {
                value = generatingText;
            } else {
                value = fallback;
                rubyText = fallbackRubyText == null ? "" : fallbackRubyText.trim();
                syllables = fallbackSyllables == null ? Collections.emptyList() : fallbackSyllables;
                kind = fallbackKind;
            }
        }
        if (samePreviewTextAlreadyShown(rows, value)) {
            return;
        }
        addPreviewRow(rows, value, rubyText, syllables, kind, slotId);
    }

    private void addPreviewRow(List<MainLyricPreviewView.PreviewLine> rows, String text) {
        addPreviewRow(rows, text, Collections.emptyList(), "vocal");
    }

    private void addPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            List<LyricsLine.Syllable> syllables
    ) {
        addPreviewRow(rows, text, syllables, "vocal");
    }

    private void addPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            List<LyricsLine.Syllable> syllables,
            String kind
    ) {
        addPreviewRow(rows, text, syllables, kind, rows.isEmpty()
                ? AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL
                : AiLyricsSettings.TYPO_MAIN_PREVIEW_PRONUNCIATION);
    }

    private void addPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            List<LyricsLine.Syllable> syllables,
            String kind,
            String slotId
    ) {
        addPreviewRow(rows, text, "", syllables, kind, slotId);
    }

    private void addPreviewRow(
            List<MainLyricPreviewView.PreviewLine> rows,
            String text,
            String rubyText,
            List<LyricsLine.Syllable> syllables,
            String kind,
            String slotId
    ) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return;
        }
        rows.add(new MainLyricPreviewView.PreviewLine(value, rubyText, rows.isEmpty(), syllables, kind, slotId));
    }

    private boolean samePreviewTextAlreadyShown(List<MainLyricPreviewView.PreviewLine> rows, String text) {
        String value = text == null ? "" : text.trim();
        for (MainLyricPreviewView.PreviewLine row : rows) {
            if (row.text.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPreviewSupplementGenerating(int item) {
        if (!aiLyricsGenerating || aiLyricsSettings == null) {
            return false;
        }
        AiLyricsSettings.Snapshot snapshot = aiLyricsSettings.snapshot();
        if (!snapshot.hasApiKey()) {
            return false;
        }
        String source = effectiveSelectedSourceLang();
        AiLyricsSettings.LanguageRule rule = snapshot.ruleForSource(source);
        if (item == AiLyricsSettings.PREVIEW_ITEM_TRANSLATION) {
            String target = snapshot.resolveTargetLanguage(source);
            return rule.translationEnabled && !snapshot.shouldSkipTranslation(source, target);
        }
        if (item == AiLyricsSettings.PREVIEW_ITEM_PRONUNCIATION) {
            return rule.pronunciationEnabled;
        }
        return false;
    }

    private boolean shouldGenerateJapaneseFurigana(AiLyricsSettings.Snapshot snapshot, String sourceLang) {
        return snapshot != null
                && snapshot.japaneseFuriganaEnabled
                && "ja".equalsIgnoreCase(AiLyricsSettings.normalizeLanguageCode(sourceLang))
                && lyricsContainKanji(currentBaseLyricsResult);
    }

    private boolean lyricsContainKanji(LyricsResult result) {
        if (result == null || result.lines == null) {
            return false;
        }
        for (LyricsLine line : result.lines) {
            if (line == null) {
                continue;
            }
            if (containsKanji(line.text)) {
                return true;
            }
            if (line.vocalParts != null) {
                for (LyricsLine.VocalPart part : line.vocalParts) {
                    if (part != null && containsKanji(part.text)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean containsKanji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if ((codePoint >= 0x3400 && codePoint <= 0x4DBF)
                    || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                    || (codePoint >= 0xF900 && codePoint <= 0xFAFF)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private PreviewText originalPreviewText(LyricsLine line) {
        if (!hasMultiplePreviewVocalParts(line) && line.text != null && !line.text.trim().isEmpty()) {
            String text = line.text.trim();
            return new PreviewText(text, previewLineRubyText(line), karaokeSyllablesForText(text, line.syllables), line.kind);
        }
        StringBuilder builder = new StringBuilder();
        StringBuilder rubyBuilder = new StringBuilder();
        List<LyricsLine.Syllable> syllables = new ArrayList<>();
        boolean syllablesUsable = true;
        for (LyricsLine.VocalPart part : line.vocalParts) {
            if (part.text == null || part.text.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
                rubyBuilder.append(' ');
                syllables.add(spaceSyllable(syllables, part));
            }
            String partText = part.text.trim();
            builder.append(partText);
            rubyBuilder.append(previewPartRubyText(part, partText));
            List<LyricsLine.Syllable> partSyllables = karaokeSyllablesForText(partText, part.syllables);
            if (partSyllables.isEmpty()) {
                syllablesUsable = false;
            }
            syllables.addAll(partSyllables);
        }
        if (builder.length() == 0) {
            return new PreviewText("♪", "", Collections.emptyList(), line.kind);
        }
        return new PreviewText(
                builder.toString(),
                rubyBuilder.toString(),
                syllablesUsable ? syllables : Collections.emptyList(),
                line.kind
        );
    }

    private String previewLineRubyText(LyricsLine line) {
        if (!previewJapaneseFuriganaEnabled() || line == null) {
            return "";
        }
        return line.furiganaText == null ? "" : line.furiganaText.trim();
    }

    private String previewPartRubyText(LyricsLine.VocalPart part, String fallbackText) {
        if (!previewJapaneseFuriganaEnabled() || part == null) {
            return fallbackText == null ? "" : fallbackText;
        }
        String rubyText = part.furiganaText == null ? "" : part.furiganaText.trim();
        return rubyText.isEmpty() ? (fallbackText == null ? "" : fallbackText) : rubyText;
    }

    private boolean previewJapaneseFuriganaEnabled() {
        return aiLyricsSettings != null && aiLyricsSettings.snapshot().japaneseFuriganaEnabled;
    }

    private boolean hasMultiplePreviewVocalParts(LyricsLine line) {
        if (line == null || line.vocalParts == null) {
            return false;
        }
        int count = 0;
        for (LyricsLine.VocalPart part : line.vocalParts) {
            if (part != null && part.text != null && !part.text.trim().isEmpty()) {
                count++;
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<LyricsLine.Syllable> karaokeSyllablesForText(String text, List<LyricsLine.Syllable> syllables) {
        if (text == null || syllables == null || syllables.isEmpty()) {
            return Collections.emptyList();
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder builder = new StringBuilder();
        List<LyricsLine.Syllable> usable = new ArrayList<>();
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null || syllable.text == null || syllable.text.isEmpty()) {
                continue;
            }
            builder.append(syllable.text);
            usable.add(syllable);
        }
        return builder.toString().trim().equals(value) ? trimPreviewSyllables(usable) : Collections.emptyList();
    }

    private List<LyricsLine.Syllable> trimPreviewSyllables(List<LyricsLine.Syllable> syllables) {
        if (syllables == null || syllables.isEmpty()) {
            return Collections.emptyList();
        }
        int start = 0;
        int end = syllables.size() - 1;
        while (start <= end && isWhitespaceSyllable(syllables.get(start))) {
            start++;
        }
        while (end >= start && isWhitespaceSyllable(syllables.get(end))) {
            end--;
        }
        if (start > end) {
            return Collections.emptyList();
        }
        return new ArrayList<>(syllables.subList(start, end + 1));
    }

    private boolean isWhitespaceSyllable(LyricsLine.Syllable syllable) {
        return syllable == null
                || syllable.text == null
                || syllable.text.isEmpty()
                || syllable.text.codePoints().allMatch(Character::isWhitespace);
    }

    private LyricsLine.Syllable spaceSyllable(List<LyricsLine.Syllable> previous, LyricsLine.VocalPart nextPart) {
        long start = previous == null || previous.isEmpty()
                ? (nextPart == null ? 0L : nextPart.startTimeMs)
                : previous.get(previous.size() - 1).endTimeMs;
        long end = nextPart == null ? start : Math.max(start, nextPart.startTimeMs);
        return new LyricsLine.Syllable(" ", start, end);
    }

    private void showLyricsPage(boolean show) {
        if (isLandscapeLayout()) {
            if (lyricsPage != null) {
                lyricsPage.setVisibility(View.GONE);
                lyricsPage.setTranslationY(0f);
            }
            if (mainPage != null) {
                mainPage.setAlpha(1f);
            }
            lyricsPageVisible = false;
            return;
        }
        if (lyricsPage == null || mainPage == null || show == lyricsPageVisible) {
            return;
        }
        lastBackPressElapsedMs = 0L;
        lyricsPageVisible = show;
        int height = getResources().getDisplayMetrics().heightPixels;
        lyricsPage.animate().cancel();
        mainPage.animate().cancel();

        if (show) {
            resetLyricsPageDragTopPadding(false);
            lyricsPage.setVisibility(View.VISIBLE);
            lyricsPage.bringToFront();
            setLyricsPageCornerRadius(28);
            if (debugPanel != null && debugPanel.getVisibility() == View.VISIBLE) {
                debugPanel.bringToFront();
            }
            lyricsPage.setTranslationY(height);
            lyricsPage.setAlpha(1f);
            if (lyricsBackgroundView != null) {
                lyricsBackgroundView.setAlpha(1f);
            }
            lyricsPage.animate()
                    .translationY(0f)
                    .setDuration(330L)
                    .withEndAction(() -> {
                        setLyricsPageCornerRadius(0);
                        resetLyricsPageDragTopPadding(false);
                        maybeShowLyricsMetaTip();
                    })
                    .start();
            mainPage.animate()
                    .alpha(0f)
                    .setDuration(330L)
                    .start();
        } else {
            dismissLyricsMetaTip();
            mainPage.setAlpha(1f);
            setLyricsPageCornerRadius(28);
            lyricsPage.setAlpha(1f);
            if (lyricsBackgroundView != null) {
                lyricsBackgroundView.setAlpha(1f);
            }
            lyricsPage.animate()
                    .translationY(height)
                    .setDuration(280L)
                    .withEndAction(() -> {
                        lyricsPage.setVisibility(View.GONE);
                        lyricsPage.setTranslationY(0f);
                        setLyricsPageCornerRadius(0);
                        resetLyricsPageDragTopPadding(false);
                    })
                    .start();
        }
    }

    private boolean isInAppBrowserVisible() {
        return inAppBrowserPage != null
                && inAppBrowserVisible
                && inAppBrowserPage.getVisibility() == View.VISIBLE;
    }

    private void destroyInAppBrowserWebView() {
        stopInAppBrowserSkeletonAnimation();
        if (inAppBrowserWebView == null) {
            return;
        }
        inAppBrowserWebView.stopLoading();
        inAppBrowserWebView.destroy();
        inAppBrowserWebView = null;
    }

    private void openInAppBrowser(String url) {
        String safeUrl = url == null ? "" : url.trim();
        if (safeUrl.isEmpty() || inAppBrowserPage == null || inAppBrowserWebView == null) {
            return;
        }
        inAppBrowserInitialUrl = safeUrl;
        applyInAppBrowserChromeTheme();
        showInAppBrowserLoading(true);
        inAppBrowserWebView.stopLoading();
        inAppBrowserWebView.clearHistory();
        inAppBrowserWebView.loadUrl(safeUrl);
        showInAppBrowser(true);
    }

    private void showInAppBrowser(boolean show) {
        if (inAppBrowserPage == null || show == inAppBrowserVisible) {
            return;
        }
        lastBackPressElapsedMs = 0L;
        inAppBrowserVisible = show;
        int height = getResources().getDisplayMetrics().heightPixels;
        inAppBrowserPage.animate().cancel();
        if (show) {
            updateInAppBrowserSheetLayout();
            inAppBrowserPage.setVisibility(View.VISIBLE);
            inAppBrowserPage.bringToFront();
            inAppBrowserPage.setTranslationY(height);
            inAppBrowserPage.animate()
                    .translationY(0f)
                    .setDuration(320L)
                    .start();
            return;
        }
        inAppBrowserPage.animate()
                .translationY(height)
                .setDuration(260L)
                .withEndAction(() -> {
                    inAppBrowserPage.setVisibility(View.GONE);
                    inAppBrowserPage.setTranslationY(0f);
                })
                .start();
    }

    private void updateInAppBrowserSheetLayout() {
        if (inAppBrowserSheet == null) {
            return;
        }
        ViewGroup.LayoutParams rawParams = inAppBrowserSheet.getLayoutParams();
        if (!(rawParams instanceof FrameLayout.LayoutParams)) {
            return;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) rawParams;
        int topMargin = inAppBrowserSheetTopMarginPx();
        if (params.topMargin == topMargin) {
            return;
        }
        params.topMargin = topMargin;
        inAppBrowserSheet.setLayoutParams(params);
    }

    private void applyInAppBrowserChromeTheme() {
        int background = inAppBrowserBackgroundColor();
        if (inAppBrowserSheet != null) {
            inAppBrowserSheet.setBackground(topRoundDrawable(background, dp(24)));
        }
        if (inAppBrowserWebView != null) {
            inAppBrowserWebView.setBackgroundColor(background);
        }
        if (inAppBrowserHandleView != null) {
            inAppBrowserHandleView.setBackground(roundDrawable(inAppBrowserHandleColor(), dp(1.5f)));
        }
        rebuildInAppBrowserLoadingView();
    }

    private void rebuildInAppBrowserLoadingView() {
        if (inAppBrowserSheet == null) {
            return;
        }
        boolean wasVisible = inAppBrowserLoadingView != null && inAppBrowserLoadingView.getVisibility() == View.VISIBLE;
        if (inAppBrowserLoadingView != null) {
            inAppBrowserSheet.removeView(inAppBrowserLoadingView);
        }
        inAppBrowserLoadingView = buildInAppBrowserLoadingView();
        inAppBrowserLoadingView.setVisibility(wasVisible ? View.VISIBLE : View.GONE);
        inAppBrowserSheet.addView(inAppBrowserLoadingView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        if (inAppBrowserHandleTouchTarget != null) {
            inAppBrowserHandleTouchTarget.bringToFront();
        }
        if (wasVisible) {
            startInAppBrowserSkeletonAnimation();
        }
    }

    private int inAppBrowserBackgroundColor() {
        return isDeviceNightMode() ? Color.rgb(14, 17, 22) : Color.rgb(251, 251, 252);
    }

    private int inAppBrowserSurfaceColor() {
        return isDeviceNightMode() ? Color.rgb(25, 28, 34) : Color.WHITE;
    }

    private int inAppBrowserSkeletonColor() {
        return isDeviceNightMode() ? Color.rgb(45, 49, 58) : Color.rgb(236, 238, 241);
    }

    private int inAppBrowserSkeletonStrongColor() {
        return isDeviceNightMode() ? Color.rgb(58, 63, 72) : Color.rgb(224, 227, 232);
    }

    private int inAppBrowserHandleColor() {
        return isDeviceNightMode() ? Color.argb(86, 255, 255, 255) : Color.argb(78, 14, 17, 22);
    }

    private boolean isDeviceNightMode() {
        int mask = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mask == Configuration.UI_MODE_NIGHT_YES;
    }

    private int inAppBrowserSheetTopMarginPx() {
        int topInset = statusBarInsetPx();
        int margin = topInset + dp(2);
        return Math.max(dp(isLandscapeLayout() ? 8 : 18), margin);
    }

    @SuppressWarnings("deprecation")
    private int statusBarInsetPx() {
        Window window = getWindow();
        View decor = window == null ? null : window.getDecorView();
        if (decor != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            WindowInsets insets = decor.getRootWindowInsets();
            if (insets != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return insets.getInsets(WindowInsets.Type.statusBars()).top;
                }
                return insets.getSystemWindowInsetTop();
            }
        }
        if (isLandscapeLayout()) {
            return 0;
        }
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return resourceId > 0 ? getResources().getDimensionPixelSize(resourceId) : 0;
    }

    private void injectInAppBrowserProfileCss(WebView view, String url) {
        if (view == null || !isLyricsProfileUrl(url)) {
            return;
        }
        String theme = isDeviceNightMode() ? "dark" : "light";
        String css = ".login-btn,"
                + ".credit[href*=\"github.com/ivLis-Studio/ivLyrics\"],"
                + ".theme-toggle,"
                + ".topbar .handle,"
                + ".topbar .handle .dot{display:none!important;}"
                + "html,body,.page,.shell,.profile,.tracks,.track,*{"
                + "-webkit-user-select:none!important;"
                + "user-select:none!important;"
                + "-webkit-touch-callout:none!important;}"
                + "img,a{"
                + "-webkit-user-drag:none!important;"
                + "user-drag:none!important;}"
                + ".page{padding-bottom:28px!important;}";
        String js = "(function(){"
                + "var theme=" + JSONObject.quote(theme) + ";"
                + "try{localStorage.setItem('ivlyrics_profile_theme',theme);}catch(error){}"
                + "document.documentElement.dataset.theme=theme;"
                + "document.documentElement.style.colorScheme=theme;"
                + "var id='ivlyrics-android-profile-style';"
                + "var old=document.getElementById(id);"
                + "if(old){old.remove();}"
                + "var style=document.createElement('style');"
                + "style.id=id;"
                + "style.textContent=" + JSONObject.quote(css) + ";"
                + "(document.head||document.documentElement).appendChild(style);"
                + "var block=function(event){event.preventDefault();return false;};"
                + "document.addEventListener('contextmenu',block,true);"
                + "document.addEventListener('selectstart',block,true);"
                + "document.addEventListener('dragstart',block,true);"
                + "document.oncontextmenu=function(){return false;};"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    private boolean isLyricsProfileUrl(String url) {
        try {
            Uri uri = Uri.parse(url == null ? "" : url.trim());
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            return "lyrics.ivl.is".equals(host) && path.startsWith("/@");
        } catch (Exception ignored) {
            return false;
        }
    }

    private FrameLayout buildInAppBrowserLoadingView() {
        inAppBrowserSkeletonPulseViews.clear();
        FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(true);
        overlay.setBackgroundColor(inAppBrowserBackgroundColor());

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(16), dp(28), dp(16), dp(18));
        overlay.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout topbar = new LinearLayout(this);
        topbar.setOrientation(LinearLayout.HORIZONTAL);
        topbar.setGravity(Gravity.CENTER_VERTICAL);
        shell.addView(topbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));
        topbar.addView(skeletonBlock(inAppBrowserSkeletonStrongColor(), 118, 16, 8));
        View spacer = new View(this);
        topbar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        topbar.addView(skeletonBlock(inAppBrowserSkeletonColor(), 40, 34, 17));
        topbar.addView(skeletonBlock(inAppBrowserSkeletonColor(), 76, 34, 17), leftMargin(wrapFixed(dp(76), dp(34)), dp(8)));

        LinearLayout profile = new LinearLayout(this);
        profile.setOrientation(LinearLayout.VERTICAL);
        profile.setPadding(dp(20), dp(20), dp(20), dp(18));
        profile.setBackground(roundDrawable(inAppBrowserSurfaceColor(), dp(22)));
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        profileParams.topMargin = dp(10);
        shell.addView(profile, profileParams);

        LinearLayout profileTop = new LinearLayout(this);
        profileTop.setGravity(Gravity.CENTER_VERTICAL);
        profileTop.setOrientation(LinearLayout.HORIZONTAL);
        profile.addView(profileTop, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(82)
        ));
        profileTop.addView(skeletonBlock(inAppBrowserSkeletonStrongColor(), 78, 78, 39));
        LinearLayout profileText = new LinearLayout(this);
        profileText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams profileTextParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        profileTextParams.leftMargin = dp(16);
        profileTop.addView(profileText, profileTextParams);
        profileText.addView(skeletonBlock(inAppBrowserSkeletonStrongColor(), 142, 24, 10));
        profileText.addView(skeletonBlock(inAppBrowserSkeletonColor(), 190, 14, 7), topMargin(wrapFixed(dp(190), dp(14)), dp(10)));
        profileText.addView(skeletonBlock(inAppBrowserSkeletonColor(), 98, 14, 7), topMargin(wrapFixed(dp(98), dp(14)), dp(8)));

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        statsParams.topMargin = dp(18);
        profile.addView(stats, statsParams);
        stats.addView(skeletonBlock(inAppBrowserSkeletonColor(), 0, 54, 14), new LinearLayout.LayoutParams(0, dp(54), 1f));
        stats.addView(skeletonBlock(inAppBrowserSkeletonColor(), 0, 54, 14), leftMargin(new LinearLayout.LayoutParams(0, dp(54), 1f), dp(10)));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40)
        );
        tabsParams.topMargin = dp(16);
        shell.addView(tabs, tabsParams);
        tabs.addView(skeletonBlock(inAppBrowserSkeletonStrongColor(), 92, 34, 17));
        tabs.addView(skeletonBlock(inAppBrowserSkeletonColor(), 82, 34, 17), leftMargin(wrapFixed(dp(82), dp(34)), dp(8)));

        for (int index = 0; index < 5; index++) {
            shell.addView(buildInAppBrowserSkeletonTrack(index), topMargin(matchWrap(), dp(index == 0 ? 10 : 9)));
        }
        return overlay;
    }

    private LinearLayout buildInAppBrowserSkeletonTrack(int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(12), dp(12));
        row.setBackground(roundDrawable(inAppBrowserSurfaceColor(), dp(18)));
        row.addView(skeletonBlock(inAppBrowserSkeletonColor(), 44, 44, 14));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(12);
        row.addView(text, textParams);
        text.addView(skeletonBlock(inAppBrowserSkeletonStrongColor(), index % 2 == 0 ? 184 : 138, 17, 8));
        text.addView(skeletonBlock(inAppBrowserSkeletonColor(), index % 3 == 0 ? 126 : 162, 13, 7), topMargin(wrapFixed(dp(index % 3 == 0 ? 126 : 162), dp(13)), dp(9)));
        row.addView(skeletonBlock(inAppBrowserSkeletonColor(), 34, 34, 17));
        return row;
    }

    private View skeletonBlock(int color, int widthDp, int heightDp, int radiusDp) {
        View view = new View(this);
        view.setBackground(roundDrawable(color, dp(radiusDp)));
        view.setAlpha(0.78f);
        inAppBrowserSkeletonPulseViews.add(view);
        if (widthDp > 0 && heightDp > 0) {
            view.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)));
        }
        return view;
    }

    private LinearLayout.LayoutParams wrapFixed(int widthPx, int heightPx) {
        return new LinearLayout.LayoutParams(widthPx, heightPx);
    }

    private void showInAppBrowserLoading(boolean show) {
        if (inAppBrowserLoadingView == null) {
            return;
        }
        inAppBrowserLoadingView.animate().cancel();
        if (show) {
            inAppBrowserLoadingView.setAlpha(1f);
            inAppBrowserLoadingView.setVisibility(View.VISIBLE);
            inAppBrowserLoadingView.bringToFront();
            if (inAppBrowserHandleTouchTarget != null) {
                inAppBrowserHandleTouchTarget.bringToFront();
            }
            startInAppBrowserSkeletonAnimation();
            return;
        }
        inAppBrowserLoadingView.animate()
                .alpha(0f)
                .setDuration(160L)
                .withEndAction(() -> {
                    inAppBrowserLoadingView.setVisibility(View.GONE);
                    inAppBrowserLoadingView.setAlpha(1f);
                    stopInAppBrowserSkeletonAnimation();
                })
                .start();
    }

    private void startInAppBrowserSkeletonAnimation() {
        if (inAppBrowserSkeletonAnimator != null && inAppBrowserSkeletonAnimator.isStarted()) {
            return;
        }
        inAppBrowserSkeletonAnimator = ValueAnimator.ofFloat(0.58f, 1f);
        inAppBrowserSkeletonAnimator.setDuration(860L);
        inAppBrowserSkeletonAnimator.setRepeatCount(ValueAnimator.INFINITE);
        inAppBrowserSkeletonAnimator.setRepeatMode(ValueAnimator.REVERSE);
        inAppBrowserSkeletonAnimator.addUpdateListener(animation -> {
            float alpha = (float) animation.getAnimatedValue();
            for (int index = 0; index < inAppBrowserSkeletonPulseViews.size(); index++) {
                inAppBrowserSkeletonPulseViews.get(index).setAlpha(alpha);
            }
        });
        inAppBrowserSkeletonAnimator.start();
    }

    private void stopInAppBrowserSkeletonAnimation() {
        if (inAppBrowserSkeletonAnimator == null) {
            return;
        }
        inAppBrowserSkeletonAnimator.cancel();
        inAppBrowserSkeletonAnimator = null;
    }

    private void attachInAppBrowserSwipe(View view) {
        view.setClickable(true);
        view.setOnTouchListener((target, event) -> {
            if (pageVelocityTracker != null) {
                pageVelocityTracker.addMovement(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    recyclePageVelocityTracker();
                    pageVelocityTracker = VelocityTracker.obtain();
                    pageVelocityTracker.addMovement(event);
                    pageDragStartY = event.getRawY();
                    pageDragStartTranslationY = inAppBrowserPage == null ? 0f : inAppBrowserPage.getTranslationY();
                    pageDragging = false;
                    if (inAppBrowserPage != null) {
                        inAppBrowserPage.animate().cancel();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dy = event.getRawY() - pageDragStartY;
                    if (Math.abs(dy) > dp(10)) {
                        pageDragging = true;
                    }
                    if (inAppBrowserVisible) {
                        applyInAppBrowserDragTranslation(Math.max(0f, pageDragStartTranslationY + dy));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float releaseVelocityY = pageVelocityY();
                    if (pageDragging && inAppBrowserVisible) {
                        settleInAppBrowserDrag(releaseVelocityY);
                    } else {
                        target.performClick();
                    }
                    recyclePageVelocityTracker();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    pageDragging = false;
                    if (inAppBrowserVisible) {
                        settleInAppBrowserDrag(0f);
                    }
                    recyclePageVelocityTracker();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void attachInAppBrowserContentSwipe(WebView view) {
        view.setOnTouchListener((target, event) -> {
            if (pageVelocityTracker != null) {
                pageVelocityTracker.addMovement(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    recyclePageVelocityTracker();
                    pageVelocityTracker = VelocityTracker.obtain();
                    pageVelocityTracker.addMovement(event);
                    pageDragStartX = event.getRawX();
                    pageDragStartY = event.getRawY();
                    pageDragStartTranslationY = inAppBrowserPage == null ? 0f : inAppBrowserPage.getTranslationY();
                    pageDragging = false;
                    if (inAppBrowserPage != null) {
                        inAppBrowserPage.animate().cancel();
                    }
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - pageDragStartX;
                    float dy = event.getRawY() - pageDragStartY;
                    if (!pageDragging) {
                        boolean downwardIntent = dy > dp(14) && dy > Math.abs(dx) * 1.2f;
                        if (!inAppBrowserVisible || !downwardIntent || target.canScrollVertically(-1)) {
                            return false;
                        }
                        pageDragging = true;
                        if (target.getParent() != null) {
                            target.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    applyInAppBrowserDragTranslation(Math.max(0f, pageDragStartTranslationY + dy));
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    if (target.getParent() != null) {
                        target.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    if (!pageDragging) {
                        recyclePageVelocityTracker();
                        return false;
                    }
                    settleInAppBrowserDrag(pageVelocityY());
                    recyclePageVelocityTracker();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    if (target.getParent() != null) {
                        target.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    if (pageDragging && inAppBrowserVisible) {
                        settleInAppBrowserDrag(0f);
                    }
                    pageDragging = false;
                    recyclePageVelocityTracker();
                    return false;
                default:
                    return false;
            }
        });
    }

    private void applyInAppBrowserDragTranslation(float translationY) {
        if (inAppBrowserPage == null) {
            return;
        }
        int height = getResources().getDisplayMetrics().heightPixels;
        inAppBrowserPage.setTranslationY(Math.max(0f, Math.min(height, translationY)));
    }

    private void settleInAppBrowserDrag(float velocityY) {
        if (inAppBrowserPage == null) {
            return;
        }
        int height = getResources().getDisplayMetrics().heightPixels;
        float translationY = Math.max(0f, inAppBrowserPage.getTranslationY());
        boolean shouldClose = translationY > height * 0.24f || (velocityY > dp(1100) && translationY > dp(36));
        if (shouldClose) {
            showInAppBrowser(false);
            return;
        }
        inAppBrowserPage.animate()
                .translationY(0f)
                .setDuration(210L)
                .start();
    }

    private boolean shouldOpenBrowserNavigationExternally(String url) {
        String safeUrl = url == null ? "" : url.trim();
        if (safeUrl.isEmpty() || safeUrl.startsWith("about:") || safeUrl.startsWith("data:")) {
            return false;
        }
        if (normalizeBrowserUrl(safeUrl).equals(normalizeBrowserUrl(inAppBrowserInitialUrl))) {
            return false;
        }
        if (isSameLyricsProfileNavigation(safeUrl, inAppBrowserInitialUrl)) {
            return false;
        }
        openExternalBrowserUrl(safeUrl);
        return true;
    }

    private boolean isSameLyricsProfileNavigation(String nextUrl, String initialUrl) {
        String nextPath = lyricsProfilePath(nextUrl);
        String initialPath = lyricsProfilePath(initialUrl);
        return !nextPath.isEmpty() && nextPath.equals(initialPath);
    }

    private String lyricsProfilePath(String url) {
        try {
            Uri uri = Uri.parse(url == null ? "" : url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || !"lyrics.ivl.is".equals(host)) {
                return "";
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if (!path.startsWith("/@") || path.indexOf('/', 2) >= 0) {
                return "";
            }
            return path;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalizeBrowserUrl(String url) {
        try {
            Uri uri = Uri.parse(url == null ? "" : url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
            return scheme + "://" + host + path + query;
        } catch (Exception ignored) {
            return url == null ? "" : url.trim();
        }
    }

    private void openExternalBrowserUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception error) {
            appendLog("external browser open failed: " + error.getMessage());
        }
    }

    private void attachPageSwipe(View view, boolean opensLyrics, boolean tapOpens) {
        if (opensLyrics && isLandscapeLayout()) {
            return;
        }
        view.setOnTouchListener((target, event) -> {
            if (pageVelocityTracker != null) {
                pageVelocityTracker.addMovement(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    recyclePageVelocityTracker();
                    pageVelocityTracker = VelocityTracker.obtain();
                    pageVelocityTracker.addMovement(event);
                    pageDragStartY = event.getRawY();
                    pageDragStartTranslationY = lyricsPage == null ? 0f : lyricsPage.getTranslationY();
                    pageDragging = false;
                    if (lyricsPage != null) {
                        lyricsPage.animate().cancel();
                    }
                    if (mainPage != null) {
                        mainPage.animate().cancel();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dy = event.getRawY() - pageDragStartY;
                    if (Math.abs(dy) > dp(12)) {
                        pageDragging = true;
                    }
                    if (!opensLyrics && lyricsPageVisible) {
                        float translation = Math.max(0f, pageDragStartTranslationY + dy);
                        applyLyricsDragTranslation(translation);
                    }
                    return true;
                case MotionEvent.ACTION_UP: {
                    float releaseVelocityY = pageVelocityY();
                    float releaseDy = event.getRawY() - pageDragStartY;
                    if (opensLyrics) {
                        if (releaseDy < -dp(56) || (tapOpens && !pageDragging)) {
                            showLyricsPage(true);
                            target.performClick();
                        }
                    } else if (lyricsPageVisible) {
                        settleLyricsDrag(releaseVelocityY);
                    }
                    recyclePageVelocityTracker();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    pageDragging = false;
                    if (!opensLyrics && lyricsPageVisible) {
                        settleLyricsDrag(0f);
                    }
                    recyclePageVelocityTracker();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void attachLyricsMetaSwipe(View view) {
        view.setClickable(true);
        view.setOnTouchListener((target, event) -> {
            if (pageVelocityTracker != null) {
                pageVelocityTracker.addMovement(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    recyclePageVelocityTracker();
                    pageVelocityTracker = VelocityTracker.obtain();
                    pageVelocityTracker.addMovement(event);
                    pageDragStartY = event.getRawY();
                    pageDragStartTranslationY = lyricsPage == null ? 0f : lyricsPage.getTranslationY();
                    pageDragging = false;
                    if (lyricsPage != null) {
                        lyricsPage.animate().cancel();
                    }
                    if (mainPage != null) {
                        mainPage.animate().cancel();
                    }
                    scheduleLyricsMetaLongPress(target);
                    if (target.getParent() != null) {
                        target.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (lyricsMetaLongPressTriggered) {
                        return true;
                    }
                    float dy = event.getRawY() - pageDragStartY;
                    if (Math.abs(dy) > dp(12)) {
                        pageDragging = true;
                        cancelLyricsMetaLongPress();
                    }
                    if (lyricsPageVisible) {
                        applyLyricsDragTranslation(Math.max(0f, pageDragStartTranslationY + dy));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float releaseVelocityY = pageVelocityY();
                    cancelLyricsMetaLongPress();
                    if (target.getParent() != null) {
                        target.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    if (lyricsMetaLongPressTriggered) {
                        lyricsMetaLongPressTriggered = false;
                    } else if (pageDragging && lyricsPageVisible) {
                        settleLyricsDrag(releaseVelocityY);
                    } else {
                        handleLyricsMetaTap();
                    }
                    recyclePageVelocityTracker();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    cancelLyricsMetaLongPress();
                    lyricsMetaLongPressTriggered = false;
                    if (target.getParent() != null) {
                        target.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    pageDragging = false;
                    if (lyricsPageVisible) {
                        settleLyricsDrag(0f);
                    }
                    recyclePageVelocityTracker();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void attachArtworkSwipe(View view) {
        view.setOnTouchListener((target, event) -> {
            if (artworkVelocityTracker != null) {
                artworkVelocityTracker.addMovement(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    recycleArtworkVelocityTracker();
                    artworkVelocityTracker = VelocityTracker.obtain();
                    artworkVelocityTracker.addMovement(event);
                    artworkSwipeStartX = event.getRawX();
                    artworkSwipeStartY = event.getRawY();
                    artworkSwipeDragging = false;
                    target.animate().cancel();
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - artworkSwipeStartX;
                    float dy = event.getRawY() - artworkSwipeStartY;
                    if (!artworkSwipeDragging && Math.abs(dx) > dp(16) && Math.abs(dx) > Math.abs(dy) * 1.15f) {
                        artworkSwipeDragging = true;
                        if (target.getParent() != null) {
                            target.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                    if (artworkSwipeDragging) {
                        float maxOffset = Math.max(dp(26), target.getWidth() * 0.12f);
                        float offset = Math.max(-maxOffset, Math.min(maxOffset, dx * 0.16f));
                        target.setTranslationX(offset);
                        target.setRotation(offset / Math.max(1f, maxOffset) * 1.6f);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    float dx = event.getRawX() - artworkSwipeStartX;
                    float velocityX = artworkVelocityX();
                    boolean shouldSwitch = artworkSwipeDragging
                            && (Math.abs(dx) > target.getWidth() * 0.18f || Math.abs(velocityX) > dp(900));
                    if (shouldSwitch) {
                        runTransportCommand(dx < 0f
                                ? () -> NowPlayingService.skipToNext()
                                : () -> NowPlayingService.skipToPrevious());
                    }
                    settleArtworkSwipe(target);
                    recycleArtworkVelocityTracker();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                    settleArtworkSwipe(target);
                    recycleArtworkVelocityTracker();
                    return true;
                default:
                    return true;
            }
        });
    }

    private void settleArtworkSwipe(View target) {
        artworkSwipeDragging = false;
        if (target.getParent() != null) {
            target.getParent().requestDisallowInterceptTouchEvent(false);
        }
        target.animate()
                .translationX(0f)
                .rotation(0f)
                .setDuration(150L)
                .start();
    }

    private float artworkVelocityX() {
        if (artworkVelocityTracker == null) {
            return 0f;
        }
        artworkVelocityTracker.computeCurrentVelocity(1000);
        return artworkVelocityTracker.getXVelocity();
    }

    private void recycleArtworkVelocityTracker() {
        if (artworkVelocityTracker != null) {
            artworkVelocityTracker.recycle();
            artworkVelocityTracker = null;
        }
    }

    private void applyLyricsDragTranslation(float translationY) {
        if (lyricsPage == null || mainPage == null) {
            return;
        }
        int height = getResources().getDisplayMetrics().heightPixels;
        float boundedTranslation = Math.max(0f, Math.min(height, translationY));
        lyricsPage.setAlpha(1f);
        if (lyricsBackgroundView != null) {
            lyricsBackgroundView.setAlpha(1f);
        }
        lyricsPage.setTranslationY(boundedTranslation);
        setLyricsPageCornerRadius(boundedTranslation > 1f ? 28 : 0);
        applyLyricsPageDragTopPadding(boundedTranslation);
        float reveal = Math.max(0f, Math.min(1f, boundedTranslation / Math.max(1f, dp(120))));
        mainPage.setAlpha(reveal);
    }

    private void applyLyricsPageDragTopPadding(float translationY) {
        if (lyricsPageContent == null) {
            return;
        }
        if (lyricsPageContentPaddingAnimator != null) {
            lyricsPageContentPaddingAnimator.cancel();
            lyricsPageContentPaddingAnimator = null;
        }
        float progress = Math.max(0f, Math.min(1f,
                translationY / Math.max(1f, dp(LYRICS_PAGE_TOP_PADDING_SHRINK_DISTANCE_DP))));
        int expanded = dp(LYRICS_PAGE_TOP_PADDING_EXPANDED_DP);
        int compact = dp(LYRICS_PAGE_TOP_PADDING_COMPACT_DP);
        int topPadding = Math.round(expanded + ((compact - expanded) * progress));
        setLyricsPageContentTopPadding(topPadding);
    }

    private void resetLyricsPageDragTopPadding(boolean animate) {
        int expanded = dp(LYRICS_PAGE_TOP_PADDING_EXPANDED_DP);
        if (!animate || lyricsPageContent == null) {
            if (lyricsPageContentPaddingAnimator != null) {
                lyricsPageContentPaddingAnimator.cancel();
                lyricsPageContentPaddingAnimator = null;
            }
            setLyricsPageContentTopPadding(expanded);
            return;
        }
        int start = lyricsPageContentTopPaddingPx >= 0
                ? lyricsPageContentTopPaddingPx
                : lyricsPageContent.getPaddingTop();
        if (start == expanded) {
            return;
        }
        if (lyricsPageContentPaddingAnimator != null) {
            lyricsPageContentPaddingAnimator.cancel();
        }
        lyricsPageContentPaddingAnimator = ValueAnimator.ofInt(start, expanded);
        lyricsPageContentPaddingAnimator.setDuration(210L);
        lyricsPageContentPaddingAnimator.addUpdateListener(animator ->
                setLyricsPageContentTopPadding((Integer) animator.getAnimatedValue()));
        lyricsPageContentPaddingAnimator.start();
    }

    private void setLyricsPageContentTopPadding(int topPadding) {
        if (lyricsPageContent == null || lyricsPageContentTopPaddingPx == topPadding) {
            return;
        }
        lyricsPageContentTopPaddingPx = topPadding;
        lyricsPageContent.setPadding(
                lyricsPageContent.getPaddingLeft(),
                topPadding,
                lyricsPageContent.getPaddingRight(),
                lyricsPageContent.getPaddingBottom()
        );
    }

    private void settleLyricsDrag(float velocityY) {
        if (lyricsPage == null || mainPage == null) {
            return;
        }
        int height = getResources().getDisplayMetrics().heightPixels;
        float translationY = Math.max(0f, lyricsPage.getTranslationY());
        boolean shouldClose = translationY > height * 0.30f || (velocityY > dp(1200) && translationY > dp(42));
        if (shouldClose) {
            showLyricsPage(false);
            return;
        }
        lyricsPage.animate()
                .translationY(0f)
                .setDuration(210L)
                .withEndAction(() -> setLyricsPageCornerRadius(0))
                .start();
        resetLyricsPageDragTopPadding(true);
        mainPage.animate()
                .alpha(0f)
                .setDuration(210L)
                .start();
    }

    private float pageVelocityY() {
        if (pageVelocityTracker == null) {
            return 0f;
        }
        pageVelocityTracker.computeCurrentVelocity(1000);
        return pageVelocityTracker.getYVelocity();
    }

    private void recyclePageVelocityTracker() {
        if (pageVelocityTracker != null) {
            pageVelocityTracker.recycle();
            pageVelocityTracker = null;
        }
    }

    private void seekToPosition(long positionMs) {
        TrackSnapshot snapshot = currentTrack != null ? currentTrack : NowPlayingService.getLatestSnapshot();
        long duration = snapshot == null ? 0L : snapshot.durationMs;
        long target = playerPositionForLyricsTime(positionMs, duration);
        seekPlayerToPositionInternal(target, duration);
    }

    private void seekPlayerToPosition(long positionMs) {
        TrackSnapshot snapshot = currentTrack != null ? currentTrack : NowPlayingService.getLatestSnapshot();
        long duration = snapshot == null ? 0L : snapshot.durationMs;
        long target = duration > 0L
                ? Math.max(0L, Math.min(duration, positionMs))
                : Math.max(0L, positionMs);
        seekPlayerToPositionInternal(target, duration);
    }

    private void seekPlayerToPositionInternal(long target, long duration) {
        long now = SystemClock.uptimeMillis();
        pendingSeekPositionMs = target;
        pendingSeekUptimeMs = now;
        long lyricsPosition = lyricsPlaybackPosition(target, duration);
        setLyricsPlaybackPositionOnViews(lyricsPosition);
        updateLyricPreview(lyricsPosition);
        updateProgressViews(target, duration);
        updateYouTubeBackgroundPlaybackState();
        if (now - lastSeekCommandUptimeMs < 220L && Math.abs(target - lastSeekCommandPositionMs) < 700L) {
            return;
        }
        lastSeekCommandUptimeMs = now;
        lastSeekCommandPositionMs = target;
        runTransportCommand(() -> NowPlayingService.seekTo(target));
    }

    private void runTransportCommand(Runnable command) {
        if (command == null || seekExecutor.isShutdown()) {
            return;
        }
        seekExecutor.execute(() -> {
            try {
                command.run();
                handler.post(this::requestNowPlayingRefreshBurst);
            } catch (RuntimeException ignored) {
                // Media session commands can fail if the player disappears during the request.
            }
        });
    }

    private void requestNowPlayingRefreshBurst() {
        NowPlayingService.requestRefresh(this);
        handler.postDelayed(() -> NowPlayingService.requestRefresh(this), 90L);
        handler.postDelayed(() -> NowPlayingService.requestRefresh(this), 260L);
        handler.postDelayed(() -> NowPlayingService.requestRefresh(this), 620L);
    }

    private long currentPlaybackPosition(TrackSnapshot snapshot) {
        long position = snapshot.positionNow();
        if (pendingSeekPositionMs >= 0L) {
            long now = SystemClock.uptimeMillis();
            long elapsed = now - pendingSeekUptimeMs;
            if (elapsed <= 1200L) {
                position = pendingSeekPositionMs + (snapshot.playing ? elapsed : 0L);
            } else {
                pendingSeekPositionMs = -1L;
            }
        }
        return snapshot.durationMs > 0L
                ? Math.max(0L, Math.min(snapshot.durationMs, position))
                : Math.max(0L, position);
    }

    private long currentLyricsPlaybackPosition(TrackSnapshot snapshot) {
        if (snapshot == null) {
            return 0L;
        }
        return lyricsPlaybackPosition(currentPlaybackPosition(snapshot), snapshot.durationMs);
    }

    private void updateArtwork(Bitmap artwork, String artworkKey) {
        currentArtworkBitmap = artwork;
        if (backgroundView != null) {
            backgroundView.setArtwork(artwork, artworkKey);
        }
        if (lyricsBackgroundView != null) {
            lyricsBackgroundView.setArtwork(artwork, artworkKey);
        }
        if (artwork != null) {
            if (artworkView != null) {
                artworkView.setBackgroundColor(Color.TRANSPARENT);
                artworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                artworkView.setImageBitmap(artwork);
            }
            if (lyricsArtworkView != null) {
                lyricsArtworkView.setBackgroundColor(Color.TRANSPARENT);
                lyricsArtworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                lyricsArtworkView.setImageBitmap(artwork);
            }
        } else {
            if (artworkView != null) {
                artworkView.setImageDrawable(null);
                artworkView.setBackground(albumFallbackDrawable());
            }
            if (lyricsArtworkView != null) {
                lyricsArtworkView.setImageDrawable(null);
                lyricsArtworkView.setBackground(albumFallbackDrawable());
            }
        }
    }

    private void toggleDebugPanel() {
        if (debugPanel == null) {
            return;
        }
        boolean show = debugPanel.getVisibility() != View.VISIBLE;
        debugPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            debugPanel.bringToFront();
        }
    }

    private void resetLogs(String firstLine) {
        logLines.clear();
        appendLog(firstLine);
    }

    private void appendLog(String message) {
        if (logView == null) {
            return;
        }
        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isEmpty()) {
            return;
        }
        logLines.add(formatLogLine(safeMessage));
        while (logLines.size() > MAX_LOG_LINES) {
            logLines.remove(0);
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < logLines.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(logLines.get(index));
        }
        logView.setText(builder.toString());
        if (logScrollView != null) {
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String formatLogLine(String message) {
        return formatTime(System.currentTimeMillis() % 3_600_000L) + "  " + message;
    }

    private String ui(String key) {
        return AppI18n.t(aiLyricsSettings == null ? "ko" : aiLyricsSettings.snapshot().uiLang, key);
    }

    private String uiFormat(String key, Object... args) {
        return String.format(Locale.ROOT, ui(key), args);
    }

    private TextView label(String value, float sizeSp, int color, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setTypeface(typeface);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView slidingLabel(String value, float sizeSp, int color, Typeface typeface) {
        SlidingTextView view = new SlidingTextView(this);
        view.setEdgeFadeEnabled(false);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setTypeface(typeface);
        view.setIncludeFontPadding(false);
        return view;
    }

    private LinearLayout createLyricsSupplementLoadingIndicator() {
        LinearLayout indicator = new LinearLayout(this);
        indicator.setOrientation(LinearLayout.HORIZONTAL);
        indicator.setGravity(Gravity.CENTER);
        indicator.setPadding(dp(8), 0, dp(9), 0);
        indicator.setBackground(roundDrawable(Color.argb(38, 255, 255, 255), dp(14)));
        indicator.setVisibility(View.GONE);

        ProgressBar loadingSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        loadingSpinner.setIndeterminate(true);
        if (loadingSpinner.getIndeterminateDrawable() != null) {
            loadingSpinner.getIndeterminateDrawable().setTint(Color.argb(210, 255, 255, 255));
        }
        indicator.addView(loadingSpinner, new LinearLayout.LayoutParams(dp(14), dp(14)));

        TextView loadingText = label(ui("loading.generating"), 11f, Color.argb(205, 255, 255, 255), AppFonts.semiBold(this));
        LinearLayout.LayoutParams loadingTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        loadingTextParams.leftMargin = dp(5);
        indicator.addView(loadingText, loadingTextParams);
        return indicator;
    }

    private ImageButton iconButton(int drawableRes, int sizeDp, int iconSizeDp, int iconColor, int backgroundColor, String description) {
        ImageButton view = new ImageButton(this);
        view.setImageResource(drawableRes);
        view.setColorFilter(iconColor);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setContentDescription(description);
        view.setBackground(backgroundColor == Color.TRANSPARENT
                ? roundDrawable(Color.argb(1, 255, 255, 255), dp(sizeDp / 2f))
                : roundDrawable(backgroundColor, dp(sizeDp / 2f)));
        int padding = Math.max(0, Math.round((dp(sizeDp) - dp(iconSizeDp)) * 0.5f));
        view.setPadding(padding, padding, padding, padding);
        view.setMinimumWidth(dp(sizeDp));
        view.setMinimumHeight(dp(sizeDp));
        return view;
    }

    private ImageView inlineIcon(int drawableRes, int sizeDp, int iconSizeDp, int iconColor, String description) {
        ImageView view = new ImageView(this);
        view.setImageResource(drawableRes);
        view.setColorFilter(iconColor);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setContentDescription(description);
        int padding = Math.max(0, Math.round((dp(sizeDp) - dp(iconSizeDp)) * 0.5f));
        view.setPadding(padding, padding, padding, padding);
        return view;
    }

    private TextView pillButton(String label) {
        TextView view = label(label, 13f, Color.WHITE, AppFonts.semiBold(this));
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundDrawable(Color.argb(46, 255, 255, 255), dp(22)));
        return view;
    }

    private TextView debugButton(String label) {
        TextView view = label(label, 13f, Color.WHITE, AppFonts.semiBold(this));
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundDrawable(Color.argb(42, 255, 255, 255), dp(9)));
        view.setPadding(dp(12), 0, dp(12), 0);
        view.setMinHeight(dp(42));
        return view;
    }

    private void clipRound(ImageView view, int radiusDp) {
        clipRoundView(view, radiusDp);
    }

    private void clipRoundView(View target, int radiusDp) {
        target.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(radiusDp));
            }
        });
        target.setClipToOutline(true);
    }

    private void clipTopRoundView(View target, int radiusDp) {
        target.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int radius = dp(radiusDp);
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight() + radius, radius);
            }
        });
        target.setClipToOutline(true);
    }

    private void setLyricsPageCornerRadius(int radiusDp) {
        if (lyricsPage == null || lyricsPageCornerRadiusDp == radiusDp) {
            return;
        }
        lyricsPageCornerRadiusDp = radiusDp;
        if (radiusDp <= 0) {
            lyricsPage.setClipToOutline(false);
            lyricsPage.setOutlineProvider(null);
            return;
        }
        clipRoundView(lyricsPage, radiusDp);
    }

    private GradientDrawable albumFallbackDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(34, 35, 40));
        drawable.setCornerRadius(dp(14));
        return drawable;
    }

    private GradientDrawable roundDrawable(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable topRoundDrawable(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadii(new float[]{
                radius, radius,
                radius, radius,
                0f, 0f,
                0f, 0f
        });
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams topMargin(LinearLayout.LayoutParams params, int topMargin) {
        params.topMargin = topMargin;
        return params;
    }

    private LinearLayout.LayoutParams leftMargin(LinearLayout.LayoutParams params, int leftMargin) {
        params.leftMargin = leftMargin;
        return params;
    }

    private LinearLayout.LayoutParams fixedControlParams(int sizeDp, int sideMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp));
        params.leftMargin = dp(sideMarginDp);
        params.rightMargin = dp(sideMarginDp);
        params.gravity = Gravity.CENTER_VERTICAL;
        return params;
    }

    private View flexSpacer(float weight) {
        View view = new View(this);
        view.setMinimumHeight(0);
        return view;
    }

    private LinearLayout.LayoutParams weightedButtonParams(float weight, int sideMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), weight);
        params.leftMargin = sideMargin;
        params.rightMargin = sideMargin;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private String packageSuffix(String packageName) {
        return packageName == null || packageName.isEmpty() ? "" : " / package=" + packageName;
    }

    private String artworkDebug(TrackSnapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }
        if (snapshot.artwork != null) {
            return "bitmap "
                    + snapshot.artwork.getWidth()
                    + "x"
                    + snapshot.artwork.getHeight()
                    + (snapshot.artworkUri.isEmpty() ? "" : " / uri=" + snapshot.artworkUri);
        }
        return snapshot.artworkUri.isEmpty() ? "none" : "uri pending " + snapshot.artworkUri;
    }

    private String formatTime(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private String formatRemaining(long position, long duration) {
        if (duration <= 0L) {
            return "-0:00";
        }
        return "-" + formatTime(Math.max(0L, duration - position));
    }

    private interface ChoiceHandler {
        void onChoice(String code);
    }

    private static final class LanguageChoice {
        final String code;
        final String label;

        LanguageChoice(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    private static final class PreviewChoice {
        final String label;
        final int item;

        PreviewChoice(String label, int item) {
            this.label = label;
            this.item = item;
        }
    }

    private abstract static class SimpleSeekBarChangeListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }

    private static final class PreviewText {
        final String text;
        final String rubyText;
        final List<LyricsLine.Syllable> syllables;
        final String kind;

        PreviewText(String text, String rubyText, List<LyricsLine.Syllable> syllables, String kind) {
            this.text = text == null ? "" : text;
            this.rubyText = rubyText == null ? "" : rubyText;
            this.syllables = syllables == null ? Collections.emptyList() : new ArrayList<>(syllables);
            this.kind = kind == null || kind.trim().isEmpty() ? "vocal" : kind.trim();
        }
    }

    private static final class PreviewEntry {
        final LyricsLine line;
        final long startTimeMs;
        final long endTimeMs;
        final String interludeKind;

        private PreviewEntry(LyricsLine line, long startTimeMs, long endTimeMs, String interludeKind) {
            this.line = line;
            this.startTimeMs = Math.max(0L, startTimeMs);
            this.endTimeMs = Math.max(this.startTimeMs, endTimeMs);
            this.interludeKind = interludeKind == null ? "" : interludeKind;
        }

        static PreviewEntry line(LyricsLine line) {
            return new PreviewEntry(line, line == null ? 0L : line.startTimeMs, line == null ? 0L : line.endTimeMs, "");
        }

        static PreviewEntry interlude(long startTimeMs, long endTimeMs, String kind) {
            return new PreviewEntry(null, startTimeMs, endTimeMs, kind);
        }

        boolean isInterlude() {
            return line == null && !interludeKind.isEmpty();
        }

        boolean contains(long positionMs) {
            return positionMs >= startTimeMs && positionMs < endTimeMs;
        }
    }
}
