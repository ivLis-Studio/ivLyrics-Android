package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ResearchDocumentTest {
    @Test
    public void derivesThumbnailForYouTubeMedia() {
        ResearchDocument.MediaItem media = new ResearchDocument.MediaItem(
                "youtube", "Official video", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "", ""
        );
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", media.imageUrl);
    }

    @Test
    public void researchLyricsContainOnlyPlainText() {
        LyricsResult lyrics = new LyricsResult(Arrays.asList(
                new LyricsLine(12_500L, 18_000L, "first line", Collections.emptyList()),
                new LyricsLine(18_420L, 24_000L, "second line", Collections.emptyList())
        ), "test", "", false);

        String plainText = ResearchDocument.lyricPlainText(lyrics);
        assertEquals("first line\nsecond line", plainText);
        assertFalse(plainText.contains("start_time_ms"));
        assertFalse(plainText.contains("line_index"));
    }
}
