#!/usr/bin/env python3
"""Run extracted production settings/failover and full i18n tables without an Android SDK."""
import subprocess
from regression_runtime import ROOT, SHARED, REPORTS, classpath, java_tool, json_jar

work = REPORTS / "openai-connections"
work.mkdir(parents=True, exist_ok=True)
settings = (SHARED / "AiLyricsSettings.java").read_text()
repository = (SHARED / "AiLyricsRepository.java").read_text()


def section(source, start, end):
    return source[source.index(start):source.index(end, source.index(start))]


settings_methods = "\n".join([
    section(settings, "    void setProviderProfile(", "    void setPreviewMode("),
    section(settings, "    List<OpenAIConnection> openAIConnections()", "    static List<String> normalizeAiProviderOrder("),
    section(settings, "    private static String providerProfilesJson(", "    private static List<Provider> allAiProviders("),
    section(settings, "    static final class ProviderProfile {", "    static final class BackgroundMode {"),
])
snapshot_methods = "\n".join([
    section(settings, "        boolean hasApiKey()", "        boolean hasKeylessTranslationProvider()"),
    section(settings, "        List<Snapshot> openAIConnectionSnapshots()", "        boolean hasSpotifyApiCredentials()"),
])
settings_fixture = '''package kr.ivlis.ivlyricsandroid;
import java.util.*;
import org.json.*;
class AiLyricsSettings {
    static final String KEY_AI_PROVIDER_PROFILES = "profiles", KEY_PROVIDER = "provider", DEFAULT_PROVIDER = "chatgpt",
        KEY_API_KEYS = "keys", KEY_BASE_URL = "base", KEY_MODEL = "model", KEY_MAX_TOKENS = "tokens", KEY_TEMPERATURE = "temp";
    static final List<Provider> PROVIDERS = List.of(new Provider("chatgpt"), new Provider("claude"));
    final SharedPreferences prefs = new SharedPreferences();
    final SharedPreferences secureStore = new SharedPreferences();
    Snapshot cachedSnapshot;
    static float clampFloat(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    static Provider providerById(String id) { return PROVIDERS.stream().filter(p -> p.id.equals(id)).findFirst().orElse(PROVIDERS.get(0)); }
    static String normalizeLanguageCode(String language) { return language; }
    static class Language {
        final String code, name, nativeName;
        Language(String c, String n, String nn, String unused) { code=c; name=n; nativeName=nn; }
    }
    static class Provider {
        final String id, defaultBaseUrl, defaultModel;
        Provider(String value) { id=value; defaultBaseUrl="https://"+value+".test/v1"; defaultModel="primary"; }
    }
    static class SharedPreferences {
        final Map<String,Object> values = new HashMap<>();
        String getString(String key,String fallback) { return (String) values.getOrDefault(key,fallback); }
        int getInt(String key,int fallback) { return (Integer) values.getOrDefault(key,fallback); }
        float getFloat(String key,float fallback) { return (Float) values.getOrDefault(key,fallback); }
        void putString(String key,String value) { values.put(key,value); }
        Editor edit() { return new Editor(); }
        class Editor {
            Editor putString(String k,String v) { values.put(k,v); return this; }
            Editor putInt(String k,int v) { values.put(k,v); return this; }
            Editor putFloat(String k,float v) { values.put(k,v); return this; }
            void apply() {}
        }
    }
    // SETTINGS_METHODS
    static class Snapshot {
        final Provider provider;
        final Map<String,ProviderProfile> providerProfiles;
        final String apiKeys, baseUrl, model, pollinationsAccessToken = "";
        final int maxTokens;
        final float temperature;
        Snapshot(Provider provider, ProviderProfile profile, Map<String,ProviderProfile> profiles) {
            this.provider=provider; providerProfiles=profiles; apiKeys=profile.apiKeys; baseUrl=profile.baseUrl;
            model=profile.model; maxTokens=profile.maxTokens; temperature=profile.temperature;
        }
        private Snapshot withProviderProfile(Provider p, ProviderProfile profile) { return new Snapshot(p,profile,providerProfiles); }
        // SNAPSHOT_METHODS
    }
}
'''
settings_fixture = settings_fixture.replace("    // SETTINGS_METHODS", settings_methods).replace("        // SNAPSHOT_METHODS", snapshot_methods)
(work / "AiLyricsSettings.java").write_text(settings_fixture)
helper = section(repository, "    private interface ConnectionRequest<T>", "    private List<String> loadSupplementValuesStreamFirst(")
helper = helper.replace("private interface", "interface").replace("private <T>", "<T>")
(work / "ConnectionRunner.java").write_text("package kr.ivlis.ivlyricsandroid;\nimport java.io.IOException;\nclass ConnectionRunner {\n" + helper + "}\n")
sources = [work / "AiLyricsSettings.java", work / "ConnectionRunner.java", ROOT / "tests/kr/ivlis/ivlyricsandroid/OpenAIConnectionsRegression.java"]
sources += [SHARED / name for name in ("OpenAIConnection.java", "OpenAIConnectionI18n.java", "AppI18n.java", "SettingsTranslationOverrides.java", "LyricsToolsTranslationOverrides.java", "ResearchI18n.java", "VideoSelectionTranslations.java", "SettingsExperienceTranslations.java")]
dependencies = classpath(work, json_jar())
subprocess.run([java_tool("javac"), "-cp", dependencies, "-d", str(work), *map(str, sources)], check=True)
subprocess.run([java_tool("java"), "-cp", dependencies, "kr.ivlis.ivlyricsandroid.OpenAIConnectionsRegression"], check=True)
