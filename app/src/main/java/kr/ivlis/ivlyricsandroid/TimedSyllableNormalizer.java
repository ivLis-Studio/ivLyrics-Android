package kr.ivlis.ivlyricsandroid;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.RandomAccess;

/** Expands provider word/chunk timings into renderer-safe user-perceived characters. */
final class TimedSyllableNormalizer {
    private TimedSyllableNormalizer() {
    }

    static List<LyricsLine.Syllable> normalize(List<LyricsLine.Syllable> syllables) {
        if (syllables == null || syllables.isEmpty()) {
            return Collections.emptyList();
        }

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
                    return syllables;
                }
                List<LyricsLine.Syllable> normalized = new ArrayList<>(syllables.size());
                for (int index = 0; index < syllables.size(); index++) {
                    LyricsLine.Syllable syllable = syllables.get(index);
                    if (syllable != null) {
                        normalized.add(syllable);
                    }
                }
                return normalized;
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
                normalized.add(new LyricsLine.Syllable(
                        graphemes.get(index),
                        partStartMs,
                        partEndMs
                ));
            }
        }
        return changed ? normalized : syllables;
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
