package kr.ivlis.ivlyricsandroid;

import java.net.URI;
import java.util.Locale;

final class InAppBrowserUrlPolicy {
    private InAppBrowserUrlPolicy() {
    }

    static boolean isAllowedInitialUrl(String url, boolean creatorLogin) {
        String host = httpsHost(url);
        if (host.isEmpty()) {
            return false;
        }
        if (creatorLogin) {
            return isCreatorLoginHost(host);
        }
        return "lyrics.ivl.is".equals(host);
    }

    static boolean isAllowedCreatorLoginUrl(String url) {
        String host = httpsHost(url);
        return !host.isEmpty() && isCreatorLoginHost(host);
    }

    static boolean isExternalHttpUrl(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String httpsHost(String url) {
        try {
            URI uri = URI.create(url == null ? "" : url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"https".equals(scheme) || uri.getHost() == null) {
                return "";
            }
            return uri.getHost().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static boolean isCreatorLoginHost(String host) {
        return "discord.com".equals(host)
                || host.endsWith(".discord.com")
                || "discordapp.com".equals(host)
                || host.endsWith(".discordapp.com")
                || "ivl.is".equals(host)
                || "lyrics.ivl.is".equals(host)
                || "lyrics.api.ivl.is".equals(host);
    }
}
