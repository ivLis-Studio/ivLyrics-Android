package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pronunciation generated per sung unit of the original, the same as the desktop app's
 * PronunciationSlots.js and the iOS app.
 *
 * <p>Karaoke songs request their pronunciation already split over the units of each line: single
 * characters for Japanese, Chinese and Korean text, whole words for space-separated text, and
 * punctuation attached to the unit it touches. The line pronunciation is the joined slots, and
 * the part sung during each karaoke syllable is derived from them locally, so a timing change
 * never needs a new request.</p>
 */
final class PronunciationSlots {
    /** Bump when the unit split or the stored shape changes. */
    static final int VERSION = 1;
    /*
     * Reasoning models think long before writing unit slots and that time grows with the request,
     * so songs go out in small chunks that run in parallel.
     */
    static final int MAX_CHUNK_UNITS = 110;
    static final int MAX_CHUNK_ITEMS = 8;
    static final int PARALLEL_REQUESTS = 5;
    static final String REPAIR_NOTE = "Your previous answer for these lines had the wrong number of p strings or the wrong writing system. Count u again for every line: p must have exactly n strings.";

    private static final int SPACE = 0;
    private static final int CHAR = 1;
    private static final int WORD = 2;
    private static final int PUNCT = 3;

    private static final int[][] SYLLABIC_RANGES = {
            {0x1100, 0x11FF}, {0x2E80, 0x2FDF}, {0x3005, 0x3007}, {0x3021, 0x3029}, {0x3038, 0x303B},
            {0x3040, 0x30FF}, {0x3100, 0x312F}, {0x3130, 0x318F}, {0x31A0, 0x31BF}, {0x31F0, 0x31FF},
            {0x3400, 0x4DBF}, {0x4E00, 0x9FFF}, {0xA960, 0xA97F}, {0xAC00, 0xD7FF}, {0xF900, 0xFAFF},
            {0xFF66, 0xFF9F}, {0xFFA0, 0xFFDC}, {0x1B000, 0x1B16F}, {0x20000, 0x323AF}
    };

    private PronunciationSlots() {
    }

    /** One sung unit of a line; offsets are UTF-16 indices of the trimmed line. */
    static final class Unit {
        final String text;
        final int start;
        final int end;
        final boolean gapBefore;

        Unit(String text, int start, int end, boolean gapBefore) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.gapBefore = gapBefore;
        }
    }

    /** A line sent to AI: its request index and unit texts. */
    static final class Item {
        final int index;
        final List<String> units;

        Item(int index, List<String> units) {
            this.index = index;
            this.units = units;
        }
    }

    private static boolean isSpaceCodePoint(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    private static boolean isSoundCodePoint(int codePoint) {
        if (Character.isLetter(codePoint) || Character.isDigit(codePoint)) return true;
        int type = Character.getType(codePoint);
        return type == Character.LETTER_NUMBER || type == Character.OTHER_NUMBER;
    }

    private static boolean isSyllabicCodePoint(int codePoint) {
        for (int[] range : SYLLABIC_RANGES) {
            if (codePoint >= range[0] && codePoint <= range[1]) return true;
        }
        return false;
    }

    private static int kind(String grapheme) {
        boolean sound = false;
        boolean syllabic = false;
        boolean space = true;
        for (int offset = 0; offset < grapheme.length(); ) {
            int codePoint = grapheme.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!isSpaceCodePoint(codePoint)) space = false;
            if (isSoundCodePoint(codePoint)) sound = true;
            if (isSyllabicCodePoint(codePoint)) syllabic = true;
        }
        if (space) return SPACE;
        if (sound) return syllabic ? CHAR : WORD;
        return PUNCT;
    }

    static boolean hasSound(String text) {
        if (text == null) return false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (isSoundCodePoint(codePoint)) return true;
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    /** Trims Unicode whitespace (including the ideographic space). */
    static String trimSpaces(String text) {
        if (text == null) return "";
        int start = 0;
        int end = text.length();
        while (start < end) {
            int codePoint = text.codePointAt(start);
            if (!isSpaceCodePoint(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = text.codePointBefore(end);
            if (!isSpaceCodePoint(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return text.substring(start, end);
    }

    static String normalizeLine(String text) {
        return Normalizer.normalize(trimSpaces(text), Normalizer.Form.NFC);
    }

    private static boolean isWordJoiner(String grapheme) {
        return "'".equals(grapheme) || "\u2019".equals(grapheme);
    }

    private static final class Draft {
        int start;
        int end;
        final int kind;
        final boolean gapBefore;
        boolean closed;

        Draft(int start, int end, int kind, boolean gapBefore) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.gapBefore = gapBefore;
        }
    }

    /** Sung units of one line, in order. */
    static List<Unit> splitUnits(String text) {
        String source = text == null ? "" : text;
        List<String> graphemes = PronunciationFirst.graphemes(source);
        int[] starts = new int[graphemes.size()];
        int[] kinds = new int[graphemes.size()];
        int offset = 0;
        for (int index = 0; index < graphemes.size(); index++) {
            starts[index] = offset;
            kinds[index] = kind(graphemes.get(index));
            offset += graphemes.get(index).length();
        }
        List<Draft> units = new ArrayList<>();
        boolean gap = false;
        Draft pending = null;
        for (int index = 0; index < graphemes.size(); index++) {
            String grapheme = graphemes.get(index);
            int start = starts[index];
            int end = start + grapheme.length();
            int itemKind = kinds[index];
            Draft last = units.isEmpty() ? null : units.get(units.size() - 1);
            if (itemKind == SPACE) {
                gap = true;
                continue;
            }
            if (itemKind == PUNCT) {
                boolean free = last != null && !gap && pending == null;
                if (free && isWordJoiner(grapheme) && last.kind == WORD && !last.closed
                        && index + 1 < kinds.length && kinds[index + 1] == WORD) {
                    last.end = end;
                    continue;
                }
                if (free) {
                    last.end = end;
                    last.closed = true;
                    continue;
                }
                if (pending != null && !gap) {
                    pending.end = end;
                } else if (pending != null) {
                    units.add(new Draft(pending.start, pending.end, PUNCT, pending.gapBefore));
                    pending = new Draft(start, end, PUNCT, true);
                } else {
                    pending = new Draft(start, end, PUNCT, !units.isEmpty() && gap);
                }
                gap = false;
                continue;
            }
            if (itemKind == WORD && last != null && last.kind == WORD && !last.closed && !gap && pending == null) {
                last.end = end;
                continue;
            }
            if (pending != null && gap) {
                // Punctuation standing between spaces is a unit of its own.
                units.add(new Draft(pending.start, pending.end, PUNCT, pending.gapBefore));
                pending = null;
            }
            units.add(new Draft(
                    pending != null ? pending.start : start,
                    end,
                    itemKind,
                    pending != null ? pending.gapBefore : !units.isEmpty() && gap
            ));
            pending = null;
            gap = false;
        }
        if (pending != null) units.add(new Draft(pending.start, pending.end, PUNCT, pending.gapBefore));
        List<Unit> result = new ArrayList<>(units.size());
        for (Draft draft : units) {
            result.add(new Unit(source.substring(draft.start, draft.end), draft.start, draft.end, draft.gapBefore));
        }
        return result;
    }

    private static String cleanPart(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\u3000]+", " ");
    }

    /**
     * The line pronunciation of the slots: the original's spaces return between units, and a
     * slot ending with a space marks a word break.
     */
    static String join(List<Unit> units, List<String> parts) {
        StringBuilder output = new StringBuilder();
        boolean needSpace = false;
        for (int index = 0; units != null && index < units.size(); index++) {
            if (units.get(index).gapBefore) needSpace = true;
            String raw = cleanPart(parts != null && index < parts.size() ? parts.get(index) : "");
            String core = raw.trim();
            if (raw.startsWith(" ")) needSpace = true;
            if (core.isEmpty()) continue;
            if (needSpace && output.length() > 0) output.append(' ');
            output.append(core);
            needSpace = raw.endsWith(" ");
        }
        return output.toString();
    }

    /**
     * Pronunciation sung during each karaoke syllable of a line whose slots are known. A unit sung
     * over several syllables is spread over them. Null when the syllables do not split into the
     * units the slots were made for.
     */
    static List<String> deriveSyllableParts(List<String> syllableTexts, List<String> parts) {
        if (syllableTexts == null || syllableTexts.isEmpty() || parts == null) return null;
        StringBuilder joinedBuilder = new StringBuilder();
        List<int[]> ranges = new ArrayList<>(syllableTexts.size());
        for (String text : syllableTexts) {
            String value = text == null ? "" : text;
            ranges.add(new int[]{joinedBuilder.length(), joinedBuilder.length() + value.length()});
            joinedBuilder.append(value);
        }
        String joined = joinedBuilder.toString();
        String trimmed = trimSpaces(joined);
        int leading = trimmed.isEmpty() ? 0 : joined.indexOf(trimmed);
        List<Unit> units = splitUnits(trimmed);
        if (units.isEmpty() || units.size() != parts.size()) return null;
        List<StringBuilder> result = new ArrayList<>(syllableTexts.size());
        for (int index = 0; index < syllableTexts.size(); index++) result.add(new StringBuilder());
        int last = -1;
        boolean needSpace = false;
        for (int index = 0; index < units.size(); index++) {
            Unit unit = units.get(index);
            if (unit.gapBefore) needSpace = true;
            String raw = cleanPart(parts.get(index));
            String core = raw.trim();
            if (raw.startsWith(" ")) needSpace = true;
            if (core.isEmpty()) continue;
            if (needSpace && last >= 0 && !endsWithSpace(result.get(last))) result.get(last).append(' ');
            needSpace = raw.endsWith(" ");
            int start = leading + unit.start;
            int end = leading + unit.end;
            List<Integer> syllables = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            for (int syllable = 0; syllable < ranges.size(); syllable++) {
                int from = Math.max(start, ranges.get(syllable)[0]);
                int to = Math.min(end, ranges.get(syllable)[1]);
                if (to > from) {
                    syllables.add(syllable);
                    texts.add(joined.substring(from, to));
                }
            }
            if (syllables.isEmpty()) continue;
            if (syllables.size() == 1) {
                result.get(syllables.get(0)).append(core);
                last = Math.max(last, syllables.get(0));
                continue;
            }
            List<String> split = PronunciationFirst.distribute(texts, core);
            for (int position = 0; position < syllables.size(); position++) {
                String piece = position < split.size() ? split.get(position) : "";
                if (piece.isEmpty()) continue;
                result.get(syllables.get(position)).append(piece);
                last = Math.max(last, syllables.get(position));
            }
        }
        List<String> output = new ArrayList<>(result.size());
        boolean any = false;
        for (StringBuilder builder : result) {
            output.add(builder.toString());
            if (!builder.toString().trim().isEmpty()) any = true;
        }
        return any ? output : null;
    }

    private static boolean endsWithSpace(StringBuilder builder) {
        return builder.length() > 0 && builder.charAt(builder.length() - 1) == ' ';
    }

    // ---------------------------------------------------------------------
    // Request
    // ---------------------------------------------------------------------

    /** Lines with sound, as the request sends them. */
    static List<Item> items(List<String> texts) {
        List<Item> items = new ArrayList<>();
        for (int index = 0; texts != null && index < texts.size(); index++) {
            String text = texts.get(index);
            if (!hasSound(text)) continue;
            List<String> units = new ArrayList<>();
            for (Unit unit : splitUnits(normalizeLine(text))) units.add(unit.text);
            if (!units.isEmpty()) items.add(new Item(index, units));
        }
        return items;
    }

    static List<List<Item>> chunks(List<Item> items, int maxUnits, int maxItems) {
        List<List<Item>> chunks = new ArrayList<>();
        List<Item> current = new ArrayList<>();
        int units = 0;
        for (Item item : items) {
            if (!current.isEmpty() && (current.size() >= maxItems || units + item.units.size() > maxUnits)) {
                chunks.add(current);
                current = new ArrayList<>();
                units = 0;
            }
            current.add(item);
            units += item.units.size();
        }
        if (!current.isEmpty()) chunks.add(current);
        return chunks;
    }

    static List<List<Item>> chunks(List<Item> items) {
        return chunks(items, MAX_CHUNK_UNITS, MAX_CHUNK_ITEMS);
    }

    /** Compact items in a fixed key order ({"i","n","u"}), the same text as the other apps send. */
    static String itemsJson(List<Item> items) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            if (index > 0) json.append(',');
            json.append("{\"i\":").append(item.index).append(",\"n\":").append(item.units.size()).append(",\"u\":[");
            for (int unit = 0; unit < item.units.size(); unit++) {
                if (unit > 0) json.append(',');
                json.append(JSONObject.quote(item.units.get(unit)).replace("\\/", "/"));
            }
            json.append("]}");
        }
        return json.append(']').toString();
    }

    private static String examples(String notation, String lang, String outputScript) {
        String language = lang == null ? "" : lang.toLowerCase(Locale.ROOT);
        if (AiLyricsSettings.PRONUNCIATION_NOTATION_IPA.equals(notation)) {
            return "- 夢ならば: u=[\"夢\",\"な\",\"ら\",\"ば\"] → p=[\"jɯme \",\"na\",\"ɾa\",\"ba\"]\n"
                    + "- 今日も: u=[\"今\",\"日\",\"も\"] → p=[\"kʲoː\",\"\",\"mo\"]\n"
                    + "- hold me tight: u=[\"hold\",\"me\",\"tight\"] → p=[\"hoʊld\",\"mi\",\"taɪt\"]";
        }
        if (AiLyricsSettings.PRONUNCIATION_NOTATION_LATIN.equals(notation) || language.equals("en") || language.startsWith("en-")) {
            return "- 夢ならばどれほど: u=[\"夢\",\"な\",\"ら\",\"ば\",\"ど\",\"れ\",\"ほ\",\"ど\"] → p=[\"yume \",\"na\",\"ra\",\"ba \",\"do\",\"re\",\"ho\",\"do\"] (reads \"yume naraba dorehodo\")\n"
                    + "- 今日も: u=[\"今\",\"日\",\"も\"] → p=[\"kyou\",\"\",\"mo\"]\n"
                    + "- 走って: u=[\"走\",\"っ\",\"て\"] → p=[\"hashi\",\"t\",\"te\"]\n"
                    + "- 君を見てる: u=[\"君\",\"を\",\"見\",\"て\",\"る\"] → p=[\"kimi \",\"o \",\"mi\",\"te\",\"ru\"], never [\"ki\",\"mi\",\"o\",\"mi\",\"teru\"]\n"
                    + "- 사랑해 너를: u=[\"사\",\"랑\",\"해\",\"너\",\"를\"] → p=[\"sa\",\"rang\",\"hae\",\"neo\",\"reul\"] (reads \"saranghae neoreul\")";
        }
        if (language.equals("ko") || language.startsWith("ko-")) {
            return "- 夢ならばどれほど: u=[\"夢\",\"な\",\"ら\",\"ば\",\"ど\",\"れ\",\"ほ\",\"ど\"] → p=[\"유메\",\"나\",\"라\",\"바 \",\"도\",\"레\",\"호\",\"도\"] (reads \"유메나라바 도레호도\")\n"
                    + "- 今日も: u=[\"今\",\"日\",\"も\"] → p=[\"쿄오\",\"\",\"모\"]\n"
                    + "- 走って: u=[\"走\",\"っ\",\"て\"] → p=[\"하싯\",\"\",\"테\"]\n"
                    + "- がらんどう: u=[\"が\",\"ら\",\"ん\",\"ど\",\"う\"] → p=[\"가\",\"란\",\"\",\"도\",\"오\"], never a lone letter such as \"ㄴ\"\n"
                    + "- 君を見てる: u=[\"君\",\"を\",\"見\",\"て\",\"る\"] → p=[\"키미\",\"오 \",\"미\",\"테\",\"루\"], never [\"키\",\"미\",\"오\",\"미\",\"테루\"]\n"
                    + "- hold me tight: u=[\"hold\",\"me\",\"tight\"] → p=[\"홀드\",\"미\",\"타잇\"]";
        }
        return "- 夢ならば: u=[\"夢\",\"な\",\"ら\",\"ば\"] → 4 strings: the reading of 夢, then of な, ら and ば, all in " + outputScript + ".\n"
                + "- 今日も: u=[\"今\",\"日\",\"も\"] → the whole reading of 今日 on 今, \"\" on 日, then the reading of も.\n"
                + "- hold me tight: u=[\"hold\",\"me\",\"tight\"] → one pronunciation per word.";
    }

    /** The same request as the desktop app's buildPronunciationSlotPrompt, with this app's script policy. */
    static String prompt(
            List<Item> items,
            String lang,
            String sourceLang,
            String notation,
            String scriptInstruction,
            String outputScript,
            String audience
    ) {
        String source = sourceLang == null || sourceLang.trim().isEmpty() ? "auto" : sourceLang.trim();
        return "You are the pronunciation conversion system for ivLyrics karaoke.\n\n"
                + "Convert lyric sounds for " + audience + ", written ONLY in " + outputScript + ".\n"
                + scriptInstruction + "\n\n"
                + "TASK:\n"
                + "Each item is one lyric line split into its sung units, in order: i is the line index, u lists the units and n is how many there are. Japanese, Chinese and Korean text is split into single characters, space-separated text into whole words, and punctuation stays attached to a neighbouring unit.\n"
                + "Read the whole line first and decide its natural sung pronunciation in context. Then return p: exactly n strings, where p[k] is the part of that pronunciation sung on unit u[k].\n\n"
                + "RULES:\n"
                + "- Return ONLY valid JSON, no markdown or explanations. The first character must be { and the last must be }.\n"
                + "- Shape: {\"l\":[{\"i\":0,\"p\":[\"...\"]}]} with one entry per input item, keeping its i.\n"
                + "- p must contain exactly n strings in unit order. Never merge, drop, add, or reorder slots. Count u before answering: if n is 12, p has exactly 12 strings.\n"
                + "- Put each sound on the unit that produces it: a kanji/hanzi reading on that character, okurigana and particles on their own kana, a whole word's pronunciation on a word unit.\n"
                + "- Small kana such as ゃ ゅ ょ ぁ ぃ ぅ ぇ ぉ join the previous kana's sound: give them \"\". Put the sound of っ, ー and ん on their own unit when the output script can write it there; otherwise use \"\".\n"
                + "- When a reading cannot be divided between characters (for example 今日 or 大人), put the whole reading on the first character of that word and \"\" on the rest of it.\n"
                + "- Particles は, へ, を use their particle pronunciation when used as particles.\n"
                + "- Use \"\" for units without sound (punctuation and symbols).\n"
                + "- Never move part of one character's reading onto a neighbouring unit to fill slots: each reading stays on the character that produces it, even when that leaves \"\" elsewhere.\n"
                + "- Never write a lone Hangul letter (jamo such as ㄴ, ㅅ, ㅇ). A sound that is only a final consonant (ん, っ) joins the previous syllable block as its final consonant, and its own unit gets \"\".\n"
                + "- Spaces of the original line are restored automatically between units. Inside unspaced text (Japanese, Chinese) mark every word break yourself: end the last unit of each word or particle phrase with one space (usually after particles such as は, が, を, に, で, と, も, and between separate words), exactly where a natural pronunciation line would have a space. A long line must not come out as one unbroken run.\n"
                + "- Joining the p of a line must give one natural pronunciation of the whole line. This is pronunciation, not translation: never translate the meaning.\n\n"
                + "SLOT EXAMPLES:\n"
                + examples(notation, lang, outputScript) + "\n\n"
                + "Input source language: " + source + ".\n"
                + "Items:\n"
                + itemsJson(items);
    }

    private static boolean isIpaDisallowed(int codePoint) {
        if (isSyllabicCodePoint(codePoint)) return true;
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.CYRILLIC || script == Character.UnicodeScript.ARABIC
                || script == Character.UnicodeScript.DEVANAGARI || script == Character.UnicodeScript.BENGALI
                || script == Character.UnicodeScript.THAI;
    }

    /** Latin and IPA notations fix the output writing system. */
    static boolean isInNotationScript(String text, String notation) {
        boolean latin = AiLyricsSettings.PRONUNCIATION_NOTATION_LATIN.equals(notation);
        boolean ipa = AiLyricsSettings.PRONUNCIATION_NOTATION_IPA.equals(notation);
        if ((!latin && !ipa) || text == null) return true;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (latin && Character.isLetter(codePoint)
                    && Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN) return false;
            if (ipa && isIpaDisallowed(codePoint)) return false;
        }
        return true;
    }

    /** Lone Hangul letters are never a readable pronunciation (ん written as ㄴ). */
    static boolean hasLoneJamo(String text) {
        if (text == null) return false;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if ((codePoint >= 0x1100 && codePoint <= 0x11FF) || (codePoint >= 0x3131 && codePoint <= 0x318E)
                    || (codePoint >= 0xA960 && codePoint <= 0xA97F) || (codePoint >= 0xD7B0 && codePoint <= 0xD7FF)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Unit pronunciations by request index; lines with a wrong slot count, no sound, the wrong
     * writing system or a lone Hangul letter are left out (and asked again).
     */
    static Map<Integer, List<String>> parse(String raw, List<Item> items, String notation) {
        Map<Integer, List<String>> parsed = new LinkedHashMap<>();
        if (raw == null) return parsed;
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return parsed;
        try {
            JSONObject object = new JSONObject(raw.substring(start, end + 1));
            JSONArray rows = object.optJSONArray("l");
            if (rows == null) rows = object.optJSONArray("lines");
            if (rows == null) return parsed;
            Map<Integer, Item> expected = new HashMap<>();
            for (Item item : items) expected.put(item.index, item);
            for (int row = 0; row < rows.length(); row++) {
                JSONObject entry = rows.optJSONObject(row);
                if (entry == null) continue;
                int index = entry.has("i") ? entry.optInt("i", -1) : entry.optInt("index", -1);
                Item item = expected.get(index);
                JSONArray values = entry.optJSONArray("p");
                if (item == null || values == null || values.length() != item.units.size()) continue;
                List<String> parts = new ArrayList<>(values.length());
                boolean any = false;
                boolean loneJamo = false;
                StringBuilder joined = new StringBuilder();
                for (int position = 0; position < values.length(); position++) {
                    Object value = values.opt(position);
                    String part = value instanceof String ? (String) value : "";
                    parts.add(part);
                    joined.append(part);
                    if (!part.trim().isEmpty()) any = true;
                    if (hasLoneJamo(part)) loneJamo = true;
                }
                if (!any || loneJamo || !isInNotationScript(joined.toString(), notation)) continue;
                parsed.put(index, Collections.unmodifiableList(parts));
            }
        } catch (JSONException ignored) {
            parsed.clear();
        }
        return parsed;
    }

    // ---------------------------------------------------------------------
    // Stored slots
    // ---------------------------------------------------------------------

    /** Slots by line text for the requests that got them. */
    static Map<String, List<String>> stored(List<String> texts, Map<Integer, List<String>> slots) {
        Map<String, List<String>> lines = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<String>> entry : slots.entrySet()) {
            int index = entry.getKey();
            if (texts == null || index < 0 || index >= texts.size()) continue;
            String key = normalizeLine(texts.get(index));
            if (splitUnits(key).size() != entry.getValue().size()) continue;
            lines.put(key, entry.getValue());
        }
        return lines;
    }

    static String toJson(Map<String, List<String>> lines) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("v", VERSION);
        JSONObject byLine = new JSONObject();
        for (Map.Entry<String, List<String>> entry : lines.entrySet()) {
            byLine.put(entry.getKey(), new JSONArray(entry.getValue()));
        }
        object.put("lines", byLine);
        return object.toString();
    }

    /** Null for another version or a malformed value. */
    static Map<String, List<String>> fromJson(String raw) {
        try {
            JSONObject object = new JSONObject(raw);
            if (object.optInt("v", -1) != VERSION) return null;
            JSONObject byLine = object.getJSONObject("lines");
            Map<String, List<String>> lines = new LinkedHashMap<>();
            for (Iterator<String> keys = byLine.keys(); keys.hasNext(); ) {
                String key = keys.next();
                JSONArray values = byLine.getJSONArray(key);
                List<String> parts = new ArrayList<>(values.length());
                for (int index = 0; index < values.length(); index++) parts.add(values.optString(index, ""));
                lines.put(key, Collections.unmodifiableList(parts));
            }
            return lines;
        } catch (Exception ignored) {
            return null;
        }
    }
}
