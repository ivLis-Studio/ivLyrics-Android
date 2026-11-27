package kr.ivlis.ivlyricsandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pronunciation Lab, the keyless rule- and dictionary-based pronunciation provider, in a hidden WebView.
 * The page holds only the ivLyrics engine; on its first request it imports the newest pronunciation-lab
 * release (GPL-3.0, never bundled) from jsDelivr, and each language's dictionaries download the first
 * time a lyric needs them. One page serves the whole process, the standalone app or Spotify with the
 * module, so the library and its dictionaries stay loaded between songs.
 *
 * <p>The WebView is created lazily on the main thread. Requests wait until the page posts "ready" and
 * may run side by side, keyed by id ({@link PronunciationLabDispatcher}). Each request is answered or
 * failed within {@link #REQUEST_TIMEOUT_MS}; a timeout or a cancelled caller also cancels it in the page.
 * When the page's renderer dies, its requests fail and the next request loads a new page.
 */
final class PronunciationLabRepository implements PronunciationLabProtocol.Converter {
    /** The first request of a language downloads its dictionaries (about 20 MB for Japanese). */
    static final long REQUEST_TIMEOUT_MS = 90_000L;
    /** A waiting caller gives up a little after the page's own timeout, in case the main thread is late. */
    private static final long CALLER_GRACE_MS = 5_000L;
    static final String PAGE_ASSET = "pronunciation-lab/PronunciationLabPage.html";
    static final String PAGE_BASE_URL = "https://appassets.androidplatform.net/pronunciation-lab/";
    static final String BRIDGE_NAME = "ivLyricsPronunciationLabNative";

    // Holds the application context only (see shared(Context)), never an Activity: the page outlives screens.
    @SuppressLint("StaticFieldLeak")
    private static PronunciationLabRepository shared;

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong nextId = new AtomicLong();
    private volatile String pageHtml;
    // Main thread only.
    private final PronunciationLabDispatcher dispatcher = new PronunciationLabDispatcher(this::evaluate);
    private final Map<String, Runnable> timeouts = new HashMap<>();
    private WebView webView;
    private long webViewGeneration;

    interface Callback {
        /** Runs once on the main thread: either {@code lines} or {@code error} is set. */
        void onFinished(List<String> lines, Exception error);
    }

    /**
     * The process's repository. It keeps the application context, never an Activity; in the Spotify
     * module that context is the module-wrapped one, whose assets are the module APK's.
     */
    static synchronized PronunciationLabRepository shared(Context context) {
        if (shared == null && context != null) {
            Context application = context.getApplicationContext();
            shared = new PronunciationLabRepository(application == null ? context : application);
        }
        return shared;
    }

    PronunciationLabRepository(Context context) {
        this.context = context;
    }

    /** Blocks a worker thread until the page answers; never call it on the main thread, which delivers the answer. */
    @Override
    public List<String> convert(PronunciationLabProtocol.Request request) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Pronunciation Lab answers on the main thread; wait for it on a worker thread");
        }
        if (request.lines.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            loadPage(); // Read here, off the main thread; the WebView reuses it.
        } catch (IOException missing) {
            throw new PronunciationLabProtocol.Failure(PronunciationLabProtocol.UNAVAILABLE,
                    "Page asset unavailable: " + describe(missing));
        }
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<List<String>> lines = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        String id = start(request, (values, error) -> {
            lines.set(values);
            failure.set(error);
            finished.countDown();
        });
        try {
            if (!finished.await(REQUEST_TIMEOUT_MS + CALLER_GRACE_MS, TimeUnit.MILLISECONDS)) {
                cancel(id);
                throw new PronunciationLabProtocol.Failure(PronunciationLabProtocol.TIMEOUT,
                        "No answer within " + (REQUEST_TIMEOUT_MS / 1000L) + " s");
            }
        } catch (InterruptedException interrupted) {
            // The caller is going away (its executor shut down), so the page stops working on it too.
            cancel(id);
            throw interrupted;
        }
        Exception error = failure.get();
        if (error != null) {
            throw error;
        }
        return lines.get();
    }

    /** Starts a request from any thread and returns its id; the callback runs once on the main thread. */
    String start(PronunciationLabProtocol.Request request, Callback callback) {
        String id = "p" + nextId.incrementAndGet();
        mainHandler.post(() -> begin(id, request, callback));
        return id;
    }

    /** Stops a request from any thread; its callback hears ABORTED unless it had already finished. */
    void cancel(String id) {
        mainHandler.post(() -> {
            try {
                dispatcher.cancel(id, new PronunciationLabProtocol.Failure(PronunciationLabProtocol.ABORTED, "Request cancelled"));
            } catch (RuntimeException error) {
                dropWebView(failure("Page script failed: " + describe(error)));
            }
        });
    }

    private void begin(String id, PronunciationLabProtocol.Request request, Callback callback) {
        Runnable timeout = () -> expire(id);
        timeouts.put(id, timeout);
        mainHandler.postDelayed(timeout, REQUEST_TIMEOUT_MS);
        try {
            dispatcher.submit(id, request, (lines, failure) -> {
                Runnable pending = timeouts.remove(id);
                if (pending != null) mainHandler.removeCallbacks(pending);
                callback.onFinished(lines, failure);
            });
            ensureWebView();
        } catch (Exception | LinkageError error) {
            // No WebView on this device, a missing asset, or a page that cannot take scripts.
            dropWebView(failure("WebView unavailable: " + describe(error)));
        }
    }

    private void expire(String id) {
        if (timeouts.remove(id) == null) {
            return;
        }
        boolean pageStarted = dispatcher.isReady();
        try {
            dispatcher.cancel(id, new PronunciationLabProtocol.Failure(PronunciationLabProtocol.TIMEOUT,
                    "No answer within " + (REQUEST_TIMEOUT_MS / 1000L) + " s"));
        } catch (RuntimeException error) {
            dropWebView(failure("Page script failed: " + describe(error)));
            return;
        }
        // A page that never posted "ready" did not start; the next request loads a new one. A started page
        // stays: a slow first download keeps going and its dictionaries serve the next request.
        if (!pageStarted && dispatcher.size() == 0) {
            dropWebView(failure("The page did not start"));
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    private void ensureWebView() throws IOException {
        if (webView != null) {
            return;
        }
        String html = loadPage();
        long generation = ++webViewGeneration;
        WebView view = new WebView(context);
        webView = view;
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        // Added before loading: the page posts "ready" through it while its script runs.
        view.addJavascriptInterface(new NativeBridge(generation), BRIDGE_NAME);
        view.setWebViewClient(new PageClient(generation));
        view.loadDataWithBaseURL(PAGE_BASE_URL, html, "text/html", "UTF-8", null);
    }

    /**
     * The page's HTML from this APK's assets. In the Spotify module WebView's android_asset URLs read
     * Spotify's APK, so the page is read through the module context, as FuriganaRepository does.
     */
    private String loadPage() throws IOException {
        String html = pageHtml;
        if (html != null) {
            return html;
        }
        try (InputStream input = context.getAssets().open(PAGE_ASSET);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            html = new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
        pageHtml = html;
        return html;
    }

    /** The dispatcher's page; it only sends scripts while a page that posted "ready" exists. */
    private void evaluate(String script) {
        WebView view = webView;
        if (view != null) {
            view.evaluateJavascript(script, null);
        }
    }

    /** Fails every request, and releases the WebView; the next request creates a new one. */
    private void dropWebView(PronunciationLabProtocol.Failure failure) {
        WebView dropped = webView;
        webView = null;
        webViewGeneration++;
        dispatcher.reset(failure);
        if (dropped != null) {
            // Destroyed after the current callback, which may be the WebView's own.
            mainHandler.post(() -> {
                try {
                    dropped.destroy();
                } catch (RuntimeException ignored) {
                }
            });
        }
    }

    private static PronunciationLabProtocol.Failure failure(String detail) {
        return new PronunciationLabProtocol.Failure(PronunciationLabProtocol.UNAVAILABLE, detail);
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.trim().isEmpty() ? "" : ": " + message.trim());
    }

    /** Receives the page's messages; Chromium calls it on its own thread. */
    private final class NativeBridge {
        private final long generation;

        NativeBridge(long generation) {
            this.generation = generation;
        }

        @JavascriptInterface
        public void postMessage(String json) {
            PronunciationLabProtocol.Message message = PronunciationLabProtocol.parse(json);
            mainHandler.post(() -> {
                if (generation != webViewGeneration || webView == null) {
                    return; // A page that was already dropped.
                }
                try {
                    dispatcher.handle(message);
                } catch (RuntimeException error) {
                    dropWebView(failure("Page script failed: " + describe(error)));
                }
            });
        }
    }

    private final class PageClient extends WebViewClient {
        private final long generation;

        PageClient(long generation) {
            this.generation = generation;
        }

        private boolean current(WebView view) {
            return view == webView && generation == webViewGeneration;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return true; // The page never navigates, and no other page may reach its bridge.
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            // Library and dictionary failures come back from the page as PRONUNCIATION_LAB_LOAD_FAILED.
            if (request != null && request.isForMainFrame() && current(view)) {
                dropWebView(failure("Page load failed: " + error.getDescription()));
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // Handled, so the app (Spotify, in the module) keeps running; the next request loads a new page.
            if (current(view)) {
                dropWebView(failure(detail != null && detail.didCrash()
                        ? "The page's renderer crashed" : "The page's renderer was stopped"));
            }
            return true;
        }
    }
}
