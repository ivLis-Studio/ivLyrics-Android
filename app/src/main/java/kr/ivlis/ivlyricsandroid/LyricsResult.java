package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class LyricsResult {
    final List<LyricsLine> lines;
    final String providerLabel;
    final String detail;
    final boolean karaoke;
    final String isrc;
    final String spotifyTrackId;
    final List<SyncContributor> contributors;
    final String providerId;
    final String selectionPolicyKey;

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
        this(lines, providerLabel, detail, karaoke, isrc, spotifyTrackId, Collections.emptyList());
    }

    LyricsResult(
            List<LyricsLine> lines,
            String providerLabel,
            String detail,
            boolean karaoke,
            String isrc,
            String spotifyTrackId,
            List<SyncContributor> contributors
    ) {
        this(lines, providerLabel, detail, karaoke, isrc, spotifyTrackId, contributors, "", "");
    }

    LyricsResult(
            List<LyricsLine> lines,
            String providerLabel,
            String detail,
            boolean karaoke,
            String isrc,
            String spotifyTrackId,
            List<SyncContributor> contributors,
            String providerId,
            String selectionPolicyKey
    ) {
        this.lines = lines == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(lines));
        this.providerLabel = providerLabel == null ? "" : providerLabel;
        this.detail = detail == null ? "" : detail;
        this.karaoke = karaoke;
        this.isrc = TrackSnapshot.normalizeIsrc(isrc);
        this.spotifyTrackId = spotifyTrackId == null ? "" : spotifyTrackId.trim();
        this.contributors = contributors == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(contributors));
        this.providerId = providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
        this.selectionPolicyKey = selectionPolicyKey == null ? "" : selectionPolicyKey;
    }

    LyricsResult withSelection(String providerId, String selectionPolicyKey) {
        return new LyricsResult(
                lines,
                providerLabel,
                detail,
                karaoke,
                isrc,
                spotifyTrackId,
                contributors,
                providerId,
                selectionPolicyKey
        );
    }

    static LyricsResult empty(String detail) {
        return new LyricsResult(Collections.emptyList(), "", detail, false);
    }

    static final class SyncContributor {
        final String name;
        final String userHash;
        final boolean profileAvailable;

        SyncContributor(String name, String userHash, boolean profileAvailable) {
            String safeName = name == null ? "" : name.trim();
            String safeHash = userHash == null ? "" : userHash.trim();
            this.name = safeName.isEmpty() ? "Anonymous" : safeName;
            this.userHash = safeHash;
            this.profileAvailable = profileAvailable && !safeHash.isEmpty();
        }
    }
}
