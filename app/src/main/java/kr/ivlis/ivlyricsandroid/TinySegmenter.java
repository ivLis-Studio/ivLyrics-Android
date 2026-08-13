package kr.ivlis.ivlyricsandroid;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Client-side TinySegmenter 0.2 runtime using the bundled, versioned model. */
final class TinySegmenter {
    private static final String NUMERIC_KANJI = "一二三四五六七八九十百千万億兆";

    private final int bias;
    private final Map<String, Map<String, Integer>> weights;

    private TinySegmenter(int bias, Map<String, Map<String, Integer>> weights) {
        this.bias = bias;
        this.weights = weights;
    }

    static TinySegmenter fromAssets(Context context) throws Exception {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("tiny_segmenter_model.json"),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
        }
        return fromJson(json.toString());
    }

    @SuppressWarnings("unchecked")
    static TinySegmenter fromJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject source = root.getJSONObject("weights");
        int bias = source.getInt("BIAS");
        Map<String, Map<String, Integer>> tables = new HashMap<>();
        Iterator<String> names = source.keys();
        while (names.hasNext()) {
            String name = names.next();
            Object raw = source.get(name);
            if (!(raw instanceof JSONObject)) continue;
            JSONObject object = (JSONObject) raw;
            Map<String, Integer> table = new HashMap<>();
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                table.put(key, object.getInt(key));
            }
            tables.put(name, table);
        }
        return new TinySegmenter(bias, tables);
    }

    List<String> segment(String input) {
        if (input == null || input.isEmpty()) return new ArrayList<>();
        List<String> characters = codePoints(input);
        List<String> segments = new ArrayList<>();
        List<String> types = new ArrayList<>();
        segments.add("B3");
        segments.add("B2");
        segments.add("B1");
        types.add("O");
        types.add("O");
        types.add("O");
        for (String character : characters) {
            segments.add(character);
            types.add(characterType(character));
        }
        segments.add("E1");
        segments.add("E2");
        segments.add("E3");
        types.add("O");
        types.add("O");
        types.add("O");

        List<String> result = new ArrayList<>();
        StringBuilder word = new StringBuilder(segments.get(3));
        String p1 = "U";
        String p2 = "U";
        String p3 = "U";
        for (int index = 4; index < segments.size() - 3; index++) {
            String w1 = segments.get(index - 3);
            String w2 = segments.get(index - 2);
            String w3 = segments.get(index - 1);
            String w4 = segments.get(index);
            String w5 = segments.get(index + 1);
            String w6 = segments.get(index + 2);
            String c1 = types.get(index - 3);
            String c2 = types.get(index - 2);
            String c3 = types.get(index - 1);
            String c4 = types.get(index);
            String c5 = types.get(index + 1);
            String c6 = types.get(index + 2);
            int score = bias;
            score += score("UP1", p1) + score("UP2", p2) + score("UP3", p3);
            score += score("BP1", p1 + p2) + score("BP2", p2 + p3);
            score += score("UW1", w1) + score("UW2", w2) + score("UW3", w3);
            score += score("UW4", w4) + score("UW5", w5) + score("UW6", w6);
            score += score("BW1", w2 + w3) + score("BW2", w3 + w4) + score("BW3", w4 + w5);
            score += score("TW1", w1 + w2 + w3) + score("TW2", w2 + w3 + w4);
            score += score("TW3", w3 + w4 + w5) + score("TW4", w4 + w5 + w6);
            score += score("UC1", c1) + score("UC2", c2) + score("UC3", c3);
            score += score("UC4", c4) + score("UC5", c5) + score("UC6", c6);
            score += score("BC1", c2 + c3) + score("BC2", c3 + c4) + score("BC3", c4 + c5);
            score += score("TC1", c1 + c2 + c3) + score("TC2", c2 + c3 + c4);
            score += score("TC3", c3 + c4 + c5) + score("TC4", c4 + c5 + c6);
            score += score("UQ1", p1 + c1) + score("UQ2", p2 + c2) + score("UQ3", p3 + c3);
            score += score("BQ1", p2 + c2 + c3) + score("BQ2", p2 + c3 + c4);
            score += score("BQ3", p3 + c2 + c3) + score("BQ4", p3 + c3 + c4);
            score += score("TQ1", p2 + c1 + c2 + c3) + score("TQ2", p2 + c2 + c3 + c4);
            score += score("TQ3", p3 + c1 + c2 + c3) + score("TQ4", p3 + c2 + c3 + c4);

            String p = "O";
            if (score > 0) {
                result.add(word.toString());
                word.setLength(0);
                p = "B";
            }
            p1 = p2;
            p2 = p3;
            p3 = p;
            word.append(segments.get(index));
        }
        result.add(word.toString());
        return result;
    }

    private int score(String tableName, String key) {
        Map<String, Integer> table = weights.get(tableName);
        if (table == null) return 0;
        Integer value = table.get(key);
        return value == null ? 0 : value;
    }

    private static String characterType(String value) {
        int codePoint = value.codePointAt(0);
        if (NUMERIC_KANJI.indexOf(value) >= 0) return "M";
        if ((codePoint >= '一' && codePoint <= '龠') || "々〆ヵヶ".contains(value)) return "H";
        if (codePoint >= 'ぁ' && codePoint <= 'ん') return "I";
        if ((codePoint >= 'ァ' && codePoint <= 'ヴ') || codePoint == 'ー'
                || (codePoint >= 'ｱ' && codePoint <= 'ﾝ') || codePoint == 'ﾞ' || codePoint == 'ｰ') return "K";
        if ((codePoint >= 'a' && codePoint <= 'z') || (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'ａ' && codePoint <= 'ｚ') || (codePoint >= 'Ａ' && codePoint <= 'Ｚ')) return "A";
        if ((codePoint >= '0' && codePoint <= '9') || (codePoint >= '０' && codePoint <= '９')) return "N";
        return "O";
    }

    private static List<String> codePoints(String text) {
        List<String> output = new ArrayList<>();
        text.codePoints().forEach(codePoint -> output.add(new String(Character.toChars(codePoint))));
        return output;
    }
}
