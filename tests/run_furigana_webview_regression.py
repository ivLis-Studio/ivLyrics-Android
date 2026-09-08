#!/usr/bin/env python3
"""Run the production WebView bootstrap with distinct host/module asset contexts."""
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

work = REPORTS / "furigana-webview"
work.mkdir(parents=True, exist_ok=True)
stubs = {
    "android/content/Context.java": """
package android.content;
import android.content.res.AssetManager;
import java.io.File;
public class Context {
    public final AssetManager assets = new AssetManager();
    public Context application = this;
    public AssetManager getAssets() { return assets; }
    public Context getApplicationContext() { return application; }
    public File getFilesDir() { return new File(System.getProperty("java.io.tmpdir"), "ivlyrics-furigana-fixture"); }
}
""",
    "android/content/res/AssetManager.java": """
package android.content.res;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class AssetManager {
    public final Map<String, String> files = new HashMap<>();
    public int reads;
    public InputStream open(String name) throws IOException {
        reads++;
        if (!files.containsKey(name)) throw new FileNotFoundException(name);
        return new ByteArrayInputStream(files.get(name).getBytes(StandardCharsets.UTF_8));
    }
}
""",
    "android/os/Handler.java": """
package android.os;
import java.util.ArrayDeque;
public final class Handler {
    private static final ArrayDeque<Runnable> READY = new ArrayDeque<>(), DELAYED = new ArrayDeque<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { READY.add(task); return true; }
    public boolean postDelayed(Runnable task, long delay) { DELAYED.add(task); return true; }
    public void removeCallbacks(Runnable task) { READY.remove(task); DELAYED.remove(task); }
    public static void drain() { while (!READY.isEmpty()) READY.remove().run(); }
    public static void timeOut() { while (!DELAYED.isEmpty()) DELAYED.remove().run(); drain(); }
}
""",
    "android/webkit/WebSettings.java": """
package android.webkit;
public class WebSettings {
    public boolean fileAccess = true, contentAccess = true, fileFromFile = true, universalFromFile = true;
    public boolean javascript;
    public void setJavaScriptEnabled(boolean value) { javascript = value; }
    public void setDomStorageEnabled(boolean value) { }
    public void setAllowFileAccess(boolean value) { fileAccess = value; }
    public void setAllowContentAccess(boolean value) { contentAccess = value; }
    public void setAllowFileAccessFromFileURLs(boolean value) { fileFromFile = value; }
    public void setAllowUniversalAccessFromFileURLs(boolean value) { universalFromFile = value; }
}
""",
    "android/webkit/WebViewClient.java": """
package android.webkit;
public class WebViewClient {
    public void onPageFinished(WebView view, String url) { }
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) { }
}
""",
    "android/webkit/WebResourceRequest.java": """
package android.webkit;
public interface WebResourceRequest { boolean isForMainFrame(); }
""",
    "android/webkit/WebResourceError.java": """
package android.webkit;
public class WebResourceError {
    public CharSequence getDescription() { return "fixture page failure"; }
}
""",
    "android/webkit/WebView.java": """
package android.webkit;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class WebView {
    public static final List<WebView> instances = new ArrayList<>();
    public static Context processContext;
    public static boolean throwCreate, throwLoad, throwEvaluate;
    public final WebSettings settings = new WebSettings();
    public final List<String> scripts = new ArrayList<>();
    public final Context context;
    public WebViewClient client;
    public Object bridge;
    public String html, baseUrl, mimeType, encoding;
    public boolean destroyed;
    public WebView(Context context) {
        if (throwCreate) throw new IllegalStateException("fixture WebView creation failure");
        this.context = context;
        instances.add(this);
    }
    public WebSettings getSettings() { return settings; }
    public void addJavascriptInterface(Object bridge, String name) { this.bridge = bridge; }
    public void setWebViewClient(WebViewClient client) { this.client = client; }
    public void loadUrl(String url) {
        // Chromium's native asset handler uses the process application, not this.context.
        try (InputStream input = processContext.getAssets().open(url.replace("file:///android_asset/", ""))) {
            html = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            baseUrl = url;
        } catch (IOException error) {
            fail(true);
        }
    }
    public void loadDataWithBaseURL(String base, String html, String mime, String encoding, String history) {
        if (throwLoad) throw new IllegalStateException("fixture page setup failure");
        this.baseUrl = base; this.html = html; this.mimeType = mime; this.encoding = encoding;
    }
    public void evaluateJavascript(String script, ValueCallback<String> callback) {
        if (throwEvaluate) throw new IllegalStateException("fixture script failure");
        scripts.add(script);
    }
    public void destroy() { destroyed = true; }
    public void finish() { client.onPageFinished(this, baseUrl); }
    public void fail(boolean mainFrame) { client.onReceivedError(this, () -> mainFrame, new WebResourceError()); }
}
""",
}
files = []
for name, content in stubs.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)
    files.append(path)
files += [ROOT / "tests/android/os/Looper.java", ROOT / "tests/FuriganaWebViewRegression.java"]
production = [SHARED / name for name in ("FuriganaRepository.java", "LyricsLine.java", "LyricsResult.java", "LyricsDiskCache.java")]
files += production
test_classpath = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
subprocess.run([java_tool("javac"), "-cp", test_classpath, "-d", str(work), *map(str, files)], check=True)
result = subprocess.run([java_tool("java"), "-cp", test_classpath,
                         "kr.ivlis.ivlyricsandroid.FuriganaWebViewRegression"],
                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=30)
report = "Production WebView bootstrap/callback lifecycle; separate module/host assets, controlled Android stubs. No device/network.\n"
for path in production:
    report += f"{path.name} SHA256 {hashlib.sha256(path.read_bytes()).hexdigest()}\n"
report += result.stdout
(work / "result.txt").write_text(report)
print(report, end="")
raise SystemExit(result.returncode)
