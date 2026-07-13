package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AiLyricsRepository {
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 70_000;
    private static final long STREAM_PARTIAL_DISPATCH_INTERVAL_MS = 600L;
    private static final String SUPPLEMENT_PROMPT_VERSION = "v4-id-aligned-ai-only";
    private static final String TMI_PROMPT_VERSION = "origin-v1";
    private static final String SUPPLEMENT_TASK_PRONUNCIATION = "pronunciation";
    private static final String SUPPLEMENT_TASK_TRANSLATION = "translation";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final Pattern TAGGED_OUTPUT_PATTERN = Pattern.compile(
            "^\\s*(?:[-*]\\s*)?(?:\\[?L(\\d{1,4})\\]?|(?:row|line)\\s*(\\d{1,4})|#?(\\d{1,4}))\\s*(?:\\t|[:：|\\-]|\\.\\s+|\\s+)\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LATIN_WORD_SEPARATOR_PATTERN = Pattern.compile("[^\\p{L}\\p{N}_]+");
    private static final String VIETNAMESE_HINTS = "ăâđêôơưạảấầẩẫậắằẳẵặếềểễệịỉọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ";
    private static final String CZECH_UNIQUE_HINTS = "ěřů";
    private static final String TURKISH_UNIQUE_HINTS = "ğış";
    private static final String GERMAN_HINTS = "ßü";
    private static final String SPANISH_HINTS = "ñ¿¡";
    private static final String PORTUGUESE_HINTS = "ãõ";
    private static final String FRENCH_HINTS = "æœçëïÿ";
    private static final String LATIN_LANGUAGE_HINTS = VIETNAMESE_HINTS
            + CZECH_UNIQUE_HINTS
            + TURKISH_UNIQUE_HINTS
            + "å"
            + GERMAN_HINTS
            + SPANISH_HINTS
            + PORTUGUESE_HINTS
            + FRENCH_HINTS;
    private static final int VIETNAMESE_HINTS_END = VIETNAMESE_HINTS.length();
    private static final int CZECH_HINTS_END = VIETNAMESE_HINTS_END + CZECH_UNIQUE_HINTS.length();
    private static final int TURKISH_HINTS_END = CZECH_HINTS_END + TURKISH_UNIQUE_HINTS.length();
    private static final int SWEDISH_HINTS_END = TURKISH_HINTS_END + 1;
    private static final int GERMAN_HINTS_END = SWEDISH_HINTS_END + GERMAN_HINTS.length();
    private static final int SPANISH_HINTS_END = GERMAN_HINTS_END + SPANISH_HINTS.length();
    private static final int PORTUGUESE_HINTS_END = SPANISH_HINTS_END + PORTUGUESE_HINTS.length();

    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, LyricsResult> cache = new ConcurrentHashMap<>();
    private final Map<String, MetadataTranslation> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, TmiInfo> tmiCache = new ConcurrentHashMap<>();
    private final LyricsDiskCache diskCache;
    private final SharedPreferences metadataPrefs;
    private final SharedPreferences tmiPrefs;

    AiLyricsRepository() {
        diskCache = null;
        metadataPrefs = null;
        tmiPrefs = null;
    }

    AiLyricsRepository(Context context) {
        diskCache = context == null
                ? null
                : new LyricsDiskCache(context.getApplicationContext(), "ai_lyrics", 500);
        metadataPrefs = context == null
                ? null
                : context.getApplicationContext().getSharedPreferences("ai_metadata_translations", Context.MODE_PRIVATE);
        tmiPrefs = context == null
                ? null
                : context.getApplicationContext().getSharedPreferences("ai_tmi_cache", Context.MODE_PRIVATE);
    }

    interface Callback {
        void onAiLyricsLoaded(String trackKey, LyricsResult result);

        void onAiLyricsPartialLoaded(
                String trackKey,
                LyricsResult result,
                boolean pronunciationLoading,
                boolean translationLoading,
                boolean finished,
                boolean hadError
        );

        void onAiLyricsError(String trackKey, String message);

        void onAiLyricsTaskError(
                String trackKey,
                String message,
                boolean pronunciationLoading,
                boolean translationLoading,
                boolean finished
        );

        void onAiLyricsLog(String trackKey, String message);

        void onAiMetadataTranslationLoaded(String trackKey, MetadataTranslation translation);

        void onAiMetadataTranslationError(String trackKey, String message);

        void onAiTmiLoaded(String trackKey, TmiInfo info);

        void onAiTmiError(String trackKey, String message);
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

    static final class TmiSource {
        final String title;
        final String url;

        TmiSource(String title, String url) {
            this.title = title == null ? "" : title.trim();
            this.url = url == null ? "" : url.trim();
        }

        String displayTitle() {
            if (!title.isEmpty()) {
                return title;
            }
            try {
                String host = new URL(url).getHost();
                return host == null || host.trim().isEmpty()
                        ? url
                        : host.replaceFirst("^www\\.", "");
            } catch (Exception ignored) {
                return url;
            }
        }
    }

    static final class TmiInfo {
        final String description;
        final List<String> trivia;
        final List<TmiSource> verifiedSources;
        final List<TmiSource> relatedSources;
        final List<TmiSource> otherSources;
        final String confidence;
        final boolean hasVerifiedSources;
        final int verifiedSourceCount;
        final int relatedSourceCount;
        final int totalSourceCount;
        final String targetLang;

        TmiInfo(
                String description,
                List<String> trivia,
                List<TmiSource> verifiedSources,
                List<TmiSource> relatedSources,
                List<TmiSource> otherSources,
                String confidence,
                boolean hasVerifiedSources,
                int verifiedSourceCount,
                int relatedSourceCount,
                int totalSourceCount,
                String targetLang
        ) {
            this.description = description == null ? "" : description.trim();
            this.trivia = immutableStringList(trivia);
            this.verifiedSources = immutableSourceList(verifiedSources);
            this.relatedSources = immutableSourceList(relatedSources);
            this.otherSources = immutableSourceList(otherSources);
            this.confidence = confidence == null ? "" : confidence.trim();
            this.hasVerifiedSources = hasVerifiedSources;
            this.verifiedSourceCount = Math.max(0, verifiedSourceCount);
            this.relatedSourceCount = Math.max(0, relatedSourceCount);
            this.totalSourceCount = Math.max(0, totalSourceCount);
            this.targetLang = AiLyricsSettings.normalizeLanguageCode(targetLang);
        }

        boolean hasContent() {
            return !description.isEmpty() || !trivia.isEmpty();
        }

        List<TmiSource> allSources() {
            List<TmiSource> sources = new ArrayList<>();
            sources.addAll(verifiedSources);
            sources.addAll(relatedSources);
            sources.addAll(otherSources);
            return sources;
        }

        JSONObject toJson(String cacheKey) throws JSONException {
            JSONObject object = new JSONObject();
            object.put("cacheKey", cacheKey == null ? "" : cacheKey);
            object.put("description", description);
            object.put("trivia", stringArray(trivia));
            object.put("verifiedSources", sourceArray(verifiedSources));
            object.put("relatedSources", sourceArray(relatedSources));
            object.put("otherSources", sourceArray(otherSources));
            object.put("confidence", confidence);
            object.put("hasVerifiedSources", hasVerifiedSources);
            object.put("verifiedSourceCount", verifiedSourceCount);
            object.put("relatedSourceCount", relatedSourceCount);
            object.put("totalSourceCount", totalSourceCount);
            object.put("targetLang", targetLang);
            object.put("savedAtMs", System.currentTimeMillis());
            return object;
        }

        static TmiInfo fromJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            return new TmiInfo(
                    object.optString("description", ""),
                    stringList(object.optJSONArray("trivia")),
                    sourceList(object.optJSONArray("verifiedSources")),
                    sourceList(object.optJSONArray("relatedSources")),
                    sourceList(object.optJSONArray("otherSources")),
                    object.optString("confidence", ""),
                    object.optBoolean("hasVerifiedSources", false),
                    object.optInt("verifiedSourceCount", 0),
                    object.optInt("relatedSourceCount", 0),
                    object.optInt("totalSourceCount", 0),
                    object.optString("targetLang", "")
            );
        }

        private static List<String> immutableStringList(List<String> values) {
            List<String> copy = new ArrayList<>();
            if (values != null) {
                for (String value : values) {
                    if (value != null && !value.trim().isEmpty()) {
                        copy.add(value.trim());
                    }
                }
            }
            return Collections.unmodifiableList(copy);
        }

        private static List<TmiSource> immutableSourceList(List<TmiSource> values) {
            List<TmiSource> copy = new ArrayList<>();
            if (values != null) {
                for (TmiSource value : values) {
                    if (value != null && !value.url.isEmpty()) {
                        copy.add(value);
                    }
                }
            }
            return Collections.unmodifiableList(copy);
        }

        private static JSONArray stringArray(List<String> values) {
            JSONArray array = new JSONArray();
            if (values != null) {
                for (String value : values) {
                    array.put(value == null ? "" : value);
                }
            }
            return array;
        }

        private static JSONArray sourceArray(List<TmiSource> values) throws JSONException {
            JSONArray array = new JSONArray();
            if (values != null) {
                for (TmiSource source : values) {
                    if (source == null || source.url.isEmpty()) {
                        continue;
                    }
                    array.put(new JSONObject()
                            .put("title", source.title)
                            .put("url", source.url));
                }
            }
            return array;
        }

        private static List<String> stringList(JSONArray array) {
            if (array == null) {
                return Collections.emptyList();
            }
            List<String> values = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                String value = array.optString(index, "").trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
            return values;
        }

        private static List<TmiSource> sourceList(JSONArray array) {
            if (array == null) {
                return Collections.emptyList();
            }
            List<TmiSource> values = new ArrayList<>();
            for (int index = 0; index < array.length(); index++) {
                Object raw = array.opt(index);
                TmiSource source = null;
                if (raw instanceof JSONObject) {
                    JSONObject object = (JSONObject) raw;
                    source = new TmiSource(
                            object.optString("title", ""),
                            firstNonEmpty(object.optString("url", ""), object.optString("uri", ""))
                    );
                } else if (raw instanceof String) {
                    source = new TmiSource("", (String) raw);
                }
                if (source != null && !source.url.isEmpty()) {
                    values.add(source);
                }
            }
            return values;
        }
    }

    private interface LogSink {
        void write(String message);
    }

    private interface TextDeltaSink {
        void onDelta(String text) throws Exception;
    }

    private interface StreamRowSink {
        void onRow(int index, String value);
    }

    private interface SseDataHandler {
        String onEvent(String eventName, String data) throws Exception;
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
        SupplementResult(SupplementRequest request, String pronunciation, String translation) {
            this.request = request;
            this.pronunciation = pronunciation == null ? "" : pronunciation;
            this.translation = translation == null ? "" : translation;
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

    private static final class SupplementSession {
        final LyricsResult baseResult;
        final List<SupplementRequest> requests;
        final boolean needsPronunciation;
        final boolean needsTranslation;
        List<String> pronunciation = Collections.emptyList();
        List<String> translation = Collections.emptyList();
        boolean pronunciationFinished;
        boolean translationFinished;
        boolean hadError;

        SupplementSession(
                LyricsResult baseResult,
                List<SupplementRequest> requests,
                boolean needsPronunciation,
                boolean needsTranslation
        ) {
            this.baseResult = baseResult;
            this.requests = requests == null ? Collections.emptyList() : requests;
            this.needsPronunciation = needsPronunciation;
            this.needsTranslation = needsTranslation;
            this.pronunciationFinished = !needsPronunciation;
            this.translationFinished = !needsTranslation;
        }

        synchronized void setPronunciation(List<String> values) {
            pronunciation = normalizedCopy(values, requests.size());
            pronunciationFinished = true;
        }

        synchronized void setTranslation(List<String> values) {
            translation = normalizedCopy(values, requests.size());
            translationFinished = true;
        }

        synchronized void setPronunciationValue(int index, String value) {
            pronunciation = withValue(pronunciation, requests.size(), index, value);
        }

        synchronized void setTranslationValue(int index, String value) {
            translation = withValue(translation, requests.size(), index, value);
        }

        synchronized void markPronunciationFailed() {
            pronunciationFinished = true;
            hadError = true;
        }

        synchronized void markTranslationFailed() {
            translationFinished = true;
            hadError = true;
        }

        synchronized boolean pronunciationLoading() {
            return needsPronunciation && !pronunciationFinished;
        }

        synchronized boolean translationLoading() {
            return needsTranslation && !translationFinished;
        }

        synchronized boolean finished() {
            return pronunciationFinished && translationFinished;
        }

        synchronized boolean hasError() {
            return hadError;
        }

        synchronized List<String> pronunciationSnapshot() {
            return normalizedCopy(pronunciation, requests.size());
        }

        synchronized List<String> translationSnapshot() {
            return normalizedCopy(translation, requests.size());
        }

        private static List<String> normalizedCopy(List<String> values, int size) {
            List<String> copy = values == null ? new ArrayList<>() : new ArrayList<>(values);
            while (copy.size() < size) {
                copy.add("");
            }
            if (copy.size() > size) {
                copy = new ArrayList<>(copy.subList(0, size));
            }
            return copy;
        }

        private static List<String> withValue(List<String> values, int size, int index, String value) {
            List<String> copy = normalizedCopy(values, size);
            if (index >= 0 && index < copy.size()) {
                copy.set(index, value == null ? "" : value);
            }
            return copy;
        }
    }

    private final class SupplementPartialDispatcher {
        private final String trackKey;
        private final SupplementSession session;
        private final AiLyricsSettings.Snapshot settings;
        private final AiLyricsSettings.LanguageRule rule;
        private final String sourceLang;
        private final String targetLang;
        private final String pronunciationLang;
        private final boolean translationSkipped;
        private final Callback callback;
        private final Runnable pendingRunnable = this::dispatchPending;
        private boolean pending;
        private long lastDispatchMs;

        SupplementPartialDispatcher(
                String trackKey,
                SupplementSession session,
                AiLyricsSettings.Snapshot settings,
                AiLyricsSettings.LanguageRule rule,
                String sourceLang,
                String targetLang,
                String pronunciationLang,
                boolean translationSkipped,
                Callback callback
        ) {
            this.trackKey = trackKey;
            this.session = session;
            this.settings = settings;
            this.rule = rule;
            this.sourceLang = sourceLang;
            this.targetLang = targetLang;
            this.pronunciationLang = pronunciationLang;
            this.translationSkipped = translationSkipped;
            this.callback = callback;
        }

        void request() {
            boolean dispatchNow = false;
            long delay = STREAM_PARTIAL_DISPATCH_INTERVAL_MS;
            synchronized (this) {
                long now = SystemClock.uptimeMillis();
                long elapsed = now - lastDispatchMs;
                if (elapsed >= STREAM_PARTIAL_DISPATCH_INTERVAL_MS && !pending) {
                    lastDispatchMs = now;
                    dispatchNow = true;
                } else if (!pending) {
                    pending = true;
                    delay = Math.max(1L, STREAM_PARTIAL_DISPATCH_INTERVAL_MS - elapsed);
                } else {
                    return;
                }
            }
            if (dispatchNow) {
                dispatch();
            } else {
                mainHandler.postDelayed(pendingRunnable, delay);
            }
        }

        void flush() {
            boolean shouldDispatch;
            synchronized (this) {
                shouldDispatch = pending;
                pending = false;
                lastDispatchMs = SystemClock.uptimeMillis();
            }
            mainHandler.removeCallbacks(pendingRunnable);
            if (shouldDispatch) {
                dispatch();
            }
        }

        void cancelPending() {
            synchronized (this) {
                pending = false;
            }
            mainHandler.removeCallbacks(pendingRunnable);
        }

        private void dispatchPending() {
            synchronized (this) {
                if (!pending) {
                    return;
                }
                pending = false;
                lastDispatchMs = SystemClock.uptimeMillis();
            }
            dispatch();
        }

        private void dispatch() {
            LyricsResult result = buildMergedSupplementResult(
                    session.baseResult,
                    session.requests,
                    session.pronunciationSnapshot(),
                    session.translationSnapshot(),
                    settings,
                    rule,
                    sourceLang,
                    targetLang,
                    pronunciationLang,
                    translationSkipped
            );
            boolean pronunciationLoading = session.pronunciationLoading();
            boolean translationLoading = session.translationLoading();
            boolean finished = session.finished();
            boolean hadError = session.hasError();
            mainHandler.post(() -> callback.onAiLyricsPartialLoaded(
                    trackKey,
                    result,
                    pronunciationLoading,
                    translationLoading,
                    finished,
                    hadError
            ));
        }
    }

    private static final class TaggedTextStreamAccumulator {
        private final int expectedLineCount;
        private final boolean[] seen;
        private final StringBuilder pending = new StringBuilder();
        private int newlineSearchStart;
        private int matched;
        private int duplicate;

        TaggedTextStreamAccumulator(int expectedLineCount) {
            this.expectedLineCount = Math.max(0, expectedLineCount);
            this.seen = new boolean[this.expectedLineCount];
        }

        void append(String delta, StreamRowSink sink) {
            if (delta == null || delta.isEmpty() || expectedLineCount <= 0) {
                return;
            }
            pending.append(delta);
            drain(false, sink);
        }

        void finish(StreamRowSink sink) {
            drain(true, sink);
        }

        int matchedCount() {
            return matched;
        }

        int duplicateCount() {
            return duplicate;
        }

        private void drain(boolean flush, StreamRowSink sink) {
            while (true) {
                int newline = firstNewlineIndex(pending, newlineSearchStart);
                if (newline < 0) {
                    newlineSearchStart = pending.length();
                    break;
                }
                String line = pending.substring(0, newline);
                int removeLength = newline + 1;
                if (newline + 1 < pending.length()
                        && pending.charAt(newline) == '\r'
                        && pending.charAt(newline + 1) == '\n') {
                    removeLength = newline + 2;
                }
                pending.delete(0, removeLength);
                newlineSearchStart = 0;
                emitLine(line, sink);
            }
            if (flush && pending.length() > 0) {
                String line = pending.toString();
                pending.setLength(0);
                newlineSearchStart = 0;
                emitLine(line, sink);
            }
        }

        private void emitLine(String rawLine, StreamRowSink sink) {
            TaggedOutputLine tagged = parseTaggedOutputLine(stripCodeFences(rawLine));
            if (tagged == null || tagged.index < 0 || tagged.index >= expectedLineCount) {
                return;
            }
            String value = cleanSupplementOutput(tagged.value);
            if (seen[tagged.index]) {
                duplicate++;
                return;
            }
            seen[tagged.index] = true;
            matched++;
            if (sink != null) {
                sink.onRow(tagged.index, value);
            }
        }

        private static int firstNewlineIndex(StringBuilder value, int startIndex) {
            for (int index = startIndex; index < value.length(); index++) {
                char c = value.charAt(index);
                if (c == '\n' || c == '\r') {
                    return index;
                }
            }
            return -1;
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
        if (!rule.enabled()) {
            emitLog(trackKey, callback, "ai lyrics skipped for source=" + sourceLang
                    + ": translation=false / pronunciation=false");
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
                cached = rebaseCachedSupplements(cached, baseResult);
                cache.put(cacheKey, cached);
                emitLog(trackKey, callback, "ai lyrics cache hit: " + settings.provider.label);
                callback.onAiLyricsLoaded(trackKey, cached);
                return;
            }
            LyricsResult diskCached = diskCache == null ? null : diskCache.get(cacheKey);
            if (diskCached != null) {
                diskCached = rebaseCachedSupplements(diskCached, baseResult);
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
                + " / pronunciation=" + rule.pronunciationEnabled);

        List<SupplementRequest> requests = buildSupplementRequests(baseResult.lines);
        boolean translationSkipped = settings.shouldSkipTranslation(sourceLang, targetLang);
        boolean needsPronunciation = rule.pronunciationEnabled;
        boolean needsTranslation = rule.translationEnabled && !translationSkipped;
        if (translationSkipped) {
            emitLog(trackKey, callback, "ai translation skipped: source language matches target (" + sourceLang + " -> " + targetLang + ")");
        }
        SupplementSession session = new SupplementSession(baseResult, requests, needsPronunciation, needsTranslation);
        String pronunciationCacheKey = supplementTaskCacheKey(
                trackKey,
                detectedSourceLang,
                sourceLang,
                settings,
                textPayload,
                SUPPLEMENT_TASK_PRONUNCIATION,
                pronunciationLang
        );
        String translationCacheKey = supplementTaskCacheKey(
                trackKey,
                detectedSourceLang,
                sourceLang,
                settings,
                textPayload,
                SUPPLEMENT_TASK_TRANSLATION,
                targetLang
        );

        if (!bypassCache && needsPronunciation) {
            LyricsResult pronunciationCached = cachedResult(pronunciationCacheKey);
            if (pronunciationCached != null) {
                session.setPronunciation(extractSupplementValues(pronunciationCached, requests, true));
                emitLog(trackKey, callback, "ai pronunciation cache hit: " + settings.provider.label);
            }
        }
        if (!bypassCache && needsTranslation) {
            LyricsResult translationCached = cachedResult(translationCacheKey);
            if (translationCached != null) {
                session.setTranslation(extractSupplementValues(translationCached, requests, false));
                emitLog(trackKey, callback, "ai translation cache hit: " + settings.provider.label);
            }
        }

        if (session.finished()) {
            LyricsResult result = buildMergedSupplementResult(
                    baseResult,
                    requests,
                    session.pronunciationSnapshot(),
                    session.translationSnapshot(),
                    settings,
                    rule,
                    sourceLang,
                    targetLang,
                    pronunciationLang,
                    translationSkipped
            );
            cacheResult(cacheKey, result);
            callback.onAiLyricsLoaded(trackKey, result);
            return;
        }

        LyricsResult cachedPartial = buildMergedSupplementResult(
                baseResult,
                requests,
                session.pronunciationSnapshot(),
                session.translationSnapshot(),
                settings,
                rule,
                sourceLang,
                targetLang,
                pronunciationLang,
                translationSkipped
        );
        if (needsPronunciation != session.pronunciationLoading() || needsTranslation != session.translationLoading()) {
            callback.onAiLyricsPartialLoaded(
                    trackKey,
                    cachedPartial,
                    session.pronunciationLoading(),
                    session.translationLoading(),
                    false,
                    false
            );
        }

        LogSink log = message -> emitLog(trackKey, callback, message);
        SupplementPartialDispatcher partialDispatcher = new SupplementPartialDispatcher(
                trackKey,
                session,
                settings,
                rule,
                sourceLang,
                targetLang,
                pronunciationLang,
                translationSkipped,
                callback
        );
        if (session.pronunciationLoading()) {
            executor.execute(() -> loadSupplementTask(
                    trackKey,
                    settings,
                    session,
                    cacheKey,
                    pronunciationCacheKey,
                    rule,
                    sourceLang,
                    targetLang,
                    pronunciationLang,
                    translationSkipped,
                    SUPPLEMENT_TASK_PRONUNCIATION,
                    partialDispatcher,
                    log,
                    callback
            ));
        }
        if (session.translationLoading()) {
            executor.execute(() -> loadSupplementTask(
                    trackKey,
                    settings,
                    session,
                    cacheKey,
                    translationCacheKey,
                    rule,
                    sourceLang,
                    targetLang,
                    pronunciationLang,
                    translationSkipped,
                    SUPPLEMENT_TASK_TRANSLATION,
                    partialDispatcher,
                    log,
                    callback
            ));
        }
    }

    private LyricsResult rebaseCachedSupplements(LyricsResult result, LyricsResult baseResult) {
        if (result == null || baseResult == null) {
            return result;
        }

        List<LyricsLine> rebasedLines = new ArrayList<>();
        for (int index = 0; index < baseResult.lines.size(); index++) {
            LyricsLine baseLine = baseResult.lines.get(index);
            LyricsLine cachedLine = index < result.lines.size() ? result.lines.get(index) : null;
            rebasedLines.add(rebaseCachedSupplementLine(baseLine, cachedLine));
        }

        String detail = baseResult.detail;
        int aiSuffixStart = result.detail.indexOf(" AI ");
        if (aiSuffixStart >= 0) {
            detail += result.detail.substring(aiSuffixStart);
        }
        return resultWithBaseIdentity(
                baseResult,
                rebasedLines,
                detail
        );
    }

    private LyricsLine rebaseCachedSupplementLine(LyricsLine baseLine, LyricsLine cachedLine) {
        if (baseLine == null || cachedLine == null) {
            return baseLine;
        }
        if (baseLine.vocalParts == null || baseLine.vocalParts.isEmpty()) {
            return baseLine.withSupplements(
                    cachedLine.pronunciationText,
                    cachedLine.translationText,
                    baseLine.furiganaText
            );
        }

        List<LyricsLine.VocalPart> parts = new ArrayList<>(baseLine.vocalParts.size());
        for (int index = 0; index < baseLine.vocalParts.size(); index++) {
            LyricsLine.VocalPart basePart = baseLine.vocalParts.get(index);
            LyricsLine.VocalPart cachedPart = cachedLine.vocalParts != null && index < cachedLine.vocalParts.size()
                    ? cachedLine.vocalParts.get(index)
                    : null;
            parts.add(cachedPart == null
                    ? basePart
                    : basePart.withSupplements(
                    cachedPart.pronunciationText,
                    cachedPart.translationText,
                    basePart.furiganaText
            ));
        }
        return new LyricsLine(
                baseLine.startTimeMs,
                baseLine.endTimeMs,
                baseLine.text,
                baseLine.syllables,
                baseLine.speaker,
                baseLine.speakerColor,
                baseLine.speakerFallback,
                baseLine.kind,
                parts,
                cachedLine.pronunciationText,
                cachedLine.translationText,
                baseLine.furiganaText
        );
    }

    static boolean hasSameBaseLyrics(LyricsResult expected, LyricsResult candidate) {
        if (expected == candidate) {
            return true;
        }
        if (expected == null || candidate == null
                || expected.karaoke != candidate.karaoke
                || !expected.providerId.equals(candidate.providerId)
                || !expected.selectionPolicyKey.equals(candidate.selectionPolicyKey)
                || !expected.providerLabel.equals(candidate.providerLabel)
                || !expected.isrc.equals(candidate.isrc)
                || !expected.spotifyTrackId.equals(candidate.spotifyTrackId)
                || expected.lines.size() != candidate.lines.size()) {
            return false;
        }
        for (int index = 0; index < expected.lines.size(); index++) {
            if (!hasSameBaseLine(expected.lines.get(index), candidate.lines.get(index))) {
                return false;
            }
        }
        return true;
    }

    static LyricsResult resultWithBaseIdentity(
            LyricsResult baseResult,
            List<LyricsLine> lines,
            String detail
    ) {
        if (baseResult == null) {
            return null;
        }
        return new LyricsResult(
                lines,
                baseResult.providerLabel,
                detail,
                baseResult.karaoke,
                baseResult.isrc,
                baseResult.spotifyTrackId,
                baseResult.contributors,
                baseResult.providerId,
                baseResult.selectionPolicyKey
        );
    }

    private static boolean hasSameBaseLine(LyricsLine expected, LyricsLine candidate) {
        if (expected == candidate) {
            return true;
        }
        if (expected == null || candidate == null
                || expected.startTimeMs != candidate.startTimeMs
                || expected.endTimeMs != candidate.endTimeMs
                || !expected.text.equals(candidate.text)
                || !expected.speaker.equals(candidate.speaker)
                || !expected.speakerColor.equals(candidate.speakerColor)
                || !expected.speakerFallback.equals(candidate.speakerFallback)
                || !expected.kind.equals(candidate.kind)
                || !hasSameBaseSyllables(expected.syllables, candidate.syllables)
                || expected.vocalParts.size() != candidate.vocalParts.size()) {
            return false;
        }
        for (int index = 0; index < expected.vocalParts.size(); index++) {
            if (!hasSameBaseVocalPart(expected.vocalParts.get(index), candidate.vocalParts.get(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasSameBaseVocalPart(
            LyricsLine.VocalPart expected,
            LyricsLine.VocalPart candidate
    ) {
        return expected == candidate || (expected != null
                && candidate != null
                && expected.id.equals(candidate.id)
                && expected.role.equals(candidate.role)
                && expected.speaker.equals(candidate.speaker)
                && expected.speakerColor.equals(candidate.speakerColor)
                && expected.speakerFallback.equals(candidate.speakerFallback)
                && expected.kind.equals(candidate.kind)
                && expected.text.equals(candidate.text)
                && hasSameBaseSyllables(expected.syllables, candidate.syllables));
    }

    private static boolean hasSameBaseSyllables(
            List<LyricsLine.Syllable> expected,
            List<LyricsLine.Syllable> candidate
    ) {
        if (expected == candidate) {
            return true;
        }
        if (expected == null || candidate == null || expected.size() != candidate.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            LyricsLine.Syllable expectedSyllable = expected.get(index);
            LyricsLine.Syllable candidateSyllable = candidate.get(index);
            if (expectedSyllable == null || candidateSyllable == null
                    || expectedSyllable.startTimeMs != candidateSyllable.startTimeMs
                    || expectedSyllable.endTimeMs != candidateSyllable.endTimeMs
                    || !expectedSyllable.text.equals(candidateSyllable.text)) {
                return false;
            }
        }
        return true;
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

    void loadTmi(
            TrackSnapshot track,
            AiLyricsSettings.Snapshot settings,
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
        String targetLang = settings.pronunciationLanguage();
        String cacheKey = "tmi|"
                + trackKey
                + "|lang=" + targetLang
                + "|prompt=" + TMI_PROMPT_VERSION
                + "|provider=" + settings.provider.id
                + "|model=" + settings.model
                + "|url=" + settings.baseUrl
                + "|tok=" + settings.maxTokens
                + "|temp=" + settings.temperature
                + "|text=" + sha256(title + "\n" + artist);

        if (!bypassCache) {
            TmiInfo cached = tmiCache.get(cacheKey);
            if (cached != null) {
                emitLog(trackKey, callback, "ai tmi cache hit: " + settings.provider.label);
                callback.onAiTmiLoaded(trackKey, cached);
                return;
            }
            TmiInfo persisted = tmiFromPrefs(cacheKey);
            if (persisted != null) {
                tmiCache.put(cacheKey, persisted);
                emitLog(trackKey, callback, "ai tmi disk cache hit: " + settings.provider.label);
                callback.onAiTmiLoaded(trackKey, persisted);
                return;
            }
        }

        if (!settings.hasApiKey()) {
            emitLog(trackKey, callback, "ai tmi skipped: API key missing for " + settings.provider.label);
            callback.onAiTmiError(trackKey, "AI 제공자 API 키가 설정되지 않았습니다");
            return;
        }

        emitLog(trackKey, callback, "ai tmi: provider=" + settings.provider.label
                + " / model=" + settings.model
                + " / target=" + targetLang);

        executor.execute(() -> {
            LogSink log = message -> emitLog(trackKey, callback, message);
            try {
                String raw = callProviderRaw(buildTmiPrompt(title, artist, targetLang), settings);
                TmiInfo info = parseTmiInfo(raw, targetLang);
                tmiCache.put(cacheKey, info);
                putTmiToPrefs(cacheKey, info);
                log.write("ai tmi response: description=" + !info.description.isEmpty()
                        + " / trivia=" + info.trivia.size()
                        + " / sources=" + info.allSources().size()
                        + " / confidence=" + info.confidence);
                mainHandler.post(() -> callback.onAiTmiLoaded(trackKey, info));
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                log.write("ai tmi error: " + message);
                mainHandler.post(() -> callback.onAiTmiError(trackKey, message));
            }
        });
    }

    void clearCache() {
        cache.clear();
        metadataCache.clear();
        tmiCache.clear();
        if (diskCache != null) {
            diskCache.clear();
        }
        if (metadataPrefs != null) {
            metadataPrefs.edit().clear().apply();
        }
        if (tmiPrefs != null) {
            tmiPrefs.edit().clear().apply();
        }
    }

    void clearTrackCache(String trackKey) {
        String key = trackKey == null ? "" : trackKey.trim();
        if (key.isEmpty()) {
            return;
        }
        removeCacheEntriesByPrefix(cache, key + "|");
        removeCacheEntriesByPrefix(metadataCache, "metadata|" + key + "|");
        removeCacheEntriesByPrefix(tmiCache, "tmi|" + key + "|");
        if (diskCache != null) {
            diskCache.removeByKeyPrefix(key + "|");
        }
        clearMetadataPrefsForTrack(key);
        clearTmiPrefsForTrack(key);
    }

    void clearMemoryCache() {
        cache.clear();
        metadataCache.clear();
        tmiCache.clear();
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private void loadSupplementTask(
            String trackKey,
            AiLyricsSettings.Snapshot settings,
            SupplementSession session,
            String combinedCacheKey,
            String taskCacheKey,
            AiLyricsSettings.LanguageRule rule,
            String sourceLang,
            String targetLang,
            String pronunciationLang,
            boolean translationSkipped,
            String task,
            SupplementPartialDispatcher partialDispatcher,
            LogSink log,
            Callback callback
    ) {
        try {
            List<SupplementRequest> requests = session.requests;
            int expectedLineCount = requests.size();
            boolean pronunciation = SUPPLEMENT_TASK_PRONUNCIATION.equals(task);
            List<String> values;
            String prompt;
            if (pronunciation) {
                prompt = buildPhoneticPrompt(requests, pronunciationLang);
                log.write("ai pronunciation stream request: lines=" + expectedLineCount + " / pronunciation=" + pronunciationLang);
                values = loadSupplementValuesStreamFirst(
                        prompt,
                        settings,
                        requests,
                        SUPPLEMENT_TASK_PRONUNCIATION,
                        session,
                        true,
                        trackKey,
                        rule,
                        sourceLang,
                        targetLang,
                        pronunciationLang,
                        translationSkipped,
                        partialDispatcher,
                        log
                );
                log.write("ai pronunciation response: lines=" + values.size());
                session.setPronunciation(values);
            } else {
                prompt = buildTranslationPrompt(requests, targetLang);
                log.write("ai translation stream request: lines=" + expectedLineCount);
                values = loadSupplementValuesStreamFirst(
                        prompt,
                        settings,
                        requests,
                        SUPPLEMENT_TASK_TRANSLATION,
                        session,
                        false,
                        trackKey,
                        rule,
                        sourceLang,
                        targetLang,
                        pronunciationLang,
                        translationSkipped,
                        partialDispatcher,
                        log
                );
                log.write("ai translation response: lines=" + values.size());
                session.setTranslation(values);
            }

            LyricsResult taskResult = buildTaskResult(
                    session.baseResult,
                    requests,
                    values,
                    pronunciation
            );
            cacheResult(taskCacheKey, taskResult);

            LyricsResult result = buildMergedSupplementResult(
                    session.baseResult,
                    requests,
                    session.pronunciationSnapshot(),
                    session.translationSnapshot(),
                    settings,
                    rule,
                    sourceLang,
                    targetLang,
                    pronunciationLang,
                    translationSkipped
            );
            partialDispatcher.cancelPending();
            if (session.finished() && !session.hasError()) {
                cacheResult(combinedCacheKey, result);
                mainHandler.post(() -> callback.onAiLyricsLoaded(trackKey, result));
            } else {
                mainHandler.post(() -> callback.onAiLyricsPartialLoaded(
                        trackKey,
                        result,
                        session.pronunciationLoading(),
                        session.translationLoading(),
                        session.finished(),
                        session.hasError()
                ));
            }
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            if (SUPPLEMENT_TASK_PRONUNCIATION.equals(task)) {
                session.markPronunciationFailed();
                log.write("ai pronunciation error: " + message);
            } else {
                session.markTranslationFailed();
                log.write("ai translation error: " + message);
            }
            partialDispatcher.flush();
            mainHandler.post(() -> callback.onAiLyricsTaskError(
                    trackKey,
                    message,
                    session.pronunciationLoading(),
                    session.translationLoading(),
                    session.finished()
            ));
        }
    }

    private List<String> loadSupplementValuesStreamFirst(
            String prompt,
            AiLyricsSettings.Snapshot settings,
            List<SupplementRequest> requests,
            String taskName,
            SupplementSession session,
            boolean pronunciation,
            String trackKey,
            AiLyricsSettings.LanguageRule rule,
            String sourceLang,
            String targetLang,
            String pronunciationLang,
            boolean translationSkipped,
            SupplementPartialDispatcher partialDispatcher,
            LogSink log
    ) throws Exception {
        TaggedTextStreamAccumulator accumulator = new TaggedTextStreamAccumulator(requests.size());
        StreamRowSink rowSink = (index, value) -> {
            if (pronunciation) {
                session.setPronunciationValue(index, value);
            } else {
                session.setTranslationValue(index, value);
            }
            partialDispatcher.request();
        };

        try {
            String raw = callProviderStreamRaw(prompt, settings, delta -> accumulator.append(delta, rowSink));
            accumulator.finish(rowSink);
            if (accumulator.duplicateCount() > 0 && log != null) {
                log.write("ai " + taskName + " stream alignment: duplicate IDs ignored=" + accumulator.duplicateCount());
            }
            if (accumulator.matchedCount() > 0 && log != null) {
                log.write("ai " + taskName + " stream rows=" + accumulator.matchedCount() + "/" + requests.size());
            }
            return parseTaggedTextLines(raw, requests, taskName, log);
        } catch (Exception streamError) {
            if (log != null) {
                log.write("ai " + taskName + " stream fallback: " + errorMessage(streamError));
            }
            String raw = callProviderRaw(prompt, settings);
            return parseTaggedTextLines(raw, requests, taskName, log);
        }
    }

    private LyricsResult buildMergedSupplementResult(
            LyricsResult baseResult,
            List<SupplementRequest> requests,
            List<String> pronunciation,
            List<String> translation,
            AiLyricsSettings.Snapshot settings,
            AiLyricsSettings.LanguageRule rule,
            String sourceLang,
            String targetLang,
            String pronunciationLang,
            boolean translationSkipped
    ) {
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
                    valueAt(translation, index)
            ));
        }

        List<LyricsLine> merged = new ArrayList<>();
        for (int index = 0; index < baseResult.lines.size(); index++) {
            LyricsLine line = baseResult.lines.get(index);
            merged.add(mergeSupplementLine(line, resultsByLine.get(index)));
        }

        String detail = baseResult.detail;
        String suffix = "";
        if (settings != null && rule != null) {
            boolean pronunciationApplied = rule.pronunciationEnabled && pronunciation != null && !pronunciation.isEmpty();
            boolean translationApplied = rule.translationEnabled
                    && !translationSkipped
                    && translation != null
                    && !translation.isEmpty();
            String taskLabel = translationSkipped
                    ? (pronunciationApplied
                    ? "translation skipped, pronunciation"
                    : "translation skipped")
                    : translationApplied && pronunciationApplied
                    ? "translation/pronunciation"
                    : translationApplied
                    ? "translation"
                    : pronunciationApplied
                    ? "pronunciation"
                    : "none";
            suffix = " AI " + settings.provider.label + " "
                    + taskLabel
                    + " applied. source=" + sourceLang + ", pronunciation=" + pronunciationLang + ", target=" + targetLang + ".";
        }
        return resultWithBaseIdentity(
                baseResult,
                merged,
                detail + suffix
        );
    }

    private LyricsResult buildTaskResult(
            LyricsResult baseResult,
            List<SupplementRequest> requests,
            List<String> values,
            boolean pronunciation
    ) {
        return buildMergedSupplementResult(
                baseResult,
                requests,
                pronunciation ? values : Collections.emptyList(),
                pronunciation ? Collections.emptyList() : values,
                null,
                null,
                "",
                "",
                "",
                false
        );
    }

    private LyricsResult cachedResult(String key) {
        LyricsResult cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        LyricsResult diskCached = diskCache == null ? null : diskCache.get(key);
        if (diskCached != null) {
            cache.put(key, diskCached);
        }
        return diskCached;
    }

    private void cacheResult(String key, LyricsResult result) {
        if (key == null || key.trim().isEmpty() || result == null || result.lines.isEmpty()) {
            return;
        }
        cache.put(key, result);
        if (diskCache != null) {
            diskCache.put(key, result);
        }
    }

    private String supplementTaskCacheKey(
            String trackKey,
            String detectedSourceLang,
            String sourceLang,
            AiLyricsSettings.Snapshot settings,
            String textPayload,
            String task,
            String outputLang
    ) {
        return trackKey
                + "|source=" + sourceLang
                + "|detected=" + detectedSourceLang
                + "|prompt=" + SUPPLEMENT_PROMPT_VERSION
                + "|task=" + task
                + "|provider=" + settings.provider.id
                + "|model=" + settings.model
                + "|url=" + settings.baseUrl
                + "|tok=" + settings.maxTokens
                + "|temp=" + settings.temperature
                + "|output=" + outputLang
                + "|text=" + sha256(textPayload);
    }

    private List<String> extractSupplementValues(
            LyricsResult result,
            List<SupplementRequest> requests,
            boolean pronunciation
    ) {
        if (result == null || requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (SupplementRequest request : requests) {
            LyricsLine line = request.lineIndex >= 0 && request.lineIndex < result.lines.size()
                    ? result.lines.get(request.lineIndex)
                    : null;
            String value = "";
            if (line != null
                    && request.partIndex >= 0
                    && line.vocalParts != null
                    && request.partIndex < line.vocalParts.size()) {
                LyricsLine.VocalPart part = line.vocalParts.get(request.partIndex);
                value = pronunciation ? part.pronunciationText : part.translationText;
            } else if (line != null) {
                value = pronunciation ? line.pronunciationText : line.translationText;
            }
            values.add(value == null ? "" : value);
        }
        return values;
    }

    private LyricsLine mergeSupplementLine(LyricsLine line, List<SupplementResult> results) {
        if (line == null || results == null || results.isEmpty()) {
            return line;
        }

        String pronunciationText = joinSupplementResults(results, true);
        String translationText = joinSupplementResults(results, false);
        String furiganaText = line.furiganaText;
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
            parts.set(partIndex, part.withSupplements(result.pronunciation, result.translation, part.furiganaText));
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
                line.speakerColor,
                line.speakerFallback,
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

    private String callProviderStreamRaw(
            String prompt,
            AiLyricsSettings.Snapshot settings,
            TextDeltaSink sink
    ) throws Exception {
        List<String> apiKeys = providerApiKeys(settings);
        if (apiKeys.isEmpty()) {
            throw new IOException("API 키가 필요합니다");
        }

        Exception lastError = null;
        for (String apiKey : apiKeys) {
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    return callProviderStreamRawOnce(prompt, settings, apiKey, sink);
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
        throw lastError == null ? new IOException("AI 제공자 스트림 요청 실패") : lastError;
    }

    private String callProviderStreamRawOnce(
            String prompt,
            AiLyricsSettings.Snapshot settings,
            String apiKey,
            TextDeltaSink sink
    ) throws Exception {
        String providerId = settings.provider.id;
        if ("gemini".equals(providerId)) {
            return callGeminiStream(prompt, settings, apiKey, sink);
        }
        if ("claude".equals(providerId)) {
            return callClaudeStream(prompt, settings, apiKey, sink);
        }
        return callOpenAiCompatibleStream(prompt, settings, apiKey, sink);
    }

    private String callProviderRaw(String prompt, AiLyricsSettings.Snapshot settings) throws Exception {
        List<String> apiKeys = providerApiKeys(settings);
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

    private String callGeminiStream(
            String prompt,
            AiLyricsSettings.Snapshot settings,
            String apiKey,
            TextDeltaSink sink
    ) throws Exception {
        String endpoint = trimRight(settings.baseUrl, "/")
                + "/models/" + urlPath(settings.model)
                + ":streamGenerateContent?alt=sse&key=" + urlQuery(apiKey);
        JSONObject body = geminiBody(prompt, settings);
        return postJsonSse(endpoint, body, Collections.singletonMap("Content-Type", "application/json"), (eventName, data) -> {
            if (data == null || data.trim().isEmpty() || "[DONE]".equals(data.trim())) {
                return "";
            }
            JSONObject response = new JSONObject(data);
            JSONArray candidates = response.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                return "";
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
            return builder.toString();
        }, sink);
    }

    private String callGemini(String prompt, AiLyricsSettings.Snapshot settings, String apiKey) throws Exception {
        String endpoint = trimRight(settings.baseUrl, "/")
                + "/models/" + urlPath(settings.model)
                + ":generateContent?key=" + urlQuery(apiKey);
        JSONObject body = geminiBody(prompt, settings);

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

    private JSONObject geminiBody(String prompt, AiLyricsSettings.Snapshot settings) throws JSONException {
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
        return body;
    }

    private String callClaudeStream(
            String prompt,
            AiLyricsSettings.Snapshot settings,
            String apiKey,
            TextDeltaSink sink
    ) throws Exception {
        String endpoint = trimRight(settings.baseUrl, "/") + "/messages";
        JSONObject body = claudeBody(prompt, settings);
        body.put("stream", true);
        Map<String, String> headers = claudeHeaders(apiKey);
        return postJsonSse(endpoint, body, headers, (eventName, data) -> {
            if (data == null || data.trim().isEmpty() || "[DONE]".equals(data.trim())) {
                return "";
            }
            JSONObject object = new JSONObject(data);
            String type = object.optString("type", eventName == null ? "" : eventName);
            if (!"content_block_delta".equals(type)) {
                return "";
            }
            JSONObject delta = object.optJSONObject("delta");
            return delta == null ? "" : delta.optString("text", "");
        }, sink);
    }

    private String callClaude(String prompt, AiLyricsSettings.Snapshot settings, String apiKey) throws Exception {
        String endpoint = trimRight(settings.baseUrl, "/") + "/messages";
        JSONObject body = claudeBody(prompt, settings);
        Map<String, String> headers = claudeHeaders(apiKey);

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

    private JSONObject claudeBody(String prompt, AiLyricsSettings.Snapshot settings) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", settings.model);
        body.put("max_tokens", settings.maxTokens);
        body.put("temperature", settings.temperature);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", prompt));
        body.put("messages", messages);
        return body;
    }

    private Map<String, String> claudeHeaders(String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", "2023-06-01");
        return headers;
    }

    private String callOpenAiCompatibleStream(
            String prompt,
            AiLyricsSettings.Snapshot settings,
            String apiKey,
            TextDeltaSink sink
    ) throws Exception {
        String endpoint = openAiEndpoint(settings);
        JSONObject body = openAiCompatibleBody(prompt, settings);
        body.put("stream", true);
        Map<String, String> headers = openAiCompatibleHeaders(settings, apiKey);
        return postJsonSse(endpoint, body, headers, (eventName, data) -> {
            if (data == null || data.trim().isEmpty() || "[DONE]".equals(data.trim())) {
                return "";
            }
            JSONObject response = new JSONObject(data);
            JSONArray choices = response.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return "";
            }
            JSONObject choice = choices.optJSONObject(0);
            JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
            if (delta != null) {
                return extractOpenAiContent(delta.opt("content"));
            }
            JSONObject message = choice == null ? null : choice.optJSONObject("message");
            return extractOpenAiContent(message == null ? null : message.opt("content"));
        }, sink);
    }

    private String callOpenAiCompatible(String prompt, AiLyricsSettings.Snapshot settings, String apiKey) throws Exception {
        String endpoint = openAiEndpoint(settings);
        JSONObject body = openAiCompatibleBody(prompt, settings);
        Map<String, String> headers = openAiCompatibleHeaders(settings, apiKey);

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

    private JSONObject openAiCompatibleBody(String prompt, AiLyricsSettings.Snapshot settings) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", settings.model);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", prompt));
        body.put("messages", messages);
        body.put(tokenField(settings.provider.id), settings.maxTokens);
        body.put("temperature", settings.temperature);
        return body;
    }

    private Map<String, String> openAiCompatibleHeaders(AiLyricsSettings.Snapshot settings, String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        if ("openrouter".equals(settings.provider.id)) {
            headers.put("HTTP-Referer", "https://github.com/ivLis-STUDIO/ivLyrics");
            headers.put("X-Title", "ivLyrics");
        }
        return headers;
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
        HttpURLConnection connection = openJsonPostConnection(endpoint, headers, false);
        try {
            writeJsonBody(connection, body);
            int code = connection.getResponseCode();
            String response = readResponse(connection, code >= 400);
            if (code < 200 || code >= 300) {
                throw new HttpStatusException(code, extractErrorMessage(response, code));
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private String postJsonSse(
            String endpoint,
            JSONObject body,
            Map<String, String> headers,
            SseDataHandler handler,
            TextDeltaSink sink
    ) throws Exception {
        HttpURLConnection connection = openJsonPostConnection(endpoint, headers, true);
        StringBuilder raw = new StringBuilder();
        try {
            writeJsonBody(connection, body);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                String response = readResponse(connection, true);
                throw new HttpStatusException(code, extractErrorMessage(response, code));
            }

            InputStream input = connection.getInputStream();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String eventName = "";
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        String delta = handleSseEvent(eventName, data.toString(), handler);
                        if (!delta.isEmpty()) {
                            raw.append(delta);
                            if (sink != null) {
                                sink.onDelta(delta);
                            }
                        }
                        eventName = "";
                        data.setLength(0);
                        continue;
                    }
                    if (line.startsWith(":")) {
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        eventName = line.substring("event:".length()).trim();
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        if (data.length() > 0) {
                            data.append('\n');
                        }
                        data.append(line.substring("data:".length()).trim());
                    }
                }
                if (data.length() > 0) {
                    String delta = handleSseEvent(eventName, data.toString(), handler);
                    if (!delta.isEmpty()) {
                        raw.append(delta);
                        if (sink != null) {
                            sink.onDelta(delta);
                        }
                    }
                }
            }
        } finally {
            connection.disconnect();
        }

        String text = raw.toString();
        if (text.trim().isEmpty()) {
            throw new IOException("Streaming returned no text");
        }
        return text;
    }

    private HttpURLConnection openJsonPostConnection(
            String endpoint,
            Map<String, String> headers,
            boolean sse
    ) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        if (sse) {
            connection.setRequestProperty("Accept", "text/event-stream");
        }
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
        }
        return connection;
    }

    private void writeJsonBody(HttpURLConnection connection, JSONObject body) throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
    }

    private String handleSseEvent(String eventName, String data, SseDataHandler handler) throws Exception {
        if (data == null || data.trim().isEmpty() || handler == null) {
            return "";
        }
        return firstNonNull(handler.onEvent(eventName == null ? "" : eventName, data));
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

    private String buildTmiPrompt(String title, String artist, String lang) {
        AiLyricsSettings.Language langInfo = AiLyricsSettings.languageInfo(lang);
        return "You are a music knowledge expert. Generate interesting facts and trivia about the song \""
                + (title == null ? "" : title.trim())
                + "\" by \""
                + (artist == null ? "" : artist.trim())
                + "\".\n\n"
                + "LANGUAGE REQUIREMENT - FOLLOW STRICTLY:\n"
                + "- Write ALL human-readable content in " + langInfo.name + " (" + langInfo.nativeName + ")\n"
                + "- This includes track.description and every string inside track.trivia\n"
                + "- Do NOT write explanatory sentences in English unless the target language itself is English\n"
                + "- Even if the song title, artist name, album, or source pages are English, your explanation sentences must still be in " + langInfo.nativeName + "\n"
                + "- The only text that may remain non-" + langInfo.nativeName + " is:\n"
                + "  1. JSON keys\n"
                + "  2. URLs\n"
                + "  3. Proper nouns, official song titles, artist names, album names, and short quoted lyric fragments\n"
                + "  4. reliability.confidence enum values: \"very_high\", \"high\", \"medium\", \"low\", \"none\"\n\n"
                + "Before returning, silently verify:\n"
                + "- track.description is fully written in " + langInfo.nativeName + "\n"
                + "- every item in track.trivia is fully written in " + langInfo.nativeName + "\n"
                + "- if any sentence is mostly English, rewrite it into natural " + langInfo.nativeName + " before returning\n\n"
                + "Return ONLY valid JSON. Do not add any text before or after the JSON.\n\n"
                + "**Output JSON Structure**:\n"
                + "{\n"
                + "  \"track\": {\n"
                + "    \"description\": \"2-3 sentence description in " + langInfo.nativeName + "\",\n"
                + "    \"trivia\": [\n"
                + "      \"Fact 1 in " + langInfo.nativeName + "\",\n"
                + "      \"Fact 2 in " + langInfo.nativeName + "\",\n"
                + "      \"Fact 3 in " + langInfo.nativeName + "\"\n"
                + "    ],\n"
                + "    \"sources\": {\n"
                + "      \"verified\": [],\n"
                + "      \"related\": [],\n"
                + "      \"other\": []\n"
                + "    },\n"
                + "    \"reliability\": {\n"
                + "      \"confidence\": \"medium\",\n"
                + "      \"has_verified_sources\": false,\n"
                + "      \"verified_source_count\": 0,\n"
                + "      \"related_source_count\": 0,\n"
                + "      \"total_source_count\": 0\n"
                + "    }\n"
                + "  }\n"
                + "}\n\n"
                + "**Rules**:\n"
                + "1. description: write 2-3 natural sentences in " + langInfo.nativeName + "\n"
                + "2. trivia: include 3-5 concise facts, each written in " + langInfo.nativeName + "\n"
                + "3. Prefer natural " + langInfo.nativeName + " wording, not mixed-language fragments\n"
                + "4. Be accurate - if you're not sure about a fact, mark confidence as \"low\"\n"
                + "5. Do NOT use markdown code blocks\n"
                + "6. Do NOT add any explanation outside the JSON";
    }

    private TmiInfo parseTmiInfo(String raw, String targetLang) throws JSONException {
        JSONObject root = parseJsonObjectResponse(raw);
        JSONObject track = root.optJSONObject("track");
        if (track == null) {
            track = root;
        }
        JSONObject sources = track.optJSONObject("sources");
        JSONObject reliability = track.optJSONObject("reliability");
        List<TmiSource> verifiedSources = parseTmiSources(sources == null ? null : sources.optJSONArray("verified"));
        List<TmiSource> relatedSources = parseTmiSources(sources == null ? null : sources.optJSONArray("related"));
        List<TmiSource> otherSources = parseTmiSources(sources == null ? null : sources.optJSONArray("other"));
        int totalSources = reliability == null
                ? verifiedSources.size() + relatedSources.size() + otherSources.size()
                : reliability.optInt("total_source_count", verifiedSources.size() + relatedSources.size() + otherSources.size());
        return new TmiInfo(
                track.optString("description", ""),
                parseStringArray(track.optJSONArray("trivia")),
                verifiedSources,
                relatedSources,
                otherSources,
                reliability == null ? "" : reliability.optString("confidence", ""),
                reliability != null && reliability.optBoolean("has_verified_sources", !verifiedSources.isEmpty()),
                reliability == null ? verifiedSources.size() : reliability.optInt("verified_source_count", verifiedSources.size()),
                reliability == null ? relatedSources.size() : reliability.optInt("related_source_count", relatedSources.size()),
                totalSources,
                targetLang
        );
    }

    private JSONObject parseJsonObjectResponse(String raw) throws JSONException {
        String cleaned = stripCodeFences(raw).trim();
        if (!cleaned.startsWith("{")) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
        }
        return new JSONObject(cleaned);
    }

    private List<String> parseStringArray(JSONArray array) {
        if (array == null) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            String value = array.optString(index, "").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private List<TmiSource> parseTmiSources(JSONArray array) {
        if (array == null) {
            return Collections.emptyList();
        }
        List<TmiSource> values = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            Object raw = array.opt(index);
            TmiSource source = null;
            if (raw instanceof JSONObject) {
                JSONObject object = (JSONObject) raw;
                source = new TmiSource(
                        object.optString("title", ""),
                        firstNonEmpty(object.optString("uri", ""), object.optString("url", ""))
                );
            } else if (raw instanceof String) {
                source = new TmiSource("", (String) raw);
            }
            if (source != null && !source.url.isEmpty()) {
                values.add(source);
            }
        }
        return values;
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
            case "cs":
                return "Use Czech spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for Czech speakers using the Latin alphabet and Czech diacritics; do not translate meanings.";
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
            case "tr":
                return "Use Turkish spelling conventions only for pronunciation guides. "
                        + "Write sounds naturally for Turkish speakers using the Latin alphabet and Turkish diacritics; do not translate meanings.";
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
            case "cs":
                return "Czech Latin spelling";
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
            case "tr":
                return "Turkish Latin spelling";
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

    private static String firstNonNull(String value) {
        return value == null ? "" : value;
    }

    private static String errorMessage(Exception error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName()
                : message.trim();
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

    private List<String> providerApiKeys(AiLyricsSettings.Snapshot settings) {
        if (settings == null) {
            return Collections.emptyList();
        }
        List<String> keys = new ArrayList<>();
        if ("pollinations".equals(settings.provider.id)) {
            String token = settings.pollinationsAccessToken == null ? "" : settings.pollinationsAccessToken.trim();
            if (!token.isEmpty()) {
                keys.add(token);
            }
        }
        for (String key : parseApiKeys(settings.apiKeys)) {
            if (!keys.contains(key)) {
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

    private TmiInfo tmiFromPrefs(String cacheKey) {
        if (tmiPrefs == null || cacheKey == null || cacheKey.trim().isEmpty()) {
            return null;
        }
        try {
            String raw = tmiPrefs.getString(sha256(cacheKey), "");
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            return TmiInfo.fromJson(new JSONObject(raw));
        } catch (Exception ignored) {
            tmiPrefs.edit().remove(sha256(cacheKey)).apply();
            return null;
        }
    }

    private void putTmiToPrefs(String cacheKey, TmiInfo info) {
        if (tmiPrefs == null
                || cacheKey == null
                || cacheKey.trim().isEmpty()
                || info == null
                || !info.hasContent()) {
            return;
        }
        try {
            tmiPrefs.edit().putString(sha256(cacheKey), info.toJson(cacheKey).toString()).apply();
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

    private void clearTmiPrefsForTrack(String trackKey) {
        if (tmiPrefs == null || trackKey == null || trackKey.trim().isEmpty()) {
            return;
        }
        String prefix = "tmi|" + trackKey.trim() + "|";
        SharedPreferences.Editor editor = null;
        for (Map.Entry<String, ?> entry : tmiPrefs.getAll().entrySet()) {
            Object rawValue = entry.getValue();
            if (!(rawValue instanceof String)) {
                continue;
            }
            try {
                JSONObject object = new JSONObject((String) rawValue);
                if (object.optString("cacheKey", "").startsWith(prefix)) {
                    if (editor == null) {
                        editor = tmiPrefs.edit();
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
            char[] encoded = new char[hashed.length * 2];
            int offset = 0;
            for (byte b : hashed) {
                int unsigned = b & 0xff;
                encoded[offset++] = HEX_DIGITS[unsigned >>> 4];
                encoded[offset++] = HEX_DIGITS[unsigned & 0x0f];
            }
            return new String(encoded);
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
        Set<String> words = new HashSet<>();
        for (String word : LATIN_WORD_SEPARATOR_PATTERN.split(lower)) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        if (scoreWords(words, "jsem", "jste", "jsme", "není", "nejsem", "jsi", "můj", "moje", "tvůj", "tvoje", "láska", "srdce", "tobě", "chci", "mám", "když") >= 2) {
            return "cs";
        }
        if (scoreWords(words, "ben", "sen", "biz", "siz", "değil", "için", "çok", "beni", "seni", "aşk", "kalp", "gece", "şimdi", "gibi") >= 2) {
            return "tr";
        }
        String hintedLanguage = detectLatinHintLanguage(lower);
        if (!hintedLanguage.isEmpty()) {
            return hintedLanguage;
        }

        String best = "en";
        int bestScore = scoreWords(words, "the", "and", "you", "that", "with", "love", "your", "for", "not", "we", "are");
        int score = scoreWords(words, "que", "de", "el", "la", "y", "en", "un", "una", "mi", "tu", "no", "por");
        if (score > bestScore) { best = "es"; bestScore = score; }
        score = scoreWords(words, "que", "de", "le", "la", "les", "et", "je", "tu", "pas", "mon", "pour", "dans");
        if (score > bestScore) { best = "fr"; bestScore = score; }
        score = scoreWords(words, "que", "de", "o", "a", "e", "eu", "voce", "você", "não", "por", "meu", "pra");
        if (score > bestScore) { best = "pt"; bestScore = score; }
        score = scoreWords(words, "che", "di", "il", "la", "e", "io", "tu", "non", "per", "mio", "nel", "sono");
        if (score > bestScore) { best = "it"; bestScore = score; }
        score = scoreWords(words, "ich", "du", "und", "der", "die", "das", "nicht", "mein", "mit", "ein", "ist");
        if (score > bestScore) { best = "de"; bestScore = score; }
        score = scoreWords(words, "och", "det", "jag", "du", "inte", "att", "min", "med", "en", "är", "för");
        if (score > bestScore) { best = "sv"; bestScore = score; }
        score = scoreWords(words, "aku", "kamu", "yang", "dan", "di", "ke", "tak", "tidak", "cinta", "ini", "itu");
        if (score > bestScore) { best = "id"; bestScore = score; }
        score = scoreWords(words, "aku", "kamu", "yang", "dan", "di", "ke", "tak", "tidak", "cinta", "ini", "itu", "kau");
        if (score > bestScore) { best = "ms"; bestScore = score; }
        return bestScore >= 2 ? best : "en";
    }

    private static int scoreWords(Set<String> textWords, String... words) {
        int score = 0;
        for (String word : words) {
            if (textWords.contains(word)) {
                score++;
            }
        }
        return score;
    }

    private static String detectLatinHintLanguage(String text) {
        int bestHint = Integer.MAX_VALUE;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            int hintIndex = LATIN_LANGUAGE_HINTS.indexOf(codePoint);
            if (hintIndex >= 0) {
                if (hintIndex < VIETNAMESE_HINTS_END) {
                    return "vi";
                } else if (hintIndex < CZECH_HINTS_END) {
                    bestHint = Math.min(bestHint, 1);
                } else if (hintIndex < TURKISH_HINTS_END) {
                    bestHint = Math.min(bestHint, 2);
                } else if (hintIndex < SWEDISH_HINTS_END) {
                    bestHint = Math.min(bestHint, 3);
                } else if (hintIndex < GERMAN_HINTS_END) {
                    bestHint = Math.min(bestHint, 4);
                } else if (hintIndex < SPANISH_HINTS_END) {
                    bestHint = Math.min(bestHint, 5);
                } else if (hintIndex < PORTUGUESE_HINTS_END) {
                    bestHint = Math.min(bestHint, 6);
                } else {
                    bestHint = Math.min(bestHint, 7);
                }
            }
            offset += Character.charCount(codePoint);
        }
        switch (bestHint) {
            case 1: return "cs";
            case 2: return "tr";
            case 3: return "sv";
            case 4: return "de";
            case 5: return "es";
            case 6: return "pt";
            case 7: return "fr";
            default: return "";
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
