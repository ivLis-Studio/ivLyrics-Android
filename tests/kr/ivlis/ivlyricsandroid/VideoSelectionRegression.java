package kr.ivlis.ivlyricsandroid;
import org.json.JSONObject;
import java.util.*;
public final class VideoSelectionRegression {
    public static void main(String[] args) throws Exception {
        String id = "Abc_123-XyZ";
        for (String value : List.of(id, "https://youtu.be/" + id + "?t=3", "https://www.youtube.com/watch?feature=shared&v=" + id,
                "https://m.youtube.com/shorts/" + id, "https://youtube.com/embed/" + id,
                "https://youtube.com/watch?v=Abc%5F123%2DXyZ")) {
            if (!id.equals(YouTubeVideoSelection.extractId(value))) throw new AssertionError(value);
        }
        for (String value : List.of("", "bad", "https://youtube.com.evil/watch?v=" + id, "file://youtube.com/watch?v=" + id,
                "https://youtube.com/watch?v=invalid", "https://youtube.com/watch?v=%ZZ", "https://example.com/" + id)) {
            if (!YouTubeVideoSelection.extractId(value).isEmpty()) throw new AssertionError(value);
        }
        JSONObject item = new JSONObject().put("youtubeVideoId", id).put("startTime", 12.5).put("youtubeTitle", "Fixture");
        var community = YouTubeBackgroundRepository.VideoInfo.fromJson("USAAA2600001", item);
        if (!community.hasCaptionStartTime || community.captionStartTimeSeconds != 12.5) throw new AssertionError("community timing lost");
        var saved = YouTubeBackgroundRepository.VideoInfo.fromJson("", community.toJson());
        if (!saved.youtubeVideoId.equals(id) || saved.captionStartTimeSeconds != 12.5) throw new AssertionError("saved timing lost");
        var local = new YouTubeBackgroundRepository.VideoInfo("", "track", id, id, false, 0, false, "");
        if (!local.youtubeVideoId.equals(id) || local.hasCaptionStartTime) throw new AssertionError("local video without ISRC");
        String[] languages = {"ko", "en", "ja", "zh-CN", "zh-TW", "hi", "es", "fr", "ar", "fa", "de", "ru", "sv", "pt", "bn", "cs", "it", "th", "vi", "id", "ms", "tr"};
        Map<String,String> baseline = new HashMap<>(); VideoSelectionTranslations.apply("ko", baseline);
        for (String language : languages) {
            Map<String,String> translated = new HashMap<>(); VideoSelectionTranslations.apply(language, translated);
            if (!translated.keySet().equals(baseline.keySet()) || translated.values().stream().anyMatch(String::isBlank)) throw new AssertionError(language);
        }
        System.out.println("Video selection regression: URL parsing, community timing, persistence and all 22 languages passed");
    }
}
