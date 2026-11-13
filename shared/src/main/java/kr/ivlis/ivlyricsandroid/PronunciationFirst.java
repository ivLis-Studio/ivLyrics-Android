package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pronunciation-first display mode.
 *
 * <p>The lyric rows keep their appearance and only swap values: the pronunciation is shown in the
 * original row and the original in the pronunciation row. Karaoke units keep their timing; the
 * pronunciation is split across them by AI (the same request as the desktop app) or, until that
 * arrives, in proportion to each unit's length.</p>
 */
final class PronunciationFirst {
    static final String PROMPT_VERSION = "timed-pronunciation-v1";
    private static final int MAX_CHUNK_UNITS = 420;
    private static final int MAX_CHUNK_ITEMS = 24;

    private PronunciationFirst() {
    }

    /** One timed unit group: a line's own syllables or one vocal part. */
    static final class Group {
        final String key;
        final List<LyricsLine.Syllable> syllables;
        final String pronunciation;

        Group(String key, List<LyricsLine.Syllable> syllables, String pronunciation) {
            this.key = key;
            this.syllables = syllables;
            this.pronunciation = pronunciation;
        }
    }

    /** A group sent to AI: {@code r} is the pronunciation, {@code u} its timed units. */
    static final class Item {
        final String key;
        final String pronunciation;
        final List<LyricsLine.Syllable> units;

        Item(String key, String pronunciation, List<LyricsLine.Syllable> units) {
            this.key = key;
            this.pronunciation = pronunciation;
            this.units = units;
        }
    }

    static List<Group> unitGroups(LyricsLine line) {
        if (line == null) return Collections.emptyList();
        List<Group> groups = new ArrayList<>();
        int timedParts = 0;
        for (LyricsLine.VocalPart part : line.vocalParts) {
            if (part != null && !part.syllables.isEmpty() && !part.text.trim().isEmpty()) timedParts++;
        }
        for (int index = 0; index < line.vocalParts.size(); index++) {
            LyricsLine.VocalPart part = line.vocalParts.get(index);
            if (part == null || part.syllables.isEmpty()) continue;
            String pronunciation = part.pronunciationText.trim();
            // A single voice can carry the whole line's pronunciation.
            if (pronunciation.isEmpty() && timedParts == 1) pronunciation = line.pronunciationText.trim();
            groups.add(new Group("part:" + index, part.syllables, pronunciation));
        }
        if (!groups.isEmpty()) return groups;
        if (!line.syllables.isEmpty()) {
            groups.add(new Group("line", line.syllables, line.pronunciationText.trim()));
        }
        return groups;
    }

    /** Display copy of the result with original and pronunciation swapped. */
    static LyricsResult apply(LyricsResult result, Map<String, List<String>> timed) {
        if (result == null || result.lines.isEmpty()) return result;
        List<LyricsLine> lines = new ArrayList<>(result.lines.size());
        boolean changed = false;
        for (int index = 0; index < result.lines.size(); index++) {
            LyricsLine line = result.lines.get(index);
            LyricsLine swapped = swapLine(line, index, timed);
            changed |= swapped != line;
            lines.add(swapped);
        }
        return changed ? result.withLines(lines) : result;
    }

    static LyricsLine swapLine(LyricsLine line, int lineIndex, Map<String, List<String>> timed) {
        if (line == null) return null;
        String pronunciation = line.pronunciationText.trim();
        List<LyricsLine.Syllable> syllables = line.syllables;
        List<LyricsLine.VocalPart> parts = line.vocalParts;
        boolean changed = false;
        for (Group group : unitGroups(line)) {
            if (group.pronunciation.isEmpty()) continue;
            List<String> unitParts = groupParts(timed, lineIndex, group);
            List<LyricsLine.Syllable> swappedSyllables = swapSyllables(group.syllables, unitParts);
            if ("line".equals(group.key)) {
                syllables = swappedSyllables;
            } else {
                int partIndex = Integer.parseInt(group.key.substring(5));
                if (parts == line.vocalParts) parts = new ArrayList<>(line.vocalParts);
                LyricsLine.VocalPart part = line.vocalParts.get(partIndex);
                parts.set(partIndex, new LyricsLine.VocalPart(
                        part.id, part.role, part.speaker, part.speakerColor, part.speakerFallback, part.kind,
                        group.pronunciation, swappedSyllables, part.text, part.translationText, ""
                ));
            }
            changed = true;
        }
        boolean hasTimedParts = parts != line.vocalParts;
        if (pronunciation.isEmpty() && !changed) return line;
        String text = pronunciation.isEmpty() ? line.text : pronunciation;
        // Parallel voices already show their own original below each part.
        String lineSubText = pronunciation.isEmpty() || hasTimedParts ? "" : line.text;
        return new LyricsLine(
                line.startTimeMs,
                line.endTimeMs,
                text,
                syllables,
                line.speaker,
                line.speakerColor,
                line.speakerFallback,
                line.kind,
                parts,
                lineSubText,
                line.translationText,
                ""
        );
    }

    private static List<String> groupParts(Map<String, List<String>> timed, int lineIndex, Group group) {
        int length = group.syllables.size();
        List<String> stored = timed == null ? null : timed.get(lineIndex + ":" + group.key);
        List<String> parts = isValidParts(stored, length) ? stored : distribute(unitTexts(group.syllables), group.pronunciation);
        return normalizeParts(parts, length);
    }

    private static List<LyricsLine.Syllable> swapSyllables(List<LyricsLine.Syllable> syllables, List<String> parts) {
        List<LyricsLine.Syllable> swapped = new ArrayList<>(syllables.size());
        for (int index = 0; index < syllables.size(); index++) {
            LyricsLine.Syllable syllable = syllables.get(index);
            swapped.add(syllable.copy(index < parts.size() ? parts.get(index) : "",
                    syllable.startTimeMs, syllable.endTimeMs, syllable.sourceWordUnit));
        }
        return swapped;
    }

    private static List<String> unitTexts(List<LyricsLine.Syllable> syllables) {
        List<String> texts = new ArrayList<>(syllables.size());
        for (LyricsLine.Syllable syllable : syllables) texts.add(syllable.text);
        return texts;
    }

    static boolean isValidParts(List<String> parts, int length) {
        if (parts == null || parts.size() != length) return false;
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) return true;
        }
        return false;
    }

    static List<String> graphemes(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            result.add(text.substring(start, end));
        }
        return result;
    }

    private static boolean isSound(String grapheme) {
        int codePoint = grapheme.codePointAt(0);
        return Character.isLetterOrDigit(codePoint);
    }

    private static boolean isSpace(String grapheme) {
        return grapheme.trim().isEmpty();
    }

    /** Spreads a pronunciation over units in proportion to how much each unit is sung. */
    static List<String> distribute(List<String> unitTexts, String pronunciation) {
        int count = unitTexts == null ? 0 : unitTexts.size();
        List<StringBuilder> builders = new ArrayList<>(count);
        for (int index = 0; index < count; index++) builders.add(new StringBuilder());
        List<String> characters = graphemes(pronunciation == null ? "" : pronunciation.trim());
        if (count == 0 || characters.isEmpty()) return toStrings(builders);
        int[] weights = new int[count];
        int total = 0;
        for (int index = 0; index < count; index++) {
            for (String grapheme : graphemes(unitTexts.get(index))) {
                if (isSound(grapheme)) weights[index]++;
            }
            total += weights[index];
        }
        if (total == 0) {
            int target = 0;
            for (int index = 0; index < count; index++) {
                if (unitTexts.get(index) != null && !unitTexts.get(index).trim().isEmpty()) {
                    target = index;
                    break;
                }
            }
            builders.get(target).append(String.join("", characters));
            return toStrings(builders);
        }
        int visibleTotal = 0;
        for (String character : characters) {
            if (!isSpace(character)) visibleTotal++;
        }
        int[] bounds = new int[count];
        int accumulated = 0;
        for (int index = 0; index < count; index++) {
            accumulated += weights[index];
            bounds[index] = Math.round((float) visibleTotal * accumulated / total);
        }
        int unit = 0;
        int seen = 0;
        for (String character : characters) {
            if (!isSpace(character)) {
                while (unit < count - 1 && seen >= bounds[unit]) unit++;
                seen++;
            }
            builders.get(unit).append(character);
        }
        return toStrings(builders);
    }

    private static List<String> toStrings(List<StringBuilder> builders) {
        List<String> values = new ArrayList<>(builders.size());
        for (StringBuilder builder : builders) values.add(builder.toString());
        return values;
    }

    /** Removes duplicated spaces at unit boundaries and spaces around the line. */
    static List<String> normalizeParts(List<String> parts, int length) {
        List<String> result = new ArrayList<>(length);
        for (int index = 0; index < length; index++) {
            String value = parts != null && index < parts.size() && parts.get(index) != null ? parts.get(index) : "";
            result.add(value.replaceAll("\\s+", " "));
        }
        boolean previousEndsWithSpace = true;
        for (int index = 0; index < result.size(); index++) {
            String value = result.get(index);
            if (value.isEmpty()) continue;
            if (previousEndsWithSpace) value = value.replaceFirst("^ +", "");
            result.set(index, value);
            if (!value.isEmpty()) previousEndsWithSpace = value.endsWith(" ");
        }
        for (int index = result.size() - 1; index >= 0; index--) {
            String value = result.get(index);
            if (value.isEmpty()) continue;
            value = value.replaceFirst(" +$", "");
            result.set(index, value);
            if (!value.isEmpty()) break;
        }
        return result;
    }

    // ---------------------------------------------------------------------
    // AI request
    // ---------------------------------------------------------------------

    static List<Item> timingItems(LyricsResult result) {
        List<Item> items = new ArrayList<>();
        if (result == null || !result.karaoke) return items;
        for (int lineIndex = 0; lineIndex < result.lines.size(); lineIndex++) {
            for (Group group : unitGroups(result.lines.get(lineIndex))) {
                if (group.pronunciation.isEmpty()) continue;
                boolean hasText = false;
                for (LyricsLine.Syllable syllable : group.syllables) {
                    if (!syllable.text.trim().isEmpty()) {
                        hasText = true;
                        break;
                    }
                }
                if (hasText) items.add(new Item(lineIndex + ":" + group.key, group.pronunciation, group.syllables));
            }
        }
        return items;
    }

    static JSONArray itemsJson(List<Item> items, int offset) throws Exception {
        JSONArray array = new JSONArray();
        for (int index = 0; index < items.size(); index++) {
            Item item = items.get(index);
            JSONArray units = new JSONArray();
            for (LyricsLine.Syllable syllable : item.units) {
                units.put(new JSONArray().put(syllable.text).put(syllable.startTimeMs).put(syllable.endTimeMs));
            }
            array.put(new JSONObject()
                    .put("i", offset + index)
                    .put("n", item.units.size())
                    .put("r", item.pronunciation)
                    .put("u", units));
        }
        return array;
    }

    static String signature(List<Item> items, String targetLang, String sourceLang) {
        StringBuilder builder = new StringBuilder(PROMPT_VERSION).append('|').append(targetLang).append('|').append(sourceLang);
        for (Item item : items) {
            builder.append('\n').append(item.key).append('\u0001').append(item.pronunciation);
            for (LyricsLine.Syllable syllable : item.units) {
                builder.append('\u0002').append(syllable.text).append('\u0003')
                        .append(syllable.startTimeMs).append('-').append(syllable.endTimeMs);
            }
        }
        return builder.toString();
    }

    static String lyricsText(LyricsResult result) {
        StringBuilder builder = new StringBuilder();
        for (LyricsLine line : result.lines) {
            String text = line.text.trim();
            if (text.isEmpty()) continue;
            if (builder.length() > 0) builder.append('\n');
            builder.append(text);
        }
        return builder.length() > 6000 ? builder.substring(0, 6000) : builder.toString();
    }

    /** Items grouped so each request stays small enough for one response. */
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

    /** The same prompt as the desktop app's buildTimedPronunciationPrompt. */
    static String prompt(List<Item> items, String lyricsText, String sourceLang) throws Exception {
        return "You align lyric pronunciation to karaoke timing for ivLyrics.\n\n"
                + "Each item is one sung lyric line. u lists its timed karaoke units in order; each unit is [source text, start ms, end ms]. n is the number of units. r is the pronunciation of that line, already written in the reader's writing system.\n\n"
                + "Task: split r across the units so that p[k] is exactly the part of r that is sung during unit k.\n\n"
                + "Rules:\n"
                + "- Return ONLY valid JSON, no markdown or explanations: {\"l\":[{\"i\":0,\"p\":[\"...\"]}]}\n"
                + "- Return one entry per input item with the same i.\n"
                + "- p must contain exactly n strings, one per unit, in unit order. Never merge, drop, add, or reorder slots.\n"
                + "- Use only the text of r, in its original order. Joining every p of an item must give back r; spaces may only move to unit boundaries. Do not translate, re-romanize, correct, or add anything to r.\n"
                + "- Put each sound on the unit whose source text produces it: kanji/hanzi readings on their characters, okurigana and particles on their own kana, a word's syllables across that word's units, a whole word on a word unit.\n"
                + "- If a reading cannot be divided between the units of one word, keep it on the first of those units and use \"\" for the rest of that word.\n"
                + "- Units that are only spaces or punctuation get \"\" unless r has a space there, in which case use \" \".\n"
                + "- Keep the word spaces of r: end a unit's string with a space where r has a space after it.\n"
                + "- Use the timing only to understand how the line is sung (long notes, held vowels); never change r because of it.\n\n"
                + "Song lyrics for context only (do not output them):\n"
                + (lyricsText == null ? "" : lyricsText) + "\n\n"
                + "Input source language: " + (sourceLang == null || sourceLang.isEmpty() ? "auto" : sourceLang) + "\n"
                + "Items:\n"
                + itemsJson(items, 0).toString();
    }

    /** Unit pronunciations by item key; items with a slot-count mismatch are left out. */
    static Map<String, List<String>> parse(String raw, List<Item> items) {
        Map<String, List<String>> parsed = new HashMap<>();
        if (raw == null) return parsed;
        String text = raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return parsed;
        try {
            JSONObject object = new JSONObject(text.substring(start, end + 1));
            JSONArray rows = object.optJSONArray("l");
            if (rows == null) rows = object.optJSONArray("lines");
            if (rows == null) return parsed;
            for (int row = 0; row < rows.length(); row++) {
                JSONObject entry = rows.optJSONObject(row);
                if (entry == null) continue;
                int index = entry.optInt("i", entry.optInt("index", -1));
                JSONArray values = entry.optJSONArray("p");
                if (index < 0 || index >= items.size() || values == null) continue;
                Item item = items.get(index);
                if (values.length() != item.units.size()) continue;
                List<String> parts = new ArrayList<>(values.length());
                for (int slot = 0; slot < values.length(); slot++) {
                    parts.add(values.isNull(slot) ? "" : values.optString(slot, ""));
                }
                if (isValidParts(parts, item.units.size())) parsed.put(item.key, Collections.unmodifiableList(parts));
            }
        } catch (Exception ignored) {
            // A malformed response leaves the proportional timing in place.
        }
        return parsed;
    }

    static String toJson(Map<String, List<String>> parts) throws Exception {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, List<String>> entry : parts.entrySet()) {
            object.put(entry.getKey(), new JSONArray(entry.getValue()));
        }
        return object.toString();
    }

    static Map<String, List<String>> fromJson(String json) throws Exception {
        Map<String, List<String>> parts = new HashMap<>();
        JSONObject object = new JSONObject(json);
        JSONArray names = object.names();
        if (names == null) return parts;
        for (int index = 0; index < names.length(); index++) {
            String key = names.getString(index);
            JSONArray values = object.getJSONArray(key);
            List<String> list = new ArrayList<>(values.length());
            for (int slot = 0; slot < values.length(); slot++) list.add(values.optString(slot, ""));
            parts.put(key, Collections.unmodifiableList(list));
        }
        return parts;
    }
}
