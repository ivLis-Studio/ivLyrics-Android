package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pronunciation Lab in the unified provider list: flags, order, defaults and capability gates. */
public final class PronunciationLabProviderTest {
    private static final String LAB = AiLyricsSettings.Provider.PRONUNCIATION_LAB_ID;

    @Test public void registeredAsAKeylessPronunciationOnlyProviderOnByDefault() {
        AiLyricsSettings.Provider lab = AiLyricsSettings.aiProviderById(LAB);
        assertNotNull(lab);
        assertEquals("pronunciation-lab", lab.id);
        assertEquals("Pronunciation Lab", lab.label);
        assertTrue(lab.keyless);
        assertTrue(lab.defaultEnabled);
        assertTrue(lab.pronunciationOnly);
        assertFalse(lab.localModel);
        assertFalse(lab.translationOnly());
        assertFalse(lab.fullAi());
        assertFalse(lab.canTranslate());
        assertTrue(lab.canPronounce());
        assertEquals("", lab.defaultBaseUrl + lab.defaultModel + lab.apiKeyUrl);
        assertEquals(lab, AiLyricsSettings.aiProviderById(" Pronunciation-Lab "));
        // No connection profile: it is not a selectable keyed provider.
        for (AiLyricsSettings.Provider provider : AiLyricsSettings.PROVIDERS) {
            assertFalse(provider.id, provider.pronunciationOnly);
        }
        assertEquals("gemini", AiLyricsSettings.providerById(LAB).id);
    }

    @Test public void orderedAfterEveryExistingProvider() {
        assertEquals(Arrays.asList(
                KeylessTranslationProviders.BING_ID, KeylessTranslationProviders.GOOGLE_ID,
                "gemini", "chatgpt", "claude", "openrouter", "groq", "paxsenix", "perplexity", "pollinations", "deepl",
                AiLyricsSettings.Provider.LOCAL_AI_ID, LAB), AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER);
        List<AiLyricsSettings.Provider> all = AiLyricsSettings.ALL_AI_PROVIDERS;
        assertEquals(LAB, all.get(all.size() - 1).id);
    }

    @Test public void aSavedOrderGainsItAtTheEndAndKeepsAMovedOne() {
        List<String> saved = Arrays.asList("claude", "gemini", "google-translate", "unknown-provider");
        List<String> normalized = AiLyricsSettings.normalizeAiProviderOrder(saved);
        assertEquals(Arrays.asList("claude", "gemini", "google-translate"), normalized.subList(0, 3));
        assertEquals(LAB, normalized.get(normalized.size() - 1));
        assertEquals(AiLyricsSettings.ALL_AI_PROVIDERS.size(), normalized.size());
        // A saved order from before Local AI and Pronunciation Lab gains both, in that order.
        List<String> legacy = new ArrayList<>(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER);
        legacy.remove(LAB);
        legacy.remove(AiLyricsSettings.Provider.LOCAL_AI_ID);
        Collections.reverse(legacy);
        List<String> upgraded = AiLyricsSettings.normalizeAiProviderOrder(legacy);
        assertEquals(legacy, upgraded.subList(0, legacy.size()));
        assertEquals(Arrays.asList(AiLyricsSettings.Provider.LOCAL_AI_ID, LAB), upgraded.subList(legacy.size(), upgraded.size()));
        List<String> moved = AiLyricsSettings.normalizeAiProviderOrder(Arrays.asList(LAB, "chatgpt"));
        assertEquals(Arrays.asList(LAB, "chatgpt"), moved.subList(0, 2));
        assertEquals(1, Collections.frequency(moved, LAB));
    }

    @Test public void existingProvidersKeepTheirCapabilities() {
        for (AiLyricsSettings.Provider provider : AiLyricsSettings.ALL_AI_PROVIDERS) {
            if (provider.id.equals(LAB)) continue;
            boolean previouslyTranslationOnly = provider.keyless || provider.localModel || "deepl".equals(provider.id);
            assertFalse(provider.id, provider.pronunciationOnly);
            assertEquals(provider.id, previouslyTranslationOnly, provider.translationOnly());
            assertEquals(provider.id, !previouslyTranslationOnly, provider.fullAi());
            assertTrue(provider.id, provider.canTranslate());
            assertEquals(provider.id, !previouslyTranslationOnly, provider.canPronounce());
        }
    }

    @Test public void withoutAnAiProviderItIsTheOnlyPronunciationProvider() {
        AiLyricsSettings.Snapshot settings = snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, defaultsEnabled(),
                Collections.emptyMap());
        assertTrue(settings.isAiProviderEnabled(LAB));
        assertEquals(Collections.singletonList(LAB), settings.linePronunciationProviderIds());
        assertTrue(settings.hasLinePronunciationProvider());
        // Not an AI provider: unit pronunciation, timing, research and every other AI task ignore it.
        assertTrue(settings.readyAiProviderSnapshots().isEmpty());
        assertFalse(settings.hasReadyAiProvider());
        assertFalse(settings.hasEnabledAiProvider());
        assertNull(settings.forProvider(LAB));
        // Translation readiness comes from the keyless translators alone.
        assertTrue(settings.hasAnyTranslationProvider());
        assertTrue(settings.hasLyricsTranslationProvider());
        Map<String, Boolean> noTranslators = defaultsEnabled();
        noTranslators.put(KeylessTranslationProviders.BING_ID, false);
        noTranslators.put(KeylessTranslationProviders.GOOGLE_ID, false);
        AiLyricsSettings.Snapshot pronunciationOnly = snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, noTranslators,
                Collections.emptyMap());
        assertFalse(pronunciationOnly.hasAnyTranslationProvider());
        assertFalse(pronunciationOnly.hasLyricsTranslationProvider());
        assertTrue(pronunciationOnly.hasLinePronunciationProvider());
    }

    @Test public void readyAiProvidersAnswerFirstAndItIsTheFallback() {
        Map<String, Boolean> enabled = defaultsEnabled();
        enabled.put("chatgpt", true);
        enabled.put("claude", true); // Enabled without a key: not ready, so never asked.
        AiLyricsSettings.Snapshot settings = snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, enabled, readyChatGpt());
        assertEquals(Arrays.asList("chatgpt", LAB), settings.linePronunciationProviderIds());
        assertEquals(1, settings.readyAiProviderSnapshots().size());
        assertEquals("chatgpt", settings.readyAiProviderSnapshots().get(0).provider.id);
        assertTrue(settings.hasEnabledAiProvider());

        enabled.put(LAB, false);
        AiLyricsSettings.Snapshot off = snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, enabled, readyChatGpt());
        assertEquals(Collections.singletonList("chatgpt"), off.linePronunciationProviderIds());

        enabled.put("chatgpt", false);
        AiLyricsSettings.Snapshot none = snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, enabled, readyChatGpt());
        assertFalse(none.hasLinePronunciationProvider());

        enabled.put(LAB, true);
        enabled.put("chatgpt", true);
        AiLyricsSettings.Snapshot first = snapshot(Arrays.asList(LAB, "chatgpt"), enabled, readyChatGpt());
        assertEquals(Arrays.asList(LAB, "chatgpt"), first.linePronunciationProviderIds());
    }

    @Test public void generalCacheKeysNeverDependOnIt() {
        Map<String, Boolean> enabled = defaultsEnabled();
        String on = snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, enabled, Collections.emptyMap()).cacheKey();
        enabled.put(LAB, false);
        String off = snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, enabled, Collections.emptyMap()).cacheKey();
        List<String> moved = new ArrayList<>(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER);
        moved.remove(LAB);
        moved.add(0, LAB);
        String first = snapshot(moved, defaultsEnabled(), Collections.emptyMap()).cacheKey();
        assertFalse(on.contains(LAB));
        assertEquals(on, off);
        assertEquals(on, first);
    }

    @Test public void pronunciationCacheKeysChangeOnlyWhileItWritesThePronunciation() {
        Map<String, Boolean> enabled = defaultsEnabled();
        enabled.put("chatgpt", true);
        // The default place behind a ready AI provider keeps the keys cached before it existed.
        assertEquals("", snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, enabled, readyChatGpt()).pronunciationLabCacheKey());
        // Alone: a combined entry cached for the song earlier holds no pronunciation.
        assertEquals("|pronunciationProvider=pronunciation-lab",
                snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, defaultsEnabled(), Collections.emptyMap()).pronunciationLabCacheKey());
        // Asked before the AI provider, so it writes the pronunciation.
        assertEquals("|pronunciationProvider=pronunciation-lab",
                snapshot(Arrays.asList(LAB, "chatgpt"), enabled, readyChatGpt()).pronunciationLabCacheKey());
        // Off: the AI pronunciation cached before it existed is used again.
        enabled.put(LAB, false);
        assertEquals("",
                snapshot(AiLyricsSettings.DEFAULT_AI_PROVIDER_ORDER, enabled, readyChatGpt()).pronunciationLabCacheKey());
    }

    /** The enabled map of a fresh install: every provider at its default. */
    private static Map<String, Boolean> defaultsEnabled() {
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        for (AiLyricsSettings.Provider provider : AiLyricsSettings.ALL_AI_PROVIDERS) {
            enabled.put(provider.id, provider.defaultEnabled);
        }
        return enabled;
    }

    private static Map<String, AiLyricsSettings.ProviderProfile> readyChatGpt() {
        return Collections.singletonMap("chatgpt", new AiLyricsSettings.ProviderProfile(
                "fixture-key", "https://fixture.invalid/v1", "fixture-model", 16000, 0.3f));
    }

    private static AiLyricsSettings.Snapshot snapshot(
            List<String> order,
            Map<String, Boolean> enabled,
            Map<String, AiLyricsSettings.ProviderProfile> profiles
    ) {
        AiLyricsSettings.Provider selected = AiLyricsSettings.providerById("chatgpt");
        AiLyricsSettings.ProviderProfile profile = profiles.containsKey(selected.id)
                ? profiles.get(selected.id) : AiLyricsSettings.ProviderProfile.defaults(selected);
        return new AiLyricsSettings.Snapshot(
                "en", AiLyricsSettings.OUTPUT_LANG_SAME_UI, AiLyricsSettings.PRONUNCIATION_NOTATION_TRANSLATION, selected,
                new AiLyricsSettings.LanguageRule(AiLyricsSettings.DEFAULT_SOURCE_LANG, true, true, AiLyricsSettings.OUTPUT_LANG_SAME_UI),
                Collections.emptyMap(), profile.apiKeys, "", profile.baseUrl, profile.model, profile.maxTokens, profile.temperature,
                AiLyricsSettings.PREVIEW_MODE_ORIGINAL, AiLyricsSettings.PREVIEW_ITEM_ORIGINAL,
                true, true, false, true, AiLyricsSettings.KARAOKE_DISPLAY_CHARACTER,
                null, true, true, false, true, AiLyricsSettings.PIP_ORIENTATION_SQUARE, AiLyricsSettings.LYRICS_ALIGN_CENTER, 150,
                false, false,
                Boolean.TRUE.equals(enabled.get(KeylessTranslationProviders.BING_ID)),
                Boolean.TRUE.equals(enabled.get(KeylessTranslationProviders.GOOGLE_ID)),
                order, enabled, profiles,
                false, AiLyricsSettings.CULTURAL_FONT_PRETENDARD, 14, 300, 60, AiLyricsSettings.CULTURAL_FONT_PRETENDARD, 12, 300, 60,
                null, null, null, true, AiLyricsSettings.LYRICS_ALIGN_LEFT, "", "",
                LocalAiModels.DEFAULT_TIER.id, LocalAiModels.DEFAULT_IDLE_UNLOAD_MINUTES, false);
    }
}
