package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

public final class AppI18nInitializationTest {
    @SuppressWarnings("unchecked")
    @Test public void firstUseCopyHasEveryLocaleAndMatchingPlaceholders() throws Exception {
        java.lang.reflect.Field field = AppI18n.class.getDeclaredField("STRINGS");
        field.setAccessible(true);
        java.util.Map<String, java.util.Map<String, String>> tables =
                (java.util.Map<String, java.util.Map<String, String>>) field.get(null);
        org.junit.Assert.assertEquals(22, tables.size());
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            for (String key : FirstUseI18n.KEYS) {
                String value = tables.get(language.code).get(key);
                String label = language.code + ":" + key;
                org.junit.Assert.assertNotNull(label, value);
                org.junit.Assert.assertFalse(label, value.isBlank());
                org.junit.Assert.assertEquals(label, placeholders(AppI18n.t("en", key)), placeholders(value));
                if (!"en".equals(language.code)) org.junit.Assert.assertNotEquals(label, AppI18n.t("en", key), value);
            }
        }
        org.junit.Assert.assertEquals("언어와 화면", AppI18n.t("ko", "onboarding.welcome_title"));
        org.junit.Assert.assertEquals("일본어 가사", String.format(java.util.Locale.KOREAN,
                AppI18n.t("ko", "first_language.title_format"), "일본어"));
    }

    @SuppressWarnings("unchecked")
    @Test public void regenerationLabelIsPresentWithoutFallbackInEveryLanguage() throws Exception {
        java.lang.reflect.Field field = AppI18n.class.getDeclaredField("STRINGS");
        field.setAccessible(true);
        java.util.Map<String, java.util.Map<String, String>> tables =
                (java.util.Map<String, java.util.Map<String, String>>) field.get(null);
        org.junit.Assert.assertEquals(22, AppI18n.UI_LANGUAGES.size());
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            String value = tables.get(language.code).get("lyrics.regenerate_supplements");
            org.junit.Assert.assertNotNull(language.code, value);
            org.junit.Assert.assertFalse(language.code, value.isBlank());
            org.junit.Assert.assertEquals(value, AppI18n.t(language.code, "lyrics.regenerate_supplements"));
        }
        org.junit.Assert.assertEquals("번역·발음 재생성", AppI18n.t("ko", "lyrics.regenerate_supplements"));
        org.junit.Assert.assertEquals("Regenerate translation & pronunciation", AppI18n.t("en", "lyrics.regenerate_supplements"));
    }

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

    @Test public void englishFallbacksOfHelperPassesKeepCzechTranslations() {
        // Helpers without a Czech case used to overwrite these entries of the Czech table with English.
        String[] keys = {"update.status_idle", "section.app_update", "button.check_updates", "section.typography",
                "typography.weight", "typography.weight.regular", "section.speaker_colors", "button.apply_colors",
                "setting.lyrics_alignment", "section.pip", "pip.unavailable", "lyrics.video_sync.title",
                "lyrics.bluetooth_sync.title", "tmi.did_you_know", "tmi.require_key", "lyrics.lrclib_search.title",
                "section.spotify_shortcut", "setting.keep_screen_on", "toast.spotify_open_failed",
                "label.same_as_ui_language", "section.language_desc", "setting.video_scale",
                "background.mode.video_desc", "setting.creator_speaker_colors", "lyrics.tab.background"};
        for (String key : keys) {
            org.junit.Assert.assertNotEquals(key, AppI18n.t("en", key), AppI18n.t("cs", key));
        }
        org.junit.Assert.assertEquals("Aktualizace zatím nebyly zkontrolovány.", AppI18n.t("cs", "update.status_idle"));
        org.junit.Assert.assertEquals("Pozadí", AppI18n.t("cs", "lyrics.tab.background"));
    }

    @Test public void labelsSharedWithTheIosAppUseTheSameWords() {
        String[][] background = {{"ko", "배경"}, {"en", "Background"}, {"zh-CN", "背景"}, {"zh-TW", "背景"},
                {"ja", "背景"}, {"hi", "पृष्ठभूमि"}, {"es", "Fondo"}, {"fr", "Arrière-plan"}, {"ar", "الخلفية"},
                {"fa", "پس\u200cزمینه"}, {"de", "Hintergrund"}, {"ru", "Фон"}, {"sv", "Bakgrund"}, {"pt", "Fundo"},
                {"bn", "পটভূমি"}, {"it", "Sfondo"}, {"th", "พื้นหลัง"}, {"vi", "Nền"}, {"id", "Latar belakang"},
                {"ms", "Latar belakang"}, {"tr", "Arka plan"}, {"cs", "Pozadí"}};
        org.junit.Assert.assertEquals(22, background.length);
        for (String[] row : background) {
            org.junit.Assert.assertEquals(row[0], row[1], AppI18n.t(row[0], "lyrics.tab.background"));
        }
        org.junit.Assert.assertEquals("%d / %d단계", AppI18n.t("ko", "onboarding.step_format"));
        org.junit.Assert.assertEquals("第 %d / %d 步", AppI18n.t("zh-CN", "onboarding.step_format"));
        org.junit.Assert.assertEquals("ステップ %d / %d", AppI18n.t("ja", "onboarding.step_format"));
        org.junit.Assert.assertEquals("싱크: %s", AppI18n.t("ko", "lyrics.credit_sync_by_format"));
        org.junit.Assert.assertEquals("同期: %s", AppI18n.t("ja", "lyrics.credit_sync_by_format"));
        org.junit.Assert.assertEquals("남성", AppI18n.t("ko", "speaker_color.male"));
        org.junit.Assert.assertEquals("Ženský", AppI18n.t("cs", "speaker_color.female"));
        org.junit.Assert.assertEquals("Normale", AppI18n.t("it", "speaker_color.normal"));
        org.junit.Assert.assertEquals("Фуригана", AppI18n.t("ru", "lyrics.furigana"));
        org.junit.Assert.assertEquals("پایان\u200cبندی", AppI18n.t("fa", "interlude.postlude"));
        org.junit.Assert.assertEquals("حداکثر توکن", AppI18n.t("fa", "field.max_tokens"));
        for (String language : new String[]{"fr", "de", "sv"}) {
            org.junit.Assert.assertFalse(language, AppI18n.t(language, "research.token_consent_agree").contains("Research"));
        }
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            for (String weight : new String[]{"regular", "semibold", "bold"}) {
                String key = "typography.weight." + weight;
                if (!"en".equals(language.code)) {
                    org.junit.Assert.assertNotEquals(language.code + ":" + key, AppI18n.t("en", key), AppI18n.t(language.code, key));
                }
            }
        }
    }

    @Test public void messagesForTheModuleErrorsAndAccessibilityExistInEveryLanguage() {
        String[] keys = {"sing_along.state_off", "sing_along.state_low", "sing_along.state_high", "sing_along.stopping",
                "sing_along.cancel", "sing_along.no_track", "sing_along.remote", "sing_along.offline",
                "sing_along.quality", "sing_along.version", "sing_along.unsupported_track",
                "sing_along.unsupported_account", "sing_along.mask_error", "sing_along.timeout",
                "sing_along.reset_failed", "sing_along.error", "sing_along.waiting", "error.api_key_required",
                "error.ai_request_failed", "error.keyless_none_enabled", "error.provider_unexpected_response_format",
                "error.provider_unavailable_format", "error.provider_http_format", "accessibility.open_lyrics",
                "accessibility.open_ivlyrics_lyrics", "bluetooth_sync.device_ble_headset",
                "bluetooth_sync.device_ble_speaker", "bluetooth_sync.device_hearing_aid"};
        java.util.regex.Pattern hangul = java.util.regex.Pattern.compile("[\uac00-\ud7a3]");
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            for (String key : keys) {
                String value = AppI18n.t(language.code, key);
                String label = language.code + ":" + key;
                org.junit.Assert.assertNotEquals(label, key, value);
                org.junit.Assert.assertFalse(label, value.trim().isEmpty());
                org.junit.Assert.assertEquals(label, placeholders(AppI18n.t("en", key)), placeholders(value));
                if (!"ko".equals(language.code)) org.junit.Assert.assertFalse(label, hangul.matcher(value).find());
                if (!"en".equals(language.code)) org.junit.Assert.assertNotEquals(label, AppI18n.t("en", key), value);
            }
        }
        org.junit.Assert.assertEquals("Google Translate request failed (HTTP 429).",
                String.format(java.util.Locale.ROOT, AppI18n.t("en", "error.provider_http_format"), "Google Translate", 429));
    }

    private static java.util.List<String> placeholders(String value) {
        java.util.List<String> found = new java.util.ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("%[sd]").matcher(value);
        while (matcher.find()) found.add(matcher.group());
        return found;
    }
}
