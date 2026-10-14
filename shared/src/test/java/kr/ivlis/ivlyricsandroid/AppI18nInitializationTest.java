package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

public final class AppI18nInitializationTest {
    @Test
    public void everyLanguageTableInitializes() throws Exception {
        Class.forName("kr.ivlis.ivlyricsandroid.AppI18n", true, AppI18n.class.getClassLoader());
    }

    @Test
    public void appearanceChoicesHaveTranslationsInEveryLanguage() {
        String[] keys = {"fallback", "title_tap", "title_player", "title_disabled", "title_album", "title_context",
                "video_aspect", "video_fit", "video_fill", "video_stretch", "video_edges",
                "landscape_original", "landscape_artwork", "empty_notice", "context_unavailable"};
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            for (String suffix : keys) {
                String key = "appearance." + suffix;
                String value = AppI18n.t(language.code, key);
                org.junit.Assert.assertNotEquals(language.code + ":" + key, key, value);
                org.junit.Assert.assertFalse(language.code + ":" + key, value.trim().isEmpty());
                if (!"en".equals(language.code)) org.junit.Assert.assertNotEquals(
                        language.code + ":" + key, AppI18n.t("en", key), value);
            }
        }
    }

    @Test public void vinylControlsHaveTranslationsInEveryLanguage() {
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            for (String suffix : new String[]{"track_changes", "track_changes_desc", "rotation_speed",
                    "rotation_speed_desc", "simple_title", "simple_title_desc"}) {
                String key = "vinyl." + suffix;
                String value = AppI18n.t(language.code, key);
                org.junit.Assert.assertNotEquals(key, value);
                org.junit.Assert.assertFalse(value.trim().isEmpty());
                if (!"en".equals(language.code)) org.junit.Assert.assertNotEquals(AppI18n.t("en", key), value);
            }
        }
    }
}
