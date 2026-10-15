package kr.ivlis.ivlyricsandroid;

import org.junit.Test;
import static org.junit.Assert.*;

public final class WelcomeGreetingRotationTest {
    private static final class Fixture implements WelcomeGreetingRotation.Scheduler, WelcomeGreetingRotation.Renderer {
        Runnable pending;
        long delay;
        int renders;
        boolean korean;
        boolean animate;
        final WelcomeGreetingRotation rotation;

        Fixture(boolean korean, boolean interacted) {
            rotation = new WelcomeGreetingRotation(korean, interacted, this, this);
        }
        @Override public void postDelayed(Runnable callback, long delayMs) {
            assertNull("only one timer", pending);
            pending = callback;
            delay = delayMs;
        }
        @Override public void removeCallbacks(Runnable callback) {
            assertSame(pending, callback);
            pending = null;
        }
        @Override public void show(boolean korean, boolean animate) {
            this.korean = korean;
            this.animate = animate;
            renders++;
        }
        void advance() {
            Runnable callback = pending;
            assertNotNull(callback);
            pending = null;
            callback.run();
        }
    }

    @Test public void alternatesOnlyPresentationEveryThreeAndAHalfSeconds() {
        Fixture fixture = new Fixture(false, false);
        assertNull(fixture.pending);
        fixture.rotation.setActive(true);
        Runnable timer = fixture.pending;
        fixture.rotation.setActive(true);
        assertSame("layout updates do not reset cadence", timer, fixture.pending);
        assertEquals(3500, fixture.delay);
        fixture.advance();
        assertTrue(fixture.korean);
        assertTrue(fixture.animate);
        fixture.advance();
        assertFalse(fixture.korean);
        assertFalse(fixture.rotation.hasLanguageInteraction());
    }

    @Test public void deactivationCancelsAndSettlesEvenAlreadyDequeuedCallbacks() {
        Fixture fixture = new Fixture(true, false);
        fixture.rotation.setActive(true);
        Runnable stale = fixture.pending;
        fixture.rotation.setActive(false);
        assertNull(fixture.pending);
        assertTrue(fixture.korean);
        assertFalse("settle an interrupted crossfade", fixture.animate);
        fixture.rotation.setActive(true);
        Runnable current = fixture.pending;
        stale.run();
        assertSame("old callbacks cannot advance a resumed view", current, fixture.pending);
        assertTrue(fixture.rotation.isKorean());
        fixture.rotation.setActive(false);
    }

    @Test public void pickerInteractionLocksMatchingGreetingAcrossBackRebuildAndRestore() {
        Fixture fixture = new Fixture(true, false);
        fixture.rotation.setActive(true);
        Runnable stale = fixture.pending;
        fixture.rotation.interactWithLanguage("de");
        assertFalse("all other supported languages use Hello", fixture.korean);
        assertFalse(fixture.animate);
        assertTrue(fixture.rotation.hasLanguageInteraction());
        assertNull(fixture.pending);
        stale.run();
        for (int rebuild = 0; rebuild < 3; rebuild++) {
            fixture.rotation.setActive(false);
            fixture.rotation.setActive(true);
            assertNull(fixture.pending);
            assertFalse(fixture.rotation.isKorean());
        }
        fixture.rotation.interactWithLanguage("ko");
        assertTrue(fixture.korean);
        Fixture restored = new Fixture(fixture.rotation.isKorean(), fixture.rotation.hasLanguageInteraction());
        restored.rotation.setActive(true);
        assertNull(restored.pending);
        assertTrue(restored.rotation.isKorean());
    }

    @Test public void phaseExitSettlesToUiLanguageEvenAfterAResumablePause() {
        Fixture fixture = new Fixture(false, false);
        fixture.rotation.setActive(true);
        fixture.advance();
        assertTrue("presentation has rotated away from the English UI language", fixture.korean);
        fixture.rotation.setActive(false);
        assertFalse(fixture.rotation.hasLanguageInteraction());
        fixture.rotation.interactWithLanguage("en");
        fixture.rotation.setActive(false);
        assertFalse(fixture.korean);
        assertFalse(fixture.animate);
        assertTrue(fixture.rotation.hasLanguageInteraction());
        fixture.rotation.setActive(true);
        assertNull("returning to welcome cannot restart rotation", fixture.pending);
        assertFalse(fixture.rotation.isKorean());
    }
}
