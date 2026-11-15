package kr.ivlis.ivlyricsandroid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * The song as the motion lyrics page reads it ({@link MotionLyricsModeView}): the desktop app's
 * line format. The JSON is written by hand so the mapping runs in plain JVM tests.
 */
final class MotionLyricsSong {
    private MotionLyricsSong() {}

    /**
     * Lines with word or character timing can play; a line-synced song cannot (the words would
     * leave the singing).
     */
    static boolean canPlay(LyricsResult lyrics) {
        if (lyrics == null || !lyrics.karaoke) {
            return false;
        }
        for (LyricsLine line : lyrics.lines) {
            if (!line.syllables.isEmpty()) {
                return true;
            }
            for (LyricsLine.VocalPart part : line.vocalParts) {
                if (!part.syllables.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Spotify tracks keep their URI; others get a stable key the page can cache a direction by. */
    static String uri(String trackId, String stableKey) {
        return trackId == null || trackId.isEmpty()
                ? "ivlyrics:track:" + sha256(stableKey == null ? "" : stableKey).substring(0, 22)
                : "spotify:track:" + trackId;
    }

    /** The JSON the page takes in setTrack. A line-synced result sends no lines, so the page turns it down. */
    static String json(
            String uri, String title, String artist, String accentHex, String language, LyricsResult lyrics
    ) {
        StringBuilder out = new StringBuilder(8192);
        out.append("{\"uri\":");
        string(out, uri);
        out.append(",\"title\":");
        string(out, title);
        out.append(",\"artist\":");
        string(out, artist);
        out.append(",\"accent\":");
        string(out, accentHex);
        out.append(",\"language\":");
        string(out, language);
        out.append(",\"lyrics\":[");
        if (lyrics != null && lyrics.karaoke) {
            boolean first = true;
            for (LyricsLine line : lyrics.lines) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                line(out, line);
            }
        }
        return out.append("]}").toString();
    }

    private static void line(StringBuilder out, LyricsLine line) {
        out.append("{\"text\":");
        string(out, line.text);
        out.append(",\"startTime\":").append(line.startTimeMs);
        out.append(",\"endTime\":").append(line.endTimeMs);
        out.append(",\"syllables\":");
        syllables(out, line.syllables);
        if (!line.translationText.trim().isEmpty()) {
            out.append(",\"text2\":");
            string(out, line.translationText);
        }
        // The first lead part leads the line; the others sing with it (a duet partner is another lead).
        List<LyricsLine.VocalPart> sung = new ArrayList<>();
        LyricsLine.VocalPart lead = null;
        for (LyricsLine.VocalPart part : line.vocalParts) {
            if (part.syllables.isEmpty()) {
                continue;
            }
            if (lead == null && "lead".equals(part.role)) {
                lead = part;
            } else {
                sung.add(part);
            }
        }
        if (lead != null) {
            out.append(",\"vocals\":{\"lead\":");
            part(out, lead);
            out.append(",\"background\":[");
            for (int index = 0; index < sung.size(); index++) {
                if (index > 0) {
                    out.append(',');
                }
                part(out, sung.get(index));
            }
            out.append("]}");
        }
        out.append('}');
    }

    private static void part(StringBuilder out, LyricsLine.VocalPart part) {
        out.append("{\"text\":");
        string(out, part.text);
        out.append(",\"role\":");
        string(out, part.role);
        out.append(",\"speaker\":");
        string(out, part.speaker);
        out.append(",\"syllables\":");
        syllables(out, part.syllables);
        out.append('}');
    }

    private static void syllables(StringBuilder out, List<LyricsLine.Syllable> syllables) {
        out.append('[');
        for (int index = 0; index < syllables.size(); index++) {
            LyricsLine.Syllable syllable = syllables.get(index);
            if (index > 0) {
                out.append(',');
            }
            out.append("{\"text\":");
            string(out, syllable.text);
            out.append(",\"startTime\":").append(syllable.startTimeMs);
            out.append(",\"endTime\":").append(syllable.endTimeMs).append('}');
        }
        out.append(']');
    }

    /** A JSON string that is also safe inside a script: the line separators JavaScript once refused are escaped. */
    static void string(StringBuilder out, String value) {
        out.append('"');
        String text = value == null ? "" : value;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            switch (ch) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (ch < 0x20 || ch == '\u2028' || ch == '\u2029') {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
            }
        }
        out.append('"');
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
