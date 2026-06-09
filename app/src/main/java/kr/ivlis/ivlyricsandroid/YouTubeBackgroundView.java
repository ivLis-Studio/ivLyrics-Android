package kr.ivlis.ivlyricsandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import java.util.Locale;

public final class YouTubeBackgroundView extends FrameLayout {
    private static final long SYNC_INTERVAL_MS = 500L;
    private static final String HTML_ORIGIN = "https://xpui.app.spotify.com/";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            syncPlaybackToWebView();
            if (enabled && currentInfo != null) {
                handler.postDelayed(this, SYNC_INTERVAL_MS);
            }
        }
    };

    private WebView webView;
    private View dimView;
    private YouTubeBackgroundRepository.VideoInfo currentInfo;
    private String currentVideoId = "";
    private long playerPositionMs;
    private long lastPositionSetElapsedMs;
    private long firstLyricTimeMs;
    private int trackOffsetMs;
    private boolean playing;
    private boolean enabled;
    private boolean playerReady;
    private boolean syncLoopStarted;
    private boolean lastVisibleState;

    public YouTubeBackgroundView(Context context) {
        super(context);
        init(context);
    }

    public YouTubeBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void init(Context context) {
        setVisibility(GONE);
        setAlpha(0f);
        setClickable(false);
        setClipChildren(true);
        setClipToPadding(true);

        webView = new WebView(context);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setOverScrollMode(OVER_SCROLL_NEVER);
        webView.setWebChromeClient(new WebChromeClient());
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        webView.addJavascriptInterface(new Bridge(), "AndroidYouTubeBackground");
        addView(webView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        dimView = new View(context);
        dimView.setBackgroundColor(Color.argb(176, 0, 0, 0));
        dimView.setClickable(false);
        addView(dimView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    void setBackgroundSettings(AiLyricsSettings.BackgroundSettings settings) {
        AiLyricsSettings.BackgroundSettings safeSettings = settings == null
                ? new AiLyricsSettings.BackgroundSettings(AiLyricsSettings.BACKGROUND_MODE_GRADIENT, 30, 20, false, false, "#1e3a8a")
                : settings;
        int dimAlpha = Math.max(42, Math.min(220, Math.round(214f - safeSettings.brightness * 1.28f)));
        dimView.setBackgroundColor(Color.argb(dimAlpha, 0, 0, 0));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            float radius = safeSettings.blur <= 0 ? 0f : Math.min(18f, Math.max(0f, safeSettings.blur * 0.16f));
            webView.setRenderEffect(radius <= 0f
                    ? null
                    : RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        }
    }

    void setVideoBackgroundEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (!enabled) {
            clearVideo();
        } else {
            setVisibility(VISIBLE);
            if (currentInfo == null) {
                setAlpha(0f);
            }
            ensureSyncLoop();
        }
    }

    void loadVideo(YouTubeBackgroundRepository.VideoInfo info) {
        if (info == null || info.youtubeVideoId.isEmpty()) {
            clearVideo();
            return;
        }
        currentInfo = info;
        setVisibility(enabled ? VISIBLE : GONE);
        if (info.youtubeVideoId.equals(currentVideoId)) {
            ensureVisibleState();
            ensureSyncLoop();
            return;
        }
        currentVideoId = info.youtubeVideoId;
        playerReady = false;
        lastVisibleState = false;
        setAlpha(0f);
        webView.loadDataWithBaseURL(
                HTML_ORIGIN,
                buildHtml(info.youtubeVideoId),
                "text/html",
                "UTF-8",
                null
        );
        ensureSyncLoop();
    }

    void setPlaybackState(long playerPositionMs, boolean playing, long firstLyricTimeMs, int trackOffsetMs) {
        this.playerPositionMs = Math.max(0L, playerPositionMs);
        this.lastPositionSetElapsedMs = SystemClock.elapsedRealtime();
        this.playing = playing;
        this.firstLyricTimeMs = Math.max(0L, firstLyricTimeMs);
        this.trackOffsetMs = trackOffsetMs;
        ensureVisibleState();
        ensureSyncLoop();
    }

    void clearVideo() {
        handler.removeCallbacks(syncRunnable);
        syncLoopStarted = false;
        currentInfo = null;
        currentVideoId = "";
        playerReady = false;
        lastVisibleState = false;
        setAlpha(0f);
        setVisibility(GONE);
        if (webView != null) {
            webView.loadUrl("about:blank");
        }
    }

    void destroy() {
        handler.removeCallbacks(syncRunnable);
        syncLoopStarted = false;
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidYouTubeBackground");
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(syncRunnable);
        syncLoopStarted = false;
        super.onDetachedFromWindow();
    }

    private void ensureSyncLoop() {
        if (!enabled || currentInfo == null || syncLoopStarted) {
            return;
        }
        syncLoopStarted = true;
        handler.removeCallbacks(syncRunnable);
        handler.post(syncRunnable);
    }

    private void ensureVisibleState() {
        boolean shouldShow = enabled && currentInfo != null && playerReady && playing;
        if (lastVisibleState == shouldShow) {
            return;
        }
        lastVisibleState = shouldShow;
        animate().cancel();
        if (!shouldShow) {
            animate().alpha(0f).setDuration(180L).start();
            return;
        }
        setVisibility(VISIBLE);
        animate().alpha(1f).setDuration(280L).start();
    }

    private void syncPlaybackToWebView() {
        if (!enabled || currentInfo == null || webView == null) {
            syncLoopStarted = false;
            return;
        }
        long elapsed = playing
                ? Math.max(0L, SystemClock.elapsedRealtime() - lastPositionSetElapsedMs)
                : 0L;
        long currentPlayerPositionMs = playerPositionMs + elapsed;
        double playerSeconds = currentPlayerPositionMs / 1000d;
        double firstLyricSeconds = firstLyricTimeMs / 1000d;
        double offsetSeconds = trackOffsetMs / 1000d;
        double captionStart = currentInfo.captionStartTimeSeconds;
        String script = String.format(
                Locale.US,
                "window.ivLyricsSyncVideo(%f,%s,%f,%f,%s,%f,%s,%s);",
                playerSeconds,
                playing ? "true" : "false",
                firstLyricSeconds,
                offsetSeconds,
                currentInfo.hasCaptionStartTime ? "true" : "false",
                captionStart,
                currentInfo.isAutoMatchedUnknownCaptionStart() ? "true" : "false",
                enabled ? "true" : "false"
        );
        webView.evaluateJavascript(script, null);
    }

    private String buildHtml(String videoId) {
        String safeVideoId = videoId == null ? "" : videoId.replace("\\", "").replace("'", "");
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent;}"
                + "#wrap{position:fixed;inset:0;overflow:hidden;background:transparent;}"
                + "#player{position:absolute;top:50%;left:50%;width:177.7778vh;height:100vh;min-width:100vw;min-height:56.25vw;transform:translate(-50%,-50%);}"
                + "iframe{position:absolute!important;inset:0!important;width:100%!important;height:100%!important;pointer-events:none!important;}</style></head>"
                + "<body><div id=\"wrap\"><div id=\"player\"></div></div>"
                + "<script src=\"https://www.youtube.com/iframe_api\"></script>"
                + "<script>"
                + "var player=null,ready=false,lastCaptionDisable=0;"
                + "function disableCaptions(){try{if(player&&player.unloadModule){player.unloadModule('captions');player.unloadModule('cc');}}catch(e){}"
                + "try{if(player&&player.setOption){player.setOption('captions','track',{});player.setOption('cc','track',{});}}catch(e){}}"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('player',{host:'https://www.youtube-nocookie.com',videoId:'" + safeVideoId + "',"
                + "playerVars:{autoplay:1,controls:0,disablekb:1,fs:0,rel:0,iv_load_policy:3,cc_load_policy:0,mute:1,playsinline:1,origin:'https://xpui.app.spotify.com'},"
                + "events:{onReady:function(e){player=e.target;ready=true;try{player.mute();player.playVideo();}catch(x){}disableCaptions();setTimeout(disableCaptions,250);setTimeout(disableCaptions,1000);setTimeout(disableCaptions,2500);try{AndroidYouTubeBackground.onReady();}catch(x){}},"
                + "onStateChange:function(){disableCaptions();}}});}"
                + "window.ivLyricsSyncVideo=function(playerSeconds,playing,firstLyricSeconds,offsetSeconds,hasCaption,captionStart,autoUnknown,enabled){"
                + "if(!enabled||!player||!ready||!player.getPlayerState)return;"
                + "try{var now=Date.now();if(now-lastCaptionDisable>5000){lastCaptionDisable=now;disableCaptions();}"
                + "var extra=0;if(hasCaption&&!autoUnknown){extra=Number(captionStart||0)-Number(firstLyricSeconds||0);}"
                + "var target=Number(playerSeconds||0)+extra+Number(offsetSeconds||0);"
                + "if(target<0)return;"
                + "if(player.getDuration){var duration=player.getDuration();if(duration>0&&target>=duration){target=target%duration;}}"
                + "var state=player.getPlayerState();"
                + "if(playing){if(state!==1&&state!==3){player.playVideo();}var current=player.getCurrentTime?player.getCurrentTime():0;if(Math.abs(current-target)>0.5){player.seekTo(target,true);}}"
                + "else{if(state===1||state===3){player.pauseVideo();}}"
                + "}catch(e){}};"
                + "</script></body></html>";
    }

    private final class Bridge {
        @JavascriptInterface
        public void onReady() {
            handler.post(() -> {
                playerReady = true;
                ensureVisibleState();
            });
        }
    }
}
