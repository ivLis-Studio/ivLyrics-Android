package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** User imports are durable overrides, separate from the disposable network cache. */
final class LocalLyricsStore {
    static final int MAX_BYTES = 1024 * 1024;
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,6}):([0-5]?\\d)(?:[.,](\\d{1,3}))?\\]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\s*\\[offset:([+-]?\\d{1,9})\\]\\s*$");
    private static final Pattern METADATA = Pattern.compile("(?i)^\\[(?:ar|al|ti|au|length|by|offset|re|ve):.*\\]$");
    private static final Map<SharedPreferences, Map<String, Long>> EDIT_GENERATIONS = new WeakHashMap<>();
    private final SharedPreferences prefs;

    LocalLyricsStore(Context context) {
        Context app = context.getApplicationContext();
        prefs = (app == null ? context : app).getSharedPreferences("local_lyrics", Context.MODE_PRIVATE);
    }

    LyricsResult load(TrackSnapshot track) throws IOException {
        String text = prefs.getString(track.stableKey(), null);
        return text == null ? null : parse(text, track.durationMs);
    }

    void save(String key, String text) throws IOException {
        synchronized (EDIT_GENERATIONS) {
            beginImport(key);
            if (!prefs.edit().putString(key, text).commit()) throw new IOException("Could not save local lyrics");
        }
    }

    void remove(String key) throws IOException {
        synchronized (EDIT_GENERATIONS) {
            // A file that is still being read must not restore a removed override afterwards.
            beginImport(key);
            if (!prefs.edit().remove(key).commit()) throw new IOException("Could not remove local lyrics");
        }
    }

    long beginImport(String key) {
        synchronized (EDIT_GENERATIONS) {
            Map<String, Long> edits = EDIT_GENERATIONS.computeIfAbsent(prefs, ignored -> new HashMap<>());
            long next = edits.getOrDefault(key, 0L) + 1L;
            edits.put(key, next);
            return next;
        }
    }

    boolean saveIfCurrent(String key, String text, long generation) throws IOException {
        synchronized (EDIT_GENERATIONS) {
            if (!isCurrentImport(key, generation)) return false;
            if (!prefs.edit().putString(key, text).commit()) throw new IOException("Could not save local lyrics");
            return true;
        }
    }

    boolean isCurrentImport(String key, long generation) {
        synchronized (EDIT_GENERATIONS) {
            Map<String, Long> edits = EDIT_GENERATIONS.get(prefs);
            return edits != null && edits.getOrDefault(key, 0L) == generation;
        }
    }

    Runnable observe(Consumer<String> onChange) {
        SharedPreferences.OnSharedPreferenceChangeListener listener = (preferences, key) -> onChange.accept(key);
        prefs.registerOnSharedPreferenceChangeListener(listener);
        return () -> prefs.unregisterOnSharedPreferenceChangeListener(listener);
    }

    static String decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid lyrics file size");
        Charset charset = StandardCharsets.UTF_8;
        int offset = 0;
        if (bytes.length >= 2 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 254) {
            charset = StandardCharsets.UTF_16LE; offset = 2;
        } else if (bytes.length >= 2 && (bytes[0] & 255) == 254 && (bytes[1] & 255) == 255) {
            charset = StandardCharsets.UTF_16BE; offset = 2;
        }
        try {
            String text = charset.newDecoder().decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
            if (text.indexOf('\0') >= 0) throw new IOException("Not a text file");
            return text.startsWith("\uFEFF") ? text.substring(1) : text;
        } catch (CharacterCodingException error) {
            throw new IOException("Use a UTF-8 or UTF-16 lyrics file", error);
        }
    }

    static LyricsResult parse(String text, long durationMs) throws IOException {
        long offset = 0;
        Matcher offsetMatcher = OFFSET.matcher(text);
        while (offsetMatcher.find()) offset = Long.parseLong(offsetMatcher.group(1));
        List<LyricsLine> timed = new ArrayList<>();
        List<String> plain = new ArrayList<>();
        for (String raw : text.split("\\r\\n|\\r|\\n")) {
            String line = raw.trim();
            if (line.isEmpty() || METADATA.matcher(line).matches()) continue;
            Matcher stamps = TIMESTAMP.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (stamps.find() && stamps.start() == end) {
                String fraction = stamps.group(3);
                long millis = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                times.add(Math.max(0, Long.parseLong(stamps.group(1)) * 60_000
                        + Long.parseLong(stamps.group(2)) * 1000 + millis + offset));
                end = stamps.end();
            }
            if (times.isEmpty()) {
                plain.add(line);
            } else {
                String words = line.substring(end).trim();
                for (long time : times) timed.add(new LyricsLine(time, time, words, Collections.emptyList()));
            }
        }
        boolean synced = !timed.isEmpty();
        List<LyricsLine> lines;
        if (synced) {
            timed.sort(Comparator.comparingLong(line -> line.startTimeMs));
            lines = new ArrayList<>();
            long nextDistinctStart = -1;
            for (int i = timed.size() - 1; i >= 0; i--) {
                LyricsLine line = timed.get(i);
                if (i + 1 < timed.size() && timed.get(i + 1).startTimeMs > line.startTimeMs) {
                    nextDistinctStart = timed.get(i + 1).startTimeMs;
                }
                long end = nextDistinctStart >= 0 ? nextDistinctStart
                        : durationMs > line.startTimeMs ? durationMs : line.startTimeMs + 4000;
                lines.add(new LyricsLine(line.startTimeMs, end, line.text, Collections.emptyList()));
            }
            Collections.reverse(lines);
        } else {
            lines = LrcParser.parsePlain(String.join("\n", plain));
        }
        boolean visible = false;
        for (LyricsLine line : lines) if (!line.text.isEmpty()) visible = true;
        if (!visible) throw new IOException("No lyrics in file");
        return new LyricsResult(lines, synced ? "Local LRC" : "Local", "", false,
                "", "", Collections.emptyList(), "local", "local", synced ? "line" : "unknown", synced ? lines.size() : 0);
    }
}
