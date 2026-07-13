package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class MainLyricPreviewView extends View {
    private static final float PRIMARY_TEXT_SP = 17f;
    private static final float SECONDARY_TEXT_SP = 14.5f;
    private static final float ROW_GAP_SP = 4f;
    private static final float EDGE_FADE_DP = 28f;
    private static final float FURIGANA_TEXT_RATIO = 0.46f;
    private static final float SLIDE_START_HOLD = 0.3f;
    private static final float SLIDE_MOVE_DURATION = 0.4f;
    private static final int RESERVED_TEXT_ROWS = 3;
    private static final float WAVE_PERIOD_MS = 980f;
    private static final int KARAOKE_BOUNCE_MAX_SEGMENT_DISTANCE = 3;
    private static final long KARAOKE_BOUNCE_PRELEAD_MS = 70L;
    private static final long KARAOKE_BOUNCE_RISE_MS = 220L;
    private static final long KARAOKE_BOUNCE_RELEASE_MS = 640L;
    private static final int PREVIEW_LAYOUT_MATCH = 1;
    private static final int PREVIEW_INSTANCES_MATCH = 1 << 1;
    private static final int PREVIEW_WIDTH_INPUTS_MATCH = 1 << 2;
    private static final int PRIMARY_KARAOKE_ACTIVE_COLOR = Color.argb(252, 255, 255, 255);
    private static final int SECONDARY_KARAOKE_ACTIVE_COLOR = Color.argb(226, 255, 255, 255);
    private static final int[] PRIMARY_KARAOKE_FILL_COLORS = {
            PRIMARY_KARAOKE_ACTIVE_COLOR,
            PRIMARY_KARAOKE_ACTIVE_COLOR,
            Color.argb(0, 255, 255, 255)
    };
    private static final int[] SECONDARY_KARAOKE_FILL_COLORS = {
            SECONDARY_KARAOKE_ACTIVE_COLOR,
            SECONDARY_KARAOKE_ACTIVE_COLOR,
            Color.argb(0, 255, 255, 255)
    };
    private static final float[] KARAOKE_FILL_STOPS = {0f, 0.34f, 1f};
    private static final float[] LOADING_ROW_WIDTH_FACTORS = {0.54f, 0.78f, 0.42f};
    private static final float[] LOADING_SHIMMER_STOPS = {0f, 0.5f, 1f};
    private static final float[] EFFECT_TRANSLATE_X = {0f, -0.5f, 0.45f, -0.25f};
    private static final float[] EFFECT_TRANSLATE_Y = {0f, 0.25f, -0.25f, -0.35f};
    private static final Pattern MARKUP_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgeFadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics textFontMetrics = new Paint.FontMetrics();
    private final PorterDuffXfermode edgeFadeXfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
    private final LinearGradient primaryKaraokeFillShader = karaokeFillShader(PRIMARY_KARAOKE_FILL_COLORS);
    private final LinearGradient secondaryKaraokeFillShader = karaokeFillShader(SECONDARY_KARAOKE_FILL_COLORS);
    private final int[] loadingShimmerColors = {
            Color.argb(0, 255, 255, 255),
            Color.argb(0, 255, 255, 255),
            Color.argb(0, 255, 255, 255)
    };
    private final Matrix karaokeFillShaderMatrix = new Matrix();
    private final List<PreviewLine> lines = new ArrayList<>();
    private final Map<PreviewLine, List<TextSegment>> textSegmentCache = new IdentityHashMap<>();
    private final Map<String, BounceState> bounceStates = new HashMap<>();
    private final Set<String> completedBounceKeys = new HashSet<>();
    private float[] measuredLineWidths = new float[0];
    private boolean measuredLineWidthsValid;
    private float measuredLineWidthsScaledDensity = Float.NaN;
    private LinearGradient edgeFadeShader;
    private float edgeFadeShaderLeft = Float.NaN;
    private float edgeFadeShaderWidth = Float.NaN;
    private float edgeFadeShaderStop = Float.NaN;
    private Typeface primaryTypeface;
    private Typeface secondaryTypeface;
    private AiLyricsSettings.TypographySettings typographySettings = AiLyricsSettings.TypographySettings.defaults();
    private long basePositionMs;
    private long baseUptimeMs;
    private long lineStartMs;
    private long lineEndMs;
    private boolean playing;
    private boolean karaokeBounceEffectEnabled = true;
    private boolean karaokeDataAsLineSynced;
    private float textScale = 1f;

    MainLyricPreviewView(Context context) {
        super(context);
        init();
    }

    MainLyricPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    void setPreview(List<PreviewLine> nextLines, long positionMs, long startTimeMs, long endTimeMs, boolean isPlaying) {
        List<PreviewLine> safeLines = nextLines == null ? Collections.emptyList() : nextLines;
        int previewMatches = comparePreviewLines(safeLines);
        boolean layoutChanged = (previewMatches & PREVIEW_LAYOUT_MATCH) == 0;
        boolean segmentInputsChanged = (previewMatches & PREVIEW_INSTANCES_MATCH) == 0;
        boolean lineWidthInputsChanged = (previewMatches & PREVIEW_WIDTH_INPUTS_MATCH) == 0;
        lines.clear();
        lines.addAll(safeLines);
        basePositionMs = Math.max(0L, positionMs);
        baseUptimeMs = SystemClock.uptimeMillis();
        lineStartMs = Math.max(0L, startTimeMs);
        lineEndMs = Math.max(lineStartMs, endTimeMs);
        playing = isPlaying;
        if (segmentInputsChanged) {
            textSegmentCache.clear();
        }
        if (lineWidthInputsChanged) {
            measuredLineWidthsValid = false;
        }
        if (layoutChanged) {
            bounceStates.clear();
            completedBounceKeys.clear();
            requestLayout();
        }
        postInvalidateOnAnimation();
    }

    void clear() {
        setPreview(Collections.emptyList(), 0L, 0L, 0L, false);
    }

    void setTextScale(float scale) {
        float safeScale = Math.max(0.85f, Math.min(1.75f, scale));
        if (Math.abs(textScale - safeScale) < 0.01f) {
            return;
        }
        textScale = safeScale;
        textSegmentCache.clear();
        measuredLineWidthsValid = false;
        requestLayout();
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

    void setKaraokeDataAsLineSynced(boolean enabled) {
        if (karaokeDataAsLineSynced == enabled) {
            return;
        }
        karaokeDataAsLineSynced = enabled;
        bounceStates.clear();
        completedBounceKeys.clear();
        postInvalidateOnAnimation();
    }

    void setTypographySettings(AiLyricsSettings.TypographySettings settings) {
        typographySettings = settings == null ? AiLyricsSettings.TypographySettings.defaults() : settings;
        textSegmentCache.clear();
        measuredLineWidthsValid = false;
        requestLayout();
        postInvalidateOnAnimation();
    }

    private void init() {
        primaryTypeface = AppFonts.semiBold(getContext());
        secondaryTypeface = AppFonts.semiBold(getContext());
        textPaint.setSubpixelText(true);
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = Math.round(Math.max(desiredContentHeight(), reservedContentHeight()));
        int measuredWidth = MeasureSpec.getSize(widthMeasureSpec);
        int measuredHeight = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (lines.isEmpty()) {
            return;
        }

        float totalHeight = desiredContentHeight();
        float top = Math.max(0f, (getHeight() - totalHeight) * 0.5f);
        long position = estimatedPositionMs();
        float progress = lineProgress(position);
        float left = getPaddingLeft();
        float width = Math.max(1f, getWidth() - getPaddingLeft() - getPaddingRight());
        boolean overflow = measureLineWidths(width);

        int save = overflow
                ? canvas.saveLayer(left, 0f, left + width, getHeight(), null)
                : canvas.save();
        canvas.clipRect(left, 0f, left + width, getHeight());
        for (int index = 0; index < lines.size(); index++) {
            PreviewLine line = lines.get(index);
            float textSize = sp(textSizeSp(line));
            textPaint.setTypeface(typefaceForLine(line));
            textPaint.setTextSize(textSize);
            int alpha = line.primary ? 244 : 208;
            textPaint.setColor(Color.argb(alpha, 255, 255, 255));

            textPaint.getFontMetrics(textFontMetrics);
            float rowHeight = rowHeight(line);
            float rubyExtraHeight = rubyExtraHeight(line, textSize);
            float baseline = top
                    + rubyExtraHeight
                    + (rowHeight - rubyExtraHeight - textFontMetrics.ascent - textFontMetrics.descent) * 0.5f;
            float textWidth = Float.isNaN(measuredLineWidths[index])
                    ? measureLineWidth(line)
                    : measuredLineWidths[index];
            float x = xForText(textWidth, width, left, progress);
            drawPreviewLine(canvas, line, x, baseline, textSize, alpha, position, left, width);
            top += rowHeight;
            if (index + 1 < lines.size()) {
                top += sp(rowGapSp());
            }
        }
        if (overflow) {
            applyEdgeFadeMask(canvas, left, width);
        }
        canvas.restoreToCount(save);

        if ((playing && lineEndMs > lineStartMs && estimatedPositionMs() < lineEndMs && (overflow || hasKaraokeLine()))
                || hasAnimatedLine()) {
            postInvalidateOnAnimation();
        }
    }

    private void drawPreviewLine(
            Canvas canvas,
            PreviewLine line,
            float x,
            float baseline,
            float textSize,
            int normalAlpha,
            long positionMs,
            float left,
            float width
    ) {
        if (line.isInterlude()) {
            drawInterludeLine(canvas, line.text, baseline, textSize, normalAlpha, left, width);
            return;
        }
        if (line.isLoading()) {
            drawLoadingLine(canvas, line.text, baseline, textSize, normalAlpha, left, width);
            return;
        }
        if (karaokeDataAsLineSynced || !line.hasKaraoke()) {
            textPaint.setColor(Color.argb(normalAlpha, 255, 255, 255));
            canvas.drawText(line.text, x, baseline, textPaint);
            drawPlainRubyText(canvas, line, x, baseline, textSize, normalAlpha);
            return;
        }

        drawKaraokeLine(canvas, line, x, baseline, textSize, positionMs);
    }

    private void drawInterludeLine(
            Canvas canvas,
            String label,
            float baseline,
            float textSize,
            int alpha,
            float left,
            float width
    ) {
        long now = SystemClock.uptimeMillis();
        float barWidth = dp(3.2f);
        float barGap = dp(3.8f);
        float iconWidth = barWidth * 4f + barGap * 3f;
        float labelGap = dp(10f);
        boolean showLabel = label != null && !label.trim().isEmpty();
        float labelWidth = showLabel ? textPaint.measureText(label) : 0f;
        float totalWidth = iconWidth + (showLabel ? labelGap + labelWidth : 0f);
        float start = left + Math.max(0f, (width - totalWidth) * 0.5f);
        float centerY = baseline - textSize * 0.36f;
        float minHeight = dp(7f);
        float maxHeight = dp(22f);
        float radius = barWidth * 0.7f;

        shapePaint.setShader(null);
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(Color.argb(Math.min(255, alpha + 4), 255, 255, 255));
        for (int index = 0; index < 4; index++) {
            float phase = positiveSin(now + index * 145L, 980L);
            float height = minHeight + (maxHeight - minHeight) * (0.2f + phase * 0.8f);
            float x = start + index * (barWidth + barGap);
            canvas.drawRoundRect(x, centerY - height * 0.5f, x + barWidth, centerY + height * 0.5f, radius, radius, shapePaint);
        }

        if (showLabel) {
            textPaint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawText(label, start + iconWidth + labelGap, baseline, textPaint);
        }
    }

    private void drawLoadingLine(
            Canvas canvas,
            String label,
            float baseline,
            float textSize,
            int alpha,
            float left,
            float width
    ) {
        long now = SystemClock.uptimeMillis();
        float railWidth = Math.min(width * 0.72f, dp(210f));
        float railHeight = dp(4.2f);
        float railGap = dp(6.6f);
        float totalHeight = railHeight * LOADING_ROW_WIDTH_FACTORS.length
                + railGap * (LOADING_ROW_WIDTH_FACTORS.length - 1);
        float startY = baseline - textSize * 0.34f - totalHeight * 0.5f;
        float radius = railHeight * 0.9f;

        shapePaint.setShader(null);
        shapePaint.setStyle(Paint.Style.FILL);
        loadingShimmerColors[1] = Color.argb(Math.min(255, alpha), 255, 255, 255);
        for (int index = 0; index < LOADING_ROW_WIDTH_FACTORS.length; index++) {
            float rowWidth = Math.max(dp(42f), railWidth * LOADING_ROW_WIDTH_FACTORS[index]);
            float rowLeft = left + Math.max(0f, (width - rowWidth) * 0.5f);
            float rowTop = startY + index * (railHeight + railGap);
            shapePaint.setColor(Color.argb(index == 1 ? 76 : 46, 255, 255, 255));
            canvas.drawRoundRect(rowLeft, rowTop, rowLeft + rowWidth, rowTop + railHeight, radius, radius, shapePaint);

            float shimmerWidth = Math.max(dp(28f), rowWidth * 0.36f);
            float phase = ((now + index * 145L) % 1280L) / 1280f;
            float shimmerLeft = rowLeft - shimmerWidth + (rowWidth + shimmerWidth * 2f) * phase;
            shapePaint.setShader(new LinearGradient(
                    shimmerLeft,
                    0f,
                    shimmerLeft + shimmerWidth,
                    0f,
                    loadingShimmerColors,
                    LOADING_SHIMMER_STOPS,
                    Shader.TileMode.CLAMP
            ));
            int save = canvas.save();
            canvas.clipRect(rowLeft, rowTop, rowLeft + rowWidth, rowTop + railHeight);
            canvas.drawRoundRect(rowLeft, rowTop, rowLeft + rowWidth, rowTop + railHeight, radius, radius, shapePaint);
            canvas.restoreToCount(save);
            shapePaint.setShader(null);
        }
    }

    private float xForText(float textWidth, float width, float left, float progress) {
        if (textWidth <= width) {
            return left + (width - textWidth) * 0.5f;
        }
        float fadeWidth = edgeFadeWidth(width);
        float startX = left + fadeWidth;
        float endX = left + width - fadeWidth - textWidth;
        return startX + (endX - startX) * slideProgress(progress);
    }

    private float slideProgress(float lineProgress) {
        if (lineProgress <= SLIDE_START_HOLD) {
            return 0f;
        }
        if (lineProgress >= SLIDE_START_HOLD + SLIDE_MOVE_DURATION) {
            return 1f;
        }
        return clamp((lineProgress - SLIDE_START_HOLD) / SLIDE_MOVE_DURATION);
    }

    private void applyEdgeFadeMask(Canvas canvas, float left, float width) {
        float fadeWidth = edgeFadeWidth(width);
        if (fadeWidth <= 0f) {
            return;
        }
        float fadeStop = clamp(fadeWidth / width);
        float rightFadeStart = Math.max(fadeStop, 1f - fadeStop);
        if (edgeFadeShader == null
                || Float.compare(edgeFadeShaderLeft, left) != 0
                || Float.compare(edgeFadeShaderWidth, width) != 0
                || Float.compare(edgeFadeShaderStop, fadeStop) != 0) {
            edgeFadeShader = new LinearGradient(
                    left,
                    0f,
                    left + width,
                    0f,
                    new int[] {
                            Color.TRANSPARENT,
                            Color.BLACK,
                            Color.BLACK,
                            Color.TRANSPARENT
                    },
                    new float[] {
                            0f,
                            fadeStop,
                            rightFadeStart,
                            1f
                    },
                    Shader.TileMode.CLAMP
            );
            edgeFadeShaderLeft = left;
            edgeFadeShaderWidth = width;
            edgeFadeShaderStop = fadeStop;
        }
        edgeFadePaint.setShader(edgeFadeShader);
        edgeFadePaint.setXfermode(edgeFadeXfermode);
        canvas.drawRect(left, 0f, left + width, getHeight(), edgeFadePaint);
        edgeFadePaint.setXfermode(null);
        edgeFadePaint.setShader(null);
    }

    private float edgeFadeWidth(float width) {
        return Math.min(dp(EDGE_FADE_DP), width * 0.28f);
    }

    private long estimatedPositionMs() {
        if (!playing) {
            return basePositionMs;
        }
        return basePositionMs + Math.max(0L, SystemClock.uptimeMillis() - baseUptimeMs);
    }

    private float lineProgress(long positionMs) {
        if (lineEndMs <= lineStartMs) {
            return 0f;
        }
        return clamp((positionMs - lineStartMs) / (float) (lineEndMs - lineStartMs));
    }

    private boolean measureLineWidths(float width) {
        int lineCount = lines.size();
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        if (!measuredLineWidthsValid
                || Float.compare(measuredLineWidthsScaledDensity, scaledDensity) != 0) {
            if (measuredLineWidths.length < lineCount) {
                measuredLineWidths = new float[lineCount];
            }
            for (int index = 0; index < lineCount; index++) {
                PreviewLine line = lines.get(index);
                if (line.isAnimatedVisual()) {
                    measuredLineWidths[index] = Float.NaN;
                    continue;
                }
                textPaint.setTypeface(typefaceForLine(line));
                textPaint.setTextSize(sp(textSizeSp(line)));
                measuredLineWidths[index] = measureLineWidth(line);
            }
            measuredLineWidthsValid = true;
            measuredLineWidthsScaledDensity = scaledDensity;
        }
        for (int index = 0; index < lineCount; index++) {
            if (measuredLineWidths[index] > width) {
                return true;
            }
        }
        return false;
    }

    private float measureLineWidth(PreviewLine line) {
        if (line != null && line.hasKaraoke()) {
            float width = 0f;
            for (LyricsLine.Syllable syllable : line.syllables) {
                if (syllable == null || syllable.text == null || syllable.text.isEmpty()) {
                    continue;
                }
                width += textPaint.measureText(syllable.text);
            }
            if (width > 0f) {
                return width;
            }
        }
        return line == null ? 0f : textPaint.measureText(line.text);
    }

    private boolean hasAnimatedLine() {
        for (PreviewLine line : lines) {
            if (line.isAnimatedVisual()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasKaraokeLine() {
        if (karaokeDataAsLineSynced) {
            return false;
        }
        for (PreviewLine line : lines) {
            if (line.hasKaraoke()) {
                return true;
            }
        }
        return false;
    }

    private float desiredContentHeight() {
        if (lines.isEmpty()) {
            return 0f;
        }
        float height = 0f;
        for (int index = 0; index < lines.size(); index++) {
            height += rowHeight(lines.get(index));
            if (index + 1 < lines.size()) {
                height += sp(rowGapSp());
            }
        }
        return height;
    }

    private float reservedContentHeight() {
        float primaryText = textSizeSp(AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL, PRIMARY_TEXT_SP);
        float primaryRow = sp(primaryText * 1.22f + primaryText * FURIGANA_TEXT_RATIO * 0.82f);
        float secondaryRow = sp(Math.max(
                textSizeSp(AiLyricsSettings.TYPO_MAIN_PREVIEW_PRONUNCIATION, SECONDARY_TEXT_SP),
                textSizeSp(AiLyricsSettings.TYPO_MAIN_PREVIEW_TRANSLATION, SECONDARY_TEXT_SP)
        ) * 1.18f);
        float gap = sp(rowGapSp());
        float height = primaryRow;
        for (int index = 1; index < RESERVED_TEXT_ROWS; index++) {
            height += gap + secondaryRow;
        }
        return height;
    }

    private float rowHeight(PreviewLine line) {
        if (line.isAnimatedVisual()) {
            return sp((line.primary ? 24f : 21f) * textScale * typographyStyle(slotForLine(line)).scale());
        }
        float textSize = sp(textSizeSp(line));
        return textSize * (line.primary ? 1.22f : 1.18f) + rubyExtraHeight(line, textSize);
    }

    private float rubyExtraHeight(PreviewLine line, float textSize) {
        if (line == null || !line.hasRuby()) {
            return 0f;
        }
        return Math.max(sp(7f), textSize * FURIGANA_TEXT_RATIO * 0.82f);
    }

    private int comparePreviewLines(List<PreviewLine> nextLines) {
        if (lines.size() != nextLines.size()) {
            return 0;
        }
        int matches = PREVIEW_LAYOUT_MATCH | PREVIEW_INSTANCES_MATCH | PREVIEW_WIDTH_INPUTS_MATCH;
        for (int index = 0; index < lines.size(); index++) {
            PreviewLine current = lines.get(index);
            PreviewLine next = nextLines.get(index);
            if (current != next) {
                matches &= ~PREVIEW_INSTANCES_MATCH;
            }
            if (current.primary != next.primary
                    || current.type != next.type
                    || current.hasKaraoke() != next.hasKaraoke()
                    || !current.kind.equals(next.kind)
                    || !current.slotId.equals(next.slotId)
                    || !current.rubyText.equals(next.rubyText)
                    || !current.text.equals(next.text)) {
                matches &= ~PREVIEW_LAYOUT_MATCH;
            }
            if (current.primary != next.primary
                    || current.type != next.type
                    || current.hasKaraoke() != next.hasKaraoke()
                    || !current.slotId.equals(next.slotId)
                    || !current.text.equals(next.text)
                    || (current.hasKaraoke() && !sameSyllableText(current.syllables, next.syllables))) {
                matches &= ~PREVIEW_WIDTH_INPUTS_MATCH;
            }
            if (matches == 0) {
                break;
            }
        }
        return matches;
    }

    private boolean sameSyllableText(List<LyricsLine.Syllable> current, List<LyricsLine.Syllable> next) {
        if (current.size() != next.size()) {
            return false;
        }
        for (int index = 0; index < current.size(); index++) {
            LyricsLine.Syllable currentSyllable = current.get(index);
            LyricsLine.Syllable nextSyllable = next.get(index);
            String currentText = currentSyllable == null || currentSyllable.text == null ? "" : currentSyllable.text;
            String nextText = nextSyllable == null || nextSyllable.text == null ? "" : nextSyllable.text;
            if (!currentText.equals(nextText)) {
                return false;
            }
        }
        return true;
    }

    private float positiveSin(long timeMs, long periodMs) {
        return (float) ((Math.sin((timeMs % periodMs) / (double) periodMs * Math.PI * 2.0) + 1.0) * 0.5);
    }

    private float rowGapSp() {
        return ROW_GAP_SP * Math.min(1.25f, textScale);
    }

    private float textSizeSp(PreviewLine line) {
        return textSizeSp(slotForLine(line), line != null && line.primary ? PRIMARY_TEXT_SP : SECONDARY_TEXT_SP);
    }

    private float textSizeSp(String slotId, float baseSizeSp) {
        return Math.max(8f, baseSizeSp * textScale * typographyStyle(slotId).scale());
    }

    private Typeface typefaceForLine(PreviewLine line) {
        return AppFonts.byWeight(getContext(), typographyStyle(slotForLine(line)).weight);
    }

    private AiLyricsSettings.TypographyStyle typographyStyle(String slotId) {
        AiLyricsSettings.TypographySettings settings = typographySettings == null
                ? AiLyricsSettings.TypographySettings.defaults()
                : typographySettings;
        return settings.style(slotId);
    }

    private String slotForLine(PreviewLine line) {
        if (line == null) {
            return AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL;
        }
        String normalized = AiLyricsSettings.typographySlotById(line.slotId).id;
        if (AiLyricsSettings.TYPO_MAIN_PREVIEW_PRONUNCIATION.equals(normalized)
                || AiLyricsSettings.TYPO_MAIN_PREVIEW_TRANSLATION.equals(normalized)
                || AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL.equals(normalized)) {
            return normalized;
        }
        return line.primary ? AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL : AiLyricsSettings.TYPO_MAIN_PREVIEW_PRONUNCIATION;
    }

    private void drawKaraokeLine(Canvas canvas, PreviewLine line, float x, float baseline, float textSize, long positionMs) {
        List<TextSegment> segments = textSegments(line);
        if (segments.isEmpty()) {
            textPaint.setColor(Color.argb(line.primary ? 244 : 208, 255, 255, 255));
            canvas.drawText(line.text, x, baseline, textPaint);
            return;
        }

        int inactiveColor = Color.argb(line.primary ? 116 : 96, 255, 255, 255);
        int activeColor = line.primary ? PRIMARY_KARAOKE_ACTIVE_COLOR : SECONDARY_KARAOKE_ACTIVE_COLOR;
        int activeSegmentIndex = activeSegmentIndex(segments, positionMs);
        float rowWidth = 0f;
        for (TextSegment segment : segments) {
            rowWidth += segment.width;
        }

        int rowSave = canvas.save();
        applyCanvasEffect(canvas, line.kind, x + rowWidth * 0.5f, baseline, textSize, line.rowSeed());
        float cursor = x;
        for (int index = 0; index < segments.size(); index++) {
            TextSegment segment = segments.get(index);
            float offsetY = "wave".equals(line.kind)
                    ? baseWaveOffset(line.kind, line.rowSeed(), index, textSize)
                    : 0f;
            float fill = segmentFillFraction(segment, positionMs);
            KaraokeBounce bounce = karaokeBounce(segment, activeSegmentIndex, line, positionMs, textSize);

            int segmentSave = canvas.save();
            if (bounce.active) {
                float pivotX = cursor + segment.width * 0.5f;
                float pivotY = baseline - textSize * 0.45f;
                canvas.translate(0f, bounce.offsetY);
                canvas.scale(bounce.scale, bounce.scale, pivotX, pivotY);
            }

            drawRubyText(canvas, segment, cursor, baseline + offsetY, line.kind, textSize, fill, line.primary);
            float textLeft = cursor + segment.textInset;
            configureTextPaint(inactiveColor, line.kind, textSize, false);
            canvas.drawText(segment.text, textLeft, baseline + offsetY, textPaint);
            if (fill > 0f) {
                drawActiveFill(canvas, segment, cursor, baseline, offsetY, line.kind, textSize, activeColor, fill);
            }
            canvas.restoreToCount(segmentSave);
            cursor += segment.width;
        }
        canvas.restoreToCount(rowSave);
        resetPaintEffects();
    }

    private void drawActiveFill(
            Canvas canvas,
            TextSegment segment,
            float cursor,
            float baseline,
            float offsetY,
            String kind,
            float textSize,
            int activeColor,
            float fill
    ) {
        float safeFill = clamp(fill);
        float textLeft = cursor + segment.textInset;
        float textRight = textLeft + segment.textWidth;
        float fillRight = textLeft + segment.textWidth * safeFill;
        float top = baseline - textSize * 1.28f;
        float bottom = baseline + textSize * 0.48f;
        float softWidth = Math.min(sp(7f), Math.max(0f, segment.textWidth * 0.30f));
        float clipRight = safeFill >= 0.995f
                ? textRight
                : Math.min(textRight, fillRight + softWidth);

        int clipSave = canvas.save();
        canvas.clipRect(textLeft, top, clipRight, bottom);
        configureTextPaint(activeColor, kind, textSize, true);
        if (safeFill < 0.995f && softWidth > 1f && clipRight > textLeft) {
            float softStart = Math.max(textLeft, fillRight - softWidth * 0.42f);
            float softEnd = Math.max(softStart + 1f, Math.min(textRight, fillRight + softWidth));
            LinearGradient shader = activeColor == PRIMARY_KARAOKE_ACTIVE_COLOR
                    ? primaryKaraokeFillShader
                    : secondaryKaraokeFillShader;
            karaokeFillShaderMatrix.setScale(softEnd - softStart, 1f);
            karaokeFillShaderMatrix.postTranslate(softStart, 0f);
            shader.setLocalMatrix(karaokeFillShaderMatrix);
            textPaint.setShader(shader);
        }
        canvas.drawText(segment.text, textLeft, baseline + offsetY, textPaint);
        textPaint.setShader(null);
        canvas.restoreToCount(clipSave);
    }

    private static LinearGradient karaokeFillShader(int[] colors) {
        return new LinearGradient(
                0f,
                0f,
                1f,
                0f,
                colors,
                KARAOKE_FILL_STOPS,
                Shader.TileMode.CLAMP
        );
    }

    private void drawPlainRubyText(
            Canvas canvas,
            PreviewLine line,
            float x,
            float baseline,
            float textSize,
            int alpha
    ) {
        if (line == null || !line.hasRuby()) {
            return;
        }
        List<RubyAnnotation> annotations = line.rubyAnnotations();
        if (annotations.isEmpty()) {
            return;
        }
        String text = line.text == null ? "" : line.text;
        float rubySize = Math.max(sp(7.4f), textSize * FURIGANA_TEXT_RATIO);
        int rubyColor = Color.argb(Math.round(alpha * 0.82f), 255, 255, 255);
        float rubyBaseline = baseline - textSize * 0.88f;
        for (RubyAnnotation annotation : annotations) {
            int startIndex = charIndexForCodePointOffset(text, annotation.start);
            int endIndex = charIndexForCodePointOffset(text, annotation.end());
            if (endIndex <= startIndex) {
                continue;
            }
            textPaint.setTypeface(typefaceForLine(line));
            textPaint.setTextSize(textSize);
            float baseLeft = x + textPaint.measureText(text, 0, startIndex);
            float baseWidth = textPaint.measureText(text, startIndex, endIndex);
            configureTextPaint(rubyColor, line.kind, rubySize, false);
            float rubyWidth = textPaint.measureText(annotation.reading);
            canvas.drawText(annotation.reading, baseLeft + baseWidth * 0.5f - rubyWidth * 0.5f, rubyBaseline, textPaint);
        }
    }

    private void drawRubyText(
            Canvas canvas,
            TextSegment segment,
            float cursor,
            float baseline,
            String kind,
            float textSize,
            float fill,
            boolean primary
    ) {
        if (segment == null || segment.rubyText == null || segment.rubyText.trim().isEmpty()) {
            return;
        }
        float rubySize = Math.max(sp(7.4f), textSize * FURIGANA_TEXT_RATIO);
        int alpha = primary ? 214 : 186;
        int color = fill > 0f
                ? Color.argb(alpha, 255, 255, 255)
                : Color.argb(Math.round(alpha * 0.66f), 255, 255, 255);
        configureTextPaint(color, kind, rubySize, fill > 0f);
        float rubyWidth = textPaint.measureText(segment.rubyText);
        float rubyLeft = cursor + segment.width * 0.5f - rubyWidth * 0.5f;
        float rubyBaseline = baseline - textSize * 0.88f;
        canvas.drawText(segment.rubyText, rubyLeft, rubyBaseline, textPaint);
    }

    private List<TextSegment> buildSegments(PreviewLine line) {
        List<LyricsLine.Syllable> syllables = line == null ? Collections.emptyList() : line.syllables;
        if (syllables == null || syllables.isEmpty()) {
            return Collections.emptyList();
        }
        List<LyricsLine.Syllable> renderSyllables = TimedSyllableNormalizer.normalize(syllables);
        List<RubyAnnotation> rubyAnnotations = line.rubyAnnotations();
        List<TextSegment> segments = new ArrayList<>(renderSyllables.size());
        int charOffset = 0;
        for (int index = 0; index < renderSyllables.size(); index++) {
            LyricsLine.Syllable syllable = renderSyllables.get(index);
            if (syllable == null || syllable.text == null || syllable.text.isEmpty()) {
                continue;
            }
            String value = syllable.text;
            int charLength = Math.max(1, value.codePointCount(0, value.length()));
            segments.add(createMeasuredSegment(
                    value,
                    syllable.startTimeMs,
                    syllable.endTimeMs,
                    index,
                    1,
                    rubyForRange(rubyAnnotations, charOffset, charLength)
            ));
            charOffset += charLength;
        }
        return segments;
    }

    private List<TextSegment> textSegments(PreviewLine line) {
        List<TextSegment> cached = textSegmentCache.get(line);
        if (cached != null) {
            return cached;
        }
        List<TextSegment> segments = buildSegments(line);
        textSegmentCache.put(line, segments);
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
        textPaint.setTextSize(Math.max(sp(7.4f), previousSize * FURIGANA_TEXT_RATIO));
        textPaint.setTypeface(previousTypeface);
        float rubyWidth = textPaint.measureText(ruby);
        textPaint.setTextSize(previousSize);
        textPaint.setTypeface(previousTypeface);
        return Math.max(textWidth, rubyWidth + sp(2f));
    }

    private static List<RubyAnnotation> parseRubyAnnotations(String text, String rubyText) {
        if (text == null || text.isEmpty() || rubyText == null || !rubyText.contains("<ruby>")) {
            return Collections.emptyList();
        }
        List<RubyAnnotation> annotations = new ArrayList<>();
        int currentChar = 0;
        int cursor = 0;
        while (cursor < rubyText.length()) {
            int rubyStart = rubyText.indexOf("<ruby>", cursor);
            if (rubyStart < 0) {
                currentChar += codePointCount(MARKUP_TAG_PATTERN.matcher(rubyText.substring(cursor)).replaceAll(""));
                break;
            }
            String before = rubyText.substring(cursor, rubyStart);
            currentChar += codePointCount(MARKUP_TAG_PATTERN.matcher(before).replaceAll(""));

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

        return plainRubyText(rubyText).equals(text) ? annotations : Collections.emptyList();
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

    private float segmentFillFraction(TextSegment segment, long positionMs) {
        if (positionMs >= segment.endTimeMs) {
            return 1f;
        }
        if (positionMs <= segment.startTimeMs || segment.endTimeMs <= segment.startTimeMs) {
            return 0f;
        }
        return clamp((positionMs - segment.startTimeMs) / (float) (segment.endTimeMs - segment.startTimeMs));
    }

    private int activeSegmentIndex(List<TextSegment> segments, long positionMs) {
        int fallbackIndex = -1;
        long fallbackEnd = Long.MIN_VALUE;
        int nextIndex = -1;
        long nextStart = Long.MAX_VALUE;
        for (TextSegment segment : segments) {
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
        if (fallbackIndex >= 0 && positionMs - fallbackEnd < 2000L) {
            return nextIndex >= 0 ? nextIndex : fallbackIndex;
        }
        return nextIndex >= 0 ? nextIndex : fallbackIndex;
    }

    private KaraokeBounce karaokeBounce(
            TextSegment segment,
            int activeSegmentIndex,
            PreviewLine line,
            long positionMs,
            float textSize
    ) {
        if (!karaokeBounceEffectEnabled || activeSegmentIndex < 0) {
            return KaraokeBounce.IDLE;
        }

        float centerIndex = segment.sourceIndex + Math.max(0, segment.sourceLength - 1) * 0.5f;
        float distance = Math.abs(centerIndex - activeSegmentIndex);
        String bounceKey = segment.bounceKey(line.bounceKeyPrefix());
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

        float offsetY = Math.round((-textSize * 0.23f * waveStrength) * 2f) / 2f;
        float scale = Math.round((1f + 0.055f * waveStrength) * 100f) / 100f;
        return new KaraokeBounce(offsetY, scale, offsetY != 0f || scale != 1f);
    }

    private float baseWaveOffset(String kind, int rowIndex, int segmentIndex, float textSize) {
        long now = System.currentTimeMillis();
        float phase = ((now + rowIndex * 95L + segmentIndex * 62L) % (long) WAVE_PERIOD_MS) / WAVE_PERIOD_MS;
        float wave = (float) Math.sin(phase * Math.PI * 2.0);
        float amplitude = "wave".equals(kind) ? 0.145f : 0.085f;
        float bounce = positiveSin(now + segmentIndex * 42L, 760L) * textSize * 0.018f;
        return wave * textSize * amplitude - bounce;
    }

    private void applyCanvasEffect(Canvas canvas, String kind, float centerX, float y, float textSize, int rowIndex) {
        long now = System.currentTimeMillis() + rowIndex * 73L;
        switch (kind) {
            case "effect": {
                float density = getResources().getDisplayMetrics().density;
                int step = (int) ((now / 45L) % 4L);
                float dx = EFFECT_TRANSLATE_X[step] * density;
                float dy = EFFECT_TRANSLATE_Y[step] * density;
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

    private void configureTextPaint(int color, String kind, float textSize, boolean activeFill) {
        resetPaintEffects();
        textPaint.setTextSize(textSize);
        textPaint.setColor(color);
        textPaint.setAlpha(Color.alpha(color));

        long now = System.currentTimeMillis();
        int alpha = Color.alpha(color);
        switch (kind) {
            case "sparkle": {
                float glow = positiveSin(now, 1180L);
                textPaint.setShadowLayer(textSize * (0.07f + glow * 0.18f), 0f, 0f, withAlpha(activeFill ? color : Color.WHITE, 70 + Math.round(glow * 90f)));
                break;
            }
            case "echo":
                textPaint.setShadowLayer(textSize * 0.12f, textSize * 0.06f, textSize * 0.035f, withAlpha(color, 78));
                break;
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
            case "glitch":
                textPaint.setShadowLayer(0f, textSize * 0.04f, 0f, Color.argb(78, 111, 211, 255));
                break;
            default:
                break;
        }
    }

    private void resetPaintEffects() {
        textPaint.setShader(null);
        textPaint.clearShadowLayer();
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private float sin(long now, long periodMs) {
        return (float) Math.sin((now % periodMs) / (double) periodMs * Math.PI * 2.0);
    }

    private float easeOutCubic(float value) {
        float t = clamp(value);
        return 1f - (float) Math.pow(1f - t, 3.0);
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

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private int charIndexForCodePointOffset(String text, int codePointOffset) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int safeOffset = Math.max(0, Math.min(codePointOffset, text.codePointCount(0, text.length())));
        return text.offsetByCodePoints(0, safeOffset);
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
                builder.append(MARKUP_TAG_PATTERN.matcher(value.substring(cursor)).replaceAll(""));
                break;
            }
            builder.append(MARKUP_TAG_PATTERN.matcher(value.substring(cursor, rubyStart)).replaceAll(""));
            int baseStart = rubyStart + "<ruby>".length();
            int rtStart = value.indexOf("<rt>", baseStart);
            int rtEnd = rtStart < 0 ? -1 : value.indexOf("</rt>", rtStart);
            int rubyEnd = rtEnd < 0 ? -1 : value.indexOf("</ruby>", rtEnd);
            if (rtStart < 0 || rtEnd < 0 || rubyEnd < 0) {
                return MARKUP_TAG_PATTERN.matcher(value).replaceAll("");
            }
            builder.append(value, baseStart, rtStart);
            cursor = rubyEnd + "</ruby>".length();
        }
        return builder.toString();
    }

    private static int codePointCount(String value) {
        return value == null || value.isEmpty() ? 0 : value.codePointCount(0, value.length());
    }

    private static List<String> splitChars(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> chars = new ArrayList<>();
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            chars.add(new String(Character.toChars(codePoint)));
            offset += Character.charCount(codePoint);
        }
        return chars;
    }

    static final class PreviewLine {
        private static final int TYPE_TEXT = 0;
        private static final int TYPE_INTERLUDE = 1;
        private static final int TYPE_LOADING = 2;

        final String text;
        final String rubyText;
        final boolean primary;
        final List<LyricsLine.Syllable> syllables;
        final String kind;
        final int type;
        final String slotId;
        final boolean rubyMarkupMatchesText;
        final int stableRowSeed;
        String cachedBounceKeyPrefix;
        List<RubyAnnotation> cachedRubyAnnotations;

        PreviewLine(String text, boolean primary) {
            this(text, primary, Collections.emptyList());
        }

        PreviewLine(String text, String rubyText, boolean primary, List<LyricsLine.Syllable> syllables, String kind, String slotId) {
            this(text, rubyText, primary, syllables, kind, TYPE_TEXT, slotId);
        }

        PreviewLine(String text, boolean primary, List<LyricsLine.Syllable> syllables) {
            this(text, primary, syllables, "vocal");
        }

        PreviewLine(String text, boolean primary, List<LyricsLine.Syllable> syllables, String kind) {
            this(text, primary, syllables, kind, primary
                    ? AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL
                    : AiLyricsSettings.TYPO_MAIN_PREVIEW_PRONUNCIATION);
        }

        PreviewLine(String text, boolean primary, List<LyricsLine.Syllable> syllables, String kind, String slotId) {
            this(text, "", primary, syllables, kind, TYPE_TEXT, slotId);
        }

        private PreviewLine(String text, boolean primary, List<LyricsLine.Syllable> syllables, String kind, int type) {
            this(text, "", primary, syllables, kind, type, primary
                    ? AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL
                    : AiLyricsSettings.TYPO_MAIN_PREVIEW_PRONUNCIATION);
        }

        private PreviewLine(String text, boolean primary, List<LyricsLine.Syllable> syllables, String kind, int type, String slotId) {
            this(text, "", primary, syllables, kind, type, slotId);
        }

        private PreviewLine(String text, String rubyText, boolean primary, List<LyricsLine.Syllable> syllables, String kind, int type, String slotId) {
            this.text = text == null ? "" : text;
            this.rubyText = rubyText == null ? "" : rubyText;
            this.primary = primary;
            this.syllables = syllables == null ? Collections.emptyList() : new ArrayList<>(syllables);
            this.kind = normalizeKind(kind);
            this.type = type;
            this.slotId = AiLyricsSettings.typographySlotById(slotId).id;
            this.rubyMarkupMatchesText = type == TYPE_TEXT
                    && this.rubyText.contains("<ruby>")
                    && plainRubyText(this.rubyText).equals(this.text);
            int seed = 17;
            seed = seed * 31 + this.text.hashCode();
            seed = seed * 31 + this.rubyText.hashCode();
            seed = seed * 31 + this.kind.hashCode();
            this.stableRowSeed = Math.abs(seed);
        }

        static PreviewLine interlude(String text) {
            return new PreviewLine(text, true, Collections.emptyList(), "vocal", TYPE_INTERLUDE, AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL);
        }

        static PreviewLine loading(String text) {
            return new PreviewLine(text, true, Collections.emptyList(), "vocal", TYPE_LOADING, AiLyricsSettings.TYPO_MAIN_PREVIEW_ORIGINAL);
        }

        boolean hasKaraoke() {
            return type == TYPE_TEXT && !syllables.isEmpty();
        }

        boolean hasRuby() {
            return rubyMarkupMatchesText;
        }

        List<RubyAnnotation> rubyAnnotations() {
            if (cachedRubyAnnotations == null) {
                cachedRubyAnnotations = parseRubyAnnotations(text, rubyText);
            }
            return cachedRubyAnnotations;
        }

        boolean isInterlude() {
            return type == TYPE_INTERLUDE;
        }

        boolean isLoading() {
            return type == TYPE_LOADING;
        }

        boolean isAnimatedVisual() {
            return type == TYPE_INTERLUDE || type == TYPE_LOADING;
        }

        int rowSeed() {
            return stableRowSeed;
        }

        String bounceKeyPrefix() {
            if (cachedBounceKeyPrefix == null) {
                cachedBounceKeyPrefix = type + ":" + stableRowSeed;
            }
            return cachedBounceKeyPrefix;
        }

        private static String normalizeKind(String kind) {
            String value = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
            return value.isEmpty() ? "vocal" : value;
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
        String cachedBounceKeyPrefix;
        String cachedBounceKey;

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
            this.rubyText = rubyText == null ? "" : rubyText.trim();
        }

        String bounceKey(String prefix) {
            boolean samePrefix = prefix == null
                    ? cachedBounceKeyPrefix == null
                    : prefix.equals(cachedBounceKeyPrefix);
            if (cachedBounceKey == null || !samePrefix) {
                cachedBounceKeyPrefix = prefix;
                cachedBounceKey = prefix + ':' + sourceIndex;
            }
            return cachedBounceKey;
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
}
