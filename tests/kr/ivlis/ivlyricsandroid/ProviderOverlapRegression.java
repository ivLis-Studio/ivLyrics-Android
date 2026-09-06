package kr.ivlis.ivlyricsandroid;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Synthetic source rows only; an optional local TTML is never copied into the repository. */
public final class ProviderOverlapRegression {
    private static int assertions;

    private static void equal(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    private static void line(LyricsLine line, long start, long end, String text) {
        equal(start, line.startTimeMs);
        equal(end, line.endTimeMs);
        equal(text, line.text);
        equal(0, line.vocalParts.size());
    }

    private static JSONObject plusLine(String text, long start, long end) throws Exception {
        return new JSONObject().put("time", start).put("duration", end - start).put("text", text)
                .put("element", new JSONObject().put("key", text).put("singer", text))
                .put("syllabus", new JSONArray().put(new JSONObject().put("text", text)
                        .put("time", start).put("duration", end - start)));
    }

    private static JSONObject paxToken(String text, double start, double end) throws Exception {
        return new JSONObject().put("text", text).put("timestamp", start * 1000).put("endtime", end * 1000);
    }

    private static JSONObject paxLine(String text, double start, double end) throws Exception {
        return new JSONObject().put("key", text).put("agent", text).put("timestamp", start * 1000)
                .put("endtime", end * 1000).put("text", new JSONArray().put(paxToken(text, start, end)));
    }

    private static void unison() throws Exception {
        String ttml = "<tt xmlns='http://www.w3.org/ns/ttml' xmlns:ttm='http://www.w3.org/ns/ttml#metadata'><body><div>"
                + "<p begin='1s' end='3s'><span begin='1s' end='3s'>A</span></p>"
                + "<p begin='2s' end='6s'><span begin='2s' end='6s'>B</span></p>"
                + "<p begin='5.9s' end='9s'><span begin='5.9s' end='9s'>C</span></p>"
                + "<p begin='8.95s' end='10s'><span begin='8.95s' end='10s'>D</span></p>"
                + "<p begin='11s' end='15s'><span begin='12s' end='14s'>Lead</span>"
                + "<span ttm:role='x-bg' begin='11s' end='15s'><span begin='11s' end='15s'>(Backing)</span></span></p>"
                + "<p begin='11s' end='13s'><span begin='11s' end='13s'>Equal</span></p>"
                + "</div></body></tt>";
        List<LyricsLine> lines = UnisonLyricsProvider.parseTtmlLyrics(ttml, 20000).lines;
        equal(6, lines.size());
        line(lines.get(0), 1000, 3000, "A");
        line(lines.get(1), 2000, 6000, "B");
        line(lines.get(2), 5900, 9000, "C");
        line(lines.get(3), 8950, 10000, "D");
        equal(2, lines.get(4).vocalParts.size());
        equal("lead", lines.get(4).vocalParts.get(0).role);
        equal("Lead", lines.get(4).vocalParts.get(0).text);
        equal(12000L, lines.get(4).vocalParts.get(0).startTimeMs);
        equal("background", lines.get(4).vocalParts.get(1).role);
        equal("Backing", lines.get(4).vocalParts.get(1).text);
        equal(11000L, lines.get(4).vocalParts.get(1).startTimeMs);
        line(lines.get(5), 11000, 13000, "Equal");
    }

    private static void lyricsPlus() throws Exception {
        JSONObject explicit = plusLine("Lead", 11000, 15000);
        explicit.getJSONArray("syllabus").put(new JSONObject().put("text", "(Backing)")
                .put("time", 11000).put("duration", 4000).put("isBackground", true));
        JSONArray input = new JSONArray().put(plusLine("A", 1000, 3000))
                .put(plusLine("B", 2000, 6000)).put(plusLine("C", 5900, 9000))
                .put(plusLine("D", 8950, 10000)).put(explicit).put(plusLine("Equal", 11000, 13000));
        List<LyricsLine> lines = LyricsPlusLyricsProvider.parsePayload(
                new JSONObject().put("type", "word").put("lyrics", input), 20000, "", "").lines;
        equal(6, lines.size());
        line(lines.get(0), 1000, 3000, "A");
        line(lines.get(1), 2000, 6000, "B");
        line(lines.get(2), 5900, 9000, "C");
        line(lines.get(3), 8950, 10000, "D");
        equal(2, lines.get(4).vocalParts.size());
        equal("lead", lines.get(4).vocalParts.get(0).role);
        equal("Lead", lines.get(4).vocalParts.get(0).text);
        equal("background", lines.get(4).vocalParts.get(1).role);
        equal("Backing", lines.get(4).vocalParts.get(1).text);
        line(lines.get(5), 11000, 13000, "Equal");
    }

    private static void paxsenix() throws Exception {
        JSONObject explicit = paxLine("Lead", 12, 15).put("backgroundText",
                new JSONArray().put(paxToken("Backing", 11, 15)));
        JSONArray input = new JSONArray().put(paxLine("Long", 1, 30)).put(paxLine("Short", 2, 3))
                .put(paxLine("Next", 2.95, 6)).put(explicit).put(paxLine("Equal", 11, 13));
        List<LyricsLine> lines = PaxsenixLyricsProvider.parseStructuredLyrics(
                new JSONObject().put("syncType", "syllable").put("lyrics", input),
                60000, "", "", "", "").karaoke.lines;
        equal(5, lines.size());
        // A legitimate sustained part can extend well beyond the next row's start.
        line(lines.get(0), 1000, 30000, "Long");
        equal(30000L, lines.get(0).syllables.get(0).endTimeMs);
        line(lines.get(1), 2000, 3000, "Short");
        line(lines.get(2), 2950, 6000, "Next");
        equal(2, lines.get(3).vocalParts.size());
        equal("lead", lines.get(3).vocalParts.get(0).role);
        equal("Lead", lines.get(3).vocalParts.get(0).text);
        equal("background", lines.get(3).vocalParts.get(1).role);
        equal(11000L, lines.get(3).vocalParts.get(1).startTimeMs);
        line(lines.get(4), 11000, 13000, "Equal");
    }

    public static void main(String[] args) throws Exception {
        unison();
        lyricsPlus();
        paxsenix();
        System.out.println("Provider overlap regression: " + assertions + " assertions passed.");
        if (args.length > 0) {
            List<LyricsLine> lines = UnisonLyricsProvider.parseTtmlLyrics(Files.readString(Path.of(args[0])), 214884).lines;
            equal(40, lines.size());
            long[][] windows = {{152415,157942}, {157018,162446}, {172498,180254},
                    {180117,182555}, {182297,188056}, {187993,190494}, {190542,192128}, {192075,202129}};
            int[] indices = {28,29,33,34,35,36,37,38};
            for (int i = 0; i < indices.length; i++) {
                equal(windows[i][0], lines.get(indices[i]).startTimeMs);
                equal(windows[i][1], lines.get(indices[i]).endTimeMs);
            }
            System.out.println("Local report TTML: 40 source rows retained; all 8 reported overlap windows unchanged.");
        }
    }
}
