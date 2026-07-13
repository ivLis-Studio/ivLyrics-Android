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
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;

public final class YouTubeBackgroundView extends FrameLayout {
    private static final long SYNC_INTERVAL_MS = 500L;
    private static final String HTML_ORIGIN = "https://xpui.app.spotify.com/";
    private static final String YOUTUBE_COMMAND_FEEDBACK_SCRIPT =
            "(function(){function install(){if(document.getElementById('ivlyrics-hide-command-feedback'))return true;"
                    + "var root=document.head||document.documentElement;if(!root)return false;"
                    + "var style=document.createElement('style');style.id='ivlyrics-hide-command-feedback';"
                    + "style.textContent='.ytp-bezel,.ytp-bezel-text-wrapper,.ytp-pause-overlay,.ytwPlayerMiddleControlsHost{display:none!important;visibility:hidden!important;opacity:0!important}';"
                    + "root.appendChild(style);return true;}if(!install())document.addEventListener('DOMContentLoaded',install,{once:true});})();";
    private static final Pattern[] AD_URL_PATTERNS = new Pattern[]{
            Pattern.compile("doubleclick\\.net", Pattern.CASE_INSENSITIVE),
            Pattern.compile("googlesyndication\\.com", Pattern.CASE_INSENSITIVE),
            Pattern.compile("googleads\\.g\\.doubleclick\\.net", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pagead(?!.*youtube\\.com/iframe)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("tpc\\.googlesyndication\\.com", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pubads\\.g\\.doubleclick\\.net", Pattern.CASE_INSENSITIVE),
            Pattern.compile("securepubads\\.g\\.doubleclick\\.net", Pattern.CASE_INSENSITIVE),
            Pattern.compile("gvt\\d+\\.com/ads", Pattern.CASE_INSENSITIVE),
            Pattern.compile("manifest\\.googlevideo\\.com/api/manifest/ads", Pattern.CASE_INSENSITIVE),
            Pattern.compile("googlevideo\\.com/videoplayback.*[&?](ctier|oad|adformat)=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("googlevideo\\.com/initplayback.*[&?](ctier|oad|adformat)=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("youtube\\.com/pagead", Pattern.CASE_INSENSITIVE),
            Pattern.compile("youtube\\.com/ptracking", Pattern.CASE_INSENSITIVE),
            Pattern.compile("youtube\\.com/api/stats/(ads|qoe|watchtime|playback)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("youtubei/v1/log_event", Pattern.CASE_INSENSITIVE),
            Pattern.compile("youtubei/v1/player.*adformat", Pattern.CASE_INSENSITIVE),
            Pattern.compile("youtube\\.com/get_video_info.*adformat", Pattern.CASE_INSENSITIVE),
            Pattern.compile("youtube\\.com/yva_", Pattern.CASE_INSENSITIVE),
            Pattern.compile("yt\\d?\\.ggpht\\.com/ad", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ytimg\\.com/.*ad", Pattern.CASE_INSENSITIVE),
            Pattern.compile("yt3\\.ggpht\\.com/ytc/.*ad", Pattern.CASE_INSENSITIVE),
            Pattern.compile("s0\\.2mdn\\.net", Pattern.CASE_INSENSITIVE),
            Pattern.compile("gstaticadssl\\.googleapis\\.com", Pattern.CASE_INSENSITIVE)
    };

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
    private long suppressHardSyncUntilElapsedMs;
    private int videoScalePercent = 100;

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
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request == null || request.getUrl() == null ? "" : request.getUrl().toString();
                return shouldBlockUrl(url) ? emptyBlockedResponse() : super.shouldInterceptRequest(view, request);
            }

            @Override
            @SuppressWarnings("deprecation")
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return shouldBlockUrl(url) ? emptyBlockedResponse() : super.shouldInterceptRequest(view, url);
            }
        });
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    YOUTUBE_COMMAND_FEEDBACK_SCRIPT,
                    Collections.singleton("https://www.youtube-nocookie.com")
            );
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
                ? new AiLyricsSettings.BackgroundSettings(AiLyricsSettings.BACKGROUND_MODE_GRADIENT, 30, 20, false, false, "#1e3a8a", 100)
                : settings;
        int dimAlpha = Math.max(42, Math.min(220, Math.round(214f - safeSettings.brightness * 1.28f)));
        dimView.setBackgroundColor(Color.argb(dimAlpha, 0, 0, 0));
        setVideoScalePercent(safeSettings.videoScale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            int effectiveBlur = Math.min(200, Math.max(0, safeSettings.blur * 2));
            float radius = effectiveBlur <= 0 ? 0f : Math.min(36f, Math.max(0f, effectiveBlur * 0.16f));
            webView.setRenderEffect(radius <= 0f
                    ? null
                    : RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        }
    }

    private void setVideoScalePercent(int scalePercent) {
        int safeScale = Math.max(100, Math.min(180, scalePercent));
        if (videoScalePercent == safeScale) {
            return;
        }
        videoScalePercent = safeScale;
        applyVideoScaleToWebView();
    }

    private void applyVideoScaleToWebView() {
        if (webView == null) {
            return;
        }
        String scale = String.format(Locale.US, "%.3f", videoScalePercent / 100f);
        webView.evaluateJavascript(
                "document.documentElement.style.setProperty('--video-scale','" + scale + "');",
                null
        );
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
        applyVideoScaleToWebView();
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

    void suppressHardSyncFor(long durationMs) {
        long until = SystemClock.elapsedRealtime() + Math.max(0L, durationMs);
        suppressHardSyncUntilElapsedMs = Math.max(suppressHardSyncUntilElapsedMs, until);
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
        boolean allowHardSync = SystemClock.elapsedRealtime() >= suppressHardSyncUntilElapsedMs;
        String script = String.format(
                Locale.US,
                "window.ivLyricsSyncVideo(%f,%s,%f,%f,%s,%f,%s,%s,%s);",
                playerSeconds,
                playing ? "true" : "false",
                firstLyricSeconds,
                offsetSeconds,
                currentInfo.hasCaptionStartTime ? "true" : "false",
                captionStart,
                currentInfo.isAutoMatchedUnknownCaptionStart() ? "true" : "false",
                enabled ? "true" : "false",
                allowHardSync ? "true" : "false"
        );
        webView.evaluateJavascript(script, null);
    }

    private static boolean shouldBlockUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        for (Pattern pattern : AD_URL_PATTERNS) {
            if (pattern.matcher(url).find()) {
                return true;
            }
        }
        return false;
    }

    private static WebResourceResponse emptyBlockedResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                new ByteArrayInputStream(new byte[0])
        );
    }

    private String buildHtml(String videoId) {
        String safeVideoId = videoId == null ? "" : videoId.replace("\\", "").replace("'", "");
        String initialScale = String.format(Locale.US, "%.3f", videoScalePercent / 100f);
        return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:transparent;}"
                + ":root{--video-scale:" + initialScale + ";}"
                + "#wrap{position:fixed;inset:0;overflow:hidden;background:transparent;}"
                + "#stage{position:absolute;top:50%;left:50%;width:100vw;height:56.25vw;min-width:177.7778vh;min-height:100vh;transform:translate3d(-50%,-50%,0) scale(var(--video-scale));overflow:hidden;background:transparent;transition:transform .18s ease;will-change:transform;}"
                + "#player,#stage iframe{position:absolute!important;inset:0!important;width:100%!important;height:100%!important;transform:none!important;pointer-events:none!important;border:0!important;outline:0!important;}"
                + "*{-webkit-tap-highlight-color:transparent!important;user-select:none!important;-webkit-user-select:none!important;}</style></head>"
                + "<body><div id=\"wrap\"><div id=\"stage\"><div id=\"player\"></div></div></div>"
                + "<script src=\"https://www.youtube.com/iframe_api\"></script>"
                + "<script>"
                + "var player=null,ready=false,lastCaptionDisable=0,lastSeekAt=0,lastDesired={videoId:'" + safeVideoId + "',startSeconds:0};"
                + "var adPatterns=[/doubleclick\\.net/i,/googlesyndication\\.com/i,/googleads\\.g\\.doubleclick\\.net/i,/pagead(?!.*youtube\\.com\\/iframe)/i,/tpc\\.googlesyndication\\.com/i,/pubads\\.g\\.doubleclick\\.net/i,/securepubads\\.g\\.doubleclick\\.net/i,/gvt\\d+\\.com\\/ads/i,/manifest\\.googlevideo\\.com\\/api\\/manifest\\/ads/i,/googlevideo\\.com\\/videoplayback.*[&?](ctier|oad|adformat)=/i,/googlevideo\\.com\\/initplayback.*[&?](ctier|oad|adformat)=/i,/youtube\\.com\\/pagead/i,/youtube\\.com\\/ptracking/i,/youtube\\.com\\/api\\/stats\\/(ads|qoe|watchtime|playback)/i,/youtubei\\/v1\\/log_event/i,/youtubei\\/v1\\/player.*adformat/i,/youtube\\.com\\/get_video_info.*adformat/i,/youtube\\.com\\/yva_/i,/yt\\d?\\.ggpht\\.com\\/ad/i,/ytimg\\.com\\/.*ad/i,/yt3\\.ggpht\\.com\\/ytc\\/.*ad/i,/s0\\.2mdn\\.net/i,/gstaticadssl\\.googleapis\\.com/i];"
                + "function isAdUrl(u){try{u=(typeof u==='string'?u:(u&&u.url)||'');return !!u&&adPatterns.some(function(p){return p.test(u);});}catch(e){return false;}}"
                + "(function patchRequests(){try{var of=window.fetch;if(of&&!of.__ivl){var nf=function(r,i){var u=typeof r==='string'?r:(r&&r.url);if(isAdUrl(u))return Promise.resolve(new Response('',{status:204,statusText:'No Content'}));return of.call(this,r,i);};nf.__ivl=1;window.fetch=nf;}}catch(e){}"
                + "try{var xo=XMLHttpRequest.prototype.open,xs=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.open=function(m,u){this.__ivlUrl=u;return xo.apply(this,arguments);};XMLHttpRequest.prototype.send=function(b){if(isAdUrl(this.__ivlUrl)){setTimeout(()=>{try{this.dispatchEvent(new Event('error'));this.onerror&&this.onerror(new Event('error'));}catch(e){}},0);return;}return xs.apply(this,arguments);};}catch(e){}"
                + "try{var sb=navigator.sendBeacon&&navigator.sendBeacon.bind(navigator);if(sb){navigator.sendBeacon=function(u,d){return isAdUrl(u)?true:sb(u,d);};}}catch(e){}"
                + "try{var oo=window.open;if(oo){window.open=function(u,t,f){return isAdUrl(u)?null:oo.call(this,u,t,f);};}}catch(e){}})();"
                + "function disableCaptions(){try{if(player&&player.unloadModule){player.unloadModule('captions');player.unloadModule('cc');}}catch(e){}"
                + "try{if(player&&player.setOption){player.setOption('captions','track',{});player.setOption('cc','track',{});}}catch(e){}}"
                + "function sanitizeIframe(){try{var f=player&&player.getIframe&&player.getIframe();if(!f)return;f.setAttribute('referrerpolicy','origin');f.setAttribute('allow','autoplay; encrypted-media; picture-in-picture');f.setAttribute('tabindex','-1');f.setAttribute('aria-hidden','true');f.style.position='absolute';f.style.inset='0';f.style.width='100%';f.style.height='100%';f.style.transform='none';f.style.pointerEvents='none';f.style.border='0';}catch(e){}}"
                + "function isAdPlayback(){try{if(player&&player.getAdState&&player.getAdState()===1)return true;}catch(e){}try{return [105,106,107,108,109,110,111].indexOf(player&&player.getPlayerState&&player.getPlayerState())>=0;}catch(e){return false;}}"
                + "function reloadDesired(){try{if(player&&player.loadVideoById&&lastDesired&&lastDesired.videoId){player.loadVideoById({videoId:lastDesired.videoId,startSeconds:lastDesired.startSeconds||0,suggestedQuality:'default'});return true;}}catch(e){}return false;}"
                + "function suppressAds(){if(!isAdPlayback())return;try{player.mute&&player.mute();}catch(e){}try{player.setPlaybackRate&&player.setPlaybackRate(16);}catch(e){}try{player.skipAd&&player.skipAd();return;}catch(e){}try{var d=player.getDuration&&player.getDuration();if(d>0){player.seekTo(Math.max(d-0.1,0),true);return;}}catch(e){}reloadDesired();}"
                + "function restorePlaybackRate(){try{if(player&&player.setPlaybackRate&&player.getPlaybackRate&&player.getPlaybackRate()!==1)player.setPlaybackRate(1);}catch(e){}}"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('player',{host:'https://www.youtube-nocookie.com',videoId:'" + safeVideoId + "',"
                + "playerVars:{autoplay:1,controls:0,disablekb:1,fs:0,rel:0,iv_load_policy:3,cc_load_policy:0,mute:1,playsinline:1,modestbranding:1,autohide:1,showinfo:0,enablecastapi:0,allowfullscreen:0,disable_polymer:1,suppress_ads:1,adformat:'0_0',widget_referrer:'https://xpui.app.spotify.com',origin:'https://xpui.app.spotify.com',fflags:'disable_persistent_ads=true&kevlar_allow_multistep_video_ads=false&enable_desktop_ad_controls=false&html5_disable_ads=true&disable_new_pause_state3_player_ads=true&player_ads_enable_gcf=false&web_player_disable_afa=true&preskip_button_style_ads_backend=false&html5_player_enable_ads_client=false'},"
                + "events:{onReady:function(e){player=e.target;ready=true;sanitizeIframe();try{player.mute();player.playVideo();}catch(x){}disableCaptions();setTimeout(disableCaptions,250);setTimeout(disableCaptions,1000);setTimeout(disableCaptions,2500);try{AndroidYouTubeBackground.onReady();}catch(x){}},"
                + "onStateChange:function(e){sanitizeIframe();disableCaptions();suppressAds();if(!isAdPlayback())restorePlaybackRate();},onError:function(){reloadDesired();}}});setInterval(function(){if(player&&ready){sanitizeIframe();suppressAds();}},400);}"
                + "window.ivLyricsSyncVideo=function(playerSeconds,playing,firstLyricSeconds,offsetSeconds,hasCaption,captionStart,autoUnknown,enabled,allowHardSync){"
                + "if(!enabled||!player||!ready||!player.getPlayerState)return;"
                + "try{var now=Date.now();if(now-lastCaptionDisable>5000){lastCaptionDisable=now;disableCaptions();}"
                + "suppressAds();"
                + "var extra=0;if(hasCaption&&!autoUnknown){extra=Number(captionStart||0)-Number(firstLyricSeconds||0);}"
                + "var target=Number(playerSeconds||0)+extra+Number(offsetSeconds||0);"
                + "if(target<0)return;"
                + "if(player.getDuration){var duration=player.getDuration();if(duration>0&&target>=duration){target=target%duration;}}"
                + "lastDesired={videoId:'" + safeVideoId + "',startSeconds:target};"
                + "var state=player.getPlayerState();"
                + "if(playing){if(state!==1&&state!==3&&!isAdPlayback()){player.playVideo();}var current=player.getCurrentTime?player.getCurrentTime():0;var diff=Math.abs(current-target);if(allowHardSync&&diff>3.25&&(diff>6||now-lastSeekAt>2200)){lastSeekAt=now;player.seekTo(target,true);}}"
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
