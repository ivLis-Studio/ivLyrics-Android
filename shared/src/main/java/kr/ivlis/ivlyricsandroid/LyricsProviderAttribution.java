package kr.ivlis.ivlyricsandroid;

import java.util.Locale;

final class LyricsProviderAttribution {
    private static final int DEFAULT_CONTRIBUTOR_LIMIT = 3;
    private static final String[] KNOWN_PROVIDER_IDS = {
            LyricsProviderSettings.PROVIDER_LRCLIB,
            LyricsProviderSettings.PROVIDER_PAXSENIX,
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

    static String contributorNames(LyricsResult result) {
        return contributorNames(result, DEFAULT_CONTRIBUTOR_LIMIT);
    }

    static String landscapeContributorNames(LyricsResult result) {
        return landscapeContributorNames(result, "Anonymous");
    }

    static String landscapeContributorNames(LyricsResult result, String anonymousLabel) {
        return displayName(result).isEmpty()
                ? ""
                : contributorNames(result, DEFAULT_CONTRIBUTOR_LIMIT, anonymousLabel);
    }

    static String contributorNames(LyricsResult result, int limit) {
        return contributorNames(result, limit, "Anonymous");
    }

    static String contributorNames(LyricsResult result, int limit, String anonymousLabel) {
        if (result == null || result.lines.isEmpty() || result.contributors.isEmpty()) {
            return "";
        }

        int visibleLimit = Math.max(1, limit);
        int validContributorCount = 0;
        StringBuilder visibleNames = new StringBuilder();
        for (LyricsResult.SyncContributor contributor : result.contributors) {
            if (contributor == null || contributor.name == null || contributor.name.trim().isEmpty()) {
                continue;
            }
            validContributorCount++;
            if (validContributorCount > visibleLimit) {
                continue;
            }
            if (visibleNames.length() > 0) {
                visibleNames.append(", ");
            }
            String name = contributor.anonymous
                    ? (anonymousLabel == null || anonymousLabel.trim().isEmpty() ? "Anonymous" : anonymousLabel.trim())
                    : contributor.name.trim();
            visibleNames.append(name);
        }
        if (visibleNames.length() == 0) {
            return "";
        }
        if (validContributorCount > visibleLimit) {
            visibleNames.append(" +").append(validContributorCount - visibleLimit);
        }
        return visibleNames.toString();
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
