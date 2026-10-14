package kr.ivlis.ivlyricsandroid;

import org.junit.Test;
import static org.junit.Assert.*;

public final class RecordRotationTest {
    @Test public void speedChangesPausesAndMotionSwitchesKeepThePhase() {
        RecordRotation rotation = new RecordRotation();
        assertEquals(0, rotation.advance(100, true), 0.001);
        assertEquals(9, rotation.advance(1100, true), 0.001);
        rotation.setSpeed(200);
        assertEquals(9, rotation.advance(1100, true), 0.001);
        assertEquals(27, rotation.advance(2100, true), 0.001);
        assertEquals(27, rotation.advance(3100, false), 0.001);
        rotation.setSpeed(50);
        assertEquals(27, rotation.advance(4100, false), 0.001);
        assertEquals(31.5, rotation.advance(5100, true), 0.001);
        assertEquals(31.5, rotation.advance(6100, false), 0.001);
        assertEquals(36, rotation.advance(7100, true), 0.001);
    }

    @Test public void defaultSpeedWrapsAfterFortySecondsAndClampsTheSlider() {
        RecordRotation rotation = new RecordRotation();
        rotation.advance(0, true);
        assertEquals(0, rotation.advance(40000, true), 0.001);
        assertEquals(90, rotation.advance(50000, true), 0.001);
        assertEquals(25, RecordRotation.speed(-10));
        assertEquals(100, RecordRotation.speed(100));
        assertEquals(200, RecordRotation.speed(999));
    }

    @Test public void trackChangeAnimationHasItsOwnSwitchWithinTheMasterAndMotionGates() {
        assertTrue(RecordRotation.animateTrackChange(true, true, true, true, true));
        assertFalse(RecordRotation.animateTrackChange(true, false, true, true, true));
        assertFalse(RecordRotation.animateTrackChange(false, true, true, true, true));
        assertFalse(RecordRotation.animateTrackChange(true, true, false, true, true));
        assertFalse(RecordRotation.animateTrackChange(true, true, true, false, true));
        assertFalse(RecordRotation.animateTrackChange(true, true, true, true, false));
    }

    @Test public void simplifiedLabelsKeepMeaningfulTitleTextAndTranslatedUnicode() {
        assertEquals("곡 (너에게)", RecordRotation.displayTitle("곡 (너에게)", true));
        assertEquals("곡", RecordRotation.displayTitle("곡 (feat. Guest)", true));
        assertEquals("曲", RecordRotation.displayTitle("曲 - 2024 Remastered", true));
        assertEquals("Song", RecordRotation.displayTitle("Song (Live)", true));
        assertEquals("Song (Live)", RecordRotation.displayTitle("Song (Live)", false));
        assertEquals("", RecordRotation.displayTitle(null, true));
    }
}
