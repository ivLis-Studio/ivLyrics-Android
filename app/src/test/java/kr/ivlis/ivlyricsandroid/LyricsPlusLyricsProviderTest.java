package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class LyricsPlusLyricsProviderTest {
    @Test
    public void wordPayloadSplitsLongMixedScriptLineWithoutSupplements() throws Exception {
        JSONObject line = new JSONObject()
                .put("time", 1_000)
                .put("duration", 6_000)
                .put("text", "ドラマの中に迷い込んだ 結末を知らない One day You Make Me Wonder")
                .put("transliteration", "ignored pronunciation")
                .put("element", new JSONObject().put("key", "long-line").put("singer", "lead"));
        JSONArray syllables = new JSONArray();
        syllables.put(syllable("ドラマの中に", 1_000, 1_000));
        syllables.put(syllable("迷い込んだ ", 2_000, 1_000));
        syllables.put(syllable("結末を知らない ", 3_000, 1_000));
        syllables.put(syllable("One day ", 4_000, 1_000));
        syllables.put(syllable("You Make Me Wonder", 5_000, 2_000));
        line.put("syllabus", syllables);

        LyricsResult result = LyricsPlusLyricsProvider.parsePayload(
                payload("Word", new JSONArray().put(line)),
                10_000L,
                "JPPC02513528",
                ""
        );

        assertNotNull(result);
        assertTrue(result.karaoke);
        assertTrue(result.lines.size() >= 2);
        assertTrue(result.lines.size() <= 4);
        for (LyricsLine split : result.lines) {
            assertFalse(split.syllables.isEmpty());
            assertTrue(split.pronunciationText.isEmpty());
            assertTrue(split.translationText.isEmpty());
        }
        String joined = joinTexts(result);
        assertTrue(joined.contains("ドラマの中に"));
        assertTrue(joined.contains("One day"));
        assertTrue(joined.contains("You Make Me Wonder"));
    }

    @Test
    public void longLineIsNotSplitWhenSourceTextDiffersFromTimedSyllables() throws Exception {
        JSONObject line = new JSONObject()
                .put("time", 1_000)
                .put("duration", 4_000)
                .put("text", "Displayed source text must remain completely intact here")
                .put("syllabus", new JSONArray()
                        .put(syllable("Different timed ", 1_000, 1_000))
                        .put(syllable("syllable text ", 2_000, 1_000))
                        .put(syllable("must not replace ", 3_000, 1_000))
                        .put(syllable("the source", 4_000, 1_000)));

        LyricsResult result = LyricsPlusLyricsProvider.parsePayload(
                payload("Word", new JSONArray().put(line)),
                6_000L,
                "",
                ""
        );

        assertNotNull(result);
        assertEquals(1, result.lines.size());
        assertEquals("Displayed source text must remain completely intact here", result.lines.get(0).text);
    }

    @Test
    public void overlappingDifferentSingersBecomeParallelVocalParts() throws Exception {
        JSONArray lyrics = new JSONArray()
                .put(wordLine("a", "main", "どうして", 1_000, 2_000))
                .put(wordLine("b", "back", "まだ終わらない", 1_500, 1_500));

        LyricsPlusLyricsProvider.Variants variants = LyricsPlusLyricsProvider.parsePayloadVariants(
                payload("Word", lyrics),
                5_000L,
                "JPU902503234",
                ""
        );
        LyricsResult result = variants == null ? null : variants.karaoke;

        assertNotNull(result);
        assertTrue(result.karaoke);
        assertEquals(1, result.lines.size());
        assertEquals(2, result.lines.get(0).vocalParts.size());
        assertEquals("lead", result.lines.get(0).vocalParts.get(0).role);
        assertEquals("background", result.lines.get(0).vocalParts.get(1).role);
        assertNotNull(variants.synced);
        assertNotNull(variants.plain);
        assertEquals(2, variants.synced.lines.size());
        assertEquals(2, variants.plain.lines.size());
        assertTrue(variants.synced.lines.get(0).vocalParts.isEmpty());
    }

    @Test
    public void incidentalOverlapBelowThirtyMillisecondsStaysSeparate() throws Exception {
        JSONArray lyrics = new JSONArray()
                .put(wordLine("a", "main", "first", 1_000, 1_000))
                .put(wordLine("b", "back", "second", 1_971, 500));

        LyricsResult result = LyricsPlusLyricsProvider.parsePayload(
                payload("Word", lyrics),
                4_000L,
                "",
                ""
        );

        assertNotNull(result);
        assertEquals(2, result.lines.size());
        assertTrue(result.lines.get(0).vocalParts.isEmpty());
        assertTrue(result.lines.get(1).vocalParts.isEmpty());
    }

    @Test
    public void sameSingerSourceLinesMergeIntoStableVocalLanes() throws Exception {
        JSONArray lyrics = new JSONArray()
                .put(wordLine("a1", "main", "main one", 1_000, 1_000))
                .put(wordLine("b1", "back", "back one", 1_500, 1_000))
                .put(wordLine("a2", "main", "main two", 2_000, 1_000))
                .put(wordLine("b2", "back", "back two", 2_500, 1_000));

        LyricsResult result = LyricsPlusLyricsProvider.parsePayload(payload("Word", lyrics), 5_000L, "", "");

        assertNotNull(result);
        assertEquals(1, result.lines.size());
        assertEquals(2, result.lines.get(0).vocalParts.size());
        LyricsLine.VocalPart lead = result.lines.get(0).vocalParts.get(0);
        LyricsLine.VocalPart background = result.lines.get(0).vocalParts.get(1);
        assertEquals("lead", lead.role);
        assertEquals("background", background.role);
        assertTrue(lead.text.contains("main one"));
        assertTrue(lead.text.contains("main two"));
        assertTrue(background.text.contains("back one"));
        assertTrue(background.text.contains("back two"));
    }

    @Test
    public void subThirtyMillisecondOverlapStillConnectsSeededComponent() throws Exception {
        JSONArray lyrics = new JSONArray()
                .put(wordLine("a1", "main", "main one", 1_000, 800))
                .put(wordLine("b1", "back", "back", 1_500, 500))
                .put(wordLine("a2", "main", "main two", 1_971, 529));

        LyricsResult result = LyricsPlusLyricsProvider.parsePayload(payload("Word", lyrics), 4_000L, "", "");

        assertNotNull(result);
        assertEquals(1, result.lines.size());
        assertEquals(2, result.lines.get(0).vocalParts.size());
        assertTrue(result.lines.get(0).vocalParts.get(0).text.contains("main two"));
    }

    @Test
    public void parallelGroupsNeverContainMoreThanFourSourceLines() throws Exception {
        JSONArray lyrics = new JSONArray()
                .put(wordLine("line-0", "singer-0", "part 0", 1_000, 1_000))
                .put(wordLine("line-1", "singer-1", "part 1", 1_800, 1_000))
                .put(wordLine("line-2", "singer-2", "part 2", 2_600, 1_000));
        JSONObject crossingLine = new JSONObject()
                .put("time", 3_400)
                .put("duration", 1_600)
                .put("text", "part 3a part 3b")
                .put("element", new JSONObject().put("key", "line-3").put("singer", "singer-3"))
                .put("syllabus", new JSONArray()
                        .put(syllable("part 3a ", 3_400, 800))
                        .put(syllable("part 3b", 4_200, 800)));
        lyrics.put(crossingLine)
                .put(wordLine("line-4", "singer-4", "part 4", 4_200, 1_000));

        LyricsResult result = LyricsPlusLyricsProvider.parsePayload(payload("Word", lyrics), 6_000L, "", "");

        assertNotNull(result);
        assertEquals(2, result.lines.size());
        for (LyricsLine line : result.lines) {
            assertTrue(line.vocalParts.size() <= 4);
        }
        assertEquals(result.lines.get(0).endTimeMs, result.lines.get(1).startTimeMs);
        String allParts = joinVocalPartTexts(result);
        for (int index = 0; index < 5; index++) {
            assertTrue(allParts.contains("part " + index));
        }
    }

    @Test
    public void lineAndPlainPayloadsPreserveTheirFormats() throws Exception {
        JSONObject timed = new JSONObject()
                .put("time", 1_000)
                .put("duration", 2_000)
                .put("text", "line synced");
        LyricsResult lineResult = LyricsPlusLyricsProvider.parsePayload(
                payload("Line", new JSONArray().put(timed)),
                5_000L,
                "",
                ""
        );
        assertNotNull(lineResult);
        assertFalse(lineResult.karaoke);
        assertTrue(lineResult.lines.get(0).isTimed());
        assertTrue(lineResult.lines.get(0).syllables.isEmpty());

        JSONObject plain = new JSONObject()
                .put("text", "plain lyrics")
                .put("transliteration", "must not be imported");
        LyricsResult plainResult = LyricsPlusLyricsProvider.parsePayload(
                payload("None", new JSONArray().put(plain)),
                5_000L,
                "",
                ""
        );
        assertNotNull(plainResult);
        assertFalse(plainResult.karaoke);
        assertFalse(plainResult.lines.get(0).isTimed());
        assertTrue(plainResult.lines.get(0).pronunciationText.isEmpty());
    }

    @Test
    public void syllableOnlyPayloadWithNullErrorIsAccepted() throws Exception {
        JSONObject line = new JSONObject()
                .put("time", 1_000)
                .put("duration", 1_000)
                .put("text", "")
                .put("syllabus", new JSONArray().put(syllable("syllable only", 1_000, 1_000)));
        JSONObject source = payload("Word", new JSONArray().put(line)).put("error", JSONObject.NULL);

        LyricsResult result = LyricsPlusLyricsProvider.parsePayload(source, 3_000L, "", "");

        assertNotNull(result);
        assertEquals("syllable only", result.lines.get(0).text);
    }

    private static JSONObject payload(String type, JSONArray lyrics) throws Exception {
        return new JSONObject().put("type", type).put("lyrics", lyrics);
    }

    private static JSONObject wordLine(
            String key,
            String singer,
            String text,
            long start,
            long duration
    ) throws Exception {
        return new JSONObject()
                .put("time", start)
                .put("duration", duration)
                .put("text", text)
                .put("element", new JSONObject().put("key", key).put("singer", singer))
                .put("syllabus", new JSONArray().put(syllable(text, start, duration)));
    }

    private static JSONObject syllable(String text, long start, long duration) throws Exception {
        return new JSONObject().put("text", text).put("time", start).put("duration", duration);
    }

    private static String joinTexts(LyricsResult result) {
        StringBuilder joined = new StringBuilder();
        for (LyricsLine line : result.lines) {
            if (joined.length() > 0) joined.append(' ');
            joined.append(line.text);
        }
        return joined.toString();
    }

    private static String joinVocalPartTexts(LyricsResult result) {
        StringBuilder joined = new StringBuilder();
        for (LyricsLine line : result.lines) {
            for (LyricsLine.VocalPart part : line.vocalParts) {
                if (joined.length() > 0) joined.append(' ');
                joined.append(part.text);
            }
        }
        return joined.toString();
    }

}
