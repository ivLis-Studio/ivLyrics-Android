package kr.ivlis.ivlyricsandroid;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Locale;

/** Public track identity extraction shared by direct, nested and TrackV4 metadata paths. */
final class NativeTrackIdentity {
    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    final String uri;
    final String isrc;

    private NativeTrackIdentity(String uri, String isrc) { this.uri = uri; this.isrc = isrc; }

    static NativeTrackIdentity fromProto(Object track) throws ReflectiveOperationException {
        String isrc = externalIsrc((Iterable<?>) field(track, "externalId_"), "type_", "id_");
        if (isrc.isEmpty()) return null;
        Object gid = field(track, "gid_");
        byte[] bytes = null;
        for (Method method : gid.getClass().getMethods()) {
            if (method.getParameterTypes().length == 0 && method.getReturnType() == byte[].class) {
                method.setAccessible(true);
                bytes = (byte[]) method.invoke(gid);
                break;
            }
        }
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
        return id.matches("[0-9a-zA-Z]{22}") ? "spotify:track:" + id : "";
    }

    static String normalizedIsrc(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        return normalized.matches("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}") ? normalized : "";
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
        Field field = value.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(value);
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
