package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AiLyricsSettings {
    static final String PREFS_NAME = "ai_lyrics_settings";
    static final String KEY_TRANSLATION_ENABLED = "translation_enabled";
    static final String KEY_PRONUNCIATION_ENABLED = "pronunciation_enabled";
    static final String KEY_PROVIDER = "provider";
    static final String KEY_TARGET_LANG = "target_lang";
    static final String KEY_UI_LANG = "ui_lang";
    static final String KEY_OUTPUT_LANG = "output_lang";
    static final String KEY_PRONUNCIATION_LANG = "pronunciation_lang";
    static final String KEY_LANGUAGE_RULES = "language_rules_v2";
    static final String KEY_API_KEYS = "api_keys";
    static final String KEY_POLLINATIONS_ACCESS_TOKEN = "pollinations_access_token";
    static final String KEY_MODEL = "model";
    static final String KEY_BASE_URL = "base_url";
    static final String KEY_MAX_TOKENS = "max_tokens";
    static final String KEY_TEMPERATURE = "temperature";
    static final String KEY_PREVIEW_MODE = "preview_mode";
    static final String KEY_PREVIEW_ITEMS = "preview_items";
    static final String KEY_AUTO_INSTRUMENTAL_BREAK = "auto_instrumental_break";
    static final String KEY_INTERLUDE_LABELS_ENABLED = "interlude_labels_enabled";
    static final String KEY_SYNCED_LYRICS_KARAOKE_ANIMATION = "synced_lyrics_karaoke_animation";
    static final String KEY_KARAOKE_BOUNCE_EFFECT = "karaoke_bounce_effect";
    static final String KEY_BACKGROUND_MODE = "background_mode";
    static final String KEY_BACKGROUND_BRIGHTNESS = "background_brightness";
    static final String KEY_BACKGROUND_BLUR = "background_blur";
    static final String KEY_BACKGROUND_NOISE = "background_noise";
    static final String KEY_BACKGROUND_REDUCE_MOTION = "background_reduce_motion";
    static final String KEY_BACKGROUND_SOLID_COLOR = "background_solid_color";
    static final String KEY_BACKGROUND_VIDEO_SCALE = "background_video_scale";
    static final String KEY_LANDSCAPE_AUTO_HIDE_CONTROLS = "landscape_auto_hide_controls";
    static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    static final String KEY_TRACK_SYNC_OFFSETS = "track_sync_offsets_v1";
    static final String KEY_TRACK_VIDEO_SYNC_OFFSETS = "track_video_sync_offsets_v1";
    static final String KEY_SPOTIFY_CLIENT_ID = "spotify_client_id";
    static final String KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret";
    static final String KEY_METADATA_TRANSLATION_ENABLED = "metadata_translation_enabled";
    static final String KEY_JAPANESE_FURIGANA_ENABLED = "japanese_furigana_enabled";
    static final String KEY_TYPOGRAPHY_SETTINGS = "typography_settings_v1";
    static final String KEY_SPEAKER_COLOR_SETTINGS = "speaker_color_settings_v1";

    static final String DEFAULT_SOURCE_LANG = "default";
    static final String PREVIEW_MODE_ORIGINAL = "original";
    static final String PREVIEW_MODE_TRANSLATION = "translation";
    static final String PREVIEW_MODE_PRONUNCIATION = "pronunciation";
    static final String BACKGROUND_MODE_GRADIENT = "gradient-background";
    static final String BACKGROUND_MODE_BLUR_GRADIENT = "blur-gradient-background";
    static final String BACKGROUND_MODE_VIDEO = "video-background";
    static final String BACKGROUND_MODE_SOLID = "solid-background";
    static final String OUTPUT_LANG_SAME_UI = "same_ui";
    static final int PREVIEW_ITEM_NONE = 0;
    static final int PREVIEW_ITEM_ORIGINAL = 1;
    static final int PREVIEW_ITEM_PRONUNCIATION = 1 << 1;
    static final int PREVIEW_ITEM_TRANSLATION = 1 << 2;
    static final String TYPO_MAIN_TITLE = "main_title";
    static final String TYPO_MAIN_ARTIST = "main_artist";
    static final String TYPO_MAIN_PREVIEW_ORIGINAL = "main_preview_original";
    static final String TYPO_MAIN_PREVIEW_PRONUNCIATION = "main_preview_pronunciation";
    static final String TYPO_MAIN_PREVIEW_TRANSLATION = "main_preview_translation";
    static final String TYPO_LYRICS_HEADER_TITLE = "lyrics_header_title";
    static final String TYPO_LYRICS_HEADER_ARTIST = "lyrics_header_artist";
    static final String TYPO_LYRICS_ORIGINAL = "lyrics_original";
    static final String TYPO_LYRICS_PRONUNCIATION = "lyrics_pronunciation";
    static final String TYPO_LYRICS_TRANSLATION = "lyrics_translation";
    static final String TYPO_WEIGHT_REGULAR = "regular";
    static final String TYPO_WEIGHT_SEMIBOLD = "semibold";
    static final String TYPO_WEIGHT_BOLD = "bold";
    static final String SPEAKER_COLOR_NORMAL = "normal";
    private static final String DEFAULT_PROVIDER = "gemini";
    private static final String DEFAULT_TARGET_LANG_RULES = OUTPUT_LANG_SAME_UI;
    private static final String DEFAULT_BACKGROUND_MODE = BACKGROUND_MODE_GRADIENT;
    private static final String DEFAULT_SOLID_BACKGROUND_COLOR = "#1e3a8a";

    static final List<Provider> PROVIDERS = Collections.unmodifiableList(Arrays.asList(
            new Provider(
                    "gemini",
                    "Google Gemini",
                    "Google AI Studio API 사용",
                    "https://generativelanguage.googleapis.com/v1beta",
                    "gemini-2.5-flash",
                    "https://aistudio.google.com/apikey"
            ),
            new Provider(
                    "chatgpt",
                    "OpenAI ChatGPT",
                    "OpenAI 호환 API 지원",
                    "https://api.openai.com/v1",
                    "gpt-4o-mini",
                    "https://platform.openai.com/api-keys"
            ),
            new Provider(
                    "claude",
                    "Anthropic Claude",
                    "Claude Messages API 사용",
                    "https://api.anthropic.com/v1",
                    "claude-sonnet-4-20250514",
                    "https://console.anthropic.com/settings/keys"
            ),
            new Provider(
                    "openrouter",
                    "OpenRouter",
                    "여러 AI 모델 라우팅",
                    "https://openrouter.ai/api/v1",
                    "anthropic/claude-3.5-sonnet",
                    "https://openrouter.ai/keys"
            ),
            new Provider(
                    "groq",
                    "Groq",
                    "빠른 OpenAI 호환 추론",
                    "https://api.groq.com/openai/v1",
                    "llama-3.3-70b-versatile",
                    "https://console.groq.com/keys"
            ),
            new Provider(
                    "perplexity",
                    "Perplexity",
                    "Sonar API 사용",
                    "https://api.perplexity.ai",
                    "sonar-pro",
                    "https://www.perplexity.ai/settings/api"
            ),
            new Provider(
                    "pollinations",
                    "Pollinations.ai",
                    "Pollinations OpenAI 호환 API",
                    "https://gen.pollinations.ai",
                    "openai",
                    "https://enter.pollinations.ai"
            )
    ));
    static final List<BackgroundMode> BACKGROUND_MODES = Collections.unmodifiableList(Arrays.asList(
            new BackgroundMode(BACKGROUND_MODE_GRADIENT, "앨범 커버", "현재 앨범 커버를 크게 블러 처리해 배경으로 사용합니다."),
            new BackgroundMode(BACKGROUND_MODE_BLUR_GRADIENT, "블러 그라데이션", "앨범 색상을 추출해 움직이는 블러 그라데이션을 만듭니다."),
            new BackgroundMode(BACKGROUND_MODE_VIDEO, "영상", "ivLyrics YouTube 영상 정보를 불러와 실제 영상을 배경으로 재생합니다."),
            new BackgroundMode(BACKGROUND_MODE_SOLID, "단색", "사용자 지정 단색 배경을 사용합니다.")
    ));
    static final List<TypographySlot> TYPOGRAPHY_SLOTS = Collections.unmodifiableList(Arrays.asList(
            new TypographySlot(TYPO_MAIN_TITLE, "typography.slot.main_title", "typography.slot.main_title_desc", 100, TYPO_WEIGHT_BOLD),
            new TypographySlot(TYPO_MAIN_ARTIST, "typography.slot.main_artist", "typography.slot.main_artist_desc", 100, TYPO_WEIGHT_REGULAR),
            new TypographySlot(TYPO_MAIN_PREVIEW_ORIGINAL, "typography.slot.main_preview_original", "typography.slot.main_preview_original_desc", 100, TYPO_WEIGHT_SEMIBOLD),
            new TypographySlot(TYPO_MAIN_PREVIEW_PRONUNCIATION, "typography.slot.main_preview_pronunciation", "typography.slot.main_preview_pronunciation_desc", 100, TYPO_WEIGHT_SEMIBOLD),
            new TypographySlot(TYPO_MAIN_PREVIEW_TRANSLATION, "typography.slot.main_preview_translation", "typography.slot.main_preview_translation_desc", 100, TYPO_WEIGHT_SEMIBOLD),
            new TypographySlot(TYPO_LYRICS_HEADER_TITLE, "typography.slot.lyrics_header_title", "typography.slot.lyrics_header_title_desc", 100, TYPO_WEIGHT_BOLD),
            new TypographySlot(TYPO_LYRICS_HEADER_ARTIST, "typography.slot.lyrics_header_artist", "typography.slot.lyrics_header_artist_desc", 100, TYPO_WEIGHT_REGULAR),
            new TypographySlot(TYPO_LYRICS_ORIGINAL, "typography.slot.lyrics_original", "typography.slot.lyrics_original_desc", 100, TYPO_WEIGHT_SEMIBOLD),
            new TypographySlot(TYPO_LYRICS_PRONUNCIATION, "typography.slot.lyrics_pronunciation", "typography.slot.lyrics_pronunciation_desc", 100, TYPO_WEIGHT_SEMIBOLD),
            new TypographySlot(TYPO_LYRICS_TRANSLATION, "typography.slot.lyrics_translation", "typography.slot.lyrics_translation_desc", 100, TYPO_WEIGHT_SEMIBOLD)
    ));
    static final List<SpeakerColorSlot> SPEAKER_COLOR_SLOTS = Collections.unmodifiableList(Arrays.asList(
            new SpeakerColorSlot(SPEAKER_COLOR_NORMAL, "speaker_color.normal", "#ffffff"),
            new SpeakerColorSlot("duet1", "speaker_color.duet", "#eadfff"),
            new SpeakerColorSlot("duet2", "speaker_color.duet", "#e2d2ff"),
            new SpeakerColorSlot("duet3", "speaker_color.duet", "#f0e8ff"),
            new SpeakerColorSlot("duet4", "speaker_color.duet", "#dec9ff"),
            new SpeakerColorSlot("duet5", "speaker_color.duet", "#e9dcff"),
            new SpeakerColorSlot("male1", "speaker_color.male", "#e6f2ff"),
            new SpeakerColorSlot("male2", "speaker_color.male", "#d7ecff"),
            new SpeakerColorSlot("male3", "speaker_color.male", "#edf7ff"),
            new SpeakerColorSlot("male4", "speaker_color.male", "#dbe7ff"),
            new SpeakerColorSlot("male5", "speaker_color.male", "#e2f8ff"),
            new SpeakerColorSlot("female1", "speaker_color.female", "#ffe7ef"),
            new SpeakerColorSlot("female2", "speaker_color.female", "#ffe0e8"),
            new SpeakerColorSlot("female3", "speaker_color.female", "#fff0f5"),
            new SpeakerColorSlot("female4", "speaker_color.female", "#ffdfe0"),
            new SpeakerColorSlot("female5", "speaker_color.female", "#fbe5ff")
    ));
    static final List<Language> SUPPORTED_LANGUAGES = Collections.unmodifiableList(Arrays.asList(
            new Language("ko", "Korean", "한국어", "Korean Hangul pronunciation, e.g. こんにちは -> 콘니치와"),
            new Language("en", "English", "English", "English romanization"),
            new Language("zh-CN", "Simplified Chinese", "简体中文", "Chinese characters for pronunciation"),
            new Language("zh-TW", "Traditional Chinese", "繁體中文", "Chinese characters for pronunciation"),
            new Language("ja", "Japanese", "日本語", "Japanese Katakana pronunciation"),
            new Language("hi", "Hindi", "हिन्दी", "Hindi Devanagari pronunciation"),
            new Language("es", "Spanish", "Español", "Spanish phonetic spelling"),
            new Language("fr", "French", "Français", "French phonetic spelling"),
            new Language("ar", "Arabic", "العربية", "Arabic script pronunciation"),
            new Language("fa", "Persian", "فارسی", "Persian script pronunciation"),
            new Language("de", "German", "Deutsch", "German phonetic spelling"),
            new Language("ru", "Russian", "Русский", "Russian Cyrillic pronunciation"),
            new Language("sv", "Swedish", "Svenska", "Swedish phonetic spelling"),
            new Language("pt", "Portuguese", "Português", "Portuguese phonetic spelling"),
            new Language("bn", "Bengali", "বাংলা", "Bengali script pronunciation"),
            new Language("it", "Italian", "Italiano", "Italian phonetic spelling"),
            new Language("th", "Thai", "ภาษาไทย", "Thai script pronunciation"),
            new Language("vi", "Vietnamese", "Tiếng Việt", "Vietnamese phonetic spelling"),
            new Language("id", "Indonesian", "Bahasa Indonesia", "Indonesian phonetic spelling"),
            new Language("ms", "Malay", "Bahasa Melayu", "Malay phonetic spelling"),
            new Language("tr", "Turkish", "Türkçe", "Turkish phonetic spelling")
    ));
    private static final Map<String, Language> LANGUAGE_BY_CODE = buildLanguageMap();

    private final SharedPreferences prefs;

    AiLyricsSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    Snapshot snapshot() {
        String providerId = prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER);
        Provider provider = providerById(providerId);
        String baseUrl = prefs.getString(KEY_BASE_URL, provider.defaultBaseUrl);
        String model = prefs.getString(KEY_MODEL, provider.defaultModel);
        RuleConfig ruleConfig = loadRuleConfig();
        String outputLang = storedOutputLanguage(ruleConfig);
        ruleConfig = ruleConfig.withTarget(outputLang);
        return new Snapshot(
                normalizedUiLanguage(prefs.getString(KEY_UI_LANG, autoTargetLanguage())),
                outputLang,
                provider,
                ruleConfig.defaultRule,
                ruleConfig.languageRules,
                prefs.getString(KEY_API_KEYS, ""),
                prefs.getString(KEY_POLLINATIONS_ACCESS_TOKEN, ""),
                baseUrl == null || baseUrl.trim().isEmpty() ? provider.defaultBaseUrl : baseUrl.trim(),
                model == null || model.trim().isEmpty() ? provider.defaultModel : model.trim(),
                Math.max(256, prefs.getInt(KEY_MAX_TOKENS, 16000)),
                clampFloat(prefs.getFloat(KEY_TEMPERATURE, 0.3f), 0f, 2f),
                normalizePreviewMode(prefs.getString(KEY_PREVIEW_MODE, PREVIEW_MODE_ORIGINAL)),
                normalizePreviewItems(prefs.contains(KEY_PREVIEW_ITEMS)
                        ? prefs.getInt(KEY_PREVIEW_ITEMS, PREVIEW_ITEM_ORIGINAL)
                        : previewItemsForMode(prefs.getString(KEY_PREVIEW_MODE, PREVIEW_MODE_ORIGINAL))),
                prefs.getBoolean(KEY_AUTO_INSTRUMENTAL_BREAK, true),
                prefs.getBoolean(KEY_INTERLUDE_LABELS_ENABLED, true),
                prefs.getBoolean(KEY_SYNCED_LYRICS_KARAOKE_ANIMATION, true),
                prefs.getBoolean(KEY_KARAOKE_BOUNCE_EFFECT, true),
                backgroundSettings(),
                prefs.getBoolean(KEY_LANDSCAPE_AUTO_HIDE_CONTROLS, true),
                prefs.getBoolean(KEY_KEEP_SCREEN_ON, false),
                prefs.getBoolean(KEY_METADATA_TRANSLATION_ENABLED, true),
                prefs.getBoolean(KEY_JAPANESE_FURIGANA_ENABLED, false),
                typographySettings(),
                speakerColorSettings(),
                prefs.getString(KEY_SPOTIFY_CLIENT_ID, ""),
                prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, "")
        );
    }

    void setUiLang(String lang) {
        prefs.edit().putString(KEY_UI_LANG, normalizedUiLanguage(lang)).apply();
    }

    void setPronunciationLang(String lang) {
        setOutputLang(lang);
    }

    void setMetadataTranslationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_METADATA_TRANSLATION_ENABLED, enabled).apply();
    }

    void setJapaneseFuriganaEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_JAPANESE_FURIGANA_ENABLED, enabled).apply();
    }

    void setTypographyStyle(String slotId, int sizePercent, String weight) {
        TypographySettings current = typographySettings();
        Map<String, TypographyStyle> next = new LinkedHashMap<>(current.styles);
        TypographySlot slot = typographySlotById(slotId);
        next.put(slot.id, new TypographyStyle(
                clampInt(sizePercent, 70, 160),
                normalizeTypographyWeight(weight, slot.defaultWeight)
        ));
        saveTypographySettings(new TypographySettings(next));
    }

    void setSpeakerColors(Map<String, String> colors) {
        saveSpeakerColorSettings(new SpeakerColorSettings(colors));
    }

    void resetSpeakerColors() {
        prefs.edit().remove(KEY_SPEAKER_COLOR_SETTINGS).apply();
    }

    void setTranslationEnabled(boolean enabled) {
        Snapshot snapshot = snapshot();
        LanguageRule rule = snapshot.defaultRule;
        setLanguageRule(DEFAULT_SOURCE_LANG, enabled, rule.pronunciationEnabled, rule.targetLang);
    }

    void setPronunciationEnabled(boolean enabled) {
        Snapshot snapshot = snapshot();
        LanguageRule rule = snapshot.defaultRule;
        setLanguageRule(DEFAULT_SOURCE_LANG, rule.translationEnabled, enabled, rule.targetLang);
    }

    void setProvider(String providerId) {
        Provider provider = providerById(providerId);
        prefs.edit()
                .putString(KEY_PROVIDER, provider.id)
                .putString(KEY_BASE_URL, provider.defaultBaseUrl)
                .putString(KEY_MODEL, provider.defaultModel)
                .apply();
    }

    void setTargetLang(String lang) {
        setOutputLang(lang);
    }

    void setTranslationLang(String lang) {
        setOutputLang(lang);
    }

    void setOutputLang(String lang) {
        Snapshot snapshot = snapshot();
        String target = normalizeOutputLanguage(lang);
        LanguageRule defaultRule = new LanguageRule(
                DEFAULT_SOURCE_LANG,
                snapshot.defaultRule.translationEnabled,
                snapshot.defaultRule.pronunciationEnabled,
                target
        );
        Map<String, LanguageRule> rules = new LinkedHashMap<>();
        for (Map.Entry<String, LanguageRule> entry : snapshot.languageRules.entrySet()) {
            LanguageRule rule = entry.getValue();
            rules.put(entry.getKey(), new LanguageRule(
                    rule.sourceLang,
                    rule.translationEnabled,
                    rule.pronunciationEnabled,
                    target
            ));
        }
        saveRuleConfig(defaultRule, rules);
        prefs.edit()
                .putString(KEY_OUTPUT_LANG, target)
                .remove(KEY_PRONUNCIATION_LANG)
                .apply();
    }

    void setLanguageRule(String sourceLang, boolean translationEnabled, boolean pronunciationEnabled, String targetLang) {
        Snapshot snapshot = snapshot();
        String sourceKey = normalizeSourceLanguageKey(sourceLang);
        String target = DEFAULT_SOURCE_LANG.equals(sourceKey)
                ? normalizeTargetLanguage(targetLang)
                : snapshot.defaultRule.targetLang;
        LanguageRule nextRule = new LanguageRule(
                sourceKey,
                translationEnabled,
                pronunciationEnabled,
                target
        );
        LanguageRule defaultRule = snapshot.defaultRule;
        Map<String, LanguageRule> rules = new LinkedHashMap<>(snapshot.languageRules);
        if (DEFAULT_SOURCE_LANG.equals(sourceKey)) {
            defaultRule = nextRule;
        } else {
            rules.put(sourceKey, nextRule);
        }
        saveRuleConfig(defaultRule, rules);
    }

    void setApiKeys(String apiKeys) {
        prefs.edit().putString(KEY_API_KEYS, apiKeys == null ? "" : apiKeys.trim()).apply();
    }

    void setPollinationsAccessToken(String accessToken) {
        prefs.edit()
                .putString(KEY_POLLINATIONS_ACCESS_TOKEN, accessToken == null ? "" : accessToken.trim())
                .apply();
    }

    void clearPollinationsAccessToken() {
        prefs.edit().remove(KEY_POLLINATIONS_ACCESS_TOKEN).apply();
    }

    void setModel(String model) {
        prefs.edit().putString(KEY_MODEL, model == null ? "" : model.trim()).apply();
    }

    void setBaseUrl(String baseUrl) {
        prefs.edit().putString(KEY_BASE_URL, baseUrl == null ? "" : baseUrl.trim()).apply();
    }

    void setMaxTokens(int maxTokens) {
        prefs.edit().putInt(KEY_MAX_TOKENS, Math.max(256, maxTokens)).apply();
    }

    void setTemperature(float temperature) {
        prefs.edit().putFloat(KEY_TEMPERATURE, clampFloat(temperature, 0f, 2f)).apply();
    }

    void setPreviewMode(String previewMode) {
        String normalized = normalizePreviewMode(previewMode);
        prefs.edit()
                .putString(KEY_PREVIEW_MODE, normalized)
                .putInt(KEY_PREVIEW_ITEMS, previewItemsForMode(normalized))
                .apply();
    }

    void setPreviewItems(int previewItems) {
        prefs.edit().putInt(KEY_PREVIEW_ITEMS, normalizePreviewItems(previewItems)).apply();
    }

    void setAutoInstrumentalBreakEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_INSTRUMENTAL_BREAK, enabled).apply();
    }

    void setInterludeLabelsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_INTERLUDE_LABELS_ENABLED, enabled).apply();
    }

    void setSyncedLyricsKaraokeAnimationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SYNCED_LYRICS_KARAOKE_ANIMATION, enabled).apply();
    }

    void setKaraokeBounceEffectEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_KARAOKE_BOUNCE_EFFECT, enabled).apply();
    }

    void setBackgroundMode(String mode) {
        prefs.edit().putString(KEY_BACKGROUND_MODE, normalizeBackgroundMode(mode)).apply();
    }

    void setBackgroundBrightness(int brightness) {
        prefs.edit().putInt(KEY_BACKGROUND_BRIGHTNESS, clampInt(brightness, 0, 100)).apply();
    }

    void setBackgroundBlur(int blur) {
        prefs.edit().putInt(KEY_BACKGROUND_BLUR, clampInt(blur, 0, 100)).apply();
    }

    void setBackgroundVideoScale(int scalePercent) {
        prefs.edit().putInt(KEY_BACKGROUND_VIDEO_SCALE, clampInt(scalePercent, 100, 180)).apply();
    }

    void setBackgroundNoise(boolean enabled) {
        prefs.edit().putBoolean(KEY_BACKGROUND_NOISE, enabled).apply();
    }

    void setBackgroundReduceMotion(boolean enabled) {
        prefs.edit().putBoolean(KEY_BACKGROUND_REDUCE_MOTION, enabled).apply();
    }

    void setBackgroundSolidColor(String color) {
        prefs.edit().putString(KEY_BACKGROUND_SOLID_COLOR, normalizeHexColor(color, DEFAULT_SOLID_BACKGROUND_COLOR)).apply();
    }

    void setLandscapeAutoHideControls(boolean enabled) {
        prefs.edit().putBoolean(KEY_LANDSCAPE_AUTO_HIDE_CONTROLS, enabled).apply();
    }

    void setKeepScreenOn(boolean enabled) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply();
    }

    void setSpotifyApiCredentials(String clientId, String clientSecret) {
        prefs.edit()
                .putString(KEY_SPOTIFY_CLIENT_ID, clientId == null ? "" : clientId.trim())
                .putString(KEY_SPOTIFY_CLIENT_SECRET, clientSecret == null ? "" : clientSecret.trim())
                .apply();
    }

    int trackSyncOffsetMs(String trackKey) {
        return trackOffsetMs(KEY_TRACK_SYNC_OFFSETS, trackKey);
    }

    void setTrackSyncOffsetMs(String trackKey, int offsetMs) {
        setTrackOffsetMs(KEY_TRACK_SYNC_OFFSETS, trackKey, offsetMs);
    }

    int trackVideoSyncOffsetMs(String trackKey) {
        return trackOffsetMs(KEY_TRACK_VIDEO_SYNC_OFFSETS, trackKey);
    }

    void setTrackVideoSyncOffsetMs(String trackKey, int offsetMs) {
        setTrackOffsetMs(KEY_TRACK_VIDEO_SYNC_OFFSETS, trackKey, offsetMs);
    }

    private int trackOffsetMs(String prefsKey, String trackKey) {
        String key = trackKey == null ? "" : trackKey.trim();
        if (key.isEmpty()) {
            return 0;
        }
        try {
            JSONObject object = new JSONObject(prefs.getString(prefsKey, "{}"));
            return clampInt(object.optInt(key, 0), -10000, 10000);
        } catch (JSONException ignored) {
            prefs.edit().remove(prefsKey).apply();
            return 0;
        }
    }

    private void setTrackOffsetMs(String prefsKey, String trackKey, int offsetMs) {
        String key = trackKey == null ? "" : trackKey.trim();
        if (key.isEmpty()) {
            return;
        }
        int safeOffset = clampInt(offsetMs, -10000, 10000);
        try {
            JSONObject object = new JSONObject(prefs.getString(prefsKey, "{}"));
            if (safeOffset == 0) {
                object.remove(key);
            } else {
                object.put(key, safeOffset);
            }
            prefs.edit().putString(prefsKey, object.toString()).apply();
        } catch (JSONException ignored) {
            JSONObject object = new JSONObject();
            try {
                if (safeOffset != 0) {
                    object.put(key, safeOffset);
                }
            } catch (JSONException ignoredAgain) {
            }
            prefs.edit().putString(prefsKey, object.toString()).apply();
        }
    }

    private BackgroundSettings backgroundSettings() {
        return new BackgroundSettings(
                normalizeBackgroundMode(prefs.getString(KEY_BACKGROUND_MODE, DEFAULT_BACKGROUND_MODE)),
                clampInt(prefs.getInt(KEY_BACKGROUND_BRIGHTNESS, 30), 0, 100),
                clampInt(prefs.getInt(KEY_BACKGROUND_BLUR, 20), 0, 100),
                prefs.getBoolean(KEY_BACKGROUND_NOISE, false),
                prefs.getBoolean(KEY_BACKGROUND_REDUCE_MOTION, false),
                normalizeHexColor(prefs.getString(KEY_BACKGROUND_SOLID_COLOR, DEFAULT_SOLID_BACKGROUND_COLOR), DEFAULT_SOLID_BACKGROUND_COLOR),
                clampInt(prefs.getInt(KEY_BACKGROUND_VIDEO_SCALE, 100), 100, 180)
        );
    }

    private TypographySettings typographySettings() {
        Map<String, TypographyStyle> styles = new LinkedHashMap<>();
        String stored = prefs.getString(KEY_TYPOGRAPHY_SETTINGS, "");
        JSONObject object = null;
        if (stored != null && !stored.trim().isEmpty()) {
            try {
                object = new JSONObject(stored);
            } catch (JSONException ignored) {
            }
        }
        for (TypographySlot slot : TYPOGRAPHY_SLOTS) {
            TypographyStyle style = null;
            if (object != null) {
                JSONObject slotObject = object.optJSONObject(slot.id);
                if (slotObject != null) {
                    style = new TypographyStyle(
                            slotObject.optInt("size", slot.defaultSizePercent),
                            slotObject.optString("weight", slot.defaultWeight),
                            slot
                    );
                }
            }
            styles.put(slot.id, style == null ? slot.defaultStyle() : style);
        }
        return new TypographySettings(styles);
    }

    private SpeakerColorSettings speakerColorSettings() {
        Map<String, String> colors = new LinkedHashMap<>();
        String stored = prefs.getString(KEY_SPEAKER_COLOR_SETTINGS, "");
        JSONObject object = null;
        if (stored != null && !stored.trim().isEmpty()) {
            try {
                object = new JSONObject(stored);
            } catch (JSONException ignored) {
            }
        }
        for (SpeakerColorSlot slot : SPEAKER_COLOR_SLOTS) {
            String color = object == null ? "" : object.optString(slot.id, "");
            colors.put(slot.id, normalizeHexColor(color, slot.defaultColor));
        }
        return new SpeakerColorSettings(colors);
    }

    private void saveTypographySettings(TypographySettings typography) {
        try {
            JSONObject object = new JSONObject();
            TypographySettings safe = typography == null ? TypographySettings.defaults() : typography;
            for (TypographySlot slot : TYPOGRAPHY_SLOTS) {
                TypographyStyle style = safe.style(slot.id);
                JSONObject slotObject = new JSONObject();
                slotObject.put("size", style.sizePercent);
                slotObject.put("weight", style.weight);
                object.put(slot.id, slotObject);
            }
            prefs.edit().putString(KEY_TYPOGRAPHY_SETTINGS, object.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private void saveSpeakerColorSettings(SpeakerColorSettings settings) {
        try {
            JSONObject object = new JSONObject();
            SpeakerColorSettings safe = settings == null ? SpeakerColorSettings.defaults() : settings;
            for (SpeakerColorSlot slot : SPEAKER_COLOR_SLOTS) {
                object.put(slot.id, safe.hex(slot.id));
            }
            prefs.edit().putString(KEY_SPEAKER_COLOR_SETTINGS, object.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private RuleConfig loadRuleConfig() {
        boolean legacyTranslation = prefs.getBoolean(KEY_TRANSLATION_ENABLED, false);
        boolean legacyPronunciation = prefs.getBoolean(KEY_PRONUNCIATION_ENABLED, false);
        String legacyTarget = normalizeTargetRules(prefs.getString(KEY_TARGET_LANG, DEFAULT_TARGET_LANG_RULES));
        Map<String, String> legacyTargetRules = parseTargetRules(legacyTarget);
        String defaultTarget = firstNonEmpty(
                legacyTargetRules.get("default"),
                legacyTargetRules.get("*"),
                legacyTargetRules.isEmpty() ? legacyTarget : DEFAULT_TARGET_LANG_RULES
        );
        LanguageRule defaultRule = new LanguageRule(
                DEFAULT_SOURCE_LANG,
                legacyTranslation,
                legacyPronunciation,
                normalizeTargetLanguage(defaultTarget)
        );
        Map<String, LanguageRule> rules = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : legacyTargetRules.entrySet()) {
            String source = normalizeSourceLanguageKey(entry.getKey());
            if (DEFAULT_SOURCE_LANG.equals(source)) {
                continue;
            }
            rules.put(source, new LanguageRule(
                    source,
                    legacyTranslation,
                    legacyPronunciation,
                    normalizeTargetLanguage(entry.getValue())
            ));
        }

        String stored = prefs.getString(KEY_LANGUAGE_RULES, "");
        if (stored == null || stored.trim().isEmpty()) {
            return new RuleConfig(defaultRule, rules);
        }
        try {
            JSONObject object = new JSONObject(stored);
            JSONObject defaultObject = object.optJSONObject("default");
            if (defaultObject != null) {
                defaultRule = parseRule(DEFAULT_SOURCE_LANG, defaultObject, defaultRule);
            }
            JSONObject rulesObject = object.optJSONObject("rules");
            if (rulesObject != null) {
                Iterator<String> keys = rulesObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String source = normalizeSourceLanguageKey(key);
                    if (DEFAULT_SOURCE_LANG.equals(source)) {
                        continue;
                    }
                    JSONObject ruleObject = rulesObject.optJSONObject(key);
                    if (ruleObject == null) {
                        continue;
                    }
                    LanguageRule fallback = rules.containsKey(source)
                            ? rules.get(source)
                            : new LanguageRule(source, defaultRule.translationEnabled, defaultRule.pronunciationEnabled, defaultRule.targetLang);
                    rules.put(source, parseRule(source, ruleObject, fallback));
                }
            }
        } catch (JSONException ignored) {
        }
        return new RuleConfig(defaultRule, rules);
    }

    private String storedOutputLanguage(RuleConfig ruleConfig) {
        if (prefs.contains(KEY_OUTPUT_LANG)) {
            return normalizeOutputLanguage(prefs.getString(KEY_OUTPUT_LANG, OUTPUT_LANG_SAME_UI));
        }
        String target = ruleConfig == null || ruleConfig.defaultRule == null ? "" : ruleConfig.defaultRule.targetLang;
        if (!target.trim().isEmpty() && !OUTPUT_LANG_SAME_UI.equalsIgnoreCase(target) && !"auto".equalsIgnoreCase(target)) {
            return normalizeOutputLanguage(target);
        }
        if (prefs.contains(KEY_PRONUNCIATION_LANG)) {
            return normalizeOutputLanguage(prefs.getString(KEY_PRONUNCIATION_LANG, OUTPUT_LANG_SAME_UI));
        }
        return OUTPUT_LANG_SAME_UI;
    }

    private void saveRuleConfig(LanguageRule defaultRule, Map<String, LanguageRule> rules) {
        try {
            JSONObject object = new JSONObject();
            object.put("default", ruleToJson(defaultRule));
            JSONObject rulesObject = new JSONObject();
            for (Map.Entry<String, LanguageRule> entry : rules.entrySet()) {
                rulesObject.put(entry.getKey(), ruleToJson(entry.getValue()));
            }
            object.put("rules", rulesObject);
            prefs.edit().putString(KEY_LANGUAGE_RULES, object.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private static JSONObject ruleToJson(LanguageRule rule) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("translation", rule.translationEnabled);
        object.put("pronunciation", rule.pronunciationEnabled);
        object.put("target", normalizeTargetLanguage(rule.targetLang));
        return object;
    }

    private static LanguageRule parseRule(String source, JSONObject object, LanguageRule fallback) {
        return new LanguageRule(
                source,
                object.optBoolean("translation", fallback.translationEnabled),
                object.optBoolean("pronunciation", fallback.pronunciationEnabled),
                normalizeTargetLanguage(object.optString("target", fallback.targetLang))
        );
    }

    static Provider providerById(String providerId) {
        String normalized = providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
        for (Provider provider : PROVIDERS) {
            if (provider.id.equals(normalized)) {
                return provider;
            }
        }
        return PROVIDERS.get(0);
    }

    static Language languageInfo(String lang) {
        Language language = LANGUAGE_BY_CODE.get(normalizeLanguageCode(lang).toLowerCase(Locale.ROOT));
        return language == null ? LANGUAGE_BY_CODE.get("en") : language;
    }

    static String languageLabel(String lang) {
        String normalized = normalizeLanguageCode(lang);
        Language language = LANGUAGE_BY_CODE.get(normalized.toLowerCase(Locale.ROOT));
        if (language == null) {
            return normalized.isEmpty() ? "Auto" : normalized;
        }
        return language.nativeName + " · " + language.name;
    }

    static String normalizeLanguageCode(String lang) {
        String value = lang == null ? "" : lang.trim();
        if (value.isEmpty()) {
            return "";
        }
        String lower = value.replace('_', '-').toLowerCase(Locale.ROOT);
        switch (lower) {
            case "jp":
                return "ja";
            case "kr":
                return "ko";
            case "cn":
            case "zh":
            case "zh-hans":
            case "zh-cn":
            case "zh-sg":
                return "zh-CN";
            case "tw":
            case "hk":
            case "zh-hant":
            case "zh-tw":
            case "zh-hk":
                return "zh-TW";
            default:
                for (Language language : SUPPORTED_LANGUAGES) {
                    if (language.code.equalsIgnoreCase(lower) || language.code.toLowerCase(Locale.ROOT).equals(lower)) {
                        return language.code;
                    }
                }
                int dash = lower.indexOf('-');
                String base = dash > 0 ? lower.substring(0, dash) : lower;
                for (Language language : SUPPORTED_LANGUAGES) {
                    if (language.code.equalsIgnoreCase(base)) {
                        return language.code;
                    }
                }
                return value;
        }
    }

    static String normalizeSourceLanguageKey(String lang) {
        String value = lang == null ? "" : lang.trim();
        if (value.isEmpty()
                || DEFAULT_SOURCE_LANG.equalsIgnoreCase(value)
                || "*".equals(value)
                || "all".equalsIgnoreCase(value)) {
            return DEFAULT_SOURCE_LANG;
        }
        String normalized = normalizeLanguageCode(value);
        return normalized.isEmpty() ? DEFAULT_SOURCE_LANG : normalized;
    }

    static String normalizeTargetLanguage(String lang) {
        return normalizeOutputLanguage(lang);
    }

    static String normalizeOutputLanguage(String lang) {
        String value = lang == null ? "" : lang.trim();
        if (value.isEmpty()
                || "auto".equalsIgnoreCase(value)
                || OUTPUT_LANG_SAME_UI.equalsIgnoreCase(value)
                || "ui".equalsIgnoreCase(value)
                || "ui_lang".equalsIgnoreCase(value)
                || "ui_language".equalsIgnoreCase(value)) {
            return DEFAULT_TARGET_LANG_RULES;
        }
        String normalized = normalizeLanguageCode(value);
        return LANGUAGE_BY_CODE.containsKey(normalized.toLowerCase(Locale.ROOT)) ? normalized : DEFAULT_TARGET_LANG_RULES;
    }

    static String normalizePreviewMode(String mode) {
        String value = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        if (PREVIEW_MODE_TRANSLATION.equals(value)) {
            return PREVIEW_MODE_TRANSLATION;
        }
        if (PREVIEW_MODE_PRONUNCIATION.equals(value)) {
            return PREVIEW_MODE_PRONUNCIATION;
        }
        return PREVIEW_MODE_ORIGINAL;
    }

    static int normalizePreviewItems(int previewItems) {
        int allowed = PREVIEW_ITEM_ORIGINAL | PREVIEW_ITEM_PRONUNCIATION | PREVIEW_ITEM_TRANSLATION;
        return previewItems & allowed;
    }

    static boolean previewItemEnabled(int previewItems, int item) {
        return (normalizePreviewItems(previewItems) & item) == item;
    }

    static String normalizeTypographyWeight(String weight) {
        return normalizeTypographyWeight(weight, TYPO_WEIGHT_SEMIBOLD);
    }

    static String normalizeTypographyWeight(String weight, String fallback) {
        String value = weight == null ? "" : weight.trim().toLowerCase(Locale.ROOT);
        if (TYPO_WEIGHT_REGULAR.equals(value) || TYPO_WEIGHT_SEMIBOLD.equals(value) || TYPO_WEIGHT_BOLD.equals(value)) {
            return value;
        }
        String safeFallback = fallback == null ? "" : fallback.trim().toLowerCase(Locale.ROOT);
        if (TYPO_WEIGHT_REGULAR.equals(safeFallback) || TYPO_WEIGHT_BOLD.equals(safeFallback)) {
            return safeFallback;
        }
        return TYPO_WEIGHT_SEMIBOLD;
    }

    static TypographySlot typographySlotById(String slotId) {
        String normalized = slotId == null ? "" : slotId.trim();
        for (TypographySlot slot : TYPOGRAPHY_SLOTS) {
            if (slot.id.equals(normalized)) {
                return slot;
            }
        }
        return TYPOGRAPHY_SLOTS.get(0);
    }

    static String normalizeBackgroundMode(String mode) {
        String value = mode == null ? "" : mode.trim().toLowerCase(Locale.ROOT);
        for (BackgroundMode backgroundMode : BACKGROUND_MODES) {
            if (backgroundMode.id.equals(value)) {
                return backgroundMode.id;
            }
        }
        return DEFAULT_BACKGROUND_MODE;
    }

    static String backgroundModeLabel(String mode) {
        String normalized = normalizeBackgroundMode(mode);
        for (BackgroundMode backgroundMode : BACKGROUND_MODES) {
            if (backgroundMode.id.equals(normalized)) {
                return backgroundMode.label;
            }
        }
        return BACKGROUND_MODES.get(0).label;
    }

    private static int previewItemsForMode(String mode) {
        String normalized = normalizePreviewMode(mode);
        if (PREVIEW_MODE_TRANSLATION.equals(normalized)) {
            return PREVIEW_ITEM_TRANSLATION;
        }
        if (PREVIEW_MODE_PRONUNCIATION.equals(normalized)) {
            return PREVIEW_ITEM_PRONUNCIATION;
        }
        return PREVIEW_ITEM_ORIGINAL;
    }

    static boolean isSameLanguage(String sourceLang, String targetLang) {
        String source = normalizeLanguageCode(sourceLang);
        String target = normalizeLanguageCode(targetLang);
        if (source.isEmpty() || target.isEmpty() || "auto".equalsIgnoreCase(target) || OUTPUT_LANG_SAME_UI.equalsIgnoreCase(target)) {
            return false;
        }
        return source.equalsIgnoreCase(target);
    }

    private static String normalizeTargetRules(String value) {
        String rules = value == null ? "" : value.trim();
        return rules.isEmpty() ? DEFAULT_TARGET_LANG_RULES : rules;
    }

    private static String normalizedUiLanguage(String lang) {
        String normalized = normalizeLanguageCode(lang);
        if (AppI18n.supports(normalized)) {
            return AppI18n.normalize(normalized);
        }
        String auto = autoTargetLanguage();
        return AppI18n.supports(auto) ? AppI18n.normalize(auto) : "en";
    }

    private static String normalizedPronunciationLanguage(String lang) {
        String normalized = normalizeLanguageCode(lang);
        return LANGUAGE_BY_CODE.containsKey(normalized.toLowerCase(Locale.ROOT))
                ? normalized
                : autoTargetLanguage();
    }

    private static String resolveOutputLanguage(String outputLang, String uiLang) {
        String normalized = normalizeOutputLanguage(outputLang);
        if (OUTPUT_LANG_SAME_UI.equalsIgnoreCase(normalized)) {
            String ui = normalizedUiLanguage(uiLang);
            return LANGUAGE_BY_CODE.containsKey(ui.toLowerCase(Locale.ROOT)) ? ui : autoTargetLanguage();
        }
        return normalizeLanguageCode(normalized);
    }

    static String defaultOutputLanguage() {
        return autoTargetLanguage();
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeHexColor(String color, String fallback) {
        String value = color == null ? "" : color.trim();
        if (value.matches("^#?[0-9a-fA-F]{6}$")) {
            return (value.startsWith("#") ? value : "#" + value).toLowerCase(Locale.ROOT);
        }
        return fallback;
    }

    static boolean isHexColor(String color) {
        String value = color == null ? "" : color.trim();
        return value.matches("^#?[0-9a-fA-F]{6}$");
    }

    static SpeakerColorSlot speakerColorSlotById(String slotId) {
        String normalized = slotId == null ? "" : slotId.trim();
        for (SpeakerColorSlot slot : SPEAKER_COLOR_SLOTS) {
            if (slot.id.equals(normalized)) {
                return slot;
            }
        }
        return SPEAKER_COLOR_SLOTS.get(0);
    }

    private static Map<String, Language> buildLanguageMap() {
        Map<String, Language> map = new LinkedHashMap<>();
        for (Language language : SUPPORTED_LANGUAGES) {
            map.put(language.code.toLowerCase(Locale.ROOT), language);
        }
        return Collections.unmodifiableMap(map);
    }

    private static String autoTargetLanguage() {
        Locale locale = Locale.getDefault();
        String candidate;
        if ("zh".equalsIgnoreCase(locale.getLanguage())) {
            String country = locale.getCountry();
            candidate = ("TW".equalsIgnoreCase(country) || "HK".equalsIgnoreCase(country) || "MO".equalsIgnoreCase(country))
                    ? "zh-TW"
                    : "zh-CN";
        } else {
            candidate = locale.getLanguage();
        }
        String normalized = normalizeLanguageCode(candidate);
        return LANGUAGE_BY_CODE.containsKey(normalized.toLowerCase(Locale.ROOT)) ? normalized : "en";
    }

    private static Map<String, String> parseTargetRules(String raw) {
        Map<String, String> rules = new LinkedHashMap<>();
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || !value.matches("(?s).*[=:].*")) {
            return rules;
        }
        String[] entries = value.split("[\\n;,]+");
        for (String entry : entries) {
            String item = entry.trim();
            if (item.isEmpty()) {
                continue;
            }
            int colon = item.indexOf(':');
            int equals = item.indexOf('=');
            int split = colon >= 0 && equals >= 0 ? Math.min(colon, equals) : Math.max(colon, equals);
            if (split <= 0 || split >= item.length() - 1) {
                continue;
            }
            String source = normalizeSourceLanguageKey(item.substring(0, split).trim());
            String target = normalizeTargetLanguage(item.substring(split + 1).trim());
            if (!source.isEmpty() && !target.isEmpty()) {
                rules.put(source, target);
            }
        }
        return rules;
    }

    static List<String> supportedLanguageLabels() {
        List<String> values = new ArrayList<>();
        values.add("auto");
        for (Language language : SUPPORTED_LANGUAGES) {
            values.add(language.code + " " + language.nativeName);
        }
        return values;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static final class RuleConfig {
        final LanguageRule defaultRule;
        final Map<String, LanguageRule> languageRules;

        RuleConfig(LanguageRule defaultRule, Map<String, LanguageRule> languageRules) {
            this.defaultRule = defaultRule;
            this.languageRules = Collections.unmodifiableMap(new LinkedHashMap<>(languageRules));
        }

        RuleConfig withTarget(String targetLang) {
            String target = normalizeOutputLanguage(targetLang);
            LanguageRule nextDefault = new LanguageRule(
                    defaultRule.sourceLang,
                    defaultRule.translationEnabled,
                    defaultRule.pronunciationEnabled,
                    target
            );
            Map<String, LanguageRule> nextRules = new LinkedHashMap<>();
            for (Map.Entry<String, LanguageRule> entry : languageRules.entrySet()) {
                LanguageRule rule = entry.getValue();
                nextRules.put(entry.getKey(), new LanguageRule(
                        rule.sourceLang,
                        rule.translationEnabled,
                        rule.pronunciationEnabled,
                        target
                ));
            }
            return new RuleConfig(nextDefault, nextRules);
        }
    }

    static final class Provider {
        final String id;
        final String label;
        final String description;
        final String defaultBaseUrl;
        final String defaultModel;
        final String apiKeyUrl;

        Provider(String id, String label, String description, String defaultBaseUrl, String defaultModel, String apiKeyUrl) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.defaultBaseUrl = defaultBaseUrl;
            this.defaultModel = defaultModel;
            this.apiKeyUrl = apiKeyUrl;
        }
    }

    static final class BackgroundMode {
        final String id;
        final String label;
        final String description;

        BackgroundMode(String id, String label, String description) {
            this.id = id;
            this.label = label;
            this.description = description;
        }
    }

    static final class BackgroundSettings {
        final String mode;
        final int brightness;
        final int blur;
        final boolean noise;
        final boolean reduceMotion;
        final String solidColor;
        final int videoScale;

        BackgroundSettings(String mode, int brightness, int blur, boolean noise, boolean reduceMotion, String solidColor, int videoScale) {
            this.mode = normalizeBackgroundMode(mode);
            this.brightness = clampInt(brightness, 0, 100);
            this.blur = clampInt(blur, 0, 100);
            this.noise = noise;
            this.reduceMotion = reduceMotion;
            this.solidColor = normalizeHexColor(solidColor, DEFAULT_SOLID_BACKGROUND_COLOR);
            this.videoScale = clampInt(videoScale, 100, 180);
        }
    }

    static final class TypographySlot {
        final String id;
        final String titleKey;
        final String descriptionKey;
        final int defaultSizePercent;
        final String defaultWeight;

        TypographySlot(String id, String titleKey, String descriptionKey, int defaultSizePercent, String defaultWeight) {
            this.id = id == null ? "" : id;
            this.titleKey = titleKey == null ? "" : titleKey;
            this.descriptionKey = descriptionKey == null ? "" : descriptionKey;
            this.defaultSizePercent = clampInt(defaultSizePercent, 70, 160);
            this.defaultWeight = normalizeTypographyWeight(defaultWeight, TYPO_WEIGHT_SEMIBOLD);
        }

        TypographyStyle defaultStyle() {
            return new TypographyStyle(defaultSizePercent, defaultWeight, this);
        }
    }

    static final class TypographyStyle {
        final int sizePercent;
        final String weight;

        TypographyStyle(int sizePercent, String weight) {
            this(sizePercent, weight, null);
        }

        TypographyStyle(int sizePercent, String weight, TypographySlot slot) {
            int fallbackSize = slot == null ? 100 : slot.defaultSizePercent;
            String fallbackWeight = slot == null ? TYPO_WEIGHT_SEMIBOLD : slot.defaultWeight;
            this.sizePercent = clampInt(sizePercent <= 0 ? fallbackSize : sizePercent, 70, 160);
            this.weight = normalizeTypographyWeight(weight, fallbackWeight);
        }

        float scale() {
            return sizePercent / 100f;
        }
    }

    static final class TypographySettings {
        final Map<String, TypographyStyle> styles;

        TypographySettings(Map<String, TypographyStyle> styles) {
            Map<String, TypographyStyle> values = new LinkedHashMap<>();
            for (TypographySlot slot : TYPOGRAPHY_SLOTS) {
                TypographyStyle style = styles == null ? null : styles.get(slot.id);
                values.put(slot.id, style == null ? slot.defaultStyle() : new TypographyStyle(style.sizePercent, style.weight, slot));
            }
            this.styles = Collections.unmodifiableMap(values);
        }

        static TypographySettings defaults() {
            return new TypographySettings(Collections.emptyMap());
        }

        TypographyStyle style(String slotId) {
            TypographySlot slot = typographySlotById(slotId);
            TypographyStyle style = styles.get(slot.id);
            return style == null ? slot.defaultStyle() : style;
        }
    }

    static final class SpeakerColorSlot {
        final String id;
        final String titleKey;
        final String defaultColor;

        SpeakerColorSlot(String id, String titleKey, String defaultColor) {
            this.id = id == null ? "" : id;
            this.titleKey = titleKey == null ? "" : titleKey;
            this.defaultColor = normalizeHexColor(defaultColor, "#ffffff");
        }

        int defaultColorInt() {
            return SpeakerColorSettings.colorInt(defaultColor, Color.WHITE);
        }
    }

    static final class SpeakerColorSettings {
        final Map<String, String> colors;

        SpeakerColorSettings(Map<String, String> colors) {
            Map<String, String> values = new LinkedHashMap<>();
            for (SpeakerColorSlot slot : SPEAKER_COLOR_SLOTS) {
                String color = colors == null ? "" : colors.get(slot.id);
                values.put(slot.id, normalizeHexColor(color, slot.defaultColor));
            }
            this.colors = Collections.unmodifiableMap(values);
        }

        static SpeakerColorSettings defaults() {
            return new SpeakerColorSettings(Collections.emptyMap());
        }

        String hex(String slotId) {
            SpeakerColorSlot slot = speakerColorSlotById(slotId);
            String color = colors.get(slot.id);
            return normalizeHexColor(color, slot.defaultColor);
        }

        int color(String slotId) {
            SpeakerColorSlot slot = speakerColorSlotById(slotId);
            return colorInt(hex(slot.id), slot.defaultColorInt());
        }

        private static int colorInt(String hex, int fallback) {
            try {
                return Color.parseColor(normalizeHexColor(hex, "#ffffff"));
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }

    static final class Language {
        final String code;
        final String name;
        final String nativeName;
        final String phoneticDescription;

        Language(String code, String name, String nativeName, String phoneticDescription) {
            this.code = code;
            this.name = name;
            this.nativeName = nativeName;
            this.phoneticDescription = phoneticDescription;
        }
    }

    static final class LanguageRule {
        final String sourceLang;
        final boolean translationEnabled;
        final boolean pronunciationEnabled;
        final String targetLang;

        LanguageRule(String sourceLang, boolean translationEnabled, boolean pronunciationEnabled, String targetLang) {
            this.sourceLang = normalizeSourceLanguageKey(sourceLang);
            this.translationEnabled = translationEnabled;
            this.pronunciationEnabled = pronunciationEnabled;
            this.targetLang = normalizeTargetLanguage(targetLang);
        }

        boolean enabled() {
            return translationEnabled || pronunciationEnabled;
        }

        String cacheKey() {
            return sourceLang + ":t=" + translationEnabled + ":p=" + pronunciationEnabled;
        }
    }

    static final class Snapshot {
        final String uiLang;
        final String outputLang;
        final Provider provider;
        final LanguageRule defaultRule;
        final Map<String, LanguageRule> languageRules;
        final String apiKeys;
        final String pollinationsAccessToken;
        final String baseUrl;
        final String model;
        final int maxTokens;
        final float temperature;
        final String previewMode;
        final int previewItems;
        final boolean autoInstrumentalBreakEnabled;
        final boolean interludeLabelsEnabled;
        final boolean syncedLyricsKaraokeAnimationEnabled;
        final boolean karaokeBounceEffectEnabled;
        final BackgroundSettings background;
        final boolean landscapeAutoHideControls;
        final boolean keepScreenOn;
        final boolean metadataTranslationEnabled;
        final boolean japaneseFuriganaEnabled;
        final TypographySettings typography;
        final SpeakerColorSettings speakerColors;
        final String spotifyClientId;
        final String spotifyClientSecret;

        Snapshot(
                String uiLang,
                String outputLang,
                Provider provider,
                LanguageRule defaultRule,
                Map<String, LanguageRule> languageRules,
                String apiKeys,
                String pollinationsAccessToken,
                String baseUrl,
                String model,
                int maxTokens,
                float temperature,
                String previewMode,
                int previewItems,
                boolean autoInstrumentalBreakEnabled,
                boolean interludeLabelsEnabled,
                boolean syncedLyricsKaraokeAnimationEnabled,
                boolean karaokeBounceEffectEnabled,
                BackgroundSettings background,
                boolean landscapeAutoHideControls,
                boolean keepScreenOn,
                boolean metadataTranslationEnabled,
                boolean japaneseFuriganaEnabled,
                TypographySettings typography,
                SpeakerColorSettings speakerColors,
                String spotifyClientId,
                String spotifyClientSecret
        ) {
            this.uiLang = normalizedUiLanguage(uiLang);
            this.outputLang = normalizeOutputLanguage(outputLang);
            this.provider = provider;
            this.defaultRule = defaultRule == null
                    ? new LanguageRule(DEFAULT_SOURCE_LANG, false, false, DEFAULT_TARGET_LANG_RULES)
                    : defaultRule;
            this.languageRules = Collections.unmodifiableMap(new LinkedHashMap<>(languageRules));
            this.apiKeys = apiKeys == null ? "" : apiKeys;
            this.pollinationsAccessToken = pollinationsAccessToken == null ? "" : pollinationsAccessToken.trim();
            this.baseUrl = baseUrl == null ? "" : baseUrl;
            this.model = model == null ? "" : model;
            this.maxTokens = Math.max(256, maxTokens);
            this.temperature = clampFloat(temperature, 0f, 2f);
            this.previewMode = normalizePreviewMode(previewMode);
            this.previewItems = normalizePreviewItems(previewItems);
            this.autoInstrumentalBreakEnabled = autoInstrumentalBreakEnabled;
            this.interludeLabelsEnabled = interludeLabelsEnabled;
            this.syncedLyricsKaraokeAnimationEnabled = syncedLyricsKaraokeAnimationEnabled;
            this.karaokeBounceEffectEnabled = karaokeBounceEffectEnabled;
            this.background = background == null
                    ? new BackgroundSettings(DEFAULT_BACKGROUND_MODE, 30, 20, false, false, DEFAULT_SOLID_BACKGROUND_COLOR, 100)
                    : background;
            this.landscapeAutoHideControls = landscapeAutoHideControls;
            this.keepScreenOn = keepScreenOn;
            this.metadataTranslationEnabled = metadataTranslationEnabled;
            this.japaneseFuriganaEnabled = japaneseFuriganaEnabled;
            this.typography = typography == null ? TypographySettings.defaults() : typography;
            this.speakerColors = speakerColors == null ? SpeakerColorSettings.defaults() : speakerColors;
            this.spotifyClientId = spotifyClientId == null ? "" : spotifyClientId.trim();
            this.spotifyClientSecret = spotifyClientSecret == null ? "" : spotifyClientSecret.trim();
        }

        boolean enabled() {
            if (japaneseFuriganaEnabled) {
                return true;
            }
            if (defaultRule.enabled()) {
                return true;
            }
            for (LanguageRule rule : languageRules.values()) {
                if (rule.enabled()) {
                    return true;
                }
            }
            return false;
        }

        boolean hasApiKey() {
            if ("pollinations".equals(provider.id) && !pollinationsAccessToken.trim().isEmpty()) {
                return true;
            }
            return !apiKeys.trim().isEmpty();
        }

        boolean hasSpotifyApiCredentials() {
            return !spotifyClientId.trim().isEmpty() && !spotifyClientSecret.trim().isEmpty();
        }

        LanguageRule ruleForSource(String sourceLang) {
            String source = normalizeSourceLanguageKey(sourceLang);
            if (languageRules.containsKey(source)) {
                return languageRules.get(source);
            }
            int dash = source.indexOf('-');
            if (dash > 0) {
                String base = source.substring(0, dash);
                if (languageRules.containsKey(base)) {
                    return languageRules.get(base);
                }
            }
            return new LanguageRule(source, defaultRule.translationEnabled, defaultRule.pronunciationEnabled, defaultRule.targetLang);
        }

        String resolveTargetLanguage(String sourceLang) {
            return resolveOutputLanguage(defaultRule.targetLang, uiLang);
        }

        String pronunciationLanguage() {
            return resolveOutputLanguage(outputLang, uiLang);
        }

        boolean shouldSkipTranslation(String sourceLang, String resolvedTargetLang) {
            return ruleForSource(sourceLang).translationEnabled && isSameLanguage(sourceLang, resolvedTargetLang);
        }

        String cacheKey() {
            StringBuilder builder = new StringBuilder();
            builder.append(provider.id)
                    .append("|output=").append(outputLang)
                    .append("|resolvedOutput=").append(resolveOutputLanguage(outputLang, uiLang))
                    .append("|translationTarget=").append(defaultRule.targetLang)
                    .append("|default=").append(defaultRule.cacheKey())
                    .append("|furigana=").append(japaneseFuriganaEnabled)
                    .append("|model=").append(model)
                    .append("|url=").append(baseUrl)
                    .append("|tok=").append(maxTokens)
                    .append("|temp=").append(temperature);
            for (LanguageRule rule : languageRules.values()) {
                builder.append("|rule=").append(rule.cacheKey());
            }
            return builder.toString();
        }
    }
}
