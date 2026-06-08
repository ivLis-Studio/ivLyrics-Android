package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LyricsResult {
    final List<LyricsLine> lines;
    final String providerLabel;
    final String detail;
    final boolean karaoke;
    final String isrc;
    final String spotifyTrackId;

    LyricsResult(List<LyricsLine> lines, String providerLabel, String detail, boolean karaoke) {
        this(lines, providerLabel, detail, karaoke, "", "");
    }

    LyricsResult(
            List<LyricsLine> lines,
            String providerLabel,
            String detail,
            boolean karaoke,
            String isrc,
            String spotifyTrackId
    ) {
        this.lines = lines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(lines));
        this.providerLabel = providerLabel == null ? "" : providerLabel;
        this.detail = detail == null ? "" : detail;
        this.karaoke = karaoke;
        this.isrc = TrackSnapshot.normalizeIsrc(isrc);
        this.spotifyTrackId = spotifyTrackId == null ? "" : spotifyTrackId.trim();
    }

    static LyricsResult empty(String detail) {
        return new LyricsResult(Collections.emptyList(), "", detail, false);
    }
}
