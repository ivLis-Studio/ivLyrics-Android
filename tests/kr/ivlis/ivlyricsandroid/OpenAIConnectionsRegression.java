package kr.ivlis.ivlyricsandroid;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;

public final class OpenAIConnectionsRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static OpenAIConnection connection(String id, boolean enabled) {
        return new OpenAIConnection(id, id, "https://" + id + ".test/v1", "key-" + id, id, enabled);
    }
    private static AiLyricsSettings.Snapshot snapshot(String provider, AiLyricsSettings.ProviderProfile profile) {
        return new AiLyricsSettings.Snapshot(AiLyricsSettings.providerById(provider), profile, Map.of("chatgpt", profile));
    }
    private static List<String> placeholders(String value) {
        return Pattern.compile("\\{\\w+\\}|%(?:\\d+\\$)?[sd@]").matcher(value).results().map(match -> match.group()).sorted().toList();
    }
    public static void main(String[] args) throws Exception {
        AiLyricsSettings storage = new AiLyricsSettings();
        storage.secureStore.putString("keys", "legacy-secret");
        storage.prefs.edit().putString("model", "legacy-model").apply();
        check(storage.openAIConnections().isEmpty(), "Legacy storage has no extra connections");
        List<OpenAIConnection> extras = new ArrayList<>(List.of(connection("disabled", false), connection("backup", true), connection("last", true)));
        storage.cachedSnapshot = snapshot("chatgpt", new AiLyricsSettings.ProviderProfile("k", "b", "m", 1000, .3f));
        storage.setOpenAIConnections(extras);
        check(storage.cachedSnapshot == null, "Connection changes invalidate cached snapshots");
        extras.clear();
        check(storage.openAIConnections().size() == 3, "Saving makes an isolated copy");
        JSONObject stored = new JSONObject(storage.secureStore.getString("profiles", "")).getJSONObject("chatgpt");
        check(stored.getString("apiKeys").equals("legacy-secret") && stored.getString("model").equals("legacy-model"), "Legacy primary survives migration");
        check(!storage.prefs.values.containsKey("profiles"), "Secrets use secure storage, not preferences");
        storage.cachedSnapshot = snapshot("chatgpt", new AiLyricsSettings.ProviderProfile("k", "b", "m", 1000, .3f));
        storage.setProviderProfile("chatgpt", "primary-secret", "https://primary.test/v1", "primary", 4096, .7f);
        check(storage.cachedSnapshot == null, "Primary edits invalidate cached snapshots");
        check(storage.openAIConnections().get(1).apiKeys.equals("key-backup"), "Primary edits preserve extra credentials");
        storage.setProviderProfile("claude", "other-secret", "https://claude.test", "other", 4096, .7f);
        check(storage.openAIConnections().size() == 3, "Editing a different provider preserves connections");
        try { storage.openAIConnections().clear(); throw new AssertionError("Mutable connection collection"); }
        catch (UnsupportedOperationException expected) { checks++; }
        List<OpenAIConnection> roundTrip = OpenAIConnection.parse(OpenAIConnection.toJson(storage.openAIConnections()));
        check(!roundTrip.get(0).enabled && roundTrip.get(2).id.equals("last"), "JSON preserves order and disabled state");
        check(OpenAIConnection.parse(new JSONArray("[null,7,{\"model\":\"m\",\"apiKeys\":\"k\"}]")).get(0).isReady(), "Missing optional fields and invalid rows handled");
        var profile = new AiLyricsSettings.ProviderProfile("primary-secret", "https://primary.test/v1", "primary", 4096, .7f, roundTrip);
        var source = snapshot("chatgpt", profile);
        List<AiLyricsSettings.Snapshot> candidates = source.openAIConnectionSnapshots();
        check(candidates.stream().map(s -> s.model).toList().equals(List.of("primary", "backup", "last")), "Primary first; disabled skipped; user order retained");
        check(candidates.get(1).apiKeys.equals("key-backup") && candidates.get(1).baseUrl.equals("https://backup.test/v1"), "Each connection uses its own credentials and URL");
        check(candidates.get(1).maxTokens == 4096 && candidates.get(1).temperature == .7f, "Common tuning preserved");
        check(source.model.equals("primary") && profile.apiKeys.equals("primary-secret"), "Request copies do not mutate saved primary");
        check(snapshot("claude", profile).openAIConnectionSnapshots().size() == 1, "Other providers do not use OpenAI extras");
        var emptyPrimary = snapshot("chatgpt", new AiLyricsSettings.ProviderProfile("", "", "", 4096, .7f, roundTrip));
        check(emptyPrimary.hasApiKey() && emptyPrimary.hasModel(), "Usable backups satisfy readiness with empty primary");
        var disabledOnly = snapshot("chatgpt", new AiLyricsSettings.ProviderProfile("", "", "", 4096, .7f, List.of(connection("disabled", false))));
        check(!disabledOnly.hasApiKey() && !disabledOnly.hasModel(), "Disabled connection cannot satisfy readiness");

        ConnectionRunner runner = new ConnectionRunner();
        List<String> attempts = new ArrayList<>();
        List<String> output = new ArrayList<>();
        String answer = runner.withOpenAIConnections(source, output::clear, item -> {
            attempts.add(item.model);
            if (item.model.equals("primary")) { output.add("failed partial"); throw new IOException("timeout"); }
            check(output.isEmpty(), "Previous partial output cleared before backup");
            output.add("complete");
            return item.model;
        });
        check(answer.equals("backup") && attempts.equals(List.of("primary", "backup")), "Failover stops on first success");
        check(output.equals(List.of("complete")), "No failed partial text in successful output");
        IOException lastError = new IOException("last");
        attempts.clear();
        try {
            runner.withOpenAIConnections(source, null, item -> {
                attempts.add(item.model);
                if (item.model.equals("last")) throw lastError;
                throw new JSONException("invalid response");
            });
            throw new AssertionError("All failed but succeeded");
        } catch (IOException error) {
            check(error == lastError && attempts.size() == 3, "Validation errors advance and final error is returned");
        }
        attempts.clear();
        try {
            runner.withOpenAIConnections(source, null, item -> { attempts.add(item.model); throw new InterruptedException(); });
            throw new AssertionError("Cancellation ignored");
        } catch (InterruptedException expected) {
            check(attempts.size() == 1 && Thread.currentThread().isInterrupted(), "Cancellation stops without trying next provider");
        } finally { Thread.interrupted(); }
        Thread.currentThread().interrupt();
        try {
            runner.withOpenAIConnections(source, null, item -> { throw new AssertionError("Canceled request started"); });
            throw new AssertionError("Preexisting cancellation ignored");
        } catch (InterruptedException expected) { checks++; }
        finally { Thread.interrupted(); }

        Field field = AppI18n.class.getDeclaredField("STRINGS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked") var languages = (Map<String, Map<String, String>>) field.get(null);
        var reference = languages.get("ko");
        List<String> newKeys = List.of("openai.connections", "openai.connections_desc", "openai.add_connection", "openai.connection_name", "openai.remove_connection", "openai.save_connection", "status.models_builtin_sonar");
        for (var language : languages.entrySet()) {
            var missing = new TreeSet<>(reference.keySet());
            missing.removeAll(language.getValue().keySet());
            check(missing.isEmpty(), language.getKey() + " missing translation keys: " + missing);
            for (String key : reference.keySet()) {
                String value = language.getValue().get(key);
                if (value == null || value.isBlank() || !placeholders(value).equals(placeholders(reference.get(key)))) {
                    throw new AssertionError(language.getKey() + " empty translation or mismatched placeholders: " + key);
                }
            }
            for (String key : newKeys) {
                String value = language.getValue().get(key);
                check(value != null && !value.isBlank(), language.getKey() + " empty " + key);
                if (!language.getKey().equals("en")) check(!value.equals(languages.get("en").get(key)), language.getKey() + " untranslated " + key);
            }
            check(!AppI18n.t(language.getKey(), "dialog.select_model").contains("Paxsenix"), "Generic model chooser localized: " + language.getKey());
        }
        System.out.println("OpenAI connections and i18n regression: " + checks + " checks passed (" + languages.size() + " locales)");
    }
}
