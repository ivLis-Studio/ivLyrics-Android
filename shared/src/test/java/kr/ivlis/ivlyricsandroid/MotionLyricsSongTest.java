package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public final class MotionLyricsSongTest {
    private static List<LyricsLine.Syllable> timed(long start, String... words) {
        LyricsLine.Syllable[] syllables = new LyricsLine.Syllable[words.length];
        for (int index = 0; index < words.length; index++) {
            syllables[index] = new LyricsLine.Syllable(words[index], start + index * 300L, start + (index + 1) * 300L);
        }
        return Arrays.asList(syllables);
    }

    private static LyricsResult karaoke(boolean karaoke, LyricsLine... lines) {
        return new LyricsResult(Arrays.asList(lines), "provider", "", karaoke);
    }

    private static final LyricsLine PLAIN = new LyricsLine(
            1000, 2800, "Every road", timed(1000, "Every ", "road"), "", "vocal", Collections.emptyList(), "", "모든 길", "");
    private static final LyricsLine LINE_SYNCED = new LyricsLine(1000, 3000, "No word timing", Collections.emptyList());

    @Test public void onlyWordTimedLyricsPlay() {
        assertTrue(MotionLyricsSong.canPlay(karaoke(true, PLAIN)));
        LyricsLine.VocalPart lead = new LyricsLine.VocalPart("a", "lead", "v1", "vocal", "Hold on", timed(5000, "Hold ", "on"));
        LyricsLine partsOnly = new LyricsLine(
                5000, 6200, "Hold on", Collections.emptyList(), "", "vocal", Collections.singletonList(lead));
        assertTrue("timing that lives only in the vocal parts plays", MotionLyricsSong.canPlay(karaoke(true, partsOnly)));
        assertFalse(MotionLyricsSong.canPlay(karaoke(true, LINE_SYNCED)));
        assertFalse(MotionLyricsSong.canPlay(karaoke(false, PLAIN)));
        assertFalse(MotionLyricsSong.canPlay(karaoke(true)));
        assertFalse(MotionLyricsSong.canPlay(null));
    }

    @Test public void spotifyTracksKeepTheirUriAndOthersGetAStableKey() {
        assertEquals("spotify:track:4LhDEsC2tzaVuZbWAaCLrP", MotionLyricsSong.uri("4LhDEsC2tzaVuZbWAaCLrP", "spotify:4LhDEsC2tzaVuZbWAaCLrP"));
        String other = MotionLyricsSong.uri("", "song|singer|180000");
        assertTrue(other.matches("ivlyrics:track:[0-9a-f]{22}"));
        assertEquals(other, MotionLyricsSong.uri(null, "song|singer|180000"));
        assertNotEquals(other, MotionLyricsSong.uri("", "song|singer|180001"));
    }

    @Test public void linesGoOutInTheDesktopFormat() {
        String json = MotionLyricsSong.json("spotify:track:id1", "Song", "Singer", "#ff8800", "en", karaoke(true, PLAIN));
        assertEquals("{\"uri\":\"spotify:track:id1\",\"title\":\"Song\",\"artist\":\"Singer\",\"accent\":\"#ff8800\","
                + "\"language\":\"en\",\"lyrics\":[{\"text\":\"Every road\",\"startTime\":1000,\"endTime\":2800,"
                + "\"syllables\":[{\"text\":\"Every \",\"startTime\":1000,\"endTime\":1300},"
                + "{\"text\":\"road\",\"startTime\":1300,\"endTime\":1600}],\"text2\":\"모든 길\"}]}", json);
    }

    @Test public void theFirstLeadPartLeadsAndTheOthersSingWithIt() {
        LyricsLine.VocalPart lead = new LyricsLine.VocalPart("a", "lead", "v1", "vocal", "Hold on", timed(5000, "Hold ", "on"));
        LyricsLine.VocalPart partner = new LyricsLine.VocalPart("b", "lead", "v2", "vocal", "I will", timed(5200, "I ", "will"));
        LyricsLine.VocalPart backing = new LyricsLine.VocalPart("c", "background", "v1", "vocal", "ooh", timed(5400, "ooh"));
        LyricsLine.VocalPart silent = new LyricsLine.VocalPart("d", "background", "v1", "vocal", "(ah)", Collections.emptyList());
        LyricsLine voices = new LyricsLine(5000, 6200, "Hold on I will ooh", timed(5000, "Hold ", "on"), "", "vocal",
                Arrays.asList(lead, partner, backing, silent));
        String json = MotionLyricsSong.json("u", "t", "a", "", "", karaoke(true, voices));
        assertTrue(json.contains("\"vocals\":{\"lead\":{\"text\":\"Hold on\",\"role\":\"lead\",\"speaker\":\"v1\","));
        assertTrue("a second lead part is a duet partner", json.contains("\"background\":[{\"text\":\"I will\",\"role\":\"lead\",\"speaker\":\"v2\","));
        assertTrue(json.contains("{\"text\":\"ooh\",\"role\":\"background\",\"speaker\":\"v1\",\"syllables\":[{\"text\":\"ooh\",\"startTime\":5400,\"endTime\":5700}]}]}"));
        assertFalse("parts without timing are left out", json.contains("(ah)"));
        assertFalse("no translation, no text2", json.contains("text2"));
    }

    @Test public void lineSyncedResultsSendNoLyricsSoThePageTurnsThemDown() {
        assertTrue(MotionLyricsSong.json("u", "t", "a", "", "", karaoke(false, PLAIN)).endsWith("\"lyrics\":[]}"));
        assertTrue(MotionLyricsSong.json("u", "t", "a", "", "", null).endsWith("\"lyrics\":[]}"));
    }

    @Test public void stringsAreSafeInsideAScript() {
        StringBuilder out = new StringBuilder();
        MotionLyricsSong.string(out, "He said \"hi\"\\\n\tnext\u2028line\u0001");
        assertEquals("\"He said \\\"hi\\\"\\\\\\n\\tnext\\u2028line\\u0001\"", out.toString());
        out.setLength(0);
        MotionLyricsSong.string(out, null);
        assertEquals("\"\"", out.toString());
    }
}
