package kr.ivlis.ivlyricsandroid;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Public track identity extraction shared by direct, nested and TrackV4 metadata paths. */
final class NativeTrackIdentity {
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern TRACK_ID = Pattern.compile("[0-9a-zA-Z]{22}");
    private static final Pattern ISRC_SEPARATORS = Pattern.compile("[\\s-]");
    private static final Pattern ISRC_VALUE = Pattern.compile("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}");
    // Bound retained host classes as well as per-class members. Cache bindings, never
    // protobuf instances, so newer metadata for the same recording is still decoded.
    private static final Map<Class<?>, Members> MEMBERS = new LinkedHashMap<Class<?>, Members>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Class<?>, Members> entry) { return size() > 32; }
    };
    final String uri;
    final String isrc;

    private NativeTrackIdentity(String uri, String isrc) { this.uri = uri; this.isrc = isrc; }

    static NativeTrackIdentity fromProto(Object track) throws ReflectiveOperationException {
        String isrc = externalIsrc((Iterable<?>) field(track, "externalId_"), "type_", "id_");
        if (isrc.isEmpty()) return null;
        Object gid = field(track, "gid_");
        Method bytesMethod = members(gid.getClass()).bytesMethod(gid.getClass());
        byte[] bytes = bytesMethod == null ? null : (byte[]) bytesMethod.invoke(gid);
        if (bytes == null || bytes.length != 16) return null;
        return new NativeTrackIdentity("spotify:track:" + base62(bytes), isrc);
    }

    static NativeTrackIdentity fromTrackV4(Object track) throws ReflectiveOperationException {
        String uri = canonicalUri((String) field(track, "a"));
        String isrc = externalIsrc((Iterable<?>) field(track, "j"), "b", "a");
        return uri.isEmpty() || isrc.isEmpty() ? null : new NativeTrackIdentity(uri, isrc);
    }

    static String canonicalUri(String value) {
        if (value == null) return "";
        String id = value.startsWith("spotify:track:") ? value.substring(14) : value;
        return TRACK_ID.matcher(id).matches() ? "spotify:track:" + id : "";
    }

    static String normalizedIsrc(String value) {
        if (value == null) return "";
        String normalized = ISRC_SEPARATORS.matcher(value).replaceAll("").toUpperCase(Locale.ROOT);
        return ISRC_VALUE.matcher(normalized).matches() ? normalized : "";
    }

    private static String externalIsrc(Iterable<?> ids, String typeField, String idField) throws ReflectiveOperationException {
        for (Object external : ids) {
            if ("isrc".equalsIgnoreCase((String) field(external, typeField))) {
                String isrc = normalizedIsrc((String) field(external, idField));
                if (!isrc.isEmpty()) return isrc;
            }
        }
        return "";
    }

    static Object field(Object value, String name) throws ReflectiveOperationException {
        return members(value.getClass()).field(value.getClass(), name).get(value);
    }

    private static Members members(Class<?> type) {
        synchronized (MEMBERS) {
            Members cached = MEMBERS.get(type);
            if (cached == null) {
                cached = new Members();
                MEMBERS.put(type, cached);
            }
            return cached;
        }
    }

    private static final class Members {
        final Map<String, Field> fields = new LinkedHashMap<String, Field>(8, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, Field> entry) { return size() > 16; }
        };
        Method bytesMethod;
        boolean bytesResolved;

        synchronized Field field(Class<?> type, String name) throws ReflectiveOperationException {
            Field cached = fields.get(name);
            if (cached == null) {
                cached = type.getDeclaredField(name);
                cached.setAccessible(true);
                fields.put(name, cached);
            }
            return cached;
        }

        synchronized Method bytesMethod(Class<?> type) {
            if (!bytesResolved) {
                for (Method method : type.getMethods()) {
                    if (method.getParameterTypes().length == 0 && method.getReturnType() == byte[].class) {
                        method.setAccessible(true);
                        bytesMethod = method;
                        break;
                    }
                }
                bytesResolved = true;
            }
            return bytesMethod;
        }
    }

    static String base62(byte[] bytes) {
        BigInteger value = new BigInteger(1, bytes);
        BigInteger radix = BigInteger.valueOf(62);
        char[] output = new char[22];
        for (int i = output.length - 1; i >= 0; i--) {
            BigInteger[] division = value.divideAndRemainder(radix);
            output[i] = BASE62.charAt(division[1].intValue());
            value = division[0];
        }
        return new String(output);
    }
}
