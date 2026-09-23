package kr.ivlis.ivlyricsandroid;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class AiModelsRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static List<AiProviderModels.Model> parse(String provider, String json) throws Exception {
        return AiProviderModels.parse(provider, new JSONObject(json));
    }
    public static void main(String[] args) throws Exception {
        for (String provider : new String[]{"chatgpt", "groq", "openrouter", "perplexity", "pollinations", "paxsenix"}) {
            var models = parse(provider, "{\"data\":[{\"id\":\"text-model\"},{\"id\":\"text-model\"},{\"id\":\"whisper-large\"},{\"id\":\"text-embedding-3\"},{\"id\":\"\"}]}");
            check(models.size() == 1 && models.get(0).id.equals("text-model"), provider + " text filtering/deduplication");
        }
        var gemini = parse("gemini", "{\"models\":[{\"name\":\"models/gemini-test\",\"displayName\":\"Test Gemini\",\"supportedGenerationMethods\":[\"generateContent\"]},{\"name\":\"models/embedding\",\"supportedGenerationMethods\":[\"embedContent\"]}]}");
        check(gemini.size() == 1 && gemini.get(0).id.equals("gemini-test") && gemini.get(0).name.equals("Test Gemini"), "Gemini resource prefix/name/capability");
        check(parse("claude", "{\"data\":[{\"id\":\"claude-test\",\"type\":\"model\",\"display_name\":\"Test Claude\"}]}").get(0).name.equals("Test Claude"), "Claude display name");
        check(parse("openrouter", "{\"data\":[{\"id\":\"image-only\",\"architecture\":{\"output_modalities\":[\"image\"]}},{\"id\":\"text-and-audio\",\"architecture\":{\"input_modalities\":[\"text\"],\"output_modalities\":[\"text\",\"audio\"]}}]}").size() == 1, "Capabilities override ID heuristic");
        var pollinations = parse("pollinations", "{\"data\":[{\"id\":\"anthropic/claude-test\",\"title\":\"Claude Test\",\"category\":\"text\",\"supported_endpoints\":[\"/v1/chat/completions\"]},{\"id\":\"openai/image\",\"category\":\"image\"},{\"id\":\"responses-only\",\"supported_endpoints\":[\"/v1/responses\"]}]}");
        check(pollinations.size() == 1 && pollinations.get(0).name.equals("Claude Test"), "Pollinations includes other providers and excludes other endpoints");
        check(parse("paxsenix", "{\"data\":[{\"id\":\"available\",\"status\":\"Available\",\"type\":\"chat.completions\",\"endpoint\":\"/v1/chat/completions\",\"modalities\":{\"input\":[\"text\"],\"output\":[\"text\"]}},{\"id\":\"down\",\"status\":\"Unavailable\"},{\"id\":\"image\",\"type\":\"images.generations\"}]}").size() == 1, "Paxsenix availability and endpoint");
        check(AiProviderModels.endpoint("pollinations", "https://example.test/v1/", "").equals("https://example.test/v1/models"), "Do not duplicate v1");
        check(AiProviderModels.endpoint("chatgpt", "https://proxy.test/api/v1/", "").equals("https://proxy.test/api/v1/models"), "Custom base URL");
        check(AiProviderModels.headers("gemini", "fixture").get("x-goog-api-key").equals("fixture"), "Gemini header");
        check(AiProviderModels.headers("claude", "fixture").get("anthropic-version").equals("2023-06-01"), "Claude version header");
        check(AiProviderModels.headers("pollinations", "token").get("Authorization").equals("Bearer token"), "Pollinations bearer");
        check(!AiProviderModels.headers("openrouter", "").containsKey("Authorization"), "Anonymous public catalog");
        check(AiProviderModels.fetch("perplexity", "https://api.perplexity.ai", "").builtIn, "Sonar catalog is explicitly built in");
        check(!AiProviderModels.usesSonarCatalog("perplexity", "https://api.perplexity.ai/router/v1"), "Router uses its own dynamic catalog");
        try { parse("chatgpt", "{\"error\":{\"message\":\"failed\"}}"); throw new AssertionError("Invalid response accepted"); }
        catch (java.io.IOException expected) { checks++; }
        try { AiProviderModels.nextCursor("claude", new JSONObject("{\"has_more\":true}")); throw new AssertionError("Missing cursor accepted"); }
        catch (java.io.IOException expected) { checks++; }

        var method = PollinationsAuthClient.class.getDeclaredMethod("buildAuthorizeUrl", String.class);
        method.setAccessible(true);
        String authUrl = (String) method.invoke(new PollinationsAuthClient(), "AB CD");
        String query = URI.create(authUrl).getRawQuery();
        check(!query.contains("models=") && query.contains("user_code=AB+CD") && query.contains("budget=999"), "All-model login preserves device code and budget");

        AtomicInteger pages = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models", exchange -> {
            int page = pages.incrementAndGet();
            check("fixture".equals(exchange.getRequestHeaders().getFirst("x-goog-api-key")), "Network auth header");
            String queryText = exchange.getRequestURI().getRawQuery();
            check(queryText.contains("pageSize=1000") && (page == 1 || queryText.contains("pageToken=next%2Fpage")), "Network pagination query");
            String response = page == 1
                    ? "{\"models\":[{\"name\":\"models/z\",\"supportedGenerationMethods\":[\"generateContent\"]}],\"nextPageToken\":\"next/page\"}"
                    : "{\"models\":[{\"name\":\"models/a\",\"supportedGenerationMethods\":[\"generateContent\"]},{\"name\":\"models/z\",\"supportedGenerationMethods\":[\"generateContent\"]}]}";
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.createContext("/denied/models", exchange -> { exchange.sendResponseHeaders(401, -1); exchange.close(); });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var catalog = AiProviderModels.fetch("gemini", base, "fixture");
            check(pages.get() == 2 && catalog.models.size() == 2 && catalog.models.get(0).id.equals("a"), "All pages merged, deduplicated and sorted");
            try { AiProviderModels.fetch("chatgpt", base + "/denied", "fixture"); throw new AssertionError("401 accepted"); }
            catch (java.io.IOException expected) { check(expected.getMessage().contains("401"), "HTTP error propagated"); }
        } finally { server.stop(0); }
        System.out.println("AI model regression: " + checks + " checks passed");
    }
}
