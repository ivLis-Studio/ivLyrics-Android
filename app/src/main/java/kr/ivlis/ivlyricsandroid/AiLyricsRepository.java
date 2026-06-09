package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AiLyricsRepository {
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 70_000;
    private static final String SUPPLEMENT_PROMPT_VERSION = "v3-id-aligned-furigana";
    private static final Pattern TAGGED_OUTPUT_PATTERN = Pattern.compile(
            "^\\s*(?:[-*]\\s*)?(?:\\[?L(\\d{1,4})\\]?|(?:row|line)\\s*(\\d{1,4})|#?(\\d{1,4}))\\s*(?:\\t|[:：|\\-]|\\.\\s+|\\s+)\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RUBY_TAG_PATTERN = Pattern.compile(
            "<ruby>([^<>]+)<rt>([^<>]*)</rt></ruby>",
            Pattern.CASE_INSENSITIVE
    );

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, LyricsResult> cache = new HashMap<>();
    private final Map<String, MetadataTranslation> metadataCache = new HashMap<>();
    private final LyricsDiskCache diskCache;
    private final SharedPreferences metadataPrefs;

    AiLyricsRepository() {
        diskCache = null;
        metadataPrefs = null;
    }

    AiLyricsRepository(Context context) {
        diskCache = context == null
                ? null
                : new LyricsDiskCache(context.getApplicationContext(), "ai_lyrics", 500);
        metadataPrefs = context == null
                ? null
                : context.getApplicationContext().getSharedPreferences("ai_metadata_translations", Context.MODE_PRIVATE);
    }

    interface Callback {
        void onAiLyricsLoaded(String trackKey, LyricsResult result);

        void onAiLyricsError(String trackKey, String message);

        void onAiLyricsLog(String trackKey, String message);

        void onAiMetadataTranslationLoaded(String trackKey, MetadataTranslation translation);

        void onAiMetadataTranslationError(String trackKey, String message);
    }

    static final class MetadataTranslation {
        final String title;
        final String artist;
        final String sourceLang;
        final String targetLang;

        MetadataTranslation(String title, String artist, String sourceLang, String targetLang) {
            this.title = title == null ? "" : title.trim();
            this.artist = artist == null ? "" : artist.trim();
            this.sourceLang = AiLyricsSettings.normalizeLanguageCode(sourceLang);
            this.targetLang = AiLyricsSettings.normalizeLanguageCode(targetLang);
        }
    }

    private interface LogSink {
        void write(String message);
    }

    private static final class SupplementRequest {
        final int lineIndex;
        final int partIndex;
        final String text;

        SupplementRequest(int lineIndex, int partIndex, String text) {
            this.lineIndex = lineIndex;
            this.partIndex = partIndex;
            this.text = text == null ? "" : text;
        }
    }

    private static final class SupplementResult {
        final SupplementRequest request;
        final String pronunciation;
        final String translation;
        final String furigana;

        SupplementResult(SupplementRequest request, String pronunciation, String translation) {
            this(request, pronunciation, translation, "");
        }

        SupplementResult(SupplementRequest request, String pronunciation, String translation, String furigana) {
            this.request = request;
            this.pronunciation = pronunciation == null ? "" : pronunciation;
            this.translation = translation == null ? "" : translation;
            this.furigana = furigana == null ? "" : furigana;
        }
    }

    private static final class TaggedOutputLine {
        final int index;
        final String value;

        TaggedOutputLine(int index, String value) {
            this.index = index;
            this.value = value == null ? "" : value;
        }
    }

    void loadSupplements(
            TrackSnapshot track,
            LyricsResult baseResult,
            AiLyricsSettings.Snapshot settings,
            Callback callback
    ) {
        loadSupplements(track, baseResult, settings, "", callback);
    }

    void loadSupplements(
            TrackSnapshot track,
            LyricsResult baseResult,
            AiLyricsSettings.Snapshot settings,
            String sourceLangOverride,
            Callback callback
    ) {
        loadSupplements(track, baseResult, settings, sourceLangOverride, false, callback);
    }

    void loadSupplements(
            TrackSnapshot track,
            LyricsResult baseResult,
            AiLyricsSettings.Snapshot settings,
            String sourceLangOverride,
            boolean bypassCache,
            Callback callback
    ) {
        if (track == null || !track.hasUsableMetadata() || baseResult == null || baseResult.lines.isEmpty()) {
            return;
        }
        if (settings == null || !settings.enabled()) {
            return;
        }

        String trackKey = track.stableKey();
        String textPayload = buildPayloadText(baseResult.lines);
        String detectedSourceLang = detectLanguage(textPayload);
        String normalizedOverride = AiLyricsSettings.normalizeLanguageCode(sourceLangOverride);
        String sourceLang = normalizedOverride.isEmpty() || "auto".equalsIgnoreCase(normalizedOverride)
                ? detectedSourceLang
                : normalizedOverride;
        AiLyricsSettings.LanguageRule rule = settings.ruleForSource(sourceLang);
        String targetLang = settings.resolveTargetLanguage(sourceLang);
        String pronunciationLang = settings.pronunciationLanguage();
        boolean furiganaEnabled = shouldGenerateFurigana(settings, sourceLang, textPayload);
        if (!rule.enabled() && !furiganaEnabled) {
            emitLog(trackKey, callback, "ai lyrics skipped for source=" + sourceLang
                    + ": translation=false / pronunciation=false / furigana=false");
            callback.onAiLyricsLoaded(trackKey, baseResult);
            return;
        }
        String cacheKey = trackKey
                + "|source=" + sourceLang
                + "|detected=" + detectedSourceLang
                + "|prompt=" + SUPPLEMENT_PROMPT_VERSION
                + "|" + settings.cacheKey()
                + "|text=" + sha256(textPayload);
        if (!bypassCache) {
            LyricsResult cached = cache.get(cacheKey);
            if (cached != null) {
                cached = withBaseContributors(cached, baseResult);
                cache.put(cacheKey, cached);
                emitLog(trackKey, callback, "ai lyrics cache hit: " + settings.provider.label);
                callback.onAiLyricsLoaded(trackKey, cached);
                return;
            }
            LyricsResult diskCached = diskCache == null ? null : diskCache.get(cacheKey);
            if (diskCached != null) {
                diskCached = withBaseContributors(diskCached, baseResult);
                cache.put(cacheKey, diskCached);
                emitLog(trackKey, callback, "ai lyrics disk cache hit: " + settings.provider.label);
                callback.onAiLyricsLoaded(trackKey, diskCached);
                return;
            }
        }

        if (!settings.hasApiKey()) {
            emitLog(trackKey, callback, "ai lyrics skipped: API key missing for " + settings.provider.label);
            callback.onAiLyricsError(trackKey, "AI 제공자 API 키가 설정되지 않았습니다");
            return;
        }

        emitLog(trackKey, callback, "ai lyrics: provider=" + settings.provider.label
                + " / model=" + settings.model
                + " / source=" + sourceLang
                + (sourceLang.equalsIgnoreCase(detectedSourceLang) ? "" : " / detected=" + detectedSourceLang)
                + " / pronunciation=" + pronunciationLang
                + " / target=" + targetLang
                + " / translation=" + rule.translationEnabled
                + " / pronunciation=" + rule.pronunciationEnabled
                + " / furigana=" + furiganaEnabled);

        executor.execute(() -> {
            LogSink log = message -> emitLog(trackKey, callback, message);
            try {
                LyricsResult result = loadSupplementsBlocking(baseResult, settings, rule, textPayload, sourceLang, targetLang, pronunciationLang, furiganaEnabled, log);
                cache.put(cacheKey, result);
                if (diskCache != null) {
                    diskCache.put(cacheKey, result);
                }
                mainHandler.post(() -> callback.onAiLyricsLoaded(trackKey, result));
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                log.write("ai lyrics error: " + message);
                mainHandler.post(() -> callback.onAiLyricsError(trackKey, message));
            }
        });
    }

    private LyricsResult withBaseContributors(LyricsResult result, LyricsResult baseResult) {
        if (result == null || baseResult == null) {
            return result;
        }
        List<LyricsResult.SyncContributor> baseContributors = baseResult.contributors == null
                ? Collections.emptyList()
                : baseResult.contributors;
        List<LyricsResult.SyncContributor> resultContributors = result.contributors == null
                ? Collections.emptyList()
                : result.contributors;
        if (resultContributors.equals(baseContributors)) {
            return result;
        }
        return new LyricsResult(
                result.lines,
                result.providerLabel,
                result.detail,
                result.karaoke,
                result.isrc,
                result.spotifyTrackId,
                baseContributors
        );
    }

    void loadMetadataTranslation(
            TrackSnapshot track,
            AiLyricsSettings.Snapshot settings,
            String sourceLangOverride,
            boolean bypassCache,
            Callback callback
    ) {
        if (track == null || !track.hasUsableMetadata() || settings == null || callback == null) {
            return;
        }
        String title = track.title == null ? "" : track.title.trim();
        String artist = track.artist == null ? "" : track.artist.trim();
        if (title.isEmpty() && artist.isEmpty()) {
            return;
        }

        String trackKey = track.stableKey();
        String detectedSourceLang = detectLanguage(title + "\n" + artist);
        String normalizedOverride = AiLyricsSettings.normalizeLanguageCode(sourceLangOverride);
        String sourceLang = normalizedOverride.isEmpty() || "auto".equalsIgnoreCase(normalizedOverride)
                ? detectedSourceLang
                : normalizedOverride;
        String targetLang = settings.resolveTargetLanguage(sourceLang);
        if (!settings.metadataTranslationEnabled || AiLyricsSettings.isSameLanguage(sourceLang, targetLang)) {
            return;
        }
        if (!settings.hasApiKey()) {
            emitLog(trackKey, callback, "ai metadata skipped: API key missing for " + settings.provider.label);
            callback.onAiMetadataTranslationError(trackKey, "AI 제공자 API 키가 설정되지 않았습니다");
            return;
        }

        String cacheKey = "metadata|"
                + trackKey
                + "|source=" + sourceLang
                + "|target=" + targetLang
                + "|provider=" + settings.provider.id
                + "|model=" + settings.model
                + "|url=" + settings.baseUrl
                + "|temp=" + settings.temperature
                + "|text=" + sha256(title + "\n" + artist);
        if (!bypassCache) {
            MetadataTranslation cached = metadataCache.get(cacheKey);
            if (cached != null) {
                emitLog(trackKey, callback, "ai metadata cache hit: " + settings.provider.label);
                callback.onAiMetadataTranslationLoaded(trackKey, cached);
                return;
            }
            MetadataTranslation persisted = metadataTranslationFromPrefs(cacheKey);
            if (persisted != null) {
                metadataCache.put(cacheKey, persisted);
                emitLog(trackKey, callback, "ai metadata disk cache hit: " + settings.provider.label);
                callback.onAiMetadataTranslationLoaded(trackKey, persisted);
                return;
            }
        }

        emitLog(trackKey, callback, "ai metadata: provider=" + settings.provider.label
                + " / source=" + sourceLang
                + (sourceLang.equalsIgnoreCase(detectedSourceLang) ? "" : " / detected=" + detectedSourceLang)
                + " / target=" + targetLang);

        executor.execute(() -> {
            LogSink log = message -> emitLog(trackKey, callback, message);
            try {
                String raw = callProviderRaw(buildMetadataTranslationPrompt(title, artist, targetLang), settings);
                MetadataTranslation translation = parseMetadataTranslation(raw, title, artist, sourceLang, targetLang);
                metadataCache.put(cacheKey, translation);
                putMetadataTranslationToPrefs(cacheKey, translation);
                log.write("ai metadata response: title="
                        + (!translation.title.isEmpty())
                        + " / artist="
                        + (!translation.artist.isEmpty()));
                mainHandler.post(() -> callback.onAiMetadataTranslationLoaded(trackKey, translation));
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                log.write("ai metadata error: " + message);
                mainHandler.post(() -> callback.onAiMetadataTranslationError(trackKey, message));
            }
        });
    }

    void clearCache() {
        cache.clear();
        metadataCache.clear();
        if (diskCache != null) {
            diskCache.clear();
        }
        if (metadataPrefs != null) {
            metadataPrefs.edit().clear().apply();
        }
    }

    void clearTrackCache(String trackKey) {
        String key = trackKey == null ? "" : trackKey.trim();
        if (key.isEmpty()) {
            return;
        }
        removeCacheEntriesByPrefix(cache, key + "|");
        removeCacheEntriesByPrefix(metadataCache, "metadata|" + key + "|");
        if (diskCache != null) {
            diskCache.removeByKeyPrefix(key + "|");
        }
        clearMetadataPrefsForTrack(key);
    }

    void clearMemoryCache() {
        cache.clear();
        metadataCache.clear();
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private LyricsResult loadSupplementsBlocking(
            LyricsResult baseResult,
            AiLyricsSettings.Snapshot settings,
            AiLyricsSettings.LanguageRule rule,
            String textPayload,
            String sourceLang,
            String targetLang,
            String pronunciationLang,
            boolean furiganaEnabled,
            LogSink log
    ) throws Exception {
        List<SupplementRequest> requests = buildSupplementRequests(baseResult.lines);
        int expectedLineCount = requests.size();
        List<String> pronunciation = Collections.emptyList();
        List<String> translation = Collections.emptyList();
        List<String> furigana = Collections.emptyList();

        if (rule.pronunciationEnabled) {
            log.write("ai pronunciation request: lines=" + expectedLineCount + " / pronunciation=" + pronunciationLang);
            String raw = callProviderRaw(buildPhoneticPrompt(requests, pronunciationLang), settings);
            pronunciation = parseTaggedTextLines(raw, requests, "pronunciation", log);
            log.write("ai pronunciation response: lines=" + pronunciation.size());
        }

        if (furiganaEnabled) {
            log.write("ai furigana request: lines=" + expectedLineCount);
            String raw = callProviderRaw(buildFuriganaPrompt(requests), settings);
            furigana = parseFuriganaTextLines(raw, requests, log);
            log.write("ai furigana response: lines=" + furigana.size());
        }

        boolean translationSkipped = settings.shouldSkipTranslation(sourceLang, targetLang);
        if (translationSkipped) {
            log.write("ai translation skipped: source language matches target (" + sourceLang + " -> " + targetLang + ")");
        } else if (rule.translationEnabled) {
            log.write("ai translation request: lines=" + expectedLineCount);
            String raw = callProviderRaw(buildTranslationPrompt(requests, targetLang), settings);
            translation = parseTaggedTextLines(raw, requests, "translation", log);
            log.write("ai translation response: lines=" + translation.size());
        }

        Map<Integer, List<SupplementResult>> resultsByLine = new HashMap<>();
        for (int index = 0; index < requests.size(); index++) {
            SupplementRequest request = requests.get(index);
            List<SupplementResult> lineResults = resultsByLine.get(request.lineIndex);
            if (lineResults == null) {
                lineResults = new ArrayList<>();
                resultsByLine.put(request.lineIndex, lineResults);
            }
            lineResults.add(new SupplementResult(
                    request,
                    valueAt(pronunciation, index),
                    valueAt(translation, index),
                    valueAt(furigana, index)
            ));
        }

        List<LyricsLine> merged = new ArrayList<>();
        for (int index = 0; index < baseResult.lines.size(); index++) {
            LyricsLine line = baseResult.lines.get(index);
            merged.add(mergeSupplementLine(line, resultsByLine.get(index)));
        }

        String detail = baseResult.detail;
        String taskLabel = translationSkipped
                ? (rule.pronunciationEnabled
                ? (furiganaEnabled ? "translation skipped, pronunciation/furigana" : "translation skipped, pronunciation")
                : (furiganaEnabled ? "translation skipped, furigana" : "translation skipped"))
                : rule.translationEnabled && rule.pronunciationEnabled
                ? (furiganaEnabled ? "translation/pronunciation/furigana" : "translation/pronunciation")
                : rule.translationEnabled
                ? (furiganaEnabled ? "translation/furigana" : "translation")
                : rule.pronunciationEnabled
                ? (furiganaEnabled ? "pronunciation/furigana" : "pronunciation")
                : "furigana";
        String suffix = " AI " + settings.provider.label + " "
                + taskLabel
                + " applied. source=" + sourceLang + ", pronunciation=" + pronunciationLang + ", target=" + targetLang + ".";
        return new LyricsResult(
                merged,
                baseResult.providerLabel,
                detail + suffix,
                baseResult.karaoke,
                baseResult.isrc,
                baseResult.spotifyTrackId,
                baseResult.contributors
        );
    }

    private LyricsLine mergeSupplementLine(LyricsLine line, List<SupplementResult> results) {
        if (line == null || results == null || results.isEmpty()) {
            return line;
        }

        String pronunciationText = joinSupplementResults(results, true);
        String translationText = joinSupplementResults(results, false);
        String furiganaText = joinFuriganaResults(results);
        if (line.vocalParts == null || line.vocalParts.isEmpty()) {
            return line.withSupplements(pronunciationText, translationText, furiganaText);
        }

        List<LyricsLine.VocalPart> parts = new ArrayList<>(line.vocalParts);
        boolean changedPart = false;
        for (SupplementResult result : results) {
            int partIndex = result.request.partIndex;
            if (partIndex < 0 || partIndex >= parts.size()) {
                continue;
            }
            LyricsLine.VocalPart part = parts.get(partIndex);
            parts.set(partIndex, part.withSupplements(result.pronunciation, result.translation, result.furigana));
            changedPart = true;
        }
        if (!changedPart) {
            return line.withSupplements(pronunciationText, translationText, furiganaText);
        }
        return new LyricsLine(
                line.startTimeMs,
                line.endTimeMs,
                line.text,
                line.syllables,
                line.speaker,
                line.kind,
                parts,
                pronunciationText,
                translationText,
                furiganaText
        );
    }

    private String joinSupplementResults(List<SupplementResult> results, boolean pronunciation) {
        StringBuilder builder = new StringBuilder();
        for (SupplementResult result : results) {
            String value = pronunciation ? result.pronunciation : result.translation;
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private String joinFuriganaResults(List<SupplementResult> results) {
        StringBuilder builder = new StringBuilder();
        for (SupplementResult result : results) {
            String value = result.furigana == null ? "" : result.furigana.trim();
            if (value.isEmpty()) {
                String fallback = result.request == null ? "" : result.request.text;
                value = fallback == null ? "" : fallback.trim();
            }
            if (value.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private String callProviderRaw(String prompt, AiLyricsSettings.Snapshot settings) throws Exception {
        List<String> apiKeys = parseApiKeys(settings.apiKeys);
        if (apiKeys.isEmpty()) {
            throw new IOException("API 키가 필요합니다");
        }

        Exception lastError = null;
        for (String apiKey : apiKeys) {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    return callProviderRawOnce(prompt, settings, apiKey);
                } catch (HttpStatusException error) {
                    lastError = error;
                    if (error.statusCode == 401) {
                        throw error;
                    }
                    if (error.statusCode == 403 || error.statusCode == 429) {
                        break;
                    }
                    if (attempt == 1) {
                        throw error;
                    }
                    Thread.sleep(900L * (attempt + 1));
                } catch (Exception error) {
                    lastError = error;
                    if (attempt == 1) {
                        throw error;
                    }
                    Thread.sleep(900L * (attempt + 1));
                }
            }
        }
        throw lastError == null ? new IOException("AI 제공자 요청 실패") : lastError;
    }

    private String callProviderRawOnce(String prompt, AiLyricsSettings.Snapshot settings, String apiKey) throws Exception {
        String providerId = settings.provider.id;
        if ("gemini".equals(providerId)) {
            return callGemini(prompt, settings, apiKey);
        }
        if ("claude".equals(providerId)) {
            return callClaude(prompt, settings, apiKey);
        }
        return callOpenAiCompatible(prompt, settings, apiKey);
    }

    private String callGemini(String prompt, AiLyricsSettings.Snapshot settings, String apiKey) throws Exception {
        String endpoint = trimRight(settings.baseUrl, "/")
                + "/models/" + urlPath(settings.model)
                + ":generateContent?key=" + urlQuery(apiKey);
        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        content.put("role", "user");
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        content.put("parts", parts);
        contents.put(content);
        body.put("contents", contents);
        JSONObject config = new JSONObject();
        config.put("maxOutputTokens", settings.maxTokens);
        config.put("temperature", settings.temperature);
        config.put("thinkingConfig", new JSONObject().put("thinkingBudget", 0));
        body.put("generationConfig", config);

        JSONObject response = new JSONObject(postJson(endpoint, body, Collections.singletonMap("Content-Type", "application/json")));
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new IOException("[Gemini] Empty response from API");
        }
        JSONObject candidate = candidates.optJSONObject(0);
        JSONObject responseContent = candidate == null ? null : candidate.optJSONObject("content");
        JSONArray responseParts = responseContent == null ? null : responseContent.optJSONArray("parts");
        StringBuilder builder = new StringBuilder();
        if (responseParts != null) {
            for (int index = 0; index < responseParts.length(); index++) {
                JSONObject part = responseParts.optJSONObject(index);
                if (part != null) {
                    builder.append(part.optString("text", ""));
                }
            }
        }
        String raw = builder.toString();
        if (raw.trim().isEmpty()) {
            throw new IOException("[Gemini] Empty response from API");
        }
        return raw;
    }

    private String callClaude(String prompt, AiLyricsSettings.Snapshot settings, String apiKey) throws Exception {
        String endpoint = trimRight(settings.baseUrl, "/") + "/messages";
        JSONObject body = new JSONObject();
        body.put("model", settings.model);
        body.put("max_tokens", settings.maxTokens);
        body.put("temperature", settings.temperature);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", prompt));
        body.put("messages", messages);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", "2023-06-01");

        JSONObject response = new JSONObject(postJson(endpoint, body, headers));
        JSONArray content = response.optJSONArray("content");
        StringBuilder builder = new StringBuilder();
        if (content != null) {
            for (int index = 0; index < content.length(); index++) {
                JSONObject part = content.optJSONObject(index);
                if (part != null) {
                    builder.append(part.optString("text", ""));
                }
            }
        }
        String raw = builder.toString();
        if (raw.trim().isEmpty()) {
            throw new IOException("[Claude] Empty response from API");
        }
        return raw;
    }

    private String callOpenAiCompatible(String prompt, AiLyricsSettings.Snapshot settings, String apiKey) throws Exception {
        String endpoint = openAiEndpoint(settings);
        JSONObject body = new JSONObject();
        body.put("model", settings.model);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", prompt));
        body.put("messages", messages);
        body.put(tokenField(settings.provider.id), settings.maxTokens);
        body.put("temperature", settings.temperature);

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        if ("openrouter".equals(settings.provider.id)) {
            headers.put("HTTP-Referer", "https://github.com/ivLis-STUDIO/ivLyrics");
            headers.put("X-Title", "ivLyrics");
        }

        JSONObject response = new JSONObject(postJson(endpoint, body, headers));
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IOException("[" + settings.provider.label + "] Empty response from API");
        }
        JSONObject choice = choices.optJSONObject(0);
        JSONObject message = choice == null ? null : choice.optJSONObject("message");
        String raw = extractOpenAiContent(message == null ? null : message.opt("content"));
        if (raw.trim().isEmpty()) {
            throw new IOException("[" + settings.provider.label + "] Empty response from API");
        }
        return raw;
    }

    private String openAiEndpoint(AiLyricsSettings.Snapshot settings) {
        String base = trimRight(settings.baseUrl, "/");
        if ("perplexity".equals(settings.provider.id)) {
            return base + "/chat/completions";
        }
        if ("pollinations".equals(settings.provider.id)) {
            return base + "/v1/chat/completions";
        }
        return base + "/chat/completions";
    }

    private String tokenField(String providerId) {
        return "chatgpt".equals(providerId) ? "max_completion_tokens" : "max_tokens";
    }

    private String postJson(String endpoint, JSONObject body, Map<String, String> headers) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
        int code = connection.getResponseCode();
        String response = readResponse(connection, code >= 400);
        connection.disconnect();
        if (code < 200 || code >= 300) {
            throw new HttpStatusException(code, extractErrorMessage(response, code));
        }
        return response;
    }

    private String readResponse(HttpURLConnection connection, boolean error) throws IOException {
        InputStream input;
        try {
            input = error ? connection.getErrorStream() : connection.getInputStream();
        } catch (IOException io) {
            input = connection.getErrorStream();
            if (input == null) {
                throw io;
            }
        }
        if (input == null) {
            return "";
        }
        try (BufferedInputStream buffered = new BufferedInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = buffered.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String extractErrorMessage(String response, int statusCode) {
        try {
            JSONObject object = new JSONObject(response == null ? "" : response);
            JSONObject error = object.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.isEmpty()) {
                    return "HTTP " + statusCode + ": " + message;
                }
            }
            String message = object.optString("message", "");
            if (!message.isEmpty()) {
                return "HTTP " + statusCode + ": " + message;
            }
        } catch (JSONException ignored) {
        }
        return "HTTP " + statusCode;
    }

    private String buildTranslationPrompt(List<SupplementRequest> requests, String lang) {
        AiLyricsSettings.Language langInfo = AiLyricsSettings.languageInfo(lang);
        int lineCount = requests == null ? 0 : requests.size();
        return "You are a lyrics translator. Translate these " + lineCount
                + " indexed rows of song lyrics into " + langInfo.name + " (" + langInfo.nativeName + ").\n\n"
                + "CRITICAL RULES:\n"
                + "- This is a TRANSLATION task - translate the MEANING of each line\n"
                + "- Output must be written in " + langInfo.name + " (" + langInfo.nativeName + ") only\n"
                + "- Do NOT output the original lyrics unchanged\n"
                + "- Do NOT output romanization or pronunciation instead of translation\n"
                + "- Input rows are ID-tagged as L0001, L0002, etc. Treat each ID as an immutable timing anchor\n"
                + "- Output EXACTLY " + lineCount + " rows, one output row for every input row\n"
                + "- Preserve every row ID exactly and keep the same order\n"
                + "- Output format must be: L0001<TAB>translated text\n"
                + "- Row L000N in the output must translate ONLY row L000N from the input\n"
                + "- Never merge adjacent rows, even if the sentence continues across rows\n"
                + "- Never split one row into multiple rows, even if the translation is long\n"
                + "- Never move a translation to the previous or next row\n"
                + "- If an input row is a short fragment, translate that fragment on the same ID; do not complete it using neighboring rows\n"
                + "- If an input row contains \" / \" between simultaneous vocal parts, preserve \" / \" and translate each part separately\n"
                + "- If an input row is empty or untranslatable, output the same ID followed by a tab and nothing else\n"
                + "- Keep music symbols and markers like [Chorus], (Yeah) as-is\n"
                + "- Do NOT add extra row IDs, line numbers, prefixes, or explanations\n"
                + "- Do NOT use JSON or code blocks\n"
                + "- Just output the ID-tagged translated rows, nothing else\n\n"
                + "INPUT_ROWS (tab-separated ID and source text):\n" + buildTaggedPayload(requests) + "\n\n"
                + "ID alignment example (format only; use the target language above for the real output):\n"
                + "Input:\nL0001\t生きていることとは\nL0002\t変わり続けることだ\n\n"
                + "Correct output:\nL0001\t살아 있다는 것은\nL0002\t계속 변해 가는 것이다\n\n"
                + "Wrong output:\nL0001\t살아 있다는 것은 계속 변해 가는 것이다\nL0002\t\n\n"
                + "OUTPUT_ROWS (" + lineCount + " rows, same IDs, tab-separated):";
    }

    private String buildMetadataTranslationPrompt(String title, String artist, String lang) {
        AiLyricsSettings.Language langInfo = AiLyricsSettings.languageInfo(lang);
        return "You translate music metadata for a now-playing screen.\n"
                + "Target language: " + langInfo.name + " (" + langInfo.nativeName + ").\n\n"
                + "CRITICAL RULES:\n"
                + "- Output exactly two lines and nothing else\n"
                + "- Line 1: translated or localized song title\n"
                + "- Line 2: localized artist display name\n"
                + "- For the song title, translate the meaning naturally into the target language\n"
                + "- For the artist, use a commonly known target-language name if it exists; otherwise use a natural phonetic transliteration\n"
                + "- Do not add labels like Title: or Artist:\n"
                + "- Do not add explanations, JSON, markdown, or code blocks\n"
                + "- If a field should remain unchanged, repeat it unchanged on its line\n\n"
                + "TITLE:\n" + (title == null ? "" : title.trim()) + "\n\n"
                + "ARTIST:\n" + (artist == null ? "" : artist.trim()) + "\n\n"
                + "OUTPUT (2 lines):";
    }

    private MetadataTranslation parseMetadataTranslation(
            String raw,
            String originalTitle,
            String originalArtist,
            String sourceLang,
            String targetLang
    ) {
        List<String> lines = parseTextLines(raw, 2);
        String title = cleanMetadataOutputLine(valueAt(lines, 0), "title");
        String artist = cleanMetadataOutputLine(valueAt(lines, 1), "artist");
        if (title.isEmpty()) {
            title = originalTitle == null ? "" : originalTitle.trim();
        }
        if (artist.isEmpty()) {
            artist = originalArtist == null ? "" : originalArtist.trim();
        }
        return new MetadataTranslation(title, artist, sourceLang, targetLang);
    }

    private String cleanMetadataOutputLine(String value, String kind) {
        String cleaned = (value == null ? "" : value)
                .replaceAll("(?i)```[a-z]*\\s*", "")
                .replace("```", "")
                .trim();
        if ("artist".equals(kind)) {
            cleaned = cleaned.replaceFirst("(?i)^\\s*(artist|artist name|아티스트|가수|아티스트명)\\s*[:：\\-]\\s*", "");
        } else {
            cleaned = cleaned.replaceFirst("(?i)^\\s*(title|song title|track title|제목|곡 제목|노래 제목)\\s*[:：\\-]\\s*", "");
        }
        return cleaned.trim();
    }

    private String buildPhoneticPrompt(List<SupplementRequest> requests, String lang) {
        AiLyricsSettings.Language langInfo = AiLyricsSettings.languageInfo(lang);
        String normalizedLang = AiLyricsSettings.normalizeLanguageCode(lang);
        int lineCount = requests == null ? 0 : requests.size();
        String scriptInstruction = phoneticScriptInstruction(normalizedLang, langInfo);
        String outputScript = pronunciationOutputScript(normalizedLang, langInfo);

        return "You are a pronunciation converter. Convert these " + lineCount
                + " indexed rows of lyrics into how they SOUND (pronunciation) for "
                + langInfo.name + " speakers.\n"
                + scriptInstruction + "\n\n"
                + "CRITICAL RULES:\n"
                + "- This is a PRONUNCIATION task, NOT a translation task\n"
                + "- Output how each line SOUNDS when spoken aloud, written ONLY in " + outputScript + "\n"
                + "- Never use the input language's original script unless it is also " + outputScript + "\n"
                + "- Do NOT translate the meaning of the lyrics\n"
                + "- Do NOT output the original lyrics unchanged\n"
                + "- Input rows are ID-tagged as L0001, L0002, etc. Treat each ID as an immutable timing anchor\n"
                + "- Output EXACTLY " + lineCount + " rows, one output row for every input row\n"
                + "- Preserve every row ID exactly and keep the same order\n"
                + "- Output format must be: L0001<TAB>pronunciation text\n"
                + "- Row L000N in the output must convert ONLY row L000N from the input\n"
                + "- Never merge adjacent rows, even if the phrase continues across rows\n"
                + "- Never split one row into multiple rows\n"
                + "- Never move pronunciation to the previous or next row\n"
                + "- If an input row is a short fragment, convert that fragment on the same ID; do not complete it using neighboring rows\n"
                + "- If an input row contains \" / \" between simultaneous vocal parts, preserve \" / \" and convert each part separately\n"
                + "- If an input row is empty or unpronounceable, output the same ID followed by a tab and nothing else\n"
                + "- Keep music symbols and markers like [Chorus], (Yeah) as-is\n"
                + "- Do NOT add extra row IDs, line numbers, prefixes, or explanations\n"
                + "- Do NOT use JSON or code blocks\n"
                + "- Just output the ID-tagged pronunciation rows, nothing else\n\n"
                + "INPUT_ROWS (tab-separated ID and source text):\n" + buildTaggedPayload(requests) + "\n\n"
                + "ID alignment example (format only; use the requested pronunciation script above for the real output):\n"
                + "Input:\nL0001\t生きていることとは\nL0002\t変わり続けることだ\n\n"
                + "Correct output for Korean pronunciation:\nL0001\t이키테이루 코토토와\nL0002\t카와리 츠즈케루 코토다\n\n"
                + "Wrong output:\nL0001\t이키테이루 코토토와 카와리 츠즈케루 코토다\nL0002\t\n\n"
                + "OUTPUT_ROWS (" + lineCount + " rows, same IDs, tab-separated pronunciation only):";
    }

    private String buildFuriganaPrompt(List<SupplementRequest> requests) {
        int lineCount = requests == null ? 0 : requests.size();
        return "You are a Japanese furigana annotator for song lyrics. Add hiragana ruby readings above kanji.\n\n"
                + "CRITICAL RULES:\n"
                + "- This is a FURIGANA task, NOT a translation or romanization task\n"
                + "- Output must preserve the exact original visible Japanese text for each row\n"
                + "- Annotate kanji or kanji compounds only using this exact markup: <ruby>漢字<rt>かんじ</rt></ruby>\n"
                + "- Use hiragana readings inside <rt>, never katakana, romaji, Hangul, or translations\n"
                + "- Do not wrap kana-only text in ruby\n"
                + "- Do not add spaces, remove spaces, change punctuation, or normalize characters\n"
                + "- If a row has no kanji, output the original row text unchanged\n"
                + "- Input rows are ID-tagged as L0001, L0002, etc. Treat each ID as an immutable timing anchor\n"
                + "- Output EXACTLY " + lineCount + " rows, one output row for every input row\n"
                + "- Preserve every row ID exactly and keep the same order\n"
                + "- Output format must be: L0001<TAB>original text with ruby tags\n"
                + "- Row L000N in the output must annotate ONLY row L000N from the input\n"
                + "- Never merge adjacent rows and never split one row into multiple rows\n"
                + "- If an input row contains \" / \" between simultaneous vocal parts, preserve \" / \" exactly\n"
                + "- Do NOT output explanations, JSON, markdown, code blocks, or extra row IDs\n\n"
                + "INPUT_ROWS (tab-separated ID and source text):\n" + buildTaggedPayload(requests) + "\n\n"
                + "Correct output example:\n"
                + "L0001\t<ruby>紅蓮<rt>ぐれん</rt></ruby>の<ruby>弓矢<rt>ゆみや</rt></ruby>\n"
                + "L0002\tだから<ruby>一定<rt>いってい</rt></ruby>の<ruby>距離<rt>きょり</rt></ruby>は<ruby>保<rt>たも</rt></ruby>った\n\n"
                + "Wrong output examples:\n"
                + "L0001\tGuren no Yumiya\n"
                + "L0001\t홍련의 화살\n"
                + "L0001\t紅蓮の弓矢の読み方はぐれんのゆみやです\n\n"
                + "OUTPUT_ROWS (" + lineCount + " rows, same IDs, tab-separated ruby text only):";
    }

    private String phoneticScriptInstruction(String lang, AiLyricsSettings.Language langInfo) {
        switch (AiLyricsSettings.normalizeLanguageCode(lang)) {
            case "ko":
                return "Use Korean Hangul syllables only. Example: こんにちは -> 콘니치와, ありがとう -> 아리가토, hello -> 헬로. "
                        + "Never output Japanese kana, Chinese characters, or Latin romanization for Korean pronunciation.";
            case "en":
                return "Use Latin alphabet only (romanization). Example: こんにちは -> konnichiwa, 안녕하세요 -> annyeonghaseyo. "
                        + "Never output Hangul, kana, or Chinese characters for English romanization.";
            case "ja":
                return "Use Japanese Katakana only. Example: hello -> ハロー, 안녕하세요 -> アンニョンハセヨ. "
                        + "Prefer Katakana over Hiragana for foreign pronunciation guides.";
            case "zh-CN":
                return "Use Simplified Chinese characters only for a Chinese pronunciation guide. "
                        + "Do not output Latin pinyin unless the input itself is a non-pronounceable marker.";
            case "zh-TW":
                return "Use Traditional Chinese characters only for a Chinese pronunciation guide. "
                        + "Do not output Latin pinyin unless the input itself is a non-pronounceable marker.";
            case "hi":
                return "Use Devanagari script only for Hindi pronunciation. " + langInfo.phoneticDescription;
            case "es":
                return "Use Spanish spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for Spanish speakers using the Latin alphabet; do not translate meanings.";
            case "fr":
                return "Use French spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for French speakers using the Latin alphabet; do not translate meanings.";
            case "ar":
                return "Use Arabic script only for Arabic pronunciation. " + langInfo.phoneticDescription;
            case "fa":
                return "Use Persian script only for Persian pronunciation. " + langInfo.phoneticDescription;
            case "de":
                return "Use German spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for German speakers using the Latin alphabet; do not translate meanings.";
            case "ru":
                return "Use Cyrillic script only for Russian pronunciation. " + langInfo.phoneticDescription;
            case "sv":
                return "Use Swedish spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for Swedish speakers using the Latin alphabet; do not translate meanings.";
            case "pt":
                return "Use Portuguese spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for Portuguese speakers using the Latin alphabet; do not translate meanings.";
            case "bn":
                return "Use Bengali script only for Bengali pronunciation. " + langInfo.phoneticDescription;
            case "it":
                return "Use Italian spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for Italian speakers using the Latin alphabet; do not translate meanings.";
            case "th":
                return "Use Thai script only for Thai pronunciation. " + langInfo.phoneticDescription;
            case "vi":
                return "Use Vietnamese Quốc Ngữ spelling only for pronunciation guides. "
                        + "Use Vietnamese diacritics where they help pronunciation; do not translate meanings.";
            case "id":
                return "Use Indonesian spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for Indonesian speakers using the Latin alphabet; do not translate meanings.";
            case "ms":
                return "Use Malay spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for Malay speakers using the Latin alphabet; do not translate meanings.";
            default:
                return "Write pronunciation in " + langInfo.nativeName + " spelling. " + langInfo.phoneticDescription;
        }
    }

    private String pronunciationOutputScript(String lang, AiLyricsSettings.Language langInfo) {
        switch (AiLyricsSettings.normalizeLanguageCode(lang)) {
            case "ko":
                return "Korean Hangul";
            case "en":
                return "Latin alphabet";
            case "ja":
                return "Japanese Katakana";
            case "zh-CN":
                return "Simplified Chinese";
            case "zh-TW":
                return "Traditional Chinese";
            case "hi":
                return "Devanagari";
            case "es":
                return "Spanish Latin spelling";
            case "fr":
                return "French Latin spelling";
            case "ar":
                return "Arabic script";
            case "fa":
                return "Persian script";
            case "de":
                return "German Latin spelling";
            case "ru":
                return "Cyrillic";
            case "sv":
                return "Swedish Latin spelling";
            case "pt":
                return "Portuguese Latin spelling";
            case "bn":
                return "Bengali script";
            case "it":
                return "Italian Latin spelling";
            case "th":
                return "Thai script";
            case "vi":
                return "Vietnamese Quốc Ngữ";
            case "id":
                return "Indonesian Latin spelling";
            case "ms":
                return "Malay Latin spelling";
            default:
                return langInfo.name + " pronunciation spelling";
        }
    }

    private List<String> parseTextLines(String text, int expectedLineCount) {
        String cleaned = (text == null ? "" : text)
                .replaceAll("(?i)```[a-z]*\\s*", "")
                .replace("```", "")
                .trim();
        List<String> lines = new ArrayList<>();
        Collections.addAll(lines, cleaned.split("\\r?\\n", -1));
        if (lines.size() == expectedLineCount) {
            return lines;
        }
        if (lines.size() > expectedLineCount) {
            return new ArrayList<>(lines.subList(lines.size() - expectedLineCount, lines.size()));
        }
        while (lines.size() < expectedLineCount) {
            lines.add("");
        }
        return lines;
    }

    private List<String> parseTaggedTextLines(
            String text,
            List<SupplementRequest> requests,
            String taskName,
            LogSink log
    ) {
        int expectedLineCount = requests == null ? 0 : requests.size();
        List<String> values = emptySupplementList(expectedLineCount);
        if (expectedLineCount <= 0) {
            return values;
        }

        String cleaned = stripCodeFences(text == null ? "" : text);
        String[] rawLines = cleaned.split("\\r?\\n", -1);
        boolean[] seen = new boolean[expectedLineCount];
        int matched = 0;
        int duplicate = 0;
        for (String rawLine : rawLines) {
            TaggedOutputLine tagged = parseTaggedOutputLine(rawLine);
            if (tagged == null || tagged.index < 0 || tagged.index >= expectedLineCount) {
                continue;
            }
            String value = cleanSupplementOutput(tagged.value);
            if (seen[tagged.index]) {
                duplicate++;
                if (values.get(tagged.index).trim().isEmpty() && !value.isEmpty()) {
                    values.set(tagged.index, value);
                }
                continue;
            }
            seen[tagged.index] = true;
            matched++;
            values.set(tagged.index, value);
        }

        if (matched == expectedLineCount) {
            if (duplicate > 0 && log != null) {
                log.write("ai " + taskName + " alignment: duplicate IDs ignored=" + duplicate);
            }
            return values;
        }

        if (matched > 0) {
            if (log != null) {
                log.write("ai " + taskName + " alignment: matched=" + matched
                        + "/" + expectedLineCount + ", missing rows left empty");
            }
            return values;
        }

        if (log != null) {
            log.write("ai " + taskName + " alignment: no row IDs in response, using line-count fallback");
        }
        return parseTextLines(text, expectedLineCount);
    }

    private List<String> parseFuriganaTextLines(String text, List<SupplementRequest> requests, LogSink log) {
        List<String> values = parseTaggedTextLines(text, requests, "furigana", log);
        if (values.isEmpty() || requests == null || requests.isEmpty()) {
            return values;
        }
        int dropped = 0;
        List<String> sanitized = new ArrayList<>();
        int count = Math.min(values.size(), requests.size());
        for (int index = 0; index < count; index++) {
            String value = sanitizeFuriganaOutput(values.get(index), requests.get(index).text);
            if (!values.get(index).trim().isEmpty() && value.isEmpty() && containsKanji(requests.get(index).text)) {
                dropped++;
            }
            sanitized.add(value);
        }
        while (sanitized.size() < values.size()) {
            sanitized.add("");
        }
        if (dropped > 0 && log != null) {
            log.write("ai furigana alignment: dropped invalid ruby rows=" + dropped);
        }
        return sanitized;
    }

    private static String sanitizeFuriganaOutput(String value, String original) {
        String cleaned = cleanSupplementOutput(value)
                .replace("&lt;ruby&gt;", "<ruby>")
                .replace("&lt;/ruby&gt;", "</ruby>")
                .replace("&lt;rt&gt;", "<rt>")
                .replace("&lt;/rt&gt;", "</rt>")
                .trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        String originalText = promptRowText(original);
        if (!containsKanji(originalText)) {
            return "";
        }
        if (!cleaned.contains("<ruby>")) {
            return cleaned.equals(originalText) ? "" : "";
        }
        String plain = stripRubyMarkup(cleaned);
        if (!plain.equals(originalText)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        Matcher matcher = RUBY_TAG_PATTERN.matcher(cleaned);
        int cursor = 0;
        while (matcher.find()) {
            String before = cleaned.substring(cursor, matcher.start());
            if (before.contains("<") || before.contains(">")) {
                return "";
            }
            builder.append(before);
            String base = matcher.group(1);
            String reading = matcher.group(2);
            if (base == null || reading == null || base.trim().isEmpty() || reading.trim().isEmpty()) {
                return "";
            }
            if (!containsKanji(base)) {
                builder.append(base);
            } else {
                builder.append("<ruby>")
                        .append(base)
                        .append("<rt>")
                        .append(reading)
                        .append("</rt></ruby>");
            }
            cursor = matcher.end();
        }
        String tail = cleaned.substring(cursor);
        if (tail.contains("<") || tail.contains(">")) {
            return "";
        }
        builder.append(tail);
        return builder.toString();
    }

    private static List<String> emptySupplementList(int size) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            values.add("");
        }
        return values;
    }

    private static TaggedOutputLine parseTaggedOutputLine(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        Matcher matcher = TAGGED_OUTPUT_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        String rawNumber = firstNonEmpty(matcher.group(1), matcher.group(2), matcher.group(3));
        int number;
        try {
            number = Integer.parseInt(rawNumber);
        } catch (Exception ignored) {
            return null;
        }
        if (number <= 0) {
            return null;
        }
        return new TaggedOutputLine(number - 1, matcher.group(4));
    }

    private static String cleanSupplementOutput(String value) {
        String cleaned = value == null ? "" : value.trim();
        cleaned = cleaned.replaceFirst("(?i)^\\s*(translation|translated text|pronunciation|pronunciation text|romanization|furigana|ruby|reading|번역|발음|후리가나|후라가나)\\s*[:：\\-]\\s*", "");
        if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.equals("<empty>")
                || lower.equals("[empty]")
                || lower.equals("(empty)")
                || lower.equals("empty")
                || cleaned.equals("∅")) {
            return "";
        }
        return cleaned;
    }

    private static String stripCodeFences(String value) {
        return (value == null ? "" : value)
                .replaceAll("(?i)```[a-z]*\\s*", "")
                .replace("```", "");
    }

    static String buildPayloadText(List<LyricsLine> lines) {
        return payloadTextForRequests(buildSupplementRequests(lines));
    }

    private static boolean shouldGenerateFurigana(AiLyricsSettings.Snapshot settings, String sourceLang, String textPayload) {
        return settings != null
                && settings.japaneseFuriganaEnabled
                && "ja".equalsIgnoreCase(AiLyricsSettings.normalizeLanguageCode(sourceLang))
                && containsKanji(textPayload);
    }

    private static boolean containsKanji(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if ((codePoint >= 0x3400 && codePoint <= 0x4DBF)
                    || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                    || (codePoint >= 0xF900 && codePoint <= 0xFAFF)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static String stripRubyMarkup(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuffer buffer = new StringBuffer();
        Matcher matcher = RUBY_TAG_PATTERN.matcher(value);
        while (matcher.find()) {
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(buffer);
        return buffer.toString().replaceAll("<[^>]+>", "");
    }

    private static String buildTaggedPayload(List<SupplementRequest> requests) {
        StringBuilder builder = new StringBuilder();
        if (requests == null) {
            return "";
        }
        for (int index = 0; index < requests.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(rowId(index))
                    .append('\t')
                    .append(promptRowText(requests.get(index).text));
        }
        return builder.toString();
    }

    private static String rowId(int index) {
        return String.format(Locale.ROOT, "L%04d", index + 1);
    }

    private static String promptRowText(String value) {
        return (value == null ? "" : value)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
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

    private static String payloadTextForRequests(List<SupplementRequest> requests) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < requests.size(); index++) {
            if (index > 0) {
                builder.append('\n');
            }
            builder.append(requests.get(index).text);
        }
        return builder.toString();
    }

    private static List<SupplementRequest> buildSupplementRequests(List<LyricsLine> lines) {
        List<SupplementRequest> requests = new ArrayList<>();
        if (lines == null) {
            return requests;
        }
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            LyricsLine line = lines.get(lineIndex);
            List<SupplementRequest> vocalRequests = displayedVocalPartRequests(line, lineIndex);
            if (vocalRequests.size() > 1) {
                requests.addAll(vocalRequests);
            } else {
                requests.add(new SupplementRequest(lineIndex, -1, displayLineText(line)));
            }
        }
        return requests;
    }

    private static List<SupplementRequest> displayedVocalPartRequests(LyricsLine line, int lineIndex) {
        List<SupplementRequest> requests = new ArrayList<>();
        if (line == null || line.vocalParts == null || line.vocalParts.isEmpty()) {
            return requests;
        }
        for (int index = 0; index < line.vocalParts.size(); index++) {
            LyricsLine.VocalPart part = line.vocalParts.get(index);
            if (!"lead".equals(part.role)) {
                continue;
            }
            String text = displayPartText(part);
            if (!text.isEmpty()) {
                requests.add(new SupplementRequest(lineIndex, index, text));
            }
        }
        for (int index = 0; index < line.vocalParts.size(); index++) {
            LyricsLine.VocalPart part = line.vocalParts.get(index);
            if ("lead".equals(part.role)) {
                continue;
            }
            String text = displayPartText(part);
            if (!text.isEmpty()) {
                requests.add(new SupplementRequest(lineIndex, index, text));
            }
        }
        return requests;
    }

    private static String displayLineText(LyricsLine line) {
        if (line == null) {
            return "";
        }
        if (line.text != null && !line.text.trim().isEmpty()) {
            return line.text.trim();
        }
        StringBuilder builder = new StringBuilder();
        if (line.vocalParts != null) {
            for (LyricsLine.VocalPart part : line.vocalParts) {
                if (part.text == null || part.text.trim().isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(" / ");
                }
                builder.append(part.text.trim());
            }
        }
        return builder.toString();
    }

    private static String displayPartText(LyricsLine.VocalPart part) {
        if (part == null) {
            return "";
        }
        if (part.text != null && !part.text.trim().isEmpty()) {
            return part.text.trim();
        }
        StringBuilder builder = new StringBuilder();
        if (part.syllables != null) {
            for (LyricsLine.Syllable syllable : part.syllables) {
                if (syllable != null && syllable.text != null) {
                    builder.append(syllable.text);
                }
            }
        }
        return builder.toString().trim();
    }

    private List<String> parseApiKeys(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>();
        if (value.startsWith("[")) {
            try {
                JSONArray array = new JSONArray(value);
                for (int index = 0; index < array.length(); index++) {
                    String item = array.optString(index, "").trim();
                    if (!item.isEmpty()) {
                        keys.add(item);
                    }
                }
                return keys;
            } catch (JSONException ignored) {
            }
        }
        for (String item : value.split("[\\n,]")) {
            String key = item.trim();
            if (!key.isEmpty()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private String extractOpenAiContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String) {
            return (String) content;
        }
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < array.length(); index++) {
                Object item = array.opt(index);
                if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    builder.append(object.optString("text", ""));
                } else if (item != null) {
                    builder.append(item);
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    private String valueAt(List<String> values, int index) {
        if (values == null || index < 0 || index >= values.size()) {
            return "";
        }
        return values.get(index) == null ? "" : values.get(index);
    }

    private MetadataTranslation metadataTranslationFromPrefs(String cacheKey) {
        if (metadataPrefs == null || cacheKey == null || cacheKey.trim().isEmpty()) {
            return null;
        }
        try {
            String raw = metadataPrefs.getString(sha256(cacheKey), "");
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            JSONObject object = new JSONObject(raw);
            return new MetadataTranslation(
                    object.optString("title", ""),
                    object.optString("artist", ""),
                    object.optString("sourceLang", ""),
                    object.optString("targetLang", "")
            );
        } catch (Exception ignored) {
            metadataPrefs.edit().remove(sha256(cacheKey)).apply();
            return null;
        }
    }

    private void putMetadataTranslationToPrefs(String cacheKey, MetadataTranslation translation) {
        if (metadataPrefs == null
                || cacheKey == null
                || cacheKey.trim().isEmpty()
                || translation == null
                || (translation.title.isEmpty() && translation.artist.isEmpty())) {
            return;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("cacheKey", cacheKey);
            object.put("title", translation.title);
            object.put("artist", translation.artist);
            object.put("sourceLang", translation.sourceLang);
            object.put("targetLang", translation.targetLang);
            object.put("savedAtMs", System.currentTimeMillis());
            metadataPrefs.edit().putString(sha256(cacheKey), object.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void removeCacheEntriesByPrefix(Map<String, ?> target, String prefix) {
        if (target == null || prefix == null || prefix.isEmpty()) {
            return;
        }
        List<String> removeKeys = new ArrayList<>();
        for (String key : target.keySet()) {
            if (key != null && key.startsWith(prefix)) {
                removeKeys.add(key);
            }
        }
        for (String key : removeKeys) {
            target.remove(key);
        }
    }

    private void clearMetadataPrefsForTrack(String trackKey) {
        if (metadataPrefs == null || trackKey == null || trackKey.trim().isEmpty()) {
            return;
        }
        String prefix = "metadata|" + trackKey.trim() + "|";
        SharedPreferences.Editor editor = null;
        for (Map.Entry<String, ?> entry : metadataPrefs.getAll().entrySet()) {
            Object rawValue = entry.getValue();
            if (!(rawValue instanceof String)) {
                continue;
            }
            try {
                JSONObject object = new JSONObject((String) rawValue);
                if (object.optString("cacheKey", "").startsWith(prefix)) {
                    if (editor == null) {
                        editor = metadataPrefs.edit();
                    }
                    editor.remove(entry.getKey());
                }
            } catch (Exception ignored) {
            }
        }
        if (editor != null) {
            editor.apply();
        }
    }

    private String trimRight(String value, String suffix) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith(suffix)) {
            result = result.substring(0, result.length() - suffix.length());
        }
        return result;
    }

    private String urlPath(String value) {
        return (value == null ? "" : value.trim()).replace(" ", "%20");
    }

    private String urlQuery(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return String.valueOf(value == null ? 0 : value.hashCode());
        }
    }

    private void emitLog(String trackKey, Callback callback, String message) {
        mainHandler.post(() -> callback.onAiLyricsLog(trackKey, message));
    }

    static String detectLanguage(String text) {
        String value = text == null ? "" : text;
        if (value.trim().isEmpty()) {
            return "en";
        }

        int kana = 0;
        int hangul = 0;
        int han = 0;
        int simplifiedHint = 0;
        int traditionalHint = 0;
        int cyrillic = 0;
        int arabic = 0;
        int persianHint = 0;
        int thai = 0;
        int devanagari = 0;
        int bengali = 0;
        int latin = 0;
        int letters = 0;

        for (int offset = 0; offset < value.length(); ) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            letters++;
            Character.UnicodeScript script = Character.UnicodeScript.of(cp);
            switch (script) {
                case HIRAGANA:
                case KATAKANA:
                    kana++;
                    break;
                case HANGUL:
                    hangul++;
                    break;
                case HAN:
                    han++;
                    if (SIMPLIFIED_HINTS.indexOf(cp) >= 0) {
                        simplifiedHint++;
                    }
                    if (TRADITIONAL_HINTS.indexOf(cp) >= 0) {
                        traditionalHint++;
                    }
                    break;
                case CYRILLIC:
                    cyrillic++;
                    break;
                case ARABIC:
                    arabic++;
                    if (PERSIAN_HINTS.indexOf(cp) >= 0) {
                        persianHint++;
                    }
                    break;
                case THAI:
                    thai++;
                    break;
                case DEVANAGARI:
                    devanagari++;
                    break;
                case BENGALI:
                    bengali++;
                    break;
                case LATIN:
                    latin++;
                    break;
                default:
                    break;
            }
        }

        int threshold = Math.max(2, Math.round(Math.max(1, letters) * 0.08f));
        if (kana >= 2) {
            return "ja";
        }
        if (hangul >= threshold) {
            return "ko";
        }
        if (thai >= threshold) {
            return "th";
        }
        if (devanagari >= threshold) {
            return "hi";
        }
        if (bengali >= threshold) {
            return "bn";
        }
        if (arabic >= threshold) {
            return persianHint > 0 ? "fa" : "ar";
        }
        if (cyrillic >= threshold) {
            return "ru";
        }
        if (han >= Math.max(1, threshold)) {
            return traditionalHint > simplifiedHint ? "zh-TW" : "zh-CN";
        }
        if (latin > 0) {
            return detectLatinLanguage(value);
        }
        return "en";
    }

    private static String detectLatinLanguage(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.matches("(?s).*[ăâđêôơưạảấầẩẫậắằẳẵặếềểễệịỉọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ].*")) {
            return "vi";
        }
        if (lower.matches("(?s).*[å].*")) {
            return "sv";
        }
        if (lower.matches("(?s).*[ßü].*")) {
            return "de";
        }
        if (lower.matches("(?s).*[ñ¿¡].*")) {
            return "es";
        }
        if (lower.matches("(?s).*[ãõ].*")) {
            return "pt";
        }
        if (lower.matches("(?s).*[æœçëïÿ].*")) {
            return "fr";
        }

        Map<String, Integer> scores = new HashMap<>();
        scoreWords(scores, lower, "en", "the", "and", "you", "that", "with", "love", "your", "for", "not", "we", "are");
        scoreWords(scores, lower, "es", "que", "de", "el", "la", "y", "en", "un", "una", "mi", "tu", "no", "por");
        scoreWords(scores, lower, "fr", "que", "de", "le", "la", "les", "et", "je", "tu", "pas", "mon", "pour", "dans");
        scoreWords(scores, lower, "pt", "que", "de", "o", "a", "e", "eu", "voce", "você", "não", "por", "meu", "pra");
        scoreWords(scores, lower, "it", "che", "di", "il", "la", "e", "io", "tu", "non", "per", "mio", "nel", "sono");
        scoreWords(scores, lower, "de", "ich", "du", "und", "der", "die", "das", "nicht", "mein", "mit", "ein", "ist");
        scoreWords(scores, lower, "sv", "och", "det", "jag", "du", "inte", "att", "min", "med", "en", "är", "för");
        scoreWords(scores, lower, "id", "aku", "kamu", "yang", "dan", "di", "ke", "tak", "tidak", "cinta", "ini", "itu");
        scoreWords(scores, lower, "ms", "aku", "kamu", "yang", "dan", "di", "ke", "tak", "tidak", "cinta", "ini", "itu", "kau");

        String best = "en";
        int bestScore = 0;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                best = entry.getKey();
                bestScore = entry.getValue();
            }
        }
        return bestScore >= 2 ? best : "en";
    }

    private static void scoreWords(Map<String, Integer> scores, String text, String lang, String... words) {
        int score = 0;
        for (String word : words) {
            if (text.matches("(?s).*\\b" + java.util.regex.Pattern.quote(word) + "\\b.*")) {
                score++;
            }
        }
        if (score > 0) {
            scores.put(lang, score);
        }
    }

    private static final String SIMPLIFIED_HINTS = "这为国们会来时说对过还后个无爱声体见长门马鸟鱼龙云";
    private static final String TRADITIONAL_HINTS = "這為國們會來時說對過還後個無愛聲體見長門馬鳥魚龍雲";
    private static final String PERSIAN_HINTS = "پچژگک";

    private static final class HttpStatusException extends IOException {
        final int statusCode;

        HttpStatusException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
