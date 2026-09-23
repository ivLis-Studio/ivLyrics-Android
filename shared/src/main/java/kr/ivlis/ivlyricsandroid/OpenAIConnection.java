package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class OpenAIConnection {
    final String id, name, baseUrl, apiKeys, model;
    final boolean enabled;

    OpenAIConnection(String id, String name, String baseUrl, String apiKeys, String model, boolean enabled) {
        this.id = clean(id).isEmpty() ? UUID.randomUUID().toString() : clean(id);
        this.name = clean(name);
        this.baseUrl = clean(baseUrl).isEmpty() ? "https://api.openai.com/v1" : clean(baseUrl);
        this.apiKeys = clean(apiKeys);
        this.model = clean(model);
        this.enabled = enabled;
    }

    boolean isReady() { return enabled && !apiKeys.isEmpty() && !model.isEmpty(); }

    static List<OpenAIConnection> parse(JSONArray rows) {
        List<OpenAIConnection> result = new ArrayList<>();
        if (rows != null) for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null) result.add(new OpenAIConnection(row.optString("id"), row.optString("name"),
                    row.optString("baseUrl"), row.optString("apiKeys"), row.optString("model"), row.optBoolean("enabled", true)));
        }
        return Collections.unmodifiableList(result);
    }

    static JSONArray toJson(List<OpenAIConnection> connections) {
        JSONArray rows = new JSONArray();
        for (OpenAIConnection connection : connections) {
            JSONObject row = new JSONObject();
            try {
                row.put("id", connection.id); row.put("name", connection.name);
                row.put("baseUrl", connection.baseUrl); row.put("apiKeys", connection.apiKeys);
                row.put("model", connection.model); row.put("enabled", connection.enabled);
                rows.put(row);
            } catch (org.json.JSONException error) { throw new IllegalStateException(error); }
        }
        return rows;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
