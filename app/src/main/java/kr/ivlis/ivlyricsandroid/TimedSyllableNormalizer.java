package kr.ivlis.ivlyricsandroid;

import android.icu.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.RandomAccess;

/** Expands provider word/chunk timings into renderer-safe user-perceived characters. */
final class TimedSyllableNormalizer {
    private static final long PRE_WHITESPACE_MIN_DURATION_MS = 40L;
    private static final double PRE_WHITESPACE_NEXT_DURATION_RATIO = 0.35;
    private static final long PRE_WHITESPACE_MAX_DURATION_MS = 60L;

    private TimedSyllableNormalizer() {
    }

    static List<LyricsLine.Syllable> normalize(List<LyricsLine.Syllable> syllables) {
        if (syllables == null || syllables.isEmpty()) {
            return Collections.emptyList();
        }

        boolean preserveJoining = requiresContinuousShaping(syllables);

        if (syllables instanceof RandomAccess) {
            boolean containsNull = false;
            boolean allSingleGrapheme = true;
            for (int index = 0; index < syllables.size(); index++) {
                LyricsLine.Syllable syllable = syllables.get(index);
                if (syllable == null) {
                    containsNull = true;
                } else if (!isSingleGraphemeFast(syllable.text)) {
                    allSingleGrapheme = false;
                    break;
                }
            }
            if (allSingleGrapheme) {
                if (!containsNull) {
                    return finalizeTimings(syllables, preserveJoining);
                }
                List<LyricsLine.Syllable> normalized = new ArrayList<>(syllables.size());
                for (int index = 0; index < syllables.size(); index++) {
                    LyricsLine.Syllable syllable = syllables.get(index);
                    if (syllable != null) {
                        normalized.add(syllable);
                    }
                }
                return finalizeTimings(normalized, preserveJoining);
            }
        }

        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        List<LyricsLine.Syllable> normalized = new ArrayList<>(syllables.size());
        boolean changed = false;
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null) {
                changed = true;
                continue;
            }

            List<String> graphemes = splitGraphemes(syllable.text, iterator);
            if (graphemes.size() <= 1) {
                normalized.add(syllable);
                continue;
            }

            changed = true;
            long startTimeMs = syllable.startTimeMs;
            long durationMs = Math.max(0L, syllable.endTimeMs - startTimeMs);
            long wholeStepMs = durationMs / graphemes.size();
            long remainderMs = durationMs % graphemes.size();
            for (int index = 0; index < graphemes.size(); index++) {
                long partStartMs = interpolatedBoundary(
                        startTimeMs,
                        wholeStepMs,
                        remainderMs,
                        index
                );
                long partEndMs = interpolatedBoundary(
                        startTimeMs,
                        wholeStepMs,
                        remainderMs,
                        index + 1
                );
                normalized.add(syllable.copy(
                        graphemes.get(index),
                        partStartMs,
                        partEndMs,
                        syllable.sourceWordUnit
                ));
            }
        }
        List<LyricsLine.Syllable> result = changed ? normalized : syllables;
        return finalizeTimings(result, preserveJoining);
    }

    private static List<LyricsLine.Syllable> finalizeTimings(
            List<LyricsLine.Syllable> syllables,
            boolean preserveJoining
    ) {
        List<LyricsLine.Syllable> compensated = compensatePreWhitespaceTimings(syllables);
        return preserveJoining ? mergeWordRuns(compensated) : compensated;
    }

    private static List<LyricsLine.Syllable> compensatePreWhitespaceTimings(
            List<LyricsLine.Syllable> syllables
    ) {
        if (syllables == null || syllables.size() < 2) {
            return syllables == null ? Collections.emptyList() : syllables;
        }

        List<LyricsLine.Syllable> result = null;
        for (int index = 0; index < syllables.size() - 1; index++) {
            LyricsLine.Syllable current = syllables.get(index);
            LyricsLine.Syllable next = syllables.get(index + 1);
            if (current == null || next == null || isWhitespace(current.text) || !isWhitespace(next.text)) {
                continue;
            }

            long durationMs = Math.max(0L, current.endTimeMs - current.startTimeMs);
            if (durationMs >= PRE_WHITESPACE_MIN_DURATION_MS) {
                continue;
            }

            long nextDurationMs = Math.max(0L, next.endTimeMs - next.startTimeMs);
            long compensatedDurationMs = Math.max(
                    PRE_WHITESPACE_MIN_DURATION_MS,
                    Math.min(
                            PRE_WHITESPACE_MAX_DURATION_MS,
                            Math.round(nextDurationMs * PRE_WHITESPACE_NEXT_DURATION_RATIO)
                    )
            );
            if (result == null) {
                result = new ArrayList<>(syllables);
            }
            result.set(index, current.copy(
                    current.text,
                    current.startTimeMs,
                    current.startTimeMs + compensatedDurationMs,
                    current.sourceWordUnit
            ));
        }
        return result == null ? syllables : result;
    }

    static boolean requiresContinuousShaping(String text) {
        String value = text == null ? "" : text;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (isArabicScriptCodePoint(codePoint)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean requiresContinuousShaping(List<LyricsLine.Syllable> syllables) {
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable != null && requiresContinuousShaping(syllable.text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Arabic shaping and bidi resolution need the whole logical word. Keeping one
     * renderer item per grapheme makes every letter use its isolated form, so fold
     * adjacent non-whitespace timings back into words while retaining their time span.
     */
    private static List<LyricsLine.Syllable> mergeWordRuns(List<LyricsLine.Syllable> syllables) {
        if (syllables == null || syllables.isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricsLine.Syllable> result = new ArrayList<>(syllables.size());
        StringBuilder word = new StringBuilder();
        long wordStartMs = 0L;
        long wordEndMs = 0L;
        LyricsLine.Syllable wordStyle = null;

        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null) {
                continue;
            }
            String text = syllable.text == null ? "" : syllable.text;
            if (text.isEmpty()) {
                continue;
            }
            if (isWhitespace(text)) {
                appendWord(result, word, wordStartMs, wordEndMs, wordStyle);
                wordStyle = null;
                result.add(syllable);
                continue;
            }
            if (word.length() > 0 && wordStyle != null && !wordStyle.styleKey().equals(syllable.styleKey())) {
                appendWord(result, word, wordStartMs, wordEndMs, wordStyle);
                wordStyle = null;
            }
            if (word.length() == 0) {
                wordStartMs = syllable.startTimeMs;
                wordEndMs = syllable.endTimeMs;
                wordStyle = syllable;
            } else {
                wordEndMs = Math.max(wordEndMs, syllable.endTimeMs);
            }
            word.append(text);
        }
        appendWord(result, word, wordStartMs, wordEndMs, wordStyle);
        return result;
    }

    private static void appendWord(
            List<LyricsLine.Syllable> result,
            StringBuilder word,
            long startTimeMs,
            long endTimeMs,
            LyricsLine.Syllable style
    ) {
        if (word.length() == 0) {
            return;
        }
        result.add(style == null
                ? new LyricsLine.Syllable(word.toString(), startTimeMs, endTimeMs)
                : style.copy(word.toString(), startTimeMs, endTimeMs, style.sourceWordUnit));
        word.setLength(0);
    }

    private static boolean isWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (!Character.isWhitespace(codePoint) && codePoint != 0x00A0) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isArabicScriptCodePoint(int codePoint) {
        return (codePoint >= 0x0600 && codePoint <= 0x06FF)
                || (codePoint >= 0x0750 && codePoint <= 0x077F)
                || (codePoint >= 0x0870 && codePoint <= 0x089F)
                || (codePoint >= 0x08A0 && codePoint <= 0x08FF)
                || (codePoint >= 0xFB50 && codePoint <= 0xFDFF)
                || (codePoint >= 0xFE70 && codePoint <= 0xFEFF)
                || (codePoint >= 0x1EE00 && codePoint <= 0x1EEFF);
    }

    private static boolean isSingleGraphemeFast(String text) {
        if (text == null || text.length() <= 1) {
            return true;
        }
        return text.length() == 2 && Character.isSurrogatePair(text.charAt(0), text.charAt(1));
    }

    static List<String> splitGraphemes(String text) {
        return splitGraphemes(text, BreakIterator.getCharacterInstance(Locale.ROOT));
    }

    /** Native equivalent of Intl.Segmenter({ granularity: "word" }). */
    static List<LyricsLine.Syllable> groupForWordDisplay(
            List<LyricsLine.Syllable> syllables,
            String lyricsLocale
    ) {
        if (syllables == null || syllables.isEmpty()) {
            return Collections.emptyList();
        }
        if (shouldPreserveSourceWordUnits(syllables)) {
            return groupPreservedSourceWordUnits(syllables);
        }
        List<LyricsLine.Syllable> normalized = normalize(syllables);
        StringBuilder textBuilder = new StringBuilder();
        List<LyricsLine.Syllable> source = new ArrayList<>(normalized.size());
        List<Integer> starts = new ArrayList<>(normalized.size());
        List<Integer> ends = new ArrayList<>(normalized.size());
        for (LyricsLine.Syllable syllable : normalized) {
            if (syllable == null || syllable.text == null || syllable.text.isEmpty()) {
                continue;
            }
            starts.add(textBuilder.length());
            textBuilder.append(syllable.text);
            ends.add(textBuilder.length());
            source.add(syllable);
        }
        String text = textBuilder.toString();
        if (text.isEmpty()) {
            return Collections.emptyList();
        }

        List<LyricsWordSegmenter.Range> displayRanges = LyricsWordSegmenter.displayRanges(text, lyricsLocale);
        List<LyricsLine.Syllable> result = new ArrayList<>();
        for (LyricsWordSegmenter.Range displayRange : displayRanges) {
            int rangeStart = displayRange.start;
            int rangeEnd = displayRange.end;
            if (rangeEnd <= rangeStart) {
                continue;
            }
            long startTimeMs = Long.MAX_VALUE;
            long endTimeMs = Long.MIN_VALUE;
            for (int index = 0; index < source.size(); index++) {
                if (ends.get(index) <= rangeStart || starts.get(index) >= rangeEnd) {
                    continue;
                }
                LyricsLine.Syllable syllable = source.get(index);
                startTimeMs = Math.min(startTimeMs, syllable.startTimeMs);
                endTimeMs = Math.max(endTimeMs, syllable.endTimeMs);
            }
            if (startTimeMs == Long.MAX_VALUE) {
                startTimeMs = 0L;
                endTimeMs = 0L;
            }
            LyricsLine.Syllable pendingStyle = null;
            StringBuilder pendingText = new StringBuilder();
            for (int index = 0; index < source.size(); index++) {
                int overlapStart = Math.max(rangeStart, starts.get(index));
                int overlapEnd = Math.min(rangeEnd, ends.get(index));
                if (overlapEnd <= overlapStart) continue;
                LyricsLine.Syllable syllable = source.get(index);
                if (pendingStyle != null && !pendingStyle.styleKey().equals(syllable.styleKey())) {
                    result.add(pendingStyle.copy(
                            pendingText.toString(), startTimeMs, Math.max(startTimeMs, endTimeMs), true
                    ));
                    pendingText.setLength(0);
                    pendingStyle = null;
                }
                if (pendingStyle == null) pendingStyle = syllable;
                pendingText.append(text, overlapStart, overlapEnd);
            }
            if (pendingStyle != null && pendingText.length() > 0) {
                result.add(pendingStyle.copy(
                        pendingText.toString(), startTimeMs, Math.max(startTimeMs, endTimeMs), true
                ));
            }
        }
        return result.isEmpty() ? syllables : result;
    }

    private static boolean shouldPreserveSourceWordUnits(List<LyricsLine.Syllable> syllables) {
        int visibleUnits = 0;
        boolean hasMultiGraphemeUnit = false;
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null || syllable.text == null || syllable.text.isEmpty()
                    || isWhitespace(syllable.text)) {
                continue;
            }
            if (syllable.sourceWordUnit) {
                return true;
            }
            visibleUnits++;
            if (!hasMultiGraphemeUnit && splitGraphemes(syllable.text).size() > 1) {
                hasMultiGraphemeUnit = true;
            }
        }
        // Migrates cached word-sync data written before sourceWordUnit was persisted.
        return visibleUnits > 1 && hasMultiGraphemeUnit;
    }

    private static List<LyricsLine.Syllable> groupPreservedSourceWordUnits(
            List<LyricsLine.Syllable> syllables
    ) {
        List<LyricsLine.Syllable> result = new ArrayList<>();
        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null || syllable.text == null || syllable.text.isEmpty()) {
                continue;
            }
            StringBuilder run = new StringBuilder();
            Boolean whitespaceRun = null;
            for (String grapheme : splitGraphemes(syllable.text)) {
                boolean whitespace = isWhitespace(grapheme);
                if (whitespaceRun != null && whitespaceRun != whitespace) {
                    result.add(syllable.copy(
                            run.toString(),
                            syllable.startTimeMs,
                            syllable.endTimeMs,
                            true
                    ));
                    run.setLength(0);
                }
                whitespaceRun = whitespace;
                run.append(grapheme);
            }
            if (run.length() > 0) {
                result.add(syllable.copy(
                        run.toString(),
                        syllable.startTimeMs,
                        syllable.endTimeMs,
                        true
                ));
            }
        }
        return result.isEmpty() ? syllables : result;
    }

    private static List<String> splitGraphemes(String text, BreakIterator iterator) {
        String value = text == null ? "" : text;
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        iterator.setText(value);
        List<String> result = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            result.add(value.substring(start, end));
        }
        return result;
    }

    private static long interpolatedBoundary(
            long startTimeMs,
            long wholeStepMs,
            long remainderMs,
            int index
    ) {
        return startTimeMs
                + wholeStepMs * index
                + Math.min((long) index, remainderMs);
    }
}
