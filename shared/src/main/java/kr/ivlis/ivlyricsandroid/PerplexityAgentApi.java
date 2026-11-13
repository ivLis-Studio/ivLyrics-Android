package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Perplexity's Agent API. Sonar Chat Completions support ended on September 27, 2026: model ids
 * now carry a provider prefix (perplexity/sonar, openai/...), there is no sonar-pro, and the
 * model list is GET /v1/models.
 * https://docs.perplexity.ai/docs/agent-api/migrate-from-sonar/overview
 */
final class PerplexityAgentApi {
    static final String PROVIDER_ID = "perplexity";
    /** The migration guide's successor for sonar and sonar-pro when the contract is a model id. */
    static final String DEFAULT_MODEL = "perplexity/sonar";
    /** The default model first, then Perplexity's own models, then the rest by id. */
    static final Comparator<String> MODEL_ORDER = Comparator
            .comparing((String id) -> !DEFAULT_MODEL.equals(id))
            .thenComparing(id -> !id.startsWith("perplexity/"))
            .thenComparing(id -> id.toLowerCase(Locale.ROOT));
    // Grounded answers cite as [web:1][web:2]; nothing in ivLyrics resolves those markers.
    private static final Pattern WEB_CITATION_MARKER = Pattern.compile("\\[web:\\d+\\]");

    private PerplexityAgentApi() {
    }

    /** A saved id without a provider prefix is a retired Sonar name and becomes the default. */
    static String normalizeModel(String model) {
        String id = model == null ? "" : model.trim();
        return id.contains("/") ? id : DEFAULT_MODEL;
    }

    static String normalizeModel(String providerId, String model) {
        return PROVIDER_ID.equals(providerId) ? normalizeModel(model) : model;
    }

    static String agentEndpoint(String baseUrl) {
        return versionedBase(baseUrl) + "/agent";
    }

    static String modelsEndpoint(String baseUrl) {
        return versionedBase(baseUrl) + "/models";
    }

    private static String versionedBase(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        return base.endsWith("/v1") ? base : base + "/v1";
    }

    /**
     * The Agent API rejects any field it does not know with a 400, so the body carries only
     * mapped fields. It searches only when offered the web_search tool.
     */
    static JSONObject requestBody(
            String prompt,
            String model,
            int maxOutputTokens,
            float temperature,
            boolean stream,
            boolean webSearch
    ) throws JSONException {
        JSONObject body = new JSONObject()
                .put("model", normalizeModel(model))
                .put("input", prompt == null ? "" : prompt)
                .put("max_output_tokens", maxOutputTokens)
                .put("temperature", temperature)
                .put("store", false);
        if (webSearch) body.put("tools", new JSONArray().put(new JSONObject().put("type", "web_search")));
        if (stream) body.put("stream", true);
        return body;
    }

    /** Failed and cancelled runs arrive as HTTP 200, so the status decides. */
    static String responseText(JSONObject response) throws IOException {
        if (response == null) throw new IOException("[Perplexity] Empty response from API");
        String status = response.optString("status", "").trim().toLowerCase(Locale.ROOT);
        if (status.isEmpty() && response.has("error")) {
            throw new IOException("[Perplexity] " + errorMessage(response.opt("error"), "API response error"));
        }
        if ("failed".equals(status) || "cancelled".equals(status)) {
            throw new IOException("[Perplexity] " + errorMessage(response.opt("error"), "Response " + status));
        }
        if ("incomplete".equals(status)) {
            JSONObject details = response.optJSONObject("incomplete_details");
            String reason = details == null ? "" : details.optString("reason", "");
            throw new IOException("[Perplexity] Response incomplete (" + (reason.isEmpty() ? "incomplete" : reason) + ")");
        }
        if (!"completed".equals(status)) {
            throw new IOException("[Perplexity] Response rejected (" + (status.isEmpty() ? "missing_status" : status) + ")");
        }
        StringBuilder text = new StringBuilder();
        JSONArray output = response.optJSONArray("output");
        for (int index = 0; output != null && index < output.length(); index++) {
            JSONObject item = output.optJSONObject(index);
            JSONArray content = item == null || !"message".equals(item.optString("type", ""))
                    ? null : item.optJSONArray("content");
            for (int partIndex = 0; content != null && partIndex < content.length(); partIndex++) {
                JSONObject part = content.optJSONObject(partIndex);
                String type = part == null ? "" : part.optString("type", "");
                if ("refusal".equals(type)) {
                    throw new IOException("[Perplexity] Response refused: " + part.optString("refusal", "Request refused"));
                }
                if ("output_text".equals(type)) text.append(part.optString("text", ""));
            }
        }
        if (text.length() == 0) text.append(response.optString("output_text", ""));
        return text.toString();
    }

    static String stripWebCitations(String text) {
        return text == null ? "" : WEB_CITATION_MARKER.matcher(text).replaceAll("");
    }

    /** One stream: exactly one terminal event (completed, failed, incomplete, cancelled) or an error. */
    static final class StreamState {
        private boolean receivedDelta;
        private boolean completed;

        boolean completed() {
            return completed;
        }

        /** The text an event adds. Failed, incomplete, cancelled, refusal and error events throw. */
        String consume(String eventName, String data) throws Exception {
            String value = data == null ? "" : data.trim();
            if (value.isEmpty() || "[DONE]".equals(value)) return "";
            JSONObject event = new JSONObject(value);
            String type = event.optString("type", eventName == null ? "" : eventName);
            switch (type) {
                case "response.output_text.delta": {
                    String delta = event.optString("delta", "");
                    if (!delta.isEmpty()) receivedDelta = true;
                    return delta;
                }
                case "response.output_text.done": {
                    String text = event.optString("text", "");
                    if (receivedDelta || text.isEmpty()) return "";
                    receivedDelta = true;
                    return text;
                }
                case "response.refusal.done":
                    throw new IOException("[Perplexity] Response refused: " + event.optString("refusal", "Request refused"));
                case "response.completed": {
                    completed = true;
                    JSONObject response = event.optJSONObject("response");
                    return receivedDelta || response == null ? "" : responseText(response);
                }
                case "response.failed":
                case "response.incomplete":
                case "response.cancelled": {
                    String status = type.substring("response.".length());
                    JSONObject response = event.optJSONObject("response");
                    if (response == null) response = new JSONObject();
                    if (!response.has("status")) response.put("status", status);
                    responseText(response);
                    throw new IOException("[Perplexity] Response " + status);
                }
                case "error":
                    throw new IOException("[Perplexity] " + errorMessage(
                            event.has("error") ? event.opt("error") : event.opt("message"), "Stream error"));
                default:
                    return "";
            }
        }
    }

    private static String errorMessage(Object error, String fallback) {
        if (error instanceof JSONObject) {
            JSONObject object = (JSONObject) error;
            String message = object.optString("message", "").trim();
            if (!message.isEmpty()) return message;
            String type = object.optString("type", "").trim();
            return type.isEmpty() ? fallback : type;
        }
        String text = error == null || error == JSONObject.NULL ? "" : String.valueOf(error).trim();
        return text.isEmpty() ? fallback : text;
    }
}
