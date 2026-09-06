package kr.ivlis.ivlyricsandroid;

import android.app.Application;

/** Installs standalone integrations before any shared Activity or view is created. */
public final class StandaloneApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        StandaloneWebViewSupport.install();
    }
}
