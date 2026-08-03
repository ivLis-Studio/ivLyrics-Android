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
                    return preserveJoining ? mergeWordRuns(syllables) : syllables;
                }
                List<LyricsLine.Syllable> normalized = new ArrayList<>(syllables.size());
                for (int index = 0; index < syllables.size(); index++) {
                    LyricsLine.Syllable syllable = syllables.get(index);
                    if (syllable != null) {
                        normalized.add(syllable);
                    }
                }
                return preserveJoining ? mergeWordRuns(normalized) : normalized;
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
        List<LyricsLine.Syllable> result = changed ? normalized : syllables;
        return preserveJoining ? mergeWordRuns(result) : result;
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

        for (LyricsLine.Syllable syllable : syllables) {
            if (syllable == null) {
                continue;
            }
            String text = syllable.text == null ? "" : syllable.text;
            if (text.isEmpty()) {
                continue;
            }
            if (isWhitespace(text)) {
                appendWord(result, word, wordStartMs, wordEndMs);
                result.add(syllable);
                continue;
            }
            if (word.length() == 0) {
                wordStartMs = syllable.startTimeMs;
                wordEndMs = syllable.endTimeMs;
            } else {
                wordEndMs = Math.max(wordEndMs, syllable.endTimeMs);
            }
            word.append(text);
        }
        appendWord(result, word, wordStartMs, wordEndMs);
        return result;
    }

    private static void appendWord(
            List<LyricsLine.Syllable> result,
            StringBuilder word,
            long startTimeMs,
            long endTimeMs
    ) {
        if (word.length() == 0) {
            return;
        }
        result.add(new LyricsLine.Syllable(word.toString(), startTimeMs, endTimeMs));
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
