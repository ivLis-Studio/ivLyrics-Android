package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Model discovery for the same base URL and credentials used for generation. */
final class AiProviderModels {
    static final class Model {
        final String id;
        final String name;

        Model(String id, String name) { this.id = id; this.name = name; }
        String displayLabel() { return name.equals(id) ? id : name + "\n" + id; }
    }

    static final class Catalog {
        final List<Model> models;
        final boolean builtIn;

        Catalog(List<Model> models, boolean builtIn) {
            this.models = Collections.unmodifiableList(models);
            this.builtIn = builtIn;
        }
    }

    static Catalog fetch(String provider, String baseUrl, String apiKey) throws Exception {
        // Sonar's chat API has no model catalog. /v1/models lists Agent API models,
        // which cannot be sent to Sonar's /chat/completions endpoint.
        if (usesSonarCatalog(provider, baseUrl)) return sonarCatalog();
        if (apiKey.isEmpty() && !"pollinations".equals(provider) && !"openrouter".equals(provider)) {
            throw new IOException("API key is required");
        }
        Map<String, Model> models = new LinkedHashMap<>();
        Set<String> cursors = new HashSet<>();
        String cursor = "";
        for (int page = 0; page < 100; page++) {
            JSONObject root = request(provider, endpoint(provider, baseUrl, cursor), apiKey);
            for (Model model : parse(provider, root)) models.putIfAbsent(model.id, model);
            cursor = nextCursor(provider, root);
            if (cursor.isEmpty()) {
                List<Model> result = new ArrayList<>(models.values());
                result.sort(Comparator.comparing(model -> model.id.toLowerCase(Locale.ROOT)));
                return new Catalog(result, false);
            }
            if (!cursors.add(cursor)) throw new IOException("Repeated model page cursor");
        }
        throw new IOException("Too many model pages");
    }

    static boolean usesSonarCatalog(String provider, String baseUrl) {
        if (!"perplexity".equals(provider)) return false;
        try {
            URI uri = URI.create(baseUrl);
            return "api.perplexity.ai".equalsIgnoreCase(uri.getHost())
                    && (uri.getPath().isEmpty() || "/".equals(uri.getPath()));
        } catch (Exception ignored) { return false; }
    }

    private static Catalog sonarCatalog() {
        List<Model> result = new ArrayList<>();
        for (String id : new String[]{"sonar", "sonar-pro", "sonar-reasoning-pro", "sonar-deep-research"}) {
            result.add(new Model(id, id));
        }
        return new Catalog(result, true);
    }

    static String endpoint(String provider, String baseUrl, String cursor) throws IOException {
        String base = baseUrl.trim().replaceAll("/+$", "");
        if ("pollinations".equals(provider) && !base.endsWith("/v1")) base += "/v1";
        String url = base + "/models";
        if ("gemini".equals(provider)) url += "?pageSize=1000";
        if ("claude".equals(provider)) url += "?limit=1000";
        if (!cursor.isEmpty()) {
            url += "&" + ("gemini".equals(provider) ? "pageToken=" : "after_id=")
                    + URLEncoder.encode(cursor, StandardCharsets.UTF_8.name());
        }
        return url;
    }

    static Map<String, String> headers(String provider, String apiKey) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        if ("claude".equals(provider)) {
            headers.put("x-api-key", apiKey);
            headers.put("anthropic-version", "2023-06-01");
        } else if ("gemini".equals(provider)) {
            headers.put("x-goog-api-key", apiKey);
        } else if (!apiKey.isEmpty()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    private static JSONObject request(String provider, String endpoint, String apiKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(12_000);
        for (Map.Entry<String, String> header : headers(provider, apiKey).entrySet()) {
            connection.setRequestProperty(header.getKey(), header.getValue());
        }
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Model request failed (" + status + ")");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {
            connection.disconnect();
        }
    }

    static List<Model> parse(String provider, JSONObject root) throws IOException {
        JSONArray rows = root.optJSONArray("gemini".equals(provider) ? "models" : "data");
        if (rows == null) throw new IOException("Invalid model list response");
        List<Model> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            boolean gemini = "gemini".equals(provider);
            String id = row.optString(gemini ? "name" : "id", "").trim();
            if (gemini && id.startsWith("models/")) id = id.substring(7);
            if (id.isEmpty()) continue;
            if (gemini && !contains(row.optJSONArray("supportedGenerationMethods"), "generateContent", false)) continue;
            if (!isTextChatModel(row, id) || !seen.add(id)) continue;
            String name = row.optString(gemini ? "displayName" : "display_name", "").trim();
            if (name.isEmpty()) name = row.optString("name", "").trim();
            if (name.isEmpty()) name = row.optString("title", "").trim();
            result.add(new Model(id, name.isEmpty() ? id : name));
        }
        return result;
    }

    static String nextCursor(String provider, JSONObject root) throws IOException {
        if ("gemini".equals(provider)) return root.optString("nextPageToken", "").trim();
        if ("claude".equals(provider) && root.optBoolean("has_more", false)) {
            String cursor = root.optString("last_id", "").trim();
            if (cursor.isEmpty()) throw new IOException("Missing model page cursor");
            return cursor;
        }
        return "";
    }

    private static boolean isTextChatModel(JSONObject row, String id) {
        String type = row.optString("type", "");
        if (!type.isEmpty() && !"model".equals(type) && !"chat.completions".equalsIgnoreCase(type)) return false;
        String endpoint = row.optString("endpoint", "");
        if (!endpoint.isEmpty() && !endpoint.endsWith("/chat/completions")) return false;
        String status = row.optString("status", "");
        if (!status.isEmpty() && !"available".equalsIgnoreCase(status) && !"active".equalsIgnoreCase(status)) return false;
        if (!row.optBoolean("active", true)) return false;
        String category = row.optString("category", "");
        if (!category.isEmpty() && !"text".equals(category)) return false;
        if (!contains(row.optJSONArray("supported_endpoints"), "/v1/chat/completions", true)) return false;
        JSONObject architecture = row.optJSONObject("architecture");
        JSONObject modalities = row.optJSONObject("modalities");
        JSONArray input = row.optJSONArray("input_modalities");
        JSONArray output = row.optJSONArray("output_modalities");
        if (architecture != null) {
            input = architecture.optJSONArray("input_modalities");
            output = architecture.optJSONArray("output_modalities");
        } else if (modalities != null) {
            input = modalities.optJSONArray("input");
            output = modalities.optJSONArray("output");
        }
        if (!contains(input, "text", true) || !contains(output, "text", true)) return false;
        // Older OpenAI-compatible catalogs do not expose capabilities.
        return output != null || !id.toLowerCase(Locale.ROOT).matches(
                ".*(embedding|whisper|tts|dall-e|realtime|moderation|transcribe|image|video|audio|music|midijourney).*");
    }

    private static boolean contains(JSONArray values, String expected, boolean ifAbsent) {
        if (values == null) return ifAbsent;
        for (int i = 0; i < values.length(); i++) {
            if (expected.equalsIgnoreCase(values.optString(i, ""))) return true;
        }
        return false;
    }
}
