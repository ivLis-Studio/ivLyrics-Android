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
    final String syncType;
    final int syncPoints;

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
            String syncType,
            int syncPoints
    ) {
        this(
                lines,
                providerLabel,
                detail,
                karaoke,
                isrc,
                spotifyTrackId,
                contributors,
                "",
                "",
                syncType,
                syncPoints
        );
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
        this(
                lines,
                providerLabel,
                detail,
                karaoke,
                isrc,
                spotifyTrackId,
                contributors,
                providerId,
                selectionPolicyKey,
                "unknown",
                0
        );
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
            String selectionPolicyKey,
            String syncType,
            int syncPoints
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
        this.syncType = normalizeSyncType(syncType);
        this.syncPoints = Math.max(0, syncPoints);
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
                selectionPolicyKey,
                syncType,
                syncPoints
        );
    }

    static LyricsResult empty(String detail) {
        return new LyricsResult(Collections.emptyList(), "", detail, false);
    }

    static final class SyncContributor {
        static final class CreatorDecoration {
            final String mode;
            final String solidColor;
            final String gradientStartColor;
            final String gradientEndColor;
            final int gradientAngle;

            CreatorDecoration(
                    String mode,
                    String solidColor,
                    String gradientStartColor,
                    String gradientEndColor,
                    int gradientAngle
            ) {
                this.mode = mode == null ? "" : mode.trim();
                this.solidColor = solidColor == null ? "" : solidColor.trim();
                this.gradientStartColor = gradientStartColor == null ? "" : gradientStartColor.trim();
                this.gradientEndColor = gradientEndColor == null ? "" : gradientEndColor.trim();
                this.gradientAngle = Math.max(0, Math.min(360, gradientAngle));
            }
        }

        final String name;
        final String userHash;
        final boolean profileAvailable;
        final boolean anonymous;
        final boolean isPrivate;
        final CreatorDecoration decoration;
        final String syncType;
        final int syncPoints;

        SyncContributor(String name, String userHash, boolean profileAvailable) {
            this(name, userHash, profileAvailable, false, false);
        }

        SyncContributor(
                String name,
                String userHash,
                boolean profileAvailable,
                boolean anonymous,
                boolean isPrivate
        ) {
			this(name, userHash, profileAvailable, anonymous, isPrivate, null, "unknown", 0);
        }

        SyncContributor(
                String name,
                String userHash,
                boolean profileAvailable,
                boolean anonymous,
                boolean isPrivate,
                CreatorDecoration decoration
        ) {
			this(name, userHash, profileAvailable, anonymous, isPrivate, decoration, "unknown", 0);
		}

		SyncContributor(
				String name,
				String userHash,
				boolean profileAvailable,
				boolean anonymous,
				boolean isPrivate,
				CreatorDecoration decoration,
				String syncType,
				int syncPoints
		) {
            String safeName = name == null ? "" : name.trim();
            String safeHash = userHash == null ? "" : userHash.trim();
            boolean hidesIdentity = anonymous || isPrivate;
            this.name = hidesIdentity || safeName.isEmpty() ? "Anonymous" : safeName;
            this.userHash = hidesIdentity ? "" : safeHash;
            this.profileAvailable = !hidesIdentity && profileAvailable && !safeHash.isEmpty();
            this.anonymous = hidesIdentity || ("Anonymous".equalsIgnoreCase(this.name) && this.userHash.isEmpty());
            this.isPrivate = isPrivate;
            this.decoration = hidesIdentity ? null : decoration;
            this.syncType = normalizeSyncType(syncType);
            this.syncPoints = Math.max(0, syncPoints);
        }
    }

    private static String normalizeSyncType(String syncType) {
        String normalizedType = syncType == null ? "" : syncType.trim().toLowerCase(Locale.ROOT);
        return "line".equals(normalizedType)
                || "word".equals(normalizedType)
                || "character".equals(normalizedType)
                || "mixed".equals(normalizedType)
                ? normalizedType
                : "unknown";
    }
}
