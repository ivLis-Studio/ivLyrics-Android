package kr.ivlis.ivlyricsandroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FuriganaRepository {
    private static final String CACHE_VERSION = "furigana-js-kuromoji-v1";
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final long REQUEST_TIMEOUT_MS = 45_000L;
    private static final Pattern RUBY_TAG_PATTERN = Pattern.compile(
            "<ruby>([^<>]+)<rt>([^<>]*)</rt></ruby>",
            Pattern.CASE_INSENSITIVE
    );

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService cacheExecutor = Executors.newSingleThreadExecutor();
    private final Map<String, LyricsResult> memoryCache = new HashMap<>();
    private final Map<String, PendingRequest> pendingRequests = new HashMap<>();
    private final List<String> queuedScripts = new ArrayList<>();
    private final LyricsDiskCache diskCache;
    private WebView webView;
    private boolean pageLoaded;
    private long nextRequestId;
    private long activeLoadGeneration;
    private volatile long cacheGeneration;

    FuriganaRepository(Context context) {
        this.context = context;
        this.diskCache = context == null
                ? null
                : new LyricsDiskCache(context.getApplicationContext(), "furigana_lyrics", 500);
    }

    interface Callback {
        void onFuriganaLoaded(String trackKey, LyricsResult result);

        void onFuriganaError(String trackKey, String message);

        void onFuriganaLog(String trackKey, String message);
    }

    private static final class FuriganaRequest {
        final int lineIndex;
        final int partIndex;
        final String text;

        FuriganaRequest(int lineIndex, int partIndex, String text) {
            this.lineIndex = lineIndex;
            this.partIndex = partIndex;
            this.text = text == null ? "" : text;
        }
    }

    private static final class FuriganaValue {
        final int partIndex;
        final String value;

        FuriganaValue(int partIndex, String value) {
            this.partIndex = partIndex;
            this.value = value == null ? "" : value;
        }
    }

    private final class PendingRequest {
        final String requestId;
        final String trackKey;
        final String cacheKey;
        final LyricsResult baseResult;
        final List<FuriganaRequest> requests;
        final Callback callback;
        final Runnable timeoutRunnable;

        PendingRequest(
                String requestId,
                String trackKey,
                String cacheKey,
                LyricsResult baseResult,
                List<FuriganaRequest> requests,
                Callback callback
        ) {
            this.requestId = requestId;
            this.trackKey = trackKey;
            this.cacheKey = cacheKey;
            this.baseResult = baseResult;
            this.requests = requests;
            this.callback = callback;
            this.timeoutRunnable = () -> handleTimeout(requestId);
        }
    }

    void loadFurigana(
            TrackSnapshot track,
            LyricsResult baseResult,
            boolean bypassCache,
            Callback callback
    ) {
        if (callback == null || track == null || !track.hasUsableMetadata()
                || baseResult == null || baseResult.lines.isEmpty()) {
            return;
        }
        long loadGeneration = ++activeLoadGeneration;
        cancelPendingRequests();
        List<FuriganaRequest> requests = buildRequests(baseResult.lines);
        String requestPayload = requests.isEmpty() ? "" : payloadText(requests);
        if (requests.isEmpty() || !containsKanji(requestPayload)) {
            callback.onFuriganaLoaded(track.stableKey(), baseResult);
            return;
        }

        String trackKey = track.stableKey();
        String cacheKey = trackKey
                + "|source=kuromoji"
                + "|version=" + CACHE_VERSION
                + "|text=" + sha256(requestPayload);
        if (!bypassCache) {
            LyricsResult cached = memoryCache.get(cacheKey);
            if (cached != null) {
                callback.onFuriganaLog(trackKey, "furigana js cache hit");
                callback.onFuriganaLoaded(trackKey, mergeFurigana(baseResult, cached));
                return;
            }
            if (diskCache != null) {
                long expectedCacheGeneration = cacheGeneration;
                executeCacheTask(() -> {
                    LyricsResult diskCached = diskCache.get(cacheKey);
                    mainHandler.post(() -> finishDiskLookup(
                            loadGeneration,
                            expectedCacheGeneration,
                            trackKey,
                            cacheKey,
                            baseResult,
                            requests,
                            callback,
                            diskCached
                    ));
                });
                return;
            }
        }

        startFuriganaRequest(trackKey, cacheKey, baseResult, requests, callback);
    }

    private void finishDiskLookup(
            long loadGeneration,
            long expectedCacheGeneration,
            String trackKey,
            String cacheKey,
            LyricsResult baseResult,
            List<FuriganaRequest> requests,
            Callback callback,
            LyricsResult diskCached
    ) {
        if (loadGeneration != activeLoadGeneration || expectedCacheGeneration != cacheGeneration) {
            return;
        }
        if (diskCached != null) {
            memoryCache.put(cacheKey, diskCached);
            callback.onFuriganaLog(trackKey, "furigana js disk cache hit");
            callback.onFuriganaLoaded(trackKey, mergeFurigana(baseResult, diskCached));
            return;
        }
        startFuriganaRequest(trackKey, cacheKey, baseResult, requests, callback);
    }

    private void startFuriganaRequest(
            String trackKey,
            String cacheKey,
            LyricsResult baseResult,
            List<FuriganaRequest> requests,
            Callback callback
    ) {
        String requestId = "f" + (++nextRequestId);
        PendingRequest pending = new PendingRequest(
                requestId,
                trackKey,
                cacheKey,
                baseResult,
                requests,
                callback
        );
        pendingRequests.put(requestId, pending);
        mainHandler.postDelayed(pending.timeoutRunnable, REQUEST_TIMEOUT_MS);
        callback.onFuriganaLog(trackKey, "furigana js request: lines=" + requests.size());
        ensureWebView();
        evaluateWhenReady(buildRequestScript(requestId, requests));
    }

    private void cancelPendingRequests() {
        for (PendingRequest request : new ArrayList<>(pendingRequests.values())) {
            mainHandler.removeCallbacks(request.timeoutRunnable);
        }
        pendingRequests.clear();
        queuedScripts.clear();
    }

    void clearMemoryCache() {
        memoryCache.clear();
    }

    void clearTrackCache(String trackKey) {
        String key = trackKey == null ? "" : trackKey.trim();
        if (key.isEmpty()) {
            return;
        }
        cacheGeneration++;
        List<String> removeKeys = new ArrayList<>();
        for (String cacheKey : memoryCache.keySet()) {
            if (cacheKey.startsWith(key + "|")) {
                removeKeys.add(cacheKey);
            }
        }
        for (String cacheKey : removeKeys) {
            memoryCache.remove(cacheKey);
        }
        if (diskCache != null) {
            executeCacheTask(() -> diskCache.removeByKeyPrefix(key + "|"));
        }
    }

    void clearCache() {
        cacheGeneration++;
        memoryCache.clear();
        if (diskCache != null) {
            executeCacheTask(diskCache::clear);
        }
    }

    void shutdown() {
        activeLoadGeneration++;
        cacheGeneration++;
        cancelPendingRequests();
        queuedScripts.clear();
        cacheExecutor.shutdownNow();
        if (webView != null) {
            WebView target = webView;
            webView = null;
            mainHandler.post(() -> {
                try {
                    target.destroy();
                } catch (Exception ignored) {
                }
            });
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @SuppressWarnings("deprecation")
    private void ensureWebView() {
        if (webView != null || context == null) {
            return;
        }
        webView = new WebView(context);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        webView.addJavascriptInterface(new Bridge(), "AndroidFurigana");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
                flushQueuedScripts();
            }
        });
        webView.loadUrl("file:///android_asset/furigana/bridge.html");
    }

    private void evaluateWhenReady(String script) {
        if (webView == null) {
            return;
        }
        if (!pageLoaded) {
            queuedScripts.add(script);
            return;
        }
        webView.evaluateJavascript(script, null);
    }

    private void flushQueuedScripts() {
        if (webView == null || queuedScripts.isEmpty()) {
            return;
        }
        List<String> scripts = new ArrayList<>(queuedScripts);
        queuedScripts.clear();
        for (String script : scripts) {
            webView.evaluateJavascript(script, null);
        }
    }

    private String buildRequestScript(String requestId, List<FuriganaRequest> requests) {
        JSONObject payload = new JSONObject();
        JSONArray lines = new JSONArray();
        try {
            for (FuriganaRequest request : requests) {
                lines.put(request.text);
            }
            payload.put("lines", lines);
        } catch (Exception ignored) {
        }
        return "window.ivLyricsFurigana.request("
                + JSONObject.quote(requestId)
                + ","
                + JSONObject.quote(payload.toString())
                + ");";
    }

    private void handleTimeout(String requestId) {
        PendingRequest request = pendingRequests.remove(requestId);
        if (request == null) {
            return;
        }
        request.callback.onFuriganaError(request.trackKey, "후리가나 JS 처리 시간이 초과되었습니다");
    }

    private void handleResult(String requestId, String rawJson) {
        PendingRequest request = pendingRequests.remove(requestId);
        if (request == null) {
            return;
        }
        mainHandler.removeCallbacks(request.timeoutRunnable);
        try {
            JSONObject object = new JSONObject(rawJson == null ? "" : rawJson);
            if (!object.optBoolean("ok", false)) {
                String error = object.optString("error", "Kuromoji JS request failed");
                request.callback.onFuriganaError(request.trackKey, error);
                return;
            }
            JSONArray lines = object.optJSONArray("lines");
            List<String> annotations = new ArrayList<>();
            if (lines != null) {
                for (int index = 0; index < lines.length(); index++) {
                    annotations.add(lines.optString(index, ""));
                }
            }
            LyricsResult result = buildAnnotatedResult(request.baseResult, request.requests, annotations);
            memoryCache.put(request.cacheKey, result);
            if (diskCache != null) {
                long expectedCacheGeneration = cacheGeneration;
                executeCacheTask(() -> {
                    if (expectedCacheGeneration == cacheGeneration) {
                        diskCache.put(request.cacheKey, result);
                    }
                });
            }
            request.callback.onFuriganaLog(request.trackKey, "furigana js response: lines=" + annotations.size());
            request.callback.onFuriganaLoaded(request.trackKey, result);
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            request.callback.onFuriganaError(request.trackKey, message);
        }
    }

    private void handleLog(String message) {
        for (PendingRequest request : pendingRequests.values()) {
            request.callback.onFuriganaLog(request.trackKey, message);
            return;
        }
    }

    private void executeCacheTask(Runnable task) {
        if (task == null || cacheExecutor.isShutdown()) {
            return;
        }
        try {
            cacheExecutor.execute(task);
        } catch (RuntimeException ignored) {
            // The repository may be shutting down while a callback is finishing.
        }
    }

    private final class Bridge {
        @JavascriptInterface
        public void onReady() {
            mainHandler.post(FuriganaRepository.this::flushQueuedScripts);
        }

        @JavascriptInterface
        public void onResult(String requestId, String rawJson) {
            mainHandler.post(() -> handleResult(requestId, rawJson));
        }

        @JavascriptInterface
        public void onLog(String message) {
            mainHandler.post(() -> handleLog(message));
        }
    }

    private static LyricsResult buildAnnotatedResult(
            LyricsResult baseResult,
            List<FuriganaRequest> requests,
            List<String> annotations
    ) {
        Map<Integer, List<FuriganaValue>> valuesByLine = new HashMap<>();
        int count = Math.min(requests.size(), annotations == null ? 0 : annotations.size());
        for (int index = 0; index < count; index++) {
            FuriganaRequest request = requests.get(index);
            String value = sanitizeRubyText(annotations.get(index), request.text);
            if (value.isEmpty()) {
                continue;
            }
            List<FuriganaValue> lineValues = valuesByLine.get(request.lineIndex);
            if (lineValues == null) {
                lineValues = new ArrayList<>();
                valuesByLine.put(request.lineIndex, lineValues);
            }
            lineValues.add(new FuriganaValue(request.partIndex, value));
        }
        return mergeFurigana(baseResult, valuesByLine);
    }

    private static LyricsResult mergeFurigana(LyricsResult baseResult, LyricsResult furiganaResult) {
        if (furiganaResult == null) {
            return mergeFurigana(baseResult, Collections.emptyMap());
        }
        Map<Integer, List<FuriganaValue>> valuesByLine = new HashMap<>();
        int count = Math.min(baseResult.lines.size(), furiganaResult.lines.size());
        for (int index = 0; index < count; index++) {
            LyricsLine line = furiganaResult.lines.get(index);
            List<FuriganaValue> values = new ArrayList<>();
            if (line.vocalParts != null && !line.vocalParts.isEmpty()) {
                for (int partIndex = 0; partIndex < line.vocalParts.size(); partIndex++) {
                    String value = line.vocalParts.get(partIndex).furiganaText;
                    if (value != null && !value.trim().isEmpty()) {
                        values.add(new FuriganaValue(partIndex, value));
                    }
                }
            }
            if (values.isEmpty() && line.furiganaText != null && !line.furiganaText.trim().isEmpty()) {
                values.add(new FuriganaValue(-1, line.furiganaText));
            }
            if (!values.isEmpty()) {
                valuesByLine.put(index, values);
            }
        }
        return mergeFurigana(baseResult, valuesByLine);
    }

    private static LyricsResult mergeFurigana(
            LyricsResult baseResult,
            Map<Integer, List<FuriganaValue>> valuesByLine
    ) {
        if (baseResult == null) {
            return LyricsResult.empty("");
        }
        List<LyricsLine> lines = new ArrayList<>();
        Map<Integer, List<FuriganaValue>> safeValues = valuesByLine == null
                ? Collections.emptyMap()
                : valuesByLine;
        for (int index = 0; index < baseResult.lines.size(); index++) {
            LyricsLine line = baseResult.lines.get(index);
            lines.add(mergeLine(line, safeValues.get(index)));
        }
        String detail = appendDetailOnce(baseResult.detail, " JS furigana applied.");
        return new LyricsResult(
                lines,
                baseResult.providerLabel,
                detail,
                baseResult.karaoke,
                baseResult.isrc,
                baseResult.spotifyTrackId,
                baseResult.contributors
        );
    }

    private static LyricsLine mergeLine(LyricsLine line, List<FuriganaValue> values) {
        if (line == null) {
            return null;
        }
        List<LyricsLine.VocalPart> clearedParts = clearPartFurigana(line.vocalParts);
        if (values == null || values.isEmpty()) {
            return new LyricsLine(
                    line.startTimeMs,
                    line.endTimeMs,
                    line.text,
                    line.syllables,
                    line.speaker,
                    line.speakerColor,
                    line.speakerFallback,
                    line.kind,
                    clearedParts,
                    line.pronunciationText,
                    line.translationText,
                    ""
            );
        }
        if (line.vocalParts == null || line.vocalParts.isEmpty()) {
            String furigana = firstLineLevelValue(values);
            return line.withSupplements(line.pronunciationText, line.translationText, furigana);
        }

        List<LyricsLine.VocalPart> parts = new ArrayList<>(clearedParts);
        boolean changedPart = false;
        for (FuriganaValue value : values) {
            if (value.partIndex < 0 || value.partIndex >= parts.size()) {
                continue;
            }
            LyricsLine.VocalPart part = parts.get(value.partIndex);
            parts.set(value.partIndex, part.withSupplements(
                    part.pronunciationText,
                    part.translationText,
                    value.value
            ));
            changedPart = true;
        }

        String lineFurigana;
        if (changedPart) {
            lineFurigana = joinPartFurigana(parts);
        } else {
            lineFurigana = firstLineLevelValue(values);
            applyLineLevelFuriganaToMatchingPart(parts, lineFurigana);
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
                line.pronunciationText,
                line.translationText,
                lineFurigana
        );
    }

    private static List<LyricsLine.VocalPart> clearPartFurigana(List<LyricsLine.VocalPart> sourceParts) {
        List<LyricsLine.VocalPart> parts = new ArrayList<>();
        if (sourceParts == null) {
            return parts;
        }
        for (LyricsLine.VocalPart part : sourceParts) {
            if (part == null) {
                continue;
            }
            parts.add(part.withSupplements(part.pronunciationText, part.translationText, ""));
        }
        return parts;
    }

    private static void applyLineLevelFuriganaToMatchingPart(List<LyricsLine.VocalPart> parts, String furigana) {
        if (parts == null || parts.isEmpty() || furigana == null || furigana.trim().isEmpty()) {
            return;
        }
        String plain = stripRubyMarkup(furigana);
        int fallbackIndex = parts.size() == 1 ? 0 : -1;
        for (int index = 0; index < parts.size(); index++) {
            LyricsLine.VocalPart part = parts.get(index);
            String text = displayPartText(part);
            if (!text.isEmpty() && text.equals(plain)) {
                fallbackIndex = index;
                break;
            }
        }
        if (fallbackIndex < 0 || fallbackIndex >= parts.size()) {
            return;
        }
        LyricsLine.VocalPart part = parts.get(fallbackIndex);
        parts.set(fallbackIndex, part.withSupplements(
                part.pronunciationText,
                part.translationText,
                furigana.trim()
        ));
    }

    private static String firstLineLevelValue(List<FuriganaValue> values) {
        if (values == null) {
            return "";
        }
        for (FuriganaValue value : values) {
            if (value.partIndex < 0 && value.value != null && !value.value.trim().isEmpty()) {
                return value.value.trim();
            }
        }
        for (FuriganaValue value : values) {
            if (value.value != null && !value.value.trim().isEmpty()) {
                return value.value.trim();
            }
        }
        return "";
    }

    private static String joinPartFurigana(List<LyricsLine.VocalPart> parts) {
        StringBuilder builder = new StringBuilder();
        if (parts == null) {
            return "";
        }
        for (LyricsLine.VocalPart part : parts) {
            if (part == null || part.furiganaText == null || part.furiganaText.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" / ");
            }
            builder.append(part.furiganaText.trim());
        }
        return builder.toString();
    }

    private static String appendDetailOnce(String detail, String suffix) {
        String base = detail == null ? "" : detail;
        if (suffix == null || suffix.isEmpty() || base.contains(suffix.trim())) {
            return base;
        }
        return base + suffix;
    }

    private static List<FuriganaRequest> buildRequests(List<LyricsLine> lines) {
        List<FuriganaRequest> requests = new ArrayList<>();
        if (lines == null) {
            return requests;
        }
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            LyricsLine line = lines.get(lineIndex);
            List<FuriganaRequest> vocalRequests = displayedVocalPartRequests(line, lineIndex);
            if (vocalRequests.size() > 1) {
                requests.addAll(vocalRequests);
            } else {
                requests.add(new FuriganaRequest(lineIndex, -1, displayLineText(line)));
            }
        }
        return requests;
    }

    private static List<FuriganaRequest> displayedVocalPartRequests(LyricsLine line, int lineIndex) {
        List<FuriganaRequest> requests = new ArrayList<>();
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
                requests.add(new FuriganaRequest(lineIndex, index, text));
            }
        }
        for (int index = 0; index < line.vocalParts.size(); index++) {
            LyricsLine.VocalPart part = line.vocalParts.get(index);
            if ("lead".equals(part.role)) {
                continue;
            }
            String text = displayPartText(part);
            if (!text.isEmpty()) {
                requests.add(new FuriganaRequest(lineIndex, index, text));
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
                String text = displayPartText(part);
                if (text.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(" / ");
                }
                builder.append(text);
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

    private static String payloadText(List<FuriganaRequest> requests) {
        StringBuilder builder = new StringBuilder();
        if (requests == null) {
            return "";
        }
        for (FuriganaRequest request : requests) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(request.text);
        }
        return builder.toString();
    }

    private static String sanitizeRubyText(String value, String original) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty() || !cleaned.contains("<ruby>")) {
            return "";
        }
        String originalText = original == null ? "" : original.trim();
        if (!stripRubyMarkup(cleaned).equals(originalText)) {
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

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            char[] encoded = new char[bytes.length * 2];
            int offset = 0;
            for (byte item : bytes) {
                int unsigned = item & 0xff;
                encoded[offset++] = HEX_DIGITS[unsigned >>> 4];
                encoded[offset++] = HEX_DIGITS[unsigned & 0x0f];
            }
            return new String(encoded);
        } catch (Exception ignored) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }
}
