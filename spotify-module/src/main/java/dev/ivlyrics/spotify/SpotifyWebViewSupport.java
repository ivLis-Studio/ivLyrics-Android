package dev.ivlyrics.spotify;

import android.webkit.WebView;

import java.util.Set;

import kr.ivlis.ivlyricsandroid.WebViewSupport;
import kr.ivlis.ivlyricsandroid.privateapi.androidx.webkit.WebViewCompat;
import kr.ivlis.ivlyricsandroid.privateapi.androidx.webkit.WebViewFeature;

/** Keeps the module's WebKit implementation isolated from Spotify's classes. */
public final class SpotifyWebViewSupport implements WebViewSupport.Backend {
    private static final SpotifyWebViewSupport INSTANCE = new SpotifyWebViewSupport();

    private SpotifyWebViewSupport() {}

    /** Called by the Spotify hook entrypoint before initializing the shared lyrics UI. */
    public static void install() {
        WebViewSupport.install(INSTANCE);
    }

    @Override public boolean addDocumentStartJavaScript(
            WebView view, String script, Set<String> allowedOriginRules
    ) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return false;
        WebViewCompat.addDocumentStartJavaScript(view, script, allowedOriginRules);
        return true;
    }
}
