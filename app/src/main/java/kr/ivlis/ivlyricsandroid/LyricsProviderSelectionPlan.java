package kr.ivlis.ivlyricsandroid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class LyricsProviderSelectionPlan {
    private static final String[] TYPES = new String[]{
            LyricsProviderSettings.TYPE_KARAOKE,
            LyricsProviderSettings.TYPE_SYNCED,
            LyricsProviderSettings.TYPE_PLAIN
    };

    final List<LyricsProviderSettings.ProviderConfig> providers;
    final List<Attempt> attempts;

    private LyricsProviderSelectionPlan(
            List<LyricsProviderSettings.ProviderConfig> providers,
            List<Attempt> attempts
    ) {
        this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
        this.attempts = Collections.unmodifiableList(new ArrayList<>(attempts));
    }

    static LyricsProviderSelectionPlan create(
            LyricsProviderSettings.Snapshot settings,
            Set<String> availableSyncDataProviders
    ) {
        if (settings == null) {
            return new LyricsProviderSelectionPlan(Collections.emptyList(), Collections.emptyList());
        }
        Set<String> syncProviders = availableSyncDataProviders == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(availableSyncDataProviders);
        List<LyricsProviderSettings.ProviderConfig> providers = preferredProviders(settings, syncProviders);
        List<Attempt> attempts = new ArrayList<>();
        if (settings.typeFirst) {
            for (String type : TYPES) {
                for (LyricsProviderSettings.ProviderConfig config : providers) {
                    if (canParticipate(config, type, syncProviders)) {
                        attempts.add(new Attempt(config, type));
                    }
                }
            }
        } else {
            for (LyricsProviderSettings.ProviderConfig config : providers) {
                for (String type : TYPES) {
                    if (canParticipate(config, type, syncProviders)) {
                        attempts.add(new Attempt(config, type));
                    }
                }
            }
        }
        return new LyricsProviderSelectionPlan(providers, attempts);
    }

    static boolean canApplyIvLyricsSyncToCachedResult(
            LyricsResult result,
            LyricsProviderSettings.Snapshot settings
    ) {
        if (result == null || result.lines.isEmpty() || result.karaoke || settings == null) {
            return false;
        }
        LyricsProviderSettings.ProviderConfig config = settings.config(result.providerId);
        return config != null
                && config.enabled
                && config.karaoke;
    }

    static boolean shouldRecheckCachedResult(
            LyricsResult result,
            LyricsProviderSettings.Snapshot settings,
            String resolvedIsrc
    ) {
        if (result == null || result.lines.isEmpty() || settings == null
                || "manual".equals(result.selectionPolicyKey)) {
            return false;
        }
        if (canApplyIvLyricsSyncToCachedResult(result, settings)) return true;
        if (!settings.preferSyncDataProvider || TrackSnapshot.normalizeIsrc(resolvedIsrc).isEmpty()) return false;
        LyricsProviderSettings.ProviderConfig current = settings.config(result.providerId);
        return current == null || !result.karaoke;
    }

    static String preferredIvLyricsSyncProviderId(
            LyricsProviderSettings.Snapshot settings,
            Set<String> availableSyncDataProviders
    ) {
        if (settings == null || availableSyncDataProviders == null || availableSyncDataProviders.isEmpty()) {
            return "";
        }
        List<LyricsProviderSettings.ProviderConfig> preferred = syncDataProvidersInPriorityOrder(
                settings.enabledProviders(),
                availableSyncDataProviders
        );
        return preferred.isEmpty() ? "" : preferred.get(0).provider.id;
    }

    private static List<LyricsProviderSettings.ProviderConfig> preferredProviders(
            LyricsProviderSettings.Snapshot settings,
            Set<String> syncProviders
    ) {
        List<LyricsProviderSettings.ProviderConfig> providers = new ArrayList<>(settings.enabledProviders());
        if (!settings.preferSyncDataProvider || syncProviders.isEmpty()) {
            return providers;
        }
        List<LyricsProviderSettings.ProviderConfig> preferred = syncDataProvidersInPriorityOrder(
                providers,
                syncProviders
        );
        Set<String> preferredIds = new LinkedHashSet<>();
        for (LyricsProviderSettings.ProviderConfig config : preferred) {
            preferredIds.add(config.provider.id);
        }
        List<LyricsProviderSettings.ProviderConfig> remaining = new ArrayList<>();
        for (LyricsProviderSettings.ProviderConfig config : providers) {
            if (!preferredIds.contains(config.provider.id)) {
                remaining.add(config);
            }
        }
        preferred.addAll(remaining);
        return preferred;
    }

    private static List<LyricsProviderSettings.ProviderConfig> syncDataProvidersInPriorityOrder(
            List<LyricsProviderSettings.ProviderConfig> providers,
            Set<String> syncProviders
    ) {
        List<LyricsProviderSettings.ProviderConfig> lrclib = new ArrayList<>();
        List<LyricsProviderSettings.ProviderConfig> others = new ArrayList<>();
        for (LyricsProviderSettings.ProviderConfig config : providers) {
            if (!config.karaoke || !syncProviders.contains(config.provider.id)) {
                continue;
            }
            if (LyricsProviderSettings.PROVIDER_LRCLIB.equals(config.provider.id)) {
                lrclib.add(config);
            } else {
                others.add(config);
            }
        }
        lrclib.addAll(others);
        return lrclib;
    }

    private static boolean canParticipate(
            LyricsProviderSettings.ProviderConfig config,
            String type,
            Set<String> syncProviders
    ) {
        if (config == null || !config.enabled || !config.allows(type)) {
            return false;
        }
        if (LyricsProviderSettings.TYPE_KARAOKE.equals(type)) {
            return config.provider.nativeKaraoke
                    || syncProviders.contains(config.provider.id);
        }
        if (LyricsProviderSettings.TYPE_SYNCED.equals(type)) {
            return config.provider.synced;
        }
        return config.provider.plain;
    }

    static final class Attempt {
        final LyricsProviderSettings.ProviderConfig config;
        final String type;

        Attempt(LyricsProviderSettings.ProviderConfig config, String type) {
            this.config = config;
            this.type = type;
        }

        String key() {
            return config.provider.id + ":" + type;
        }
    }
}
