package kr.ivlis.ivlyricsandroid;

import java.text.Normalizer;
import java.util.Locale;

final class LyricsTextComparison {
    private LyricsTextComparison() {
    }

    static boolean areEquivalent(String leftValue, String rightValue) {
        String left = normalize(leftValue);
        String right = normalize(rightValue);
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        if (left.equals(right)) {
            return true;
        }
        return withoutSpaces(left).equals(withoutSpaces(right));
    }

    static String normalize(String value) {
        String input = value == null ? "" : value;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isApostropheLike(codePoint)) {
                codePoint = '\'';
            }
            int type = Character.getType(codePoint);
            if (isDiscardedType(type)) {
                continue;
            }
            if (Character.isWhitespace(codePoint)
                    || type == Character.SPACE_SEPARATOR
                    || type == Character.LINE_SEPARATOR
                    || type == Character.PARAGRAPH_SEPARATOR) {
                pendingSpace = builder.length() > 0;
                continue;
            }
            if (pendingSpace) {
                builder.append(' ');
                pendingSpace = false;
            }
            builder.appendCodePoint(codePoint);
        }
        return builder.toString();
    }

    private static String withoutSpaces(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isWhitespace(codePoint)
                    && Character.getType(codePoint) != Character.SPACE_SEPARATOR
                    && Character.getType(codePoint) != Character.LINE_SEPARATOR
                    && Character.getType(codePoint) != Character.PARAGRAPH_SEPARATOR) {
                builder.appendCodePoint(codePoint);
            }
        }
        return builder.toString();
    }

    private static boolean isDiscardedType(int type) {
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION
                || type == Character.MATH_SYMBOL
                || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL
                || type == Character.OTHER_SYMBOL
                || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT;
    }

    private static boolean isApostropheLike(int codePoint) {
        switch (codePoint) {
            case 0x0060:
            case 0x00B4:
            case 0x02B9:
            case 0x02BB:
            case 0x02BC:
            case 0x02BE:
            case 0x02BF:
            case 0x055A:
            case 0x07F4:
            case 0x07F5:
            case 0x2018:
            case 0x2019:
            case 0x201B:
            case 0x2032:
            case 0x275B:
            case 0x275C:
            case 0xFF07:
                return true;
            default:
                return false;
        }
    }
}
