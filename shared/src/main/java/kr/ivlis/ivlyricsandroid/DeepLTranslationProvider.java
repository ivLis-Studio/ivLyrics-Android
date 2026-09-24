package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Translation-only API; the native HTTP transport calls DeepL directly. */
final class DeepLTranslationProvider {
    interface Transport {
        String post(String endpoint, JSONObject body, Map<String, String> headers) throws Exception;
    }

    static String endpoint(String key) {
        return "https://" + (key.trim().endsWith(":fx") ? "api-free.deepl.com" : "api.deepl.com") + "/v2/translate";
    }

    static String targetLanguage(String language) {
        String code = language.trim().replace('_', '-').toUpperCase(Locale.ROOT);
        switch (code) {
            case "EN": return "EN-US";
            case "PT": return "PT-PT";
            case "ZH-CN": return "ZH-HANS";
            case "ZH-TW": return "ZH-HANT";
            default: return code;
        }
    }

    static List<String> translate(List<String> texts, String language, String apiKey,
            boolean preserveLyricsStructure, Transport transport) throws Exception {
        String key = apiKey.trim();
        if (key.isEmpty()) throw new IOException("DeepL API key is required");
        List<String[]> rows = new ArrayList<>();
        List<int[]> positions = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (String text : texts) {
            String[] parts = preserveLyricsStructure ? text.split(" / ", -1) : new String[]{text};
            int row = rows.size();
            rows.add(parts);
            for (int column = 0; column < parts.length; column++) {
                String part = parts[column];
                if (part.trim().isEmpty() || (preserveLyricsStructure
                        && part.matches("(?s)^\\s*(?:♪+|\\[[^\\]\\r\\n]+]|\\([^()\\r\\n]+\\))\\s*$"))) continue;
                positions.add(new int[]{row, column});
                pending.add(part);
            }
        }
        for (int start = 0; start < pending.size();) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            JSONArray batch = new JSONArray();
            int bytes = 0;
            int batchStart = start;
            while (start < pending.size() && batch.length() < 50) {
                String text = pending.get(start);
                int size = JSONObject.quote(text).getBytes(StandardCharsets.UTF_8).length + 1;
                if (size > 120_000) throw new IOException("DeepL lyric line is too large");
                if (batch.length() > 0 && bytes + size > 120_000) break;
                batch.put(text);
                bytes += size;
                start++;
            }
            JSONObject body = new JSONObject().put("text", batch)
                    .put("target_lang", targetLanguage(language)).put("preserve_formatting", true);
            String raw = transport.post(endpoint(key), body,
                    Map.of("Authorization", "DeepL-Auth-Key " + key, "Content-Type", "application/json"));
            JSONArray translations = new JSONObject(raw).optJSONArray("translations");
            if (translations == null || translations.length() != batch.length()) {
                throw new IOException("Invalid DeepL translation response");
            }
            for (int index = 0; index < translations.length(); index++) {
                JSONObject item = translations.optJSONObject(index);
                Object value = item == null ? null : item.opt("text");
                if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
                    throw new IOException("Invalid DeepL translation response");
                }
                int[] position = positions.get(batchStart + index);
                rows.get(position[0])[position[1]] = ((String) value).replaceAll("\\r\\n?|\\n", " ").trim();
            }
        }
        List<String> output = new ArrayList<>();
        for (String[] row : rows) output.add(String.join(" / ", row));
        return output;
    }
}
