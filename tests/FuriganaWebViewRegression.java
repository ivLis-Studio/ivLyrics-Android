package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.os.Handler;
import android.webkit.WebView;
import java.lang.reflect.*;
import java.util.*;

/** Executes the complete repository WebView bootstrap against separate host/module assets. */
public final class FuriganaWebViewRegression {
    private static int assertions;
    private static final String HTML = "<!doctype html><script>window.ivLyricsFurigana={};</script><!-- 日本語 후리가나 -->";
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    private static void bridge(WebView view, String name, String... args) throws Exception {
        for (Method method : view.bridge.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name)) continue;
            method.setAccessible(true);
            method.invoke(view.bridge, (Object[]) args);
            return;
        }
        throw new NoSuchMethodException(name);
    }
    private static final class Fixture implements FuriganaRepository.Callback, AutoCloseable {
        final Context host = new Context();
        final Context module = new Context();
        final FuriganaRepository repository;
        final TrackSnapshot track = new TrackSnapshot("fixture song", "fixture artist", "", "", "", "",
                5000, 0, 1, 1f, false, null, "");
        final LyricsResult base = new LyricsResult(List.of(new LyricsLine(1000, 3000, "日本語", List.of())),
                "fixture", "base", false);
        int loaded, errors;
        String error;
        LyricsResult result;
        Fixture(boolean withAsset) throws Exception {
            module.application = host;
            WebView.processContext = host;
            if (withAsset) addAsset();
            repository = new FuriganaRepository(module);
            set(repository, "diskCache", null); // Keep this bootstrap test independent from async disk IO.
        }
        void addAsset() { module.assets.files.put("furigana/bridge.html", HTML); }
        void load() { repository.loadFurigana(track, base, true, this); }
        WebView view() throws Exception { return (WebView) get(repository, "webView"); }
        Map<?, ?> pending() throws Exception { return (Map<?, ?>) get(repository, "pendingRequests"); }
        String id() throws Exception { return (String) pending().keySet().iterator().next(); }
        void complete(WebView view) throws Exception {
            bridge(view, "onResult", id(), "{\"ok\":true,\"lines\":[\"<ruby>日本語<rt>にほんご</rt></ruby>\"]}");
            Handler.drain();
        }
        public void onFuriganaLoaded(String key, LyricsResult value) { loaded++; result = value; }
        public void onFuriganaError(String key, String message) { errors++; error = message; }
        public void onFuriganaLog(String key, String message) { }
        public void close() {
            WebView.throwCreate = WebView.throwLoad = WebView.throwEvaluate = false;
            repository.shutdown();
            Handler.drain();
            Handler.timeOut();
        }
    }
    private static void moduleAssetBootstrap() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            fixture.load();
            WebView view = fixture.view();
            check(view != null && view.context == fixture.module, "WebView receives module context");
            check(fixture.module.assets.reads == 1 && fixture.host.assets.reads == 0,
                    "asset bytes come from module, not Spotify Application assets");
            check(HTML.equals(view.html), "module HTML preserves UTF-8 kanji and Korean text");
            check(view.baseUrl.startsWith("https://") && "text/html".equals(view.mimeType)
                    && "UTF-8".equals(view.encoding), "HTML bootstraps with HTTPS origin and explicit encoding");
            check(view.settings.javascript && !view.settings.fileAccess && !view.settings.contentAccess
                    && !view.settings.fileFromFile && !view.settings.universalFromFile,
                    "JS works without host file/content access");
            check(view.scripts.isEmpty() && fixture.pending().size() == 1, "request waits for page bootstrap");
            view.finish();
            check(view.scripts.size() == 1 && view.scripts.get(0).contains("日本語"), "page load delivers queued production request");
            fixture.complete(view);
            Handler.timeOut();
            check(fixture.loaded == 1 && fixture.errors == 0 && fixture.pending().isEmpty(), "JS result completes once and cancels timeout");
            check(fixture.result.lines.get(0).furiganaText.contains("にほんご"), "Ruby survives production result mapping");
        }
    }
    private static void missingAssetAndCreationRetry() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.load();
            check(fixture.errors == 1 && fixture.view() == null && fixture.pending().isEmpty(), "missing module asset terminates immediately");
            Handler.timeOut();
            check(fixture.errors == 1, "asset failure leaves no duplicate timeout");
            fixture.addAsset();
            fixture.load();
            fixture.view().finish();
            fixture.complete(fixture.view());
            check(fixture.loaded == 1 && fixture.errors == 1, "subsequent asset load retries successfully");
        }
        try (Fixture fixture = new Fixture(true)) {
            WebView.throwCreate = true;
            fixture.load();
            check(fixture.errors == 1 && fixture.pending().isEmpty() && fixture.view() == null, "WebView factory failure terminates immediately");
            WebView.throwCreate = false;
            fixture.load();
            fixture.view().finish();
            fixture.complete(fixture.view());
            Handler.timeOut();
            check(fixture.loaded == 1 && fixture.errors == 1, "WebView factory can retry cleanly");
        }
    }
    private static void pageFailureAndStaleBridge() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            fixture.load();
            WebView old = fixture.view();
            old.fail(false);
            check(fixture.errors == 0 && fixture.view() == old, "subresource error permits JS CDN fallback");
            old.fail(true);
            Handler.drain();
            check(fixture.errors == 1 && fixture.pending().isEmpty() && fixture.view() == null && old.destroyed,
                    "main-frame failure terminates request and discards broken WebView");
            fixture.load();
            WebView current = fixture.view();
            old.finish();
            bridge(old, "onReady");
            bridge(old, "onInitializationError", "late old page failure");
            bridge(old, "onResult", fixture.id(), "{\"ok\":false,\"error\":\"old result\"}");
            Handler.drain();
            check(current.scripts.isEmpty() && fixture.view() == current && fixture.pending().size() == 1
                    && fixture.errors == 1, "old page/bridge callbacks cannot flush or fail replacement request");
            current.finish();
            fixture.complete(current);
            Handler.timeOut();
            check(fixture.loaded == 1 && fixture.errors == 1, "replacement completes after stale callbacks");
        }
        try (Fixture fixture = new Fixture(true)) {
            WebView.throwLoad = true;
            fixture.load();
            Handler.drain();
            check(fixture.errors == 1 && fixture.pending().isEmpty() && fixture.view() == null,
                    "synchronous HTML load exception terminates and resets");
            check(WebView.instances.get(WebView.instances.size() - 1).destroyed, "failed load releases WebView");
            WebView.throwLoad = false;
            fixture.load();
            fixture.view().finish();
            fixture.complete(fixture.view());
            check(fixture.loaded == 1 && fixture.errors == 1, "HTML load exception does not poison retry");
        }
        try (Fixture fixture = new Fixture(true)) {
            fixture.load();
            WebView.throwEvaluate = true;
            fixture.view().finish();
            Handler.drain();
            check(fixture.errors == 1 && fixture.pending().isEmpty() && fixture.view() == null,
                    "queued script evaluation failure terminates and resets");
            WebView.throwEvaluate = false;
            fixture.load();
            fixture.view().finish();
            fixture.complete(fixture.view());
            check(fixture.loaded == 1 && fixture.errors == 1, "script evaluation failure permits clean retry");
        }
    }
    private static void initializationErrorAndTimeoutRetry() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            fixture.load();
            WebView failed = fixture.view();
            bridge(failed, "onInitializationError", "both CDN libraries unavailable");
            Handler.drain();
            check(fixture.errors == 1 && fixture.view() == null && fixture.pending().isEmpty() && failed.destroyed,
                    "JS initialization failure ends loading and resets tokenizer page");
            Handler.timeOut();
            check(fixture.errors == 1, "JS initialization error cancels timeout");
            fixture.load();
            WebView retry = fixture.view();
            check(retry != failed, "initialization retry constructs new JS state");
            retry.finish();
            fixture.complete(retry);
            check(fixture.loaded == 1 && fixture.errors == 1, "initialization retry can generate Ruby");
        }
        try (Fixture fixture = new Fixture(true)) {
            fixture.load();
            WebView hung = fixture.view();
            Handler.timeOut();
            check(fixture.errors == 1 && fixture.pending().isEmpty() && fixture.view() == null && hung.destroyed,
                    "timeout discards a hung page so retry cannot reuse an unresolved init promise");
            fixture.load();
            fixture.view().finish();
            fixture.complete(fixture.view());
            check(fixture.loaded == 1 && fixture.errors == 1, "timeout retry succeeds without duplicate callback");
        }
    }
    private static void supersededAndShutdown() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            fixture.load();
            String oldId = fixture.id();
            fixture.load();
            WebView view = fixture.view();
            view.finish();
            check(view.scripts.size() == 1 && !view.scripts.get(0).contains("\"" + oldId + "\""),
                    "superseded request script is removed before page readiness");
            bridge(view, "onResult", oldId, "{\"ok\":true,\"lines\":[]}");
            Handler.drain();
            check(fixture.loaded == 0 && fixture.pending().size() == 1, "superseded result cannot complete current load");
            fixture.repository.shutdown();
            bridge(view, "onInitializationError", "late after shutdown");
            Handler.drain();
            Handler.timeOut();
            check(fixture.loaded == 0 && fixture.errors == 0 && fixture.pending().isEmpty() && view.destroyed,
                    "shutdown invalidates bridge callbacks and queued timeouts");
        }
    }
    public static void main(String[] args) throws Exception {
        moduleAssetBootstrap();
        missingAssetAndCreationRetry();
        pageFailureAndStaleBridge();
        initializationErrorAndTimeoutRetry();
        supersededAndShutdown();
        System.out.println("PASS " + assertions + " assertions: module assets, UTF-8/HTTPS bootstrap, init/page failures, retry, stale callbacks, timeout and shutdown");
    }
}
