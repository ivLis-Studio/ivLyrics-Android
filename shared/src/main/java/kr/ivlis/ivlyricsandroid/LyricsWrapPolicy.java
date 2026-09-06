package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure text-boundary rules shared by the lyrics renderer and local unit tests. */
final class LyricsWrapPolicy {
    private LyricsWrapPolicy() {
    }

    static List<String> splitWhitespaceRuns(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> runs = new ArrayList<>();
        int point = value.codePointAt(0);
        boolean whitespace = Character.isWhitespace(point);
        int runStart = 0;
        int offset = Character.charCount(point);
        while (offset < value.length()) {
            point = value.codePointAt(offset);
            boolean nextWhitespace = Character.isWhitespace(point);
            if (nextWhitespace != whitespace) {
                runs.add(value.substring(runStart, offset));
                runStart = offset;
                whitespace = nextWhitespace;
            }
            offset += Character.charCount(point);
        }
        runs.add(value.substring(runStart));
        return runs;
    }

    static List<Integer> safeBreakOffsets(String value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        int previous = value.codePointAt(0);
        int offset = Character.charCount(previous);
        if (offset >= value.length()) {
            return Collections.emptyList();
        }
        List<Integer> offsets = new ArrayList<>();
        int pointIndex = 1;
        while (offset < value.length()) {
            int current = value.codePointAt(offset);
            if (canBreakBetween(previous, current)) {
                offsets.add(pointIndex);
            }
            previous = current;
            offset += Character.charCount(current);
            pointIndex++;
        }
        return offsets;
    }

    static boolean canBreakBetween(String left, String right) {
        int leftPoint = lastCodePoint(left);
        int rightPoint = firstCodePoint(right);
        return leftPoint >= 0 && rightPoint >= 0 && canBreakBetween(leftPoint, rightPoint);
    }

    static boolean isSafeNoSpaceCjkBoundary(int left, int right) {
        return isNoSpaceCjkScript(left)
                && isNoSpaceCjkScript(right)
                && canBreakBetween(left, right);
    }

    private static boolean canBreakBetween(int left, int right) {
        if (Character.isWhitespace(left) || Character.isWhitespace(right)) {
            return true;
        }
        if (isJoiner(left) || isJoiner(right) || isCombining(right) || isNoSpaceContinuation(right)
                || isOpeningPunctuation(left) || isClosingPunctuation(right)) {
            return false;
        }
        boolean leftNoSpace = isNoSpaceScript(left);
        boolean rightNoSpace = isNoSpaceScript(right);
        if (leftNoSpace || rightNoSpace) {
            return true;
        }
        return isBreakPunctuation(left);
    }

    private static boolean isNoSpaceScript(int point) {
        Character.UnicodeScript script = Character.UnicodeScript.of(point);
        return isNoSpaceCjkScript(script)
                || script == Character.UnicodeScript.THAI
                || script == Character.UnicodeScript.LAO
                || script == Character.UnicodeScript.KHMER
                || script == Character.UnicodeScript.MYANMAR;
    }

    private static boolean isNoSpaceCjkScript(int point) {
        return point >= 0 && isNoSpaceCjkScript(Character.UnicodeScript.of(point));
    }

    private static boolean isNoSpaceCjkScript(Character.UnicodeScript script) {
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA;
    }

    private static boolean isCombining(int point) {
        int type = Character.getType(point);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || point == 0xFE0E
                || point == 0xFE0F;
    }

    private static boolean isJoiner(int point) {
        return point == 0x200D
                || point == '\''
                || point == '\u2019'
                || point == '-'
                || point == '\u2010'
                || point == '\u2011'
                || point == '_';
    }

    private static boolean isNoSpaceContinuation(int point) {
        return point == 0x30FC
                || point == 0x3005
                || (point >= 0x3041 && point <= 0x304A && (point & 1) == 1)
                || (point >= 0x3083 && point <= 0x3087 && (point & 1) == 1)
                || point == 0x3063
                || point == 0x308E
                || point == 0x3095
                || point == 0x3096
                || (point >= 0x30A1 && point <= 0x30AA && (point & 1) == 1)
                || (point >= 0x30E3 && point <= 0x30E7 && (point & 1) == 1)
                || point == 0x30C3
                || point == 0x30EE
                || point == 0x30F5
                || point == 0x30F6;
    }

    private static boolean isOpeningPunctuation(int point) {
        return "([{<\u3008\u300A\u300C\u300E\u3010\u3014\u3016\u3018\u301A\uFF08\uFF3B\uFF5B\u2018\u201C"
                .indexOf(point) >= 0;
    }

    private static boolean isClosingPunctuation(int point) {
        return ")]}>\u3001\u3002\u3009\u300B\u300D\u300F\u3011\u3015\u3017\u3019\u301B\uFF01\uFF09\uFF0C\uFF0E\uFF1A\uFF1B\uFF1F\uFF3D\uFF5D\u2019\u201D!?.,:;"
                .indexOf(point) >= 0;
    }

    private static boolean isBreakPunctuation(int point) {
        int type = Character.getType(point);
        return type == Character.DASH_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || isClosingPunctuation(point);
    }

    private static int firstCodePoint(String value) {
        return value == null || value.isEmpty() ? -1 : value.codePointAt(0);
    }

    private static int lastCodePoint(String value) {
        return value == null || value.isEmpty() ? -1 : value.codePointBefore(value.length());
    }
}
