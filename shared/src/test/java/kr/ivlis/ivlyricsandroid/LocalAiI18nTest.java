package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class LocalAiI18nTest {
    private static final Pattern MODEL_NAMES = Pattern.compile("(?i)Hy-MT|Q4_K|Q6_K|Q8_0|GGUF|Tencent|wllama|jsDelivr|WebGPU");

    @Test public void everyLanguageHasEveryLocalAiStringWithItsPlaceholders() {
        String[][] placeholders = {
                {"local_ai.idle_after", "{minutes}"}, {"local_ai.idle_after_hours", "{hours}"},
                {"local_ai.status_downloading", "{percent}"}, {"local_ai.status_downloading", "{loaded}"},
                {"local_ai.status_downloading", "{total}"}, {"local_ai.status_ready", "{device}"},
                {"local_ai.status_error", "{error}"}, {"local_ai.download", "{size}"},
                {"local_ai.unsupported_language", "{language}"}
        };
        for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
            for (String key : LocalAiI18n.KEYS) {
                String value = AppI18n.t(language.code, key);
                assertNotEquals(language.code + ":" + key, key, value);
                assertFalse(language.code + ":" + key, value.trim().isEmpty());
                assertFalse(language.code + ":" + key + " names the model", MODEL_NAMES.matcher(value).find());
            }
            for (String[] placeholder : placeholders) {
                assertTrue(language.code + ":" + placeholder[0], AppI18n.t(language.code, placeholder[0]).contains(placeholder[1]));
            }
        }
    }

    @Test public void stringsComeFromTheDesktopLanguageFiles() {
        assertEquals("Translation-only AI that runs locally on this device. No API key needed, and lyrics never leave your device.",
                AppI18n.t("en", "provider.desc.local-ai"));
        assertEquals("Lyrics are translated on this device and are never sent anywhere. The model (Hugging Face) is downloaded only once.",
                AppI18n.t("en", "local_ai.privacy"));
        assertEquals("번역 품질", AppI18n.t("ko", "local_ai.quality"));
        assertEquals("로컬 라이트", AppI18n.t("ko", "local_ai.tier_light"));
        assertEquals("1시간 후", LocalAiModels.format(AppI18n.t("ko", "local_ai.idle_after_hours"), "hours", "1"));
        assertEquals("Download model (1.13GB)", LocalAiModels.format(AppI18n.t("en", "local_ai.download"),
                "size", LocalAiModels.formatGigabytes(LocalAiModels.LIGHT.sizeBytes)));
        assertTrue(AppI18n.t("ko", "local_ai.not_ready").contains("Local AI"));
    }
}
