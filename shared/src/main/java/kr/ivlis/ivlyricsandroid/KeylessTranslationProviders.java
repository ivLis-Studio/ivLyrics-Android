package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Translation-only, keyless web providers used before the selected AI provider. */
final class KeylessTranslationProviders {
    static final String BING_ID = "bing-translate";
    static final String GOOGLE_ID = "google-translate";
    static final String BING_LABEL = "Bing Translate";
    static final String GOOGLE_LABEL = "Google Translate";

    private static final int REQUEST_TIMEOUT_MS = 15_000;
    private static final int MAX_CHUNK_LINES = 40;
    private static final Pattern PROTECTED_LINE_PATTERN = Pattern.compile(
            "^\\s*(?:♪+|\\[[^\\]\\r\\n]+]|\\([^()\\r\\n]+\\))\\s*$"
    );
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36 Edg/151.0.4129.59";

    // Salt-rotated 16-bit packing keeps service routes out of plain-text source.
    private static final String GOOGLE_ENDPOINT = unpack(0x47, 51, new int[]{
            0x728b, 0x2045, 0xed59, 0xef8e, 0x6685, 0xcd63, 0x9527,
            0x49fd, 0x2fc1, 0xe34a, 0xa1f4, 0x5cb4, 0x0377, 0xd52e,
            0xd8f8, 0x57b4, 0xd52b, 0xc674, 0x10b0, 0x4ce0, 0x8632,
            0xd38c, 0x69d8, 0x6107, 0x8d63, 0xc100
    });
    private static final String BING_ORIGIN = unpack(0x6d, 20, new int[]{
            0x58a1, 0x0a6f, 0xc773, 0xc5a4, 0x4faa,
            0xf109, 0xae08, 0x6cc4, 0x4ea6, 0xc162
    });
    private static final String BING_TRANSLATOR_PATH = unpack(0x31, 11, new int[]{
            0x43fd, 0x5022, 0x8666, 0xdab6, 0x10ee, 0xa800
    });
    private static final String BING_TRANSLATE_PATH = unpack(0x52, 27, new int[]{
            0x209e, 0x3552, 0xea18, 0xa6d8, 0x6696, 0xdc6e, 0xc061,
            0x54ef, 0x099f, 0xe344, 0xb2e5, 0x44a8, 0x4a23, 0x8f00
    });

    private static final Object BING_CONFIG_LOCK = new Object();
    private static BingConfig bingConfig;

    private KeylessTranslationProviders() {
    }

    static Result translate(
            List<String> texts,
            String targetLanguage,
            boolean bingEnabled,
            boolean googleEnabled
    ) throws Exception {
        Exception lastError = null;
        if (bingEnabled) {
            try {
                return new Result(
                        translateBatched(texts, targetLanguage, 2_800, KeylessTranslationProviders::translateBingText),
                        BING_ID,
                        BING_LABEL
                );
            } catch (Exception error) {
                lastError = error;
            }
        }
        if (googleEnabled) {
            try {
                return new Result(
                        translateBatched(texts, targetLanguage, 3_500, KeylessTranslationProviders::translateGoogleText),
                        GOOGLE_ID,
                        GOOGLE_LABEL
                );
            } catch (Exception error) {
                lastError = error;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("활성화된 키리스 번역 제공자가 없습니다");
    }

    static Result translateWithProvider(
            String providerId,
            List<String> texts,
            String targetLanguage
    ) throws Exception {
        if (BING_ID.equals(providerId)) {
            return new Result(
                    translateBatched(texts, targetLanguage, 2_800, KeylessTranslationProviders::translateBingText),
                    BING_ID,
                    BING_LABEL
            );
        }
        if (GOOGLE_ID.equals(providerId)) {
            return new Result(
                    translateBatched(texts, targetLanguage, 3_500, KeylessTranslationProviders::translateGoogleText),
                    GOOGLE_ID,
                    GOOGLE_LABEL
            );
        }
        throw new IOException("Unknown keyless translation provider: " + providerId);
    }

    static Result translateMetadataWithProvider(
            String providerId,
            List<String> texts,
            String targetLanguage
    ) throws Exception {
        if (BING_ID.equals(providerId)) {
            return new Result(
                    translateBatched(texts, targetLanguage, 2_800, KeylessTranslationProviders::translateBingText, false),
                    BING_ID,
                    BING_LABEL
            );
        }
        if (GOOGLE_ID.equals(providerId)) {
            return new Result(
                    translateBatched(texts, targetLanguage, 3_500, KeylessTranslationProviders::translateGoogleText, false),
                    GOOGLE_ID,
                    GOOGLE_LABEL
            );
        }
        throw new IOException("Unknown keyless translation provider: " + providerId);
    }

    private static List<String> translateBatched(
            List<String> source,
            String targetLanguage,
            int maxCharacters,
            TextTranslator translator
    ) throws Exception {
        return translateBatched(source, targetLanguage, maxCharacters, translator, true);
    }

    private static List<String> translateBatched(
            List<String> source,
            String targetLanguage,
            int maxCharacters,
            TextTranslator translator,
            boolean preserveLyricsStructure
    ) throws Exception {
        List<String> safeSource = source == null ? Collections.emptyList() : source;
        List<String> output = new ArrayList<>(Collections.nCopies(safeSource.size(), ""));
        int start = 0;
        while (start < safeSource.size()) {
            int end = start;
            int characterCount = 0;
            while (end < safeSource.size() && end - start < MAX_CHUNK_LINES) {
                int nextLength = safeSource.get(end).length() + (end > start ? 1 : 0);
                if (end > start && characterCount + nextLength > maxCharacters) {
                    break;
                }
                characterCount += nextLength;
                end++;
            }
            List<String> chunk = safeSource.subList(start, Math.max(start + 1, end));
            List<String> translated = translateAligned(chunk, targetLanguage, translator, preserveLyricsStructure);
            for (int index = 0; index < translated.size(); index++) {
                output.set(start + index, translated.get(index));
            }
            start += chunk.size();
        }
        return output;
    }

    private static List<String> translateAligned(
            List<String> lines,
            String targetLanguage,
            TextTranslator translator,
            boolean preserveLyricsStructure
    ) throws Exception {
        if (lines.isEmpty()) {
            return Collections.emptyList();
        }
        boolean allBlank = true;
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                allBlank = false;
                break;
            }
        }
        if (allBlank) {
            return new ArrayList<>(lines);
        }

        String translatedText = translator.translate(joinLines(lines), targetLanguage).replace("\r\n", "\n").replace('\r', '\n');
        String[] split = translatedText.split("\n", -1);
        int count = split.length;
        if (count == lines.size() + 1 && split[count - 1].isEmpty()) {
            count--;
        }
        if (count == lines.size()) {
            List<String> output = new ArrayList<>(lines.size());
            for (int index = 0; index < lines.size(); index++) {
                String source = lines.get(index);
                String translated = source.trim().isEmpty()
                        || (preserveLyricsStructure && PROTECTED_LINE_PATTERN.matcher(source).matches())
                        ? source
                        : split[index];
                output.add(preserveLyricsStructure
                        ? repairVocalParts(source, translated, targetLanguage, translator)
                        : translated);
            }
            return output;
        }
        if (lines.size() == 1) {
            return Collections.singletonList(translatedText);
        }

        int middle = (lines.size() + 1) / 2;
        List<String> output = new ArrayList<>(lines.size());
        output.addAll(translateAligned(lines.subList(0, middle), targetLanguage, translator, preserveLyricsStructure));
        output.addAll(translateAligned(lines.subList(middle, lines.size()), targetLanguage, translator, preserveLyricsStructure));
        return output;
    }

    private static String repairVocalParts(
            String source,
            String translated,
            String targetLanguage,
            TextTranslator translator
    ) throws Exception {
        String[] sourceParts = source.split(" / ", -1);
        if (sourceParts.length <= 1 || translated.split(" / ", -1).length == sourceParts.length) {
            return translated;
        }
        List<String> translatedParts = new ArrayList<>(sourceParts.length);
        for (String part : sourceParts) {
            translatedParts.add(translator.translate(part, targetLanguage));
        }
        return String.join(" / ", translatedParts);
    }

    private static String translateGoogleText(String text, String targetLanguage) throws Exception {
        String body = form(Map.of(
                "client", "gtx",
                "sl", "auto",
                "tl", normalizeGoogleLanguage(targetLanguage),
                "dt", "t",
                "q", text
        ));
        HttpResponse response = request(
                GOOGLE_ENDPOINT,
                "POST",
                Map.of("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8"),
                body
        );
        if (response.status < 200 || response.status >= 300) {
            throw new HttpStatusException(response.status, "Google Translate request failed");
        }
        JSONArray root = new JSONArray(response.body);
        JSONArray segments = root.optJSONArray(0);
        if (segments == null) {
            throw new IOException("Google Translate 응답 형식이 올바르지 않습니다");
        }
        StringBuilder translated = new StringBuilder();
        for (int index = 0; index < segments.length(); index++) {
            JSONArray segment = segments.optJSONArray(index);
            if (segment != null) {
                translated.append(segment.optString(0, ""));
            }
        }
        return translated.toString();
    }

    private static String translateBingText(String text, String targetLanguage) throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            BingConfig config = ensureBingConfig(attempt > 0);
            try {
                return postBingTranslation(config, text, normalizeBingLanguage(targetLanguage));
            } catch (HttpStatusException error) {
                lastError = error;
                if (error.status != 401 && error.status != 403) {
                    throw error;
                }
            }
        }
        throw lastError == null ? new IOException("Bing Translate request failed") : lastError;
    }

    private static BingConfig ensureBingConfig(boolean forceRefresh) throws Exception {
        synchronized (BING_CONFIG_LOCK) {
            if (forceRefresh) {
                bingConfig = null;
            }
            if (bingConfig == null || bingConfig.expired()) {
                bingConfig = fetchBingConfig();
            }
            return bingConfig;
        }
    }

    private static BingConfig fetchBingConfig() throws Exception {
        HttpResponse response = request(
                BING_ORIGIN + BING_TRANSLATOR_PATH,
                "GET",
                Map.of(
                        "Accept", "text/html,application/xhtml+xml",
                        "User-Agent", USER_AGENT
                ),
                null
        );
        if (response.status < 200 || response.status >= 300) {
            throw new HttpStatusException(response.status, "Bing translator configuration failed");
        }

        String ig = firstGroup(response.body, "IG:\\s*\\\"([^\\\"]+)\\\"");
        String iid = firstGroup(response.body, "data-iid=\\\"([^\\\"]+)\\\"");
        String tupleText = firstGroup(response.body, "params_AbusePreventionHelper\\s*=\\s*(\\[[^]]+])");
        if (ig.isEmpty() || iid.isEmpty() || tupleText.isEmpty()) {
            throw new IOException("Bing translator 설정을 읽을 수 없습니다");
        }
        JSONArray tuple = new JSONArray(tupleText);
        long key = tuple.optLong(0, -1L);
        String token = tuple.optString(1, "");
        long expiryInterval = tuple.optLong(2, 600_000L);
        if (key < 0 || token.isEmpty()) {
            throw new IOException("Bing translator 토큰이 올바르지 않습니다");
        }

        String finalOrigin = BING_ORIGIN;
        URL finalUrl = response.finalUrl;
        URL canonical = new URL(BING_ORIGIN);
        if (finalUrl != null
                && "https".equalsIgnoreCase(finalUrl.getProtocol())
                && (finalUrl.getHost().equalsIgnoreCase(canonical.getHost())
                || finalUrl.getHost().toLowerCase(Locale.ROOT).endsWith("." + canonical.getHost().toLowerCase(Locale.ROOT)))) {
            finalOrigin = finalUrl.getProtocol() + "://" + finalUrl.getAuthority();
        }
        return new BingConfig(
                ig,
                iid,
                key,
                token,
                Math.max(60_000L, expiryInterval),
                System.currentTimeMillis(),
                response.cookieHeader,
                finalOrigin
        );
    }

    private static String postBingTranslation(BingConfig config, String text, String targetLanguage) throws Exception {
        int sequence;
        synchronized (config) {
            sequence = ++config.count;
        }
        String endpoint = config.origin + BING_TRANSLATE_PATH
                + "&IG=" + encode(config.ig)
                + "&IID=" + encode(config.iid)
                + "&SFX=" + sequence
                + "&ref=TThis&edgepdftranslator=1";
        String body = form(Map.of(
                "fromLang", "auto-detect",
                "text", text,
                "token", config.token,
                "key", String.valueOf(config.key),
                "to", targetLanguage,
                "tryFetchingGenderDebiasedTranslations", "false"
        ));
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        headers.put("Referer", config.origin + BING_TRANSLATOR_PATH);
        headers.put("Origin", config.origin);
        headers.put("User-Agent", USER_AGENT);
        if (!config.cookieHeader.isEmpty()) {
            headers.put("Cookie", config.cookieHeader);
        }
        HttpResponse response = request(endpoint, "POST", headers, body);
        if (response.status < 200 || response.status >= 300) {
            throw new HttpStatusException(response.status, "Bing Translate request failed");
        }
        JSONArray root = new JSONArray(response.body);
        JSONObject first = root.optJSONObject(0);
        JSONArray translations = first == null ? null : first.optJSONArray("translations");
        JSONObject translation = translations == null ? null : translations.optJSONObject(0);
        String value = translation == null ? "" : translation.optString("text", "");
        if (value.isEmpty() && !text.isEmpty()) {
            throw new IOException("Bing Translate 응답 형식이 올바르지 않습니다");
        }
        return value;
    }

    private static HttpResponse request(
            String endpoint,
            String method,
            Map<String, String> headers,
            String body
    ) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
        connection.setReadTimeout(REQUEST_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod(method);
        connection.setUseCaches(false);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = read(stream);
        String cookies = cookiesFrom(connection.getHeaderFields());
        URL finalUrl = connection.getURL();
        connection.disconnect();
        return new HttpResponse(status, responseBody, cookies, finalUrl);
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder body = new StringBuilder();
            char[] buffer = new char[8_192];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                body.append(buffer, 0, read);
            }
            return body.toString();
        }
    }

    private static String cookiesFrom(Map<String, List<String>> headers) {
        List<String> cookies = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null || !"set-cookie".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (value == null || value.isEmpty()) continue;
                int semicolon = value.indexOf(';');
                cookies.add(semicolon < 0 ? value.trim() : value.substring(0, semicolon).trim());
            }
        }
        return String.join("; ", cookies);
    }

    private static String firstGroup(String source, String expression) {
        Matcher matcher = Pattern.compile(expression).matcher(source == null ? "" : source);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String form(Map<String, String> values) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return body.toString();
    }

    static String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 encoding is unavailable", impossible);
        }
    }

    private static String joinLines(List<String> lines) {
        return String.join("\n", lines);
    }

    private static String normalizeGoogleLanguage(String language) {
        String normalized = language == null ? "en" : language.trim().replace('_', '-');
        return normalized.isEmpty() ? "en" : normalized;
    }

    private static String normalizeBingLanguage(String language) {
        String normalized = normalizeGoogleLanguage(language);
        if ("zh-CN".equalsIgnoreCase(normalized) || "zh-Hans".equalsIgnoreCase(normalized)) return "zh-Hans";
        if ("zh-TW".equalsIgnoreCase(normalized) || "zh-Hant".equalsIgnoreCase(normalized)) return "zh-Hant";
        if ("pt-PT".equalsIgnoreCase(normalized)) return "pt-PT";
        int dash = normalized.indexOf('-');
        return dash > 0 ? normalized.substring(0, dash).toLowerCase(Locale.ROOT) : normalized.toLowerCase(Locale.ROOT);
    }

    private static String unpack(int seed, int length, int[] words) {
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            int packed = words[index >> 1];
            int masked = (packed >>> (index % 2 == 0 ? 8 : 0)) & 0xff;
            int lane = (seed ^ ((index + 1) * 0x5d) ^ (index << 1)) & 0xff;
            value.append((char) (masked ^ lane));
        }
        return value.toString();
    }

    interface TextTranslator {
        String translate(String text, String targetLanguage) throws Exception;
    }

    static final class Result {
        final List<String> values;
        final String providerId;
        final String providerLabel;

        Result(List<String> values, String providerId, String providerLabel) {
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
            this.providerId = providerId;
            this.providerLabel = providerLabel;
        }
    }

    private static final class BingConfig {
        final String ig;
        final String iid;
        final long key;
        final String token;
        final long expiryInterval;
        final long fetchedAt;
        final String cookieHeader;
        final String origin;
        int count;

        BingConfig(
                String ig,
                String iid,
                long key,
                String token,
                long expiryInterval,
                long fetchedAt,
                String cookieHeader,
                String origin
        ) {
            this.ig = ig;
            this.iid = iid;
            this.key = key;
            this.token = token;
            this.expiryInterval = expiryInterval;
            this.fetchedAt = fetchedAt;
            this.cookieHeader = cookieHeader == null ? "" : cookieHeader;
            this.origin = origin;
        }

        boolean expired() {
            return System.currentTimeMillis() - fetchedAt >= Math.max(1_000L, expiryInterval - 30_000L);
        }
    }

    private static final class HttpResponse {
        final int status;
        final String body;
        final String cookieHeader;
        final URL finalUrl;

        HttpResponse(int status, String body, String cookieHeader, URL finalUrl) {
            this.status = status;
            this.body = body == null ? "" : body;
            this.cookieHeader = cookieHeader == null ? "" : cookieHeader;
            this.finalUrl = finalUrl;
        }
    }

    private static final class HttpStatusException extends IOException {
        final int status;

        HttpStatusException(int status, String message) {
            super(message + " (" + status + ")");
            this.status = status;
        }
    }
}
