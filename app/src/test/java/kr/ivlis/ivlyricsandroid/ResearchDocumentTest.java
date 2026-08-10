package kr.ivlis.ivlyricsandroid;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ResearchDocumentTest {
    @Test
    public void derivesThumbnailForYouTubeMedia() {
        ResearchDocument.MediaItem media = new ResearchDocument.MediaItem(
                "youtube", "Official video", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "", ""
        );
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", media.imageUrl);
    }
}
