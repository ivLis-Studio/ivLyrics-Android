package kr.ivlis.ivlyricsandroid;

/** Shared display decisions, independent of Android views and of lyrics generation. */
final class AppearanceOptions {
    static final String FALLBACK = "video_fallback_background";
    static final String TITLE_ACTION = "title_tap_action";
    static final String VIDEO_ASPECT = "video_aspect";
    static final String VIDEO_EDGES = "video_edge_to_edge";
    static final String LANDSCAPE_ORIGINAL = "landscape_show_original";
    static final String LANDSCAPE_ARTWORK = "landscape_show_artwork";
    static final String EMPTY_NOTICE = "show_lyrics_not_found";

    private AppearanceOptions() {}

    static String fallback(String value) {
        if ("blur-gradient-background".equals(value) || "solid-background".equals(value)) return value;
        return "gradient-background";
    }

    static String titleAction(String value) {
        if ("disabled".equals(value) || "album".equals(value) || "context".equals(value)) return value;
        return "player"; // Preserve the existing tap-to-return-to-Spotify default.
    }

    static String aspect(String value) {
        return "fit".equals(value) || "stretch".equals(value) ? value : "fill";
    }

    static int videoDimAlpha(int brightness) {
        return Math.round(255f * (1f - Math.max(0, Math.min(100, brightness)) / 100f));
    }

    /** A navigation destination, never a playback command or an arbitrary external URL. */
    static String spotifyDestination(String value, boolean albumOnly) {
        String uri = value == null ? "" : value.trim();
        if (uri.startsWith("https://open.spotify.com/")) {
            String path = uri.substring("https://open.spotify.com/".length()).split("[?#]", 2)[0];
            uri = "spotify:" + path.replace('/', ':');
        }
        String[] parts = uri.split(":");
        if (parts.length != 3 || !"spotify".equals(parts[0]) || !parts[2].matches("[A-Za-z0-9]{22}")) return "";
        if (albumOnly) return "album".equals(parts[1]) ? uri : "";
        return "album".equals(parts[1]) || "playlist".equals(parts[1]) || "artist".equals(parts[1])
                || "show".equals(parts[1]) ? uri : "";
    }
}
