package dev.ivlyrics.spotify;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.LayoutInflater;

import java.io.File;

import kr.ivlis.ivlyricsandroid.IvLyricsBridge;
import kr.ivlis.ivlyricsandroid.BaseLyricsActivity;
import kr.ivlis.ivlyricsandroid.R;

/** Module resources with the host's package, preferences, files, and process identity. */
public final class ContextBridge {
    public static final String HOST_LYRICS_ACTIVITY =
            "com.spotify.lyrics.fullscreenview.page.LyricsFullscreenPageActivity";
    private static volatile String modulePath;
    private static Resources moduleResources;

    private ContextBridge() {}

    public static synchronized void initialize(String path) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("Missing module APK path");
        if (!path.equals(modulePath)) moduleResources = null;
        modulePath = path;
    }

    public static Context wrap(Context context) {
        if (context == null || context instanceof ModuleContext) return context;
        if (context instanceof SpotifyLyricsActivity) return context; // Its base is already wrapped.
        return new ModuleContext(context, loadResources(context));
    }

    private static synchronized Resources loadResources(Context host) {
        if (moduleResources != null) {
            if (!moduleResources.getConfiguration().equals(host.getResources().getConfiguration())) {
                moduleResources.updateConfiguration(new Configuration(host.getResources().getConfiguration()),
                        host.getResources().getDisplayMetrics());
            }
            return moduleResources;
        }
        String path = modulePath;
        if (path == null || !new File(path).isFile()) {
            throw new IllegalStateException("ivLyrics module APK is unavailable");
        }
        try {
            // Archive resources do not require the module to be separately installed.
            PackageInfo info = host.getPackageManager().getPackageArchiveInfo(path, 0);
            if (info == null || info.applicationInfo == null) {
                throw new IllegalStateException("Could not read ivLyrics module resources");
            }
            ApplicationInfo app = info.applicationInfo;
            app.sourceDir = path;
            app.publicSourceDir = path;
            Resources resources = host.getPackageManager().getResourcesForApplication(app);
            resources.updateConfiguration(new Configuration(host.getResources().getConfiguration()),
                    host.getResources().getDisplayMetrics());
            resources.getResourceName(R.style.AppTheme);
            moduleResources = resources;
            return resources;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("Could not load ivLyrics module resources", error);
        }
    }

    public static Intent lyricsIntent(Context context) {
        Intent intent = new Intent().setClassName(context.getPackageName(), HOST_LYRICS_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, true);
        intent.putExtra(BaseLyricsActivity.EXTRA_OPEN_LYRICS_PAGE, true);
        return intent;
    }

    private static final class ModuleContext extends ContextWrapper {
        private final Resources resources;
        private Resources.Theme theme;
        private LayoutInflater inflater;
        private Context application;

        ModuleContext(Context base, Resources resources) {
            super(base);
            this.resources = resources;
        }

        @Override public Resources getResources() { return resources; }
        @Override public AssetManager getAssets() { return resources.getAssets(); }
        @Override public ClassLoader getClassLoader() { return ContextBridge.class.getClassLoader(); }
        @Override public Context getApplicationContext() {
            if (application == null) {
                Context app = getBaseContext().getApplicationContext();
                application = app == null || app == getBaseContext()
                        ? this : new ModuleContext(app, resources);
            }
            return application;
        }
        @Override public Resources.Theme getTheme() {
            if (theme == null) {
                theme = resources.newTheme();
                theme.applyStyle(R.style.AppTheme, true);
            }
            return theme;
        }
        @Override public void setTheme(int ignoredHostResourceId) {
            getTheme().applyStyle(R.style.AppTheme, true);
        }
        @Override public Object getSystemService(String name) {
            if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                if (inflater == null) inflater = LayoutInflater.from(getBaseContext()).cloneInContext(this);
                return inflater;
            }
            return super.getSystemService(name);
        }
        @Override public Context createConfigurationContext(Configuration configuration) {
            return wrap(getBaseContext().createConfigurationContext(configuration));
        }
    }
}
