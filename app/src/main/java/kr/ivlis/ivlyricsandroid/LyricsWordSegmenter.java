package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.icu.text.BreakIterator;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Locale-aware lyric word segmentation shared by timed and synthetic karaoke rendering. */
final class LyricsWordSegmenter {
    private static final String TAG = "LyricsWordSegmenter";

    static final class Range {
        final int start;
        final int end;

        Range(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final Set<String> JA_PARTICLES = setOf(
            "は", "が", "を", "に", "へ", "で", "と", "の", "も", "や", "か", "ね", "よ", "ぞ", "ぜ",
            "から", "まで", "だけ", "しか", "ほど", "くらい", "ぐらい", "など", "こそ", "とも", "な"
    );
    private static final Set<String> JA_SAFE_SUFFIXES = setOf(
            "た", "て", "ば", "ぬ", "って", "った", "いて", "いで", "んで",
            "てる", "でる", "いてる", "えてる", "たい", "ない", "れば"
    );
    private static final Set<String> ZH_PROTECTED = setOf(
            "我们", "你们", "他们", "她们", "它们", "这个", "那个", "这些", "那些", "这里", "那里",
            "这样", "那样", "这么", "那么", "真的", "的话", "为了", "除了", "只有", "就是", "没有",
            "一下", "一起", "已经", "非常", "特别", "重新", "超级", "无法", "第一次", "经过", "难过",
            "结果", "如果", "最后"
    );
    private static final List<String> ZH_PRONOUNS = Arrays.asList(
            "我们", "你们", "他们", "她们", "它们", "我", "你", "他", "她", "它"
    );
    private static final Set<String> ZH_LEFT_ATOMS = setOf("不", "没", "很", "也", "都");
    private static final Set<String> ZH_LOCALIZERS = setOf("上", "下", "里", "中", "前", "后", "内", "外");
    private static final List<String> ZH_MULTI_PREFIXES = Collections.singletonList("一起");
    private static volatile TokenizerAdapter japaneseTokenizer;

    interface TokenizerAdapter {
        List<Token> tokenize(String text, Locale locale);
    }

    static final class Token {
        final String surface;
        final int start;
        final int end;
        final String pos;
        final String posDetail;
        final String lemma;
        final String conjugation;

        Token(String surface, int start, int end, String pos, String posDetail, String lemma, String conjugation) {
            this.surface = surface;
            this.start = start;
            this.end = end;
            this.pos = pos;
            this.posDetail = posDetail;
            this.lemma = lemma;
            this.conjugation = conjugation;
        }
    }

    private LyricsWordSegmenter() {
    }

    static void initialize(Context context) {
        if (japaneseTokenizer != null) return;
        try {
            TinySegmenter segmenter = TinySegmenter.fromAssets(context.getApplicationContext());
            japaneseTokenizer = (text, locale) -> tokenRecords(segmenter.segment(text), text);
        } catch (Exception error) {
            Log.w(TAG, "TinySegmenter model could not be loaded; ICU fallback remains active", error);
        }
    }

    static void setJapaneseTokenizer(TokenizerAdapter tokenizer) {
        japaneseTokenizer = tokenizer;
    }

    static List<Range> displayRanges(String text, String localeCode) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<Range> lexicalRanges = new ArrayList<>();
        int cursor = 0;
        for (String token : segment(text, localeCode)) {
            int start = text.indexOf(token, cursor);
            if (start < 0) {
                return fallbackDisplayRanges(text, localeCode);
            }
            lexicalRanges.add(new Range(start, start + token.length()));
            cursor = start + token.length();
        }
        if (lexicalRanges.isEmpty()) {
            return fallbackDisplayRanges(text, localeCode);
        }

        List<Range> result = new ArrayList<>();
        cursor = 0;
        for (Range range : lexicalRanges) {
            appendGapRanges(result, text, cursor, range.start);
            result.add(range);
            cursor = range.end;
        }
        appendGapRanges(result, text, cursor, text.length());
        return result;
    }

    static List<String> segment(String text, String localeCode) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        String resolvedLocale = normalizeLocale(localeCode, text);
        Locale locale = localeFor(resolvedLocale);
        List<String> graphemes = graphemes(text, locale);
        SegmentationState state = new SegmentationState(resolvedLocale, locale);

        for (int index = 0; index < graphemes.size(); index++) {
            String value = graphemes.get(index);
            String previous = index > 0 ? graphemes.get(index - 1) : null;
            String next = index + 1 < graphemes.size() ? graphemes.get(index + 1) : null;
            if (isLatinJoiner(value, previous, next)) {
                state.lexical.append(value);
            } else if (isWhitespace(value)) {
                state.flush();
            } else if (isOpeningPunctuation(value)) {
                state.flush();
                state.pendingPrefix.append(value);
            } else if (isPunctuation(value)) {
                state.flush();
                if (!state.output.isEmpty()) {
                    int lastIndex = state.output.size() - 1;
                    state.output.set(lastIndex, state.output.get(lastIndex) + value);
                } else {
                    state.pendingPrefix.append(value);
                }
            } else if (isSymbol(value)) {
                state.flush();
                state.output.add(state.pendingPrefix.length() > 0 ? state.pendingPrefix + value : value);
                state.pendingPrefix.setLength(0);
            } else {
                state.lexical.append(value);
            }
        }
        state.flush();
        if (state.pendingPrefix.length() > 0) {
            if (state.output.isEmpty()) {
                state.output.add(state.pendingPrefix.toString());
            } else {
                int lastIndex = state.output.size() - 1;
                state.output.set(lastIndex, state.output.get(lastIndex) + state.pendingPrefix);
            }
        }
        return state.output;
    }

    private static final class SegmentationState {
        final String localeCode;
        final Locale locale;
        final List<String> output = new ArrayList<>();
        final StringBuilder lexical = new StringBuilder();
        final StringBuilder pendingPrefix = new StringBuilder();

        SegmentationState(String localeCode, Locale locale) {
            this.localeCode = localeCode;
            this.locale = locale;
        }

        void flush() {
            if (lexical.length() == 0) {
                return;
            }
            List<String> tokens = segmentLexicalRun(
                    lexical.toString(),
                    localeCode,
                    locale
            );
            if (pendingPrefix.length() > 0 && !tokens.isEmpty()) {
                tokens.set(0, pendingPrefix + tokens.get(0));
                pendingPrefix.setLength(0);
            }
            for (String token : tokens) {
                if (token != null && !token.isEmpty()) {
                    output.add(token);
                }
            }
            lexical.setLength(0);
        }
    }

    private static List<String> segmentLexicalRun(
            String run,
            String localeCode,
            Locale locale
    ) {
        String language = baseLanguage(localeCode);
        if ("ja".equals(language)) {
            return segmentJapaneseRun(run, locale);
        }
        if ("zh".equals(language)) {
            List<String> result = new ArrayList<>();
            for (String word : intlWords(run, locale)) {
                result.addAll(splitChineseToken(word));
            }
            return result;
        }
        List<String> words = intlWords(run, locale);
        return words.isEmpty() ? new ArrayList<>(Collections.singletonList(run)) : words;
    }

    private static List<String> segmentJapaneseRun(String run, Locale locale) {
        List<String> output = new ArrayList<>();
        List<String> pieces = new ArrayList<>();
        List<String> pieceKinds = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String kind = null;
        for (String grapheme : graphemes(run, locale)) {
            String nextKind = isKatakana(grapheme) ? "katakana" : isLatinNumber(grapheme) ? "latin" : "japanese";
            if (kind != null && !kind.equals(nextKind)) {
                pieces.add(buffer.toString());
                pieceKinds.add(kind);
                buffer.setLength(0);
            }
            kind = nextKind;
            buffer.append(grapheme);
        }
        if (buffer.length() > 0) {
            pieces.add(buffer.toString());
            pieceKinds.add(kind);
        }
        for (int index = 0; index < pieces.size(); index++) {
            String piece = pieces.get(index);
            String pieceKind = pieceKinds.get(index);
            if ("latin".equals(pieceKind)) {
                output.add(piece);
                continue;
            }
            List<Token> tokens = tokenizeJapanese(piece, locale);
            if ("japanese".equals(pieceKind)) {
                output.addAll(groupJapaneseTokens(tokens));
            } else {
                for (Token token : tokens) output.add(token.surface);
            }
        }
        return output;
    }

    private static List<Token> tokenizeJapanese(String text, Locale locale) {
        TokenizerAdapter tokenizer = japaneseTokenizer;
        if (tokenizer != null) {
            try {
                List<Token> tokens = tokenizer.tokenize(text, locale);
                if (tokens != null && !tokens.isEmpty()) return tokens;
            } catch (RuntimeException error) {
                Log.w(TAG, "Japanese tokenizer failed; using ICU fallback", error);
            }
        }
        return tokenRecords(intlWords(text, locale), text);
    }

    private static List<Token> tokenRecords(List<String> surfaces, String text) {
        List<Token> output = new ArrayList<>();
        int cursor = 0;
        for (String surface : surfaces) {
            if (surface == null || surface.isEmpty()) continue;
            int start = text.indexOf(surface, cursor);
            if (start < 0) return Collections.emptyList();
            int end = start + surface.length();
            output.add(new Token(surface, start, end, null, null, null, null));
            cursor = end;
        }
        return output;
    }

    private static List<String> groupJapaneseTokens(List<Token> tokens) {
        List<String> output = new ArrayList<>();
        for (Token tokenRecord : tokens) {
            String token = tokenRecord.surface;
            if (output.isEmpty()) {
                output.add(token);
                continue;
            }
            int lastIndex = output.size() - 1;
            String previous = output.get(lastIndex);
            boolean previousIsParticle = JA_PARTICLES.contains(previous);
            boolean safeSuffix = JA_SAFE_SUFFIXES.contains(token);
            boolean contextualSou = "そう".equals(token)
                    && endsWithScript(previous, Character.UnicodeScript.HIRAGANA);
            String morphology = ((tokenRecord.pos == null ? "" : tokenRecord.pos) + " "
                    + (tokenRecord.posDetail == null ? "" : tokenRecord.posDetail)).toLowerCase(Locale.ROOT);
            boolean morphologicalSuffix = morphology.contains("auxiliary") || morphology.contains("suffix")
                    || morphology.contains("conjunctive") || morphology.contains("助動詞")
                    || morphology.contains("接続助詞") || morphology.contains("接尾");
            if (!previousIsParticle && (morphologicalSuffix
                    || (isHiragana(token) && (safeSuffix || contextualSou)))) {
                output.set(lastIndex, previous + token);
            } else {
                output.add(token);
            }
        }
        return output;
    }

    private static List<String> splitChineseToken(String token) {
        if (token == null || token.isEmpty() || token.codePointCount(0, token.length()) <= 1 || ZH_PROTECTED.contains(token)) {
            return token == null || token.isEmpty() ? Collections.emptyList() : new ArrayList<>(Collections.singletonList(token));
        }
        List<String> chars = codePoints(token);
        if (chars.size() >= 2 && allEqual(chars) && containsScript(chars.get(0), Character.UnicodeScript.HAN)) {
            return chars;
        }
        for (String pronoun : ZH_PRONOUNS) {
            if (token.startsWith(pronoun) && !token.equals(pronoun)) {
                List<String> result = new ArrayList<>();
                result.add(pronoun);
                result.addAll(splitChineseToken(token.substring(pronoun.length())));
                return result;
            }
        }
        for (String prefix : ZH_MULTI_PREFIXES) {
            if (token.startsWith(prefix) && !token.equals(prefix)) {
                List<String> result = new ArrayList<>();
                result.add(prefix);
                result.addAll(splitChineseToken(token.substring(prefix.length())));
                return result;
            }
        }
        String first = chars.get(0);
        String last = chars.get(chars.size() - 1);
        if (ZH_LEFT_ATOMS.contains(first)) {
            List<String> result = new ArrayList<>();
            result.add(first);
            result.addAll(splitChineseToken(join(chars, 1, chars.size())));
            return result;
        }
        for (int index = 1; index < chars.size() - 1; index++) {
            if ("了".equals(chars.get(index)) || "着".equals(chars.get(index)) || "过".equals(chars.get(index))) {
                List<String> result = new ArrayList<>();
                result.addAll(splitChineseToken(join(chars, 0, index)));
                result.add(chars.get(index));
                result.addAll(splitChineseToken(join(chars, index + 1, chars.size())));
                return result;
            }
        }
        if ("了".equals(last)) {
            List<String> result = new ArrayList<>(splitChineseToken(join(chars, 0, chars.size() - 1)));
            result.add(last);
            return result;
        }
        for (String pronoun : ZH_PRONOUNS) {
            if (token.endsWith(pronoun) && !token.equals(pronoun)) {
                List<String> result = new ArrayList<>(splitChineseToken(token.substring(0, token.length() - pronoun.length())));
                result.add(pronoun);
                return result;
            }
        }
        if ("的".equals(last)) {
            String stem = join(chars, 0, chars.size() - 1);
            if (ZH_PRONOUNS.contains(stem)) {
                return new ArrayList<>(Arrays.asList(stem, last));
            }
        }
        if (ZH_LOCALIZERS.contains(last) && chars.size() >= 3) {
            List<String> result = new ArrayList<>(splitChineseToken(join(chars, 0, chars.size() - 1)));
            result.add(last);
            return result;
        }
        return new ArrayList<>(Collections.singletonList(token));
    }

    private static List<String> intlWords(String text, Locale locale) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        BreakIterator iterator = BreakIterator.getWordInstance(locale);
        iterator.setText(text);
        List<String> result = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            if (end > start) {
                String token = text.substring(start, end);
                if (isWordLike(token)) {
                    result.add(token);
                }
            }
        }
        return result;
    }

    private static List<String> graphemes(String text, Locale locale) {
        BreakIterator iterator = BreakIterator.getCharacterInstance(locale);
        iterator.setText(text);
        List<String> result = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            if (end > start) {
                result.add(text.substring(start, end));
            }
        }
        return result;
    }

    private static int graphemeCount(String text, Locale locale) {
        return graphemes(text, locale).size();
    }

    private static void appendGapRanges(List<Range> output, String text, int start, int end) {
        int cursor = start;
        while (cursor < end) {
            int codePoint = text.codePointAt(cursor);
            int next = cursor + Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                while (next < end) {
                    int candidate = text.codePointAt(next);
                    if (!Character.isWhitespace(candidate)) {
                        break;
                    }
                    next += Character.charCount(candidate);
                }
            }
            output.add(new Range(cursor, next));
            cursor = next;
        }
    }

    private static List<Range> fallbackDisplayRanges(String text, String localeCode) {
        BreakIterator iterator = BreakIterator.getWordInstance(localeFor(normalizeLocale(localeCode, text)));
        iterator.setText(text);
        List<Range> result = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            if (end > start) {
                result.add(new Range(start, end));
            }
        }
        return result.isEmpty() ? Collections.singletonList(new Range(0, text.length())) : result;
    }

    private static String normalizeLocale(String localeCode, String text) {
        String explicit = localeCode == null ? "" : localeCode.trim().replace('_', '-');
        if (!explicit.isEmpty() && !"auto".equalsIgnoreCase(explicit)) {
            return explicit;
        }
        boolean hasHan = false;
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA) return "ja";
            if (script == Character.UnicodeScript.THAI) return "th";
            if (script == Character.UnicodeScript.LAO) return "lo";
            if (script == Character.UnicodeScript.KHMER) return "km";
            if (script == Character.UnicodeScript.MYANMAR) return "my";
            if (script == Character.UnicodeScript.HAN) hasHan = true;
            offset += Character.charCount(codePoint);
        }
        return hasHan ? "zh" : Locale.getDefault().toLanguageTag();
    }

    private static Locale localeFor(String localeCode) {
        Locale locale = Locale.forLanguageTag(localeCode == null ? "" : localeCode.replace('_', '-'));
        return locale.getLanguage().isEmpty() ? Locale.ENGLISH : locale;
    }

    private static String baseLanguage(String localeCode) {
        String value = localeCode == null ? "" : localeCode.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int separator = value.indexOf('-');
        return separator < 0 ? value : value.substring(0, separator);
    }

    private static boolean isHiragana(String value) {
        return allInScript(value, Character.UnicodeScript.HIRAGANA);
    }

    private static boolean isKatakana(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.KATAKANA
                    && codePoint != 'ー' && codePoint != 'ヽ' && codePoint != 'ヾ'
                    && type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isLatinNumber(String value) {
        if (value == null || value.isEmpty()) return false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isDigit(codePoint) && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean isLatinJoiner(String value, String previous, String next) {
        return ("'".equals(value) || "’".equals(value) || "-".equals(value) || "‐".equals(value))
                && isLatinNumber(previous) && isLatinNumber(next);
    }

    private static boolean isWhitespace(String value) {
        return value != null && !value.isEmpty() && value.codePoints().allMatch(Character::isWhitespace);
    }

    private static boolean isOpeningPunctuation(String value) {
        if (value == null || value.isEmpty()) return false;
        int type = Character.getType(value.codePointAt(0));
        return type == Character.START_PUNCTUATION || type == Character.INITIAL_QUOTE_PUNCTUATION;
    }

    private static boolean isPunctuation(String value) {
        if (value == null || value.isEmpty()) return false;
        int type = Character.getType(value.codePointAt(0));
        return type == Character.CONNECTOR_PUNCTUATION || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    private static boolean isSymbol(String value) {
        if (value == null || value.isEmpty()) return false;
        int type = Character.getType(value.codePointAt(0));
        return type == Character.MATH_SYMBOL || type == Character.CURRENCY_SYMBOL
                || type == Character.MODIFIER_SYMBOL || type == Character.OTHER_SYMBOL;
    }

    private static boolean isWordLike(String value) {
        return value != null && value.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static boolean allInScript(String value, Character.UnicodeScript script) {
        if (value == null || value.isEmpty()) return false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int type = Character.getType(codePoint);
            if (Character.UnicodeScript.of(codePoint) != script
                    && type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static boolean containsScript(String value, Character.UnicodeScript script) {
        if (value == null) return false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.UnicodeScript.of(codePoint) == script) return true;
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean endsWithScript(String value, Character.UnicodeScript script) {
        return value != null && !value.isEmpty()
                && Character.UnicodeScript.of(value.codePointBefore(value.length())) == script;
    }

    private static List<String> codePoints(String value) {
        List<String> result = new ArrayList<>();
        value.codePoints().forEach(codePoint -> result.add(new String(Character.toChars(codePoint))));
        return result;
    }

    private static boolean allEqual(List<String> values) {
        if (values.isEmpty()) return false;
        String first = values.get(0);
        for (String value : values) {
            if (!first.equals(value)) return false;
        }
        return true;
    }

    private static String join(List<String> values, int start, int end) {
        StringBuilder result = new StringBuilder();
        for (int index = start; index < end; index++) result.append(values.get(index));
        return result.toString();
    }

    @SafeVarargs
    private static <T> Set<T> setOf(T... values) {
        return new HashSet<>(Arrays.asList(values));
    }
}
