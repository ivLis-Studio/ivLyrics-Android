package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class LyricsProviderSelectionPlanTest {
    @Test
    public void typeFirstTriesEveryKaraokeProviderBeforeSyncedLyrics() {
        LyricsProviderSelectionPlan plan = LyricsProviderSelectionPlan.create(
                snapshot(true, true, null),
                Collections.emptySet()
        );

        assertEquals(Arrays.asList(
                "lyricsplus:karaoke",
                "unison:karaoke",
                "lyricsplus:synced",
                "unison:synced",
                "lrclib:synced",
                "lyricsplus:plain",
                "unison:plain",
                "lrclib:plain"
        ), keys(plan));
    }

    @Test
    public void providerFirstKeepsAllFormatsWithinProviderPriority() {
        LyricsProviderSelectionPlan plan = LyricsProviderSelectionPlan.create(
                snapshot(false, true, null),
                Collections.emptySet()
        );

        assertEquals(Arrays.asList(
                "lyricsplus:karaoke",
                "lyricsplus:synced",
                "lyricsplus:plain",
                "unison:karaoke",
                "unison:synced",
                "unison:plain",
                "lrclib:synced",
                "lrclib:plain"
        ), keys(plan));
    }

    @Test
    public void openDbSyncDataMovesOwningProviderAheadAndAddsKaraokePhase() {
        LyricsProviderSelectionPlan plan = LyricsProviderSelectionPlan.create(
                snapshot(true, true, null),
                new LinkedHashSet<>(Collections.singletonList("lrclib"))
        );

        assertEquals("lrclib", plan.providers.get(0).provider.id);
        assertEquals("lrclib:karaoke", plan.attempts.get(0).key());
    }

    @Test
    public void disabledFormatIsOmittedFromPlan() {
        Map<String, boolean[]> overrides = new LinkedHashMap<>();
        overrides.put("lyricsplus", new boolean[]{true, false, true, true});
        LyricsProviderSelectionPlan plan = LyricsProviderSelectionPlan.create(
                snapshot(true, true, overrides),
                Collections.emptySet()
        );

        assertFalse(keys(plan).contains("lyricsplus:karaoke"));
        assertEquals("unison:karaoke", plan.attempts.get(0).key());
    }

    @Test
    public void cachedSyncDataCanOnlyApplyToProviderThatDeclaresIvLyricsSync() {
        LyricsProviderSettings.Snapshot settings = snapshot(true, true, null);
        LyricsResult lyricsPlus = cachedResult("lyricsplus", false);
        LyricsResult unison = cachedResult("unison", false);
        LyricsResult lrclib = cachedResult("lrclib", false);

        assertFalse(LyricsProviderSelectionPlan.canApplyIvLyricsSyncToCachedResult(lyricsPlus, settings));
        assertFalse(LyricsProviderSelectionPlan.canApplyIvLyricsSyncToCachedResult(unison, settings));
        assertTrue(LyricsProviderSelectionPlan.canApplyIvLyricsSyncToCachedResult(lrclib, settings));
        assertFalse(LyricsProviderSelectionPlan.canApplyIvLyricsSyncToCachedResult(cachedResult("lrclib", true), settings));

        Map<String, boolean[]> overrides = new LinkedHashMap<>();
        overrides.put("lrclib", new boolean[]{true, false, true, true});
        assertFalse(LyricsProviderSelectionPlan.canApplyIvLyricsSyncToCachedResult(
                lrclib,
                snapshot(true, true, overrides)
        ));
    }

    @Test
    public void cachedExternalProviderRechecksOpenDbButSyncedOwnerDoesNot() {
        LyricsProviderSettings.Snapshot settings = snapshot(true, true, null);
        LyricsResult lyricsPlus = cachedResult("lyricsplus", true);
        LyricsResult lrclibKaraoke = cachedResult("lrclib", true);

        assertTrue(LyricsProviderSelectionPlan.shouldRecheckCachedResult(
                lyricsPlus,
                settings,
                "GBARL9300135"
        ));
        assertFalse(LyricsProviderSelectionPlan.shouldRecheckCachedResult(
                lyricsPlus,
                settings,
                ""
        ));
        assertFalse(LyricsProviderSelectionPlan.shouldRecheckCachedResult(
                lrclibKaraoke,
                settings,
                "GBARL9300135"
        ));
        assertFalse(LyricsProviderSelectionPlan.shouldRecheckCachedResult(
                lyricsPlus.withSelection("lyricsplus", "manual"),
                settings,
                "GBARL9300135"
        ));
        assertEquals("lrclib", LyricsProviderSelectionPlan.preferredIvLyricsSyncProviderId(
                settings,
                new LinkedHashSet<>(Collections.singletonList("lrclib"))
        ));
    }

    private static LyricsProviderSettings.Snapshot snapshot(
            boolean typeFirst,
            boolean preferSyncData,
            Map<String, boolean[]> overrides
    ) {
        List<String> order = Arrays.asList("lyricsplus", "unison", "lrclib");
        Map<String, LyricsProviderSettings.ProviderConfig> configs = new LinkedHashMap<>();
        for (LyricsProviderSettings.Provider provider : LyricsProviderSettings.PROVIDERS) {
            boolean[] values = overrides == null ? null : overrides.get(provider.id);
            boolean enabled = values == null || values[0];
            boolean karaoke = values == null || values[1];
            boolean synced = values == null || values[2];
            boolean plain = values == null || values[3];
            configs.put(provider.id, new LyricsProviderSettings.ProviderConfig(
                    provider,
                    enabled,
                    karaoke,
                    synced,
                    plain
            ));
        }
        return new LyricsProviderSettings.Snapshot(order, configs, preferSyncData, typeFirst);
    }

    private static List<String> keys(LyricsProviderSelectionPlan plan) {
        java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        for (LyricsProviderSelectionPlan.Attempt attempt : plan.attempts) {
            keys.add(attempt.key());
        }
        return keys;
    }

    private static LyricsResult cachedResult(String providerId, boolean karaoke) {
        LyricsLine line = new LyricsLine(1_000L, 2_000L, "cached", Collections.emptyList());
        return new LyricsResult(
                Collections.singletonList(line),
                providerId,
                "",
                karaoke,
                "",
                ""
        ).withSelection(providerId, "policy");
    }
}
