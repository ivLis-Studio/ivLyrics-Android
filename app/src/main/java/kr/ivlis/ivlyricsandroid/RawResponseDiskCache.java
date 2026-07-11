package kr.ivlis.ivlyricsandroid;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

final class RawResponseDiskCache {
    private static final int VERSION = 1;

    private final File directory;
    private final int maxEntries;
    private final long maxAgeMs;

    RawResponseDiskCache(Context context, String namespace, int maxEntries) {
        this(context, namespace, maxEntries, 0L);
    }

    RawResponseDiskCache(Context context, String namespace, int maxEntries, long maxAgeMs) {
        File root = context.getApplicationContext().getFilesDir();
        this.directory = new File(root, "lyrics_cache/" + safeNamespace(namespace));
        this.maxEntries = Math.max(16, maxEntries);
        this.maxAgeMs = Math.max(0L, maxAgeMs);
    }

    synchronized String get(String key) {
        File file = fileForKey(key);
        if (!file.isFile()) {
            return "";
        }
        try {
            JSONObject object = new JSONObject(readUtf8(file));
            if (object.optInt("version", 0) != VERSION) {
                return "";
            }
            long savedAtMs = object.optLong("savedAtMs", 0L);
            if (maxAgeMs > 0L
                    && (savedAtMs <= 0L || System.currentTimeMillis() - savedAtMs > maxAgeMs)) {
                file.delete();
                return "";
            }
            String value = object.optString("body", "");
            if (value.isEmpty()) {
                return "";
            }
            file.setLastModified(System.currentTimeMillis());
            return value;
        } catch (Exception ignored) {
            file.delete();
            return "";
        }
    }

    synchronized void put(String key, String body) {
        if (key == null || key.trim().isEmpty() || body == null || body.trim().isEmpty()) {
            return;
        }
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                return;
            }
            JSONObject object = new JSONObject();
            object.put("version", VERSION);
            object.put("cacheKey", key);
            object.put("savedAtMs", System.currentTimeMillis());
            object.put("body", body);

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
        return value.isEmpty() ? "raw" : value;
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
