package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LrcParser {
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\r?\\n");
    private static final Pattern LRC_TIME_PATTERN =
            Pattern.compile("\\[(\\d+):(\\d+)(?:[.,](\\d+))?\\](.*)");

    private LrcParser() {
    }

    static List<LyricsLine> parseSynced(String lrc, long durationMs) {
        if (lrc == null || lrc.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricsLine> result = new ArrayList<>();
        long previousStartMs = 0L;
        String previousText = null;
        String[] rawLines = LINE_BREAK_PATTERN.split(lrc);
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
            long startMs = Math.max(0L, (minutes * 60_000L) + (seconds * 1_000L) + millis);
            if (previousText != null) {
                long fallbackEnd = durationMs > previousStartMs
                        ? durationMs
                        : previousStartMs + 4_000L;
                long end = startMs > previousStartMs ? startMs : fallbackEnd;
                result.add(new LyricsLine(previousStartMs, end, previousText, Collections.emptyList()));
            }
            previousStartMs = startMs;
            previousText = text;
        }
        if (previousText != null) {
            long fallbackEnd = durationMs > previousStartMs
                    ? durationMs
                    : previousStartMs + 4_000L;
            result.add(new LyricsLine(previousStartMs, fallbackEnd, previousText, Collections.emptyList()));
        }
        return result;
    }

    static List<LyricsLine> parsePlain(String plainLyrics) {
        if (plainLyrics == null || plainLyrics.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricsLine> result = new ArrayList<>();
        String[] rawLines = LINE_BREAK_PATTERN.split(plainLyrics);
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
        int firstCharacter = fraction.charAt(0);
        boolean negative = firstCharacter == '-';
        int index = negative || firstCharacter == '+' ? 1 : 0;
        long millis = 0L;
        for (; index < 3; index++) {
            char character = index < fraction.length() ? fraction.charAt(index) : '0';
            int digit = character - '0';
            if (digit < 0 || digit > 9) {
                digit = Character.digit(character, 10);
                if (digit < 0) {
                    return 0L;
                }
            }
            millis = millis * 10L + digit;
        }
        return negative ? -millis : millis;
    }
}
