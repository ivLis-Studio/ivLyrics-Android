package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.LocaleList;
import android.os.SystemClock;
import android.view.View;
import android.widget.PopupWindow;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** Unexported, debug-only offline fixture using the real shared activity and popup. */
public final class SettingsUiHarnessActivity extends BaseLyricsActivity {
    private static volatile Configuration launchConfiguration;
    private Context storage;
    public int supplementActions;
    public int culturalActions;
    public int videoActions;
    public int searchActions;
    public int metadataActions;

    public static void prepareConfiguration(String language, float fontScale) {
        Configuration config = new Configuration();
        config.fontScale = fontScale;
        config.setLocales(new LocaleList(Locale.forLanguageTag(language)));
        config.setLayoutDirection(Locale.forLanguageTag(language));
        launchConfiguration = config;
    }

    @Override protected void attachBaseContext(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        Configuration requested = launchConfiguration;
        if (requested != null) config.updateFrom(requested);
        // Activity/the framework may resolve its theme before onCreate. Give it
        // configured resources at attachment, never applyOverrideConfiguration late.
        super.attachBaseContext(base.createConfigurationContext(config));
        storage = new ContextWrapper(base.getApplicationContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("settings_ui_fixture_" + name, mode);
            }
            @Override public File getFilesDir() { return directory(super.getFilesDir()); }
            @Override public File getCacheDir() { return directory(super.getCacheDir()); }
        };
    }

    private static File directory(File parent) {
        File result = new File(parent, "settings_ui_fixture");
        if (!result.isDirectory() && !result.mkdirs()) throw new IllegalStateException("Fixture storage unavailable");
        return result;
    }

    @Override public Context getApplicationContext() { return storage == null ? super.getApplicationContext() : storage; }
    @Override protected boolean usesLivePlayback() { return false; }
    @Override protected boolean isInitialSetupComplete() { return true; }

    @Override protected void requestAiLyrics(boolean clearCache) {
        supplementActions++;
        call("setLyricsSupplementLoading", new Class<?>[]{boolean.class, boolean.class}, true, true);
    }
    @Override protected void requestCulturalAnnotations(boolean clearCache) { culturalActions++; }
    @Override protected void showVideoSelectionDialog() { videoActions++; }
    @Override protected void performManualLrclibSearch() { searchActions++; }
    @Override protected void requestMetadataTranslation(boolean clearCache) { metadataActions++; }

    @Override protected void onCreate(Bundle savedState) {
        String language = getIntent().getStringExtra("uiLanguage");
        if (language == null) language = "ko";
        getIntent().putExtra(IvLyricsBridge.EXTRA_EMBEDDED_MODE, true);
        storage.getSharedPreferences(AiLyricsSettings.PREFS_NAME, MODE_PRIVATE).edit().clear().commit();
        AiLyricsSettings settings = new AiLyricsSettings(storage);
        settings.setUiLang(language);
        for (AiLyricsSettings.Provider provider : AiLyricsSettings.PROVIDERS) settings.setAiProviderEnabled(provider.id, false);
        settings.setCulturalAnnotationsEnabled(true);
        // No credentials, no live session registration, no repository load calls.
        super.onCreate(savedState);
        seedTrack();
        View panel = view("settingsPanel");
        panel.setVisibility(View.VISIBLE);
        panel.setAlpha(1f);
        panel.bringToFront();
    }

    public void seedTrack() {
        put("currentTrack", new TrackSnapshot("ぼくらの", "Eve", "UI fixture", "fixture.offline", "fixture:track", "",
                120000L, 12000L, SystemClock.elapsedRealtime(), 1f, false, null, ""));
        LyricsResult result = new LyricsResult(Arrays.asList(
                new LyricsLine(0, 10000, "君と歩いた道を", Collections.emptyList()),
                new LyricsLine(10000, 20000, "忘れないよ", Collections.emptyList())), "Fixture", "Offline UI fixture", false);
        put("currentBaseLyricsResult", result);
        put("currentLyricsResult", result);
        put("detectedLyricsSourceLang", "ja");
        call("updateLyricsLanguageSettingsUi");
    }

    public void settings(String tab) {
        dismissPopup();
        view("settingsPanel").setVisibility(View.VISIBLE);
        view("settingsPanel").setAlpha(1f);
        call("switchSettingsTab", new Class<?>[]{String.class}, tab);
    }

    public View popup(String tab) {
        view("settingsPanel").setVisibility(View.GONE);
        call("switchLyricsPopupTab", new Class<?>[]{String.class}, tab);
        call("showLyricsMetaMenuPopup", new Class<?>[]{View.class}, (Object) null);
        return ((PopupWindow) value("lyricsMetaMenuPopup")).getContentView();
    }

    public void dismissPopup() { call("dismissLyricsMetaMenuPopup"); }
    public View view(String name) { return (View) value(name); }
    public Object value(String name) {
        try { Field field = BaseLyricsActivity.class.getDeclaredField(name); field.setAccessible(true); return field.get(this); }
        catch (ReflectiveOperationException error) { throw new AssertionError(name, error); }
    }
    public void put(String name, Object value) {
        try { Field field = BaseLyricsActivity.class.getDeclaredField(name); field.setAccessible(true); field.set(this, value); }
        catch (ReflectiveOperationException error) { throw new AssertionError(name, error); }
    }
    public void call(String name) { call(name, new Class<?>[0]); }
    public void call(String name, Class<?>[] types, Object... values) {
        try { Method method = BaseLyricsActivity.class.getDeclaredMethod(name, types); method.setAccessible(true); method.invoke(this, values); }
        catch (ReflectiveOperationException error) { throw new AssertionError(name, error); }
    }
}
