package kr.ivlis.ivlyricsandroid;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Successful metadata reports within one load; never a lyrics or contributor-privacy cache. */
final class SyncMetadataReportScope {
    private final Set<String> accepted = new HashSet<>();
    private long generation = Long.MIN_VALUE;

    static String fingerprint(Map<String, String> params) {
        StringBuilder key = new StringBuilder();
        for (String field : new String[]{"isrc", "trackId", "title", "artist", "album"}) {
            String value = params.get(field);
            if (value == null) value = "";
            key.append(value.length()).append(':').append(value);
        }
        return key.toString();
    }

    boolean isAccepted(String fingerprint, long currentGeneration) {
        if (generation != currentGeneration) {
            accepted.clear();
            generation = currentGeneration;
        }
        return accepted.contains(fingerprint);
    }

    void accept(String fingerprint, long requestGeneration, long currentGeneration) {
        if (requestGeneration != currentGeneration) return;
        isAccepted(fingerprint, currentGeneration);
        accepted.add(fingerprint);
    }
}
