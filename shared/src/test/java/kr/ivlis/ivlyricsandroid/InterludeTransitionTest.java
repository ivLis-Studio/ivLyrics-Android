package kr.ivlis.ivlyricsandroid;

import org.junit.Test;
import static org.junit.Assert.*;

public class InterludeTransitionTest {
    @Test public void indicatorEntersAndExitsContinuouslyWithoutChangingTheInterval() {
        assertEquals(0, InterludeTransition.enter(1000, 1000, true), 0.0001);
        assertEquals(0.5, InterludeTransition.enter(1000, 1090, true), 0.0001);
        assertEquals(1, InterludeTransition.enter(1000, 1180, true), 0.0001);
        assertEquals(1, InterludeTransition.exit(0, true), 0.0001);
        assertEquals(0.5, InterludeTransition.exit(160, true), 0.0001);
        assertEquals(0, InterludeTransition.exit(320, true), 0.0001);
        float previous = 1f;
        for (long elapsed = 0; elapsed <= 340; elapsed += 8) {
            float opacity = InterludeTransition.exit(elapsed, true);
            assertTrue(opacity >= 0 && opacity <= previous);
            assertTrue(previous - opacity < 0.05f);
            assertTrue(InterludeTransition.scale(opacity) >= 0.94f && InterludeTransition.scale(opacity) <= 1f);
            previous = opacity;
        }
    }

    @Test public void reducedMotionAndSeeksDoNotLeaveAStaleIndicator() {
        assertEquals(1, InterludeTransition.enter(1000, 1000, false), 0.0001);
        assertEquals(0, InterludeTransition.exit(0, false), 0.0001);
        assertFalse(InterludeTransition.canExit(4000, 4000, false));
        assertFalse(InterludeTransition.canExit(4000, 3999, true));
        assertTrue(InterludeTransition.canExit(4000, 4000, true));
        assertTrue(InterludeTransition.canExit(4000, 4250, true));
        assertFalse(InterludeTransition.canExit(4000, 6000, true));
    }
}
