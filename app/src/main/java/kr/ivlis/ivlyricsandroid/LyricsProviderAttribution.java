package kr.ivlis.ivlyricsandroid;

import java.util.Locale;

final class LyricsProviderAttribution {
    private static final String[] KNOWN_PROVIDER_IDS = {
            LyricsProviderSettings.PROVIDER_LRCLIB,
            LyricsProviderSettings.PROVIDER_LYRICS_PLUS,
            LyricsProviderSettings.PROVIDER_UNISON
    };

    private LyricsProviderAttribution() {
    }

    static String displayName(LyricsResult result) {
        if (result == null || result.lines.isEmpty()) {
            return "";
        }

        String providerId = result.providerId == null
                ? ""
                : result.providerId.trim().toLowerCase(Locale.ROOT);
        if (isKnownProvider(providerId)) {
            return providerId;
        }

        String providerLabel = result.providerLabel == null ? "" : result.providerLabel.trim();
        return providerFromLegacyLabel(providerLabel);
    }

    private static boolean isKnownProvider(String providerId) {
        for (String knownProviderId : KNOWN_PROVIDER_IDS) {
            if (knownProviderId.equals(providerId)) {
                return true;
            }
        }
        return false;
    }

    private static String providerFromLegacyLabel(String label) {
        if (label.isEmpty()) {
            return "";
        }

        String lower = label.toLowerCase(Locale.ROOT);
        String matchedProvider = "";
        for (String knownProviderId : KNOWN_PROVIDER_IDS) {
            if (!containsProviderToken(lower, knownProviderId)) {
                continue;
            }
            if (!matchedProvider.isEmpty()) {
                return "";
            }
            matchedProvider = knownProviderId;
        }
        return matchedProvider;
    }

    private static boolean containsProviderToken(String label, String providerId) {
        int offset = 0;
        while (offset < label.length()) {
            int index = label.indexOf(providerId, offset);
            if (index < 0) {
                return false;
            }
            int end = index + providerId.length();
            boolean startsAtBoundary = index == 0 || !Character.isLetterOrDigit(label.charAt(index - 1));
            boolean endsAtBoundary = end == label.length() || !Character.isLetterOrDigit(label.charAt(end));
            if (startsAtBoundary && endsAtBoundary) {
                return true;
            }
            offset = index + 1;
        }
        return false;
    }
}
