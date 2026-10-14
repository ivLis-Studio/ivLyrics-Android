package kr.ivlis.ivlyricsandroid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lyric translation with the on-device model, identical to the desktop provider
 * (ivLyrics/Addon_AI_LocalModel.js): simultaneous vocal parts are translated separately, blank and
 * [marker] parts are kept, each unique part is translated once in first-appearance order, and a block
 * that loses or merges a line is translated again one part at a time.
 */
final class LocalAiTranslator {
    static final int BLOCK_MAX_PARTS = 8;
    static final int BLOCK_MAX_CHARACTERS = 480;
    static final int MAX_OUTPUT_TOKENS = 1536;
    static final String PART_SEPARATOR = " / ";
    private static final Map<String, String> LANGUAGE_NAMES = languageNames();

    interface Engine {
        Completion complete(String prompt, int maxTokens) throws Exception;
    }

    interface LineListener {
        void onLine(int row, String text);
    }

    static final class Completion {
        final String text;
        final boolean stoppedAtTokenLimit;

        Completion(String text, boolean stoppedAtTokenLimit) {
            this.text = text == null ? "" : text;
            this.stoppedAtTokenLimit = stoppedAtTokenLimit;
        }
    }

    private LocalAiTranslator() {
    }

    /** English language name used in the prompt, or null when the model cannot translate into it. */
    static String languageName(String languageCode) {
        String code = languageCode == null || languageCode.trim().isEmpty()
                ? "en"
                : languageCode.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        String name = LANGUAGE_NAMES.get(code);
        int dash = code.indexOf('-');
        if (name == null && dash > 0) {
            name = LANGUAGE_NAMES.get(code.substring(0, dash));
        }
        return name;
    }

    static String blockPrompt(List<String> parts, String language) {
        return "Please accurately translate the following text into " + language + ".\n"
                + "You must retain the exact same number of delimiters in the translation. Strictly do not omit, "
                + "escape, or translate these symbols, and pay close attention to their placement.\n\n"
                + String.join("\n", parts);
    }

    static String linePrompt(String part, String language) {
        return "Translate the following text into " + language + ". Note that you should only output the "
                + "translated result without any additional explanation:\n\n" + part;
    }

    static int outputBudget(String text) {
        return Math.min(MAX_OUTPUT_TOKENS, 48 + 3 * (text == null ? 0 : text.length()));
    }

    /** Groups parts into blocks of at most eight parts and 480 characters (each part counts one separator). */
    static List<List<String>> buildBlocks(List<String> parts) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> block = new ArrayList<>();
        int characters = 0;
        for (String part : parts) {
            if (!block.isEmpty() && (block.size() >= BLOCK_MAX_PARTS
                    || characters + part.length() > BLOCK_MAX_CHARACTERS)) {
                blocks.add(Collections.unmodifiableList(block));
                block = new ArrayList<>();
                characters = 0;
            }
            block.add(part);
            characters += part.length() + 1;
        }
        if (!block.isEmpty()) {
            blocks.add(Collections.unmodifiableList(block));
        }
        return Collections.unmodifiableList(blocks);
    }

    /** Translates one block, falling back to one prompt per part when the block reply cannot be aligned. */
    static List<String> translateBlock(Engine engine, List<String> parts, String language) throws Exception {
        if (parts.size() > 1) {
            Completion reply = engine.complete(blockPrompt(parts, language), outputBudget(String.join("\n", parts)));
            String[] split = trim(reply.text.replace("\r\n", "\n").replace('\r', '\n')).split("\n", -1);
            List<String> lines = new ArrayList<>(split.length);
            boolean complete = !reply.stoppedAtTokenLimit && split.length == parts.size();
            for (String line : split) {
                String value = trim(line);
                complete &= !value.isEmpty();
                lines.add(value);
            }
            if (complete) {
                return lines;
            }
        }
        List<String> translated = new ArrayList<>(parts.size());
        for (String part : parts) {
            String line = singleLine(engine.complete(linePrompt(part, language), outputBudget(part)).text);
            if (line.isEmpty()) {
                throw new IOException("[Local AI] The model returned an empty line.");
            }
            translated.add(line);
        }
        return translated;
    }

    static boolean isTranslatable(String part) {
        String value = trim(part);
        return !value.isEmpty() && !isMarker(value);
    }

    /** The desktop's ^\s*\[.*\]\s*$ on an already trimmed part ('.' never crosses a line break). */
    private static boolean isMarker(String trimmed) {
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            return false;
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char c = trimmed.charAt(index);
            if (c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029') {
                return false;
            }
        }
        return true;
    }

    /** JavaScript String.prototype.trim: also removes Unicode spaces such as U+3000 in Japanese lyrics. */
    static String trim(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && isWhitespace(value.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isWhitespace(char c) {
        return c == '\t' || c == '\n' || c == 0x0B || c == '\f' || c == '\r' || c == ' ' || c == '\uFEFF'
                || Character.isSpaceChar(c);
    }

    /** The desktop's text.replace(/\s*\n+\s* /g, ' ').trim() for a one-part reply. */
    private static String singleLine(String text) {
        StringBuilder result = new StringBuilder();
        for (String piece : text.split("\n", -1)) {
            String value = trim(piece);
            if (value.isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(value);
        }
        return result.toString();
    }

    /** Parts of every row, the unique parts left to translate and the rows finished so far. */
    static final class Plan {
        private final List<String[]> rows = new ArrayList<>();
        private final Map<String, String> translations = new HashMap<>();
        private final List<String> pending = new ArrayList<>();
        private final boolean[] reported;

        Plan(List<String> sourceRows) {
            for (String source : sourceRows) {
                // A row is one lyric line; a stray line break inside it must not shift the rows below.
                String row = source == null ? "" : source.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ');
                String[] parts = row.split(PART_SEPARATOR, -1);
                rows.add(parts);
                for (String part : parts) {
                    String key = trim(part);
                    if (isTranslatable(part) && !translations.containsKey(key)) {
                        translations.put(key, null);
                        pending.add(key);
                    }
                }
            }
            reported = new boolean[rows.size()];
        }

        List<String> pendingParts() {
            return Collections.unmodifiableList(pending);
        }

        void put(List<String> parts, List<String> values) {
            for (int index = 0; index < parts.size(); index++) {
                translations.put(parts.get(index), values.get(index));
            }
        }

        int size() {
            return rows.size();
        }

        boolean isReady(int row) {
            for (String part : rows.get(row)) {
                if (isTranslatable(part) && translations.get(trim(part)) == null) {
                    return false;
                }
            }
            return true;
        }

        String text(int row) {
            StringBuilder result = new StringBuilder();
            String[] parts = rows.get(row);
            for (int index = 0; index < parts.length; index++) {
                if (index > 0) {
                    result.append(PART_SEPARATOR);
                }
                String part = parts[index];
                if (isTranslatable(part)) {
                    String translation = translations.get(trim(part));
                    result.append(translation == null ? "" : translation);
                } else if (!trim(part).isEmpty()) {
                    result.append(part);
                }
            }
            return result.toString();
        }

        List<String> texts() {
            List<String> texts = new ArrayList<>(rows.size());
            for (int row = 0; row < rows.size(); row++) {
                texts.add(text(row));
            }
            return texts;
        }

        /** Rows that became complete since the last call, each reported once. */
        List<Integer> takeReadyRows() {
            List<Integer> ready = new ArrayList<>();
            for (int row = 0; row < rows.size(); row++) {
                if (!reported[row] && isReady(row)) {
                    reported[row] = true;
                    ready.add(row);
                }
            }
            return ready;
        }

        List<Integer> reportedRows() {
            List<Integer> rowsSoFar = new ArrayList<>();
            for (int row = 0; row < reported.length; row++) {
                if (reported[row]) {
                    rowsSoFar.add(row);
                }
            }
            return rowsSoFar;
        }
    }

    private static Map<String, String> languageNames() {
        String[][] names = {
                {"ko", "Korean"}, {"en", "English"}, {"ja", "Japanese"},
                {"zh", "Chinese"}, {"zh-cn", "Chinese"}, {"zh-hans", "Chinese"}, {"zh-sg", "Chinese"},
                {"zh-tw", "Traditional Chinese"}, {"zh-hk", "Traditional Chinese"}, {"zh-hant", "Traditional Chinese"},
                {"es", "Spanish"}, {"fr", "French"}, {"de", "German"}, {"it", "Italian"}, {"ru", "Russian"},
                {"pt", "Portuguese"}, {"hi", "Hindi"}, {"ar", "Arabic"}, {"fa", "Persian"}, {"bn", "Bengali"},
                {"cs", "Czech"}, {"th", "Thai"}, {"tr", "Turkish"}, {"vi", "Vietnamese"}, {"id", "Indonesian"},
                {"ms", "Malay"}, {"pl", "Polish"}, {"nl", "Dutch"}, {"uk", "Ukrainian"}, {"he", "Hebrew"},
                {"tl", "Filipino"}, {"fil", "Filipino"}, {"km", "Khmer"}, {"my", "Burmese"}, {"gu", "Gujarati"},
                {"ur", "Urdu"}, {"te", "Telugu"}, {"mr", "Marathi"}, {"ta", "Tamil"}, {"bo", "Tibetan"},
                {"kk", "Kazakh"}, {"mn", "Mongolian"}, {"ug", "Uyghur"}, {"yue", "Cantonese"}
        };
        Map<String, String> map = new HashMap<>();
        for (String[] name : names) {
            map.put(name[0], name[1]);
        }
        return Collections.unmodifiableMap(map);
    }
}
