package kr.ivlis.ivlyricsandroid;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;
import java.util.Set;

/** Native read-only v6 decoder. Cache raw v6; adapt metadata/ranges only for playback. */
final class SyncDataV6Codec {
    static final String TIMING_KEY = "_ivSyncV6Timing";
    private static final int MAX_TIME = 86400000;
    private static final Set<String> META = keys("speaker", "speaker-color", "speaker-fallback", "kind");
    private static final Set<String> UNITS = keys("line", "word", "character");
    private SyncDataV6Codec() {}

    static final class Mark {
        final int end;
        final long ms;
        final String unit;
        Mark(int end, long ms, String unit) { this.end = end; this.ms = ms; this.unit = unit; }
    }
    /** Immutable virtual character view: a line stores one mark, a word one per word. */
    static final class Timing extends AbstractList<Double> implements RandomAccess {
        final List<Mark> marks;
        private final long[] times;
        private final int[] ends; // null for one mark per character
        private final byte[] unitTags; // null when the entire target uses one unit
        private final byte uniformUnit;
        final int count;
        final Long endMs;
        private static byte tag(String unit) { return (byte) ("line".equals(unit) ? 0 : "word".equals(unit) ? 1 : 2); }
        private static String unitName(byte tag) { return tag == 0 ? "line" : tag == 1 ? "word" : "character"; }
        Timing(List<Mark> input, int count, Long endMs) {
            this.count = count; this.endMs = endMs; times = new long[input.size()];
            boolean sequential = input.size() == count, uniform = true;
            uniformUnit = input.isEmpty() ? 2 : tag(input.get(0).unit);
            for (int i = 0; i < input.size(); i++) { times[i] = input.get(i).ms; sequential &= input.get(i).end == i + 1; uniform &= tag(input.get(i).unit) == uniformUnit; }
            ends = sequential ? null : new int[input.size()]; unitTags = uniform ? null : new byte[input.size()];
            for (int i = 0; i < input.size(); i++) { if (ends != null) ends[i] = input.get(i).end; if (unitTags != null) unitTags[i] = tag(input.get(i).unit); }
            // A view over primitive arrays: no persistent object per character.
            marks = Collections.unmodifiableList(new AbstractList<Mark>() {
                @Override public int size() { return times.length; }
                @Override public Mark get(int index) { return new Mark(ends == null ? index + 1 : ends[index], times[index], unitName(unitTags == null ? uniformUnit : unitTags[index])); }
            });
        }
        int markIndex(int index) {
            if (index < 0 || index >= count) throw new IndexOutOfBoundsException();
            if (ends == null) return index;
            int low = 0, high = times.length - 1;
            while (low < high) { int middle = (low + high) >>> 1; if (index < ends[middle]) high = middle; else low = middle + 1; }
            return low;
        }
        @Override public int size() { return count; }
        @Override public Double get(int index) { return times[markIndex(index)] / 1000.0; }
        String unit(int index) { return unitName(unitTags == null ? uniformUnit : unitTags[markIndex(index)]); }
        int group(int index) { int mark = markIndex(index); return "character".equals(unit(index)) ? -(index + 1) : mark; }
        int storedMarkCount() { return marks.size(); }
        @Override public Timing subList(int from, int to) {
            if (from < 0 || to < from || to > count) throw new IndexOutOfBoundsException();
            List<Mark> next = new ArrayList<>(); int start = 0;
            for (Mark mark : marks) {
                int begin = Math.max(start, from), end = Math.min(mark.end, to);
                if (begin < end) next.add(new Mark(end - from, mark.ms, mark.unit));
                start = mark.end;
            }
            return new Timing(next, to - from, to == count ? endMs : null);
        }
        Timing shifted(double seconds) {
            long offset = Math.round(seconds * 1000);
            List<Mark> next = new ArrayList<>();
            for (Mark mark : marks) next.add(new Mark(mark.end, Math.max(0L, mark.ms + offset), mark.unit));
            return new Timing(next, count, endMs == null ? null : Math.max(0L, endMs + offset));
        }
    }
    static List<Double> copyTiming(List<Double> values) {
        return values instanceof Timing ? values : values == null ? Collections.emptyList() : new ArrayList<>(values);
    }
    static List<Double> sliceTiming(List<Double> values, int from, int to) {
        return values instanceof Timing ? ((Timing) values).subList(from, to) : new ArrayList<>(values.subList(from, to));
    }
    private static Set<String> keys(String... names) { return new HashSet<>(Arrays.asList(names)); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException("Invalid sync v6: " + message);
    }
    private static JSONObject object(Object value) {
        require(value instanceof JSONObject, "expected object"); return (JSONObject) value;
    }
    private static JSONArray array(Object value, int max) {
        require(value instanceof JSONArray && ((JSONArray) value).length() <= max, "array bound"); return (JSONArray) value;
    }
    private static void fields(JSONObject object, Set<String> allowed) {
        Iterator<String> names = object.keys(); while (names.hasNext()) require(allowed.contains(names.next()), "unknown fields");
    }
    private static int integer(Object value, int min, int max, String label) {
        require(value instanceof Number, label);
        double number = ((Number) value).doubleValue();
        require(Double.isFinite(number) && Math.rint(number) == number && number >= min && number <= max, label);
        return (int) number;
    }
    private static JSONObject copy(JSONObject value) throws JSONException {
        JSONObject next = new JSONObject(); Iterator<String> names = value.keys();
        while (names.hasNext()) { String name = names.next(); next.put(name, value.opt(name)); } return next;
    }
    private static JSONObject meta(JSONObject value, String key) throws JSONException {
        if (!value.has(key)) return new JSONObject(); JSONObject result = object(value.opt(key)); fields(result, META); return copy(result);
    }
    private static JSONArray ranges(Object raw, int offset, int length) throws JSONException {
        JSONArray values = array(raw, 2000), result = new JSONArray(); int previous = 0;
        for (int i = 0; i < values.length(); i++) {
            JSONArray pair = array(values.opt(i), 2); require(pair.length() == 2, "range pair");
            int from = integer(pair.opt(0), previous, length - 1, "range start");
            int to = integer(pair.opt(1), from + 1, length, "range end"); previous = to;
            result.put(new JSONObject().put("start", offset + from).put("end", offset + to - 1));
        }
        return result;
    }
    private static int size(JSONArray ranges) {
        int size = 0; for (int i = 0; i < ranges.length(); i++) { JSONObject range = ranges.optJSONObject(i); size += range.optInt("end") - range.optInt("start") + 1; } return size;
    }
    static Timing timing(Object raw, int length) {
        JSONObject value = object(raw);
        fields(value, keys("unit", "startMs", "deltaMs", "lengths", "endMs", "declared", "runs"));
        boolean undeclared = value.has("declared"); if (undeclared) require(Boolean.FALSE.equals(value.opt("declared")), "declared");
        List<Mark> marks = new ArrayList<>(); String unit = value.optString("unit", "");
        if ("mixed".equals(unit)) {
            require(!value.has("startMs") && !value.has("deltaMs") && !value.has("lengths"), "mixed fields");
            JSONArray runs = array(value.opt("runs"), length); require(runs.length() > 0, "empty mixed timing"); int consumed = 0;
            for (int i = 0; i < runs.length(); i++) {
                JSONObject run = object(runs.opt(i)); fields(run, keys("length", "timing"));
                int count = integer(run.opt("length"), 1, length - consumed, "run length");
                JSONObject child = object(run.opt("timing")); require(!"mixed".equals(child.optString("unit")) && !child.has("endMs"), "nested mixed/hold");
                Timing decoded = timing(child, count);
                for (Mark mark : decoded.marks) marks.add(new Mark(consumed + mark.end, mark.ms, undeclared ? "character" : mark.unit));
                consumed += count;
            }
            require(consumed == length, "mixed coverage");
        } else {
            require(UNITS.contains(unit) && !value.has("runs"), "timing unit");
            int time = integer(value.opt("startMs"), 0, MAX_TIME, "startMs");
            List<Integer> lengths = new ArrayList<>();
            if ("line".equals(unit)) { require(!value.has("deltaMs") && !value.has("lengths"), "line marks"); lengths.add(length); }
            else if ("word".equals(unit)) {
                JSONArray sizes = array(value.opt("lengths"), length); int sum = 0;
                for (int i = 0; i < sizes.length(); i++) { int count = integer(sizes.opt(i), 1, length, "word length"); sum += count; lengths.add(count); }
                require(sum == length && !lengths.isEmpty(), "word coverage");
            } else { require(!value.has("lengths"), "character lengths"); for (int i = 0; i < length; i++) lengths.add(1); }
            JSONArray deltas = "line".equals(unit) ? new JSONArray() : array(value.opt("deltaMs"), length);
            require(deltas.length() == lengths.size() - 1, "delta count"); int end = 0;
            for (int i = 0; i < lengths.size(); i++) {
                if (i > 0) time += integer(deltas.opt(i - 1), 0, MAX_TIME, "deltaMs");
                require(time <= MAX_TIME, "timing overflow"); end += lengths.get(i); marks.add(new Mark(end, time, undeclared ? "character" : unit));
            }
        }
        require(!marks.isEmpty(), "empty timing");
        for (int i = 1; i < marks.size(); i++) require(marks.get(i).ms >= marks.get(i - 1).ms, "timing order");
        long last = marks.get(marks.size() - 1).ms;
        Long end = value.has("endMs") ? (long) integer(value.opt("endMs"), (int) last + 1, (int) Math.min(MAX_TIME, last + 60000), "endMs") : null;
        return new Timing(marks, length, end);
    }
    private static void attachTiming(JSONObject target, Object raw, int length) throws JSONException {
        JSONObject value = object(raw); Timing timing = timing(value, length);
        // Choose Object overload: the Collection overload eagerly expands virtual timing.
        target.put(TIMING_KEY, (Object) timing).put("granularity", !value.has("declared") && !"mixed".equals(value.optString("unit")) ? value.optString("unit") : "character");
        if (timing.endMs != null) target.put("lastCharEnd", timing.endMs / 1000.0);
    }
    static JSONObject playbackBody(JSONObject data) throws JSONException {
        if ("6".equals(data.opt("version"))) throw new IllegalArgumentException("Invalid sync v6: document version");
        if (!(data.opt("version") instanceof Number) || ((Number) data.opt("version")).doubleValue() != 6) return data;
        integer(data.opt("version"), 6, 6, "document version");
        fields(data, keys("version", "timeUnit", "indexUnit", "source", "trackDurationMs", "lines"));
        require("ms".equals(data.opt("timeUnit")) && "nfc-codepoint".equals(data.opt("indexUnit")), "document encoding");
        if (data.has("source")) object(data.opt("source"));
        if (data.has("trackDurationMs")) integer(data.opt("trackDurationMs"), 1, MAX_TIME, "trackDurationMs");
        JSONArray lines = array(data.opt("lines"), 5000), nextLines = new JSONArray(); int previous = 0, total = 0;
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = object(lines.opt(i)); fields(line, keys("id", "offset", "length", "timing", "derivedTiming", "meta", "styles", "hidden", "continuations", "parallel"));
            int offset = integer(line.opt("offset"), previous, 10000000, "line offset"), length = integer(line.opt("length"), 1, 2000, "line length");
            total += length; require(total <= 1000000, "character budget"); previous = offset + length;
            require(("l" + Integer.toString(offset, 36)).equals(line.opt("id")), "line id");
            require(line.has("timing") != line.has("derivedTiming"), "one parent timing required");
            JSONObject next = meta(line, "meta"); next.put("start", offset).put("end", offset + length - 1);
            if (line.has("hidden")) next.put("hiddenRanges", ranges(line.opt("hidden"), offset, length));
            if (line.has("styles")) {
                JSONArray styles = array(line.opt("styles"), length), values = new JSONArray(); Set<String> allowed = new HashSet<>(META); allowed.add("range");
                for (int j = 0; j < styles.length(); j++) {
                    JSONObject style = object(styles.opt(j)); fields(style, allowed);
                    JSONObject value = copy(style); value.remove("range");
                    JSONObject range = ranges(new JSONArray().put(style.opt("range")), offset, length).optJSONObject(0);
                    value.put("start", range.opt("start")).put("end", range.opt("end")); values.put(value);
                }
                next.put("styleRanges", values);
            }
            if (line.has("continuations")) {
                JSONArray points = array(line.opt("continuations"), 2000), values = new JSONArray();
                for (int j = 0; j < points.length(); j++) values.put(offset + integer(points.opt(j), 1, length - 1, "continuation"));
                next.put("mergedLineContinuationStarts", values);
            }
            JSONArray parts = new JSONArray();
            if (line.has("parallel")) {
                JSONObject parallel = object(line.opt("parallel")); fields(parallel, keys("layout", "parts", "hidden", "splits"));
                require("stack".equals(parallel.opt("layout")), "parallel layout");
                JSONArray rawParts = array(parallel.opt("parts"), 16); require(rawParts.length() > 0, "parallel parts"); Set<String> ids = new HashSet<>();
                for (int j = 0; j < rawParts.length(); j++) {
                    JSONObject part = object(rawParts.opt(j)); fields(part, keys("id", "role", "ranges", "join", "timing", "meta", "pendingUnit"));
                    Object rawId = part.opt("id"); require(rawId instanceof String && !((String) rawId).isEmpty() && ((String) rawId).length() <= 32 && ids.add((String) rawId), "part id");
                    require("lead".equals(part.opt("role")) || "background".equals(part.opt("role")), "part role");
                    JSONArray spans = ranges(part.opt("ranges"), offset, length); int count = size(spans); total += count;
                    require(count > 0 && total <= 1000000, "part coverage");
                    JSONArray join = array(part.opt("join"), 2000); require(join.length() == spans.length() - 1, "join count");
                    for (int k = 0; k < join.length(); k++) integer(join.opt(k), 0, 2, "join");
                    JSONObject value = meta(part, "meta"); value.put("id", rawId).put("role", part.opt("role")).put("ranges", spans).put("join", join);
                    if (part.has("pendingUnit")) { require(!part.has("timing") && UNITS.contains(part.optString("pendingUnit")), "pending unit"); value.put("granularity", part.opt("pendingUnit")); }
                    if (part.has("timing")) attachTiming(value, part.opt("timing"), count);
                    parts.put(value);
                }
                JSONObject value = new JSONObject().put("layout", "stack").put("parts", parts);
                if (parallel.has("hidden")) value.put("hiddenRanges", ranges(parallel.opt("hidden"), offset, length));
                if (parallel.has("splits")) {
                    JSONArray splits = array(parallel.opt("splits"), 2000);
                    for (int j = 0; j < splits.length(); j++) integer(splits.opt(j), 1, length - 1, "split");
                    value.put("manualSplitPoints", splits);
                }
                next.put("parallel", value);
            }
            attachTiming(next, line.has("derivedTiming") ? parentTiming(line.opt("derivedTiming"), parts, offset, length) : line.opt("timing"), length);
            nextLines.put(next);
        }
        return copy(data).put("lines", nextLines);
    }
    private static JSONObject parentTiming(Object raw, JSONArray parts, int offset, int length) throws JSONException {
        JSONObject shape = object(raw); fields(shape, keys("unit", "lengths", "declared", "endMs")); String unit = shape.optString("unit"); require(UNITS.contains(unit), "derived unit");
        Long[] values = new Long[length];
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i); require(part.opt(TIMING_KEY) instanceof Timing, "untimed parent part"); Timing timing = (Timing) part.opt(TIMING_KEY);
            JSONArray spans = part.optJSONArray("ranges"); int cursor = 0;
            for (int j = 0; j < spans.length(); j++) {
                JSONObject span = spans.optJSONObject(j);
                for (int absolute = span.optInt("start"); absolute <= span.optInt("end"); absolute++) {
                    int local = absolute - offset; require(values[local] == null, "overlapping parent parts"); values[local] = Math.round(timing.get(cursor++) * 1000);
                }
            }
        }
        for (int i = 0; i < length; i++) require(values[i] != null && (i == 0 || values[i] >= values[i - 1]), "parent gap/order");
        JSONObject next = copy(shape).put("startMs", values[0]);
        if ("line".equals(unit)) { for (long value : values) require(value == values[0], "derived line"); }
        else if ("character".equals(unit)) { JSONArray deltas = new JSONArray(); for (int i = 1; i < length; i++) deltas.put(values[i] - values[i - 1]); next.put("deltaMs", deltas); }
        else {
            JSONArray lengths = array(shape.opt("lengths"), length), deltas = new JSONArray(); int cursor = 0, sum = 0; Long previous = null;
            for (int i = 0; i < lengths.length(); i++) sum += integer(lengths.opt(i), 1, length, "word length"); require(lengths.length() > 0 && sum == length, "derived word coverage");
            for (int i = 0; i < lengths.length(); i++) {
                int count = ((Number) lengths.opt(i)).intValue(); long start = values[cursor];
                for (int j = 0; j < count; j++) require(values[cursor++] == start, "derived word marks");
                if (previous != null) deltas.put(start - previous); previous = start;
            }
            next.put("deltaMs", deltas);
        }
        return next;
    }
}
