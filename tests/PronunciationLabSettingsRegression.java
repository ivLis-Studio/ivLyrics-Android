package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Production AiLyricsSettings with in-memory preferences: Pronunciation Lab's default, order and saved choices. */
public final class PronunciationLabSettingsRegression {
    private static final String LAB = AiLyricsSettings.Provider.PRONUNCIATION_LAB_ID;
    private static int assertions;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(AiLyricsSettings.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String last(List<String> order) {
        return order.get(order.size() - 1);
    }

    /** What an app version before Local AI and Pronunciation Lab saved. */
    private static JSONObject olderEnabled() throws Exception {
        return new JSONObject().put("bing-translate", true).put("google-translate", false).put("gemini", true)
                .put("chatgpt", false).put("claude", false).put("openrouter", false).put("groq", false)
                .put("paxsenix", false).put("perplexity", false).put("pollinations", false).put("deepl", false);
    }

    private static JSONArray olderOrder() {
        return new JSONArray(List.of("google-translate", "gemini", "bing-translate", "chatgpt"));
    }

    private static void freshInstall() {
        Context context = new Context();
        AiLyricsSettings settings = new AiLyricsSettings(context);
        AiLyricsSettings.Snapshot snapshot = settings.snapshot();
        check(snapshot.isAiProviderEnabled(LAB), "on by default on a fresh install");
        check(LAB.equals(last(snapshot.aiProviderOrder)), "after every other provider on a fresh install");
        check(snapshot.linePronunciationProviderIds().equals(List.of(LAB)), "the pronunciation provider while no AI provider is set up");
        check(!snapshot.hasEnabledAiProvider() && !snapshot.hasReadyAiProvider(), "and not counted as an AI provider");
        check(snapshot.hasKeylessTranslationProvider() && !snapshot.isAiProviderEnabled(AiLyricsSettings.Provider.LOCAL_AI_ID),
                "the other keyless and local defaults are unchanged");
        settings.shutdown();
    }

    private static void olderSavedStateGainsItOnAndLast() throws Exception {
        Context context = new Context();
        prefs(context).edit()
                .putString(AiLyricsSettings.KEY_AI_PROVIDER_ENABLED, olderEnabled().toString())
                .putString(AiLyricsSettings.KEY_AI_PROVIDER_ORDER, olderOrder().toString())
                .commit();
        AiLyricsSettings settings = new AiLyricsSettings(context);
        AiLyricsSettings.Snapshot snapshot = settings.snapshot();
        check(snapshot.isAiProviderEnabled(LAB), "a saved enabled map without it turns it on");
        check(snapshot.isAiProviderEnabled("gemini") && !snapshot.isAiProviderEnabled("google-translate")
                && snapshot.isAiProviderEnabled("bing-translate"), "the saved choices stay as they were");
        List<String> order = snapshot.aiProviderOrder;
        check(order.subList(0, 4).equals(List.of("google-translate", "gemini", "bing-translate", "chatgpt")),
                "a saved order keeps its positions");
        check(LAB.equals(last(order)) && order.indexOf(AiLyricsSettings.Provider.LOCAL_AI_ID) == order.size() - 2,
                "and gains it at the very end, after Local AI");
        settings.shutdown();
    }

    private static void anExplicitChoiceIsKept() throws Exception {
        Context context = new Context();
        AiLyricsSettings settings = new AiLyricsSettings(context);
        settings.setAiProviderEnabled(LAB, false);
        check(!settings.snapshot().isAiProviderEnabled(LAB), "the switch turns it off");
        JSONObject stored = new JSONObject(prefs(context).getString(AiLyricsSettings.KEY_AI_PROVIDER_ENABLED, "{}"));
        check(stored.has(LAB) && !stored.getBoolean(LAB), "the choice is stored explicitly");
        settings.shutdown();

        AiLyricsSettings reloaded = new AiLyricsSettings(context);
        check(!reloaded.snapshot().isAiProviderEnabled(LAB), "an explicit off survives a restart");
        reloaded.setAiProviderEnabled("gemini", true);
        check(!reloaded.snapshot().isAiProviderEnabled(LAB), "switching another provider keeps it off");
        reloaded.setAiProviderEnabled(LAB, true);
        reloaded.shutdown();
        AiLyricsSettings again = new AiLyricsSettings(context);
        check(again.snapshot().isAiProviderEnabled(LAB), "an explicit on is kept too");
        check(!prefs(context).contains(AiLyricsSettings.KEY_BING_TRANSLATE_ENABLED)
                && !prefs(context).contains(AiLyricsSettings.KEY_GOOGLE_TRANSLATE_ENABLED),
                "its switch writes none of the translators' legacy keys");
        again.shutdown();
    }

    private static void itCanBeMovedLikeTheOthers() {
        Context context = new Context();
        AiLyricsSettings settings = new AiLyricsSettings(context);
        settings.moveAiProvider(LAB, "gemini", false);
        List<String> order = settings.snapshot().aiProviderOrder;
        check(order.indexOf(LAB) + 1 == order.indexOf("gemini"), "dragging places it above an AI provider");
        settings.moveAiProviderByOffset(LAB, 1);
        List<String> nudged = settings.snapshot().aiProviderOrder;
        check(nudged.indexOf(LAB) == nudged.indexOf("gemini") + 1, "the move buttons step it like the others");
        settings.shutdown();
        check(new AiLyricsSettings(context).snapshot().aiProviderOrder.equals(nudged), "the order is saved");
    }

    private static void cloudSettingsFromAnOlderDevice() throws Exception {
        Context context = new Context();
        AiLyricsSettings settings = new AiLyricsSettings(context);
        settings.importCloudSettings(new JSONObject()
                .put(AiLyricsSettings.KEY_AI_PROVIDER_ENABLED, olderEnabled().toString())
                .put(AiLyricsSettings.KEY_AI_PROVIDER_ORDER, olderOrder().toString()));
        AiLyricsSettings.Snapshot snapshot = settings.snapshot();
        check(snapshot.isAiProviderEnabled(LAB) && LAB.equals(last(snapshot.aiProviderOrder)),
                "settings synced from a device without it leave it on and last");
        settings.setAiProviderEnabled(LAB, false);
        JSONObject exported = settings.exportCloudSettings();
        check(!new JSONObject(exported.getString(AiLyricsSettings.KEY_AI_PROVIDER_ENABLED)).getBoolean(LAB),
                "its explicit state syncs with the other providers");
        settings.shutdown();
    }

    public static void main(String[] args) throws Exception {
        freshInstall();
        olderSavedStateGainsItOnAndLast();
        anExplicitChoiceIsKept();
        itCanBeMovedLikeTheOthers();
        cloudSettingsFromAnOlderDevice();
        System.out.println("PASS " + assertions + " assertions: on by default and last, saved enabled maps/orders from older versions,"
                + " explicit choices across restarts, reordering, cloud import/export");
    }
}
