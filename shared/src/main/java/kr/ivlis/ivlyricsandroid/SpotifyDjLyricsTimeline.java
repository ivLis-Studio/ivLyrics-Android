package kr.ivlis.ivlyricsandroid;

final class SpotifyDjLyricsTimeline {
    private static final long DISCONTINUITY_THRESHOLD_MS = 1_500L;
    private static final long HANDOFF_WINDOW_MS = 15_000L;
    private static final long DJ_SESSION_RETENTION_MS = 30L * 60L * 1_000L;

    private String trackKey = "";
    private boolean currentDjSegment;
    private boolean lastPlaying;
    private boolean handoffPending;
    private long handoffStartedAtMs;
    private long djSessionActiveUntilMs;
    private long currentFadeOverlapMs;
    private long targetOffsetMs;
    private long lyricsOffsetMs;
    private long lastPlayerPositionMs;
    private long lastLyricsPositionMs;
    private long lastSampleAtMs;

    long update(
            String incomingTrackKey,
            long playerPositionMs,
            boolean playing,
            boolean spotifyAutomix,
            boolean spotifyDjSegment,
            long fadeInStartMs,
            long fadeInCueMs,
            long fadeOverlapMs,
            long nowMs
    ) {
        String safeTrackKey = incomingTrackKey == null ? "" : incomingTrackKey;
        long safePlayerPositionMs = Math.max(0L, playerPositionMs);
        long safeFadeInStartMs = Math.max(0L, fadeInStartMs);
        long safeFadeInCueMs = Math.max(0L, fadeInCueMs);
        long safeFadeOverlapMs = Math.max(0L, fadeOverlapMs);
        long safeNowMs = Math.max(0L, nowMs);

        if (spotifyAutomix || spotifyDjSegment) {
            djSessionActiveUntilMs = safeNowMs + DJ_SESSION_RETENTION_MS;
        }
        boolean djSessionActive =
                spotifyAutomix ||
                spotifyDjSegment ||
                (djSessionActiveUntilMs > 0L && safeNowMs <= djSessionActiveUntilMs);
        boolean trackChanged = !safeTrackKey.equals(trackKey);

        if (trackChanged) {
            boolean followsDjSegment = currentDjSegment;
            long previousFadeOverlapMs = currentFadeOverlapMs;
            targetOffsetMs = djSessionActive && !spotifyDjSegment
                    ? Math.max(safeFadeInCueMs, safeFadeInStartMs + previousFadeOverlapMs)
                    : 0L;
            handoffPending =
                    djSessionActive &&
                    !spotifyDjSegment &&
                    !followsDjSegment;
            lyricsOffsetMs = handoffPending ? safeFadeInStartMs : targetOffsetMs;
            handoffStartedAtMs = safeNowMs;
            trackKey = safeTrackKey;
        } else if (lastSampleAtMs > 0L) {
            long elapsedMs = Math.max(0L, safeNowMs - lastSampleAtMs);
            long expectedElapsedMs = playing && lastPlaying ? elapsedMs : 0L;
            long driftMs = safePlayerPositionMs - lastPlayerPositionMs - expectedElapsedMs;
            boolean withinHandoff =
                    handoffPending &&
                    safeNowMs - handoffStartedAtMs <= HANDOFF_WINDOW_MS;

            if (withinHandoff && driftMs <= -DISCONTINUITY_THRESHOLD_MS) {
                long continuityOffsetMs = Math.max(
                        0L,
                        lastLyricsPositionMs + expectedElapsedMs - safePlayerPositionMs
                );
                lyricsOffsetMs = Math.max(targetOffsetMs, continuityOffsetMs);
                handoffPending = false;
            }
        }

        if (handoffPending && safeNowMs - handoffStartedAtMs > HANDOFF_WINDOW_MS) {
            lyricsOffsetMs = Math.max(lyricsOffsetMs, targetOffsetMs);
            handoffPending = false;
        }

        currentDjSegment = spotifyDjSegment;
        currentFadeOverlapMs = safeFadeOverlapMs;
        lastPlaying = playing;
        lastPlayerPositionMs = safePlayerPositionMs;
        lastLyricsPositionMs = safePlayerPositionMs + lyricsOffsetMs;
        lastSampleAtMs = safeNowMs;
        return lastLyricsPositionMs;
    }

    long offsetMs() {
        return lyricsOffsetMs;
    }

    void reset() {
        trackKey = "";
        currentDjSegment = false;
        lastPlaying = false;
        handoffPending = false;
        handoffStartedAtMs = 0L;
        djSessionActiveUntilMs = 0L;
        currentFadeOverlapMs = 0L;
        targetOffsetMs = 0L;
        lyricsOffsetMs = 0L;
        lastPlayerPositionMs = 0L;
        lastLyricsPositionMs = 0L;
        lastSampleAtMs = 0L;
    }
}
