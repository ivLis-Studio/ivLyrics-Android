package kr.ivlis.ivlyricsandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Motion lyrics: the desktop kinetic-typography presentation, shown over the player background.
 *
 * <p>The presentation itself is the desktop one. assets/motion/MotionLyricsPage.html is built in
 * the ivLyrics repository (.github/scripts/build_motion_mobile.mjs) from the desktop scripts and
 * runs them unchanged in a web view. This view sends the page the song and the playback position,
 * passes on its two requests (an AI direction, the direction cache) and reports how its camera
 * turns the background. Edit the presentation there, not here.
 */
@SuppressLint("ViewConstructor")
final class MotionLyricsModeView extends FrameLayout {
    static final String PAGE_ASSET = "motion/MotionLyricsPage.html";
    // The page is supplied whole; the origin only gives its font requests an https page to come from.
    private static final String PAGE_ORIGIN = "https://motion.ivlyrics.app/";
    private static final String BRIDGE_NAME = "ivLyricsMotionNative";
    private static final long CONTROLS_HIDE_DELAY_MS = 3000L;
    private static final long FADE_MS = 260L;
    private static final int CLOSE_BUTTON_DP = 40;
    /** The options and close buttons sit in the top end corner; the page keeps its small print clear of them. */
    private static final int CLOSE_BUTTON_RESERVE_DP = 100;
    private static final int BUTTON_GAP_DP = 8;
    // The viewer's comfort (the desktop app's motion intensity, screen rotation and photosensitivity-safe settings).
    private static final String PREFERENCES = "ivlyrics_motion_lyrics";
    static final String[] INTENSITIES = {"calm", "normal", "wild"};
    static final String[] ROTATIONS = {"full", "gentle", "off"};

    interface Listener {
        void onClose();

        /** Ask the AI provider; answer on the main thread with its raw text, or with an error. */
        void onDirectionRequested(String prompt, DirectionCallback callback);

        /** A direction the page accepted earlier, or an empty string. */
        String onCacheRead(String key);

        void onCacheWrite(String key, String value);

        /** How the page's camera turns the background: degrees, scale, and an offset in dp. */
        void onBackgroundFrame(float rotate, float scale, float x, float y);

        void onLog(String message);
    }

    interface DirectionCallback {
        void onAnswer(String answer, String error);
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final WebView webView;
    private final TextView closeButton;
    private final TextView optionsButton;
    private final LinearLayout optionsPanel;
    private final TextView redirectView;
    private final TextView intensityLabel;
    private final TextView rotationLabel;
    private final TextView[] intensityChips = new TextView[INTENSITIES.length];
    private final TextView[] rotationChips = new TextView[ROTATIONS.length];
    private final TextView safeView;
    private final SharedPreferences preferences;
    private final TextView noticeView;
    private final Runnable hideControls = this::fadeControlsOut;

    private boolean pageReady;
    private String wantedSong = "";
    private String sentSong;
    private String sentOptions;
    private String songUri = "";
    private boolean songLoaded;
    /** The track the page last judged, and its verdict. */
    private String judgedUri = "";
    private boolean judgedPlayable = true;
    private long positionMs;
    private boolean playing;
    private float rate = 1f;
    private String directingText = "";
    private String failedText = "";
    private boolean aiAvailable;
    private int insetLeft;
    private int insetTop;
    private int insetRight;
    private int insetBottom;
    /** A quarter turn of the stage inside an upright window: 90 (clockwise), -90, or 0 for none. */
    private int quarterTurn;
    private boolean shown;
    private String motionIntensity;
    private String motionRotation;
    private boolean motionSafe;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    MotionLyricsModeView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setVisibility(GONE);
        setAlpha(0f);

        webView = new WebView(context);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setOverScrollMode(OVER_SCROLL_NEVER);
        webView.setFocusable(false);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(false);
        // The page sets its own sizes from the screen; the system font scale must not move them.
        settings.setTextZoom(100);
        webView.addJavascriptInterface(new Bridge(), BRIDGE_NAME);
        addView(webView, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Touches belong to the mode's own controls, not to the page.
        View touchLayer = new View(context);
        touchLayer.setOnClickListener(view -> onStageTapped());
        addView(touchLayer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        noticeView = new TextView(context);
        noticeView.setTextColor(Color.argb(230, 255, 255, 255));
        noticeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        noticeView.setGravity(Gravity.CENTER);
        noticeView.setPadding(dp(16), dp(10), dp(16), dp(10));
        noticeView.setBackground(pill(Color.argb(118, 0, 0, 0)));
        noticeView.setVisibility(GONE);
        LayoutParams noticeParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        noticeParams.setMargins(dp(24), 0, dp(24), 0);
        addView(noticeView, noticeParams);

        closeButton = new TextView(context);
        closeButton.setText("\u2715");
        closeButton.setTextColor(Color.argb(235, 255, 255, 255));
        closeButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setIncludeFontPadding(false);
        closeButton.setBackground(pill(Color.argb(108, 0, 0, 0)));
        closeButton.setFocusable(true);
        closeButton.setOnClickListener(view -> listener.onClose());
        addView(closeButton, new LayoutParams(dp(CLOSE_BUTTON_DP), dp(CLOSE_BUTTON_DP), Gravity.TOP | Gravity.END));

        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        motionIntensity = known(preferences.getString("intensity", "normal"), INTENSITIES, "normal");
        motionRotation = known(preferences.getString("rotation", "full"), ROTATIONS, "full");
        motionSafe = preferences.getBoolean("safe", false);

        optionsButton = new TextView(context);
        optionsButton.setText("\u22EF");
        optionsButton.setTextColor(Color.argb(235, 255, 255, 255));
        optionsButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18f);
        optionsButton.setGravity(Gravity.CENTER);
        optionsButton.setIncludeFontPadding(false);
        optionsButton.setBackground(pill(Color.argb(108, 0, 0, 0)));
        optionsButton.setFocusable(true);
        optionsButton.setOnClickListener(view -> toggleOptions());
        addView(optionsButton, new LayoutParams(dp(CLOSE_BUTTON_DP), dp(CLOSE_BUTTON_DP), Gravity.TOP | Gravity.END));

        // The options panel turns with the stage (the Spotify module's sheet turns it a quarter), unlike a popup.
        optionsPanel = new LinearLayout(context);
        optionsPanel.setOrientation(LinearLayout.VERTICAL);
        optionsPanel.setPadding(dp(14), dp(12), dp(14), dp(14));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(Color.argb(222, 16, 16, 20));
        panelBackground.setCornerRadius(dp(18));
        optionsPanel.setBackground(panelBackground);
        optionsPanel.setClickable(true);
        optionsPanel.setVisibility(GONE);
        redirectView = chip(context);
        redirectView.setOnClickListener(view -> redirect());
        optionsPanel.addView(redirectView, rowParams(0));
        intensityLabel = sectionLabel(context);
        optionsPanel.addView(intensityLabel, rowParams(12));
        optionsPanel.addView(chipRow(context, intensityChips, index -> setComfort(INTENSITIES[index], motionRotation, motionSafe)), rowParams(6));
        rotationLabel = sectionLabel(context);
        optionsPanel.addView(rotationLabel, rowParams(12));
        optionsPanel.addView(chipRow(context, rotationChips, index -> setComfort(motionIntensity, ROTATIONS[index], motionSafe)), rowParams(6));
        safeView = chip(context);
        safeView.setOnClickListener(view -> setComfort(motionIntensity, motionRotation, !motionSafe));
        optionsPanel.addView(safeView, rowParams(12));
        addView(optionsPanel, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));
        updateOptionStates();
        placeCloseButton();

        String page = readPage(context);
        if (page.isEmpty()) {
            listener.onLog("motion lyrics page is missing from the assets");
        } else {
            webView.loadDataWithBaseURL(PAGE_ORIGIN, page, "text/html", "UTF-8", null);
        }
    }

    void setUiText(String close, String unavailable, String directing, String failed) {
        closeButton.setContentDescription(close);
        noticeView.setText(unavailable);
        directingText = directing == null ? "" : directing;
        failedText = failed == null ? "" : failed;
        flush();
    }

    /** The options panel's text: its button, "direct again", and the comfort settings (three choices each). */
    void setOptionsText(String options, String redirect, String intensity, String[] intensities,
                        String rotation, String[] rotations, String safe) {
        optionsButton.setContentDescription(options);
        redirectView.setText("\u21BB  " + redirect);
        intensityLabel.setText(intensity);
        rotationLabel.setText(rotation);
        for (int index = 0; index < intensityChips.length && index < intensities.length; index++) {
            intensityChips[index].setText(intensities[index]);
        }
        for (int index = 0; index < rotationChips.length && index < rotations.length; index++) {
            rotationChips[index].setText(rotations[index]);
        }
        safeView.setTag(safe);
        updateOptionStates();
    }

    /** An AI provider that answers prompts is set up. */
    void setAiAvailable(boolean available) {
        if (aiAvailable != available) {
            aiAvailable = available;
            flush();
        }
    }

    /**
     * The song for the page ({@link MotionLyricsSong#json}), or an empty string for none.
     * lyricsLoaded is false while the track's lyrics are still being fetched.
     */
    void setSong(String uri, String json, boolean lyricsLoaded) {
        songUri = uri == null ? "" : uri;
        songLoaded = lyricsLoaded;
        wantedSong = json == null ? "" : json;
        updateNotice();
        flush();
    }

    /** The lyric position in milliseconds (sync offsets applied); the page runs its own clock between reports. */
    void setPlayback(long positionMs, boolean playing, float rate) {
        this.positionMs = Math.max(0L, positionMs);
        this.playing = playing;
        this.rate = rate > 0f ? rate : 1f;
        flush();
    }

    /**
     * Lays the stage out as a wide screen inside a window that stays upright, for a host that
     * cannot turn its window: at 90 the top of the stage lies along the window's right edge, at
     * -90 along its left edge, and 0 fills the window as it is. The stage must be a child of a
     * FrameLayout the size of the window.
     */
    void setQuarterTurn(int degrees, int windowWidth, int windowHeight) {
        quarterTurn = degrees;
        ViewGroup.LayoutParams current = getLayoutParams();
        if (current instanceof LayoutParams) {
            LayoutParams params = (LayoutParams) current;
            int width = degrees == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : windowHeight;
            int height = degrees == 0 ? ViewGroup.LayoutParams.MATCH_PARENT : windowWidth;
            if (params.width != width || params.height != height || params.gravity != Gravity.CENTER) {
                params.width = width;
                params.height = height;
                params.gravity = Gravity.CENTER;
                setLayoutParams(params);
            }
        }
        setRotation(degrees);
        placeCloseButton();
        flush();
    }

    void show(boolean animate) {
        animate().cancel();
        shown = true;
        webView.onResume();
        setVisibility(VISIBLE);
        if (animate) {
            animate().alpha(1f).setDuration(FADE_MS).start();
        } else {
            setAlpha(1f);
        }
        showControls();
        closeButton.requestFocus();
    }

    void hide(boolean animate, Runnable afterwards) {
        handler.removeCallbacks(hideControls);
        animate().cancel();
        shown = false;
        optionsPanel.setVisibility(GONE);
        // The page stops its frame loop; it follows the clock again when the mode returns.
        flush();
        Runnable finish = () -> {
            setVisibility(GONE);
            webView.onPause();
            listener.onBackgroundFrame(0f, 1f, 0f, 0f);
            if (afterwards != null) {
                afterwards.run();
            }
        };
        if (animate) {
            animate().alpha(0f).setDuration(FADE_MS).withEndAction(finish).start();
        } else {
            setAlpha(0f);
            finish.run();
        }
    }

    void release() {
        handler.removeCallbacksAndMessages(null);
        webView.removeJavascriptInterface(BRIDGE_NAME);
        removeView(webView);
        webView.destroy();
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        int left;
        int top;
        int right;
        int bottom;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Insets safe = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            left = safe.left;
            top = safe.top;
            right = safe.right;
            bottom = safe.bottom;
        } else {
            left = insets.getSystemWindowInsetLeft();
            top = insets.getSystemWindowInsetTop();
            right = insets.getSystemWindowInsetRight();
            bottom = insets.getSystemWindowInsetBottom();
        }
        if (left != insetLeft || top != insetTop || right != insetRight || bottom != insetBottom) {
            insetLeft = left;
            insetTop = top;
            insetRight = right;
            insetBottom = bottom;
            placeCloseButton();
            flush();
        }
        return super.onApplyWindowInsets(insets);
    }

    /** The window's insets as the stage sees them: a turned stage has the window's top at one side. */
    private int stageInsetLeft() {
        return quarterTurn > 0 ? insetTop : quarterTurn < 0 ? insetBottom : insetLeft;
    }

    private int stageInsetTop() {
        return quarterTurn > 0 ? insetRight : quarterTurn < 0 ? insetLeft : insetTop;
    }

    private int stageInsetRight() {
        return quarterTurn > 0 ? insetBottom : quarterTurn < 0 ? insetTop : insetRight;
    }

    private int stageInsetBottom() {
        return quarterTurn > 0 ? insetLeft : quarterTurn < 0 ? insetRight : insetBottom;
    }

    private void placeCloseButton() {
        LayoutParams params = (LayoutParams) closeButton.getLayoutParams();
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        params.setMargins(
                (rtl ? stageInsetLeft() : 0) + dp(16), stageInsetTop() + dp(14),
                (rtl ? 0 : stageInsetRight()) + dp(16), 0);
        closeButton.setLayoutParams(params);
        // The options button beside it, and its panel under both.
        int beside = dp(16 + CLOSE_BUTTON_DP + BUTTON_GAP_DP);
        LayoutParams options = (LayoutParams) optionsButton.getLayoutParams();
        options.setMargins(
                rtl ? stageInsetLeft() + beside : 0, stageInsetTop() + dp(14),
                rtl ? 0 : stageInsetRight() + beside, 0);
        optionsButton.setLayoutParams(options);
        LayoutParams panel = (LayoutParams) optionsPanel.getLayoutParams();
        panel.setMargins(
                (rtl ? stageInsetLeft() : 0) + dp(16), stageInsetTop() + dp(14 + CLOSE_BUTTON_DP + BUTTON_GAP_DP),
                (rtl ? 0 : stageInsetRight()) + dp(16), dp(16));
        optionsPanel.setLayoutParams(panel);
    }

    /** Only once the song's lyrics have loaded and the page has turned them down. */
    private void updateNotice() {
        boolean show = songLoaded && !songUri.isEmpty() && songUri.equals(judgedUri) && !judgedPlayable;
        noticeView.setVisibility(show ? VISIBLE : GONE);
    }

    private void showControls() {
        handler.removeCallbacks(hideControls);
        for (View button : new View[]{closeButton, optionsButton}) {
            button.animate().cancel();
            button.setVisibility(VISIBLE);
            button.animate().alpha(1f).setDuration(180L).start();
        }
        handler.postDelayed(hideControls, CONTROLS_HIDE_DELAY_MS);
    }

    private void fadeControlsOut() {
        // A remote keeps its focus on the buttons, so they stay; so does an open options panel.
        if (optionsPanel.getVisibility() == VISIBLE || ((closeButton.isFocused() || optionsButton.isFocused()) && !isInTouchMode())) {
            return;
        }
        for (View button : new View[]{closeButton, optionsButton}) {
            button.animate().alpha(0f).setDuration(400L)
                    .withEndAction(() -> button.setVisibility(INVISIBLE)).start();
        }
    }

    private void setOptionsOpen(boolean open) {
        optionsPanel.setVisibility(open ? VISIBLE : GONE);
        if (open) {
            optionsPanel.bringToFront();
            redirectView.requestFocus();
        }
        showControls();
    }

    private void toggleOptions() {
        setOptionsOpen(optionsPanel.getVisibility() != VISIBLE);
    }

    /** A tap on the stage closes the options panel, or brings the buttons back. */
    private void onStageTapped() {
        if (optionsPanel.getVisibility() == VISIBLE) {
            setOptionsOpen(false);
        } else {
            showControls();
        }
    }

    /** "Direct again": a new edit of the current song (a new AI take, or another built-in one). */
    private void redirect() {
        if (pageReady) {
            run("ivLyricsMotionHost.redirect()");
        }
        setOptionsOpen(false);
    }

    private void setComfort(String intensity, String rotation, boolean safe) {
        motionIntensity = known(intensity, INTENSITIES, "normal");
        motionRotation = known(rotation, ROTATIONS, "full");
        motionSafe = safe;
        preferences.edit()
                .putString("intensity", motionIntensity)
                .putString("rotation", motionRotation)
                .putBoolean("safe", motionSafe)
                .apply();
        updateOptionStates();
        showControls();
        flush();
    }

    private void updateOptionStates() {
        for (int index = 0; index < intensityChips.length; index++) {
            markChip(intensityChips[index], INTENSITIES[index].equals(motionIntensity));
        }
        for (int index = 0; index < rotationChips.length; index++) {
            markChip(rotationChips[index], ROTATIONS[index].equals(motionRotation));
        }
        Object safe = safeView.getTag();
        safeView.setText((motionSafe ? "\u2713  " : "") + (safe == null ? "" : safe));
        markChip(safeView, motionSafe);
    }

    private void markChip(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setBackground(pill(selected ? Color.argb(235, 255, 255, 255) : Color.argb(46, 255, 255, 255)));
        chip.setTextColor(selected ? Color.rgb(16, 16, 20) : Color.argb(235, 255, 255, 255));
    }

    private TextView chip(Context context) {
        TextView chip = new TextView(context);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setPadding(dp(14), dp(8), dp(14), dp(8));
        chip.setFocusable(true);
        chip.setClickable(true);
        markChip(chip, false);
        return chip;
    }

    private TextView sectionLabel(Context context) {
        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        label.setTextColor(Color.argb(170, 255, 255, 255));
        return label;
    }

    private interface ChipChoice {
        void choose(int index);
    }

    private LinearLayout chipRow(Context context, TextView[] chips, ChipChoice choice) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < chips.length; index++) {
            TextView chip = chip(context);
            int chosen = index;
            chip.setOnClickListener(view -> choice.choose(chosen));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(index < chips.length - 1 ? dp(6) : 0);
            row.addView(chip, params);
            chips[index] = chip;
        }
        return row;
    }

    private LinearLayout.LayoutParams rowParams(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        return params;
    }

    private static String known(String value, String[] choices, String fallback) {
        for (String choice : choices) {
            if (choice.equals(value)) {
                return choice;
            }
        }
        return fallback;
    }

    /** Sends what changed. Options go first: the page reads them when it takes a song. */
    private void flush() {
        if (!pageReady) {
            return;
        }
        String options = optionsJson();
        if (!options.equals(sentOptions)) {
            sentOptions = options;
            run("ivLyricsMotionHost.configure(" + options + ")");
        }
        if (!wantedSong.equals(sentSong)) {
            sentSong = wantedSong;
            run("ivLyricsMotionHost.setTrack(" + (wantedSong.isEmpty() ? "null" : wantedSong) + ")");
        }
        run(String.format(Locale.US, "ivLyricsMotionHost.setPlayback({position:%d,playing:%s,rate:%s})",
                positionMs, playing && shown, rate));
    }

    /** Insets reach the page in CSS pixels, which are dp. */
    private String optionsJson() {
        float density = getResources().getDisplayMetrics().density;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float reserve = CLOSE_BUTTON_RESERVE_DP;
        StringBuilder out = new StringBuilder(256);
        out.append("{\"labels\":{\"directing\":");
        MotionLyricsSong.string(out, directingText);
        out.append(",\"failed\":");
        MotionLyricsSong.string(out, failedText);
        out.append("},\"ai\":").append(aiAvailable).append(",\"background\":true");
        out.append(",\"motion\":{\"intensity\":\"").append(motionIntensity)
                .append("\",\"rotation\":\"").append(motionRotation)
                .append("\",\"safe\":").append(motionSafe).append('}');
        out.append(",\"insets\":{");
        out.append(String.format(Locale.US, "\"top\":%.1f,\"right\":%.1f,\"bottom\":%.1f,\"left\":%.1f",
                stageInsetTop() / density,
                stageInsetRight() / density + (rtl ? 0f : reserve),
                stageInsetBottom() / density,
                stageInsetLeft() / density + (rtl ? reserve : 0f)));
        return out.append("}}").toString();
    }

    private void receive(String text) {
        JSONObject message;
        try {
            message = new JSONObject(text);
        } catch (JSONException error) {
            return;
        }
        switch (message.optString("type")) {
            case "ready":
                pageReady = true;
                sentSong = null;
                sentOptions = null;
                flush();
                break;
            case "eligible":
                judgedUri = message.optString("uri");
                judgedPlayable = message.optBoolean("value");
                updateNotice();
                break;
            case "background":
                // A frame still on its way when the mode closed must not turn the player's background.
                if (shown) {
                    listener.onBackgroundFrame(
                            (float) message.optDouble("rotate", 0d),
                            (float) message.optDouble("scale", 1d),
                            (float) message.optDouble("x", 0d),
                            (float) message.optDouble("y", 0d));
                }
                break;
            case "direct": {
                int id = message.optInt("id");
                listener.onDirectionRequested(message.optString("prompt"), (answer, error) -> resolve(id, answer, error));
                break;
            }
            case "cacheGet":
                resolve(message.optInt("id"), listener.onCacheRead(message.optString("key")), null);
                break;
            case "cacheSet":
                listener.onCacheWrite(message.optString("key"), message.optString("value"));
                break;
            case "error":
                listener.onLog("motion lyrics page: " + message.optString("message"));
                break;
            default:
                break;
        }
    }

    private void resolve(int id, String value, String error) {
        StringBuilder script = new StringBuilder("ivLyricsMotionHost.resolve(").append(id);
        if (error == null) {
            script.append(",{\"ok\":true,\"value\":");
            MotionLyricsSong.string(script, value);
        } else {
            script.append(",{\"ok\":false,\"error\":");
            MotionLyricsSong.string(script, error);
        }
        run(script.append("})").toString());
    }

    private void run(String script) {
        webView.evaluateJavascript(script, null);
    }

    private static String readPage(Context context) {
        try (InputStream input = context.getAssets().open(PAGE_ASSET);
             ByteArrayOutputStream output = new ByteArrayOutputStream(1 << 19)) {
            byte[] buffer = new byte[1 << 14];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "";
        }
    }

    private static GradientDrawable pill(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(1000f);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** The page calls this from the web view's own thread. */
    private final class Bridge {
        @JavascriptInterface
        public void postMessage(String message) {
            handler.post(() -> receive(message));
        }
    }
}
