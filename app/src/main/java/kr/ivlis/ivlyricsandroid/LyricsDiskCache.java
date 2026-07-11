package kr.ivlis.ivlyricsandroid;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class LyricsDiskCache {
    private static final int VERSION = 1;
    private static final int BASE_CONTRIBUTOR_SCHEMA_VERSION = 8;

    private final File directory;
    private final int maxEntries;
    private final boolean baseLyricsCache;
    private final long maxAgeMs;

    LyricsDiskCache(Context context, String namespace, int maxEntries) {
        this(context, namespace, maxEntries, 0L);
    }

    LyricsDiskCache(Context context, String namespace, int maxEntries, long maxAgeMs) {
        File root = context.getApplicationContext().getFilesDir();
        String safeNamespace = safeNamespace(namespace);
        this.directory = new File(root, "lyrics_cache/" + safeNamespace);
        this.maxEntries = Math.max(16, maxEntries);
        this.baseLyricsCache = "base_lyrics".equals(safeNamespace);
        this.maxAgeMs = Math.max(0L, maxAgeMs);
    }

    synchronized LyricsResult get(String key) {
        File file = fileForKey(key);
        if (!file.isFile()) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(readUtf8(file));
            if (object.optInt("version", 0) != VERSION) {
                return null;
            }
            if (baseLyricsCache
                    && object.optInt("contributorSchemaVersion", 0) < BASE_CONTRIBUTOR_SCHEMA_VERSION) {
                return null;
            }
            long savedAtMs = object.optLong("savedAtMs", 0L);
            if (maxAgeMs > 0L
                    && (savedAtMs <= 0L || System.currentTimeMillis() - savedAtMs > maxAgeMs)) {
                file.delete();
                return null;
            }
            LyricsResult result = resultFromJson(object);
            if (result.lines.isEmpty()) {
                return null;
            }
            file.setLastModified(System.currentTimeMillis());
            return result;
        } catch (Exception ignored) {
            file.delete();
            return null;
        }
    }

    synchronized void put(String key, LyricsResult result) {
        if (key == null || key.trim().isEmpty() || result == null || result.lines.isEmpty()) {
            return;
        }
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                return;
            }
            JSONObject object = resultToJson(result);
            object.put("version", VERSION);
            if (baseLyricsCache) {
                object.put("contributorSchemaVersion", BASE_CONTRIBUTOR_SCHEMA_VERSION);
            }
            object.put("cacheKey", key);
            object.put("savedAtMs", System.currentTimeMillis());
            File file = fileForKey(key);
            File temp = new File(directory, file.getName() + ".tmp");
            writeUtf8(temp, object.toString());
            if (!temp.renameTo(file)) {
                writeUtf8(file, object.toString());
                temp.delete();
            }
            prune();
        } catch (Exception ignored) {
        }
    }

    synchronized void remove(String key) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        File file = fileForKey(key);
        if (file.isFile()) {
            file.delete();
        }
    }

    synchronized void removeByKeyPrefix(String prefix) {
        String normalized = prefix == null ? "" : prefix.trim();
        if (normalized.isEmpty()) {
            return;
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                JSONObject object = new JSONObject(readUtf8(file));
                if (object.optString("cacheKey", "").startsWith(normalized)) {
                    file.delete();
                }
            } catch (Exception ignored) {
                file.delete();
            }
        }
    }

    synchronized void clear() {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                file.delete();
            }
        }
    }

    private File fileForKey(String key) {
        return new File(directory, sha256(key) + ".json");
    }

    private void prune() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length <= maxEntries) {
            return;
        }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int removeCount = files.length - maxEntries;
        for (int index = 0; index < removeCount; index++) {
            files[index].delete();
        }
    }

    private static JSONObject resultToJson(LyricsResult result) throws Exception {
        JSONObject object = new JSONObject();
        object.put("providerLabel", result.providerLabel);
        object.put("detail", result.detail);
        object.put("karaoke", result.karaoke);
        object.put("isrc", result.isrc);
        object.put("spotifyTrackId", result.spotifyTrackId);
        JSONArray contributors = new JSONArray();
        for (LyricsResult.SyncContributor contributor : result.contributors) {
            contributors.put(contributorToJson(contributor));
        }
        object.put("contributors", contributors);
        JSONArray lines = new JSONArray();
        for (LyricsLine line : result.lines) {
            lines.put(lineToJson(line));
        }
        object.put("lines", lines);
        return object;
    }

    private static LyricsResult resultFromJson(JSONObject object) {
        JSONArray array = object.optJSONArray("lines");
        List<LyricsLine> lines = new ArrayList<>();
        if (array != null) {
            for (int index = 0; index < array.length(); index++) {
                JSONObject line = array.optJSONObject(index);
                if (line != null) {
                    lines.add(lineFromJson(line));
                }
            }
        }
        return new LyricsResult(
                lines,
                object.optString("providerLabel", ""),
                object.optString("detail", ""),
                object.optBoolean("karaoke", false),
                object.optString("isrc", ""),
                object.optString("spotifyTrackId", ""),
                contributorsFromJson(object.optJSONArray("contributors"))
        );
    }

    private static JSONObject contributorToJson(LyricsResult.SyncContributor contributor) throws Exception {
        JSONObject object = new JSONObject();
        object.put("name", contributor.name);
        object.put("userHash", contributor.userHash);
        object.put("profileAvailable", contributor.profileAvailable);
        return object;
    }

    private static List<LyricsResult.SyncContributor> contributorsFromJson(JSONArray array) {
        if (array == null || array.length() == 0) {
            return new ArrayList<>();
        }
        List<LyricsResult.SyncContributor> contributors = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null) {
                continue;
            }
            contributors.add(new LyricsResult.SyncContributor(
                    object.optString("name", ""),
                    object.optString("userHash", ""),
                    object.optBoolean("profileAvailable", false)
            ));
        }
        return contributors;
    }

    private static JSONObject lineToJson(LyricsLine line) throws Exception {
        JSONObject object = new JSONObject();
        object.put("startTimeMs", line.startTimeMs);
        object.put("endTimeMs", line.endTimeMs);
        object.put("text", line.text);
        object.put("speaker", line.speaker);
        object.put("speakerColor", line.speakerColor);
        object.put("speakerFallback", line.speakerFallback);
        object.put("kind", line.kind);
        object.put("pronunciationText", line.pronunciationText);
        object.put("translationText", line.translationText);
        object.put("furiganaText", line.furiganaText);
        object.put("syllables", syllablesToJson(line.syllables));
        JSONArray parts = new JSONArray();
        for (LyricsLine.VocalPart part : line.vocalParts) {
            parts.put(vocalPartToJson(part));
        }
        object.put("vocalParts", parts);
        return object;
    }

    private static LyricsLine lineFromJson(JSONObject object) {
        return new LyricsLine(
                object.optLong("startTimeMs", 0L),
                object.optLong("endTimeMs", 0L),
                object.optString("text", ""),
                syllablesFromJson(object.optJSONArray("syllables")),
                object.optString("speaker", ""),
                object.optString("speakerColor", ""),
                object.optString("speakerFallback", ""),
                object.optString("kind", "vocal"),
                vocalPartsFromJson(object.optJSONArray("vocalParts")),
                object.optString("pronunciationText", ""),
                object.optString("translationText", ""),
                object.optString("furiganaText", "")
        );
    }

    private static JSONObject vocalPartToJson(LyricsLine.VocalPart part) throws Exception {
        JSONObject object = new JSONObject();
        object.put("id", part.id);
        object.put("role", part.role);
        object.put("speaker", part.speaker);
        object.put("speakerColor", part.speakerColor);
        object.put("speakerFallback", part.speakerFallback);
        object.put("kind", part.kind);
        object.put("text", part.text);
        object.put("pronunciationText", part.pronunciationText);
        object.put("translationText", part.translationText);
        object.put("furiganaText", part.furiganaText);
        object.put("syllables", syllablesToJson(part.syllables));
        return object;
    }

    private static List<LyricsLine.VocalPart> vocalPartsFromJson(JSONArray array) {
        List<LyricsLine.VocalPart> parts = new ArrayList<>();
        if (array == null) {
            return parts;
        }
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null) {
                continue;
            }
            parts.add(new LyricsLine.VocalPart(
                    object.optString("id", ""),
                    object.optString("role", ""),
                    object.optString("speaker", ""),
                    object.optString("speakerColor", ""),
                    object.optString("speakerFallback", ""),
                    object.optString("kind", "vocal"),
                    object.optString("text", ""),
                    syllablesFromJson(object.optJSONArray("syllables")),
                    object.optString("pronunciationText", ""),
                    object.optString("translationText", ""),
                    object.optString("furiganaText", "")
            ));
        }
        return parts;
    }

    private static JSONArray syllablesToJson(List<LyricsLine.Syllable> syllables) throws Exception {
        JSONArray array = new JSONArray();
        for (LyricsLine.Syllable syllable : syllables) {
            JSONObject object = new JSONObject();
            object.put("text", syllable.text);
            object.put("startTimeMs", syllable.startTimeMs);
            object.put("endTimeMs", syllable.endTimeMs);
            array.put(object);
        }
        return array;
    }

    private static List<LyricsLine.Syllable> syllablesFromJson(JSONArray array) {
        List<LyricsLine.Syllable> syllables = new ArrayList<>();
        if (array == null) {
            return syllables;
        }
        for (int index = 0; index < array.length(); index++) {
            JSONObject object = array.optJSONObject(index);
            if (object == null) {
                continue;
            }
            syllables.add(new LyricsLine.Syllable(
                    object.optString("text", ""),
                    object.optLong("startTimeMs", 0L),
                    object.optLong("endTimeMs", 0L)
            ));
        }
        return syllables;
    }

    private static String readUtf8(File file) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeUtf8(File file, String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String safeNamespace(String namespace) {
        String value = namespace == null ? "" : namespace.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_-]", "_");
        return value.isEmpty() ? "default" : value;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", item));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }
}
