package kr.ivlis.ivlyricsandroid;

import org.junit.Test;
import static org.junit.Assert.*;

public final class AppearanceOptionsTest {
    @Test public void oldDefaultsAndInvalidStoredValuesKeepTheExistingAppearance() {
        assertEquals("gradient-background", AppearanceOptions.fallback(null));
        assertEquals("gradient-background", AppearanceOptions.fallback("video-background"));
        assertEquals("blur-gradient-background", AppearanceOptions.fallback("blur-gradient-background"));
        assertEquals("solid-background", AppearanceOptions.fallback("solid-background"));
        assertEquals("player", AppearanceOptions.titleAction("unknown"));
        assertEquals("disabled", AppearanceOptions.titleAction("disabled"));
        assertEquals("context", AppearanceOptions.titleAction("context"));
        assertEquals("fill", AppearanceOptions.aspect(null));
        assertEquals("fit", AppearanceOptions.aspect("fit"));
        assertEquals("stretch", AppearanceOptions.aspect("stretch"));
    }

    @Test public void brightnessHasNoHiddenCapAndDimsOnlyByItsValue() {
        assertEquals(0, AppearanceOptions.videoDimAlpha(100));
        assertEquals(255, AppearanceOptions.videoDimAlpha(0));
        assertEquals(128, AppearanceOptions.videoDimAlpha(50));
        assertEquals(0, AppearanceOptions.videoDimAlpha(120));
        assertEquals(255, AppearanceOptions.videoDimAlpha(-1));
        for (int percent = 1; percent <= 100; percent++) {
            assertTrue(AppearanceOptions.videoDimAlpha(percent) <= AppearanceOptions.videoDimAlpha(percent - 1));
        }
    }

    @Test public void navigationOnlyAcceptsAlbumOrContextDestinations() {
        String id = "1234567890123456789012";
        assertEquals("spotify:album:" + id, AppearanceOptions.spotifyDestination("spotify:album:" + id, true));
        assertEquals("spotify:playlist:" + id, AppearanceOptions.spotifyDestination("https://open.spotify.com/playlist/" + id + "?si=abc", false));
        assertEquals("", AppearanceOptions.spotifyDestination("spotify:playlist:" + id, true));
        assertEquals("", AppearanceOptions.spotifyDestination("spotify:track:" + id, false));
        assertEquals("", AppearanceOptions.spotifyDestination("javascript:alert(1)", false));
        assertEquals("", AppearanceOptions.spotifyDestination("https://example.com/album/" + id, true));
        assertEquals("", AppearanceOptions.spotifyDestination("", false));
    }

    @Test public void fixedArtworkStillsOnlyTheAlbumArtBackground() {
        assertTrue(AppearanceOptions.holdsArtworkStill(AiLyricsSettings.BACKGROUND_MODE_GRADIENT, true));
        assertFalse(AppearanceOptions.holdsArtworkStill(AiLyricsSettings.BACKGROUND_MODE_GRADIENT, false));
        assertFalse(AppearanceOptions.holdsArtworkStill(AiLyricsSettings.BACKGROUND_MODE_BLUR_GRADIENT, true));
        assertFalse(AppearanceOptions.holdsArtworkStill(AiLyricsSettings.BACKGROUND_MODE_SOLID, true));
        assertFalse(AppearanceOptions.holdsArtworkStill(AiLyricsSettings.BACKGROUND_MODE_VIDEO, true));

        AiLyricsSettings.BackgroundSettings moving = new AiLyricsSettings.BackgroundSettings(
                AiLyricsSettings.BACKGROUND_MODE_GRADIENT, 40, 30, true, false, "#123456", 120);
        assertSame(moving, AiLyricsSettings.withArtworkFixed(moving, false));
        AiLyricsSettings.BackgroundSettings still = AiLyricsSettings.withArtworkFixed(moving, true);
        assertTrue(still.reduceMotion);
        assertEquals(moving.mode, still.mode);
        assertEquals(moving.brightness, still.brightness);
        assertEquals(moving.blur, still.blur);
        assertEquals(moving.noise, still.noise);
        assertEquals(moving.solidColor, still.solidColor);
        assertEquals(moving.videoScale, still.videoScale);
        AiLyricsSettings.BackgroundSettings gradient = new AiLyricsSettings.BackgroundSettings(
                AiLyricsSettings.BACKGROUND_MODE_BLUR_GRADIENT, 40, 30, false, false, "#123456", 120);
        assertSame(gradient, AiLyricsSettings.withArtworkFixed(gradient, true));
        assertNull(AiLyricsSettings.withArtworkFixed(null, true));
    }
}
