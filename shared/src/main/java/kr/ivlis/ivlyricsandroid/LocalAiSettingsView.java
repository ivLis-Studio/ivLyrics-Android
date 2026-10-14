package kr.ivlis.ivlyricsandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Download, quality, memory and test controls inside the Local AI provider card (both APKs). */
@SuppressLint("ViewConstructor")
final class LocalAiSettingsView extends LinearLayout {
    private final AiLyricsSettings settings;
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
    private final SeekBar quality;
    private final LinearLayout tierLabels;
    private final LinearLayout idleChoices;
    private boolean draggingQuality;
    private boolean confirmRemove;
    private boolean testRunning;
    private boolean testSucceeded;
    private String testText;

    LocalAiSettingsView(Context context, AiLyricsSettings settings) {
        super(context);
        this.settings = settings;
        this.runtime = LocalAiProvider.runtime(context);
        setOrientation(VERTICAL);

        status = text(13f, SettingsAppearance.SECONDARY, AppFonts.regular(context));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(status, matchWrap(0));

        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(SettingsAppearance.ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(SettingsAppearance.CONTROL));
        LayoutParams progressParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(6));
        progressParams.topMargin = dp(8);
        addView(progress, progressParams);

        actions = new LinearLayout(context);
        actions.setOrientation(VERTICAL);
        addView(actions, matchWrap(10));

        testResult = text(12.5f, SettingsAppearance.SECONDARY, AppFonts.regular(context));
        testResult.setLineSpacing(dp(2), 1f);
        addView(testResult, matchWrap(8));

        addView(text(14f, SettingsAppearance.TEXT, AppFonts.semiBold(context), ui("local_ai.quality")), matchWrap(18));
        quality = new SeekBar(context);
        quality.setMax(LocalAiModels.TIERS.size() - 1);
        SettingsAppearance.styleSlider(quality);
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
        TextView hint = text(12f, SettingsAppearance.MUTED, AppFonts.regular(context), ui("local_ai.tier_hint"));
        hint.setLineSpacing(dp(2), 1f);
        addView(hint, matchWrap(10));

        addView(text(14f, SettingsAppearance.TEXT, AppFonts.semiBold(context), ui("local_ai.idle_unload")), matchWrap(18));
        idleChoices = new LinearLayout(context);
        idleChoices.setOrientation(VERTICAL);
        addView(idleChoices, matchWrap(8));

        TextView privacy = text(11.5f, SettingsAppearance.MUTED, AppFonts.regular(context), ui("local_ai.privacy"));
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
            testText = null;
            // A different tier releases the loaded model, as on the desktop.
            applySettings();
        }
        render();
    }

    private void render() {
        LocalAiRuntime.Status snapshot = runtime.status();
        boolean supported = snapshot.unsupportedReason == null;
        status.setText(statusText(snapshot));
        status.setTextColor(snapshot.phase == LocalAiRuntime.Phase.ERROR ? SettingsAppearance.ACCENT
                : snapshot.phase == LocalAiRuntime.Phase.READY ? SettingsAppearance.MINT : SettingsAppearance.SECONDARY);
        boolean downloading = snapshot.phase == LocalAiRuntime.Phase.DOWNLOADING;
        progress.setVisibility(downloading ? VISIBLE : GONE);
        progress.setProgress((int) Math.round(Math.min(1d, snapshot.loadedBytes / (double) snapshot.totalBytes) * 1000));
        renderActions(snapshot, supported);
        testResult.setVisibility(testText == null ? GONE : VISIBLE);
        testResult.setText(testText);
        testResult.setTextColor(testSucceeded ? SettingsAppearance.MINT : SettingsAppearance.ACCENT);
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
                testText = null;
                applySettings();
                runtime.startDownload();
            }), matchWrap(0));
            return;
        }
        actions.addView(button(testRunning ? "…" : ui("local_ai.test"), true, !busy && !testRunning, this::runTest),
                matchWrap(0));
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        actions.addView(row, matchWrap(8));
        if (snapshot.phase == LocalAiRuntime.Phase.READY) {
            row.addView(button(ui("local_ai.unload"), false, true, runtime::unload), weighted(0));
        }
        row.addView(button(ui(confirmRemove ? "local_ai.confirm_remove" : "local_ai.remove"), false, !busy, () -> {
            if (!confirmRemove) {
                confirmRemove = true;
                render();
                return;
            }
            confirmRemove = false;
            testText = null;
            runtime.delete(snapshot.tier);
        }), weighted(row.getChildCount()));
    }

    private void runTest() {
        testRunning = true;
        testText = null;
        applySettings();
        AiLyricsSettings.Snapshot snapshot = settings.snapshot();
        String target = snapshot.resolveTargetLanguage("");
        runtime.testTranslation(target, LocalAiProvider.messages(snapshot.uiLang)).whenComplete((result, failure) ->
                main.post(() -> {
                    testRunning = false;
                    testSucceeded = failure == null;
                    testText = failure == null
                            ? "✓ " + result.sample + " → " + result.line
                                    + String.format(Locale.ROOT, " (%.1fs)", result.seconds)
                            : "✗ " + LocalAiRuntime.describe(cause(failure));
                    render();
                }));
        render();
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
            column.addView(text(12.5f, chosen ? SettingsAppearance.TEXT : SettingsAppearance.SECONDARY,
                    chosen ? AppFonts.semiBold(getContext()) : AppFonts.regular(getContext()), ui(tier.labelKey)));
            column.addView(text(11.5f, SettingsAppearance.MUTED, AppFonts.regular(getContext()),
                    LocalAiModels.formatGigabytes(tier.sizeBytes)), matchWrap(2));
            if (snapshot.isDownloaded(tier)) {
                column.addView(text(11f, SettingsAppearance.MINT, AppFonts.semiBold(getContext()),
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
                        snapshot.unsupportedReason != null ? ui(snapshot.unsupportedReason) : snapshot.error);
            default:
                return ui("local_ai.status_not_downloaded");
        }
    }

    private TextView button(String label, boolean primary, boolean enabled, Runnable action) {
        TextView button = text(13.5f, SettingsAppearance.TEXT, AppFonts.semiBold(getContext()), label);
        button.setTag(primary ? SettingsAppearance.PRIMARY_ACTION : SettingsAppearance.SECONDARY_ACTION);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setFocusable(true);
        SettingsAppearance.styleControls(button);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.5f);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private TextView choice(String label, boolean selected) {
        TextView choice = text(13.5f, selected ? SettingsAppearance.BACKGROUND : SettingsAppearance.SECONDARY,
                AppFonts.semiBold(getContext()), label);
        choice.setGravity(Gravity.CENTER);
        choice.setSelected(selected);
        choice.setSingleLine(true);
        choice.setEllipsize(TextUtils.TruncateAt.END);
        choice.setFocusable(true);
        choice.setPadding(dp(6), 0, dp(6), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(selected ? SettingsAppearance.TEXT : SettingsAppearance.CONTROL);
        background.setCornerRadius(dp(8));
        choice.setBackground(background);
        return choice;
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
