package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

import java.util.Arrays;
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

    @Test public void theDeviceRequirementsAreTranslatedEverywhereAndKeepTheirTechnicalTerms() {
        String[][] requirements = {
                {LocalAiModels.NEEDS_ANDROID_9, "Android 9", "Local AI needs Android 9 or later.",
                        "Local AI를 사용하려면 Android 9 이상이 필요합니다."},
                {LocalAiModels.NEEDS_ARM64, "64-bit ARM", "Local AI needs a 64-bit ARM device.",
                        "Local AI는 64-bit ARM 기기에서만 사용할 수 있습니다."}
        };
        assertEquals(22, AppI18n.UI_LANGUAGES.size());
        for (String[] requirement : requirements) {
            String key = requirement[0];
            assertTrue(key, Arrays.asList(LocalAiI18n.KEYS).contains(key));
            assertEquals(requirement[2], AppI18n.t("en", key));
            assertEquals(requirement[3], AppI18n.t("ko", key));
            for (AiLyricsSettings.Language language : AppI18n.UI_LANGUAGES) {
                String label = language.code + ":" + key;
                String value = AppI18n.t(language.code, key);
                assertNotEquals(label, key, value);
                assertFalse(label, value.trim().isEmpty());
                assertTrue(label + " keeps " + requirement[1], value.contains(requirement[1]));
                assertTrue(label + " keeps the product name", value.contains("Local AI"));
                if (!"en".equals(language.code)) {
                    assertNotEquals(label + " is translated", requirement[2], value);
                }
                // The error a translation request hands to the provider chain uses the same text.
                assertEquals(label, value, LocalAiProvider.messages(language.code).unsupportedDevice(key));
            }
        }
    }
}
