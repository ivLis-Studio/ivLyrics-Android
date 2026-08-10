package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class AppI18nTest {
    @Test
    public void persianLanguageTabHasNoCorruptedSuffix() {
        assertEquals("زبان", AppI18n.t("fa", "lyrics.tab.language"));
    }

    @Test
    public void researchStringsExistForEverySupportedLanguage() {
        String[] keys = {
                "tmi.title", "tmi.loading", "tmi.disclaimer", "research.thesis",
                "research.section.overview", "research.section.lyric_analysis",
                "research.fun_facts", "research.timeline", "research.sources",
                "research.source_note", "research.generating_more", "research.web_fallback_warning"
        };
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            for (String key : keys) {
                String value = AppI18n.t(language.code, key);
                assertFalse(language.code + " missing " + key, value == null || value.trim().isEmpty() || key.equals(value));
            }
        }
    }

    @Test
    public void localizedResearchWarningsDoNotLeakEnglishProductName() {
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            if ("en".equals(language.code)) continue;
            assertFalse(language.code + " contains untranslated Research",
                    AppI18n.t(language.code, "research.web_fallback_warning").contains("Research"));
        }
    }
}
