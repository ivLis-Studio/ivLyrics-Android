package kr.ivlis.ivlyricsandroid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SpotifyDjLyricsTimelineTest {
    private SpotifyDjLyricsTimeline handoff() {
        SpotifyDjLyricsTimeline timeline = new SpotifyDjLyricsTimeline();
        timeline.update("first", 10_000, true, true, false, true, 0, 0, 1_000, 1_000);
        assertEquals(6_300, timeline.update("next", 6_000, true, false, false, false,
                300, 1_300, 0, 2_000));
        return timeline;
    }

    @Test public void unknownContextRetainsNaturalHandoffButKnownNonDjClearsIt() {
        SpotifyDjLyricsTimeline timeline = handoff();
        assertEquals(6_400, timeline.update("next", 1_000, true, false, false, false,
                300, 1_300, 0, 2_100));
        assertEquals(5_400, timeline.offsetMs());
        assertEquals(6_500, timeline.update("next", 1_100, true, false, false, false,
                0, 0, 0, 2_200));
        assertEquals(1_200, timeline.update("next", 1_200, true, false, false, true,
                0, 0, 0, 2_300));
        assertEquals(0, timeline.offsetMs());
        // Neither the old target offset nor its 30-minute session can return on later sparse samples.
        assertEquals(100, timeline.update("third", 100, true, false, false, false,
                500, 900, 0, 20_000));
        assertEquals(200, timeline.update("third", 200, true, false, false, false,
                500, 900, 0, 40_000));
    }

    @Test public void knownNonDjClearsPendingTargetBeforeTimeoutAndTrackSwitch() {
        SpotifyDjLyricsTimeline timeline = handoff();
        assertEquals(6_100, timeline.update("next", 6_100, true, false, false, true,
                300, 1_300, 0, 2_100));
        assertEquals(6_200, timeline.update("next", 6_200, true, false, false, false,
                300, 1_300, 0, 20_000));
        assertEquals(0, timeline.offsetMs());
    }

    @Test public void explicitBackwardSeekDoesNotBecomeHandoffOrDiscardExistingOffset() {
        SpotifyDjLyricsTimeline timeline = handoff();
        timeline.registerExplicitSeek("next", 1_000, 2_050);
        assertEquals(1_350, timeline.update("next", 1_050, true, true, false, true,
                300, 1_300, 0, 2_100));
        assertEquals(300, timeline.offsetMs());
        assertEquals(2_300, timeline.update("next", 2_000, true, false, false, false,
                0, 0, 0, 40_000));
    }

    @Test public void explicitSeekOnPausedTrackAndRelativeSkipsUseSameRegistration() {
        SpotifyDjLyricsTimeline timeline = handoff();
        timeline.registerExplicitSeek("next", 2_000, 2_050);
        assertEquals(2_300, timeline.update("next", 2_000, false, true, false, true,
                300, 1_300, 0, 2_100));
        timeline.registerExplicitSeek("next", 7_000, 2_150);
        assertEquals(7_300, timeline.update("next", 7_000, false, true, false, true,
                300, 1_300, 0, 2_200));
        timeline.registerExplicitSeek("next", 0, 2_250);
        assertEquals(300, timeline.update("next", 0, false, true, false, true,
                300, 1_300, 0, 2_300));
    }

    @Test public void staleSeekCannotSuppressDifferentTracksNaturalHandoff() {
        SpotifyDjLyricsTimeline timeline = handoff();
        timeline.registerExplicitSeek("first", 1_000, 2_050);
        assertEquals(6_400, timeline.update("next", 1_000, true, false, false, false,
                300, 1_300, 0, 2_100));
    }

    @Test public void djSegmentAndAutomixRemainAuthoritativeWhenContextIsKnown() {
        SpotifyDjLyricsTimeline timeline = handoff();
        assertEquals(6_400, timeline.update("next", 1_000, true, true, false, true,
                300, 1_300, 0, 2_100));
        timeline.reset();
        timeline.update("speech", 2_000, true, false, true, true, 0, 0, 800, 3_000);
        assertEquals(1_000, timeline.update("song", 0, true, false, false, false,
                200, 1_000, 0, 4_000));
        timeline.reset();
        assertEquals(0, timeline.offsetMs());
        assertEquals(100, timeline.update("song", 100, true, false, false, false,
                200, 1_000, 0, 5_000));
    }
}
