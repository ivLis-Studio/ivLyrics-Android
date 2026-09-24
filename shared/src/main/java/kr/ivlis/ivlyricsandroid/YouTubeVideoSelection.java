package kr.ivlis.ivlyricsandroid;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class YouTubeVideoSelection {
    private YouTubeVideoSelection() {}

    static String extractId(String value) {
        String text = value == null ? "" : value.trim();
        if (validId(text)) return text;
        try {
            URI uri = URI.create(text);
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return "";
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String[] path = uri.getPath().split("/");
            if ("youtu.be".equals(host)) return path.length == 2 && validId(path[1]) ? path[1] : "";
            if (!host.equals("youtube.com") && !host.equals("www.youtube.com")
                    && !host.equals("m.youtube.com") && !host.equals("music.youtube.com")) return "";
            if (path.length == 3 && (path[1].equals("shorts") || path[1].equals("embed") || path[1].equals("live"))) {
                return validId(path[2]) ? path[2] : "";
            }
            if (!"/watch".equals(uri.getPath()) || uri.getRawQuery() == null) return "";
            for (String parameter : uri.getRawQuery().split("&")) {
                String[] pair = parameter.split("=", 2);
                if (pair.length == 2 && pair[0].equals("v")) {
                    String id = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                    return validId(id) ? id : "";
                }
            }
        } catch (IllegalArgumentException ignored) { }
        return "";
    }

    private static boolean validId(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{11}");
    }
}
