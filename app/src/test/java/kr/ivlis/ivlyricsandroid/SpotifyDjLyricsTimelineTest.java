package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SpotifyDjLyricsTimelineTest {
    @Test
    public void regularPlaybackUsesUnmodifiedPosition() {
        SpotifyDjLyricsTimeline timeline = new SpotifyDjLyricsTimeline();

        assertEquals(0L, update(timeline, "track-a", 0L, true, false, false, 0L, 0L, 0L, 0L));
        assertEquals(2_500L, update(timeline, "track-a", 2_500L, true, false, false, 0L, 0L, 0L, 2_500L));
        assertEquals(100L, update(timeline, "track-a", 100L, true, false, false, 0L, 0L, 0L, 2_600L));
        assertEquals(250L, update(timeline, "track-b", 250L, true, false, false, 0L, 0L, 0L, 2_700L));
        assertEquals(0L, timeline.offsetMs());
    }

    @Test
    public void directDjHandoffDoesNotRewindLyricsWhenPlayerClockResets() {
        SpotifyDjLyricsTimeline timeline = new SpotifyDjLyricsTimeline();
        update(timeline, "track-a", 150_000L, true, true, false, 0L, 0L, 3_270L, 0L);

        assertEquals(10L, update(timeline, "track-b", 10L, true, true, false, 0L, 3_270L, 3_400L, 100L));
        assertEquals(2_500L, update(timeline, "track-b", 2_500L, true, true, false, 0L, 3_270L, 3_400L, 2_590L));
        assertEquals(3_310L, update(timeline, "track-b", 40L, true, true, false, 0L, 3_270L, 3_400L, 2_690L));
        assertEquals(3_270L, timeline.offsetMs());
    }

    @Test
    public void djNarrationKeepsAlreadyAdvancedIncomingTrackPosition() {
        SpotifyDjLyricsTimeline timeline = new SpotifyDjLyricsTimeline();
        update(timeline, "dj-segment", 0L, true, false, true, 0L, 0L, 1_923L, 0L);

        assertEquals(5_891L, update(timeline, "track-a", 3_441L, true, true, false, 0L, 2_450L, 3_400L, 7_000L));
        assertEquals(2_450L, timeline.offsetMs());
    }

    @Test
    public void detectedDjSessionCoversTracksWithoutCustomAutomixMetadata() {
        SpotifyDjLyricsTimeline timeline = new SpotifyDjLyricsTimeline();
        update(timeline, "dj-segment", 0L, true, false, true, 0L, 0L, 0L, 0L);
        update(timeline, "track-a", 1_000L, true, false, false, 0L, 0L, 0L, 1_000L);

        assertEquals(5L, update(timeline, "track-b", 5L, true, false, false, 0L, 0L, 0L, 180_000L));
        assertEquals(2_405L, update(timeline, "track-b", 2_405L, true, false, false, 0L, 0L, 0L, 182_400L));
        assertEquals(2_505L, update(timeline, "track-b", 100L, true, false, false, 0L, 0L, 0L, 182_500L));
        assertEquals(2_405L, timeline.offsetMs());
    }

    private static long update(
            SpotifyDjLyricsTimeline timeline,
            String trackKey,
            long playerPositionMs,
            boolean playing,
            boolean spotifyAutomix,
            boolean spotifyDjSegment,
            long fadeInStartMs,
            long fadeInCueMs,
            long fadeOverlapMs,
            long nowMs
    ) {
        return timeline.update(
                trackKey,
                playerPositionMs,
                playing,
                spotifyAutomix,
                spotifyDjSegment,
                fadeInStartMs,
                fadeInCueMs,
                fadeOverlapMs,
                nowMs
        );
    }
}
