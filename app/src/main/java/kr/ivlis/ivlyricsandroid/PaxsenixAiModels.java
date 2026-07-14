package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class PaxsenixAiModels {
    private static final String MODELS_URL = "https://api.paxsenix.org/v1/models";
    private static final String CHAT_ENDPOINT = "/v1/chat/completions";
    private static final int TIMEOUT_MS = 12_000;

    private PaxsenixAiModels() {
    }

    static List<Model> fetch(String apiKey) throws Exception {
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) throw new IOException("API key is required");

        HttpURLConnection connection = (HttpURLConnection) new URL(MODELS_URL).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + key);
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 400
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = stream == null ? "" : readUtf8(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("Model request failed (" + status + ")");
            }
            return parse(body);
        } finally {
            connection.disconnect();
        }
    }

    static void requireSelectedModel(String model) throws IOException {
        if (model == null || model.trim().isEmpty()) {
            throw new IOException("AI model must be selected");
        }
    }

    static List<Model> parse(String body) {
        if (body == null || body.trim().isEmpty()) return Collections.emptyList();
        try {
            JSONArray data = new JSONObject(body).optJSONArray("data");
            if (data == null) return Collections.emptyList();
            List<Model> result = new ArrayList<>();
            for (int index = 0; index < data.length(); index++) {
                JSONObject item = data.optJSONObject(index);
                if (!isChatTextModel(item)) continue;
                String id = item.optString("id", "").trim();
                String name = item.optString("name", "").trim();
                result.add(new Model(id, name.isEmpty() ? id : name));
            }
            result.sort(Comparator.comparing(model -> model.id.toLowerCase(Locale.ROOT)));
            return Collections.unmodifiableList(result);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private static boolean isChatTextModel(JSONObject item) {
        if (item == null || item.optString("id", "").trim().isEmpty()) return false;
        String type = item.optString("type", "").trim();
        if (!type.isEmpty() && !"chat.completions".equalsIgnoreCase(type)) return false;
        String endpoint = item.optString("endpoint", "").trim();
        if (!endpoint.isEmpty() && !CHAT_ENDPOINT.equals(endpoint)) return false;
        String status = item.optString("status", "").trim();
        if (!status.isEmpty() && !"available".equalsIgnoreCase(status)) return false;

        JSONObject modalities = item.optJSONObject("modalities");
        return modalities == null
                || (containsOrAbsent(modalities.optJSONArray("input"), "text")
                && containsOrAbsent(modalities.optJSONArray("output"), "text"));
    }

    private static boolean containsOrAbsent(JSONArray values, String expected) {
        if (values == null) return true;
        for (int index = 0; index < values.length(); index++) {
            if (expected.equalsIgnoreCase(values.optString(index, ""))) return true;
        }
        return false;
    }

    private static String readUtf8(InputStream stream) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    static final class Model {
        final String id;
        final String name;

        Model(String id, String name) {
            this.id = id;
            this.name = name;
        }

        String displayLabel() {
            return name.equals(id) ? id : name + "\n" + id;
        }
    }
}
