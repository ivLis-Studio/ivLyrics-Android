package kr.ivlis.ivlyricsandroid;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Focused LP presentation shared by the portrait and landscape player pages. */
final class VinylPlayerModeView extends FrameLayout {
    interface Listener {
        void onClose();
        void onTogglePlayback();
        void onSeek(long positionMs);
        void onStopPlayback();
        void onShowTmi();
    }

    private final VinylSurface surface;
    private final MainLyricPreviewView lyricView;
    private final TextView loadingView;
    private Listener listener;

    VinylPlayerModeView(Context context) {
        this(context, null);
    }

    VinylPlayerModeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(false);
        setClipToPadding(false);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        surface = new VinylSurface(context);
        surface.setListener(new VinylSurface.Listener() {
            @Override
            public void onClose() {
                if (listener != null) listener.onClose();
            }

            @Override
            public void onTogglePlayback() {
                if (listener != null) listener.onTogglePlayback();
            }

            @Override
            public void onSeek(long positionMs) {
                if (listener != null) listener.onSeek(positionMs);
            }

            @Override
            public void onStopPlayback() {
                if (listener != null) listener.onStopPlayback();
            }

            @Override
            public void onShowTmi() {
                if (listener != null) listener.onShowTmi();
            }
        });
        addView(surface, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        lyricView = new MainLyricPreviewView(context);
        lyricView.setTextScale(1.13f);
        lyricView.setPadding(dp(10), dp(5), dp(10), dp(5));
        addView(lyricView, new LayoutParams(LayoutParams.MATCH_PARENT, dp(132)));

        loadingView = new TextView(context);
        loadingView.setTextColor(Color.argb(230, 255, 255, 255));
        loadingView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        loadingView.setTypeface(AppFonts.semiBold(context));
        loadingView.setGravity(Gravity.CENTER_VERTICAL);
        loadingView.setSingleLine(true);
        loadingView.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable loadingBackground = new GradientDrawable();
        loadingBackground.setColor(Color.argb(62, 0, 0, 0));
        loadingBackground.setCornerRadius(dp(17));
        loadingBackground.setStroke(dp(1), Color.argb(20, 255, 255, 255));
        loadingView.setBackground(loadingBackground);
        loadingView.setVisibility(GONE);
        addView(loadingView, new LayoutParams(LayoutParams.WRAP_CONTENT, dp(34)));
        setVisibility(GONE);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setUiText(String modeLabel, String closeHint, String recordHint, String tonearmHint, String tmiHint) {
        String mode = safe(modeLabel, "LP");
        String close = safe(closeHint, mode);
        String record = safe(recordHint, mode);
        String tonearm = safe(tonearmHint, mode);
        String tmi = safe(tmiHint, mode);
        setContentDescription(mode + ". " + close + ". " + record + ". " + tonearm + ". " + tmi);
        surface.setUiText(close, record, tonearm, tmi);
    }

    void setTrack(TrackSnapshot snapshot, Bitmap artwork, boolean animateChange) {
        surface.setTrack(snapshot, artwork, animateChange);
    }

    void setArtwork(String trackKey, Bitmap artwork) {
        surface.setArtwork(trackKey, artwork);
    }

    void setPlayback(long positionMs, long durationMs, boolean playing) {
        surface.setPlayback(positionMs, durationMs, playing);
    }

    void setCustomization(
            AiLyricsSettings.VinylSettings vinylSettings,
            AiLyricsSettings.TypographySettings typographySettings
    ) {
        AiLyricsSettings.VinylSettings vinyl = vinylSettings == null
                ? AiLyricsSettings.VinylSettings.defaults()
                : vinylSettings;
        surface.setCustomization(vinyl);
        int nextVisibility = vinyl.lyricsEnabled ? VISIBLE : GONE;
        if (lyricView.getVisibility() != nextVisibility) {
            lyricView.setVisibility(nextVisibility);
            requestLayout();
        }
        AiLyricsSettings.TypographySettings typography = typographySettings == null
                ? AiLyricsSettings.TypographySettings.defaults()
                : typographySettings;
        lyricView.setTypographySettings(typography.forVinylPreview());
    }

    void setCulturalAnnotationStyle(
            boolean enabled,
            String fontFamily,
            int fontSize,
            int fontWeight,
            int opacity
    ) {
        lyricView.setCulturalAnnotationStyle(fontFamily, fontSize, fontWeight, opacity);
        LayoutParams lyricParams = (LayoutParams) lyricView.getLayoutParams();
        int nextHeight = dp(enabled ? 158 : 132);
        if (lyricParams.height != nextHeight) {
            lyricParams.height = nextHeight;
            lyricView.setLayoutParams(lyricParams);
        }
        surface.setCulturalAnnotationsVisible(enabled);
    }

    MainLyricPreviewView lyricView() {
        return lyricView;
    }

    void setLoadingText(String text, boolean animate) {
        animate = animate && surface.animationsEnabled();
        String value = text == null ? "" : text.trim();
        loadingView.animate().cancel();
        if (value.isEmpty()) {
            if (!animate || loadingView.getVisibility() != VISIBLE) {
                loadingView.setVisibility(GONE);
                loadingView.setAlpha(1f);
                return;
            }
            loadingView.animate()
                    .alpha(0f)
                    .setDuration(140L)
                    .withEndAction(() -> {
                        loadingView.setVisibility(GONE);
                        loadingView.setAlpha(1f);
                    })
                    .start();
            return;
        }
        loadingView.setText(value);
        loadingView.setContentDescription(value);
        if (loadingView.getVisibility() != VISIBLE) {
            loadingView.setVisibility(VISIBLE);
            loadingView.setAlpha(animate ? 0f : 1f);
            loadingView.setTranslationY(animate ? -dp(6) : 0f);
        }
        if (animate) {
            loadingView.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        }
    }

    void show(boolean animate) {
        animate = animate && surface.animationsEnabled();
        if (animate) {
            surface.startEntrance();
        } else {
            surface.finishEntrance();
        }
        setVisibility(VISIBLE);
        bringToFront();
        animate().cancel();
        if (!animate) {
            setAlpha(1f);
            setScaleX(1f);
            setScaleY(1f);
            return;
        }
        setAlpha(0f);
        setScaleX(0.965f);
        setScaleY(0.965f);
        animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(420L)
                .setInterpolator(new DecelerateInterpolator(1.7f))
                .start();
    }

    void hide(boolean animate, Runnable endAction) {
        animate = animate && surface.animationsEnabled();
        animate().cancel();
        if (!animate) {
            setVisibility(GONE);
            setAlpha(1f);
            setScaleX(1f);
            setScaleY(1f);
            if (endAction != null) endAction.run();
            return;
        }
        animate()
                .alpha(0f)
                .scaleX(0.975f)
                .scaleY(0.975f)
                .setDuration(260L)
                .withEndAction(() -> {
                    setVisibility(GONE);
                    setAlpha(1f);
                    setScaleX(1f);
                    setScaleY(1f);
                    if (endAction != null) endAction.run();
                })
                .start();
    }

    void release() {
        animate().cancel();
        surface.release();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        surface.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );
        int lyricHeight = width > height ? dp(104) : dp(132);
        if (lyricView.getVisibility() != GONE) {
            lyricView.measure(
                    MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(36)), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(lyricHeight, MeasureSpec.EXACTLY)
            );
        }
        loadingView.measure(
                MeasureSpec.makeMeasureSpec(Math.max(0, width - dp(36)), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(dp(34), MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        surface.layout(0, 0, width, height);
        int side = dp(18);
        if (lyricView.getVisibility() != GONE) {
            int lyricBottom = width > height ? dp(10) : dp(18);
            int lyricHeight = lyricView.getMeasuredHeight();
            lyricView.layout(side, height - lyricBottom - lyricHeight, width - side, height - lyricBottom);
        }
        int loadingTop = width > height ? dp(14) : dp(20);
        loadingView.layout(
                side,
                loadingTop,
                side + loadingView.getMeasuredWidth(),
                loadingTop + loadingView.getMeasuredHeight()
        );
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        ));
    }

    private static String safe(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? fallback : text;
    }

    private static final class VinylSurface extends View {
        interface Listener {
            void onClose();
            void onTogglePlayback();
            void onSeek(long positionMs);
            void onStopPlayback();
            void onShowTmi();
        }

        // These are the same rotation values used by the desktop 260 x 620 SVG.
        // Keeping the SVG coordinate system intact makes the pivot, tube,
        // headshell and needle proportions identical on every screen size.
        private static final float TONEARM_START_DEGREES = -5.4f;
        private static final float TONEARM_END_DEGREES = 18f;
        private static final float TONEARM_PARK_DEGREES = -14f;
        private static final float TONEARM_EJECT_DEGREES = -8.2f;
        private static final float TONEARM_CUE_PLAY_DEGREES = -7.2f;
        private static final float TONEARM_LINEAR_REST_PROGRESS = -0.44f;
        private static final float TONEARM_LINEAR_EJECT_PROGRESS = -0.2f;
        private static final float TONEARM_LINEAR_CUE_PLAY_PROGRESS = -0.08f;
        private static final float TONEARM_LINEAR_TRAVEL = 95f;
        private static final float TONEARM_VIEWBOX_HEIGHT = 620f;
        private static final float TONEARM_PIVOT_SVG_X = 183f;
        private static final float TONEARM_PIVOT_SVG_Y = 64f;
        private static final float TONEARM_HEAD_SVG_X = 46f;
        private static final float TONEARM_HEAD_SVG_Y = 524f;
        private static final long PLAY_SEQUENCE_MS = 2280L;
        private static final long PAUSE_SEQUENCE_MS = 2030L;
        private static final long TRACK_SEQUENCE_MS = 3216L;
        private static final float TRACK_RECORD_CLEARED = 180f / TRACK_SEQUENCE_MS;
        private static final float TRACK_RECORD_SLEEVED = 860f / TRACK_SEQUENCE_MS;
        private static final float TRACK_ALBUM_DEPARTED = 1340f / TRACK_SEQUENCE_MS;
        private static final float TRACK_ALBUM_ARRIVED = 2040f / TRACK_SEQUENCE_MS;
        private static final float TRACK_RECORD_EMERGED = 2760f / TRACK_SEQUENCE_MS;
        private static final float TRACK_RECORD_RAISED = 3120f / TRACK_SEQUENCE_MS;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint groovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Path tonearmPath = new Path();
        private final Path labelArcPath = new Path();
        private final Matrix bitmapMatrix = new Matrix();
        private final RectF coverBounds = new RectF();
        private final RectF recordBounds = new RectF();
        private final RectF incomingCoverBounds = new RectF();
        private Listener listener;
        private VisualTrack displayedTrack;
        private VisualTrack incomingTrack;
        private ValueAnimator trackAnimator;
        private ValueAnimator playAnimator;
        private ValueAnimator tonearmAnimator;
        private ValueAnimator entranceAnimator;
        private float trackProgress;
        private float playProgress;
        private float tonearmEngagement;
        private float entranceProgress = 1f;
        private long positionMs;
        private long durationMs;
        private boolean playing;
        private boolean spinActive;
        private boolean trackHandoffCommitted;
        private boolean animationsEnabled = true;
        private boolean centerRotationEnabled = true;
        private boolean lyricsEnabled = true;
        private boolean culturalAnnotationsVisible;
        private float albumScale = 1f;
        private float recordScale = 1f;
        private String tonearmStyle = AiLyricsSettings.VINYL_TONEARM_STYLE_S;
        private String tonearmFinish = AiLyricsSettings.VINYL_TONEARM_FINISH_WHITE;
        private float tonearmScale = 1f;
        private long spinFrameUptimeMs;
        private float spinDegrees;
        private boolean scrubbingTonearm;
        private boolean coverPressed;
        private boolean recordPressed;
        private boolean longPressTriggered;
        private float dragTonearmProgress;
        private float dragTonearmRotation;
        private float dragRawTonearmRotation;
        private float dragPointerAngleOffset;
        private float dragRawTonearmProgress;
        private float dragPointerProgressOffset;
        private float dragStartPointerX;
        private float dragStartPointerY;
        private boolean dragStartedPlaying;
        private int activeTonearmPointerId = MotionEvent.INVALID_POINTER_ID;
        private float pivotX;
        private float pivotY;
        private float needleX;
        private float needleY;
        private float linearTonearmOuterX;
        private float tonearmDrawScale;
        private Runnable longPressRunnable;
        private String closeHint = "";
        private String recordHint = "";
        private String tonearmHint = "";
        private String tmiHint = "";

        VinylSurface(Context context) {
            super(context);
            setClickable(true);
            setFocusable(true);
            groovePaint.setStyle(Paint.Style.STROKE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(AppFonts.semiBold(context));
        }

        void setListener(Listener listener) {
            this.listener = listener;
        }

        void setUiText(String closeHint, String recordHint, String tonearmHint, String tmiHint) {
            this.closeHint = closeHint;
            this.recordHint = recordHint;
            this.tonearmHint = tonearmHint;
            this.tmiHint = tmiHint;
        }

        boolean animationsEnabled() {
            return animationsEnabled;
        }

        void setCustomization(AiLyricsSettings.VinylSettings settings) {
            AiLyricsSettings.VinylSettings safe = settings == null
                    ? AiLyricsSettings.VinylSettings.defaults()
                    : settings;
            albumScale = safe.albumSizePercent / 100f;
            recordScale = safe.recordSizePercent / 100f;
            animationsEnabled = safe.animationsEnabled;
            centerRotationEnabled = safe.centerRotationEnabled;
            lyricsEnabled = safe.lyricsEnabled;
            tonearmStyle = safe.tonearmStyle;
            tonearmFinish = safe.tonearmFinish;
            tonearmScale = safe.tonearmSizePercent / 100f;
            spinFrameUptimeMs = 0L;
            if (!animationsEnabled) {
                if (trackAnimator != null) trackAnimator.cancel();
                if (playAnimator != null) playAnimator.cancel();
                if (tonearmAnimator != null) tonearmAnimator.cancel();
                if (entranceAnimator != null) entranceAnimator.cancel();
                trackAnimator = null;
                playAnimator = null;
                tonearmAnimator = null;
                entranceAnimator = null;
                if (incomingTrack != null) displayedTrack = incomingTrack;
                incomingTrack = null;
                trackProgress = 0f;
                playProgress = playing ? 1f : 0f;
                tonearmEngagement = playing ? 1f : 0f;
                spinActive = playing;
                entranceProgress = 1f;
            }
            invalidate();
        }

        void setCulturalAnnotationsVisible(boolean visible) {
            if (culturalAnnotationsVisible == visible) {
                return;
            }
            culturalAnnotationsVisible = visible;
            requestLayout();
            invalidate();
        }

        void startEntrance() {
            if (entranceAnimator != null) entranceAnimator.cancel();
            if (playAnimator != null) playAnimator.cancel();
            if (tonearmAnimator != null) tonearmAnimator.cancel();
            playAnimator = null;
            tonearmAnimator = null;
            entranceProgress = 0f;
            playProgress = 0f;
            tonearmEngagement = 0f;
            spinActive = false;
            entranceAnimator = ValueAnimator.ofFloat(0f, 1f);
            entranceAnimator.setDuration(760L);
            entranceAnimator.setInterpolator(new DecelerateInterpolator(1.45f));
            entranceAnimator.addUpdateListener(animation -> {
                entranceProgress = (Float) animation.getAnimatedValue();
                invalidate();
            });
            entranceAnimator.addListener(new SimpleAnimatorEndListener(() -> {
                entranceAnimator = null;
                entranceProgress = 1f;
                invalidate();
            }));
            entranceAnimator.start();
            if (playing) {
                post(() -> {
                    if (playing) animatePlaybackState(true);
                });
            }
        }

        void finishEntrance() {
            if (entranceAnimator != null) entranceAnimator.cancel();
            if (playAnimator != null) playAnimator.cancel();
            if (tonearmAnimator != null) tonearmAnimator.cancel();
            entranceAnimator = null;
            playAnimator = null;
            tonearmAnimator = null;
            entranceProgress = 1f;
            playProgress = playing ? 1f : 0f;
            tonearmEngagement = playing ? 1f : 0f;
            spinActive = playing;
            invalidate();
        }

        void setTrack(TrackSnapshot snapshot, Bitmap artwork, boolean animateChange) {
            if (snapshot == null || !snapshot.hasUsableMetadata()) {
                displayedTrack = null;
                incomingTrack = null;
                invalidate();
                return;
            }
            VisualTrack next = new VisualTrack(snapshot, artwork);
            if (displayedTrack == null) {
                displayedTrack = next;
                invalidate();
                return;
            }
            if (displayedTrack.key.equals(next.key)) {
                displayedTrack = next.withFallbackArtwork(displayedTrack.artwork);
                if (incomingTrack != null && incomingTrack.key.equals(next.key)) {
                    incomingTrack = next.withFallbackArtwork(incomingTrack.artwork);
                }
                invalidate();
                return;
            }
            if (!animationsEnabled || !animateChange || !isShown()) {
                displayedTrack = next;
                incomingTrack = null;
                trackProgress = 0f;
                trackHandoffCommitted = false;
                invalidate();
                return;
            }
            if (trackAnimator != null) {
                trackAnimator.cancel();
                if (incomingTrack != null) displayedTrack = incomingTrack;
            }
            incomingTrack = next;
            trackProgress = 0f;
            trackHandoffCommitted = false;
            trackAnimator = ValueAnimator.ofFloat(0f, 1f);
            trackAnimator.setDuration(TRACK_SEQUENCE_MS);
            trackAnimator.setInterpolator(new LinearInterpolator());
            trackAnimator.addUpdateListener(animation -> {
                trackProgress = (Float) animation.getAnimatedValue();
                if (!trackHandoffCommitted
                        && trackProgress >= TRACK_RECORD_RAISED
                        && incomingTrack != null) {
                    displayedTrack = incomingTrack;
                    trackHandoffCommitted = true;
                }
                invalidate();
            });
            trackAnimator.addListener(new SimpleAnimatorEndListener(() -> {
                if (incomingTrack != null) displayedTrack = incomingTrack;
                incomingTrack = null;
                trackProgress = 0f;
                trackHandoffCommitted = false;
                trackAnimator = null;
                tonearmEngagement = 0f;
                if (playing) animateTonearmEngagement(1f, 720L);
                invalidate();
            }));
            trackAnimator.start();
        }

        void setArtwork(String trackKey, Bitmap artwork) {
            String key = trackKey == null ? "" : trackKey.trim();
            if (displayedTrack != null && displayedTrack.key.equals(key)) {
                displayedTrack = displayedTrack.withArtwork(artwork);
            }
            if (incomingTrack != null && incomingTrack.key.equals(key)) {
                incomingTrack = incomingTrack.withArtwork(artwork);
            }
            invalidate();
        }

        void setPlayback(long positionMs, long durationMs, boolean playing) {
            this.positionMs = Math.max(0L, positionMs);
            this.durationMs = Math.max(0L, durationMs);
            if (this.playing != playing) {
                this.playing = playing;
                animatePlaybackState(playing);
            }
            invalidate();
        }

        void release() {
            removeCallbacks(longPressRunnable);
            if (trackAnimator != null) trackAnimator.cancel();
            if (playAnimator != null) playAnimator.cancel();
            if (tonearmAnimator != null) tonearmAnimator.cancel();
            if (entranceAnimator != null) entranceAnimator.cancel();
        }

        private void animatePlaybackState(boolean targetPlaying) {
            if (playAnimator != null) playAnimator.cancel();
            if (tonearmAnimator != null) tonearmAnimator.cancel();
            tonearmAnimator = null;
            if (!animationsEnabled) {
                playAnimator = null;
                playProgress = targetPlaying ? 1f : 0f;
                tonearmEngagement = targetPlaying ? 1f : 0f;
                spinActive = targetPlaying;
                invalidate();
                return;
            }
            final float initialPlayProgress = playProgress;
            final float initialTonearmEngagement = tonearmEngagement;
            final boolean fullSequence = targetPlaying
                    ? initialPlayProgress <= 0.05f && initialTonearmEngagement <= 0.05f
                    : initialPlayProgress >= 0.95f;
            playAnimator = ValueAnimator.ofFloat(0f, 1f);
            playAnimator.setDuration(targetPlaying ? PLAY_SEQUENCE_MS : PAUSE_SEQUENCE_MS);
            playAnimator.setInterpolator(new LinearInterpolator());
            playAnimator.addUpdateListener(animation -> {
                float timeline = (Float) animation.getAnimatedValue();
                if (fullSequence) {
                    if (targetPlaying) {
                        playProgress = playCompositionForStart(timeline);
                        tonearmEngagement = smoothStep(
                                1560f / PLAY_SEQUENCE_MS,
                                1f,
                                timeline
                        );
                    } else {
                        playProgress = playCompositionForPause(timeline);
                        tonearmEngagement = lerp(
                                initialTonearmEngagement,
                                0f,
                                smoothStep(0f, 720f / PAUSE_SEQUENCE_MS, timeline)
                        );
                    }
                } else {
                    float eased = smoothStep(0f, 1f, timeline);
                    playProgress = lerp(initialPlayProgress, targetPlaying ? 1f : 0f, eased);
                    tonearmEngagement = lerp(
                            initialTonearmEngagement,
                            targetPlaying ? 1f : 0f,
                            eased
                    );
                }
                if (!targetPlaying && timeline >= 720f / PAUSE_SEQUENCE_MS) {
                    spinActive = false;
                }
                invalidate();
            });
            playAnimator.addListener(new SimpleAnimatorEndListener(() -> {
                playAnimator = null;
                playProgress = targetPlaying ? 1f : 0f;
                tonearmEngagement = targetPlaying ? 1f : 0f;
                spinActive = targetPlaying;
                invalidate();
            }));
            playAnimator.start();
        }

        private void animateTonearmEngagement(float target, long durationMs) {
            if (tonearmAnimator != null) tonearmAnimator.cancel();
            if (!animationsEnabled) {
                tonearmEngagement = target;
                invalidate();
                return;
            }
            tonearmAnimator = ValueAnimator.ofFloat(tonearmEngagement, target);
            tonearmAnimator.setDuration(durationMs);
            tonearmAnimator.setInterpolator(new DecelerateInterpolator(1.35f));
            tonearmAnimator.addUpdateListener(animation -> {
                tonearmEngagement = (Float) animation.getAnimatedValue();
                invalidate();
            });
            tonearmAnimator.addListener(new SimpleAnimatorEndListener(() -> {
                tonearmAnimator = null;
                tonearmEngagement = target;
                invalidate();
            }));
            tonearmAnimator.start();
        }

        private float playCompositionForStart(float timeline) {
            float unsleeved = smoothStep(0f, 780f / PLAY_SEQUENCE_MS, timeline) * 0.72f;
            float settled = smoothStep(
                    1000f / PLAY_SEQUENCE_MS,
                    1560f / PLAY_SEQUENCE_MS,
                    timeline
            ) * 0.28f;
            return clamp(unsleeved + settled);
        }

        private float playCompositionForPause(float timeline) {
            if (timeline <= 720f / PAUSE_SEQUENCE_MS) return 1f;
            if (timeline <= 1180f / PAUSE_SEQUENCE_MS) {
                return lerp(
                        1f,
                        0.88f,
                        smoothStep(720f / PAUSE_SEQUENCE_MS, 1180f / PAUSE_SEQUENCE_MS, timeline)
                );
            }
            if (timeline <= 1250f / PAUSE_SEQUENCE_MS) {
                return lerp(
                        0.88f,
                        0.72f,
                        smoothStep(1180f / PAUSE_SEQUENCE_MS, 1250f / PAUSE_SEQUENCE_MS, timeline)
                );
            }
            return lerp(
                    0.72f,
                    0f,
                    smoothStep(1250f / PAUSE_SEQUENCE_MS, 1f, timeline)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (displayedTrack == null || getWidth() <= 0 || getHeight() <= 0) return;
            updateSpin();
            SceneGeometry geometry = sceneGeometry(playProgress, entranceProgress);
            coverBounds.set(geometry.cover);
            recordBounds.set(geometry.record);

            if (incomingTrack != null) {
                drawOutgoingTrack(canvas, geometry);
                drawIncomingTrack(canvas, geometry);
            } else {
                drawRecord(canvas, displayedTrack, geometry.record, 255);
                drawCover(canvas, displayedTrack, geometry.cover, geometry.coverRotation, 255);
                float frontRecordProgress = smoothStep(0.54f, 0.76f, playProgress);
                if (frontRecordProgress > 0f) {
                    drawRecord(canvas, displayedTrack, geometry.record, alpha(frontRecordProgress));
                }
            }
            drawTonearm(canvas, geometry.record);

            if (animationsEnabled && (spinActive || trackAnimator != null || entranceAnimator != null)) {
                postInvalidateOnAnimation();
            }
        }

        private void drawOutgoingTrack(Canvas canvas, SceneGeometry target) {
            float clearPhase = smoothStep(0f, TRACK_RECORD_CLEARED, trackProgress);
            float sleevePhase = smoothStep(
                    TRACK_RECORD_CLEARED,
                    TRACK_RECORD_SLEEVED,
                    trackProgress
            );
            float departPhase = smoothStep(
                    TRACK_RECORD_SLEEVED,
                    TRACK_ALBUM_DEPARTED,
                    trackProgress
            );
            if (departPhase >= 1f) return;
            RectF sleevedRecord = new RectF(
                    target.cover.centerX() - target.record.width() * 0.5f,
                    target.cover.centerY() - target.record.height() * 0.5f,
                    target.cover.centerX() + target.record.width() * 0.5f,
                    target.cover.centerY() + target.record.height() * 0.5f
            );
            RectF clearRecord = new RectF(target.record);
            if (isLandscape()) {
                clearRecord.offset(
                        Math.max(0f, target.cover.right - target.record.left)
                                + target.record.width() * 0.04f,
                        0f
                );
            } else {
                clearRecord.offset(
                        0f,
                        Math.max(0f, target.cover.bottom - target.record.top)
                                + target.record.height() * 0.04f
                );
            }
            RectF record = trackProgress < TRACK_RECORD_CLEARED
                    ? interpolateRect(target.record, clearRecord, clearPhase)
                    : interpolateRect(clearRecord, sleevedRecord, sleevePhase);
            RectF cover = new RectF(target.cover);
            float exitY = -(Math.max(cover.bottom, record.bottom) + dp(36)) * departPhase;
            cover.offset(0f, exitY);
            record.offset(0f, exitY);
            int opacity = alpha(1f - departPhase);
            if (trackProgress < TRACK_RECORD_CLEARED) {
                drawCover(canvas, displayedTrack, cover, target.coverRotation, opacity);
                drawRecord(canvas, displayedTrack, record, opacity);
            } else {
                drawRecord(canvas, displayedTrack, record, opacity);
                drawCover(canvas, displayedTrack, cover, lerp(target.coverRotation, 0f, sleevePhase), opacity);
            }
        }

        private void drawIncomingTrack(Canvas canvas, SceneGeometry target) {
            float coverPhase = smoothStep(TRACK_ALBUM_DEPARTED, TRACK_ALBUM_ARRIVED, trackProgress);
            float recordPhase = smoothStep(TRACK_ALBUM_ARRIVED, TRACK_RECORD_EMERGED, trackProgress);
            float raisePhase = smoothStep(TRACK_RECORD_EMERGED, TRACK_RECORD_RAISED, trackProgress);
            if (coverPhase <= 0f) return;
            RectF startCover = new RectF(target.cover);
            if (isLandscape()) {
                startCover.offset(getWidth() - target.cover.left + target.cover.width() * 0.18f,
                        -target.cover.height() * 0.38f);
            } else {
                startCover.offset(getWidth() - target.cover.left + target.cover.width() * 0.18f,
                        getHeight() - target.cover.top + target.cover.height() * 0.18f);
            }
            RectF cover = interpolateRect(startCover, target.cover, coverPhase);
            incomingCoverBounds.set(cover);
            if (recordPhase <= 0f) {
                drawCover(canvas, incomingTrack, cover, lerp(18f, target.coverRotation, coverPhase), alpha(coverPhase));
                return;
            }
            RectF hiddenRecord = new RectF(
                    target.cover.centerX() - target.record.width() * 0.5f,
                    target.cover.centerY() - target.record.height() * 0.5f,
                    target.cover.centerX() + target.record.width() * 0.5f,
                    target.cover.centerY() + target.record.height() * 0.5f
            );
            RectF record = interpolateRect(hiddenRecord, target.record, recordPhase);
            drawRecord(canvas, incomingTrack, record, alpha(recordPhase * (1f - raisePhase)));
            drawCover(canvas, incomingTrack, cover, target.coverRotation, alpha(coverPhase));
            if (raisePhase > 0f) {
                drawRecord(canvas, incomingTrack, record, alpha(recordPhase * raisePhase));
            }
        }

        private SceneGeometry sceneGeometry(float expansion, float entrance) {
            float width = getWidth();
            float height = getHeight();
            boolean landscape = isLandscape();
            float lyricReserve = lyricsEnabled
                    ? (landscape ? dp(culturalAnnotationsVisible ? 138 : 112) : dp(culturalAnnotationsVisible ? 180 : 154))
                    : 0f;
            float availableHeight = Math.max(dp(260), height - lyricReserve);
            float size = landscape
                    ? Math.min(width * 0.34f, availableHeight * 0.72f)
                    : Math.min(width * 0.78f, availableHeight * 0.46f);
            float maxScale = Math.max(1f, Math.max(albumScale, recordScale));
            size = Math.min(size, availableHeight * 0.92f / maxScale);
            if (!landscape) {
                size = Math.min(size, width * 0.94f / maxScale);
            }
            size = Math.max(dp(170), size);

            float easedEntrance = smoothStep(0f, 1f, entrance);
            float coverX;
            float coverY;
            float recordX;
            float recordY;
            if (landscape) {
                float centerY = availableHeight * 0.49f;
                // Desktop geometry: the stopped sleeve + 40.2%-exposed LP and
                // the fully opened 1.82:1 composition share the same center.
                float pausedCoverX = width * 0.50f - size * 0.701f;
                float playingCoverX = width * 0.50f - size * 0.91f;
                float pausedRecordX = pausedCoverX + size * 0.402f;
                float playingRecordX = playingCoverX + size * 0.82f;
                coverX = lerp(pausedCoverX, playingCoverX, expansion);
                coverY = centerY - size * 0.5f;
                recordX = lerp(pausedRecordX, playingRecordX, expansion);
                recordY = centerY - size * 0.5f;
            } else {
                float centerX = width * 0.5f;
                float pausedCoverY = availableHeight * 0.16f;
                float playingCoverY = availableHeight * 0.10f;
                float coverSize = lerp(size, size * 0.78f, expansion);
                float coverCenterX = lerp(centerX, centerX + size * 0.03f, expansion);
                float coverCenterY = lerp(
                        pausedCoverY + size * 0.50f,
                        playingCoverY + size * 0.49f,
                        expansion
                );
                coverX = coverCenterX - coverSize * 0.5f;
                coverY = coverCenterY - coverSize * 0.5f;
                recordX = centerX - size * 0.5f;
                recordY = lerp(pausedCoverY + size * 0.40f, playingCoverY + size * 0.58f, expansion);
                float entryOffset = (1f - easedEntrance) * dp(54);
                coverY += entryOffset;
                recordY += entryOffset;
                return new SceneGeometry(
                        scaleRect(new RectF(coverX, coverY, coverX + coverSize, coverY + coverSize), albumScale),
                        scaleRect(new RectF(recordX, recordY, recordX + size, recordY + size), recordScale),
                        lerp(0f, -3f, smoothStep(0.28f, 1f, expansion)) + lerp(-2f, 0f, easedEntrance)
                );
            }
            float entryOffset = (1f - easedEntrance) * (landscape ? dp(32) : dp(54));
            coverY += entryOffset;
            recordY += entryOffset;
            return new SceneGeometry(
                    scaleRect(new RectF(coverX, coverY, coverX + size, coverY + size), albumScale),
                    scaleRect(new RectF(recordX, recordY, recordX + size, recordY + size), recordScale),
                    lerp(0f, -5f, smoothStep(0.28f, 1f, expansion)) + lerp(-2f, 0f, easedEntrance)
            );
        }

        private void drawCover(Canvas canvas, VisualTrack track, RectF bounds, float rotation, int alpha) {
            int save = canvas.save();
            canvas.rotate(rotation, bounds.centerX(), bounds.centerY());
            float radius = Math.max(dp(4), bounds.width() * 0.024f);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            for (int layer = 3; layer >= 1; layer--) {
                float spread = bounds.width() * 0.004f * layer;
                RectF shadow = new RectF(bounds);
                shadow.inset(-spread, -spread);
                shadow.offset(0f, bounds.width() * (0.010f + layer * 0.004f));
                paint.setColor(Color.argb(Math.round(alpha * (0.025f + layer * 0.018f)), 0, 0, 0));
                canvas.drawRoundRect(shadow, radius + spread, radius + spread, paint);
            }
            paint.setAlpha(alpha);
            if (track.artwork != null && !track.artwork.isRecycled()) {
                BitmapShader shader = new BitmapShader(track.artwork, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                float scale = Math.max(bounds.width() / track.artwork.getWidth(), bounds.height() / track.artwork.getHeight());
                bitmapMatrix.reset();
                bitmapMatrix.setScale(scale, scale);
                bitmapMatrix.postTranslate(
                        bounds.left + (bounds.width() - track.artwork.getWidth() * scale) * 0.5f,
                        bounds.top + (bounds.height() - track.artwork.getHeight() * scale) * 0.5f
                );
                shader.setLocalMatrix(bitmapMatrix);
                paint.setShader(shader);
            } else {
                paint.setShader(new LinearGradient(
                        bounds.left, bounds.top, bounds.right, bounds.bottom,
                        new int[]{Color.rgb(244, 66, 145), Color.rgb(112, 53, 223)},
                        null,
                        Shader.TileMode.CLAMP
                ));
            }
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(255);
            canvas.restoreToCount(save);
        }

        private void drawRecord(Canvas canvas, VisualTrack track, RectF bounds, int alpha) {
            int save = canvas.save();
            canvas.rotate(spinDegrees, bounds.centerX(), bounds.centerY());
            paint.setShader(new android.graphics.RadialGradient(
                    bounds.centerX() - bounds.width() * 0.13f,
                    bounds.centerY() - bounds.height() * 0.18f,
                    bounds.width() * 0.72f,
                    new int[]{Color.rgb(32, 32, 34), Color.rgb(10, 10, 11), Color.rgb(24, 24, 25)},
                    new float[]{0f, 0.72f, 1f},
                    Shader.TileMode.CLAMP
            ));
            paint.setAlpha(alpha);
            canvas.drawOval(bounds, paint);
            paint.setShader(null);

            groovePaint.setStrokeWidth(Math.max(1f, bounds.width() * 0.0022f));
            for (int index = 0; index < 34; index++) {
                float grooveOpacity = index % 4 == 0 ? 0.055f : 0.020f;
                groovePaint.setColor(Color.argb(Math.round(alpha * grooveOpacity), 255, 255, 255));
                float inset = bounds.width() * (0.035f + index * 0.0113f);
                canvas.drawOval(new RectF(bounds.left + inset, bounds.top + inset,
                        bounds.right - inset, bounds.bottom - inset), groovePaint);
            }

            if (!centerRotationEnabled) {
                canvas.rotate(-spinDegrees, bounds.centerX(), bounds.centerY());
            }

            float labelRadius = bounds.width() * 0.2235f;
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new android.graphics.RadialGradient(
                    bounds.centerX(), bounds.centerY() - labelRadius * 0.10f, labelRadius,
                    new int[]{Color.rgb(48, 38, 42), Color.rgb(33, 25, 28)},
                    null,
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), labelRadius, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(dp(1f), bounds.width() * 0.004f));
            paint.setColor(withAlpha(mixColor(track.accentColor, Color.WHITE, 0.22f), alpha));
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), labelRadius * 0.985f, paint);
            paint.setStrokeWidth(Math.max(dp(0.7f), bounds.width() * 0.0022f));
            paint.setColor(withAlpha(track.accentColor, Math.round(alpha * 0.52f)));
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), labelRadius * 0.84f, paint);
            paint.setStyle(Paint.Style.FILL);

            drawCircularLabel(canvas, track, bounds, labelRadius, alpha);

            drawCenteredText(canvas, ellipsize(track.title, 18), bounds.centerX(),
                    bounds.centerY() - labelRadius * 0.26f,
                    Math.max(dp(9), bounds.width() * 0.0455f),
                    withAlpha(mixColor(track.accentColor, Color.WHITE, 0.18f), alpha), true);
            drawCenteredText(canvas, ellipsize(track.artist, 20), bounds.centerX(),
                    bounds.centerY() + labelRadius * 0.30f,
                    Math.max(dp(6.5f), bounds.width() * 0.0245f), Color.argb(Math.round(alpha * 0.82f), 255, 255, 255), false);
            paint.setColor(Color.argb(alpha, 210, 210, 210));
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), Math.max(dp(2), bounds.width() * 0.008f), paint);
            paint.setAlpha(255);
            canvas.restoreToCount(save);
        }

        private void drawCircularLabel(Canvas canvas, VisualTrack track, RectF bounds, float labelRadius, int alpha) {
            String source = track.album == null || track.album.trim().isEmpty()
                    ? track.title + " · " + track.artist
                    : track.album;
            String text = ellipsize(source.toUpperCase(java.util.Locale.ROOT), 34);
            textPaint.setTypeface(AppFonts.semiBold(getContext()));
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTextSize(Math.max(dp(5.5f), bounds.width() * 0.0168f));
            textPaint.setColor(withAlpha(mixColor(track.accentColor, Color.WHITE, 0.28f), Math.round(alpha * 0.72f)));
            float radius = labelRadius * 0.725f;
            RectF arcBounds = new RectF(
                    bounds.centerX() - radius,
                    bounds.centerY() - radius,
                    bounds.centerX() + radius,
                    bounds.centerY() + radius
            );
            float arcLength = (float) (Math.toRadians(140f) * radius);
            float offset = Math.max(0f, (arcLength - textPaint.measureText(text)) * 0.5f);
            labelArcPath.reset();
            labelArcPath.addArc(arcBounds, 200f, 140f);
            canvas.drawTextOnPath(text, labelArcPath, offset, 0f, textPaint);
            textPaint.setColor(withAlpha(track.accentColor, Math.round(alpha * 0.62f)));
            labelArcPath.reset();
            // Match the desktop SVG: bottom copy also reads left-to-right.
            labelArcPath.addArc(arcBounds, 160f, -140f);
            canvas.drawTextOnPath(text, labelArcPath, offset, 0f, textPaint);
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        private void drawTonearm(Canvas canvas, RectF record) {
            float scale = record.width() / TONEARM_VIEWBOX_HEIGHT * tonearmScale;
            tonearmDrawScale = scale;
            pivotX = record.left + record.width() * 0.8766f;
            pivotY = record.top + record.height() * 0.1032f;

            if (AiLyricsSettings.VINYL_TONEARM_STYLE_LINEAR.equals(tonearmStyle)) {
                drawLinearTonearm(canvas, record, scale);
                resetTonearmPaint();
                return;
            }

            float rotation = currentTonearmRotation();

            double radians = Math.toRadians(rotation);
            float localHeadX = (TONEARM_HEAD_SVG_X - TONEARM_PIVOT_SVG_X) * scale;
            float localHeadY = (TONEARM_HEAD_SVG_Y - TONEARM_PIVOT_SVG_Y) * scale;
            needleX = pivotX + (float) (localHeadX * Math.cos(radians) - localHeadY * Math.sin(radians));
            needleY = pivotY + (float) (localHeadX * Math.sin(radians) + localHeadY * Math.cos(radians));

            int baseSave = canvas.save();
            canvas.translate(pivotX, pivotY);
            canvas.scale(scale, scale);
            canvas.translate(-TONEARM_PIVOT_SVG_X, -TONEARM_PIVOT_SVG_Y);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new android.graphics.RadialGradient(
                    172f, 43f, 95f,
                    tonearmBaseColors(),
                    new float[]{0f, 0.58f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawCircle(183f, 64f, 66f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(tonearmHousingEdgeColor());
            canvas.drawCircle(183f, 64f, 66f, paint);
            canvas.restoreToCount(baseSave);

            int movingSave = canvas.save();
            canvas.translate(pivotX, pivotY);
            canvas.rotate(rotation);
            canvas.scale(scale, scale);
            canvas.translate(-TONEARM_PIVOT_SVG_X, -TONEARM_PIVOT_SVG_Y);

            tonearmPath.reset();
            buildTonearmTubePath(tonearmPath, false);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setShader(null);
            paint.setStrokeWidth(17f);
            paint.setColor(Color.argb(82, 0, 0, 0));
            canvas.drawPath(tonearmPath, paint);
            paint.setStrokeWidth(14f);
            paint.setShader(new LinearGradient(
                    28f, 0f, 210f, 0f,
                    tonearmTubeColors(),
                    new float[]{0f, 0.24f, 0.55f, 1f},
                    Shader.TileMode.CLAMP
            ));
            canvas.drawPath(tonearmPath, paint);
            paint.setShader(null);

            tonearmPath.reset();
            buildTonearmTubePath(tonearmPath, true);
            paint.setStrokeWidth(3f);
            paint.setColor(tonearmHighlightColor());
            canvas.drawPath(tonearmPath, paint);

            tonearmPath.reset();
            tonearmPath.moveTo(151f, 35f);
            tonearmPath.lineTo(200f, 39f);
            tonearmPath.lineTo(215f, 66f);
            tonearmPath.lineTo(207f, 109f);
            tonearmPath.lineTo(170f, 111f);
            tonearmPath.lineTo(151f, 91f);
            tonearmPath.lineTo(144f, 61f);
            tonearmPath.close();
            drawTonearmFilledPath(canvas, tonearmPath);

            tonearmPath.reset();
            tonearmPath.moveTo(158f, 42f);
            tonearmPath.lineTo(194f, 45f);
            tonearmPath.lineTo(207f, 65f);
            tonearmPath.lineTo(202f, 91f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(4f);
            paint.setColor(tonearmHighlightColor());
            canvas.drawPath(tonearmPath, paint);

            tonearmPath.reset();
            tonearmPath.moveTo(47f, 490f);
            tonearmPath.lineTo(75f, 508f);
            tonearmPath.lineTo(54f, 546f);
            tonearmPath.lineTo(30f, 540f);
            tonearmPath.lineTo(17f, 522f);
            tonearmPath.lineTo(24f, 506f);
            tonearmPath.close();
            drawTonearmFilledPath(canvas, tonearmPath);

            tonearmPath.reset();
            tonearmPath.moveTo(28f, 509f);
            tonearmPath.lineTo(66f, 517f);
            tonearmPath.lineTo(49f, 539f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(4f);
            paint.setColor(tonearmHighlightColor());
            canvas.drawPath(tonearmPath, paint);

            tonearmPath.reset();
            tonearmPath.moveTo(35f, 539f);
            tonearmPath.lineTo(33f, 555f);
            tonearmPath.moveTo(48f, 542f);
            tonearmPath.lineTo(53f, 557f);
            paint.setStrokeCap(Paint.Cap.SQUARE);
            paint.setStrokeWidth(5f);
            paint.setColor(tonearmNeedleColor());
            canvas.drawPath(tonearmPath, paint);
            canvas.restoreToCount(movingSave);

            resetTonearmPaint();
        }

        private void drawLinearTonearm(Canvas canvas, RectF record, float scale) {
            float progress = currentTonearmProgress();
            linearTonearmOuterX = record.left + record.width() * 0.86f;
            float carriageX = linearTonearmOuterX - TONEARM_LINEAR_TRAVEL * scale * progress;
            float railY = pivotY;
            pivotX = carriageX;
            needleX = carriageX - 10f * scale;
            needleY = railY + (544f - 64f) * scale;

            float railStart = linearTonearmOuterX - 140f * scale;
            float railEnd = linearTonearmOuterX + 60f * scale;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setShader(null);
            paint.setStrokeWidth(20f * scale);
            paint.setColor(Color.argb(78, 0, 0, 0));
            canvas.drawLine(railStart, railY + 4f * scale, railEnd, railY + 4f * scale, paint);
            paint.setStrokeWidth(16f * scale);
            paint.setShader(new LinearGradient(
                    railStart, railY, railEnd, railY,
                    tonearmTubeColors(), new float[]{0f, 0.24f, 0.55f, 1f}, Shader.TileMode.CLAMP
            ));
            canvas.drawLine(railStart, railY, railEnd, railY, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(tonearmHousingColor());
            canvas.drawCircle(railStart, railY, 18f * scale, paint);
            canvas.drawCircle(railEnd, railY, 18f * scale, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * scale);
            paint.setColor(tonearmHousingEdgeColor());
            canvas.drawCircle(railStart, railY, 18f * scale, paint);
            canvas.drawCircle(railEnd, railY, 18f * scale, paint);

            int movingSave = canvas.save();
            canvas.translate(carriageX, railY);
            canvas.scale(scale, scale);
            canvas.translate(-170f, -64f);

            tonearmPath.reset();
            tonearmPath.moveTo(170f, 87f);
            tonearmPath.lineTo(170f, 476f);
            tonearmPath.lineTo(160f, 510f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setShader(null);
            paint.setStrokeWidth(17f);
            paint.setColor(Color.argb(82, 0, 0, 0));
            canvas.drawPath(tonearmPath, paint);
            paint.setStrokeWidth(14f);
            paint.setShader(new LinearGradient(
                    140f, 0f, 200f, 0f,
                    tonearmTubeColors(), new float[]{0f, 0.24f, 0.55f, 1f}, Shader.TileMode.CLAMP
            ));
            canvas.drawPath(tonearmPath, paint);
            paint.setShader(null);

            tonearmPath.reset();
            tonearmPath.moveTo(165f, 91f);
            tonearmPath.lineTo(165f, 472f);
            paint.setStrokeWidth(3f);
            paint.setColor(tonearmHighlightColor());
            canvas.drawPath(tonearmPath, paint);

            tonearmPath.reset();
            tonearmPath.addRoundRect(new RectF(144f, 38f, 196f, 104f), 13f, 13f, Path.Direction.CW);
            drawTonearmFilledPath(canvas, tonearmPath);
            tonearmPath.reset();
            tonearmPath.moveTo(153f, 48f);
            tonearmPath.lineTo(187f, 48f);
            tonearmPath.lineTo(187f, 86f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(4f);
            paint.setColor(tonearmHighlightColor());
            canvas.drawPath(tonearmPath, paint);

            tonearmPath.reset();
            tonearmPath.moveTo(139f, 487f);
            tonearmPath.lineTo(182f, 487f);
            tonearmPath.lineTo(184f, 529f);
            tonearmPath.lineTo(146f, 542f);
            tonearmPath.lineTo(132f, 523f);
            tonearmPath.close();
            drawTonearmFilledPath(canvas, tonearmPath);
            tonearmPath.reset();
            tonearmPath.moveTo(146f, 496f);
            tonearmPath.lineTo(174f, 496f);
            tonearmPath.lineTo(175f, 521f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f);
            paint.setColor(tonearmHighlightColor());
            canvas.drawPath(tonearmPath, paint);

            tonearmPath.reset();
            tonearmPath.moveTo(149f, 537f);
            tonearmPath.lineTo(148f, 555f);
            tonearmPath.moveTo(164f, 533f);
            tonearmPath.lineTo(168f, 552f);
            paint.setStrokeCap(Paint.Cap.SQUARE);
            paint.setStrokeWidth(5f);
            paint.setColor(tonearmNeedleColor());
            canvas.drawPath(tonearmPath, paint);
            canvas.restoreToCount(movingSave);
        }

        private void buildTonearmTubePath(Path path, boolean highlight) {
            if (AiLyricsSettings.VINYL_TONEARM_STYLE_STRAIGHT.equals(tonearmStyle)) {
                path.moveTo(highlight ? 184f : 189f, highlight ? 79f : 75f);
                path.lineTo(highlight ? 53f : 58f, highlight ? 508f : 513f);
                return;
            }
            if (AiLyricsSettings.VINYL_TONEARM_STYLE_J.equals(tonearmStyle)) {
                path.moveTo(highlight ? 184f : 189f, highlight ? 79f : 75f);
                path.lineTo(highlight ? 175f : 181f, highlight ? 369f : 372f);
                path.cubicTo(
                        highlight ? 173f : 179f, highlight ? 424f : 432f,
                        highlight ? 135f : 139f, highlight ? 474f : 481f,
                        highlight ? 55f : 58f, highlight ? 507f : 513f
                );
                return;
            }
            path.moveTo(highlight ? 184f : 189f, highlight ? 79f : 75f);
            path.cubicTo(
                    highlight ? 178f : 184f, highlight ? 179f : 172f,
                    highlight ? 145f : 151f, 330f,
                    highlight ? 74f : 78f, highlight ? 469f : 474f
            );
            if (!highlight) path.lineTo(58f, 513f);
        }

        private void resetTonearmPaint() {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStrokeJoin(Paint.Join.MITER);
        }

        private void drawTonearmFilledPath(Canvas canvas, Path path) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(tonearmHousingColor());
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(tonearmHousingEdgeColor());
            canvas.drawPath(path, paint);
        }

        private int[] tonearmBaseColors() {
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_BLACK.equals(tonearmFinish)) {
                return new int[]{Color.rgb(85, 86, 90), Color.rgb(36, 37, 41), Color.rgb(8, 9, 11)};
            }
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_SILVER.equals(tonearmFinish)) {
                return new int[]{Color.rgb(244, 244, 245), Color.rgb(191, 193, 197), Color.rgb(119, 122, 128)};
            }
            return new int[]{Color.argb(224, 255, 255, 255), Color.argb(184, 251, 251, 251), Color.argb(148, 238, 238, 238)};
        }

        private int[] tonearmTubeColors() {
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_BLACK.equals(tonearmFinish)) {
                return new int[]{Color.rgb(5, 5, 6), Color.rgb(85, 86, 90), Color.rgb(29, 30, 33), Color.rgb(2, 2, 3)};
            }
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_SILVER.equals(tonearmFinish)) {
                return new int[]{Color.rgb(85, 88, 93), Color.rgb(218, 220, 224), Color.rgb(247, 247, 248), Color.rgb(115, 118, 123)};
            }
            return new int[]{Color.rgb(170, 170, 170), Color.rgb(250, 250, 250), Color.WHITE, Color.rgb(187, 187, 187)};
        }

        private int tonearmHousingColor() {
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_BLACK.equals(tonearmFinish)) return Color.rgb(24, 25, 28);
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_SILVER.equals(tonearmFinish)) return Color.rgb(184, 187, 192);
            return Color.rgb(252, 252, 252);
        }

        private int tonearmHousingEdgeColor() {
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_BLACK.equals(tonearmFinish)) return Color.rgb(91, 93, 99);
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_SILVER.equals(tonearmFinish)) return Color.rgb(98, 101, 107);
            return Color.rgb(230, 230, 230);
        }

        private int tonearmHighlightColor() {
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_BLACK.equals(tonearmFinish)) return Color.argb(96, 255, 255, 255);
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_SILVER.equals(tonearmFinish)) return Color.argb(194, 255, 255, 255);
            return Color.argb(242, 255, 255, 255);
        }

        private int tonearmNeedleColor() {
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_BLACK.equals(tonearmFinish)) return Color.rgb(169, 171, 176);
            if (AiLyricsSettings.VINYL_TONEARM_FINISH_SILVER.equals(tonearmFinish)) return Color.rgb(185, 188, 193);
            return Color.rgb(238, 238, 238);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event == null || displayedTrack == null) return false;
            if (incomingTrack != null) return true;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    longPressTriggered = false;
                    float initialTonearmRotation = currentTonearmRotation();
                    float initialTonearmProgress = currentTonearmProgress();
                    scrubbingTonearm = distance(event.getX(), event.getY(), needleX, needleY) <= dp(54)
                            || distanceToSegment(event.getX(), event.getY(), pivotX, pivotY, needleX, needleY) <= dp(28);
                    if (scrubbingTonearm) {
                        activeTonearmPointerId = event.getPointerId(event.getActionIndex());
                        dragStartedPlaying = playing;
                        dragTonearmRotation = initialTonearmRotation;
                        dragRawTonearmRotation = initialTonearmRotation;
                        dragTonearmProgress = initialTonearmProgress;
                        dragRawTonearmProgress = dragTonearmProgress;
                        if (AiLyricsSettings.VINYL_TONEARM_STYLE_LINEAR.equals(tonearmStyle)) {
                            dragPointerProgressOffset = dragTonearmProgress
                                    - linearPointerProgress(event.getX());
                        } else {
                            dragPointerAngleOffset = initialTonearmRotation
                                    - pointerAngle(event.getX(), event.getY());
                        }
                        dragStartPointerX = event.getX();
                        dragStartPointerY = event.getY();
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        invalidate();
                        return true;
                    }
                    boolean recordInFront = playProgress >= 0.54f;
                    recordPressed = recordInFront && recordBounds.contains(event.getX(), event.getY());
                    coverPressed = !recordPressed && coverBounds.contains(event.getX(), event.getY());
                    if (!recordInFront && !coverPressed) {
                        recordPressed = recordBounds.contains(event.getX(), event.getY());
                    }
                    if (coverPressed) scheduleLongPress();
                    return coverPressed || recordPressed;
                case MotionEvent.ACTION_MOVE:
                    if (scrubbingTonearm) {
                        int pointerIndex = event.findPointerIndex(activeTonearmPointerId);
                        if (pointerIndex < 0) {
                            cancelTonearmDrag();
                            return true;
                        }
                        updateTonearmDrag(event.getX(pointerIndex), event.getY(pointerIndex));
                        return true;
                    }
                    if (coverPressed && !coverBounds.contains(event.getX(), event.getY())) {
                        coverPressed = false;
                        cancelCoverLongPress();
                    }
                    return coverPressed || recordPressed;
                case MotionEvent.ACTION_UP:
                    cancelCoverLongPress();
                    if (scrubbingTonearm) {
                        int pointerIndex = event.findPointerIndex(activeTonearmPointerId);
                        if (pointerIndex < 0) pointerIndex = event.getActionIndex();
                        finishTonearmDrag(event.getX(pointerIndex), event.getY(pointerIndex));
                        return true;
                    }
                    if (longPressTriggered) {
                        resetPressedState();
                        return true;
                    }
                    if (coverPressed && coverBounds.contains(event.getX(), event.getY())) {
                        performClick();
                        if (listener != null) listener.onClose();
                    } else if (recordPressed && recordBounds.contains(event.getX(), event.getY())) {
                        performClick();
                        if (listener != null) listener.onTogglePlayback();
                    }
                    resetPressedState();
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (scrubbingTonearm
                            && event.getPointerId(event.getActionIndex()) == activeTonearmPointerId) {
                        finishTonearmDrag(
                                event.getX(event.getActionIndex()),
                                event.getY(event.getActionIndex())
                        );
                        return true;
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    cancelCoverLongPress();
                    cancelTonearmDrag();
                    resetPressedState();
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private void scheduleLongPress() {
            cancelCoverLongPress();
            longPressRunnable = () -> {
                longPressRunnable = null;
                if (!coverPressed) return;
                longPressTriggered = true;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (listener != null) listener.onShowTmi();
            };
            postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
        }

        private void cancelCoverLongPress() {
            if (longPressRunnable != null) {
                removeCallbacks(longPressRunnable);
                longPressRunnable = null;
            }
        }

        private void updateTonearmDrag(float x, float y) {
            if (AiLyricsSettings.VINYL_TONEARM_STYLE_LINEAR.equals(tonearmStyle)) {
                float candidate = linearPointerProgress(x) + dragPointerProgressOffset;
                dragRawTonearmProgress = candidate;
                dragTonearmProgress = dragStartedPlaying
                        ? Math.max(TONEARM_LINEAR_REST_PROGRESS, Math.min(1f, candidate))
                        : Math.max(TONEARM_LINEAR_REST_PROGRESS, Math.min(0f, candidate));
                dragTonearmRotation = lerp(
                        TONEARM_START_DEGREES,
                        TONEARM_END_DEGREES,
                        clamp(dragTonearmProgress)
                );
                dragRawTonearmRotation = dragTonearmRotation;
                invalidate();
                return;
            }
            float candidate = pointerAngle(x, y) + dragPointerAngleOffset;
            while (candidate - dragTonearmRotation > 180f) candidate -= 360f;
            while (candidate - dragTonearmRotation < -180f) candidate += 360f;
            dragRawTonearmRotation = candidate;
            dragTonearmRotation = dragStartedPlaying
                    ? Math.max(TONEARM_PARK_DEGREES, Math.min(TONEARM_END_DEGREES, candidate))
                    : Math.max(TONEARM_PARK_DEGREES, Math.min(TONEARM_START_DEGREES, candidate));
            dragTonearmProgress = progressForTonearmRotation(dragTonearmRotation);
            dragRawTonearmProgress = dragTonearmProgress;
            invalidate();
        }

        private void finishTonearmDrag(float x, float y) {
            updateTonearmDrag(x, y);
            boolean moved = distance(dragStartPointerX, dragStartPointerY, x, y) > dp(18);
            boolean linearTonearm = AiLyricsSettings.VINYL_TONEARM_STYLE_LINEAR.equals(tonearmStyle);
            boolean ejected = dragStartedPlaying && (
                    (linearTonearm
                            ? dragRawTonearmProgress <= TONEARM_LINEAR_EJECT_PROGRESS
                            : dragRawTonearmRotation <= TONEARM_EJECT_DEGREES)
                            || moved && !recordBounds.contains(x, y)
            );
            boolean shouldCuePlay = !dragStartedPlaying
                    && (linearTonearm
                            ? dragTonearmProgress >= TONEARM_LINEAR_CUE_PLAY_PROGRESS
                            : dragTonearmRotation >= TONEARM_CUE_PLAY_DEGREES);
            float committedProgress = dragTonearmProgress;
            boolean shouldSeek = dragStartedPlaying && durationMs > 0L;
            cancelTonearmDrag();
            if (ejected) {
                if (listener != null) listener.onStopPlayback();
            } else if (listener != null && shouldSeek) {
                listener.onSeek(Math.round(durationMs * committedProgress));
            } else if (listener != null && shouldCuePlay) {
                listener.onTogglePlayback();
            }
        }

        private void cancelTonearmDrag() {
            scrubbingTonearm = false;
            activeTonearmPointerId = MotionEvent.INVALID_POINTER_ID;
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            invalidate();
        }

        private float currentTonearmRotation() {
            if (scrubbingTonearm) return dragTonearmRotation;
            float engagement = effectiveTonearmEngagement();
            float playbackRotation = lerp(
                    TONEARM_START_DEGREES,
                    TONEARM_END_DEGREES,
                    playbackProgress()
            );
            return lerp(TONEARM_PARK_DEGREES, playbackRotation, engagement);
        }

        private float currentTonearmProgress() {
            if (scrubbingTonearm) return dragTonearmProgress;
            float resting = AiLyricsSettings.VINYL_TONEARM_STYLE_LINEAR.equals(tonearmStyle)
                    ? TONEARM_LINEAR_REST_PROGRESS
                    : progressForTonearmRotation(TONEARM_PARK_DEGREES);
            return lerp(resting, playbackProgress(), effectiveTonearmEngagement());
        }

        private float effectiveTonearmEngagement() {
            if (trackAnimator == null) return tonearmEngagement;
            float parkedForTrack = 1f - smoothStep(0f, TRACK_RECORD_SLEEVED, trackProgress);
            return tonearmEngagement * parkedForTrack;
        }

        private float progressForTonearmRotation(float rotation) {
            return clamp((rotation - TONEARM_START_DEGREES)
                    / (TONEARM_END_DEGREES - TONEARM_START_DEGREES));
        }

        private float pointerAngle(float x, float y) {
            return normalizeDegrees((float) Math.toDegrees(Math.atan2(y - pivotY, x - pivotX)));
        }

        private float linearPointerProgress(float x) {
            float safeScale = Math.max(0.0001f, tonearmDrawScale);
            return (linearTonearmOuterX - x) / (TONEARM_LINEAR_TRAVEL * safeScale);
        }

        private void resetPressedState() {
            coverPressed = false;
            recordPressed = false;
        }

        private float playbackProgress() {
            if (durationMs <= 0L) return 0f;
            return clamp(positionMs / (float) durationMs);
        }

        private void updateSpin() {
            long now = SystemClock.uptimeMillis();
            if (spinFrameUptimeMs == 0L) spinFrameUptimeMs = now;
            long elapsed = Math.max(0L, now - spinFrameUptimeMs);
            spinFrameUptimeMs = now;
            if (animationsEnabled && spinActive && !scrubbingTonearm) {
                spinDegrees = (spinDegrees + elapsed * 0.009f) % 360f;
            }
        }

        private void drawCenteredText(Canvas canvas, String text, float x, float y, float size, int color, boolean bold) {
            textPaint.setTextSize(size);
            textPaint.setColor(color);
            textPaint.setTypeface(bold ? AppFonts.bold(getContext()) : AppFonts.regular(getContext()));
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) * 0.5f, textPaint);
        }

        private boolean isLandscape() {
            return getWidth() > getHeight();
        }

        private int alpha(float value) {
            return Math.round(255f * clamp(value));
        }

        private int dp(float value) {
            return Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    value,
                    getResources().getDisplayMetrics()
            ));
        }

        private static float normalizeDegrees(float value) {
            float result = value % 360f;
            return result < 0f ? result + 360f : result;
        }

        private static float distance(float x1, float y1, float x2, float y2) {
            return (float) Math.hypot(x2 - x1, y2 - y1);
        }

        private static float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            float denominator = dx * dx + dy * dy;
            if (denominator <= 0.0001f) return distance(px, py, x1, y1);
            float t = clamp(((px - x1) * dx + (py - y1) * dy) / denominator);
            return distance(px, py, x1 + dx * t, y1 + dy * t);
        }

        private static RectF interpolateRect(RectF from, RectF to, float progress) {
            return new RectF(
                    lerp(from.left, to.left, progress),
                    lerp(from.top, to.top, progress),
                    lerp(from.right, to.right, progress),
                    lerp(from.bottom, to.bottom, progress)
            );
        }

        private static RectF scaleRect(RectF source, float scale) {
            float halfWidth = source.width() * Math.max(0.7f, Math.min(1.4f, scale)) * 0.5f;
            float halfHeight = source.height() * Math.max(0.7f, Math.min(1.4f, scale)) * 0.5f;
            return new RectF(
                    source.centerX() - halfWidth,
                    source.centerY() - halfHeight,
                    source.centerX() + halfWidth,
                    source.centerY() + halfHeight
            );
        }

        private static float smoothStep(float start, float end, float value) {
            if (end <= start) return value >= end ? 1f : 0f;
            float t = clamp((value - start) / (end - start));
            return t * t * (3f - 2f * t);
        }

        private static float lerp(float start, float end, float progress) {
            return start + (end - start) * clamp(progress);
        }

        private static float clamp(float value) {
            return Math.max(0f, Math.min(1f, value));
        }

        private static int withAlpha(int color, int alpha) {
            return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
        }

        private static int mixColor(int from, int to, float toAmount) {
            float amount = clamp(toAmount);
            return Color.rgb(
                    Math.round(lerp(Color.red(from), Color.red(to), amount)),
                    Math.round(lerp(Color.green(from), Color.green(to), amount)),
                    Math.round(lerp(Color.blue(from), Color.blue(to), amount))
            );
        }

        private static String ellipsize(String value, int maxLength) {
            String text = value == null ? "" : value.trim();
            if (text.length() <= maxLength) return text;
            return text.substring(0, Math.max(1, maxLength - 1)) + "…";
        }

        private static final class SceneGeometry {
            final RectF cover;
            final RectF record;
            final float coverRotation;

            SceneGeometry(RectF cover, RectF record, float coverRotation) {
                this.cover = cover;
                this.record = record;
                this.coverRotation = coverRotation;
            }
        }

        private static final class VisualTrack {
            final String key;
            final String title;
            final String artist;
            final String album;
            final Bitmap artwork;
            final int accentColor;

            VisualTrack(TrackSnapshot snapshot, Bitmap artwork) {
                this(snapshot.stableKey(), snapshot.title, snapshot.artist, snapshot.album, artwork,
                        accentColor(artwork, snapshot.stableKey()));
            }

            private VisualTrack(String key, String title, String artist, String album, Bitmap artwork, int accentColor) {
                this.key = key;
                this.title = title;
                this.artist = artist;
                this.album = album;
                this.artwork = artwork;
                this.accentColor = accentColor;
            }

            VisualTrack withArtwork(Bitmap nextArtwork) {
                return new VisualTrack(key, title, artist, album, nextArtwork,
                        accentColor(nextArtwork, key));
            }

            VisualTrack withFallbackArtwork(Bitmap fallback) {
                return artwork == null && fallback != null ? withArtwork(fallback) : this;
            }

            private static int accentColor(Bitmap bitmap, String fallbackKey) {
                if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
                    int hash = fallbackKey == null ? 0 : fallbackKey.hashCode();
                    float hue = Math.abs(hash % 360);
                    return Color.HSVToColor(new float[]{hue, 0.48f, 1f});
                }
                long red = 0L;
                long green = 0L;
                long blue = 0L;
                int samples = 0;
                int stepX = Math.max(1, bitmap.getWidth() / 18);
                int stepY = Math.max(1, bitmap.getHeight() / 18);
                for (int y = stepY / 2; y < bitmap.getHeight(); y += stepY) {
                    for (int x = stepX / 2; x < bitmap.getWidth(); x += stepX) {
                        int color = bitmap.getPixel(x, y);
                        int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
                        int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
                        if (max < 36 || max - min < 12) continue;
                        red += Color.red(color);
                        green += Color.green(color);
                        blue += Color.blue(color);
                        samples++;
                    }
                }
                if (samples == 0) return Color.rgb(255, 128, 157);
                float[] hsv = new float[3];
                Color.RGBToHSV((int) (red / samples), (int) (green / samples), (int) (blue / samples), hsv);
                hsv[1] = Math.max(0.42f, Math.min(0.82f, hsv[1]));
                hsv[2] = Math.max(0.76f, Math.min(1f, hsv[2]));
                return Color.HSVToColor(hsv);
            }
        }
    }

    private static final class SimpleAnimatorEndListener implements android.animation.Animator.AnimatorListener {
        private final Runnable endAction;
        private boolean cancelled;

        SimpleAnimatorEndListener(Runnable endAction) {
            this.endAction = endAction;
        }

        @Override public void onAnimationStart(android.animation.Animator animation) { cancelled = false; }
        @Override public void onAnimationEnd(android.animation.Animator animation) {
            if (!cancelled && endAction != null) endAction.run();
        }
        @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
        @Override public void onAnimationRepeat(android.animation.Animator animation) { }
    }
}
