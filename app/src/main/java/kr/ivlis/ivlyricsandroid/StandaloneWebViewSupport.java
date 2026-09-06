package kr.ivlis.ivlyricsandroid;

import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Set;

/** The standalone app can use the normal AndroidX WebKit dependency. */
public final class StandaloneWebViewSupport implements WebViewSupport.Backend {
    private static final StandaloneWebViewSupport INSTANCE = new StandaloneWebViewSupport();

    private StandaloneWebViewSupport() {}

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
