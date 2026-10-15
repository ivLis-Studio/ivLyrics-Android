package kr.ivlis.ivlyricsandroid;

import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** Offline, unexported first-use fixture. Only fixture preferences/files are ever changed. */
public final class FirstUseHarnessActivity extends BaseLyricsActivity {
    private static Configuration launchConfiguration;
    private Context storage;
    public int supplementRequests;
    public Intent permissionIntent;

    public static void prepare(String language, float scale) {
        Configuration config = new Configuration();
        config.fontScale = scale;
        config.setLocales(new LocaleList(Locale.forLanguageTag(language)));
        config.setLayoutDirection(Locale.forLanguageTag(language));
        launchConfiguration = config;
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base.createConfigurationContext(new Configuration(launchConfiguration)));
        storage = new ContextWrapper(base.getApplicationContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("first_use_fixture_" + name, mode);
            }
            @Override public File getFilesDir() { return fixtureDirectory(super.getFilesDir()); }
            @Override public File getCacheDir() { return fixtureDirectory(super.getCacheDir()); }
            @Override public File getNoBackupFilesDir() { return fixtureDirectory(super.getNoBackupFilesDir()); }
        };
    }

    private static File fixtureDirectory(File parent) {
        File directory = new File(parent, "first_use_fixture");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Fixture storage unavailable");
        return directory;
    }

    @Override public Context getApplicationContext() { return storage == null ? super.getApplicationContext() : storage; }
    @Override public SharedPreferences getSharedPreferences(String name, int mode) {
        return storage == null ? super.getSharedPreferences("first_use_fixture_" + name, mode)
                : storage.getSharedPreferences(name, mode);
    }
    @Override protected boolean usesLivePlayback() { return false; }
    @Override protected boolean isInitialSetupComplete() {
        return !getIntent().getBooleanExtra("onboarding", true) || super.isInitialSetupComplete();
    }
    @Override protected void requestAiLyrics(boolean clearCache) { supplementRequests++; }
    @Override protected void requestCulturalAnnotations(boolean clearCache) {}
    @Override protected void requestMetadataTranslation(boolean clearCache) {}
    @Override public void startActivity(Intent intent) {
        if (Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS.equals(intent.getAction())) permissionIntent = intent;
        else super.startActivity(intent);
    }

    @Override protected void onCreate(Bundle savedState) {
        boolean onboarding = getIntent().getBooleanExtra("onboarding", true);
        getIntent().putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, !onboarding);
        if (savedState == null) {
            getSharedPreferences(AiLyricsSettings.PREFS_NAME, MODE_PRIVATE).edit().clear().commit();
            getSharedPreferences("settings_ui", MODE_PRIVATE).edit().clear()
                    .putString("theme", getIntent().getStringExtra("theme")).commit();
            AiLyricsSettings settings = new AiLyricsSettings(storage);
            settings.setUiLang(getIntent().getStringExtra("language"));
            // The decorative player background is outside these first-use tests. Stop only
            // its drift so native window draw-idle can observe the form on every renderer.
            // This does NOT disable ScrollView/OverScroller or native control animations.
            settings.setBackgroundReduceMotion(true);
            if (getIntent().getBooleanExtra("greeting_motion", false)) {
                // Exercise welcome motion without introducing unrelated player drift.
                settings.setBackgroundMode(AiLyricsSettings.BACKGROUND_MODE_SOLID);
                settings.setBackgroundReduceMotion(false);
            }
            for (AiLyricsSettings.Provider provider : AiLyricsSettings.PROVIDERS) settings.setAiProviderEnabled(provider.id, false);
            settings.setBingTranslateEnabled(false);
            settings.setGoogleTranslateEnabled(true);
            settings.shutdown();
        }
        super.onCreate(savedState);
        if (!onboarding) seedTrack("ja", "fixture:ja");
    }

    public void seedTrack(String language, String key) {
        put("currentLyricsKey", key);
        put("currentTrack", new TrackSnapshot("Fixture song", "Fixture artist", "Fixture album", "fixture.offline", key, "",
                120000L, 12000L, SystemClock.elapsedRealtime(), 1f, false, null, ""));
        LyricsResult lyrics = new LyricsResult(Arrays.asList(
                new LyricsLine(0, 10000, "君と歩いた道を", Collections.emptyList()),
                new LyricsLine(10000, 20000, "忘れないよ", Collections.emptyList())), "Fixture", "Offline UI fixture", false);
        put("currentBaseLyricsResult", lyrics);
        put("currentLyricsResult", lyrics);
        put("detectedLyricsSourceLang", language);
        call("updateLyricsLanguageSettingsUi");
    }

    public void dismissPrompt() {
        AlertDialog dialog = (AlertDialog) value("firstLanguagePromptDialog");
        if (dialog != null) dialog.dismiss();
    }
    public View view(String name) { return (View) value(name); }
    public Object value(String name) {
        try { Field field = BaseLyricsActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(this); }
        catch (ReflectiveOperationException error) { throw new AssertionError(name, error); }
    }
    public void put(String name, Object value) {
        try { Field field = BaseLyricsActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(this, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(name, error); }
    }
    public Object call(String name) { return call(name, new Class<?>[0]); }
    public Object call(String name, Class<?>[] types, Object... args) {
        try { Method method = BaseLyricsActivity.class.getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(this, args); }
        catch (ReflectiveOperationException error) { throw new AssertionError(name, error); }
    }
}
