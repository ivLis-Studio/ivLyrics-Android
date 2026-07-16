package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PaxsenixLyricsProviderEntityTest {
    @Test
    public void decoderHandlesOneLayerAndPreservesOrdinaryAmpersands() {
        assertEquals("Don't & stop \"now\" <3 >2 'yes' \uD83C\uDFB5",
                PaxsenixLyricsProvider.decodeLyricEntities(
                        "Don&apos;t &amp; stop &quot;now&quot; &lt;3 &gt;2 &#39;yes&#39; &#x1F3B5;"
                ));
        assertEquals("R&B &unknown; &nbsp;",
                PaxsenixLyricsProvider.decodeLyricEntities("R&B &unknown; &nbsp;"));
        assertEquals("Don&apos;t", PaxsenixLyricsProvider.decodeLyricEntities("Don&amp;apos;t"));
        assertEquals("bad &#x110000; value",
                PaxsenixLyricsProvider.decodeLyricEntities("bad &#x110000; value"));
    }

    @Test
    public void structuredLyricsDecodeLeadBackgroundAndReferenceText() throws Exception {
        JSONObject first = line(100L, 2_000L,
                token("Can", 100L, 500L, true),
                token("&apos;t", 500L, 900L, true),
                token("stop", 900L, 2_000L, false));
        first.put("key", "line-1");
        first.put("backgroundText", new JSONArray().put(
                token("R&amp;B & soul", 800L, 1_900L, false)
        ));
        JSONObject payload = new JSONObject()
                .put("syncType", "Syllable")
                .put("metadata", new JSONObject().put("rawData", new JSONObject().put(
                        "lyrics_text",
                        "[100,1900]<0,400,0>Can<400,400,0>&apos;t<800,1100,0> stop"
                )))
                .put("lyrics", new JSONArray().put(first));

        PaxsenixLyricsProvider.Variants variants = PaxsenixLyricsProvider.parseStructuredLyrics(
                payload, 3_000L, "", "", "Song", "Artist"
        );

        assertNotNull(variants);
        LyricsLine rendered = variants.karaoke.lines.get(0);
        assertEquals("Can't stop R&B & soul", rendered.text);
        assertEquals("Can't stop", rendered.vocalParts.get(0).text);
        assertEquals("R&B & soul", rendered.vocalParts.get(1).text);
        assertEquals(Arrays.asList("Can", "'t ", "stop"), syllableTexts(rendered.vocalParts.get(0)));
    }

    @Test
    public void scalarLrcAndPlainLyricsAreDecoded() throws Exception {
        JSONObject scalar = new JSONObject()
                .put("syncType", "Line")
                .put("lyrics", new JSONArray().put(new JSONObject()
                        .put("timestamp", 1_000L)
                        .put("endtime", 2_000L)
                        .put("text", "We&apos;re R&amp;B")
                        .put("backgroundText", new JSONArray())));
        PaxsenixLyricsProvider.Variants scalarVariants = PaxsenixLyricsProvider.parseStructuredLyrics(
                scalar, 3_000L, "", "", "Song", "Artist"
        );
        assertNotNull(scalarVariants);
        assertEquals("We're R&B", scalarVariants.synced.lines.get(0).text);

        JSONObject lrc = new JSONObject().put("lrc", "[00:01.00]Don&apos;t &amp; stop");
        PaxsenixLyricsProvider.Variants lrcVariants = PaxsenixLyricsProvider.parsePayloadVariants(
                lrc, 3_000L, "", "", "Song", "Artist"
        );
        assertNotNull(lrcVariants);
        assertEquals("Don't & stop", lrcVariants.synced.lines.get(0).text);

        JSONObject plain = new JSONObject().put("plain", "Rock & Roll\nWe&apos;re ready");
        PaxsenixLyricsProvider.Variants plainVariants = PaxsenixLyricsProvider.parsePayloadVariants(
                plain, 0L, "", "", "Song", "Artist"
        );
        assertNotNull(plainVariants);
        assertEquals("Rock & Roll", plainVariants.plain.lines.get(0).text);
        assertEquals("We're ready", plainVariants.plain.lines.get(1).text);
    }

    @Test
    public void onlyPaxsenixCachePolicyIncludesTextRevision() {
        Map<String, LyricsProviderSettings.ProviderConfig> configs = new LinkedHashMap<>();
        for (LyricsProviderSettings.Provider provider : LyricsProviderSettings.PROVIDERS) {
            configs.put(provider.id, new LyricsProviderSettings.ProviderConfig(
                    provider, true, true, true, true
            ));
        }
        LyricsProviderSettings.Snapshot settings = new LyricsProviderSettings.Snapshot(
                Arrays.asList("lrclib", "paxsenix", "lyricsplus", "unison"),
                configs,
                true,
                true
        );

        assertEquals(settings.cacheKey(), settings.cacheKeyForProvider("lrclib"));
        assertEquals(settings.cacheKey(), settings.cacheKeyForProvider("lyricsplus"));
        assertTrue(settings.cacheKeyForProvider("paxsenix")
                .endsWith("|paxsenix-text:" + PaxsenixLyricsProvider.TEXT_CACHE_REVISION));
        assertFalse(settings.cacheKey().equals(settings.cacheKeyForProvider("paxsenix")));

        LyricsLine cachedLine = new LyricsLine(
                1_000L,
                2_000L,
                "Don&apos;t decode an old cache repeatedly",
                java.util.Collections.emptyList()
        );
        LyricsResult oldPaxsenix = new LyricsResult(
                java.util.Collections.singletonList(cachedLine),
                "Lyrically (Paxsenix) synced",
                "",
                false,
                "",
                ""
        ).withSelection("paxsenix", settings.cacheKey());
        LyricsResult refreshedPaxsenix = oldPaxsenix.withSelection(
                "paxsenix",
                settings.cacheKeyForProvider("paxsenix")
        );
        LyricsResult unchangedLrclib = oldPaxsenix.withSelection("lrclib", settings.cacheKey());

        assertFalse(LyricsRepository.isCachedResultReusable(oldPaxsenix, settings));
        assertTrue(LyricsRepository.isCachedResultReusable(refreshedPaxsenix, settings));
        assertTrue(LyricsRepository.isCachedResultReusable(unchangedLrclib, settings));
    }

    private static JSONObject line(long start, long end, JSONObject... tokens) throws Exception {
        return new JSONObject()
                .put("timestamp", start)
                .put("endtime", end)
                .put("text", new JSONArray(Arrays.asList(tokens)))
                .put("backgroundText", new JSONArray());
    }

    private static JSONObject token(String text, long start, long end, boolean part) throws Exception {
        return new JSONObject()
                .put("text", text)
                .put("timestamp", start)
                .put("endtime", end)
                .put("part", part);
    }

    private static java.util.List<String> syllableTexts(LyricsLine.VocalPart part) {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (LyricsLine.Syllable syllable : part.syllables) result.add(syllable.text);
        return result;
    }
}
