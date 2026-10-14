package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class LocalAiModelsTest {
    @Test public void tiersUseTheProductFilesAndMobileStartsOnLight() {
        assertSame(LocalAiModels.LIGHT, LocalAiModels.DEFAULT_TIER);
        assertEquals(Arrays.asList(LocalAiModels.LIGHT, LocalAiModels.MEDIUM, LocalAiModels.HIGH), LocalAiModels.TIERS);
        String base = "https://huggingface.co/tencent/Hy-MT2-1.8B-GGUF/resolve/main/";
        assertEquals(base + "Hy-MT2-1.8B-Q4_K_M.gguf", LocalAiModels.LIGHT.url());
        assertEquals(base + "Hy-MT2-1.8B-Q6_K.gguf", LocalAiModels.MEDIUM.url());
        assertEquals(base + "Hy-MT2-1.8B-Q8_0.gguf", LocalAiModels.HIGH.url());
        assertEquals(1_133_080_448L, LocalAiModels.LIGHT.sizeBytes);
        assertEquals(1_474_785_120L, LocalAiModels.MEDIUM.sizeBytes);
        assertEquals(1_908_528_192L, LocalAiModels.HIGH.sizeBytes);
        assertEquals(Arrays.asList("1.13GB", "1.47GB", "1.91GB"), Arrays.asList(
                LocalAiModels.formatGigabytes(LocalAiModels.LIGHT.sizeBytes),
                LocalAiModels.formatGigabytes(LocalAiModels.MEDIUM.sizeBytes),
                LocalAiModels.formatGigabytes(LocalAiModels.HIGH.sizeBytes)));
        assertEquals(Arrays.asList("local_ai.tier_light", "local_ai.tier_medium", "local_ai.tier_high"), Arrays.asList(
                LocalAiModels.LIGHT.labelKey, LocalAiModels.MEDIUM.labelKey, LocalAiModels.HIGH.labelKey));
    }

    @Test public void storedTierIdsResolveAndUnknownOnesFallBackToLight() {
        assertSame(LocalAiModels.HIGH, LocalAiModels.tier(" HIGH "));
        assertSame(LocalAiModels.MEDIUM, LocalAiModels.tier("medium"));
        for (String unknown : new String[]{null, "", "q4_k_m", "q8_0", "ultra"}) {
            assertSame(unknown, LocalAiModels.LIGHT, LocalAiModels.tier(unknown));
        }
        assertSame(LocalAiModels.LIGHT, LocalAiModels.tierAt(-1));
        assertSame(LocalAiModels.MEDIUM, LocalAiModels.tierAt(1));
        assertSame(LocalAiModels.HIGH, LocalAiModels.tierAt(7));
    }

    @Test public void idleChoicesAcceptOnlyTheOfferedOptions() {
        assertEquals(10, LocalAiModels.DEFAULT_IDLE_UNLOAD_MINUTES);
        for (int minutes : new int[]{10, 30, 60, 0}) {
            assertEquals(minutes, LocalAiModels.idleUnloadMinutes(minutes));
        }
        assertEquals(30, LocalAiModels.idleUnloadMinutes(" 30 "));
        assertEquals(60, LocalAiModels.idleUnloadMinutes(60L));
        assertEquals(0, LocalAiModels.idleUnloadMinutes("0"));
        for (Object invalid : new Object[]{null, 2, 5, 15, -1, 90, 30.5, "", "never", new Object()}) {
            assertEquals(String.valueOf(invalid), 10, LocalAiModels.idleUnloadMinutes(invalid));
        }
    }

    @Test public void progressAndPlaceholdersFormatLikeTheDesktop() {
        long half = LocalAiModels.LIGHT.sizeBytes / 2;
        assertEquals("Downloading 50% · 567 / 1133 MB", LocalAiModels.format("Downloading {percent}% · {loaded} / {total} MB",
                "percent", String.valueOf(LocalAiModels.percent(half, LocalAiModels.LIGHT.sizeBytes)),
                "loaded", String.valueOf(LocalAiModels.megabytes(half)),
                "total", String.valueOf(LocalAiModels.megabytes(LocalAiModels.LIGHT.sizeBytes))));
        assertEquals(0, LocalAiModels.percent(5, 0));
        assertEquals(100, LocalAiModels.percent(9, 4));
        assertEquals("Error: {size} stays", LocalAiModels.format("Error: {error}", "error", "{size} stays", "size", "1GB"));
        assertEquals("After {hours} hr", LocalAiModels.format("After {hours} hr", "minutes", "10"));
    }
}
