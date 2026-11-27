#!/usr/bin/env python3
"""Pronunciation Lab provider: the production WebView bridge, provider dispatch and stored settings.

Three host fixtures, each with its own controlled Android stubs. No device, WebView, CDN or AI
provider is contacted:
- PronunciationLabRepository's hidden page: module/host asset contexts, ready queueing, ids, timeouts,
  cancellation, page and renderer failures, and the blocking call from a worker thread;
- AiLyricsRepository's line pronunciation through a synthetic AI provider and a fake page;
- AiLyricsSettings' stored provider order and enabled state, with in-memory preferences.
"""
import hashlib
import subprocess

from regression_runtime import ROOT, SHARED, REPORTS, android_jar, classpath, compiled_classes, java_tool, json_jar

WEBVIEW_STUBS = {
    "android/content/Context.java": """
package android.content;
import android.content.res.AssetManager;
public class Context {
    public final AssetManager assets = new AssetManager();
    public Context application = this;
    public AssetManager getAssets() { return assets; }
    public Context getApplicationContext() { return application; }
}
""",
    "android/content/res/AssetManager.java": """
package android.content.res;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class AssetManager {
    public final Map<String, String> files = new HashMap<>();
    public volatile int reads;
    public InputStream open(String name) throws IOException {
        reads++;
        if (!files.containsKey(name)) throw new FileNotFoundException(name);
        return new ByteArrayInputStream(files.get(name).getBytes(StandardCharsets.UTF_8));
    }
}
""",
    "android/os/Looper.java": """
package android.os;
/** The test's thread is the main thread; worker threads have no looper. */
public final class Looper {
    private static final Looper MAIN = new Looper();
    private static volatile Thread mainThread;
    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return Thread.currentThread() == mainThread ? MAIN : null; }
    public static void prepareMainThread() { mainThread = Thread.currentThread(); }
}
""",
    "android/os/Handler.java": """
package android.os;
import java.util.*;
/** A main-thread queue the test drains; delayed tasks run only when the test lets time pass. */
public final class Handler {
    private static final Object LOCK = new Object();
    private static final ArrayDeque<Runnable> READY = new ArrayDeque<>();
    private static final List<Runnable> DELAYED = new ArrayList<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { synchronized (LOCK) { READY.add(task); LOCK.notifyAll(); } return true; }
    public boolean postDelayed(Runnable task, long delay) { synchronized (LOCK) { DELAYED.add(task); } return true; }
    public void removeCallbacks(Runnable task) {
        synchronized (LOCK) { READY.removeIf(queued -> queued == task); DELAYED.removeIf(queued -> queued == task); }
    }
    public static int delayed() { synchronized (LOCK) { return DELAYED.size(); } }
    public static void clear() { synchronized (LOCK) { READY.clear(); DELAYED.clear(); } }
    public static void drain() {
        while (true) {
            Runnable task;
            synchronized (LOCK) { task = READY.poll(); }
            if (task == null) return;
            task.run();
        }
    }
    public static void timeOut() {
        List<Runnable> due;
        synchronized (LOCK) { due = new ArrayList<>(DELAYED); DELAYED.clear(); }
        for (Runnable task : due) task.run();
        drain();
    }
    public static boolean awaitPost(long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        synchronized (LOCK) {
            while (READY.isEmpty()) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) return false;
                LOCK.wait(left);
            }
            return true;
        }
    }
}
""",
    "android/webkit/WebSettings.java": """
package android.webkit;
public class WebSettings {
    public boolean javascript, domStorage, fileAccess = true, contentAccess = true, fileFromFile = true, universalFromFile = true;
    public void setJavaScriptEnabled(boolean value) { javascript = value; }
    public void setDomStorageEnabled(boolean value) { domStorage = value; }
    public void setAllowFileAccess(boolean value) { fileAccess = value; }
    public void setAllowContentAccess(boolean value) { contentAccess = value; }
    public void setAllowFileAccessFromFileURLs(boolean value) { fileFromFile = value; }
    public void setAllowUniversalAccessFromFileURLs(boolean value) { universalFromFile = value; }
}
""",
    "android/webkit/WebViewClient.java": """
package android.webkit;
public class WebViewClient {
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
    public void onPageFinished(WebView view, String url) { }
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) { }
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) { return false; }
}
""",
    "android/webkit/WebResourceRequest.java": """
package android.webkit;
public interface WebResourceRequest { boolean isForMainFrame(); }
""",
    "android/webkit/WebResourceError.java": """
package android.webkit;
public class WebResourceError { public CharSequence getDescription() { return "fixture page failure"; } }
""",
    "android/webkit/RenderProcessGoneDetail.java": """
package android.webkit;
public class RenderProcessGoneDetail {
    private final boolean crashed;
    public RenderProcessGoneDetail(boolean crashed) { this.crashed = crashed; }
    public boolean didCrash() { return crashed; }
}
""",
    "android/webkit/WebView.java": """
package android.webkit;
import android.content.Context;
import java.lang.reflect.Method;
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
    public String bridgeName, html, baseUrl, mimeType, encoding, historyUrl;
    public boolean destroyed;
    public WebView(Context context) {
        if (throwCreate) throw new IllegalStateException("fixture: no WebView package");
        this.context = context;
        instances.add(this);
    }
    public WebSettings getSettings() { return settings; }
    public void addJavascriptInterface(Object bridge, String name) { this.bridge = bridge; this.bridgeName = name; }
    public void setWebViewClient(WebViewClient client) { this.client = client; }
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding, String historyUrl) {
        if (throwLoad) throw new IllegalStateException("fixture: page setup failure");
        if (bridge == null) throw new AssertionError("the bridge must be added before the page loads");
        this.baseUrl = baseUrl; this.html = data; this.mimeType = mimeType; this.encoding = encoding; this.historyUrl = historyUrl;
    }
    public void evaluateJavascript(String script, ValueCallback<String> callback) {
        if (destroyed) throw new AssertionError("script sent to a destroyed WebView: " + script);
        if (throwEvaluate) throw new IllegalStateException("fixture: script failure");
        scripts.add(script);
    }
    public void destroy() { destroyed = true; }
    // What Chromium would do.
    public void post(String json) throws Exception {
        Method method = bridge.getClass().getDeclaredMethod("postMessage", String.class);
        method.setAccessible(true);
        method.invoke(bridge, json);
    }
    public boolean crash(boolean didCrash) { return client.onRenderProcessGone(this, new RenderProcessGoneDetail(didCrash)); }
    public void fail(boolean mainFrame) { client.onReceivedError(this, () -> mainFrame, new WebResourceError()); }
    public boolean navigate() { return client.shouldOverrideUrlLoading(this, () -> true); }
}
""",
}

DISPATCH_STUBS = {
    "android/os/Handler.java": """
package android.os;
import java.util.concurrent.ConcurrentLinkedQueue;
public final class Handler {
    private static final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();
    public Handler(Looper looper) { }
    public boolean post(Runnable task) { queue.add(task); return true; }
    public boolean postDelayed(Runnable task, long delay) { return post(task); }
    public void removeCallbacks(Runnable task) { queue.removeIf(queued -> queued == task); }
    public static void drain() { Runnable task; while ((task = queue.poll()) != null) task.run(); }
}
""",
    "android/os/SystemClock.java": """
package android.os;
public final class SystemClock {
    public static long uptimeMillis() { return 1000L; }
    public static long elapsedRealtime() { return 1000L; }
    public static long elapsedRealtimeNanos() { return 1_000_000_000L; }
}
""",
}

SETTINGS_STUBS = {
    "android/content/Context.java": """
package android.content;
import java.util.*;
public class Context {
    public static final int MODE_PRIVATE = 0;
    private final Map<String, MemoryPreferences> preferences = new HashMap<>();
    public Context getApplicationContext() { return this; }
    public SharedPreferences getSharedPreferences(String name, int mode) {
        return preferences.computeIfAbsent(name, ignored -> new MemoryPreferences());
    }
}
""",
    "android/content/MemoryPreferences.java": """
package android.content;
import java.util.*;
/** In-memory preferences that notify listeners like Android's, so settings drop cached snapshots. */
public final class MemoryPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();
    private final List<OnSharedPreferenceChangeListener> listeners = new ArrayList<>();
    public Map<String, ?> getAll() { return new HashMap<>(values); }
    public String getString(String key, String fallback) { Object value = values.get(key); return value instanceof String ? (String) value : fallback; }
    @SuppressWarnings("unchecked") public Set<String> getStringSet(String key, Set<String> fallback) { Object value = values.get(key); return value instanceof Set ? (Set<String>) value : fallback; }
    public int getInt(String key, int fallback) { Object value = values.get(key); return value instanceof Integer ? (Integer) value : fallback; }
    public long getLong(String key, long fallback) { Object value = values.get(key); return value instanceof Long ? (Long) value : fallback; }
    public float getFloat(String key, float fallback) { Object value = values.get(key); return value instanceof Float ? (Float) value : fallback; }
    public boolean getBoolean(String key, boolean fallback) { Object value = values.get(key); return value instanceof Boolean ? (Boolean) value : fallback; }
    public boolean contains(String key) { return values.containsKey(key); }
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.add(listener); }
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) { listeners.remove(listener); }
    public Editor edit() {
        return new Editor() {
            private final Map<String, Object> changes = new LinkedHashMap<>();
            private boolean clear;
            public Editor putString(String key, String value) { changes.put(key, value); return this; }
            public Editor putStringSet(String key, Set<String> value) { changes.put(key, value); return this; }
            public Editor putInt(String key, int value) { changes.put(key, value); return this; }
            public Editor putLong(String key, long value) { changes.put(key, value); return this; }
            public Editor putFloat(String key, float value) { changes.put(key, value); return this; }
            public Editor putBoolean(String key, boolean value) { changes.put(key, value); return this; }
            public Editor remove(String key) { changes.put(key, null); return this; }
            public Editor clear() { clear = true; return this; }
            public boolean commit() {
                if (clear) values.clear();
                for (Map.Entry<String, Object> change : changes.entrySet()) {
                    if (change.getValue() == null) values.remove(change.getKey());
                    else values.put(change.getKey(), change.getValue());
                }
                for (String key : changes.keySet()) {
                    for (OnSharedPreferenceChangeListener listener : new ArrayList<>(listeners)) {
                        listener.onSharedPreferenceChanged(MemoryPreferences.this, key);
                    }
                }
                return true;
            }
            public void apply() { commit(); }
        };
    }
}
""",
    "kr/ivlis/ivlyricsandroid/SecureStringStore.java": """
package kr.ivlis.ivlyricsandroid;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
/** Keystore-free stand-in: the regression covers provider order and enabled state, not secrets. */
final class SecureStringStore {
    private final Map<String, String> values = new HashMap<>();
    SecureStringStore(Context context) { }
    synchronized String getString(String key, String fallback) { return values.getOrDefault(key, fallback); }
    synchronized void putString(String key, String value) { if (value == null || value.isEmpty()) values.remove(key); else values.put(key, value); }
    synchronized void remove(String key) { values.remove(key); }
    synchronized void migrateFrom(SharedPreferences source, String... keys) { }
}
""",
}


def run(name, stubs, sources, main_class, production):
    work = REPORTS / "pronunciation-lab" / name
    work.mkdir(parents=True, exist_ok=True)
    files = []
    for relative, content in stubs.items():
        path = work / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        files.append(path)
    test_classpath = classpath(work, json_jar(), compiled_classes("shared"), android_jar())
    subprocess.run([java_tool("javac"), "-encoding", "UTF-8", "-cp", test_classpath, "-d", str(work),
                    *map(str, files + sources)], check=True)
    result = subprocess.run([java_tool("java"), "-cp", test_classpath, main_class],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
    report = f"{name}: production code with controlled Android stubs. No device, WebView, CDN or AI provider.\n"
    for path in production:
        report += f"{path.name} SHA256 {hashlib.sha256(path.read_bytes()).hexdigest()}\n"
    report += result.stdout
    (work / "result.txt").write_text(report)
    print(report, end="")
    return result.returncode


bridge = [SHARED / name for name in (
    "PronunciationLabRepository.java", "PronunciationLabDispatcher.java", "PronunciationLabProtocol.java")]
repository = [SHARED / name for name in (
    "LyricsLine.java", "LyricsResult.java", "LyricsDiskCache.java", "TrackSnapshot.java",
    "AiLyricsSettings.java", "AiLyricsRepository.java")]
settings = [SHARED / "AiLyricsSettings.java"]
failures = 0
failures += run("webview", WEBVIEW_STUBS, bridge + [ROOT / "tests/PronunciationLabWebViewRegression.java"],
                "kr.ivlis.ivlyricsandroid.PronunciationLabWebViewRegression", bridge) != 0
failures += run("dispatch", DISPATCH_STUBS, repository + [ROOT / "tests/android/os/Looper.java",
                ROOT / "tests/PronunciationLabDispatchRegression.java"],
                "kr.ivlis.ivlyricsandroid.PronunciationLabDispatchRegression", repository) != 0
failures += run("settings", SETTINGS_STUBS, settings + [ROOT / "tests/PronunciationLabSettingsRegression.java"],
                "kr.ivlis.ivlyricsandroid.PronunciationLabSettingsRegression", settings) != 0
raise SystemExit(1 if failures else 0)
