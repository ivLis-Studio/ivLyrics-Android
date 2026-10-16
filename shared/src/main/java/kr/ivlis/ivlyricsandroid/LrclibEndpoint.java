package kr.ivlis.ivlyricsandroid;

import java.net.URI;
import java.util.Locale;

/** Playback-only LRCLIB base URL. Never accepts credentials or weakens Android's TLS policy. */
final class LrclibEndpoint {
    static final String OFFICIAL = "https://lrclib.net/api";

    private LrclibEndpoint() { }

    static String normalize(String value) {
        String input = value == null ? "" : value.trim();
        if (input.isEmpty()) return "";
        try {
            URI uri = new URI(input);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!"https".equals(scheme) && !"http".equals(scheme)) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Invalid LRCLIB endpoint");
            }
            // HTTP request lines are ASCII. Preserve Unicode reverse-proxy paths
            // as UTF-8 percent escapes instead of replacing their letters with '?'.
            String path = URI.create(uri.toASCIIString()).getRawPath();
            if (path == null) path = "";
            // Reject path traversal, including encoded separators/control characters.
            String decoded = uri.getPath();
            if (!uri.normalize().equals(uri) || decoded.matches(".*[\\\\\\p{Cntrl}].*")
                    || decoded.contains("/../") || decoded.endsWith("/..")
                    || decoded.contains("/./") || decoded.endsWith("/.")) {
                throw new IllegalArgumentException("Invalid LRCLIB endpoint path");
            }
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.isEmpty()) path = "/api";
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            int defaultPort = "https".equals(scheme) ? 443 : 80;
            String result = scheme + "://" + host + (port < 0 || port == defaultPort ? "" : ":" + port) + path;
            return OFFICIAL.equals(result) ? "" : result;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid LRCLIB endpoint", error);
        }
    }
}
