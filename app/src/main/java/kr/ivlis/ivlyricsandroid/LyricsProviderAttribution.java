package kr.ivlis.ivlyricsandroid;

import java.util.Locale;

final class LyricsProviderAttribution {
    private LyricsProviderAttribution() {
    }

    static String displayName(LyricsResult result) {
        if (result == null || result.lines.isEmpty()) {
            return "";
        }

        String providerId = result.providerId == null
                ? ""
                : result.providerId.trim().toLowerCase(Locale.ROOT);
        if (LyricsProviderSettings.PROVIDER_LRCLIB.equals(providerId)) {
            return "LRCLIB";
        }
        if (LyricsProviderSettings.PROVIDER_LYRICS_PLUS.equals(providerId)) {
            return "LyricsPlus";
        }
        if (LyricsProviderSettings.PROVIDER_UNISON.equals(providerId)) {
            return "Unison";
        }

        String providerLabel = result.providerLabel == null ? "" : result.providerLabel.trim();
        if (!providerLabel.isEmpty()) {
            return withoutLyricsTypeSuffix(providerLabel);
        }
        return providerId;
    }

    private static String withoutLyricsTypeSuffix(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        for (String suffix : new String[]{" karaoke", " synced", " plain"}) {
            if (lower.endsWith(suffix)) {
                return label.substring(0, label.length() - suffix.length()).trim();
            }
        }
        return label;
    }
}
