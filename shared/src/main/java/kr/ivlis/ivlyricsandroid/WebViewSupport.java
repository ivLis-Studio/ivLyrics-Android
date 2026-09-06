package kr.ivlis.ivlyricsandroid;

import android.util.Log;
import android.webkit.WebView;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional WebView capabilities supplied by the application that owns this UI. */
public final class WebViewSupport {
    private static final String TAG = "ivLyricsWebView";
    private static final AtomicBoolean missingBackendLogged = new AtomicBoolean();
    private static volatile Backend backend;

    private WebViewSupport() {}

    public interface Backend {
        /** Returns false when this device's WebView does not support document-start scripts. */
        boolean addDocumentStartJavaScript(WebView view, String script, Set<String> allowedOriginRules);
    }

    /** Register before constructing shared views, from Application or the Spotify hook entrypoint. */
    public static void install(Backend implementation) {
        backend = Objects.requireNonNull(implementation, "WebView support backend");
    }

    /** Missing initialization is reported once and treated as an unsupported optional capability. */
    public static boolean addDocumentStartJavaScript(
            WebView view, String script, Set<String> allowedOriginRules
    ) {
        Backend implementation = backend;
        if (implementation == null) {
            if (missingBackendLogged.compareAndSet(false, true)) {
                Log.w(TAG, "WebView support is not initialized; document-start scripts are unavailable");
            }
            return false;
        }
        return implementation.addDocumentStartJavaScript(view, script, allowedOriginRules);
    }
}
