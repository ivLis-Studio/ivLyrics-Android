package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AiLyricsSettings implements SharedPreferences.OnSharedPreferenceChangeListener {
    static final String PREFS_NAME = "ai_lyrics_settings";
    static final String KEY_TRANSLATION_ENABLED = "translation_enabled";
    static final String KEY_PRONUNCIATION_ENABLED = "pronunciation_enabled";
    static final String KEY_BING_TRANSLATE_ENABLED = "bing_translate_enabled";
    static final String KEY_GOOGLE_TRANSLATE_ENABLED = "google_translate_enabled";
    static final String KEY_AI_PROVIDER_ORDER = "ai_provider_order_v1";
    static final String KEY_AI_PROVIDER_ENABLED = "ai_provider_enabled_v1";
    static final String KEY_AI_PROVIDER_PROFILES = "ai_provider_profiles_v1";
    static final String KEY_PROVIDER = "provider";
    static final String KEY_TARGET_LANG = "target_lang";
    static final String KEY_UI_LANG = "ui_lang";
    static final String KEY_OUTPUT_LANG = "output_lang";
    static final String KEY_PRONUNCIATION_LANG = "pronunciation_lang";
    static final String KEY_LANGUAGE_RULES = "language_rules_v2";
    private static final String KEY_FIRST_LANGUAGE_PROMPTED = "first_language_prompted_v1";
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
    static final String KEY_KARAOKE_DATA_AS_LINE_SYNCED = "karaoke_data_as_line_synced";
    static final String KEY_BACKGROUND_MODE = "background_mode";
    static final String KEY_BACKGROUND_BRIGHTNESS = "background_brightness";
    static final String KEY_BACKGROUND_BLUR = "background_blur";
    static final String KEY_BACKGROUND_NOISE = "background_noise";
    static final String KEY_BACKGROUND_REDUCE_MOTION = "background_reduce_motion";
    static final String KEY_BACKGROUND_SOLID_COLOR = "background_solid_color";
    static final String KEY_BACKGROUND_VIDEO_SCALE = "background_video_scale";
    static final String KEY_TRACK_BACKGROUND_SETTINGS = "track_background_settings_v1";
    static final String KEY_LANDSCAPE_AUTO_HIDE_CONTROLS = "landscape_auto_hide_controls";
    static final String KEY_LANDSCAPE_CENTER_NO_LYRICS = "landscape_center_no_lyrics";
    static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    static final String KEY_PIP_SHOW_ARTWORK = "pip_show_artwork";
    static final String KEY_PIP_ORIENTATION = "pip_orientation";
    static final String KEY_PIP_LYRICS_TEXT_ALIGNMENT = "pip_lyrics_text_alignment";
    static final String KEY_PIP_LYRICS_SIZE_PERCENT = "pip_lyrics_size_percent";
    static final String KEY_GLOBAL_SYNC_OFFSET = "global_sync_offset_ms";
    static final String KEY_TRACK_SYNC_OFFSETS = "track_sync_offsets_v1";
    static final String KEY_TRACK_VIDEO_SYNC_OFFSETS = "track_video_sync_offsets_v1";
    static final String KEY_BLUETOOTH_SYNC_OFFSETS = "bluetooth_sync_offsets_v1";
    static final String KEY_SPOTIFY_CLIENT_ID = "spotify_client_id";
    static final String KEY_SPOTIFY_CLIENT_SECRET = "spotify_client_secret";
    static final String KEY_METADATA_TRANSLATION_ENABLED = "metadata_translation_enabled";
    static final String KEY_JAPANESE_FURIGANA_ENABLED = "japanese_furigana_enabled";
    static final String KEY_CULTURAL_ANNOTATIONS_ENABLED = "cultural_annotations_enabled";
    static final String KEY_CULTURAL_ANNOTATIONS_FONT_FAMILY = "cultural_annotations_font_family";
    static final String KEY_CULTURAL_ANNOTATIONS_FONT_SIZE = "cultural_annotations_font_size";
    static final String KEY_CULTURAL_ANNOTATIONS_FONT_WEIGHT = "cultural_annotations_font_weight";
    static final String KEY_CULTURAL_ANNOTATIONS_OPACITY = "cultural_annotations_opacity";
    static final String KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_FAMILY = "cultural_annotations_vinyl_font_family";
    static final String KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_SIZE = "cultural_annotations_vinyl_font_size";
    static final String KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_WEIGHT = "cultural_annotations_vinyl_font_weight";
    static final String KEY_CULTURAL_ANNOTATIONS_VINYL_OPACITY = "cultural_annotations_vinyl_opacity";
    static final String KEY_TYPOGRAPHY_SETTINGS = "typography_settings_v1";
    static final String KEY_VINYL_ALBUM_SIZE_PERCENT = "vinyl_album_size_percent";
    static final String KEY_VINYL_RECORD_SIZE_PERCENT = "vinyl_record_size_percent";
    static final String KEY_VINYL_ANIMATIONS_ENABLED = "vinyl_animations_enabled";
    static final String KEY_VINYL_CENTER_ROTATION_ENABLED = "vinyl_center_rotation_enabled";
    static final String KEY_VINYL_LYRICS_ENABLED = "vinyl_lyrics_enabled";
    static final String KEY_VINYL_TONEARM_STYLE = "vinyl_tonearm_style";
    static final String KEY_VINYL_TONEARM_FINISH = "vinyl_tonearm_finish";
    static final String KEY_VINYL_TONEARM_SIZE_PERCENT = "vinyl_tonearm_size_percent";
    static final String KEY_SPEAKER_COLOR_SETTINGS = "speaker_color_settings_v1";
    static final String KEY_USE_SYNC_CREATOR_SPEAKER_COLORS = "use_sync_creator_speaker_colors";
    static final String KEY_LYRICS_TEXT_ALIGNMENT = "lyrics_text_alignment";

    static final String DEFAULT_SOURCE_LANG = "default";
    static final String PREVIEW_MODE_ORIGINAL = "original";
    static final String PREVIEW_MODE_TRANSLATION = "translation";
    static final String PREVIEW_MODE_PRONUNCIATION = "pronunciation";
    static final String BACKGROUND_MODE_GRADIENT = "gradient-background";
    static final String BACKGROUND_MODE_BLUR_GRADIENT = "blur-gradient-background";
    static final String BACKGROUND_MODE_VIDEO = "video-background";
    static final String BACKGROUND_MODE_SOLID = "solid-background";
    static final String OUTPUT_LANG_SAME_UI = "same_ui";
    static final String VINYL_TONEARM_STYLE_S = "s";
    static final String VINYL_TONEARM_STYLE_STRAIGHT = "straight";
    static final String VINYL_TONEARM_STYLE_J = "j";
    static final String VINYL_TONEARM_STYLE_LINEAR = "linear";
    static final String VINYL_TONEARM_FINISH_WHITE = "white";
    static final String VINYL_TONEARM_FINISH_SILVER = "silver";
    static final String VINYL_TONEARM_FINISH_BLACK = "black";
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
    static final String TYPO_VINYL_ORIGINAL = "vinyl_original";
    static final String TYPO_VINYL_PRONUNCIATION = "vinyl_pronunciation";
    static final String TYPO_VINYL_TRANSLATION = "vinyl_translation";
    static final String TYPO_WEIGHT_REGULAR = "regular";
    static final String TYPO_WEIGHT_SEMIBOLD = "semibold";
    static final String TYPO_WEIGHT_BOLD = "bold";
    static final String SPEAKER_COLOR_NORMAL = "normal";
    static final String LYRICS_ALIGN_LEFT = "left";
    static final String LYRICS_ALIGN_CENTER = "center";
    static final String LYRICS_ALIGN_RIGHT = "right";
    static final String PIP_ORIENTATION_LANDSCAPE = "landscape";
    static final String PIP_ORIENTATION_PORTRAIT = "portrait";
    static final String PIP_ORIENTATION_SQUARE = "square";
    static final String CULTURAL_FONT_PRETENDARD = "pretendard";
    static final String CULTURAL_FONT_SYSTEM = "system";
    static final String CULTURAL_FONT_SERIF = "serif";
    static final String CULTURAL_FONT_MONOSPACE = "monospace";
    private static final String DEFAULT_PROVIDER = "gemini";
    private static final String DEFAULT_TARGET_LANG_RULES = OUTPUT_LANG_SAME_UI;
    private static final String DEFAULT_BACKGROUND_MODE = BACKGROUND_MODE_GRADIENT;
    private static final String DEFAULT_LYRICS_TEXT_ALIGNMENT = LYRICS_ALIGN_LEFT;
    private static final String DEFAULT_PIP_ORIENTATION = PIP_ORIENTATION_SQUARE;
    private static final String DEFAULT_PIP_LYRICS_TEXT_ALIGNMENT = LYRICS_ALIGN_CENTER;
    private static final int DEFAULT_PIP_LYRICS_SIZE_PERCENT = 150;
    private static final String DEFAULT_SOLID_BACKGROUND_COLOR = "#1e3a8a";
    private static final Set<String> CLOUD_SETTING_KEYS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            KEY_TRANSLATION_ENABLED, KEY_PRONUNCIATION_ENABLED, KEY_BING_TRANSLATE_ENABLED,
            KEY_GOOGLE_TRANSLATE_ENABLED, KEY_AI_PROVIDER_ORDER, KEY_AI_PROVIDER_ENABLED,
            KEY_PROVIDER, KEY_TARGET_LANG, KEY_UI_LANG,
            KEY_OUTPUT_LANG, KEY_PRONUNCIATION_LANG, KEY_LANGUAGE_RULES, KEY_MODEL, KEY_MAX_TOKENS,
            KEY_TEMPERATURE, KEY_PREVIEW_MODE, KEY_PREVIEW_ITEMS, KEY_AUTO_INSTRUMENTAL_BREAK,
            KEY_INTERLUDE_LABELS_ENABLED, KEY_SYNCED_LYRICS_KARAOKE_ANIMATION, KEY_KARAOKE_BOUNCE_EFFECT,
            KEY_KARAOKE_DATA_AS_LINE_SYNCED, KEY_BACKGROUND_MODE, KEY_BACKGROUND_BRIGHTNESS,
            KEY_BACKGROUND_BLUR, KEY_BACKGROUND_NOISE, KEY_BACKGROUND_REDUCE_MOTION,
            KEY_BACKGROUND_SOLID_COLOR, KEY_BACKGROUND_VIDEO_SCALE, KEY_LANDSCAPE_AUTO_HIDE_CONTROLS,
            KEY_LANDSCAPE_CENTER_NO_LYRICS, KEY_KEEP_SCREEN_ON, KEY_PIP_SHOW_ARTWORK,
            KEY_PIP_ORIENTATION, KEY_PIP_LYRICS_TEXT_ALIGNMENT, KEY_PIP_LYRICS_SIZE_PERCENT,
            KEY_GLOBAL_SYNC_OFFSET, KEY_METADATA_TRANSLATION_ENABLED, KEY_JAPANESE_FURIGANA_ENABLED,
            KEY_CULTURAL_ANNOTATIONS_ENABLED, KEY_CULTURAL_ANNOTATIONS_FONT_FAMILY,
            KEY_CULTURAL_ANNOTATIONS_FONT_SIZE, KEY_CULTURAL_ANNOTATIONS_FONT_WEIGHT,
            KEY_CULTURAL_ANNOTATIONS_OPACITY, KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_FAMILY,
            KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_SIZE, KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_WEIGHT,
            KEY_CULTURAL_ANNOTATIONS_VINYL_OPACITY, KEY_TYPOGRAPHY_SETTINGS,
            KEY_VINYL_ALBUM_SIZE_PERCENT, KEY_VINYL_RECORD_SIZE_PERCENT, KEY_VINYL_ANIMATIONS_ENABLED,
            KEY_VINYL_CENTER_ROTATION_ENABLED, KEY_VINYL_LYRICS_ENABLED, KEY_VINYL_TONEARM_STYLE,
            KEY_VINYL_TONEARM_FINISH, KEY_VINYL_TONEARM_SIZE_PERCENT, KEY_SPEAKER_COLOR_SETTINGS,
            KEY_USE_SYNC_CREATOR_SPEAKER_COLORS, KEY_LYRICS_TEXT_ALIGNMENT
    )));
    private static final Set<String> CLOUD_FLOAT_SETTING_KEYS = Collections.singleton(KEY_TEMPERATURE);

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
                    "paxsenix",
                    "paxsenix",
                    "Paxsenix OpenAI 호환 API",
                    "https://api.paxsenix.org/v1",
                    "",
                    "https://api.paxsenix.org/dashboard"
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
    static final List<Provider> ALL_AI_PROVIDERS = allAiProviders();
    static final List<String> DEFAULT_AI_PROVIDER_ORDER = defaultAiProviderOrder();
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
    static final List<TypographySlot> VINYL_TYPOGRAPHY_SLOTS = Collections.unmodifiableList(Arrays.asList(
            new TypographySlot(TYPO_VINYL_ORIGINAL, "typography.slot.vinyl_original", "typography.slot.vinyl_original_desc", 70, TYPO_WEIGHT_SEMIBOLD),
            new TypographySlot(TYPO_VINYL_PRONUNCIATION, "typography.slot.vinyl_pronunciation", "typography.slot.vinyl_pronunciation_desc", 70, TYPO_WEIGHT_SEMIBOLD),
            new TypographySlot(TYPO_VINYL_TRANSLATION, "typography.slot.vinyl_translation", "typography.slot.vinyl_translation_desc", 70, TYPO_WEIGHT_SEMIBOLD)
    ));
    private static final List<TypographySlot> ALL_TYPOGRAPHY_SLOTS = allTypographySlots();
    static final List<SpeakerColorSlot> SPEAKER_COLOR_SLOTS = Collections.unmodifiableList(Arrays.asList(
            new SpeakerColorSlot(SPEAKER_COLOR_NORMAL, "speaker_color.normal", "#ffffff"),
            new SpeakerColorSlot("duet1", "speaker_color.duet", "#e4d8ff"),
            new SpeakerColorSlot("duet2", "speaker_color.duet", "#d6e4ff"),
            new SpeakerColorSlot("duet3", "speaker_color.duet", "#ffddf2"),
            new SpeakerColorSlot("duet4", "speaker_color.duet", "#bfaeff"),
            new SpeakerColorSlot("duet5", "speaker_color.duet", "#9d8cf2"),
            new SpeakerColorSlot("male1", "speaker_color.male", "#a8ccff"),
            new SpeakerColorSlot("male2", "speaker_color.male", "#9ae8d4"),
            new SpeakerColorSlot("male3", "speaker_color.male", "#bfe8ff"),
            new SpeakerColorSlot("male4", "speaker_color.male", "#7fb5e6"),
            new SpeakerColorSlot("male5", "speaker_color.male", "#6cb8b8"),
            new SpeakerColorSlot("female1", "speaker_color.female", "#ffb8c7"),
            new SpeakerColorSlot("female2", "speaker_color.female", "#ffd6b3"),
            new SpeakerColorSlot("female3", "speaker_color.female", "#f6c8ff"),
            new SpeakerColorSlot("female4", "speaker_color.female", "#e6b4d4"),
            new SpeakerColorSlot("female5", "speaker_color.female", "#f6e5a5")
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
            new Language("cs", "Czech", "Čeština", "Czech phonetic spelling"),
            new Language("it", "Italian", "Italiano", "Italian phonetic spelling"),
            new Language("th", "Thai", "ภาษาไทย", "Thai script pronunciation"),
            new Language("vi", "Vietnamese", "Tiếng Việt", "Vietnamese phonetic spelling"),
            new Language("id", "Indonesian", "Bahasa Indonesia", "Indonesian phonetic spelling"),
            new Language("ms", "Malay", "Bahasa Melayu", "Malay phonetic spelling"),
            new Language("tr", "Turkish", "Türkçe", "Turkish phonetic spelling")
    ));
    private static final Map<String, Language> LANGUAGE_BY_CODE = buildLanguageMap();

    private final SharedPreferences prefs;
    private volatile Snapshot cachedSnapshot;

    AiLyricsSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    JSONObject exportCloudSettings() throws JSONException {
        JSONObject result = new JSONObject();
        Map<String, ?> stored = prefs.getAll();
        for (String key : CLOUD_SETTING_KEYS) {
            Object value = stored.get(key);
            if (value instanceof Boolean || value instanceof Number || value instanceof String) {
                result.put(key, value);
            }
        }
        return result;
    }

    synchronized void importCloudSettings(JSONObject source) throws JSONException {
        if (source == null) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!CLOUD_SETTING_KEYS.contains(key) || source.isNull(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);
            } else if (value instanceof Number) {
                if (CLOUD_FLOAT_SETTING_KEYS.contains(key)) {
                    editor.putFloat(key, ((Number) value).floatValue());
                } else {
                    editor.putInt(key, ((Number) value).intValue());
                }
            } else if (value instanceof String) {
                editor.putString(key, (String) value);
            }
        }
        editor.apply();
        cachedSnapshot = null;
    }

    synchronized Snapshot snapshot() {
        if (cachedSnapshot != null) {
            return cachedSnapshot;
        }
        String providerId = prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER);
        Provider provider = providerById(providerId);
        Map<String, ProviderProfile> providerProfiles = loadProviderProfiles();
        ProviderProfile providerProfile = providerProfiles.get(provider.id);
        if (providerProfile == null) {
            providerProfile = ProviderProfile.defaults(provider);
        }
        List<String> providerOrder = loadAiProviderOrder();
        Map<String, Boolean> providerEnabled = loadAiProviderEnabled();
        RuleConfig ruleConfig = loadRuleConfig();
        String outputLang = storedOutputLanguage(ruleConfig);
        ruleConfig = ruleConfig.withTarget(outputLang);
        Snapshot snapshot = new Snapshot(
                normalizedUiLanguage(prefs.getString(KEY_UI_LANG, autoTargetLanguage())),
                outputLang,
                provider,
                ruleConfig.defaultRule,
                ruleConfig.languageRules,
                providerProfile.apiKeys,
                prefs.getString(KEY_POLLINATIONS_ACCESS_TOKEN, ""),
                providerProfile.baseUrl.isEmpty() ? provider.defaultBaseUrl : providerProfile.baseUrl,
                providerProfile.model,
                providerProfile.maxTokens,
                providerProfile.temperature,
                normalizePreviewMode(prefs.getString(KEY_PREVIEW_MODE, PREVIEW_MODE_ORIGINAL)),
                normalizePreviewItems(prefs.contains(KEY_PREVIEW_ITEMS)
                        ? prefs.getInt(KEY_PREVIEW_ITEMS, PREVIEW_ITEM_ORIGINAL)
                        : previewItemsForMode(prefs.getString(KEY_PREVIEW_MODE, PREVIEW_MODE_ORIGINAL))),
                prefs.getBoolean(KEY_AUTO_INSTRUMENTAL_BREAK, true),
                prefs.getBoolean(KEY_INTERLUDE_LABELS_ENABLED, true),
                prefs.getBoolean(KEY_SYNCED_LYRICS_KARAOKE_ANIMATION, true),
                prefs.getBoolean(KEY_KARAOKE_BOUNCE_EFFECT, true),
                prefs.getBoolean(KEY_KARAOKE_DATA_AS_LINE_SYNCED, false),
                backgroundSettings(),
                prefs.getBoolean(KEY_LANDSCAPE_AUTO_HIDE_CONTROLS, true),
                prefs.getBoolean(KEY_LANDSCAPE_CENTER_NO_LYRICS, true),
                prefs.getBoolean(KEY_KEEP_SCREEN_ON, false),
                prefs.getBoolean(KEY_PIP_SHOW_ARTWORK, true),
                normalizePipOrientation(prefs.getString(KEY_PIP_ORIENTATION, DEFAULT_PIP_ORIENTATION)),
                normalizeLyricsTextAlignment(prefs.getString(KEY_PIP_LYRICS_TEXT_ALIGNMENT, DEFAULT_PIP_LYRICS_TEXT_ALIGNMENT)),
                normalizePipLyricsSizePercent(prefs.getInt(KEY_PIP_LYRICS_SIZE_PERCENT, DEFAULT_PIP_LYRICS_SIZE_PERCENT)),
                prefs.getBoolean(KEY_METADATA_TRANSLATION_ENABLED, true),
                prefs.getBoolean(KEY_JAPANESE_FURIGANA_ENABLED, false),
                Boolean.TRUE.equals(providerEnabled.get(KeylessTranslationProviders.BING_ID)),
                Boolean.TRUE.equals(providerEnabled.get(KeylessTranslationProviders.GOOGLE_ID)),
                providerOrder,
                providerEnabled,
                providerProfiles,
                prefs.getBoolean(KEY_CULTURAL_ANNOTATIONS_ENABLED, false),
                normalizeCulturalFontFamily(prefs.getString(KEY_CULTURAL_ANNOTATIONS_FONT_FAMILY, CULTURAL_FONT_PRETENDARD)),
                clampInt(prefs.getInt(KEY_CULTURAL_ANNOTATIONS_FONT_SIZE, 14), 10, 28),
                normalizeCulturalFontWeight(prefs.getInt(KEY_CULTURAL_ANNOTATIONS_FONT_WEIGHT, 300)),
                clampInt(prefs.getInt(KEY_CULTURAL_ANNOTATIONS_OPACITY, 60), 20, 100),
                normalizeCulturalFontFamily(prefs.getString(KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_FAMILY, CULTURAL_FONT_PRETENDARD)),
                clampInt(prefs.getInt(KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_SIZE, 12), 10, 28),
                normalizeCulturalFontWeight(prefs.getInt(KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_WEIGHT, 300)),
                clampInt(prefs.getInt(KEY_CULTURAL_ANNOTATIONS_VINYL_OPACITY, 60), 20, 100),
                typographySettings(),
                vinylSettings(),
                speakerColorSettings(),
                prefs.getBoolean(KEY_USE_SYNC_CREATOR_SPEAKER_COLORS, true),
                normalizeLyricsTextAlignment(prefs.getString(KEY_LYRICS_TEXT_ALIGNMENT, DEFAULT_LYRICS_TEXT_ALIGNMENT)),
                prefs.getString(KEY_SPOTIFY_CLIENT_ID, ""),
                prefs.getString(KEY_SPOTIFY_CLIENT_SECRET, "")
        );
        cachedSnapshot = snapshot;
        return snapshot;
    }

    @Override
    public synchronized void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        cachedSnapshot = null;
    }

    void shutdown() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        cachedSnapshot = null;
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

    void setBingTranslateEnabled(boolean enabled) {
        setAiProviderEnabled(KeylessTranslationProviders.BING_ID, enabled);
    }

    void setGoogleTranslateEnabled(boolean enabled) {
        setAiProviderEnabled(KeylessTranslationProviders.GOOGLE_ID, enabled);
    }

    void setCulturalAnnotationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CULTURAL_ANNOTATIONS_ENABLED, enabled).apply();
    }

    void setCulturalAnnotationsFontFamily(String family) {
        prefs.edit().putString(KEY_CULTURAL_ANNOTATIONS_FONT_FAMILY, normalizeCulturalFontFamily(family)).apply();
    }

    void setCulturalAnnotationsFontSize(int sizeSp) {
        prefs.edit().putInt(KEY_CULTURAL_ANNOTATIONS_FONT_SIZE, clampInt(sizeSp, 10, 28)).apply();
    }

    void setCulturalAnnotationsFontWeight(int weight) {
        prefs.edit().putInt(KEY_CULTURAL_ANNOTATIONS_FONT_WEIGHT, normalizeCulturalFontWeight(weight)).apply();
    }

    void setCulturalAnnotationsOpacity(int opacityPercent) {
        prefs.edit().putInt(KEY_CULTURAL_ANNOTATIONS_OPACITY, clampInt(opacityPercent, 20, 100)).apply();
    }

    void setCulturalAnnotationsVinylFontFamily(String family) {
        prefs.edit().putString(KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_FAMILY, normalizeCulturalFontFamily(family)).apply();
    }

    void setCulturalAnnotationsVinylFontSize(int sizeSp) {
        prefs.edit().putInt(KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_SIZE, clampInt(sizeSp, 10, 28)).apply();
    }

    void setCulturalAnnotationsVinylFontWeight(int weight) {
        prefs.edit().putInt(KEY_CULTURAL_ANNOTATIONS_VINYL_FONT_WEIGHT, normalizeCulturalFontWeight(weight)).apply();
    }

    void setCulturalAnnotationsVinylOpacity(int opacityPercent) {
        prefs.edit().putInt(KEY_CULTURAL_ANNOTATIONS_VINYL_OPACITY, clampInt(opacityPercent, 20, 100)).apply();
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

    void setVinylAlbumSizePercent(int sizePercent) {
        prefs.edit().putInt(KEY_VINYL_ALBUM_SIZE_PERCENT, clampInt(sizePercent, 70, 140)).apply();
    }

    void setVinylRecordSizePercent(int sizePercent) {
        prefs.edit().putInt(KEY_VINYL_RECORD_SIZE_PERCENT, clampInt(sizePercent, 70, 140)).apply();
    }

    void setVinylAnimationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VINYL_ANIMATIONS_ENABLED, enabled).apply();
    }

    void setVinylCenterRotationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VINYL_CENTER_ROTATION_ENABLED, enabled).apply();
    }

    void setVinylLyricsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VINYL_LYRICS_ENABLED, enabled).apply();
    }

    void setVinylTonearmStyle(String style) {
        prefs.edit().putString(KEY_VINYL_TONEARM_STYLE, normalizeVinylTonearmStyle(style)).apply();
    }

    void setVinylTonearmFinish(String finish) {
        prefs.edit().putString(KEY_VINYL_TONEARM_FINISH, normalizeVinylTonearmFinish(finish)).apply();
    }

    void setVinylTonearmSizePercent(int sizePercent) {
        prefs.edit().putInt(KEY_VINYL_TONEARM_SIZE_PERCENT, clampInt(sizePercent, 80, 120)).apply();
    }

    void setSpeakerColors(Map<String, String> colors) {
        saveSpeakerColorSettings(new SpeakerColorSettings(colors));
    }

    void resetSpeakerColors() {
        prefs.edit().remove(KEY_SPEAKER_COLOR_SETTINGS).apply();
    }

    void setUseSyncCreatorSpeakerColors(boolean enabled) {
        prefs.edit().putBoolean(KEY_USE_SYNC_CREATOR_SPEAKER_COLORS, enabled).apply();
    }

    void setLyricsTextAlignment(String alignment) {
        prefs.edit().putString(KEY_LYRICS_TEXT_ALIGNMENT, normalizeLyricsTextAlignment(alignment)).apply();
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
        prefs.edit().putString(KEY_PROVIDER, provider.id).apply();
    }

    void setAiProviderEnabled(String providerId, boolean enabled) {
        Provider provider = aiProviderById(providerId);
        if (provider == null) {
            return;
        }
        Map<String, Boolean> values = new LinkedHashMap<>(loadAiProviderEnabled());
        values.put(provider.id, enabled);
        SharedPreferences.Editor editor = prefs.edit().putString(KEY_AI_PROVIDER_ENABLED, providerEnabledJson(values));
        if (KeylessTranslationProviders.BING_ID.equals(provider.id)) {
            editor.putBoolean(KEY_BING_TRANSLATE_ENABLED, enabled);
        } else if (KeylessTranslationProviders.GOOGLE_ID.equals(provider.id)) {
            editor.putBoolean(KEY_GOOGLE_TRANSLATE_ENABLED, enabled);
        }
        editor.apply();
    }

    void setAiProviderOrder(List<String> order) {
        prefs.edit().putString(KEY_AI_PROVIDER_ORDER, providerOrderJson(normalizeAiProviderOrder(order))).apply();
    }

    void moveAiProvider(String sourceId, String targetId, boolean after) {
        List<String> order = new ArrayList<>(loadAiProviderOrder());
        if (sourceId == null || targetId == null || sourceId.equals(targetId) || !order.remove(sourceId)) {
            return;
        }
        int targetIndex = order.indexOf(targetId);
        if (targetIndex < 0) {
            return;
        }
        order.add(Math.min(order.size(), targetIndex + (after ? 1 : 0)), sourceId);
        setAiProviderOrder(order);
    }

    void moveAiProviderByOffset(String providerId, int offset) {
        List<String> order = new ArrayList<>(loadAiProviderOrder());
        int from = order.indexOf(providerId);
        int to = Math.max(0, Math.min(order.size() - 1, from + offset));
        if (from < 0 || from == to) {
            return;
        }
        order.remove(from);
        order.add(to, providerId);
        setAiProviderOrder(order);
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

    boolean shouldPromptForFirstLanguage(String sourceLang) {
        String source = normalizeSourceLanguageKey(sourceLang);
        if (source.isEmpty()
                || DEFAULT_SOURCE_LANG.equals(source)
                || "auto".equalsIgnoreCase(source)
                || "unknown".equalsIgnoreCase(source)
                || "und".equalsIgnoreCase(source)) {
            return false;
        }
        Snapshot current = snapshot();
        if (current.languageRules.containsKey(source)) {
            return false;
        }
        int separator = source.indexOf('-');
        if (separator > 0 && current.languageRules.containsKey(source.substring(0, separator))) {
            return false;
        }
        Set<String> prompted = prefs.getStringSet(KEY_FIRST_LANGUAGE_PROMPTED, Collections.emptySet());
        if (prompted != null && prompted.contains(source.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return !isSameLanguage(source, current.resolveTargetLanguage(source));
    }

    void markFirstLanguagePrompted(String sourceLang) {
        String source = normalizeSourceLanguageKey(sourceLang).toLowerCase(Locale.ROOT);
        if (source.isEmpty() || DEFAULT_SOURCE_LANG.equals(source)) {
            return;
        }
        Set<String> prompted = new LinkedHashSet<>(
                prefs.getStringSet(KEY_FIRST_LANGUAGE_PROMPTED, Collections.emptySet())
        );
        if (prompted.add(source)) {
            prefs.edit().putStringSet(KEY_FIRST_LANGUAGE_PROMPTED, prompted).apply();
        }
    }

    void setApiKeys(String apiKeys) {
        Snapshot current = snapshot();
        setProviderProfile(current.provider.id, apiKeys, current.baseUrl, current.model, current.maxTokens, current.temperature);
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
        Snapshot current = snapshot();
        setProviderProfile(current.provider.id, current.apiKeys, current.baseUrl, model, current.maxTokens, current.temperature);
    }

    void setBaseUrl(String baseUrl) {
        Snapshot current = snapshot();
        setProviderProfile(current.provider.id, current.apiKeys, baseUrl, current.model, current.maxTokens, current.temperature);
    }

    void setMaxTokens(int maxTokens) {
        Snapshot current = snapshot();
        setProviderProfile(current.provider.id, current.apiKeys, current.baseUrl, current.model, maxTokens, current.temperature);
    }

    void setTemperature(float temperature) {
        Snapshot current = snapshot();
        setProviderProfile(current.provider.id, current.apiKeys, current.baseUrl, current.model, current.maxTokens, temperature);
    }

    void setProviderProfile(
            String providerId,
            String apiKeys,
            String baseUrl,
            String model,
            int maxTokens,
            float temperature
    ) {
        Provider provider = providerById(providerId);
        ProviderProfile profile = new ProviderProfile(
                apiKeys,
                baseUrl == null || baseUrl.trim().isEmpty() ? provider.defaultBaseUrl : baseUrl,
                model,
                maxTokens,
                temperature
        );
        Map<String, ProviderProfile> profiles = new LinkedHashMap<>(loadProviderProfiles());
        profiles.put(provider.id, profile);
        SharedPreferences.Editor editor = prefs.edit().putString(KEY_AI_PROVIDER_PROFILES, providerProfilesJson(profiles));
        if (provider.id.equals(providerById(prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER)).id)) {
            editor.putString(KEY_API_KEYS, profile.apiKeys)
                    .putString(KEY_BASE_URL, profile.baseUrl)
                    .putString(KEY_MODEL, profile.model)
                    .putInt(KEY_MAX_TOKENS, profile.maxTokens)
                    .putFloat(KEY_TEMPERATURE, profile.temperature);
        }
        editor.apply();
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

    void setKaraokeDataAsLineSynced(boolean enabled) {
        prefs.edit().putBoolean(KEY_KARAOKE_DATA_AS_LINE_SYNCED, enabled).apply();
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

    BackgroundSettings trackBackgroundSettings(String trackKey) {
        String key = trackKey == null ? "" : trackKey.trim();
        if (key.isEmpty()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(prefs.getString(KEY_TRACK_BACKGROUND_SETTINGS, "{}"));
            JSONObject settingsObject = object.optJSONObject(key);
            return settingsObject == null ? null : backgroundSettingsFromJson(settingsObject, backgroundSettings());
        } catch (JSONException ignored) {
            prefs.edit().remove(KEY_TRACK_BACKGROUND_SETTINGS).apply();
            return null;
        }
    }

    void setTrackBackgroundSettings(String trackKey, BackgroundSettings settings) {
        String key = trackKey == null ? "" : trackKey.trim();
        if (key.isEmpty()) {
            return;
        }
        try {
            JSONObject object = new JSONObject(prefs.getString(KEY_TRACK_BACKGROUND_SETTINGS, "{}"));
            if (settings == null) {
                object.remove(key);
            } else {
                object.put(key, backgroundSettingsToJson(settings));
            }
            prefs.edit().putString(KEY_TRACK_BACKGROUND_SETTINGS, object.toString()).apply();
        } catch (JSONException ignored) {
            JSONObject object = new JSONObject();
            try {
                if (settings != null) {
                    object.put(key, backgroundSettingsToJson(settings));
                }
            } catch (JSONException ignoredAgain) {
            }
            prefs.edit().putString(KEY_TRACK_BACKGROUND_SETTINGS, object.toString()).apply();
        }
    }

    void clearTrackBackgroundSettings(String trackKey) {
        setTrackBackgroundSettings(trackKey, null);
    }

    void setLandscapeAutoHideControls(boolean enabled) {
        prefs.edit().putBoolean(KEY_LANDSCAPE_AUTO_HIDE_CONTROLS, enabled).apply();
    }

    void setLandscapeCenterNoLyrics(boolean enabled) {
        prefs.edit().putBoolean(KEY_LANDSCAPE_CENTER_NO_LYRICS, enabled).apply();
    }

    void setKeepScreenOn(boolean enabled) {
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply();
    }

    void setPipShowArtwork(boolean enabled) {
        prefs.edit().putBoolean(KEY_PIP_SHOW_ARTWORK, enabled).apply();
    }

    void setPipOrientation(String orientation) {
        prefs.edit().putString(KEY_PIP_ORIENTATION, normalizePipOrientation(orientation)).apply();
    }

    void setPipLyricsTextAlignment(String alignment) {
        prefs.edit().putString(KEY_PIP_LYRICS_TEXT_ALIGNMENT, normalizeLyricsTextAlignment(alignment)).apply();
    }

    void setPipLyricsSizePercent(int sizePercent) {
        prefs.edit().putInt(KEY_PIP_LYRICS_SIZE_PERCENT, normalizePipLyricsSizePercent(sizePercent)).apply();
    }

    void setSpotifyApiCredentials(String clientId, String clientSecret) {
        prefs.edit()
                .putString(KEY_SPOTIFY_CLIENT_ID, clientId == null ? "" : clientId.trim())
                .putString(KEY_SPOTIFY_CLIENT_SECRET, clientSecret == null ? "" : clientSecret.trim())
                .apply();
    }

    int globalSyncOffsetMs() {
        return clampInt(prefs.getInt(KEY_GLOBAL_SYNC_OFFSET, 0), -10000, 10000);
    }

    void setGlobalSyncOffsetMs(int offsetMs) {
        int safeOffset = clampInt(offsetMs, -10000, 10000);
        SharedPreferences.Editor editor = prefs.edit();
        if (safeOffset == 0) {
            editor.remove(KEY_GLOBAL_SYNC_OFFSET);
        } else {
            editor.putInt(KEY_GLOBAL_SYNC_OFFSET, safeOffset);
        }
        editor.apply();
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

    int bluetoothSyncOffsetMs(String deviceKey) {
        return trackOffsetMs(KEY_BLUETOOTH_SYNC_OFFSETS, deviceKey);
    }

    void setBluetoothSyncOffsetMs(String deviceKey, int offsetMs) {
        setTrackOffsetMs(KEY_BLUETOOTH_SYNC_OFFSETS, deviceKey, offsetMs);
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

    private static JSONObject backgroundSettingsToJson(BackgroundSettings settings) throws JSONException {
        BackgroundSettings safeSettings = settings == null
                ? new BackgroundSettings(DEFAULT_BACKGROUND_MODE, 30, 20, false, false, DEFAULT_SOLID_BACKGROUND_COLOR, 100)
                : settings;
        JSONObject object = new JSONObject();
        object.put("mode", safeSettings.mode);
        object.put("brightness", safeSettings.brightness);
        object.put("blur", safeSettings.blur);
        object.put("noise", safeSettings.noise);
        object.put("reduceMotion", safeSettings.reduceMotion);
        object.put("solidColor", safeSettings.solidColor);
        object.put("videoScale", safeSettings.videoScale);
        return object;
    }

    private static BackgroundSettings backgroundSettingsFromJson(JSONObject object, BackgroundSettings fallback) {
        BackgroundSettings safeFallback = fallback == null
                ? new BackgroundSettings(DEFAULT_BACKGROUND_MODE, 30, 20, false, false, DEFAULT_SOLID_BACKGROUND_COLOR, 100)
                : fallback;
        if (object == null) {
            return safeFallback;
        }
        return new BackgroundSettings(
                object.optString("mode", safeFallback.mode),
                object.optInt("brightness", safeFallback.brightness),
                object.optInt("blur", safeFallback.blur),
                object.optBoolean("noise", safeFallback.noise),
                object.optBoolean("reduceMotion", safeFallback.reduceMotion),
                object.optString("solidColor", safeFallback.solidColor),
                object.optInt("videoScale", safeFallback.videoScale)
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
        for (TypographySlot slot : ALL_TYPOGRAPHY_SLOTS) {
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

    private VinylSettings vinylSettings() {
        return new VinylSettings(
                prefs.getInt(KEY_VINYL_ALBUM_SIZE_PERCENT, 100),
                prefs.getInt(KEY_VINYL_RECORD_SIZE_PERCENT, 100),
                prefs.getBoolean(KEY_VINYL_ANIMATIONS_ENABLED, true),
                prefs.getBoolean(KEY_VINYL_CENTER_ROTATION_ENABLED, true),
                prefs.getBoolean(KEY_VINYL_LYRICS_ENABLED, true),
                prefs.getString(KEY_VINYL_TONEARM_STYLE, VINYL_TONEARM_STYLE_S),
                prefs.getString(KEY_VINYL_TONEARM_FINISH, VINYL_TONEARM_FINISH_WHITE),
                prefs.getInt(KEY_VINYL_TONEARM_SIZE_PERCENT, 100)
        );
    }

    static String normalizeVinylTonearmStyle(String value) {
        if (VINYL_TONEARM_STYLE_STRAIGHT.equals(value)
                || VINYL_TONEARM_STYLE_J.equals(value)
                || VINYL_TONEARM_STYLE_LINEAR.equals(value)) {
            return value;
        }
        return VINYL_TONEARM_STYLE_S;
    }

    static String normalizeVinylTonearmFinish(String value) {
        if (VINYL_TONEARM_FINISH_SILVER.equals(value)
                || VINYL_TONEARM_FINISH_BLACK.equals(value)) {
            return value;
        }
        return VINYL_TONEARM_FINISH_WHITE;
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
            for (TypographySlot slot : ALL_TYPOGRAPHY_SLOTS) {
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
            cachedSnapshot = null;
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

    private List<String> loadAiProviderOrder() {
        String raw = prefs.getString(KEY_AI_PROVIDER_ORDER, "");
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_AI_PROVIDER_ORDER;
        }
        List<String> values = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                String id = array.optString(index, "").trim();
                if (!id.isEmpty()) {
                    values.add(id);
                }
            }
        } catch (Exception ignored) {
        }
        return normalizeAiProviderOrder(values);
    }

    private Map<String, Boolean> loadAiProviderEnabled() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        String raw = prefs.getString(KEY_AI_PROVIDER_ENABLED, "");
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                JSONObject object = new JSONObject(raw);
                for (Provider provider : ALL_AI_PROVIDERS) {
                    if (object.has(provider.id)) {
                        values.put(provider.id, object.optBoolean(provider.id, provider.defaultEnabled));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        boolean migrated = values.isEmpty();
        for (Provider provider : ALL_AI_PROVIDERS) {
            boolean fallback = provider.defaultEnabled;
            if (KeylessTranslationProviders.BING_ID.equals(provider.id)) {
                fallback = prefs.getBoolean(KEY_BING_TRANSLATE_ENABLED, true);
            } else if (KeylessTranslationProviders.GOOGLE_ID.equals(provider.id)) {
                fallback = prefs.getBoolean(KEY_GOOGLE_TRANSLATE_ENABLED, true);
            }
            values.putIfAbsent(provider.id, fallback);
        }
        if (migrated) {
            Provider legacyProvider = providerById(prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER));
            boolean legacyConfigured = !prefs.getString(KEY_API_KEYS, "").trim().isEmpty()
                    || ("pollinations".equals(legacyProvider.id)
                    && !prefs.getString(KEY_POLLINATIONS_ACCESS_TOKEN, "").trim().isEmpty());
            if (legacyConfigured) {
                values.put(legacyProvider.id, true);
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private Map<String, ProviderProfile> loadProviderProfiles() {
        Map<String, ProviderProfile> profiles = new LinkedHashMap<>();
        String raw = prefs.getString(KEY_AI_PROVIDER_PROFILES, "");
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                JSONObject root = new JSONObject(raw);
                for (Provider provider : PROVIDERS) {
                    JSONObject object = root.optJSONObject(provider.id);
                    if (object == null) {
                        continue;
                    }
                    profiles.put(provider.id, new ProviderProfile(
                            object.optString("apiKeys", ""),
                            object.optString("baseUrl", provider.defaultBaseUrl),
                            object.optString("model", provider.defaultModel),
                            object.optInt("maxTokens", 16000),
                            (float) object.optDouble("temperature", 0.3)
                    ));
                }
            } catch (Exception ignored) {
            }
        }
        Provider legacyProvider = providerById(prefs.getString(KEY_PROVIDER, DEFAULT_PROVIDER));
        for (Provider provider : PROVIDERS) {
            if (profiles.containsKey(provider.id)) {
                continue;
            }
            if (provider.id.equals(legacyProvider.id)) {
                profiles.put(provider.id, new ProviderProfile(
                        prefs.getString(KEY_API_KEYS, ""),
                        prefs.getString(KEY_BASE_URL, provider.defaultBaseUrl),
                        prefs.getString(KEY_MODEL, provider.defaultModel),
                        prefs.getInt(KEY_MAX_TOKENS, 16000),
                        prefs.getFloat(KEY_TEMPERATURE, 0.3f)
                ));
            } else {
                profiles.put(provider.id, ProviderProfile.defaults(provider));
            }
        }
        return Collections.unmodifiableMap(profiles);
    }

    static List<String> normalizeAiProviderOrder(List<String> order) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (order != null) {
            for (String id : order) {
                Provider provider = aiProviderById(id);
                if (provider != null) {
                    normalized.add(provider.id);
                }
            }
        }
        normalized.addAll(DEFAULT_AI_PROVIDER_ORDER);
        return Collections.unmodifiableList(new ArrayList<>(normalized));
    }

    private static String providerOrderJson(List<String> order) {
        return new JSONArray(order == null ? Collections.emptyList() : order).toString();
    }

    private static String providerEnabledJson(Map<String, Boolean> enabled) {
        JSONObject object = new JSONObject();
        for (Provider provider : ALL_AI_PROVIDERS) {
            try {
                object.put(provider.id, enabled != null && Boolean.TRUE.equals(enabled.get(provider.id)));
            } catch (JSONException ignored) {
            }
        }
        return object.toString();
    }

    private static String providerProfilesJson(Map<String, ProviderProfile> profiles) {
        JSONObject root = new JSONObject();
        for (Provider provider : PROVIDERS) {
            ProviderProfile profile = profiles == null ? null : profiles.get(provider.id);
            if (profile == null) {
                profile = ProviderProfile.defaults(provider);
            }
            try {
                JSONObject object = new JSONObject();
                object.put("apiKeys", profile.apiKeys);
                object.put("baseUrl", profile.baseUrl);
                object.put("model", profile.model);
                object.put("maxTokens", profile.maxTokens);
                object.put("temperature", profile.temperature);
                root.put(provider.id, object);
            } catch (JSONException ignored) {
            }
        }
        return root.toString();
    }

    private static List<Provider> allAiProviders() {
        List<Provider> providers = new ArrayList<>();
        providers.add(new Provider(
                KeylessTranslationProviders.BING_ID,
                KeylessTranslationProviders.BING_LABEL,
                "",
                "",
                "",
                "",
                true,
                true
        ));
        providers.add(new Provider(
                KeylessTranslationProviders.GOOGLE_ID,
                KeylessTranslationProviders.GOOGLE_LABEL,
                "",
                "",
                "",
                "",
                true,
                true
        ));
        providers.addAll(PROVIDERS);
        return Collections.unmodifiableList(providers);
    }

    private static List<String> defaultAiProviderOrder() {
        List<String> order = new ArrayList<>();
        for (Provider provider : ALL_AI_PROVIDERS) {
            order.add(provider.id);
        }
        return Collections.unmodifiableList(order);
    }

    static Provider aiProviderById(String providerId) {
        String normalized = providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
        for (Provider provider : ALL_AI_PROVIDERS) {
            if (provider.id.equals(normalized)) {
                return provider;
            }
        }
        return null;
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

    static String normalizeCulturalFontFamily(String family) {
        String normalized = family == null ? "" : family.trim().toLowerCase(Locale.ROOT);
        if (CULTURAL_FONT_SYSTEM.equals(normalized)
                || CULTURAL_FONT_SERIF.equals(normalized)
                || CULTURAL_FONT_MONOSPACE.equals(normalized)) {
            return normalized;
        }
        return CULTURAL_FONT_PRETENDARD;
    }

    static int normalizeCulturalFontWeight(int weight) {
        int clamped = clampInt(weight, 100, 900);
        return Math.round(clamped / 100f) * 100;
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
        for (TypographySlot slot : ALL_TYPOGRAPHY_SLOTS) {
            if (slot.id.equals(normalized)) {
                return slot;
            }
        }
        return TYPOGRAPHY_SLOTS.get(0);
    }

    private static List<TypographySlot> allTypographySlots() {
        List<TypographySlot> slots = new ArrayList<>(TYPOGRAPHY_SLOTS);
        slots.addAll(VINYL_TYPOGRAPHY_SLOTS);
        return Collections.unmodifiableList(slots);
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

    static String normalizeLyricsTextAlignment(String alignment) {
        String value = alignment == null ? "" : alignment.trim().toLowerCase(Locale.ROOT);
        if (LYRICS_ALIGN_CENTER.equals(value)) {
            return LYRICS_ALIGN_CENTER;
        }
        if (LYRICS_ALIGN_RIGHT.equals(value)) {
            return LYRICS_ALIGN_RIGHT;
        }
        return DEFAULT_LYRICS_TEXT_ALIGNMENT;
    }

    static String normalizePipOrientation(String orientation) {
        String value = orientation == null ? "" : orientation.trim().toLowerCase(Locale.ROOT);
        if (PIP_ORIENTATION_PORTRAIT.equals(value)) {
            return PIP_ORIENTATION_PORTRAIT;
        }
        if (PIP_ORIENTATION_SQUARE.equals(value)) {
            return PIP_ORIENTATION_SQUARE;
        }
        return PIP_ORIENTATION_LANDSCAPE;
    }

    static int normalizePipLyricsSizePercent(int sizePercent) {
        return clampInt(sizePercent, 50, 180);
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
        final boolean keyless;
        final boolean defaultEnabled;

        Provider(String id, String label, String description, String defaultBaseUrl, String defaultModel, String apiKeyUrl) {
            this(id, label, description, defaultBaseUrl, defaultModel, apiKeyUrl, false, false);
        }

        Provider(
                String id,
                String label,
                String description,
                String defaultBaseUrl,
                String defaultModel,
                String apiKeyUrl,
                boolean keyless,
                boolean defaultEnabled
        ) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.defaultBaseUrl = defaultBaseUrl;
            this.defaultModel = defaultModel;
            this.apiKeyUrl = apiKeyUrl;
            this.keyless = keyless;
            this.defaultEnabled = defaultEnabled;
        }
    }

    static final class ProviderProfile {
        final String apiKeys;
        final String baseUrl;
        final String model;
        final int maxTokens;
        final float temperature;

        ProviderProfile(String apiKeys, String baseUrl, String model, int maxTokens, float temperature) {
            this.apiKeys = apiKeys == null ? "" : apiKeys.trim();
            this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
            this.model = model == null ? "" : model.trim();
            this.maxTokens = Math.max(256, maxTokens);
            this.temperature = clampFloat(temperature, 0f, 2f);
        }

        static ProviderProfile defaults(Provider provider) {
            Provider safe = provider == null ? PROVIDERS.get(0) : provider;
            return new ProviderProfile("", safe.defaultBaseUrl, safe.defaultModel, 16000, 0.3f);
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
            for (TypographySlot slot : ALL_TYPOGRAPHY_SLOTS) {
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

        TypographySettings forVinylPreview() {
            Map<String, TypographyStyle> values = new LinkedHashMap<>(styles);
            values.put(TYPO_MAIN_PREVIEW_ORIGINAL, style(TYPO_VINYL_ORIGINAL));
            values.put(TYPO_MAIN_PREVIEW_PRONUNCIATION, style(TYPO_VINYL_PRONUNCIATION));
            values.put(TYPO_MAIN_PREVIEW_TRANSLATION, style(TYPO_VINYL_TRANSLATION));
            return new TypographySettings(values);
        }
    }

    static final class VinylSettings {
        final int albumSizePercent;
        final int recordSizePercent;
        final boolean animationsEnabled;
        final boolean centerRotationEnabled;
        final boolean lyricsEnabled;
        final String tonearmStyle;
        final String tonearmFinish;
        final int tonearmSizePercent;

        VinylSettings(
                int albumSizePercent,
                int recordSizePercent,
                boolean animationsEnabled,
                boolean centerRotationEnabled,
                boolean lyricsEnabled,
                String tonearmStyle,
                String tonearmFinish,
                int tonearmSizePercent
        ) {
            this.albumSizePercent = clampInt(albumSizePercent, 70, 140);
            this.recordSizePercent = clampInt(recordSizePercent, 70, 140);
            this.animationsEnabled = animationsEnabled;
            this.centerRotationEnabled = centerRotationEnabled;
            this.lyricsEnabled = lyricsEnabled;
            this.tonearmStyle = normalizeVinylTonearmStyle(tonearmStyle);
            this.tonearmFinish = normalizeVinylTonearmFinish(tonearmFinish);
            this.tonearmSizePercent = clampInt(tonearmSizePercent, 80, 120);
        }

        static VinylSettings defaults() {
            return new VinylSettings(
                    100, 100, true, true, true,
                    VINYL_TONEARM_STYLE_S, VINYL_TONEARM_FINISH_WHITE, 100
            );
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
        final boolean karaokeDataAsLineSynced;
        final BackgroundSettings background;
        final boolean landscapeAutoHideControls;
        final boolean landscapeCenterNoLyrics;
        final boolean keepScreenOn;
        final boolean pipShowArtwork;
        final String pipOrientation;
        final String pipLyricsTextAlignment;
        final int pipLyricsSizePercent;
        final boolean metadataTranslationEnabled;
        final boolean japaneseFuriganaEnabled;
        final boolean bingTranslateEnabled;
        final boolean googleTranslateEnabled;
        final List<String> aiProviderOrder;
        final Map<String, Boolean> aiProviderEnabled;
        final Map<String, ProviderProfile> providerProfiles;
        final boolean culturalAnnotationsEnabled;
        final String culturalAnnotationsFontFamily;
        final int culturalAnnotationsFontSize;
        final int culturalAnnotationsFontWeight;
        final int culturalAnnotationsOpacity;
        final String culturalAnnotationsVinylFontFamily;
        final int culturalAnnotationsVinylFontSize;
        final int culturalAnnotationsVinylFontWeight;
        final int culturalAnnotationsVinylOpacity;
        final TypographySettings typography;
        final VinylSettings vinyl;
        final SpeakerColorSettings speakerColors;
        final boolean useSyncCreatorSpeakerColors;
        final String lyricsTextAlignment;
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
                boolean karaokeDataAsLineSynced,
                BackgroundSettings background,
                boolean landscapeAutoHideControls,
                boolean landscapeCenterNoLyrics,
                boolean keepScreenOn,
                boolean pipShowArtwork,
                String pipOrientation,
                String pipLyricsTextAlignment,
                int pipLyricsSizePercent,
                boolean metadataTranslationEnabled,
                boolean japaneseFuriganaEnabled,
                boolean bingTranslateEnabled,
                boolean googleTranslateEnabled,
                List<String> aiProviderOrder,
                Map<String, Boolean> aiProviderEnabled,
                Map<String, ProviderProfile> providerProfiles,
                boolean culturalAnnotationsEnabled,
                String culturalAnnotationsFontFamily,
                int culturalAnnotationsFontSize,
                int culturalAnnotationsFontWeight,
                int culturalAnnotationsOpacity,
                String culturalAnnotationsVinylFontFamily,
                int culturalAnnotationsVinylFontSize,
                int culturalAnnotationsVinylFontWeight,
                int culturalAnnotationsVinylOpacity,
                TypographySettings typography,
                VinylSettings vinyl,
                SpeakerColorSettings speakerColors,
                boolean useSyncCreatorSpeakerColors,
                String lyricsTextAlignment,
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
            this.karaokeDataAsLineSynced = karaokeDataAsLineSynced;
            this.background = background == null
                    ? new BackgroundSettings(DEFAULT_BACKGROUND_MODE, 30, 20, false, false, DEFAULT_SOLID_BACKGROUND_COLOR, 100)
                    : background;
            this.landscapeAutoHideControls = landscapeAutoHideControls;
            this.landscapeCenterNoLyrics = landscapeCenterNoLyrics;
            this.keepScreenOn = keepScreenOn;
            this.pipShowArtwork = pipShowArtwork;
            this.pipOrientation = normalizePipOrientation(pipOrientation);
            this.pipLyricsTextAlignment = normalizeLyricsTextAlignment(pipLyricsTextAlignment);
            this.pipLyricsSizePercent = normalizePipLyricsSizePercent(pipLyricsSizePercent);
            this.metadataTranslationEnabled = metadataTranslationEnabled;
            this.japaneseFuriganaEnabled = japaneseFuriganaEnabled;
            this.bingTranslateEnabled = bingTranslateEnabled;
            this.googleTranslateEnabled = googleTranslateEnabled;
            this.aiProviderOrder = normalizeAiProviderOrder(aiProviderOrder);
            this.aiProviderEnabled = Collections.unmodifiableMap(new LinkedHashMap<>(aiProviderEnabled));
            this.providerProfiles = Collections.unmodifiableMap(new LinkedHashMap<>(providerProfiles));
            this.culturalAnnotationsEnabled = culturalAnnotationsEnabled;
            this.culturalAnnotationsFontFamily = normalizeCulturalFontFamily(culturalAnnotationsFontFamily);
            this.culturalAnnotationsFontSize = clampInt(culturalAnnotationsFontSize, 10, 28);
            this.culturalAnnotationsFontWeight = normalizeCulturalFontWeight(culturalAnnotationsFontWeight);
            this.culturalAnnotationsOpacity = clampInt(culturalAnnotationsOpacity, 20, 100);
            this.culturalAnnotationsVinylFontFamily = normalizeCulturalFontFamily(culturalAnnotationsVinylFontFamily);
            this.culturalAnnotationsVinylFontSize = clampInt(culturalAnnotationsVinylFontSize, 10, 28);
            this.culturalAnnotationsVinylFontWeight = normalizeCulturalFontWeight(culturalAnnotationsVinylFontWeight);
            this.culturalAnnotationsVinylOpacity = clampInt(culturalAnnotationsVinylOpacity, 20, 100);
            this.typography = typography == null ? TypographySettings.defaults() : typography;
            this.vinyl = vinyl == null ? VinylSettings.defaults() : vinyl;
            this.speakerColors = speakerColors == null ? SpeakerColorSettings.defaults() : speakerColors;
            this.useSyncCreatorSpeakerColors = useSyncCreatorSpeakerColors;
            this.lyricsTextAlignment = normalizeLyricsTextAlignment(lyricsTextAlignment);
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

        boolean hasModel() {
            return model != null && !model.trim().isEmpty();
        }

        boolean hasKeylessTranslationProvider() {
            return bingTranslateEnabled || googleTranslateEnabled;
        }

        boolean isAiProviderEnabled(String providerId) {
            return Boolean.TRUE.equals(aiProviderEnabled.get(providerId));
        }

        List<String> enabledAiProviderOrder() {
            List<String> enabled = new ArrayList<>();
            for (String providerId : aiProviderOrder) {
                if (isAiProviderEnabled(providerId)) {
                    enabled.add(providerId);
                }
            }
            return Collections.unmodifiableList(enabled);
        }

        List<Snapshot> readyAiProviderSnapshots() {
            List<Snapshot> snapshots = new ArrayList<>();
            for (String providerId : enabledAiProviderOrder()) {
                Provider candidate = aiProviderById(providerId);
                if (candidate == null || candidate.keyless) {
                    continue;
                }
                Snapshot candidateSnapshot = forProvider(providerId);
                if (candidateSnapshot != null && candidateSnapshot.hasApiKey() && candidateSnapshot.hasModel()) {
                    snapshots.add(candidateSnapshot);
                }
            }
            return Collections.unmodifiableList(snapshots);
        }

        boolean hasReadyAiProvider() {
            return !readyAiProviderSnapshots().isEmpty();
        }

        boolean hasAnyTranslationProvider() {
            if (hasKeylessTranslationProvider()) {
                return true;
            }
            return hasReadyAiProvider();
        }

        Snapshot forProvider(String providerId) {
            Provider candidate = aiProviderById(providerId);
            if (candidate == null || candidate.keyless) {
                return null;
            }
            ProviderProfile profile = providerProfiles.get(candidate.id);
            if (profile == null) {
                profile = ProviderProfile.defaults(candidate);
            }
            return new Snapshot(
                    uiLang,
                    outputLang,
                    candidate,
                    defaultRule,
                    languageRules,
                    profile.apiKeys,
                    pollinationsAccessToken,
                    profile.baseUrl,
                    profile.model,
                    profile.maxTokens,
                    profile.temperature,
                    previewMode,
                    previewItems,
                    autoInstrumentalBreakEnabled,
                    interludeLabelsEnabled,
                    syncedLyricsKaraokeAnimationEnabled,
                    karaokeBounceEffectEnabled,
                    karaokeDataAsLineSynced,
                    background,
                    landscapeAutoHideControls,
                    landscapeCenterNoLyrics,
                    keepScreenOn,
                    pipShowArtwork,
                    pipOrientation,
                    pipLyricsTextAlignment,
                    pipLyricsSizePercent,
                    metadataTranslationEnabled,
                    japaneseFuriganaEnabled,
                    bingTranslateEnabled,
                    googleTranslateEnabled,
                    aiProviderOrder,
                    aiProviderEnabled,
                    providerProfiles,
                    culturalAnnotationsEnabled,
                    culturalAnnotationsFontFamily,
                    culturalAnnotationsFontSize,
                    culturalAnnotationsFontWeight,
                    culturalAnnotationsOpacity,
                    culturalAnnotationsVinylFontFamily,
                    culturalAnnotationsVinylFontSize,
                    culturalAnnotationsVinylFontWeight,
                    culturalAnnotationsVinylOpacity,
                    typography,
                    vinyl,
                    speakerColors,
                    useSyncCreatorSpeakerColors,
                    lyricsTextAlignment,
                    spotifyClientId,
                    spotifyClientSecret
            );
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
                    .append("|bingTranslate=").append(bingTranslateEnabled)
                    .append("|googleTranslate=").append(googleTranslateEnabled)
                    .append("|default=").append(defaultRule.cacheKey())
                    .append("|furigana=").append(japaneseFuriganaEnabled)
                    .append("|model=").append(model)
                    .append("|url=").append(baseUrl)
                    .append("|tok=").append(maxTokens)
                    .append("|temp=").append(temperature);
            for (String providerId : aiProviderOrder) {
                builder.append("|provider=").append(providerId)
                        .append(":enabled=").append(isAiProviderEnabled(providerId));
                ProviderProfile profile = providerProfiles.get(providerId);
                if (profile != null) {
                    builder.append(":model=").append(profile.model)
                            .append(":url=").append(profile.baseUrl)
                            .append(":tok=").append(profile.maxTokens)
                            .append(":temp=").append(profile.temperature)
                            .append(":key=").append(profile.apiKeys.hashCode());
                }
            }
            for (LanguageRule rule : languageRules.values()) {
                builder.append("|rule=").append(rule.cacheKey());
            }
            return builder.toString();
        }
    }
}
