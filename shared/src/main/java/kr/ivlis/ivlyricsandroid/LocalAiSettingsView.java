package kr.ivlis.ivlyricsandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Download, quality, memory and test controls inside the Local AI provider card (both APKs). */
@SuppressLint("ViewConstructor")
final class LocalAiSettingsView extends LinearLayout {
    private static final String TAG = "ivLyricsLocalAi";
    private final AiLyricsSettings settings;
    /** Colours of the redesigned settings screen; null keeps the original dark tokens. */
    private final SettingsPalette palette;
    private final LocalAiRuntime runtime;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable render = this::render;
    private final Runnable listener = () -> {
        main.removeCallbacks(render);
        main.post(render);
    };
    private final TextView status;
    private final ProgressBar progress;
    private final LinearLayout actions;
    private final TextView testResult;
    private final TextView qualityTitle;
    private final SeekBar quality;
    private final LinearLayout tierLabels;
    private final TextView tierHint;
    private final TextView idleTitle;
    private final LinearLayout idleChoices;
    private final TextView privacy;
    private boolean draggingQuality;
    private boolean confirmRemove;
    private boolean testRunning;
    private boolean testSucceeded;
    private String testText;
    private String loggedError;
    private int testToken;
    private CompletableFuture<LocalAiRuntime.TestResult> testFuture;
    private String testLanguage;
    private String testUiLanguage;
    private String testTier;

    LocalAiSettingsView(Context context, AiLyricsSettings settings) {
        this(context, settings, null);
    }

    LocalAiSettingsView(Context context, AiLyricsSettings settings, SettingsPalette palette) {
        super(context);
        this.settings = settings;
        this.palette = palette;
        this.runtime = LocalAiProvider.runtime(context);
        setOrientation(VERTICAL);

        status = text(13f, secondaryColor(), AppFonts.regular(context));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(status, matchWrap(0));

        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(accentColor()));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(controlColor()));
        LayoutParams progressParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(6));
        progressParams.topMargin = dp(8);
        addView(progress, progressParams);

        actions = new LinearLayout(context);
        actions.setOrientation(VERTICAL);
        addView(actions, matchWrap(10));

        testResult = text(12.5f, secondaryColor(), AppFonts.regular(context));
        testResult.setLineSpacing(dp(2), 1f);
        testResult.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(testResult, matchWrap(8));

        // Headings are set in render(), so a card reused after a UI language change follows it.
        qualityTitle = text(14f, textColor(), AppFonts.semiBold(context));
        addView(qualityTitle, matchWrap(18));
        quality = new SeekBar(context);
        quality.setMax(LocalAiModels.TIERS.size() - 1);
        if (palette == null) {
            SettingsAppearance.styleSlider(quality);
        } else {
            quality.setProgressTintList(ColorStateList.valueOf(palette.accent));
            quality.setProgressBackgroundTintList(ColorStateList.valueOf(palette.surface4));
            quality.setThumbTintList(ColorStateList.valueOf(palette.accent));
            quality.setMinimumHeight(dp(48));
        }
        quality.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                if (draggingQuality) {
                    renderTierLabels(LocalAiModels.tierAt(value), runtime.status());
                } else {
                    selectTier(value);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                draggingQuality = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                draggingQuality = false;
                selectTier(seekBar.getProgress());
            }
        });
        LayoutParams sliderParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(44));
        sliderParams.topMargin = dp(4);
        addView(quality, sliderParams);

        tierLabels = new LinearLayout(context);
        tierLabels.setOrientation(HORIZONTAL);
        addView(tierLabels, matchWrap(0));
        tierHint = text(12f, mutedColor(), AppFonts.regular(context));
        tierHint.setLineSpacing(dp(2), 1f);
        addView(tierHint, matchWrap(10));

        idleTitle = text(14f, textColor(), AppFonts.semiBold(context));
        addView(idleTitle, matchWrap(18));
        idleChoices = new LinearLayout(context);
        idleChoices.setOrientation(VERTICAL);
        addView(idleChoices, matchWrap(8));

        privacy = text(smallSize(11.5f), mutedColor(), AppFonts.regular(context));
        privacy.setLineSpacing(dp(2), 1f);
        addView(privacy, matchWrap(16));
        render();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        runtime.addListener(listener);
        applySettings();
        render();
    }

    @Override
    protected void onDetachedFromWindow() {
        clearTest();
        runtime.removeListener(listener);
        main.removeCallbacks(render);
        super.onDetachedFromWindow();
    }

    private void applySettings() {
        runtime.configure(LocalAiModels.tier(settings.localAiTier()), settings.localAiIdleMinutes());
    }

    private void selectTier(int index) {
        LocalAiModels.Tier tier = LocalAiModels.tierAt(index);
        if (!tier.id.equals(settings.localAiTier())) {
            settings.setLocalAiTier(tier.id);
            confirmRemove = false;
            clearTest();
            // A different tier releases the loaded model, as on the desktop.
            applySettings();
        }
        render();
    }

    private void render() {
        AiLyricsSettings.Snapshot settingsSnapshot = settings.snapshot();
        if (testTier != null && (!testTier.equals(settings.localAiTier())
                || !testLanguage.equals(settingsSnapshot.resolveTargetLanguage(""))
                || !testUiLanguage.equals(settingsSnapshot.uiLang))) clearTest();
        LocalAiRuntime.Status snapshot = runtime.status();
        boolean supported = snapshot.unsupportedReason == null;
        qualityTitle.setText(ui("local_ai.quality"));
        tierHint.setText(ui("local_ai.tier_hint"));
        idleTitle.setText(ui("local_ai.idle_unload"));
        privacy.setText(ui("local_ai.privacy"));
        logError(snapshot.error);
        status.setText(statusText(snapshot));
        status.setTextColor(snapshot.phase == LocalAiRuntime.Phase.ERROR ? errorColor()
                : snapshot.phase == LocalAiRuntime.Phase.READY ? okColor() : secondaryColor());
        boolean downloading = snapshot.phase == LocalAiRuntime.Phase.DOWNLOADING;
        progress.setVisibility(downloading ? VISIBLE : GONE);
        progress.setProgress((int) Math.round(Math.min(1d, snapshot.loadedBytes / (double) snapshot.totalBytes) * 1000));
        renderActions(snapshot, supported);
        testResult.setVisibility(testText == null ? GONE : VISIBLE);
        testResult.setText(testText);
        testResult.setTextColor(testSucceeded ? okColor() : errorColor());
        if (!draggingQuality) {
            quality.setProgress(LocalAiModels.TIERS.indexOf(snapshot.tier));
        }
        quality.setEnabled(supported && !snapshot.isBusy());
        quality.setAlpha(quality.isEnabled() ? 1f : 0.5f);
        quality.setContentDescription(ui("local_ai.quality") + ", " + ui(snapshot.tier.labelKey));
        renderTierLabels(draggingQuality ? LocalAiModels.tierAt(quality.getProgress()) : snapshot.tier, snapshot);
        renderIdleChoices();
    }

    private void renderActions(LocalAiRuntime.Status snapshot, boolean supported) {
        actions.removeAllViews();
        if (!supported) {
            return;
        }
        boolean busy = snapshot.isBusy();
        boolean hasModel = snapshot.isDownloaded(snapshot.tier);
        if (snapshot.phase == LocalAiRuntime.Phase.DOWNLOADING) {
            actions.addView(button(ui("local_ai.cancel"), false, true, runtime::cancelDownload), matchWrap(0));
            return;
        }
        if (!hasModel) {
            actions.addView(button(LocalAiModels.format(ui("local_ai.download"), "size",
                    LocalAiModels.formatGigabytes(snapshot.tier.sizeBytes)), true, true, () -> {
                clearTest();
                applySettings();
                runtime.startDownload();
            }), matchWrap(0));
            return;
        }
        actions.addView(button(testRunning ? "…" : ui("local_ai.test"), true, !busy && !testRunning, this::runTest),
                matchWrap(0));
        // Long labels such as the delete confirmation wrap, and the pair stacks when it cannot fit side by side.
        LinearLayout row = new SettingsControlRow(getContext());
        row.setOrientation(HORIZONTAL);
        // Top-aligned: baseline alignment would push a wrapped label below its single-line neighbour.
        row.setBaselineAligned(false);
        actions.addView(row, matchWrap(8));
        if (snapshot.phase == LocalAiRuntime.Phase.READY) {
            row.addView(button(ui("local_ai.unload"), false, true, () -> {
                clearTest();
                runtime.unload();
            }), weighted(0));
        }
        row.addView(button(ui(confirmRemove ? "local_ai.confirm_remove" : "local_ai.remove"), false, !busy, () -> {
            if (!confirmRemove) {
                confirmRemove = true;
                render();
                return;
            }
            confirmRemove = false;
            clearTest();
            runtime.delete(snapshot.tier);
        }), weighted(row.getChildCount()));
    }

    private void runTest() {
        if (testRunning) return;
        clearTest();
        int token = testToken;
        testRunning = true;
        testText = null;
        applySettings();
        AiLyricsSettings.Snapshot snapshot = settings.snapshot();
        String target = snapshot.resolveTargetLanguage("");
        String uiLang = snapshot.uiLang;
        testLanguage = target;
        testUiLanguage = uiLang;
        testTier = settings.localAiTier();
        LocalAiModels.Tier testedTier = LocalAiModels.tier(testTier);
        testFuture = runtime.testTranslation(target, LocalAiProvider.messages(uiLang));
        testFuture.whenComplete((result, failure) ->
                main.post(() -> {
                    if (token != testToken) return;
                    AiLyricsSettings.Snapshot current = settings.snapshot();
                    if (!testTier.equals(settings.localAiTier()) || !target.equals(current.resolveTargetLanguage(""))
                            || !uiLang.equals(current.uiLang)) {
                        clearTest();
                        render();
                        return;
                    }
                    testFuture = null;
                    testRunning = false;
                    testSucceeded = failure == null;
                    if (failure != null) {
                        Log.w(TAG, "Local AI test translation failed", cause(failure));
                    }
                    testText = failure == null
                            ? "✓ " + result.sample + " → " + result.line
                                    + "\n" + LocalAiModels.format(ui("local_ai.test_total_time"), "seconds",
                                            String.format(Locale.ROOT, "%.1f", result.seconds))
                                    + "\n" + performanceText(result, testedTier)
                            : "✗ " + LocalAiProvider.userMessage(cause(failure), uiLang);
                    render();
                }));
        render();
    }

    private void clearTest() {
        testToken++;
        CompletableFuture<LocalAiRuntime.TestResult> previous = testFuture;
        testFuture = null;
        testRunning = false;
        testText = null;
        testTier = null;
        if (previous != null) previous.cancel(true);
    }

    private String performanceText(LocalAiRuntime.TestResult result, LocalAiModels.Tier testedTier) {
        LocalAiTranslator.Performance performance = result.performance;
        Double speed = performance == null ? null : performance.tokensPerSecond();
        String text = speed == null ? ui("local_ai.test_speed_unavailable")
                : LocalAiModels.format(ui("local_ai.test_speed"),
                        "speed", String.format(Locale.ROOT, "%.1f", speed),
                        "tokens", String.valueOf(performance.tokens),
                        "seconds", String.format(Locale.ROOT, "%.2f", performance.seconds))
                        + "\n" + ui("local_ai.test_speed_scope");
        int index = LocalAiModels.TIERS.indexOf(testedTier);
        if (index > 0) text += "\n" + LocalAiModels.format(ui("local_ai.test_smaller_hint"),
                "tier", ui(LocalAiModels.TIERS.get(index - 1).labelKey));
        return text;
    }

    private void renderTierLabels(LocalAiModels.Tier selected, LocalAiRuntime.Status snapshot) {
        tierLabels.removeAllViews();
        int count = LocalAiModels.TIERS.size();
        for (int index = 0; index < count; index++) {
            LocalAiModels.Tier tier = LocalAiModels.TIERS.get(index);
            int gravity = index == 0 ? Gravity.START : index == count - 1 ? Gravity.END : Gravity.CENTER_HORIZONTAL;
            LinearLayout column = new LinearLayout(getContext());
            column.setOrientation(VERTICAL);
            column.setGravity(gravity);
            boolean chosen = tier == selected;
            column.addView(text(12.5f, chosen ? textColor() : secondaryColor(),
                    chosen ? AppFonts.semiBold(getContext()) : AppFonts.regular(getContext()), ui(tier.labelKey)));
            column.addView(text(smallSize(11.5f), mutedColor(), AppFonts.regular(getContext()),
                    LocalAiModels.formatGigabytes(tier.sizeBytes)), matchWrap(2));
            if (snapshot.isDownloaded(tier)) {
                column.addView(text(smallSize(11f), okColor(), AppFonts.semiBold(getContext()),
                        ui("local_ai.tier_downloaded")), matchWrap(2));
            }
            tierLabels.addView(column, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private void renderIdleChoices() {
        idleChoices.removeAllViews();
        int selected = settings.localAiIdleMinutes();
        LinearLayout row = null;
        int[] options = LocalAiModels.IDLE_UNLOAD_MINUTES;
        for (int index = 0; index < options.length; index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(HORIZONTAL);
                idleChoices.addView(row, matchWrap(index == 0 ? 0 : 8));
            }
            int minutes = options[index];
            String label = minutes == 0 ? ui("local_ai.idle_never")
                    : minutes % 60 == 0 ? LocalAiModels.format(ui("local_ai.idle_after_hours"), "hours", String.valueOf(minutes / 60))
                    : LocalAiModels.format(ui("local_ai.idle_after"), "minutes", String.valueOf(minutes));
            TextView choice = choice(label, minutes == selected);
            choice.setOnClickListener(view -> {
                settings.setLocalAiIdleMinutes(minutes);
                applySettings();
                render();
            });
            LayoutParams params = new LayoutParams(0, dp(44), 1f);
            params.leftMargin = index % 2 == 0 ? 0 : dp(8);
            row.addView(choice, params);
        }
    }

    private String statusText(LocalAiRuntime.Status snapshot) {
        switch (snapshot.phase) {
            case DOWNLOADING:
                return LocalAiModels.format(ui("local_ai.status_downloading"),
                        "percent", String.valueOf(LocalAiModels.percent(snapshot.loadedBytes, snapshot.totalBytes)),
                        "loaded", String.valueOf(LocalAiModels.megabytes(snapshot.loadedBytes)),
                        "total", String.valueOf(LocalAiModels.megabytes(snapshot.totalBytes)));
            case DOWNLOADED:
                return ui("local_ai.status_downloaded");
            case LOADING:
                return ui("local_ai.status_loading");
            case READY:
                return LocalAiModels.format(ui("local_ai.status_ready"), "device", snapshot.device);
            case ERROR:
                return LocalAiModels.format(ui("local_ai.status_error"), "error",
                        snapshot.unsupportedReason != null ? ui(snapshot.unsupportedReason)
                                : LocalAiProvider.failureText(snapshot.errorKey == null
                                        ? LocalAiModels.ERROR_TRANSLATE : snapshot.errorKey,
                                        snapshot.errorStatus, settings.snapshot().uiLang));
            default:
                return ui("local_ai.status_not_downloaded");
        }
    }

    /** The card shows what failed in words; the technical detail goes to the log once per failure. */
    private void logError(String detail) {
        if (detail != null && !detail.equals(loggedError)) {
            Log.w(TAG, "Local AI: " + detail);
        }
        loggedError = detail;
    }

    private TextView button(String label, boolean primary, boolean enabled, Runnable action) {
        TextView button = text(13.5f, textColor(), AppFonts.semiBold(getContext()), label);
        button.setGravity(Gravity.CENTER);
        // A translation that does not fit wraps instead of being cut off; one line keeps the 48dp height.
        button.setSingleLine(false);
        button.setMaxLines(4);
        button.setEllipsize(null);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setFocusable(true);
        if (palette == null) {
            button.setTag(primary ? SettingsAppearance.PRIMARY_ACTION : SettingsAppearance.SECONDARY_ACTION);
            SettingsAppearance.styleControls(button);
        } else {
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(8));
            if (primary) {
                background.setColor(palette.ink);
                button.setTextColor(palette.inkText);
            } else {
                background.setColor(palette.surface2);
                background.setStroke(Math.max(1, dp(1)), palette.line2);
                button.setTextColor(palette.text);
            }
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(palette.ripple), background, null));
            button.setTextSize(14.5f);
            button.setMinHeight(dp(48));
            button.setMinimumHeight(dp(48));
            button.setMinWidth(dp(48));
            button.setMinimumWidth(dp(48));
        }
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.5f);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView choice(String label, boolean selected) {
        int color = palette == null
                ? (selected ? SettingsAppearance.BACKGROUND : SettingsAppearance.SECONDARY)
                : (selected ? palette.text : palette.text3);
        TextView choice = text(13.5f, color, AppFonts.semiBold(getContext()), label);
        choice.setGravity(Gravity.CENTER);
        choice.setSelected(selected);
        choice.setSingleLine(true);
        choice.setEllipsize(TextUtils.TruncateAt.END);
        choice.setFocusable(true);
        choice.setPadding(dp(6), 0, dp(6), 0);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(8));
        if (palette == null) {
            background.setColor(selected ? SettingsAppearance.TEXT : SettingsAppearance.CONTROL);
        } else {
            background.setColor(selected ? palette.raise : palette.surface2);
            background.setStroke(Math.max(1, dp(1)), selected ? palette.line3 : palette.line);
        }
        choice.setBackground(background);
        return choice;
    }

    private int textColor() {
        return palette == null ? SettingsAppearance.TEXT : palette.text;
    }

    /** The redesigned settings keep small print at 12sp or larger. */
    private float smallSize(float legacySp) {
        return palette == null ? legacySp : Math.max(12f, legacySp);
    }

    private int secondaryColor() {
        return palette == null ? SettingsAppearance.SECONDARY : palette.text2;
    }

    private int mutedColor() {
        return palette == null ? SettingsAppearance.MUTED : palette.text3;
    }

    private int errorColor() {
        return palette == null ? SettingsAppearance.ACCENT : palette.danger;
    }

    private int okColor() {
        return palette == null ? SettingsAppearance.MINT : palette.ok;
    }

    private int accentColor() {
        return palette == null ? SettingsAppearance.ACCENT : palette.accent;
    }

    private int controlColor() {
        return palette == null ? SettingsAppearance.CONTROL : palette.surface4;
    }

    private TextView text(float sizeSp, int color, Typeface typeface) {
        TextView view = new TextView(getContext());
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setTypeface(typeface);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView text(float sizeSp, int color, Typeface typeface, String value) {
        TextView view = text(sizeSp, color, typeface);
        view.setText(value);
        return view;
    }

    private LayoutParams matchWrap(int topMarginDp) {
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private LayoutParams weighted(int index) {
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = index == 0 ? 0 : dp(8);
        return params;
    }

    private int dp(float value) {
        return SettingsAppearance.dp(getContext(), value);
    }

    private String ui(String key) {
        return AppI18n.t(settings.snapshot().uiLang, key);
    }

    private static Throwable cause(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
