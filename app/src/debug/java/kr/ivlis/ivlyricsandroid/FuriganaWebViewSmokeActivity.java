package kr.ivlis.ivlyricsandroid;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;

/** Device-only fixture. The runner removes the host bridge and supplies a separate module APK. */
public final class FuriganaWebViewSmokeActivity extends Activity {
    private static final String TAG = "IvLyricsFuriganaSmoke";
    private static final String BRIDGE = "furigana/bridge.html";
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView baseline;
    private FuriganaRepository repository;
    private Context moduleContext;
    private TextView status;
    private boolean complete;
    private boolean baselineFailed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Log.i(TAG, "RUN_STARTED " + getIntent().getStringExtra("run_id"));
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setBackgroundColor(Color.rgb(18, 18, 18));
        status.setPadding(32, 72, 32, 32);
        status.setText("Running isolated furigana WebView fixture");
        setContentView(status);
        main.postDelayed(() -> fail(new AssertionError("Device fixture exceeded 70 seconds")), 70_000L);
        try {
            require(getPackageName().endsWith(".qa"), "isolated QA package");
            boolean hostHasBridge;
            try (InputStream ignored = getApplicationContext().getAssets().open(BRIDGE)) {
                hostHasBridge = true;
            } catch (IOException expected) {
                hostHasBridge = false;
            }
            require(!hostHasBridge, "host Application has no furigana bridge asset");
            File fixture = new File(getCacheDir(), "furigana-module-fixture.apk");
            try (InputStream input = getAssets().open("qa_furigana_module_fixture.apk");
                 FileOutputStream output = new FileOutputStream(fixture)) {
                byte[] buffer = new byte[16_384];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            PackageInfo info = getPackageManager().getPackageArchiveInfo(fixture.getAbsolutePath(), 0);
            require(info != null && info.applicationInfo != null, "module archive metadata readable");
            ApplicationInfo app = info.applicationInfo;
            app.sourceDir = fixture.getAbsolutePath();
            app.publicSourceDir = fixture.getAbsolutePath();
            Resources resources = getPackageManager().getResourcesForApplication(app);
            moduleContext = new ContextWrapper(this) {
                @Override public AssetManager getAssets() { return resources.getAssets(); }
                @Override public Resources getResources() { return resources; }
            };
            try (InputStream input = moduleContext.getAssets().open(BRIDGE)) {
                require(input.read() != -1, "wrapped module AssetManager contains production bridge");
            }
            runBaseline();
        } catch (Throwable error) { fail(error); }
    }

    private void runBaseline() {
        baseline = new WebView(moduleContext);
        baseline.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame() || baselineFailed || complete) return;
                if (!request.getUrl().toString().equals("file:///android_asset/" + BRIDGE)
                        || !(error.getErrorCode() == WebViewClient.ERROR_FILE_NOT_FOUND
                        || error.getDescription().toString().contains("ERR_FILE_NOT_FOUND"))) {
                    fail(new AssertionError("Unexpected baseline failure: " + request.getUrl() + " " + error.getDescription()));
                    return;
                }
                baselineFailed = true;
                Log.i(TAG, "BASELINE_FILE_ASSET_ERROR " + error.getErrorCode() + " " + error.getDescription());
                main.post(() -> {
                    baseline.destroy();
                    baseline = null;
                    runProduction();
                });
            }
            @Override public void onPageFinished(WebView view, String url) {
                main.postDelayed(() -> {
                    if (!baselineFailed && !complete) fail(new AssertionError("Baseline unexpectedly loaded host asset"));
                }, 200L);
            }
        });
        baseline.loadUrl("file:///android_asset/" + BRIDGE);
    }

    private void runProduction() {
        try {
            require(baselineFailed, "old file URL fails despite wrapped module assets");
            repository = new FuriganaRepository(moduleContext);
            TrackSnapshot track = new TrackSnapshot("日本語の歌", "ivLyrics QA", "Synthetic fixture",
                    getPackageName(), "spotify:track:0000000000000000000003", "", 20_000L, 0L,
                    SystemClock.elapsedRealtime(), 1f, false, null, "");
            LyricsResult base = new LyricsResult(Arrays.asList(
                    new LyricsLine(0L, 5_000L, "日本語の歌を聞く", Collections.emptyList())
                            .withSupplements("original pronunciation", "original translation"),
                    new LyricsLine(5_000L, 10_000L, "明日も青い空", Collections.emptyList())),
                    "Synthetic fixture", "Real Kuromoji device test", false);
            repository.loadFurigana(track, base, true, new FuriganaRepository.Callback() {
                @Override public void onFuriganaLoaded(String key, LyricsResult result) {
                    try {
                        checkResult(base, result);
                        Log.i(TAG, "COLD_RUBY " + result.lines.get(0).furiganaText);
                        repository.loadFurigana(track, base, false, new FuriganaRepository.Callback() {
                            @Override public void onFuriganaLoaded(String warmKey, LyricsResult warm) {
                                try {
                                    checkResult(base, warm);
                                    require(result.lines.get(0).furiganaText.equals(warm.lines.get(0).furiganaText),
                                            "warm cache preserves generated ruby");
                                    complete = true;
                                    main.removeCallbacksAndMessages(null);
                                    status.setText("PASS: missing host asset reproduced; module bridge generated Japanese ruby; warm cache passed");
                                    Log.i(TAG, "ALL_CHECKS_PASSED");
                                } catch (Throwable error) { fail(error); }
                            }
                            @Override public void onFuriganaError(String warmKey, String message) { fail(new AssertionError(message)); }
                            @Override public void onFuriganaLog(String warmKey, String message) { Log.i(TAG, message); }
                        });
                    } catch (Throwable error) { fail(error); }
                }
                @Override public void onFuriganaError(String key, String message) { fail(new AssertionError(message)); }
                @Override public void onFuriganaLog(String key, String message) { Log.i(TAG, message); }
            });
            Field field = FuriganaRepository.class.getDeclaredField("webView");
            field.setAccessible(true);
            WebView productionView = (WebView) field.get(repository);
            if (productionView != null) {
                WebViewClient delegate = productionView.getWebViewClient();
                productionView.setWebViewClient(new WebViewClient() {
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                        if (getIntent().getBooleanExtra("cdn_fixture", false)) {
                            String host = request.getUrl().getHost();
                            String path = request.getUrl().getPath();
                            if (("cdn.jsdelivr.net".equals(host) || "unpkg.com".equals(host)) && path != null) {
                                int index = path.indexOf("kuromoji@0.1.2/");
                                if (index >= 0) {
                                    String relative = path.substring(index + "kuromoji@0.1.2/".length());
                                    if (!relative.contains("..") && (relative.equals("build/kuromoji.js")
                                            || relative.startsWith("dict/"))) {
                                        try {
                                            InputStream input = getAssets().open("qa_kuromoji/" + relative);
                                            Log.i(TAG, "VERIFIED_CDN_FIXTURE " + relative);
                                            return new WebResourceResponse(relative.endsWith(".js")
                                                    ? "text/javascript" : "application/octet-stream", null,
                                                    200, "OK", Collections.singletonMap("Access-Control-Allow-Origin", "*"), input);
                                        } catch (IOException error) { Log.e(TAG, "Missing CDN fixture " + relative, error); }
                                    }
                                }
                            }
                        }
                        return delegate.shouldInterceptRequest(view, request);
                    }
                    @Override public void onPageFinished(WebView view, String url) { delegate.onPageFinished(view, url); }
                    @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                        Log.i(TAG, "RESOURCE_ERROR " + request.getUrl() + " " + error.getErrorCode() + " " + error.getDescription());
                        delegate.onReceivedError(view, request, error);
                    }
                });
                productionView.setWebChromeClient(new WebChromeClient() {
                    @Override public boolean onConsoleMessage(ConsoleMessage message) {
                        Log.i(TAG, "CONSOLE " + message.message());
                        return true;
                    }
                });
            }
        } catch (Throwable error) { fail(error); }
    }

    private void checkResult(LyricsResult base, LyricsResult result) {
        require(result != null && result.lines.size() == 2, "production repository completed both rows");
        for (int i = 0; i < 2; i++) {
            LyricsLine line = result.lines.get(i);
            require(line.furiganaText.contains("<ruby>") && line.furiganaText.contains("<rt>"),
                    "real tokenizer generated nonempty ruby row " + i);
            require(line.text.equals(base.lines.get(i).text) && line.startTimeMs == base.lines.get(i).startTimeMs,
                    "original text and timing retained row " + i);
        }
        require("original pronunciation".equals(result.lines.get(0).pronunciationText)
                && "original translation".equals(result.lines.get(0).translationText), "supplements retained");
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        Log.i(TAG, "PASS " + message);
    }

    private void fail(Throwable error) {
        if (complete) return;
        complete = true;
        main.removeCallbacksAndMessages(null);
        status.setText("FAIL: " + error);
        Log.e(TAG, "FIXTURE_FAILED", error);
    }

    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (baseline != null) baseline.destroy();
        if (repository != null) repository.shutdown();
        super.onDestroy();
    }
}
