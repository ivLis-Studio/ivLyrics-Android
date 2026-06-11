package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LyricsView extends View {
    private static final int VISIBLE_RADIUS = 2;
    private static final float SIDE_PADDING_SP = 18f;
    private static final float MAIN_TEXT_SP = 25f;
    private static final float SUPPLEMENT_TEXT_SP = 14f;
    private static final float EMPTY_TEXT_SP = 22f;
    private static final float LINE_HEIGHT_MULTIPLIER = 1.34f;
    private static final float FURIGANA_TEXT_RATIO = 0.42f;
    private static final float FURIGANA_EXTRA_HEIGHT_RATIO = 0.34f;
    private static final float PART_GAP_SP = 4f;
    private static final float FURIGANA_MULTI_VOCAL_GAP_SP = 8f;
    private static final float SUPPLEMENT_GAP_SP = 2f;
    private static final float BLOCK_GAP_SP = 32f;
    private static final float BOTTOM_EDGE_FADE_DP = 34f;
    private static final float WAVE_PERIOD_MS = 980f;
    private static final int KARAOKE_BOUNCE_MAX_SEGMENT_DISTANCE = 3;
    private static final long KARAOKE_BOUNCE_PRELEAD_MS = 70L;
    private static final long KARAOKE_BOUNCE_RISE_MS = 220L;
    private static final long KARAOKE_BOUNCE_RELEASE_MS = 640L;
    private static final long INTERLUDE_MIN_DURATION_MS = 500L;
    private static final long KARAOKE_TRAILING_INTERLUDE_DELAY_MS = 3_500L;
    private static final long MANUAL_SCROLL_HOLD_MS = 4_000L;
    private static final int SPEAKER_B_COLOR = Color.rgb(139, 211, 255);
    private static final int SPEAKER_C_COLOR = Color.rgb(255, 209, 102);
    private static final int SPEAKER_D_COLOR = Color.rgb(196, 167, 255);
    private static final int SPEAKER_SFX_COLOR = Color.rgb(244, 166, 200);

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint interludePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint skeletonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF edgeFadeBounds = new RectF();
    private final List<LineHitTarget> hitTargets = new ArrayList<>();
    private final Map<String, BounceState> bounceStates = new HashMap<>();
    private final Map<String, List<TextRow>> rowLayoutCache = new HashMap<>();
    private final Set<String> completedBounceKeys = new HashSet<>();
    private final Runnable rowPrewarmRunnable = this::prewarmRowLayouts;
    private Typeface lyricTypeface;
    private AiLyricsSettings.TypographySettings typographySettings = AiLyricsSettings.TypographySettings.defaults();
    private AiLyricsSettings.SpeakerColorSettings speakerColorSettings = AiLyricsSettings.SpeakerColorSettings.defaults();

    private List<LyricsLine> lines = Collections.emptyList();
    private long positionMs;
    private String emptyMessage = AppI18n.t("en", "status.lyrics_waiting");
    private String loadingMessage = AppI18n.t("en", "status.lyrics_loading");
    private String emptyFallbackMessage = AppI18n.t("en", "lyrics.empty_none");
    private String preludeLabel = AppI18n.t("en", "interlude.prelude");
    private String breakLabel = AppI18n.t("en", "interlude.break");
    private String postludeLabel = AppI18n.t("en", "interlude.postlude");
    private boolean karaoke;
    private boolean autoInstrumentalBreakEnabled = true;
    private boolean interludeLabelsEnabled = true;
    private boolean syncedLyricsKaraokeAnimationEnabled = true;
    private boolean karaokeBounceEffectEnabled = true;
    private boolean japaneseFuriganaEnabled;
    private boolean pronunciationLoading;
    private boolean translationLoading;
    private boolean centerInitialized;
    private float animatedCenterIndex;
    private int currentDisplayLineCount;
    private long trackDurationMs;
    private float verticalCenterBias = 0.50f;
    private long lastFrameMs;
    private float manualCenterIndex;
    private float scrollPixelsPerIndex = 1f;
    private float manualScrollPixelsPerIndex = 1f;
    private float touchStartY;
    private float touchLastY;
    private int touchSlop;
    private boolean manualScrollActive;
    private boolean draggingLyrics;
    private long lastManualScrollUptimeMs;
    private int rowPrewarmIndex;
    private int minimumFlingVelocity;
    private int maximumFlingVelocity;
    private OverScroller manualScroller;
    private VelocityTracker velocityTracker;
    private OnSeekListener seekListener;
    private LineHitTarget pressedTarget;
    private boolean smoothNextSeekCenter;
    private boolean smoothSeekCenterActive;

    public LyricsView(Context context) {
        super(context);
        init();
    }

    public LyricsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void setResult(LyricsResult result) {
        if (result == null || result.lines.isEmpty()) {
            lines = Collections.emptyList();
            emptyMessage = result == null || result.detail.isEmpty() ? emptyFallbackMessage : result.detail;
            karaoke = false;
        } else {
            lines = result.lines;
            emptyMessage = "";
            karaoke = result.karaoke || hasTimedKaraokeData(lines);
        }
        centerInitialized = false;
        lastFrameMs = 0L;
        hitTargets.clear();
        bounceStates.clear();
        rowLayoutCache.clear();
        completedBounceKeys.clear();
        rowPrewarmIndex = 0;
        currentDisplayLineCount = Math.max(0, lines.size());
        manualScrollActive = false;
        draggingLyrics = false;
        smoothNextSeekCenter = false;
        smoothSeekCenterActive = false;
        pressedTarget = null;
        if (manualScroller != null) {
            manualScroller.abortAnimation();
        }
        scheduleRowPrewarm();
        postInvalidateOnAnimation();
    }

    void setUiText(
            String loadingMessage,
            String emptyFallbackMessage,
            String preludeLabel,
            String breakLabel,
            String postludeLabel
    ) {
        this.loadingMessage = loadingMessage == null || loadingMessage.trim().isEmpty()
                ? AppI18n.t("en", "status.lyrics_loading")
                : loadingMessage;
        this.emptyFallbackMessage = emptyFallbackMessage == null || emptyFallbackMessage.trim().isEmpty()
                ? AppI18n.t("en", "lyrics.empty_none")
                : emptyFallbackMessage;
        this.preludeLabel = preludeLabel == null || preludeLabel.trim().isEmpty()
                ? AppI18n.t("en", "interlude.prelude")
                : preludeLabel;
        this.breakLabel = breakLabel == null || breakLabel.trim().isEmpty()
                ? AppI18n.t("en", "interlude.break")
                : breakLabel;
        this.postludeLabel = postludeLabel == null || postludeLabel.trim().isEmpty()
                ? AppI18n.t("en", "interlude.postlude")
                : postludeLabel;
        if (lines.isEmpty() && (emptyMessage == null || emptyMessage.trim().isEmpty())) {
            emptyMessage = this.emptyFallbackMessage;
        }
        postInvalidateOnAnimation();
    }

    void setTrackDuration(long durationMs) {
        long nextDurationMs = Math.max(0L, durationMs);
        if (trackDurationMs == nextDurationMs) {
            return;
        }
        trackDurationMs = nextDurationMs;
        postInvalidateOnAnimation();
    }

    void setAutoInstrumentalBreakEnabled(boolean enabled) {
        if (autoInstrumentalBreakEnabled == enabled) {
            return;
        }
        autoInstrumentalBreakEnabled = enabled;
        centerInitialized = false;
        postInvalidateOnAnimation();
    }

    void setInterludeLabelsEnabled(boolean enabled) {
        if (interludeLabelsEnabled == enabled) {
            return;
        }
        interludeLabelsEnabled = enabled;
        postInvalidateOnAnimation();
    }

    void setSyncedLyricsKaraokeAnimationEnabled(boolean enabled) {
        if (syncedLyricsKaraokeAnimationEnabled == enabled) {
            return;
        }
        syncedLyricsKaraokeAnimationEnabled = enabled;
        rowLayoutCache.clear();
        bounceStates.clear();
        completedBounceKeys.clear();
        postInvalidateOnAnimation();
    }

    void setKaraokeBounceEffectEnabled(boolean enabled) {
        if (karaokeBounceEffectEnabled == enabled) {
            return;
        }
        karaokeBounceEffectEnabled = enabled;
        bounceStates.clear();
        completedBounceKeys.clear();
        postInvalidateOnAnimation();
    }

    void setJapaneseFuriganaEnabled(boolean enabled) {
        if (japaneseFuriganaEnabled == enabled) {
            return;
        }
        japaneseFuriganaEnabled = enabled;
        rowLayoutCache.clear();
        postInvalidateOnAnimation();
    }

    void setTypographySettings(AiLyricsSettings.TypographySettings settings) {
        typographySettings = settings == null ? AiLyricsSettings.TypographySettings.defaults() : settings;
        rowLayoutCache.clear();
        requestLayout();
        postInvalidateOnAnimation();
    }

    void setSpeakerColorSettings(AiLyricsSettings.SpeakerColorSettings settings) {
        speakerColorSettings = settings == null ? AiLyricsSettings.SpeakerColorSettings.defaults() : settings;
        postInvalidateOnAnimation();
    }

    void setSupplementLoading(boolean pronunciation, boolean translation) {
        if (pronunciationLoading == pronunciation && translationLoading == translation) {
            return;
        }
        pronunciationLoading = pronunciation;
        translationLoading = translation;
        rowLayoutCache.clear();
        postInvalidateOnAnimation();
    }

    void setPlaybackPosition(long positionMs) {
        long nextPositionMs = Math.max(0L, positionMs);
        boolean smoothSeekCenter = smoothNextSeekCenter && centerInitialized;
        smoothNextSeekCenter = false;
        if (nextPositionMs + 120L < this.positionMs || Math.abs(nextPositionMs - this.positionMs) > 1600L) {
            bounceStates.clear();
            completedBounceKeys.clear();
            smoothSeekCenterActive = smoothSeekCenter;
            centerInitialized = smoothSeekCenter;
            manualScrollActive = false;
            draggingLyrics = false;
            pressedTarget = null;
            if (manualScroller != null) {
                manualScroller.abortAnimation();
            }
            lastFrameMs = 0L;
        } else if (smoothSeekCenter) {
            smoothSeekCenterActive = true;
            manualScrollActive = false;
            draggingLyrics = false;
            lastFrameMs = 0L;
        }
        this.positionMs = nextPositionMs;
        postInvalidateOnAnimation();
    }

    void setOnSeekListener(OnSeekListener seekListener) {
        this.seekListener = seekListener;
    }

    void setVerticalCenterBias(float verticalCenterBias) {
        this.verticalCenterBias = Math.max(0.30f, Math.min(0.58f, verticalCenterBias));
        postInvalidateOnAnimation();
    }

    private void init() {
        lyricTypeface = AppFonts.semiBold(getContext());
        setWillNotDraw(false);
        setClickable(true);
        ViewConfiguration configuration = ViewConfiguration.get(getContext());
        touchSlop = configuration.getScaledTouchSlop();
        minimumFlingVelocity = configuration.getScaledMinimumFlingVelocity();
        maximumFlingVelocity = configuration.getScaledMaximumFlingVelocity();
        manualScroller = new OverScroller(getContext());
        textPaint.setTypeface(lyricTypeface);
        textPaint.setSubpixelText(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawBackground(canvas);

        if (lines.isEmpty()) {
            hitTargets.clear();
            drawEmpty(canvas);
            return;
        }

        List<DisplayLine> displayLines = buildDisplayLines();
        if (displayLines.isEmpty()) {
            hitTargets.clear();
            drawEmpty(canvas);
            return;
        }
        currentDisplayLineCount = displayLines.size();

        int activeIndex = findActiveDisplayIndex(displayLines);
        updateDisplayCenter(activeIndex);

        int anchorIndex = Math.max(0, Math.min(displayLines.size() - 1, (int) Math.floor(animatedCenterIndex)));
        int firstIndex = Math.max(0, anchorIndex - VISIBLE_RADIUS - 2);
        int lastIndex = Math.min(displayLines.size() - 1, anchorIndex + VISIBLE_RADIUS + 3);
        List<LineLayout> layouts = new ArrayList<>();
        for (int displayIndex = firstIndex; displayIndex <= lastIndex; displayIndex++) {
            DisplayLine displayLine = displayLines.get(displayIndex);
            boolean active = displayIndex == activeIndex;
            float distance = Math.abs(displayIndex - animatedCenterIndex);
            List<DrawGroup> groups = buildLyricGroups(displayLine, active, distance);
            layouts.add(new LineLayout(displayIndex, displayLine, groups, groupsHeight(groups)));
        }

        float centerY = getHeight() * verticalCenterBias;
        float blockGap = Math.max(sp(BLOCK_GAP_SP), getHeight() * 0.037f);
        float anchorFraction = clamp(animatedCenterIndex - anchorIndex);
        LineLayout anchorLayout = layoutAt(layouts, anchorIndex);
        LineLayout nextLayout = layoutAt(layouts, anchorIndex + 1);
        float scrollOffset = anchorLayout != null && nextLayout != null
                ? anchorFraction * distanceBetween(anchorLayout, nextLayout, blockGap)
                : 0f;
        updateScrollPixelsPerIndex(layouts, blockGap);

        hitTargets.clear();
        int lyricLayer = canvas.saveLayer(edgeFadeBounds(0f, 0f, getWidth(), getHeight()), null);
        for (LineLayout layout : layouts) {
            float baselineCenter = centerY + offsetFromAnchor(layouts, anchorIndex, layout.index, blockGap) - scrollOffset;
            if (baselineCenter + layout.height * 0.5f < -blockGap
                    || baselineCenter - layout.height * 0.5f > getHeight() + blockGap) {
                continue;
            }
            addHitTarget(layout, baselineCenter, blockGap);
            drawGroups(canvas, layout.groups, baselineCenter, topFadeAlpha(baselineCenter, layout.height));
        }
        applyBottomEdgeFade(canvas);
        canvas.restoreToCount(lyricLayer);

        boolean activeInterlude = activeIndex >= 0
                && activeIndex < displayLines.size()
                && displayLines.get(activeIndex).isInterlude();
        if (karaoke || activeInterlude || Math.abs(activeIndex - animatedCenterIndex) > 0.002f) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth || height != oldHeight) {
            rowLayoutCache.clear();
            hitTargets.clear();
            rowPrewarmIndex = 0;
            scheduleRowPrewarm();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (lines.isEmpty()) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (manualScroller != null) {
                    manualScroller.abortAnimation();
                }
                recycleVelocityTracker();
                velocityTracker = VelocityTracker.obtain();
                velocityTracker.addMovement(event);
                touchStartY = event.getY();
                touchLastY = touchStartY;
                draggingLyrics = false;
                pressedTarget = findHitTarget(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                }
                float y = event.getY();
                float totalDy = y - touchStartY;
                if (!draggingLyrics && Math.abs(totalDy) > touchSlop) {
                    draggingLyrics = true;
                    manualScrollActive = true;
                    manualCenterIndex = animatedCenterIndex;
                    manualScrollPixelsPerIndex = Math.max(1f, scrollPixelsPerIndex);
                    pressedTarget = null;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (draggingLyrics) {
                    float dy = y - touchLastY;
                    manualCenterIndex = clampCenterIndex(manualCenterIndex - dy / Math.max(1f, manualScrollPixelsPerIndex));
                    animatedCenterIndex = manualCenterIndex;
                    centerInitialized = true;
                    lastManualScrollUptimeMs = SystemClock.uptimeMillis();
                    postInvalidateOnAnimation();
                }
                touchLastY = y;
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (velocityTracker != null) {
                    velocityTracker.addMovement(event);
                }
                if (draggingLyrics) {
                    draggingLyrics = false;
                    lastManualScrollUptimeMs = SystemClock.uptimeMillis();
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    startManualFling();
                    recycleVelocityTracker();
                    return true;
                }
                LineHitTarget target = pressedTarget != null ? pressedTarget : findHitTarget(event.getY());
                pressedTarget = null;
                recycleVelocityTracker();
                if (seekListener != null && target != null && findHitTarget(event.getY()) != null) {
                    prepareSmoothSeekCenter();
                    seekListener.onSeekRequested(target.seekTimeMs);
                    performClick();
                    return true;
                }
                return super.onTouchEvent(event);
            }
            case MotionEvent.ACTION_CANCEL:
                pressedTarget = null;
                draggingLyrics = false;
                recycleVelocityTracker();
                if (manualScroller != null) {
                    manualScroller.abortAnimation();
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
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

    private void updateDisplayCenter(int activeIndex) {
        long now = SystemClock.uptimeMillis();
        if (manualScrollActive) {
            if (!draggingLyrics && manualScroller != null && manualScroller.computeScrollOffset()) {
                manualCenterIndex = clampCenterIndex(manualScroller.getCurrY() / Math.max(1f, manualScrollPixelsPerIndex));
                animatedCenterIndex = manualCenterIndex;
                centerInitialized = true;
                lastManualScrollUptimeMs = now;
                postInvalidateOnAnimation();
                return;
            }
            if (!draggingLyrics && now - lastManualScrollUptimeMs > MANUAL_SCROLL_HOLD_MS) {
                manualScrollActive = false;
                centerInitialized = false;
            } else {
                animatedCenterIndex = clampCenterIndex(manualCenterIndex);
                centerInitialized = true;
                return;
            }
        }
        updateAnimatedCenter(activeIndex);
    }

    private void startManualFling() {
        if (velocityTracker == null || manualScroller == null || Math.max(currentDisplayLineCount, lines.size()) <= 1) {
            scheduleReturnToPlayback();
            return;
        }
        velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
        float velocityY = velocityTracker.getYVelocity();
        if (Math.abs(velocityY) < minimumFlingVelocity) {
            scheduleReturnToPlayback();
            return;
        }

        manualScrollPixelsPerIndex = Math.max(1f, manualScrollPixelsPerIndex);
        int startY = Math.round(clampCenterIndex(manualCenterIndex) * manualScrollPixelsPerIndex);
        int maxY = Math.round((Math.max(currentDisplayLineCount, lines.size()) - 1f) * manualScrollPixelsPerIndex);
        manualScroller.fling(
                0,
                startY,
                0,
                Math.round(-velocityY),
                0,
                0,
                0,
                maxY
        );
        manualScrollActive = true;
        lastManualScrollUptimeMs = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
        scheduleReturnToPlayback();
    }

    private void recycleVelocityTracker() {
        if (velocityTracker == null) {
            return;
        }
        velocityTracker.recycle();
        velocityTracker = null;
    }

    private void scheduleReturnToPlayback() {
        postDelayed(() -> {
            if (!draggingLyrics
                    && manualScrollActive
                    && (manualScroller == null || manualScroller.isFinished())
                    && SystemClock.uptimeMillis() - lastManualScrollUptimeMs >= MANUAL_SCROLL_HOLD_MS) {
                manualScrollActive = false;
                centerInitialized = false;
                postInvalidateOnAnimation();
            }
        }, MANUAL_SCROLL_HOLD_MS + 80L);
    }

    private void updateAnimatedCenter(int activeIndex) {
        long now = System.currentTimeMillis();
        float distance = Math.abs(activeIndex - animatedCenterIndex);
        if (!centerInitialized || (!smoothSeekCenterActive && distance > 3.2f)) {
            animatedCenterIndex = activeIndex;
            centerInitialized = true;
            smoothSeekCenterActive = false;
            lastFrameMs = now;
            return;
        }

        long deltaMs = lastFrameMs == 0L ? 16L : Math.max(1L, Math.min(64L, now - lastFrameMs));
        lastFrameMs = now;
        float factor = 1f - (float) Math.exp(-deltaMs / (smoothSeekCenterActive ? 270f : 230f));
        float delta = activeIndex - animatedCenterIndex;
        float step = delta * factor;
        if (smoothSeekCenterActive) {
            float maxStep = Math.max(0.75f, Math.min(2.35f, Math.abs(delta) * 0.16f));
            if (Math.abs(step) > maxStep) {
                step = Math.signum(step) * maxStep;
            }
        }
        animatedCenterIndex += step;
        if (Math.abs(activeIndex - animatedCenterIndex) < 0.002f) {
            animatedCenterIndex = activeIndex;
            smoothSeekCenterActive = false;
        }
    }

    private void prepareSmoothSeekCenter() {
        smoothNextSeekCenter = true;
        smoothSeekCenterActive = centerInitialized;
        manualScrollActive = false;
        draggingLyrics = false;
        lastFrameMs = 0L;
        if (manualScroller != null) {
            manualScroller.abortAnimation();
        }
    }

    private void updateScrollPixelsPerIndex(List<LineLayout> layouts, float blockGap) {
        float total = 0f;
        int count = 0;
        for (int index = 0; index + 1 < layouts.size(); index++) {
            LineLayout current = layouts.get(index);
            LineLayout next = layouts.get(index + 1);
            if (next.index == current.index + 1) {
                total += distanceBetween(current, next, blockGap);
                count++;
            }
        }
        if (count > 0) {
            scrollPixelsPerIndex = Math.max(sp(44f), total / count);
        } else if (!layouts.isEmpty()) {
            scrollPixelsPerIndex = Math.max(sp(44f), layouts.get(0).height + blockGap);
        }
    }

    private void scheduleRowPrewarm() {
        removeCallbacks(rowPrewarmRunnable);
        if (lines.isEmpty()) {
            return;
        }
        postDelayed(rowPrewarmRunnable, 16L);
    }

    private void prewarmRowLayouts() {
        if (lines.isEmpty() || getWidth() <= 0) {
            return;
        }

        long start = SystemClock.uptimeMillis();
        float textSize = sp(typographyTextSizeSp(AiLyricsSettings.TYPO_LYRICS_ORIGINAL, MAIN_TEXT_SP));
        int warmed = 0;
        while (rowPrewarmIndex < lines.size()
                && warmed < 18
                && SystemClock.uptimeMillis() - start < 7L) {
            prewarmLineRows(rowPrewarmIndex, lines.get(rowPrewarmIndex), textSize);
            rowPrewarmIndex++;
            warmed++;
        }

        if (rowPrewarmIndex < lines.size()) {
            postDelayed(rowPrewarmRunnable, 16L);
        }
    }

    private void prewarmLineRows(int lineIndex, LyricsLine line, float textSize) {
        if (line == null) {
            return;
        }
        if (line.vocalParts != null && !line.vocalParts.isEmpty()) {
            List<LyricsLine.VocalPart> parts = orderVocalParts(line.vocalParts);
            for (int index = 0; index < parts.size(); index++) {
                LyricsLine.VocalPart part = parts.get(index);
                cachedRows(
                        "line:" + lineIndex + ":part:" + partKey(part, index) + typographyCacheKey(AiLyricsSettings.TYPO_LYRICS_ORIGINAL),
                        part.text,
                        japaneseFuriganaEnabled ? part.furiganaText : "",
                        part.syllables,
                        part.startTimeMs,
                        part.endTimeMs,
                        textSize
                );
            }
            return;
        }
        cachedRows("line:" + lineIndex + typographyCacheKey(AiLyricsSettings.TYPO_LYRICS_ORIGINAL), line.text, japaneseFuriganaEnabled ? line.furiganaText : "", line.syllables, line.startTimeMs, line.endTimeMs, textSize);
    }

    private boolean hasTimedKaraokeData(List<LyricsLine> lyricLines) {
        if (lyricLines == null || lyricLines.isEmpty()) {
            return false;
        }
        for (LyricsLine line : lyricLines) {
            if (line == null) {
                continue;
            }
            if (hasTimedSyllables(line.syllables)) {
                return true;
            }
            if (line.vocalParts == null) {
                continue;
            }
            for (LyricsLine.VocalPart part : line.vocalParts) {
                if (part != null && hasTimedSyllables(part.syllables)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasTimedSyllables(List<LyricsLine.Syllable> syllables) {
        if (syllables == null || syllables.isEmpty()) {
            return false;
        }
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable != null && syllable.endTimeMs > syllable.startTimeMs) {
                return true;
            }
        }
        return false;
    }

    private List<DrawGroup> buildLyricGroups(DisplayLine displayLine, boolean active, float distance) {
        if (displayLine == null) {
            return Collections.emptyList();
        }
        if (displayLine.isInterlude()) {
            return Collections.singletonList(buildInterludeGroup(displayLine.interludeInfo, active, distance));
        }

        int lineIndex = displayLine.sourceIndex;
        LyricsLine line = displayLine.line;
        List<DrawGroup> groups = new ArrayList<>();
        if (line.vocalParts != null && !line.vocalParts.isEmpty()) {
            groups.addAll(buildVocalGroups(lineIndex, line, active, distance));
            if (!hasVocalPartSupplements(line)) {
                addSupplementGroups(groups, lineIndex, line, active, distance);
            }
            return groups;
        }

        int inactiveColor = inactiveColorForSpeaker(line.speaker, distance);
        int activeColor = karaoke
                ? colorForSpeaker(line.speaker, "", normalActiveColor())
                : normalActiveColor();
        groups.add(buildGroup(
                line.text,
                japaneseFuriganaEnabled ? line.furiganaText : "",
                line.syllables,
                line.startTimeMs,
                line.endTimeMs,
                MAIN_TEXT_SP,
                inactiveColor,
                activeColor,
                line.kind,
                active,
                0,
                "line:" + lineIndex,
                "line:" + lineIndex,
                AiLyricsSettings.TYPO_LYRICS_ORIGINAL
        ));
        addSupplementGroups(groups, lineIndex, line, active, distance);
        return groups;
    }

    private DrawGroup buildInterludeGroup(InterludeInfo info, boolean active, float distance) {
        int inactiveColor = Color.argb(
                Math.max(52, Math.round(150f - Math.min(2.6f, distance) * 34f)),
                212,
                218,
                230
        );
        int activeColor = Color.rgb(245, 247, 252);
        return DrawGroup.interlude(
                sp(16f),
                inactiveColor,
                activeColor,
                active,
                info == null ? InterludeInfo.none() : info,
                typographyTypeface(AiLyricsSettings.TYPO_LYRICS_ORIGINAL)
        );
    }

    private List<DrawGroup> buildVocalGroups(int lineIndex, LyricsLine line, boolean active, float distance) {
        List<LyricsLine.VocalPart> parts = orderVocalParts(line.vocalParts);
        List<DrawGroup> groups = new ArrayList<>();
        for (int index = 0; index < parts.size(); index++) {
            LyricsLine.VocalPart part = parts.get(index);
            boolean partActive = active && positionMs >= part.startTimeMs;
            int inactiveColor = inactiveColorForSpeaker(part.speaker, distance + (partActive ? 0f : 0.45f));
            int activeColor = colorForSpeaker(part.speaker, part.role, normalActiveColor());
            groups.add(buildGroup(
                    part.text,
                    japaneseFuriganaEnabled ? part.furiganaText : "",
                    part.syllables,
                    part.startTimeMs,
                    part.endTimeMs,
                    MAIN_TEXT_SP,
                    inactiveColor,
                    activeColor,
                    part.kind,
                    partActive,
                    index,
                    "line:" + lineIndex + ":part:" + partKey(part, index),
                    "line:" + lineIndex + ":part:" + partKey(part, index),
                    AiLyricsSettings.TYPO_LYRICS_ORIGINAL
            ));
            addVocalPartSupplementGroups(groups, lineIndex, part, index, partActive, distance);
        }
        return groups;
    }

    private void addVocalPartSupplementGroups(
            List<DrawGroup> groups,
            int lineIndex,
            LyricsLine.VocalPart part,
            int partIndex,
            boolean active,
            float distance
    ) {
        if (part == null) {
            return;
        }
        String pronunciation = part.pronunciationText == null ? "" : part.pronunciationText.trim();
        String translation = part.translationText == null ? "" : part.translationText.trim();
        int activePronunciationColor = Color.argb(212, 255, 255, 255);
        int activeTranslationColor = Color.argb(184, 255, 255, 255);
        int inactiveAlpha = Math.max(34, Math.round(105f - Math.min(2.8f, distance) * 24f));
        int inactiveColor = Color.argb(inactiveAlpha, 210, 216, 226);
        String key = partKey(part, partIndex);
        if (!pronunciation.isEmpty()) {
            groups.add(buildSupplementGroup(
                    pronunciation,
                    active ? activePronunciationColor : inactiveColor,
                    lineIndex,
                    "part:" + key + ":pron",
                    groups.size(),
                    AiLyricsSettings.TYPO_LYRICS_PRONUNCIATION
            ));
        }
        if (!translation.isEmpty()) {
            groups.add(buildSupplementGroup(
                    translation,
                    active ? activeTranslationColor : inactiveColor,
                    lineIndex,
                    "part:" + key + ":trans",
                    groups.size(),
                    AiLyricsSettings.TYPO_LYRICS_TRANSLATION
            ));
        }
    }

    private boolean hasVocalPartSupplements(LyricsLine line) {
        if (line == null || line.vocalParts == null) {
            return false;
        }
        for (LyricsLine.VocalPart part : line.vocalParts) {
            if (part == null) {
                continue;
            }
            String pronunciation = part.pronunciationText == null ? "" : part.pronunciationText.trim();
            String translation = part.translationText == null ? "" : part.translationText.trim();
            if (!pronunciation.isEmpty() || !translation.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void addSupplementGroups(List<DrawGroup> groups, int lineIndex, LyricsLine line, boolean active, float distance) {
        if (line == null) {
            return;
        }
        String pronunciation = line.pronunciationText == null ? "" : line.pronunciationText.trim();
        String translation = line.translationText == null ? "" : line.translationText.trim();
        int activePronunciationColor = Color.argb(212, 255, 255, 255);
        int activeTranslationColor = Color.argb(184, 255, 255, 255);
        int inactiveAlpha = Math.max(34, Math.round(105f - Math.min(2.8f, distance) * 24f));
        int inactiveColor = Color.argb(inactiveAlpha, 210, 216, 226);
        if (!pronunciation.isEmpty()) {
            groups.add(buildSupplementGroup(
                    pronunciation,
                    active ? activePronunciationColor : inactiveColor,
                    lineIndex,
                    "pron",
                    groups.size(),
                    AiLyricsSettings.TYPO_LYRICS_PRONUNCIATION
            ));
        }
        if (!translation.isEmpty()) {
            groups.add(buildSupplementGroup(
                    translation,
                    active ? activeTranslationColor : inactiveColor,
                    lineIndex,
                    "trans",
                    groups.size(),
                    AiLyricsSettings.TYPO_LYRICS_TRANSLATION
            ));
        }
    }

    private DrawGroup buildSupplementGroup(String text, int color, int lineIndex, String type, int rowSeed, String typographySlotId) {
        return buildGroup(
                text,
                "",
                Collections.emptyList(),
                0L,
                0L,
                SUPPLEMENT_TEXT_SP,
                color,
                color,
                "vocal",
                false,
                rowSeed,
                "line:" + lineIndex + ":supp:" + type + ":" + text.hashCode(),
                "line:" + lineIndex + ":supp:" + type,
                typographySlotId
        );
    }

    private DrawGroup buildGroup(
            String text,
            String rubyText,
            List<LyricsLine.Syllable> syllables,
            long startTimeMs,
            long endTimeMs,
            float textSizeSp,
            int inactiveColor,
            int activeColor,
            String kind,
            boolean active,
            int rowSeed,
            String rowCacheKey,
            String bounceKeyPrefix,
            String typographySlotId
    ) {
        String slotId = AiLyricsSettings.typographySlotById(typographySlotId).id;
        float textSize = sp(typographyTextSizeSp(slotId, textSizeSp));
        List<TextRow> rows = cachedRows(rowCacheKey + typographyCacheKey(slotId), text, rubyText, syllables, startTimeMs, endTimeMs, textSize);
        return new DrawGroup(
                rows,
                textSize,
                inactiveColor,
                activeColor,
                normalizeKind(kind),
                active,
                rowSeed,
                bounceKeyPrefix,
                active ? findActiveSegmentIndex(rows) : -1,
                typographyTypeface(slotId),
                isSupplementTypographySlot(slotId)
        );
    }

    private float typographyTextSizeSp(String slotId, float baseSizeSp) {
        return Math.max(8f, baseSizeSp * typographyStyle(slotId).scale());
    }

    private Typeface typographyTypeface(String slotId) {
        return AppFonts.byWeight(getContext(), typographyStyle(slotId).weight);
    }

    private AiLyricsSettings.TypographyStyle typographyStyle(String slotId) {
        AiLyricsSettings.TypographySettings settings = typographySettings == null
                ? AiLyricsSettings.TypographySettings.defaults()
                : typographySettings;
        return settings.style(slotId);
    }

    private String typographyCacheKey(String slotId) {
        AiLyricsSettings.TypographySlot slot = AiLyricsSettings.typographySlotById(slotId);
        AiLyricsSettings.TypographyStyle style = typographyStyle(slot.id);
        return ":typo:" + slot.id + ":" + style.sizePercent + ":" + style.weight;
    }

    private boolean isSupplementTypographySlot(String slotId) {
        String normalized = AiLyricsSettings.typographySlotById(slotId).id;
        return AiLyricsSettings.TYPO_LYRICS_PRONUNCIATION.equals(normalized)
                || AiLyricsSettings.TYPO_LYRICS_TRANSLATION.equals(normalized);
    }

    private List<TextRow> cachedRows(
            String cacheKey,
            String text,
            String rubyText,
            List<LyricsLine.Syllable> syllables,
            long startTimeMs,
            long endTimeMs,
            float textSize
    ) {
        String key = cacheKey
                + ":w:" + Math.round(contentWidth())
                + ":s:" + Math.round(textSize)
                + ":ruby:" + (japaneseFuriganaEnabled ? (rubyText == null ? 0 : rubyText.hashCode()) : 0)
                + ":fake:" + syncedLyricsKaraokeAnimationEnabled;
        List<TextRow> cached = rowLayoutCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<TextRow> rows = wrapSegments(text, rubyText, syllables, startTimeMs, endTimeMs, textSize);
        rowLayoutCache.put(key, rows);
        return rows;
    }

    private String partKey(LyricsLine.VocalPart part, int index) {
        if (part != null && part.id != null && !part.id.trim().isEmpty()) {
            return part.id.trim();
        }
        return String.valueOf(index);
    }

    private void addHitTarget(LineLayout layout, float baselineCenter, float blockGap) {
        if (layout.displayLine == null || !layout.displayLine.isTimed()) {
            return;
        }
        float padding = Math.min(blockGap * 0.22f, sp(18f));
        hitTargets.add(new LineHitTarget(
                baselineCenter - layout.height * 0.5f - padding,
                baselineCenter + layout.height * 0.5f + padding,
                baselineCenter,
                layout.displayLine.seekTimeMs()
        ));
    }

    private LineHitTarget findHitTarget(float y) {
        LineHitTarget best = null;
        float bestDistance = Float.MAX_VALUE;
        for (LineHitTarget target : hitTargets) {
            if (y < target.top || y > target.bottom) {
                continue;
            }
            float distance = Math.abs(y - target.centerY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }
        return best;
    }

    private void drawGroups(Canvas canvas, List<DrawGroup> groups, float baselineCenter, float fadeAlpha) {
        float totalHeight = groupsHeight(groups);

        float top = baselineCenter - totalHeight * 0.5f;
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            DrawGroup group = groups.get(groupIndex);
            if (group.isInterlude()) {
                drawInterludeGroup(canvas, group, top + group.height() * 0.5f, fadeAlpha);
                top += group.height();
                if (groupIndex + 1 < groups.size()) {
                    top += sp(PART_GAP_SP);
                }
                continue;
            }
            float rowTop = top;
            for (int rowIndex = 0; rowIndex < group.rows.size(); rowIndex++) {
                TextRow row = group.rows.get(rowIndex);
                float baseline = rowTop + group.baselineOffset(row);
                drawTextRow(canvas, row, baseline, group, rowIndex, fadeAlpha);
                rowTop += group.rowHeight(row);
            }
            top = rowTop;
            if (groupIndex + 1 < groups.size()) {
                top += gapBetweenGroups(groups, groupIndex, groupIndex + 1);
            }
        }
    }

    private float groupsHeight(List<DrawGroup> groups) {
        float totalHeight = 0f;
        for (int index = 0; index < groups.size(); index++) {
            if (index > 0) {
                totalHeight += gapBetweenGroups(groups, index - 1, index);
            }
            DrawGroup group = groups.get(index);
            totalHeight += group.height();
        }
        return totalHeight;
    }

    private float gapBetweenGroups(List<DrawGroup> groups, int previousIndex, int nextIndex) {
        DrawGroup previous = groups.get(previousIndex);
        DrawGroup next = groups.get(nextIndex);
        float gap = previous.supplement || next.supplement ? sp(SUPPLEMENT_GAP_SP) : sp(PART_GAP_SP);
        if (isFuriganaMultiVocalBoundary(groups, nextIndex)) {
            gap = Math.max(gap, sp(FURIGANA_MULTI_VOCAL_GAP_SP));
        }
        return gap;
    }

    private boolean isFuriganaMultiVocalBoundary(List<DrawGroup> groups, int nextIndex) {
        if (!japaneseFuriganaEnabled || nextIndex <= 0 || nextIndex >= groups.size()) {
            return false;
        }
        DrawGroup next = groups.get(nextIndex);
        if (next.supplement || next.isInterlude() || !next.firstRowHasRuby()) {
            return false;
        }
        int primaryCount = 0;
        for (DrawGroup group : groups) {
            if (!group.supplement && !group.isInterlude()) {
                primaryCount++;
                if (primaryCount > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private void drawInterludeGroup(Canvas canvas, DrawGroup group, float centerY, float fadeAlpha) {
        InterludeInfo info = group.interludeInfo == null ? InterludeInfo.none() : group.interludeInfo;
        int color = scaleAlpha(group.active ? group.activeColor : group.inactiveColor, fadeAlpha);
        long now = SystemClock.uptimeMillis();
        float left = contentLeft();
        float barWidth = sp(3.2f);
        float gap = sp(3.8f);
        float minHeight = sp(7f);
        float maxHeight = sp(23f);
        float radius = barWidth * 0.7f;

        interludePaint.setShader(null);
        interludePaint.setStyle(Paint.Style.FILL);
        interludePaint.setColor(color);
        interludePaint.setAlpha(Color.alpha(color));

        for (int index = 0; index < 4; index++) {
            float phase = positiveSin(now + index * 145L, 980L);
            float height = minHeight + (maxHeight - minHeight) * (0.18f + phase * 0.82f);
            float x = left + index * (barWidth + gap);
            canvas.drawRoundRect(x, centerY - height * 0.5f, x + barWidth, centerY + height * 0.5f, radius, radius, interludePaint);
        }

        if (!interludeLabelsEnabled) {
            return;
        }

        float labelTextSize = sp(15f);
        configurePaint(color, "vocal", false, labelTextSize, false, group.typeface);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float baseline = centerY - (metrics.ascent + metrics.descent) * 0.5f;
        float labelLeft = left + 4f * barWidth + 3f * gap + sp(11f);
        canvas.drawText(interludeLabel(info.kind), labelLeft, baseline, textPaint);
        resetPaintEffects();
    }

    private String interludeLabel(String kind) {
        if ("prelude".equals(kind)) {
            return preludeLabel;
        }
        if ("postlude".equals(kind)) {
            return postludeLabel;
        }
        return breakLabel;
    }

    private LineLayout layoutAt(List<LineLayout> layouts, int index) {
        for (LineLayout layout : layouts) {
            if (layout.index == index) {
                return layout;
            }
        }
        return null;
    }

    private float offsetFromAnchor(List<LineLayout> layouts, int anchorIndex, int targetIndex, float blockGap) {
        if (targetIndex == anchorIndex) {
            return 0f;
        }
        float offset = 0f;
        if (targetIndex > anchorIndex) {
            for (int index = anchorIndex; index < targetIndex; index++) {
                LineLayout current = layoutAt(layouts, index);
                LineLayout next = layoutAt(layouts, index + 1);
                if (current == null || next == null) {
                    break;
                }
                offset += distanceBetween(current, next, blockGap);
            }
            return offset;
        }
        for (int index = anchorIndex; index > targetIndex; index--) {
            LineLayout current = layoutAt(layouts, index);
            LineLayout previous = layoutAt(layouts, index - 1);
            if (current == null || previous == null) {
                break;
            }
            offset -= distanceBetween(previous, current, blockGap);
        }
        return offset;
    }

    private float distanceBetween(LineLayout previous, LineLayout next, float blockGap) {
        return previous.height * 0.5f + blockGap + next.height * 0.5f;
    }

    private void drawTextRow(Canvas canvas, TextRow row, float baseline, DrawGroup group, int rowIndex, float fadeAlpha) {
        float left = contentLeft();
        int canvasSave = canvas.save();
        applyCanvasEffect(
                canvas,
                group.kind,
                group.active,
                left + contentWidth() * 0.5f,
                baseline,
                group.textSize,
                group.rowSeed + rowIndex
        );

        if (!group.active && !row.hasRuby()) {
            configurePaint(scaleAlpha(group.inactiveColor, fadeAlpha), group.kind, false, group.textSize, false, group.typeface);
            canvas.drawText(row.text, left, baseline, textPaint);
            canvas.restoreToCount(canvasSave);
            resetPaintEffects();
            return;
        }

        float cursor = left;
        for (int index = 0; index < row.segments.size(); index++) {
            TextSegment segment = row.segments.get(index);
            float offsetY = "wave".equals(group.kind)
                    ? baseWaveOffset(group.kind, group.rowSeed + rowIndex, index, group.textSize)
                    : 0f;
            KaraokeBounce bounce = karaokeBounce(segment, group);
            int segmentSave = canvas.save();
            if (bounce.active) {
                float pivotX = cursor + segment.width * 0.5f;
                float pivotY = baseline - group.textSize * 0.45f;
                canvas.translate(0f, bounce.offsetY);
                canvas.scale(bounce.scale, bounce.scale, pivotX, pivotY);
            }

            float fill = group.active ? segmentFillFraction(segment) : 0f;
            drawRubyText(canvas, segment, cursor, baseline + offsetY, group, fill, fadeAlpha);

            float textLeft = cursor + segment.textInset;
            configurePaint(scaleAlpha(group.inactiveColor, fadeAlpha), group.kind, group.active, group.textSize, false, group.typeface);
            canvas.drawText(segment.text, textLeft, baseline + offsetY, textPaint);

            if (fill > 0f) {
                drawActiveFill(canvas, segment, cursor, baseline, offsetY, group, fill, fadeAlpha);
            }
            canvas.restoreToCount(segmentSave);
            cursor += segment.width;
        }

        canvas.restoreToCount(canvasSave);
        resetPaintEffects();
    }

    private void drawRubyText(
            Canvas canvas,
            TextSegment segment,
            float cursor,
            float baseline,
            DrawGroup group,
            float fill,
            float fadeAlpha
    ) {
        if (segment.rubyText == null || segment.rubyText.trim().isEmpty() || group.supplement) {
            return;
        }
        float rubySize = Math.max(sp(9f), group.textSize * FURIGANA_TEXT_RATIO);
        int color = fill > 0f ? group.activeColor : group.inactiveColor;
        configurePaint(scaleAlpha(color, fadeAlpha * 0.84f), group.kind, false, rubySize, false, group.typeface);
        float rubyWidth = textPaint.measureText(segment.rubyText);
        float rubyLeft = cursor + segment.width * 0.5f - rubyWidth * 0.5f;
        float rubyBaseline = baseline - group.textSize * 0.90f;
        canvas.drawText(segment.rubyText, rubyLeft, rubyBaseline, textPaint);
    }

    private void drawActiveFill(
            Canvas canvas,
            TextSegment segment,
            float cursor,
            float baseline,
            float offsetY,
            DrawGroup group,
            float fill,
            float fadeAlpha
    ) {
        float safeFill = clamp(fill);
        float textLeft = cursor + segment.textInset;
        float textRight = textLeft + segment.textWidth;
        float fillRight = textLeft + segment.textWidth * safeFill;
        float top = baseline - group.textSize * 1.28f;
        float bottom = baseline + group.textSize * 0.48f;
        float softWidth = Math.min(sp(7f), Math.max(0f, segment.textWidth * 0.30f));
        int activeColor = scaleAlpha(group.activeColor, fadeAlpha);
        float clipRight = safeFill >= 0.995f
                ? textRight
                : Math.min(textRight, fillRight + softWidth);

        int clipSave = canvas.save();
        canvas.clipRect(textLeft, top, clipRight, bottom);
        configurePaint(activeColor, group.kind, group.active, group.textSize, true, group.typeface);

        if (safeFill < 0.995f && softWidth > 1f && clipRight > textLeft) {
            float softStart = Math.max(textLeft, fillRight - softWidth * 0.42f);
            float softEnd = Math.max(softStart + 1f, Math.min(textRight, fillRight + softWidth));
            textPaint.setShader(new LinearGradient(
                    softStart,
                    0f,
                    softEnd,
                    0f,
                    new int[]{
                            activeColor,
                            activeColor,
                            withAlpha(activeColor, 0)
                    },
                    new float[]{0f, 0.34f, 1f},
                    Shader.TileMode.CLAMP
            ));
        }

        canvas.drawText(segment.text, textLeft, baseline + offsetY, textPaint);
        textPaint.setShader(null);
        canvas.restoreToCount(clipSave);
    }

    private List<TextRow> wrapSegments(
            String text,
            String rubyText,
            List<LyricsLine.Syllable> syllables,
            long startTimeMs,
            long endTimeMs,
            float textSize
    ) {
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(lyricTypeface);

        List<TextSegment> segments = buildSegments(text, rubyText, syllables, startTimeMs, endTimeMs);
        if (segments.isEmpty()) {
            return Collections.singletonList(new TextRow(Collections.singletonList(
                    new TextSegment("", 0f, 0f, 0L, 0L, 0, 1, "")
            )));
        }

        float maxWidth = contentWidth();
        List<TextRow> rows = shouldWrapByWords(segments)
                ? wrapWordUnits(buildWordWrapUnits(segments), maxWidth)
                : wrapIndividualSegments(segments, maxWidth);
        return rows.isEmpty() ? Collections.singletonList(new TextRow(segments)) : rows;
    }

    private List<TextRow> wrapIndividualSegments(List<TextSegment> segments, float maxWidth) {
        List<TextRow> rows = new ArrayList<>();
        List<TextSegment> current = new ArrayList<>();
        float currentWidth = 0f;

        for (TextSegment segment : segments) {
            for (TextSegment piece : splitSegmentForWrap(segment, maxWidth)) {
                if (currentWidth > 0f && currentWidth + piece.width > maxWidth) {
                    addTrimmedRow(rows, current);
                    current = new ArrayList<>();
                    currentWidth = 0f;
                }
                if (current.isEmpty() && isWhitespace(piece.text)) {
                    continue;
                }
                current.add(piece);
                currentWidth += piece.width;
            }
        }

        addTrimmedRow(rows, current);
        return rows;
    }

    private List<TextSegment> splitSegmentForWrap(TextSegment segment, float maxWidth) {
        if (segment == null) {
            return Collections.emptyList();
        }
        if (maxWidth <= 0f || segment.width <= maxWidth || isWhitespace(segment.text)) {
            return Collections.singletonList(segment);
        }

        List<String> chars = splitChars(segment.text);
        if (chars.size() <= 1) {
            return Collections.singletonList(segment);
        }

        List<TextSegment> pieces = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int currentStart = 0;
        for (int index = 0; index < chars.size(); index++) {
            String value = chars.get(index);
            List<String> next = new ArrayList<>(current);
            next.add(value);
            TextSegment nextSegment = createSplitSegment(segment, next, currentStart);
            if (!current.isEmpty() && nextSegment.width > maxWidth) {
                pieces.add(createSplitSegment(segment, current, currentStart));
                current = new ArrayList<>();
                currentStart = index;
            }
            current.add(value);
        }

        if (!current.isEmpty()) {
            pieces.add(createSplitSegment(segment, current, currentStart));
        }
        return pieces.isEmpty() ? Collections.singletonList(segment) : pieces;
    }

    private TextSegment createSplitSegment(TextSegment source, List<String> chars, int sourceOffset) {
        StringBuilder builder = new StringBuilder();
        for (String value : chars) {
            builder.append(value);
        }

        int length = Math.max(1, chars.size());
        int totalLength = Math.max(length, source.sourceLength);
        int safeOffset = Math.max(0, Math.min(sourceOffset, totalLength));
        int safeEnd = Math.min(totalLength, safeOffset + length);
        long duration = Math.max(0L, source.endTimeMs - source.startTimeMs);
        long start = duration <= 0L
                ? source.startTimeMs
                : source.startTimeMs + Math.round(duration * (safeOffset / (float) totalLength));
        long end = duration <= 0L
                ? source.endTimeMs
                : source.startTimeMs + Math.round(duration * (Math.min(totalLength, safeEnd) / (float) totalLength));
        return createMeasuredSegment(
                builder.toString(),
                start,
                Math.max(start, end),
                source.sourceIndex,
                length,
                rubyForSplitSegment(source, safeOffset, length)
        );
    }

    private String rubyForSplitSegment(TextSegment source, int start, int length) {
        if (source == null || source.rubyText == null || source.rubyText.trim().isEmpty() || length <= 0) {
            return "";
        }
        if (start <= 0 && length >= source.sourceLength) {
            return source.rubyText;
        }

        List<String> rubyChars = splitChars(source.rubyText.replace(" ", ""));
        if (rubyChars.isEmpty()) {
            return source.rubyText;
        }

        int sourceLength = Math.max(1, source.sourceLength);
        int readStart = Math.min(rubyChars.size(), Math.round(rubyChars.size() * (start / (float) sourceLength)));
        int readEnd = start + length >= sourceLength
                ? rubyChars.size()
                : Math.min(rubyChars.size(), Math.round(rubyChars.size() * ((start + length) / (float) sourceLength)));
        if (readEnd <= readStart) {
            readEnd = Math.min(rubyChars.size(), readStart + 1);
        }

        StringBuilder builder = new StringBuilder();
        for (int index = readStart; index < readEnd; index++) {
            builder.append(rubyChars.get(index));
        }
        return builder.toString();
    }

    private List<TextRow> wrapWordUnits(List<WrapUnit> units, float maxWidth) {
        List<TextRow> rows = new ArrayList<>();
        List<TextSegment> current = new ArrayList<>();
        float currentWidth = 0f;

        for (WrapUnit unit : units) {
            float unitVisibleWidth = visibleWidth(unit.segments);
            if (unitVisibleWidth <= 0f) {
                continue;
            }

            if (unitVisibleWidth > maxWidth) {
                addTrimmedRow(rows, current);
                current = new ArrayList<>();
                currentWidth = 0f;
                rows.addAll(wrapIndividualSegments(trimWhitespaceSegments(unit.segments), maxWidth));
                continue;
            }

            if (currentWidth > 0f && currentWidth + unitVisibleWidth > maxWidth) {
                addTrimmedRow(rows, current);
                current = new ArrayList<>();
                currentWidth = 0f;
            }

            current.addAll(unit.segments);
            currentWidth += unit.width;
        }

        addTrimmedRow(rows, current);
        return rows;
    }

    private boolean shouldWrapByWords(List<TextSegment> segments) {
        boolean seenWord = false;
        boolean seenSeparatorAfterWord = false;
        for (TextSegment segment : segments) {
            if (isWhitespace(segment.text)) {
                if (seenWord) {
                    seenSeparatorAfterWord = true;
                }
            } else {
                if (seenSeparatorAfterWord) {
                    return true;
                }
                seenWord = true;
            }
        }
        return false;
    }

    private List<WrapUnit> buildWordWrapUnits(List<TextSegment> segments) {
        List<WrapUnit> units = new ArrayList<>();
        List<TextSegment> current = new ArrayList<>();
        float currentWidth = 0f;
        boolean currentHasWord = false;

        for (TextSegment segment : segments) {
            boolean whitespace = isWhitespace(segment.text);
            if (whitespace && !currentHasWord) {
                continue;
            }
            if (!whitespace && currentHasWord && endsWithWhitespace(current)) {
                units.add(new WrapUnit(current, currentWidth));
                current = new ArrayList<>();
                currentWidth = 0f;
                currentHasWord = false;
            }
            current.add(segment);
            currentWidth += segment.width;
            if (!whitespace) {
                currentHasWord = true;
            }
        }

        if (!current.isEmpty()) {
            units.add(new WrapUnit(current, currentWidth));
        }
        return units;
    }

    private void addTrimmedRow(List<TextRow> rows, List<TextSegment> segments) {
        List<TextSegment> trimmed = trimWhitespaceSegments(segments);
        if (!trimmed.isEmpty()) {
            rows.add(new TextRow(trimmed));
        }
    }

    private List<TextSegment> trimWhitespaceSegments(List<TextSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }
        int start = 0;
        int end = segments.size();
        while (start < end && isWhitespace(segments.get(start).text)) {
            start++;
        }
        while (end > start && isWhitespace(segments.get(end - 1).text)) {
            end--;
        }
        if (start >= end) {
            return Collections.emptyList();
        }
        return new ArrayList<>(segments.subList(start, end));
    }

    private float visibleWidth(List<TextSegment> segments) {
        float width = 0f;
        for (TextSegment segment : trimWhitespaceSegments(segments)) {
            width += segment.width;
        }
        return width;
    }

    private boolean endsWithWhitespace(List<TextSegment> segments) {
        return segments != null && !segments.isEmpty() && isWhitespace(segments.get(segments.size() - 1).text);
    }

    private List<TextSegment> buildSegments(
            String text,
            String rubyText,
            List<LyricsLine.Syllable> syllables,
            long startTimeMs,
            long endTimeMs
    ) {
        List<TextSegment> segments = new ArrayList<>();
        List<RubyAnnotation> rubyAnnotations = parseRubyAnnotations(text, rubyText);
        if (syllables != null && !syllables.isEmpty()) {
            int charOffset = 0;
            for (int index = 0; index < syllables.size(); index++) {
                LyricsLine.Syllable syllable = syllables.get(index);
                String value = syllable.text == null ? "" : syllable.text;
                int charLength = Math.max(1, value.codePointCount(0, value.length()));
                segments.add(createMeasuredSegment(
                        value,
                        syllable.startTimeMs,
                        syllable.endTimeMs,
                        index,
                        charLength,
                        rubyForRange(rubyAnnotations, charOffset, charLength)
                ));
                charOffset += charLength;
            }
            return segments;
        }

        if (!syncedLyricsKaraokeAnimationEnabled) {
            String value = text == null ? "" : text;
            return Collections.singletonList(createMeasuredSegment(
                    value,
                    0L,
                    0L,
                    0,
                    Math.max(1, value.codePointCount(0, value.length())),
                    rubyForRange(rubyAnnotations, 0, Math.max(1, value.codePointCount(0, value.length())))
            ));
        }

        List<String> chars = splitChars(text);
        long duration = Math.max(0L, endTimeMs - startTimeMs);
        for (int index = 0; index < chars.size(); index++) {
            String value = chars.get(index);
            long start = duration <= 0L ? 0L : startTimeMs + Math.round(duration * (index / (float) Math.max(1, chars.size())));
            long end = duration <= 0L ? 0L : startTimeMs + Math.round(duration * ((index + 1) / (float) Math.max(1, chars.size())));
            segments.add(createMeasuredSegment(
                    value,
                    start,
                    end,
                    index,
                    1,
                    rubyForRange(rubyAnnotations, index, 1)
            ));
        }
        return segments;
    }

    private TextSegment createMeasuredSegment(
            String text,
            long startTimeMs,
            long endTimeMs,
            int sourceIndex,
            int sourceLength,
            String rubyText
    ) {
        String safeText = text == null ? "" : text;
        float textWidth = textPaint.measureText(safeText);
        float width = rubyAwareSegmentWidth(textWidth, rubyText);
        return new TextSegment(safeText, textWidth, width, startTimeMs, endTimeMs, sourceIndex, sourceLength, rubyText);
    }

    private float rubyAwareSegmentWidth(float textWidth, String rubyText) {
        String ruby = rubyText == null ? "" : rubyText.trim();
        if (ruby.isEmpty()) {
            return textWidth;
        }
        float previousSize = textPaint.getTextSize();
        Typeface previousTypeface = textPaint.getTypeface();
        textPaint.setTextSize(Math.max(sp(9f), previousSize * FURIGANA_TEXT_RATIO));
        textPaint.setTypeface(previousTypeface);
        float rubyWidth = textPaint.measureText(ruby);
        textPaint.setTextSize(previousSize);
        textPaint.setTypeface(previousTypeface);
        return Math.max(textWidth, rubyWidth + sp(2.5f));
    }

    private List<RubyAnnotation> parseRubyAnnotations(String text, String rubyText) {
        if (!japaneseFuriganaEnabled || text == null || text.isEmpty() || rubyText == null || !rubyText.contains("<ruby>")) {
            return Collections.emptyList();
        }
        List<RubyAnnotation> annotations = new ArrayList<>();
        int currentChar = 0;
        int cursor = 0;
        while (cursor < rubyText.length()) {
            int rubyStart = rubyText.indexOf("<ruby>", cursor);
            if (rubyStart < 0) {
                currentChar += codePointCount(rubyText.substring(cursor).replaceAll("<[^>]+>", ""));
                break;
            }
            String before = rubyText.substring(cursor, rubyStart);
            currentChar += codePointCount(before.replaceAll("<[^>]+>", ""));

            int baseStart = rubyStart + "<ruby>".length();
            int rtStart = rubyText.indexOf("<rt>", baseStart);
            int rtEnd = rtStart < 0 ? -1 : rubyText.indexOf("</rt>", rtStart);
            int rubyEnd = rtEnd < 0 ? -1 : rubyText.indexOf("</ruby>", rtEnd);
            if (rtStart < 0 || rtEnd < 0 || rubyEnd < 0) {
                return Collections.emptyList();
            }

            String base = rubyText.substring(baseStart, rtStart);
            String reading = rubyText.substring(rtStart + "<rt>".length(), rtEnd).trim();
            int length = codePointCount(base);
            if (length > 0 && !reading.isEmpty()) {
                annotations.add(new RubyAnnotation(currentChar, length, reading));
            }
            currentChar += length;
            cursor = rubyEnd + "</ruby>".length();
        }

        String plain = plainRubyText(rubyText);
        if (!plain.equals(text)) {
            return Collections.emptyList();
        }
        return annotations;
    }

    private String rubyForRange(List<RubyAnnotation> annotations, int start, int length) {
        if (annotations == null || annotations.isEmpty() || length <= 0) {
            return "";
        }
        int end = start + length;
        StringBuilder builder = new StringBuilder();
        for (RubyAnnotation annotation : annotations) {
            if (annotation.start >= end || annotation.end() <= start) {
                continue;
            }
            int overlapStart = Math.max(start, annotation.start);
            int overlapEnd = Math.min(end, annotation.end());
            String reading = annotation.readingForRange(overlapStart, overlapEnd);
            if (reading.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(reading);
        }
        return builder.toString();
    }

    private static String plainRubyText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int cursor = 0;
        while (cursor < value.length()) {
            int rubyStart = value.indexOf("<ruby>", cursor);
            if (rubyStart < 0) {
                builder.append(value.substring(cursor).replaceAll("<[^>]+>", ""));
                break;
            }
            builder.append(value.substring(cursor, rubyStart).replaceAll("<[^>]+>", ""));
            int baseStart = rubyStart + "<ruby>".length();
            int rtStart = value.indexOf("<rt>", baseStart);
            int rtEnd = rtStart < 0 ? -1 : value.indexOf("</rt>", rtStart);
            int rubyEnd = rtEnd < 0 ? -1 : value.indexOf("</ruby>", rtEnd);
            if (rtStart < 0 || rtEnd < 0 || rubyEnd < 0) {
                return value.replaceAll("<[^>]+>", "");
            }
            builder.append(value, baseStart, rtStart);
            cursor = rubyEnd + "</ruby>".length();
        }
        return builder.toString();
    }

    private static int codePointCount(String value) {
        return value == null || value.isEmpty() ? 0 : value.codePointCount(0, value.length());
    }

    private List<LyricsLine.VocalPart> orderVocalParts(List<LyricsLine.VocalPart> parts) {
        List<LyricsLine.VocalPart> ordered = new ArrayList<>();
        for (LyricsLine.VocalPart part : parts) {
            if ("lead".equals(part.role)) {
                ordered.add(part);
            }
        }
        for (LyricsLine.VocalPart part : parts) {
            if (!"lead".equals(part.role)) {
                ordered.add(part);
            }
        }
        return ordered;
    }

    private void drawBackground(Canvas canvas) {
        backgroundPaint.setShader(null);
    }

    private RectF edgeFadeBounds(float left, float top, float right, float bottom) {
        edgeFadeBounds.set(left, top, right, bottom);
        return edgeFadeBounds;
    }

    private void applyBottomEdgeFade(Canvas canvas) {
        float fadeHeight = Math.min(dp(BOTTOM_EDGE_FADE_DP), getHeight() * 0.12f);
        if (fadeHeight <= 1f) {
            return;
        }
        float top = getHeight() - fadeHeight;
        edgeFadePaint.setShader(new LinearGradient(
                0f,
                top,
                0f,
                getHeight(),
                Color.BLACK,
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));
        edgeFadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        canvas.drawRect(0f, top, getWidth(), getHeight(), edgeFadePaint);
        edgeFadePaint.setXfermode(null);
        edgeFadePaint.setShader(null);
    }

    private float topFadeAlpha(float baselineCenter, float layoutHeight) {
        float fadeHeight = Math.min(getHeight() * 0.28f, sp(150f));
        if (fadeHeight <= 1f) {
            return 1f;
        }
        float fadeY = baselineCenter - layoutHeight * 0.35f;
        return clamp(fadeY / fadeHeight);
    }

    private void drawEmpty(Canvas canvas) {
        if (isLoadingEmptyMessage()) {
            drawLoadingSkeleton(canvas);
            postInvalidateOnAnimation();
            return;
        }
        textPaint.setTextSize(sp(EMPTY_TEXT_SP));
        textPaint.setTypeface(lyricTypeface);
        configurePaint(Color.rgb(174, 181, 195), "vocal", false, sp(EMPTY_TEXT_SP), false);
        canvas.drawText(emptyMessage, contentLeft(), getHeight() * 0.5f, textPaint);
        resetPaintEffects();
    }

    private void drawLoadingSkeleton(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float left = contentLeft();
        float availableWidth = contentWidth();
        float centerY = getHeight() * verticalCenterBias;
        float[] widths = {0.62f, 0.86f, 0.74f, 0.92f, 0.56f};
        float rowHeight = sp(16f);
        float activeHeight = sp(25f);
        float rowGap = sp(20f);
        float totalHeight = rowHeight * 4f + activeHeight + rowGap * 4f;
        float top = centerY - totalHeight * 0.5f;

        for (int index = 0; index < widths.length; index++) {
            boolean active = index == 2;
            float height = active ? activeHeight : rowHeight;
            float width = Math.max(sp(86f), availableWidth * widths[index]);
            float rowTop = top;
            float radius = height * 0.45f;
            int baseAlpha = active ? 82 : 36;

            skeletonPaint.setShader(null);
            skeletonPaint.setStyle(Paint.Style.FILL);
            skeletonPaint.setColor(Color.argb(baseAlpha, 255, 255, 255));
            canvas.drawRoundRect(left, rowTop, left + width, rowTop + height, radius, radius, skeletonPaint);

            float shimmerWidth = Math.max(sp(48f), width * 0.34f);
            float phase = ((now + index * 130L) % 1350L) / 1350f;
            float shimmerLeft = left - shimmerWidth + (width + shimmerWidth * 2f) * phase;
            skeletonPaint.setShader(new LinearGradient(
                    shimmerLeft,
                    0f,
                    shimmerLeft + shimmerWidth,
                    0f,
                    new int[] {
                            Color.argb(0, 255, 255, 255),
                            Color.argb(active ? 118 : 78, 255, 255, 255),
                            Color.argb(0, 255, 255, 255)
                    },
                    new float[] {0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP
            ));
            int save = canvas.save();
            canvas.clipRect(left, rowTop, left + width, rowTop + height);
            canvas.drawRoundRect(left, rowTop, left + width, rowTop + height, radius, radius, skeletonPaint);
            canvas.restoreToCount(save);

            top += height + rowGap;
        }
        skeletonPaint.setShader(null);
    }

    private boolean isLoadingEmptyMessage() {
        String value = emptyMessage == null ? "" : emptyMessage.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return false;
        }
        if (loadingMessage != null && value.equals(loadingMessage.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (value.contains("없") || value.contains("실패") || value.contains("연주곡") || value.contains("instrumental")) {
            return false;
        }
        return value.contains("loading lyrics")
                || value.contains("lyrics loading")
                || value.contains("가사 불러")
                || value.contains("가사 찾는")
                || (value.contains("가사") && value.contains("기다"));
    }

    private float segmentFillFraction(TextSegment segment) {
        if (positionMs >= segment.endTimeMs) {
            return 1f;
        }
        if (positionMs <= segment.startTimeMs || segment.endTimeMs <= segment.startTimeMs) {
            return 0f;
        }
        return clamp((positionMs - segment.startTimeMs) / (float) (segment.endTimeMs - segment.startTimeMs));
    }

    private float baseWaveOffset(String kind, int rowIndex, int segmentIndex, float textSize) {
        long now = System.currentTimeMillis();
        float phase = ((now + rowIndex * 95L + segmentIndex * 62L) % (long) WAVE_PERIOD_MS) / WAVE_PERIOD_MS;
        float wave = (float) Math.sin(phase * Math.PI * 2.0);
        float amplitude = "wave".equals(kind) ? 0.145f : 0.085f;
        float bounce = positiveSin(now + segmentIndex * 42L, 760L) * textSize * 0.018f;
        return wave * textSize * amplitude - bounce;
    }

    private void applyCanvasEffect(
            Canvas canvas,
            String kind,
            boolean animate,
            float centerX,
            float y,
            float textSize,
            int rowIndex
    ) {
        if (!animate) {
            return;
        }

        long now = System.currentTimeMillis() + rowIndex * 73L;
        switch (kind) {
            case "effect": {
                int step = (int) ((now / 45L) % 4L);
                float density = getResources().getDisplayMetrics().density;
                float dx = new float[]{0f, -0.5f, 0.45f, -0.25f}[step] * density;
                float dy = new float[]{0f, 0.25f, -0.25f, -0.35f}[step] * density;
                canvas.translate(dx, dy);
                break;
            }
            case "adlib":
                canvas.translate(0f, sin(now, 1050L) * -sp(1.5f));
                break;
            case "pulse": {
                float scale = 1f + positiveSin(now, 940L) * 0.025f;
                canvas.scale(scale, scale, centerX, y - textSize * 0.45f);
                break;
            }
            case "bounce":
                canvas.translate(0f, -positiveSin(now, 780L) * textSize * 0.12f);
                break;
            case "sway":
                canvas.rotate(sin(now, 1350L) * 0.84f, centerX, y);
                canvas.translate(sin(now, 1350L) * textSize * 0.0245f, 0f);
                break;
            case "float":
                canvas.rotate(sin(now, 1650L) * 0.45f, centerX, y);
                canvas.translate(0f, -positiveSin(now, 1650L) * textSize * 0.09f);
                break;
            case "pop": {
                float phase = (now % 1080L) / 1080f;
                float scale = phase < 0.18f ? 1.035f : (phase < 0.34f ? 0.996f : 1f);
                canvas.scale(scale, scale, centerX, y - textSize * 0.45f);
                break;
            }
            case "glitch": {
                int step = (int) ((now / 35L) % 32L);
                if (step == 5 || step == 19) {
                    canvas.translate(textSize * 0.035f, -textSize * 0.01f);
                } else if (step == 6 || step == 20) {
                    canvas.translate(-textSize * 0.035f, textSize * 0.01f);
                }
                break;
            }
            default:
                break;
        }
    }

    private void configurePaint(int color, String kind, boolean animate, float textSize, boolean activeFill) {
        configurePaint(color, kind, animate, textSize, activeFill, lyricTypeface);
    }

    private void configurePaint(int color, String kind, boolean animate, float textSize, boolean activeFill, Typeface typeface) {
        resetPaintEffects();
        textPaint.setTypeface(typeface == null ? lyricTypeface : typeface);
        textPaint.setTextSize(textSize);
        textPaint.setColor(color);
        textPaint.setAlpha(Color.alpha(color));
        if (!animate) {
            return;
        }

        long now = System.currentTimeMillis();
        int alpha = Color.alpha(color);
        switch (kind) {
            case "sparkle": {
                float glow = positiveSin(now, 1180L);
                textPaint.setShadowLayer(textSize * (0.07f + glow * 0.18f), 0f, 0f, withAlpha(activeFill ? color : Color.WHITE, 70 + Math.round(glow * 90f)));
                break;
            }
            case "echo": {
                textPaint.setShadowLayer(textSize * 0.12f, textSize * 0.06f, textSize * 0.035f, withAlpha(color, 78));
                break;
            }
            case "whisper": {
                float amount = positiveSin(now, 1450L);
                textPaint.setAlpha(Math.round(alpha * (0.76f + (1f - amount) * 0.12f)));
                break;
            }
            case "glow": {
                float glow = 0.55f + positiveSin(now, 2800L) * 0.30f;
                textPaint.setShadowLayer(textSize * (0.14f + glow * 0.14f), 0f, 0f, withAlpha(color, 105));
                break;
            }
            case "blur": {
                float blur = 0.30f + positiveSin(now, 1500L) * 0.35f;
                textPaint.setAlpha(Math.round(alpha * (0.9f + (1f - blur) * 0.08f)));
                textPaint.setShadowLayer(textSize * blur * 0.055f, 0f, 0f, withAlpha(color, 70));
                break;
            }
            case "flicker": {
                float phase = (now % 1220L) / 1220f;
                float factor = (phase > 0.12f && phase < 0.15f) || (phase > 0.52f && phase < 0.56f) ? 0.78f : 1f;
                textPaint.setAlpha(Math.round(alpha * factor));
                break;
            }
            case "glitch": {
                textPaint.setShadowLayer(0f, textSize * 0.04f, 0f, Color.argb(78, 111, 211, 255));
                break;
            }
            default:
                break;
        }
    }

    private void resetPaintEffects() {
        textPaint.setShader(null);
        textPaint.clearShadowLayer();
    }

    private List<DisplayLine> buildDisplayLines() {
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }

        List<DisplayLine> displayLines = new ArrayList<>(lines.size() + 1);
        int lineCount = lines.size();
        for (int index = 0; index < lineCount; index++) {
            LyricsLine line = lines.get(index);
            InterludeInfo lineInterlude = interludeInfoForLine(line, index, lineCount);
            boolean markerInterlude = lineInterlude.isInterlude;
            if (!markerInterlude || (isPositionInside(lineInterlude) && !hasVisibleInterludeOverlap(displayLines, lineInterlude))) {
                displayLines.add(DisplayLine.real(line, index, displayLines.size(), markerInterlude ? lineInterlude : InterludeInfo.none()));
            }

            InterludeInfo trailingInterlude = trailingInterludeInfo(line, index, lineCount);
            if (trailingInterlude.isInterlude
                    && isPositionInside(trailingInterlude)
                    && !hasVisibleInterludeOverlap(displayLines, trailingInterlude)) {
                displayLines.add(DisplayLine.virtual(index, displayLines.size(), trailingInterlude));
            }
        }

        if (displayLines.isEmpty()) {
            LyricsLine line = lines.get(0);
            displayLines.add(DisplayLine.real(line, 0, 0, interludeInfoForLine(line, 0, lineCount)));
        }
        return displayLines;
    }

    private boolean hasVisibleInterludeOverlap(List<DisplayLine> displayLines, InterludeInfo info) {
        if (displayLines == null || displayLines.isEmpty() || info == null || !info.isInterlude) {
            return false;
        }
        for (DisplayLine displayLine : displayLines) {
            if (displayLine != null
                    && displayLine.isInterlude()
                    && interludesOverlap(displayLine.interludeInfo, info)) {
                return true;
            }
        }
        return false;
    }

    private boolean interludesOverlap(InterludeInfo first, InterludeInfo second) {
        return first != null
                && second != null
                && first.isInterlude
                && second.isInterlude
                && first.startTimeMs < second.endTimeMs
                && second.startTimeMs < first.endTimeMs;
    }

    private int findActiveDisplayIndex(List<DisplayLine> displayLines) {
        if (displayLines == null || displayLines.isEmpty()) {
            return 0;
        }

        int fallback = 0;
        for (int index = 0; index < displayLines.size(); index++) {
            DisplayLine displayLine = displayLines.get(index);
            if (!displayLine.isTimed()) {
                return Math.min(index, displayLines.size() - 1);
            }
            if (positionMs >= displayLine.startTimeMs() && positionMs < displayLine.endTimeMs()) {
                return index;
            }
            if (positionMs >= displayLine.startTimeMs()) {
                fallback = index;
            }
        }
        return fallback;
    }

    private InterludeInfo interludeInfoForLine(LyricsLine line, int lineIndex, int lineCount) {
        if (line == null || !line.isTimed() || !isInterludeMarkerText(interludeCandidateText(line))) {
            return InterludeInfo.none();
        }
        long endTimeMs = Math.max(line.endTimeMs, nextRenderableLineStartAfter(lineIndex));
        long durationMs = endTimeMs > line.startTimeMs ? endTimeMs - line.startTimeMs : 0L;
        if (durationMs <= INTERLUDE_MIN_DURATION_MS) {
            return InterludeInfo.none();
        }
        return new InterludeInfo(true, line.startTimeMs, endTimeMs, instrumentalKind(lineIndex, lineCount), false);
    }

    private InterludeInfo trailingInterludeInfo(LyricsLine line, int lineIndex, int lineCount) {
        if (!autoInstrumentalBreakEnabled || line == null || !line.isTimed() || isInterludeMarkerText(interludeCandidateText(line))) {
            return InterludeInfo.none();
        }

        long lyricEndTime = lastLyricEndTime(line);
        if (lyricEndTime < 0L) {
            return InterludeInfo.none();
        }

        long startTime = lyricEndTime + KARAOKE_TRAILING_INTERLUDE_DELAY_MS;
        long nextLyricStartTime = nextRenderableLineStartAfter(lineIndex);
        if (hasRenderableInterludeMarkerBeforeNextRenderableLine(lineIndex, lineCount)) {
            return InterludeInfo.none();
        }
        long endTime = nextLyricStartTime > startTime
                ? nextLyricStartTime
                : (lineIndex >= Math.max(0, lineCount - 1) ? trackDurationMs : 0L);
        long durationMs = endTime > startTime ? endTime - startTime : 0L;
        if (durationMs <= INTERLUDE_MIN_DURATION_MS) {
            return InterludeInfo.none();
        }
        return new InterludeInfo(true, startTime, endTime, nextLyricStartTime > 0L ? "break" : "postlude", true);
    }

    private long nextRenderableLineStartAfter(int lineIndex) {
        for (int index = Math.max(0, lineIndex + 1); index < lines.size(); index++) {
            LyricsLine candidate = lines.get(index);
            if (candidate == null || !candidate.isTimed()) {
                continue;
            }
            if (isInterludeMarkerText(interludeCandidateText(candidate))) {
                continue;
            }
            return candidate.startTimeMs;
        }
        return 0L;
    }

    private boolean hasRenderableInterludeMarkerBeforeNextRenderableLine(int lineIndex, int lineCount) {
        for (int index = Math.max(0, lineIndex + 1); index < lines.size(); index++) {
            LyricsLine candidate = lines.get(index);
            if (candidate == null || !candidate.isTimed()) {
                continue;
            }
            if (!isInterludeMarkerText(interludeCandidateText(candidate))) {
                return false;
            }
            if (interludeInfoForLine(candidate, index, lineCount).isInterlude) {
                return true;
            }
        }
        return false;
    }

    private long lastLyricEndTime(LyricsLine line) {
        if (line == null) {
            return -1L;
        }

        long lastEnd = maxSyllableEnd(line.syllables, line.endTimeMs);
        if (line.vocalParts != null) {
            for (LyricsLine.VocalPart part : line.vocalParts) {
                lastEnd = Math.max(lastEnd, maxSyllableEnd(part.syllables, line.endTimeMs));
            }
        }
        if (lastEnd >= 0L) {
            return lastEnd;
        }
        return line.endTimeMs > line.startTimeMs ? line.endTimeMs : -1L;
    }

    private long maxSyllableEnd(List<LyricsLine.Syllable> syllables, long fallbackLineEndMs) {
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

    private boolean isPositionInside(InterludeInfo info) {
        return info != null && info.isInterlude && positionMs >= info.startTimeMs && positionMs < info.endTimeMs;
    }

    private String instrumentalKind(int lineIndex, int lineCount) {
        if (lineIndex == 0) {
            return "prelude";
        }
        if (lineIndex == Math.max(0, lineCount - 1)) {
            return "postlude";
        }
        return "break";
    }

    private String interludeCandidateText(LyricsLine line) {
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

    private boolean isInterludeMarkerText(String text) {
        String normalized = text == null ? "" : text
                .replace("&nbsp;", " ")
                .replace("&NBSP;", " ")
                .trim();
        if (normalized.isEmpty()) {
            return true;
        }
        for (int offset = 0; offset < normalized.length(); ) {
            int codePoint = normalized.codePointAt(offset);
            if (!isInterludeMarkerCodePoint(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private boolean isInterludeMarkerCodePoint(int codePoint) {
        return Character.isWhitespace(codePoint)
                || codePoint == 0x00A0
                || (codePoint >= 0x200B && codePoint <= 0x200D)
                || codePoint == 0xFEFF
                || (codePoint >= 0x2669 && codePoint <= 0x266C);
    }

    private int findActiveSegmentIndex(List<TextRow> rows) {
        int fallbackIndex = -1;
        long fallbackEnd = Long.MIN_VALUE;
        int nextIndex = -1;
        long nextStart = Long.MAX_VALUE;
        for (TextRow row : rows) {
            for (TextSegment segment : row.segments) {
                if (segment.width <= 0f || isWhitespace(segment.text)) {
                    continue;
                }
                if (positionMs >= segment.startTimeMs && positionMs < segment.endTimeMs) {
                    return segment.sourceIndex;
                }
                if (positionMs >= segment.endTimeMs && segment.endTimeMs >= fallbackEnd) {
                    fallbackEnd = segment.endTimeMs;
                    fallbackIndex = segment.sourceIndex;
                }
                if (positionMs < segment.startTimeMs && segment.startTimeMs < nextStart) {
                    nextStart = segment.startTimeMs;
                    nextIndex = segment.sourceIndex;
                }
            }
        }
        if (fallbackIndex >= 0 && positionMs - fallbackEnd < 2000L) {
            return nextIndex >= 0 ? nextIndex : fallbackIndex;
        }
        return nextIndex >= 0 ? nextIndex : fallbackIndex;
    }

    private KaraokeBounce karaokeBounce(TextSegment segment, DrawGroup group) {
        if (!karaokeBounceEffectEnabled || !group.active || group.activeSegmentIndex < 0) {
            return KaraokeBounce.IDLE;
        }

        float centerIndex = segment.sourceIndex + Math.max(0, segment.sourceLength - 1) * 0.5f;
        float distance = Math.abs(centerIndex - group.activeSegmentIndex);
        String bounceKey = group.bounceKeyPrefix + ':' + segment.sourceIndex;
        BounceState state = bounceStates.get(bounceKey);
        if (state == null && distance > KARAOKE_BOUNCE_MAX_SEGMENT_DISTANCE) {
            return KaraokeBounce.IDLE;
        }

        long now = SystemClock.uptimeMillis();
        if (state == null) {
            if (completedBounceKeys.contains(bounceKey)
                    || positionMs < segment.startTimeMs - KARAOKE_BOUNCE_PRELEAD_MS
                    || positionMs > segment.startTimeMs + KARAOKE_BOUNCE_RISE_MS) {
                return KaraokeBounce.IDLE;
            }
            long offsetFromStart = Math.max(-KARAOKE_BOUNCE_PRELEAD_MS, positionMs - segment.startTimeMs);
            float attenuation = Math.max(0.22f, 1f - distance * 0.23f);
            state = new BounceState(now - offsetFromStart, attenuation);
            bounceStates.put(bounceKey, state);
        }

        float totalWindow = KARAOKE_BOUNCE_RISE_MS + KARAOKE_BOUNCE_RELEASE_MS;
        float elapsed = now - state.startUptimeMs;
        if (elapsed < -KARAOKE_BOUNCE_PRELEAD_MS) {
            return KaraokeBounce.IDLE;
        }
        if (elapsed > totalWindow) {
            bounceStates.remove(bounceKey);
            completedBounceKeys.add(bounceKey);
            return KaraokeBounce.IDLE;
        }

        float waveStrength;
        if (elapsed < 0f) {
            float preProgress = (elapsed + KARAOKE_BOUNCE_PRELEAD_MS) / (float) KARAOKE_BOUNCE_PRELEAD_MS;
            waveStrength = easeOutCubic(preProgress) * 0.22f;
        } else if (elapsed <= KARAOKE_BOUNCE_RISE_MS) {
            float riseProgress = elapsed / (float) KARAOKE_BOUNCE_RISE_MS;
            waveStrength = 0.22f + easeOutCubic(riseProgress) * 0.78f;
        } else {
            float fallProgress = Math.min(1f, (elapsed - KARAOKE_BOUNCE_RISE_MS) / (float) KARAOKE_BOUNCE_RELEASE_MS);
            waveStrength = (float) Math.pow(1f - fallProgress, 1.38f);
        }

        waveStrength *= state.attenuation;
        if (waveStrength < 0.025f) {
            return KaraokeBounce.IDLE;
        }

        float offsetY = Math.round((-group.textSize * 0.23f * waveStrength) * 2f) / 2f;
        float scale = Math.round((1f + 0.055f * waveStrength) * 100f) / 100f;
        return new KaraokeBounce(offsetY, scale, offsetY != 0f || scale != 1f);
    }

    private float easeOutCubic(float value) {
        float t = clamp(value);
        return 1f - (float) Math.pow(1f - t, 3.0);
    }

    private int inactiveColor(float distance) {
        int alpha = Math.round(185f - Math.min(2.6f, distance) * 46f);
        return Color.argb(Math.max(54, Math.min(190, alpha)), 174, 181, 195);
    }

    private String normalizeKind(String kind) {
        String value = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? "vocal" : value;
    }

    private int colorForSpeaker(String speaker, String role, int fallback) {
        String key = normalizeSpeakerKey(speaker);
        int color = speakerActiveColor(key);
        return color != 0 ? color : fallback;
    }

    private int normalActiveColor() {
        return speakerColorSettings.color(AiLyricsSettings.SPEAKER_COLOR_NORMAL);
    }

    private int inactiveColorForSpeaker(String speaker, float distance) {
        String key = normalizeSpeakerKey(speaker);
        int color = speakerActiveColor(key);
        if (color == 0) {
            return inactiveColor(distance);
        }
        int distanceAlpha = Color.alpha(inactiveColor(distance));
        float distanceFactor = distanceAlpha / 185f;
        int alpha = Math.round(255f * speakerInactiveAlpha(key) * distanceFactor);
        alpha = Math.max(40, Math.min(150, alpha));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int speakerActiveColor(String key) {
        if (key.isEmpty()) {
            return 0;
        }
        if ("speaker-b".equals(key) || "b".equals(key)) {
            return SPEAKER_B_COLOR;
        }
        if ("speaker-c".equals(key) || "c".equals(key)) {
            return SPEAKER_C_COLOR;
        }
        if ("speaker-d".equals(key) || "d".equals(key)) {
            return SPEAKER_D_COLOR;
        }
        if ("speaker-sfx".equals(key) || "sfx".equals(key)) {
            return SPEAKER_SFX_COLOR;
        }

        int color = numberedSpeakerColor(key, "male");
        if (color != 0) {
            return color;
        }
        color = numberedSpeakerColor(key, "female");
        if (color != 0) {
            return color;
        }
        color = numberedSpeakerColor(key, "duet");
        return color;
    }

    private float speakerInactiveAlpha(String key) {
        if ("speaker-b".equals(key) || "b".equals(key) || "speaker-sfx".equals(key) || "sfx".equals(key)) {
            return 0.46f;
        }
        if ("speaker-c".equals(key) || "c".equals(key) || "speaker-d".equals(key) || "d".equals(key)) {
            return 0.48f;
        }
        int maleIndex = speakerIndex(key, "male");
        if (maleIndex >= 0) {
            return maleIndex == 0 ? 0.52f : 0.50f;
        }
        int femaleIndex = speakerIndex(key, "female");
        if (femaleIndex >= 0) {
            return femaleIndex == 0 ? 0.52f : 0.50f;
        }
        int duetIndex = speakerIndex(key, "duet");
        if (duetIndex >= 0) {
            return duetIndex == 0 ? 0.52f : 0.50f;
        }
        return 0.50f;
    }

    private String normalizeSpeakerKey(String speaker) {
        String value = speaker == null ? "" : speaker.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        boolean lastDash = false;
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            if (c == '_' || Character.isWhitespace(c)) {
                if (!lastDash && normalized.length() > 0) {
                    normalized.append('-');
                    lastDash = true;
                }
            } else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-') {
                if (c == '-' && (lastDash || normalized.length() == 0)) {
                    continue;
                }
                normalized.append(c);
                lastDash = c == '-';
            }
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == '-') {
            normalized.deleteCharAt(length - 1);
        }
        return normalized.toString();
    }

    private int numberedSpeakerColor(String key, String prefix) {
        int index = speakerIndex(key, prefix);
        if (index < 0 || index >= 5) {
            return 0;
        }
        return speakerColorSettings.color(prefix + (index + 1));
    }

    private int speakerIndex(String key, String prefix) {
        if (key.equals(prefix) || key.equals("speaker-" + prefix)) {
            return 0;
        }
        if (key.startsWith(prefix + "-")) {
            return parseSpeakerIndex(key.substring(prefix.length() + 1));
        }
        if (key.startsWith(prefix) && key.length() > prefix.length()) {
            return parseSpeakerIndex(key.substring(prefix.length()));
        }
        String speakerPrefix = "speaker-" + prefix + "-";
        if (key.startsWith(speakerPrefix)) {
            return parseSpeakerIndex(key.substring(speakerPrefix.length()));
        }
        String compactSpeakerPrefix = "speaker-" + prefix;
        if (key.startsWith(compactSpeakerPrefix) && key.length() > compactSpeakerPrefix.length()) {
            return parseSpeakerIndex(key.substring(compactSpeakerPrefix.length()));
        }
        return -1;
    }

    private int parseSpeakerIndex(String value) {
        if (value == null || value.isEmpty()) {
            return -1;
        }
        try {
            int number = Integer.parseInt(value);
            return number - 1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private float contentLeft() {
        return sp(SIDE_PADDING_SP);
    }

    private float contentWidth() {
        return Math.max(sp(80f), getWidth() - sp(SIDE_PADDING_SP * 2f));
    }

    private float sp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private float dp(float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    private static List<String> splitChars(String value) {
        List<String> chars = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return chars;
        }
        value.codePoints().forEach(codePoint -> chars.add(new String(Character.toChars(codePoint))));
        return chars;
    }

    private boolean isWhitespace(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.codePoints().allMatch(Character::isWhitespace);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private int scaleAlpha(int color, float amount) {
        return withAlpha(color, Math.round(Color.alpha(color) * clamp(amount)));
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float clampCenterIndex(float value) {
        int count = Math.max(currentDisplayLineCount, lines.size());
        if (count <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(count - 1f, value));
    }

    private float sin(long now, long periodMs) {
        return (float) Math.sin((now % periodMs) / (double) periodMs * Math.PI * 2.0);
    }

    private float positiveSin(long now, long periodMs) {
        return (sin(now, periodMs) + 1f) * 0.5f;
    }

    private static final class DisplayLine {
        final LyricsLine line;
        final int sourceIndex;
        final int displayIndex;
        final InterludeInfo interludeInfo;

        static DisplayLine real(LyricsLine line, int sourceIndex, int displayIndex, InterludeInfo interludeInfo) {
            return new DisplayLine(line, sourceIndex, displayIndex, interludeInfo);
        }

        static DisplayLine virtual(int sourceIndex, int displayIndex, InterludeInfo interludeInfo) {
            return new DisplayLine(null, sourceIndex, displayIndex, interludeInfo);
        }

        DisplayLine(LyricsLine line, int sourceIndex, int displayIndex, InterludeInfo interludeInfo) {
            this.line = line;
            this.sourceIndex = sourceIndex;
            this.displayIndex = displayIndex;
            this.interludeInfo = interludeInfo == null ? InterludeInfo.none() : interludeInfo;
        }

        boolean isInterlude() {
            return interludeInfo != null && interludeInfo.isInterlude;
        }

        boolean isTimed() {
            return isInterlude() || (line != null && line.isTimed());
        }

        long startTimeMs() {
            return isInterlude() ? interludeInfo.startTimeMs : (line == null ? 0L : line.startTimeMs);
        }

        long endTimeMs() {
            return isInterlude() ? interludeInfo.endTimeMs : (line == null ? 0L : line.endTimeMs);
        }

        long seekTimeMs() {
            return startTimeMs();
        }
    }

    private static final class InterludeInfo {
        final boolean isInterlude;
        final long startTimeMs;
        final long endTimeMs;
        final String kind;
        final boolean virtual;

        static InterludeInfo none() {
            return new InterludeInfo(false, 0L, 0L, "break", false);
        }

        InterludeInfo(boolean isInterlude, long startTimeMs, long endTimeMs, String kind, boolean virtual) {
            this.isInterlude = isInterlude;
            this.startTimeMs = Math.max(0L, startTimeMs);
            this.endTimeMs = Math.max(this.startTimeMs, endTimeMs);
            this.kind = kind == null || kind.trim().isEmpty() ? "break" : kind.trim();
            this.virtual = virtual;
        }
    }

    private static final class DrawGroup {
        final List<TextRow> rows;
        final float textSize;
        final int inactiveColor;
        final int activeColor;
        final String kind;
        final boolean active;
        final int rowSeed;
        final String bounceKeyPrefix;
        final int activeSegmentIndex;
        final InterludeInfo interludeInfo;
        final Typeface typeface;
        final boolean supplement;

        DrawGroup(
                List<TextRow> rows,
                float textSize,
                int inactiveColor,
                int activeColor,
                String kind,
                boolean active,
                int rowSeed,
                String bounceKeyPrefix,
                int activeSegmentIndex,
                Typeface typeface,
                boolean supplement
        ) {
            this.rows = rows == null || rows.isEmpty() ? Collections.emptyList() : rows;
            this.textSize = textSize;
            this.inactiveColor = inactiveColor;
            this.activeColor = activeColor;
            this.kind = kind;
            this.active = active;
            this.rowSeed = rowSeed;
            this.bounceKeyPrefix = bounceKeyPrefix == null ? "" : bounceKeyPrefix;
            this.activeSegmentIndex = activeSegmentIndex;
            this.interludeInfo = InterludeInfo.none();
            this.typeface = typeface;
            this.supplement = supplement;
        }

        static DrawGroup interlude(float textSize, int inactiveColor, int activeColor, boolean active, InterludeInfo info, Typeface typeface) {
            return new DrawGroup(
                    Collections.emptyList(),
                    textSize,
                    inactiveColor,
                    activeColor,
                    "vocal",
                    active,
                    0,
                    "",
                    -1,
                    info == null ? InterludeInfo.none() : info,
                    typeface,
                    false
            );
        }

        private DrawGroup(
                List<TextRow> rows,
                float textSize,
                int inactiveColor,
                int activeColor,
                String kind,
                boolean active,
                int rowSeed,
                String bounceKeyPrefix,
                int activeSegmentIndex,
                InterludeInfo interludeInfo,
                Typeface typeface,
                boolean supplement
        ) {
            this.rows = rows == null || rows.isEmpty() ? Collections.emptyList() : rows;
            this.textSize = textSize;
            this.inactiveColor = inactiveColor;
            this.activeColor = activeColor;
            this.kind = kind;
            this.active = active;
            this.rowSeed = rowSeed;
            this.bounceKeyPrefix = bounceKeyPrefix == null ? "" : bounceKeyPrefix;
            this.activeSegmentIndex = activeSegmentIndex;
            this.interludeInfo = interludeInfo == null ? InterludeInfo.none() : interludeInfo;
            this.typeface = typeface;
            this.supplement = supplement;
        }

        float rowHeight(TextRow row) {
            return textSize * LINE_HEIGHT_MULTIPLIER + rubyExtraHeight(row);
        }

        float baselineOffset(TextRow row) {
            return textSize + rubyExtraHeight(row);
        }

        float rubyExtraHeight(TextRow row) {
            if (row == null || !row.hasRuby()) {
                return 0f;
            }
            return textSize * FURIGANA_EXTRA_HEIGHT_RATIO;
        }

        float height() {
            if (isInterlude()) {
                return textSize * 2.2f;
            }
            if (rows.isEmpty()) {
                return textSize * LINE_HEIGHT_MULTIPLIER;
            }
            float total = 0f;
            for (TextRow row : rows) {
                total += rowHeight(row);
            }
            return total;
        }

        boolean isInterlude() {
            return interludeInfo != null && interludeInfo.isInterlude;
        }

        boolean hasRuby() {
            for (TextRow row : rows) {
                if (row.hasRuby()) {
                    return true;
                }
            }
            return false;
        }

        boolean firstRowHasRuby() {
            return !rows.isEmpty() && rows.get(0).hasRuby();
        }
    }

    private static final class LineLayout {
        final int index;
        final DisplayLine displayLine;
        final List<DrawGroup> groups;
        final float height;

        LineLayout(int index, DisplayLine displayLine, List<DrawGroup> groups, float height) {
            this.index = index;
            this.displayLine = displayLine;
            this.groups = groups == null ? Collections.emptyList() : groups;
            this.height = Math.max(1f, height);
        }
    }

    private static final class LineHitTarget {
        final float top;
        final float bottom;
        final float centerY;
        final long seekTimeMs;

        LineHitTarget(float top, float bottom, float centerY, long seekTimeMs) {
            this.top = top;
            this.bottom = bottom;
            this.centerY = centerY;
            this.seekTimeMs = Math.max(0L, seekTimeMs);
        }
    }

    private static final class TextRow {
        final List<TextSegment> segments;
        final String text;
        final boolean hasRuby;

        TextRow(List<TextSegment> segments) {
            this.segments = segments == null ? Collections.emptyList() : segments;
            StringBuilder builder = new StringBuilder();
            boolean nextHasRuby = false;
            for (TextSegment segment : this.segments) {
                builder.append(segment.text);
                if (segment.rubyText != null && !segment.rubyText.trim().isEmpty()) {
                    nextHasRuby = true;
                }
            }
            this.text = builder.toString();
            this.hasRuby = nextHasRuby;
        }

        boolean hasRuby() {
            return hasRuby;
        }
    }

    private static final class WrapUnit {
        final List<TextSegment> segments;
        final float width;

        WrapUnit(List<TextSegment> segments, float width) {
            this.segments = segments == null ? Collections.emptyList() : segments;
            this.width = Math.max(0f, width);
        }
    }

    private static final class TextSegment {
        final String text;
        final float textWidth;
        final float width;
        final float textInset;
        final long startTimeMs;
        final long endTimeMs;
        final int sourceIndex;
        final int sourceLength;
        final String rubyText;

        TextSegment(String text, float width, long startTimeMs, long endTimeMs, int sourceIndex, int sourceLength) {
            this(text, width, width, startTimeMs, endTimeMs, sourceIndex, sourceLength, "");
        }

        TextSegment(String text, float width, long startTimeMs, long endTimeMs, int sourceIndex, int sourceLength, String rubyText) {
            this(text, width, width, startTimeMs, endTimeMs, sourceIndex, sourceLength, rubyText);
        }

        TextSegment(String text, float textWidth, float width, long startTimeMs, long endTimeMs, int sourceIndex, int sourceLength, String rubyText) {
            this.text = text == null ? "" : text;
            this.textWidth = Math.max(0f, textWidth);
            this.width = Math.max(0f, width);
            this.textInset = Math.max(0f, (this.width - this.textWidth) * 0.5f);
            this.startTimeMs = Math.max(0L, startTimeMs);
            this.endTimeMs = Math.max(this.startTimeMs, endTimeMs);
            this.sourceIndex = Math.max(0, sourceIndex);
            this.sourceLength = Math.max(1, sourceLength);
            this.rubyText = rubyText == null ? "" : rubyText;
        }
    }

    private static final class RubyAnnotation {
        final int start;
        final int length;
        final String reading;
        final List<String> readingChars;

        RubyAnnotation(int start, int length, String reading) {
            this.start = Math.max(0, start);
            this.length = Math.max(1, length);
            this.reading = reading == null ? "" : reading;
            this.readingChars = splitChars(this.reading);
        }

        int end() {
            return start + length;
        }

        String readingForRange(int overlapStart, int overlapEnd) {
            int safeStart = Math.max(start, overlapStart);
            int safeEnd = Math.min(end(), overlapEnd);
            if (safeStart >= safeEnd || reading.isEmpty()) {
                return "";
            }
            if (safeStart == start && safeEnd == end()) {
                return reading;
            }
            if (length <= 1 || readingChars.isEmpty()) {
                return reading;
            }
            int charsPerKanji = Math.max(1, readingChars.size() / length);
            StringBuilder builder = new StringBuilder();
            for (int sourceIndex = safeStart; sourceIndex < safeEnd; sourceIndex++) {
                int relative = sourceIndex - start;
                int readStart = Math.min(readingChars.size(), relative * charsPerKanji);
                int readEnd = relative == length - 1
                        ? readingChars.size()
                        : Math.min(readingChars.size(), (relative + 1) * charsPerKanji);
                for (int index = readStart; index < readEnd; index++) {
                    builder.append(readingChars.get(index));
                }
            }
            return builder.toString();
        }
    }

    private static final class KaraokeBounce {
        static final KaraokeBounce IDLE = new KaraokeBounce(0f, 1f, false);

        final float offsetY;
        final float scale;
        final boolean active;

        KaraokeBounce(float offsetY, float scale, boolean active) {
            this.offsetY = offsetY;
            this.scale = scale;
            this.active = active;
        }
    }

    private static final class BounceState {
        final long startUptimeMs;
        final float attenuation;

        BounceState(long startUptimeMs, float attenuation) {
            this.startUptimeMs = startUptimeMs;
            this.attenuation = Math.max(0f, attenuation);
        }
    }

    interface OnSeekListener {
        void onSeekRequested(long positionMs);
    }
}
