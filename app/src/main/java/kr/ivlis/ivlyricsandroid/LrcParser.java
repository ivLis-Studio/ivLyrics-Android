package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LrcParser {
    private static final Pattern LRC_TIME_PATTERN =
            Pattern.compile("\\[(\\d+):(\\d+)(?:[.,](\\d+))?\\](.*)");

    private LrcParser() {
    }

    static List<LyricsLine> parseSynced(String lrc, long durationMs) {
        if (lrc == null || lrc.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricsLine> starts = new ArrayList<>();
        String[] rawLines = lrc.split("\\r?\\n");
        for (String rawLine : rawLines) {
            Matcher matcher = LRC_TIME_PATTERN.matcher(rawLine);
            if (!matcher.matches()) {
                continue;
            }
            long minutes = parseLong(matcher.group(1));
            long seconds = parseLong(matcher.group(2));
            String fraction = matcher.group(3);
            long millis = fractionToMillis(fraction);
            String text = matcher.group(4) == null ? "" : matcher.group(4).trim();
            long startMs = (minutes * 60_000L) + (seconds * 1_000L) + millis;
            starts.add(new LyricsLine(startMs, startMs, text, Collections.emptyList()));
        }

        List<LyricsLine> result = new ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            LyricsLine current = starts.get(index);
            long nextStart = index + 1 < starts.size() ? starts.get(index + 1).startTimeMs : 0L;
            long fallbackEnd = durationMs > current.startTimeMs
                    ? durationMs
                    : current.startTimeMs + 4_000L;
            long end = nextStart > current.startTimeMs ? nextStart : fallbackEnd;
            result.add(new LyricsLine(current.startTimeMs, end, current.text, Collections.emptyList()));
        }
        return result;
    }

    static List<LyricsLine> parsePlain(String plainLyrics) {
        if (plainLyrics == null || plainLyrics.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricsLine> result = new ArrayList<>();
        String[] rawLines = plainLyrics.split("\\r?\\n");
        for (String rawLine : rawLines) {
            String text = rawLine.trim();
            if (!text.isEmpty()) {
                result.add(new LyricsLine(0L, 0L, text, Collections.emptyList()));
            }
        }
        return result;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static long fractionToMillis(String fraction) {
        if (fraction == null || fraction.isEmpty()) {
            return 0L;
        }
        String padded = (fraction + "000").substring(0, 3);
        return parseLong(padded);
    }
}
