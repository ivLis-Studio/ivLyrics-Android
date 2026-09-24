package kr.ivlis.ivlyricsandroid;
import java.io.IOException;
import java.util.*;
import org.json.*;

public final class DeepLRegression {
    static int checks;
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    interface Action { void run() throws Exception; }
    static void rejects(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Accepted invalid input/response"); }
        catch (IOException expected) { checks++; }
    }
    static final List<JSONObject> bodies = new ArrayList<>();
    static final List<String> urls = new ArrayList<>();
    static final List<Map<String,String>> headers = new ArrayList<>();
    static final DeepLTranslationProvider.Transport echo = (url, body, auth) -> {
        urls.add(url); bodies.add(body); headers.add(auth);
        check(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 128 * 1024, "UTF-8 body limit");
        JSONArray texts = body.getJSONArray("text"), values = new JSONArray();
        for (int i=0;i<texts.length();i++) values.put(new JSONObject().put("text", "T:" + texts.getString(i)));
        return new JSONObject().put("translations", values).toString();
    };
    public static void main(String[] args) throws Exception {
        List<String> translated = DeepLTranslationProvider.translate(Arrays.asList("", "one / two", "[Instrumental]", "♪", "(Intro)", "three", ""), "zh-TW", " free:fx ", true, echo);
        check(translated.equals(Arrays.asList("", "T:one / T:two", "[Instrumental]", "♪", "(Intro)", "T:three", "")), "preserve rows, markers and parts");
        check(urls.get(0).equals("https://api-free.deepl.com/v2/translate"), "direct Free endpoint");
        check(headers.get(0).get("Authorization").equals("DeepL-Auth-Key free:fx"), "header authentication");
        check(bodies.get(0).getString("target_lang").equals("ZH-HANT") && bodies.get(0).getBoolean("preserve_formatting"), "language and format");
        check(DeepLTranslationProvider.targetLanguage("zh_CN").equals("ZH-HANS"), "simplified Chinese");
        check(DeepLTranslationProvider.targetLanguage("en").equals("EN-US") && DeepLTranslationProvider.targetLanguage("pt").equals("PT-PT"), "regional targets");
        translated = DeepLTranslationProvider.translate(Arrays.asList("[Title]", "Singer / Guest"), "ko", "pro", false, echo);
        check(urls.get(1).equals("https://api.deepl.com/v2/translate"), "direct Pro endpoint");
        check(translated.equals(Arrays.asList("T:[Title]", "T:Singer / Guest")), "metadata not treated as lyric markers");
        bodies.clear();
        List<String> lines = new ArrayList<>(); for (int i=0;i<104;i++) lines.add(String.valueOf(i));
        translated = DeepLTranslationProvider.translate(lines, "ko", "free:fx", true, echo);
        check(bodies.size()==3 && bodies.get(0).getJSONArray("text").length()==50 && bodies.get(2).getJSONArray("text").length()==4, "50-text batching");
        check(translated.get(103).equals("T:103"), "batch order");
        bodies.clear();
        DeepLTranslationProvider.translate(Arrays.asList("한".repeat(25000), "글".repeat(25000)), "ko", "pro", true, echo);
        check(bodies.size()==2, "UTF-8 size batching");
        rejects(() -> DeepLTranslationProvider.translate(List.of("한".repeat(45000)), "ko", "pro", true, echo));
        rejects(() -> DeepLTranslationProvider.translate(List.of("a"), "ko", " ", true, echo));
        for (String invalid : List.of("{}", "{\"translations\":[]}", "{\"translations\":[{\"text\":7}]}", "{\"translations\":[{\"text\":\" \"}]}")) {
            rejects(() -> DeepLTranslationProvider.translate(List.of("a"), "ko", "pro", true, (u,b,h) -> invalid));
        }
        rejects(() -> DeepLTranslationProvider.translate(List.of("a"), "ko", "pro", true, (u,b,h) -> { throw new IOException("HTTP 456"); }));
        int requests=bodies.size();
        DeepLTranslationProvider.translate(Arrays.asList("", "[Break]"), "ko", "pro", true, echo);
        check(bodies.size()==requests, "no request for protected-only lyrics");
        check(ProviderGates.PROVIDERS.get(0).id.equals("gemini"), "default provider unchanged");
        ProviderGates.Provider provider = ProviderGates.aiProviderById("deepl");
        check(provider.translationOnly() && !provider.keyless && !provider.defaultEnabled && provider.defaultModel.isEmpty(), "translation-only keyed registration");
        ProviderGates.Snapshot settings = new ProviderGates.Snapshot(), deepl = new ProviderGates.Snapshot();
        deepl.apiKey="fixture";
        settings.profiles.put("deepl", deepl); settings.aiProviderOrder.add("deepl"); settings.aiProviderEnabled.put("deepl", true);
        check(settings.hasAnyTranslationProvider() && !settings.hasReadyAiProvider() && !settings.hasEnabledAiProvider(), "DeepL alone translates without enabling AI");
        deepl.model="legacy-model";
        check(settings.readyAiProviderSnapshots().isEmpty(), "even saved model cannot route AI to DeepL");
        deepl.apiKey=""; check(!settings.hasAnyTranslationProvider(), "missing key is not ready");
        deepl.apiKey="fixture"; settings.aiProviderEnabled.put("deepl",false); check(!settings.hasAnyTranslationProvider(), "disabled DeepL is not ready");
        settings.googleTranslateEnabled=true; check(settings.hasAnyTranslationProvider(), "existing keyless readiness retained");
        System.out.println("DeepL regression: " + checks + " checks passed");
    }
}
