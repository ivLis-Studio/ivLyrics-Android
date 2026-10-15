package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * The settings lab's live preview: a sample song drawn with the current settings (lyrics page, main
 * screen, LP mode, PiP and cultural notes). Sizes are in dp, not sp, so it stays a picture of the
 * player even with a large system font.
 */
final class SettingsPreviewView extends View {
    static final int LYRICS = 0;
    static final int MAIN = 1;
    static final int VINYL = 2;
    static final int PIP = 3;
    static final int ANNOTATION = 4;
    static final int SPEAKERS = 5;

    interface Source {
        AiLyricsSettings.Snapshot snapshot();

        String text(String key);

        String backgroundModeLabel(String mode);

        boolean embedded();
    }

    private static final String TITLE = "夜空のうた";
    private static final String ARTIST = "Sample Artist";
    private static final String PREVIOUS_ORIGINAL = "君の声が聞こえる";
    private static final String PREVIOUS_PRONUNCIATION = "kimi no koe ga kikoeru";
    private static final String ACTIVE_ORIGINAL = "明日もきっと晴れるから";
    private static final String ACTIVE_PRONUNCIATION = "ashita mo kitto hareru kara";

    private final Source source;
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private int kind = LYRICS;
    private boolean vinylAnnotations;

    SettingsPreviewView(Context context, Source source) {
        super(context);
        this.source = source;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setKind(int value) {
        if (kind == value) return;
        kind = value;
        invalidate();
    }

    int kind() {
        return kind;
    }

    void setVinylAnnotations(boolean value) {
        if (vinylAnnotations == value) return;
        vinylAnnotations = value;
        invalidate();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = Math.round(dp(216));
        setMeasuredDimension(width, resolveSize(height, heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        AiLyricsSettings.Snapshot snapshot = source.snapshot();
        if (snapshot == null) return;
        float width = getWidth();
        float height = getHeight();
        clip.reset();
        rect.set(0, 0, width, height);
        clip.addRoundRect(rect, dp(10), dp(10), Path.Direction.CW);
        int saved = canvas.save();
        canvas.clipPath(clip);
        if (kind == PIP) {
            fill.setShader(null);
            fill.setColor(0xFF15171B);
            canvas.drawRect(rect, fill);
            drawPip(canvas, snapshot, width, height);
        } else {
            drawBackground(canvas, snapshot.background, 0, 0, width, height);
            if (kind == MAIN) {
                drawMain(canvas, snapshot, width, height);
            } else if (kind == VINYL) {
                drawVinyl(canvas, snapshot, width, height);
            } else {
                drawLyrics(canvas, snapshot, width, height);
            }
        }
        canvas.restoreToCount(saved);
    }

    // ------------------------------------------------------------------ backgrounds

    private void drawBackground(Canvas canvas, AiLyricsSettings.BackgroundSettings background, float left, float top,
                                float right, float bottom) {
        String mode = background == null ? AiLyricsSettings.BACKGROUND_MODE_GRADIENT
                : AiLyricsSettings.normalizeBackgroundMode(background.mode);
        float width = right - left;
        float height = bottom - top;
        rect.set(left, top, right, bottom);
        fill.setShader(null);
        if (AiLyricsSettings.BACKGROUND_MODE_SOLID.equals(mode)) {
            fill.setColor(parse(background.solidColor, 0xFF1E3A8A));
            canvas.drawRect(rect, fill);
            return;
        }
        if (AiLyricsSettings.BACKGROUND_MODE_VIDEO.equals(mode)) {
            float stripe = dp(12);
            for (float x = left; x < right; x += stripe) {
                fill.setColor(((int) ((x - left) / stripe)) % 2 == 0 ? 0xFF17181C : 0xFF1D1F25);
                canvas.drawRect(x, top, Math.min(right, x + stripe), bottom, fill);
            }
        } else if (AiLyricsSettings.BACKGROUND_MODE_BLUR_GRADIENT.equals(mode)) {
            fill.setColor(0xFF182026);
            canvas.drawRect(rect, fill);
            fill.setShader(new RadialGradient(left + width * 0.3f, top + height * 0.4f, Math.max(1f, width * 0.6f),
                    0xCCC8925A, 0x00C8925A, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, fill);
            fill.setShader(new RadialGradient(left + width * 0.75f, top + height * 0.65f, Math.max(1f, width * 0.6f),
                    0xD94D8480, 0x004D8480, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, fill);
            fill.setShader(null);
        } else {
            fill.setShader(new LinearGradient(left, top, right, bottom,
                    new int[]{0xFFC8925A, 0xFF8C8160, 0xFF4D7472, 0xFF1E2A31},
                    new float[]{0f, 0.34f, 0.64f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(rect, fill);
            fill.setShader(null);
        }
        int brightness = background == null ? 30 : Math.max(0, Math.min(100, background.brightness));
        fill.setColor(Color.argb(Math.round((100 - brightness) / 100f * 0.78f * 255f), 0, 0, 0));
        canvas.drawRect(rect, fill);
    }

    private void drawTag(Canvas canvas, String label, float width) {
        if (label == null || label.isEmpty()) return;
        text.setTypeface(AppFonts.semiBold(getContext()));
        text.setTextSize(dp(11));
        float padding = dp(7);
        float textWidth = Math.min(text.measureText(label), width * 0.5f);
        String shown = TextUtils.ellipsize(label, text, textWidth, TextUtils.TruncateAt.END).toString();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float right = rtl ? dp(10) + textWidth + padding * 2 : width - dp(10);
        float left = right - textWidth - padding * 2;
        rect.set(left, dp(10), right, dp(10) + dp(22));
        fill.setShader(null);
        fill.setColor(0x8C000000);
        canvas.drawRoundRect(rect, dp(4), dp(4), fill);
        text.setColor(0xF2FFFFFF);
        canvas.drawText(shown, left + padding, rect.centerY() - (text.ascent() + text.descent()) / 2f, text);
    }

    // ------------------------------------------------------------------ lyrics page

    private void drawLyrics(Canvas canvas, AiLyricsSettings.Snapshot snapshot, float width, float height) {
        boolean annotation = kind == ANNOTATION;
        drawTag(canvas, annotation ? source.text("setting.cultural_annotations")
                : source.backgroundModeLabel(snapshot.background == null ? "" : snapshot.background.mode), width);
        float padding = dp(16);
        float y = dp(16);
        y += drawText(canvas, TITLE, 15f * percent(snapshot, "lyrics_header_title"),
                weight(snapshot, "lyrics_header_title"), 0xFFFFFFFF, "left", padding, width - padding * 2 - dp(80), y);
        y += dp(3);
        y += drawText(canvas, ARTIST, 11.5f * percent(snapshot, "lyrics_header_artist"),
                weight(snapshot, "lyrics_header_artist"), 0xB3FFFFFF, "left", padding, width - padding * 2 - dp(80), y);
        String align = AiLyricsSettings.normalizeLyricsTextAlignment(snapshot.lyricsTextAlignment);
        boolean colors = snapshot.useSyncCreatorSpeakerColors;
        float lines = height - dp(14) - y;
        y += Math.max(dp(10), Math.min(dp(22), lines * 0.12f));
        float available = width - padding * 2;
        if (!annotation) {
            int previous = colors ? speaker(snapshot, "female1", 0xFFFFFFFF) : 0xFFFFFFFF;
            y += lyricLine(canvas, snapshot, PREVIOUS_ORIGINAL, PREVIOUS_PRONUNCIATION,
                    source.text("settings.preview_translation_1"), previous, 0.42f, -1f, align, padding, available, y);
            y += dp(12);
        }
        int active = colors ? speaker(snapshot, AiLyricsSettings.SPEAKER_COLOR_NORMAL, 0xFFFFFFFF) : 0xFFFFFFFF;
        y += lyricLine(canvas, snapshot, ACTIVE_ORIGINAL, ACTIVE_PRONUNCIATION,
                source.text("settings.preview_translation_2"), active, 1f, 0.5f, align, padding, available, y);
        if (annotation) {
            y += dp(8);
            String family = vinylAnnotations ? snapshot.culturalAnnotationsVinylFontFamily : snapshot.culturalAnnotationsFontFamily;
            int size = vinylAnnotations ? snapshot.culturalAnnotationsVinylFontSize : snapshot.culturalAnnotationsFontSize;
            int weight = vinylAnnotations ? snapshot.culturalAnnotationsVinylFontWeight : snapshot.culturalAnnotationsFontWeight;
            int opacity = vinylAnnotations ? snapshot.culturalAnnotationsVinylOpacity : snapshot.culturalAnnotationsOpacity;
            text.setTypeface(AppFonts.cultural(getContext(), family, weight));
            text.setTextSize(dp(size * 0.85f));
            text.setColor(Color.argb(Math.round(Math.max(0, Math.min(100, opacity)) / 100f * 255f), 255, 255, 255));
            drawWrapped(canvas, source.text("settings.preview_annotation"), align, padding, available, y, 3);
        }
    }

    private float lyricLine(Canvas canvas, AiLyricsSettings.Snapshot snapshot, String original, String pronunciation,
                            String translation, int color, float alpha, float karaoke, String align, float left,
                            float available, float top) {
        float y = top;
        float originalSize = 21f * percent(snapshot, "lyrics_original");
        Typeface originalWeight = weight(snapshot, "lyrics_original");
        if (karaoke >= 0f) {
            text.setTypeface(originalWeight);
            text.setTextSize(dp(originalSize));
            String shown = TextUtils.ellipsize(original, text, available, TextUtils.TruncateAt.END).toString();
            float lineWidth = text.measureText(shown);
            float x = alignedX(align, left, available, lineWidth);
            float baseline = y - text.ascent();
            text.setColor(withAlpha(color, 0.5f));
            canvas.drawText(shown, x, baseline, text);
            int split = Math.max(1, Math.round(shown.length() * karaoke));
            text.setColor(withAlpha(color, alpha));
            canvas.drawText(shown, 0, Math.min(split, shown.length()), x, baseline, text);
            y += text.descent() - text.ascent();
        } else {
            y += drawText(canvas, original, originalSize, originalWeight, withAlpha(color, alpha), align, left, available, y);
        }
        y += dp(2);
        y += drawText(canvas, pronunciation, 12.5f * percent(snapshot, "lyrics_pronunciation"),
                weight(snapshot, "lyrics_pronunciation"), withAlpha(0xFFFFFFFF, 0.72f * alpha), align, left, available, y);
        y += dp(2);
        y += drawText(canvas, translation, 13.5f * percent(snapshot, "lyrics_translation"),
                weight(snapshot, "lyrics_translation"), withAlpha(0xFFFFFFFF, 0.86f * alpha), align, left, available, y);
        return y - top;
    }

    // ------------------------------------------------------------------ main screen

    private void drawMain(Canvas canvas, AiLyricsSettings.Snapshot snapshot, float width, float height) {
        float art = dp(68);
        float top = dp(16);
        rect.set((width - art) / 2f, top, (width + art) / 2f, top + art);
        fill.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                new int[]{0xFFE28B5B, 0xFFB15A63, 0xFF6C4E86}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, dp(8), dp(8), fill);
        fill.setShader(null);
        float y = rect.bottom + dp(12);
        float available = width - dp(32);
        y += drawText(canvas, TITLE, 17f * percent(snapshot, "main_title"), weight(snapshot, "main_title"),
                0xFFFFFFFF, "center", dp(16), available, y);
        y += dp(4);
        y += drawText(canvas, ARTIST, 12.5f * percent(snapshot, "main_artist"), weight(snapshot, "main_artist"),
                0xB3FFFFFF, "center", dp(16), available, y);
        y += dp(10);
        int items = source.embedded() ? AiLyricsSettings.PREVIEW_ITEM_ORIGINAL : snapshot.previewItems;
        if (AiLyricsSettings.previewItemEnabled(items, AiLyricsSettings.PREVIEW_ITEM_ORIGINAL)) {
            y += drawText(canvas, ACTIVE_ORIGINAL, 15f * percent(snapshot, "main_preview_original"),
                    weight(snapshot, "main_preview_original"), 0xFFFFFFFF, "center", dp(16), available, y) + dp(2);
        }
        if (AiLyricsSettings.previewItemEnabled(items, AiLyricsSettings.PREVIEW_ITEM_PRONUNCIATION)) {
            y += drawText(canvas, ACTIVE_PRONUNCIATION, 11.5f * percent(snapshot, "main_preview_pronunciation"),
                    weight(snapshot, "main_preview_pronunciation"), 0xB8FFFFFF, "center", dp(16), available, y) + dp(2);
        }
        if (AiLyricsSettings.previewItemEnabled(items, AiLyricsSettings.PREVIEW_ITEM_TRANSLATION)) {
            drawText(canvas, source.text("settings.preview_translation_2"), 12f * percent(snapshot, "main_preview_translation"),
                    weight(snapshot, "main_preview_translation"), 0xD9FFFFFF, "center", dp(16), available, y);
        }
        float barTop = height - dp(16);
        fill.setColor(0x40FFFFFF);
        rect.set(width * 0.2f, barTop, width * 0.8f, barTop + dp(3));
        canvas.drawRoundRect(rect, dp(2), dp(2), fill);
        fill.setColor(0xE6FFFFFF);
        rect.set(width * 0.2f, barTop, width * 0.2f + width * 0.6f * 0.36f, barTop + dp(3));
        canvas.drawRoundRect(rect, dp(2), dp(2), fill);
    }

    // ------------------------------------------------------------------ LP mode

    private void drawVinyl(Canvas canvas, AiLyricsSettings.Snapshot snapshot, float width, float height) {
        AiLyricsSettings.VinylSettings vinyl = snapshot.vinyl;
        boolean lyrics = vinyl == null || vinyl.lyricsEnabled;
        float areaBottom = lyrics ? height - dp(66) : height - dp(12);
        float centerY = dp(12) + (areaBottom - dp(12)) / 2f;
        float base = Math.min((areaBottom - dp(12)) / 2f, width * 0.3f);
        float radius = base * clampPercent(vinyl == null ? 100 : vinyl.recordSizePercent, 70, 140) / 120f;
        float centerX = width / 2f;
        fill.setShader(null);
        fill.setColor(0xFF0E0E10);
        canvas.drawCircle(centerX, centerY, radius, fill);
        stroke.setShader(null);
        stroke.setStrokeWidth(Math.max(1f, dp(0.6f)));
        stroke.setColor(0x26FFFFFF);
        for (float ring = radius * 0.92f; ring > radius * 0.45f; ring -= radius * 0.09f) {
            canvas.drawCircle(centerX, centerY, ring, stroke);
        }
        float label = radius * 0.36f * clampPercent(vinyl == null ? 100 : vinyl.albumSizePercent, 70, 140) / 100f;
        fill.setShader(new LinearGradient(centerX - label, centerY - label, centerX + label, centerY + label,
                new int[]{0xFFE28B5B, 0xFFB15A63, 0xFF6C4E86}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(centerX, centerY, label, fill);
        fill.setShader(null);
        fill.setColor(0xFF0E0E10);
        canvas.drawCircle(centerX, centerY, Math.max(dp(2), label * 0.12f), fill);
        drawTonearm(canvas, vinyl, centerX, centerY, radius);
        if (lyrics) {
            float y = height - dp(60);
            float available = width - dp(32);
            y += drawText(canvas, ACTIVE_ORIGINAL, 22f * percent(snapshot, "vinyl_original"), weight(snapshot, "vinyl_original"),
                    0xFFFFFFFF, "center", dp(16), available, y) + dp(2);
            y += drawText(canvas, ACTIVE_PRONUNCIATION, 15f * percent(snapshot, "vinyl_pronunciation"),
                    weight(snapshot, "vinyl_pronunciation"), 0xB8FFFFFF, "center", dp(16), available, y) + dp(1);
            drawText(canvas, source.text("settings.preview_translation_2"), 16f * percent(snapshot, "vinyl_translation"),
                    weight(snapshot, "vinyl_translation"), 0xD9FFFFFF, "center", dp(16), available, y);
        }
    }

    private void drawTonearm(Canvas canvas, AiLyricsSettings.VinylSettings vinyl, float centerX, float centerY, float radius) {
        String style = vinyl == null ? AiLyricsSettings.VINYL_TONEARM_STYLE_S : vinyl.tonearmStyle;
        String finish = vinyl == null ? AiLyricsSettings.VINYL_TONEARM_FINISH_WHITE : vinyl.tonearmFinish;
        int color = AiLyricsSettings.VINYL_TONEARM_FINISH_BLACK.equals(finish) ? 0xFF3A3D44
                : AiLyricsSettings.VINYL_TONEARM_FINISH_SILVER.equals(finish) ? 0xFFB9BEC7 : 0xFFF2F2F2;
        float scale = radius * clampPercent(vinyl == null ? 100 : vinyl.tonearmSizePercent, 80, 120) / 100f / 12f;
        float pivotX = centerX + radius * 1.02f;
        float pivotY = centerY - radius * 0.92f;
        Path arm = new Path();
        if (AiLyricsSettings.VINYL_TONEARM_STYLE_LINEAR.equals(style)) {
            arm.moveTo(centerX - radius * 0.9f, centerY - radius * 1.02f);
            arm.lineTo(centerX + radius * 0.9f, centerY - radius * 1.02f);
            arm.moveTo(centerX + radius * 0.2f, centerY - radius * 1.02f);
            arm.lineTo(centerX + radius * 0.2f, centerY - radius * 0.55f);
        } else {
            String data = AiLyricsSettings.VINYL_TONEARM_STYLE_STRAIGHT.equals(style) ? "M0 0 -7 9"
                    : AiLyricsSettings.VINYL_TONEARM_STYLE_J.equals(style) ? "M0 0v6c0 2.5-1.6 4.2-4.6 5"
                    : "M0 0c1.3 3.3.4 6.6-2.6 9.2l-3.2 3";
            Path shape = SettingsKit.PathParser.parse(data);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(pivotX, pivotY);
            shape.transform(matrix, arm);
        }
        stroke.setShader(null);
        stroke.setColor(color);
        stroke.setStrokeWidth(Math.max(dp(1.5f), radius * 0.045f));
        canvas.drawPath(arm, stroke);
        fill.setShader(null);
        fill.setColor(color);
        if (!AiLyricsSettings.VINYL_TONEARM_STYLE_LINEAR.equals(style)) {
            canvas.drawCircle(pivotX, pivotY, Math.max(dp(3), radius * 0.09f), fill);
        }
    }

    // ------------------------------------------------------------------ PiP

    private void drawPip(Canvas canvas, AiLyricsSettings.Snapshot snapshot, float width, float height) {
        String orientation = AiLyricsSettings.normalizePipOrientation(snapshot.pipOrientation);
        float maxHeight = height - dp(32);
        float windowWidth;
        float windowHeight;
        if (AiLyricsSettings.PIP_ORIENTATION_LANDSCAPE.equals(orientation)) {
            windowWidth = Math.min(width * 0.72f, maxHeight * 16f / 9f);
            windowHeight = windowWidth * 9f / 16f;
        } else if (AiLyricsSettings.PIP_ORIENTATION_PORTRAIT.equals(orientation)) {
            windowHeight = maxHeight;
            windowWidth = windowHeight * 9f / 16f;
        } else {
            windowHeight = Math.min(maxHeight, width * 0.6f);
            windowWidth = windowHeight;
        }
        float left = (width - windowWidth) / 2f;
        float top = (height - windowHeight) / 2f;
        int saved = canvas.save();
        Path window = new Path();
        rect.set(left, top, left + windowWidth, top + windowHeight);
        window.addRoundRect(rect, dp(9), dp(9), Path.Direction.CW);
        canvas.clipPath(window);
        drawBackground(canvas, snapshot.background, left, top, left + windowWidth, top + windowHeight);
        String align = AiLyricsSettings.normalizeLyricsTextAlignment(snapshot.pipLyricsTextAlignment);
        float size = AiLyricsSettings.normalizePipLyricsSizePercent(snapshot.pipLyricsSizePercent) / 100f;
        float inner = windowWidth - dp(20);
        float y = top + dp(10);
        if (snapshot.pipShowArtwork) {
            float art = Math.min(windowWidth, windowHeight) * 0.34f;
            float artLeft = alignedX(align, left + dp(10), inner, art);
            rect.set(artLeft, y, artLeft + art, y + art);
            fill.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    new int[]{0xFFE28B5B, 0xFFB15A63, 0xFF6C4E86}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rect, dp(5), dp(5), fill);
            fill.setShader(null);
            y += art + dp(8);
        } else {
            y = top + windowHeight * 0.3f;
        }
        y += drawText(canvas, ACTIVE_ORIGINAL, 9.5f * size, weight(snapshot, "lyrics_original"), 0xFFFFFFFF, align,
                left + dp(10), inner, y);
        drawText(canvas, source.text("settings.preview_translation_2"), 7.5f * size, weight(snapshot, "lyrics_translation"),
                0xD9FFFFFF, align, left + dp(10), inner, y + dp(2));
        canvas.restoreToCount(saved);
        stroke.setShader(null);
        stroke.setStrokeWidth(Math.max(1f, dp(1)));
        stroke.setColor(0x33FFFFFF);
        rect.set(left, top, left + windowWidth, top + windowHeight);
        canvas.drawRoundRect(rect, dp(9), dp(9), stroke);
    }

    // ------------------------------------------------------------------ text helpers

    /** Draws one ellipsized line and returns its height. */
    private float drawText(Canvas canvas, String value, float sizeDp, Typeface typeface, int color, String align,
                           float left, float available, float top) {
        if (value == null || value.isEmpty() || available <= 0) return 0f;
        text.setTypeface(typeface);
        text.setTextSize(dp(sizeDp));
        text.setColor(color);
        String shown = TextUtils.ellipsize(value, text, available, TextUtils.TruncateAt.END).toString();
        float lineWidth = text.measureText(shown);
        canvas.drawText(shown, alignedX(align, left, available, lineWidth), top - text.ascent(), text);
        return text.descent() - text.ascent();
    }

    /** Draws up to {@code maxLines} word-wrapped lines with the paint's current style. */
    private void drawWrapped(Canvas canvas, String value, String align, float left, float available, float top, int maxLines) {
        if (value == null || value.isEmpty()) return;
        float y = top;
        int start = 0;
        for (int line = 0; line < maxLines && start < value.length(); line++) {
            int count = text.breakText(value, start, value.length(), true, available, null);
            if (count <= 0) break;
            int end = start + count;
            if (end < value.length()) {
                int space = value.lastIndexOf(' ', end);
                if (space > start) end = space + 1;
            }
            String part = value.substring(start, end).trim();
            if (line == maxLines - 1 && end < value.length()) {
                part = TextUtils.ellipsize(value.substring(start), text, available, TextUtils.TruncateAt.END).toString();
            }
            canvas.drawText(part, alignedX(align, left, available, text.measureText(part)), y - text.ascent(), text);
            y += (text.descent() - text.ascent()) * 1.15f;
            start = end;
        }
    }

    private static float alignedX(String align, float left, float available, float lineWidth) {
        if (AiLyricsSettings.LYRICS_ALIGN_CENTER.equals(align)) return left + (available - lineWidth) / 2f;
        if (AiLyricsSettings.LYRICS_ALIGN_RIGHT.equals(align)) return left + available - lineWidth;
        return left;
    }

    private float percent(AiLyricsSettings.Snapshot snapshot, String slot) {
        AiLyricsSettings.TypographyStyle style = snapshot.typography == null ? null : snapshot.typography.style(slot);
        return style == null ? 1f : style.sizePercent / 100f;
    }

    private Typeface weight(AiLyricsSettings.Snapshot snapshot, String slot) {
        AiLyricsSettings.TypographyStyle style = snapshot.typography == null ? null : snapshot.typography.style(slot);
        return AppFonts.byWeight(getContext(), style == null ? AiLyricsSettings.TYPO_WEIGHT_SEMIBOLD : style.weight);
    }

    private static int speaker(AiLyricsSettings.Snapshot snapshot, String slot, int fallback) {
        return snapshot.speakerColors == null ? fallback : parse(snapshot.speakerColors.hex(slot), fallback);
    }

    private static float clampPercent(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, float alpha) {
        int base = Color.alpha(color);
        return (color & 0x00FFFFFF) | (Math.round(base * Math.max(0f, Math.min(1f, alpha))) << 24);
    }

    private static int parse(String value, int fallback) {
        try {
            return Color.parseColor(value);
        } catch (RuntimeException error) {
            return fallback;
        }
    }
}

