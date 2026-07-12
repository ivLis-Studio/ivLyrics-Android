package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class LyricsProviderSettings {
    static final String PROVIDER_LYRICS_PLUS = "lyricsplus";
    static final String PROVIDER_UNISON = "unison";
    static final String PROVIDER_LRCLIB = "lrclib";

    static final String TYPE_KARAOKE = "karaoke";
    static final String TYPE_SYNCED = "synced";
    static final String TYPE_PLAIN = "plain";

    static final List<Provider> PROVIDERS = Collections.unmodifiableList(Arrays.asList(
            new Provider(PROVIDER_LYRICS_PLUS, "LyricsPlus", "default", LyricsPlusLyricsProvider.PROJECT_URL, true, true, true, false),
            new Provider(PROVIDER_UNISON, "Unison", "default", "https://github.com/better-lyrics/unison", true, true, true, false),
            new Provider(PROVIDER_LRCLIB, "LRCLIB", "default", "https://lrclib.net", false, true, true, true)
    ));

    private static final String PREFS_NAME = "lyrics_provider_settings";
    private static final String KEY_ORDER = "provider_order";
    private static final String KEY_PREFER_SYNC_DATA = "prefer_sync_data_provider";
    private static final String KEY_TYPE_FIRST = "prefer_lyrics_type_over_provider_order";
    private static final String KEY_ENABLED_PREFIX = "enabled_";
    private static final String KEY_TYPE_PREFIX = "allow_";

    private final SharedPreferences preferences;

    LyricsProviderSettings(Context context) {
        Context appContext = context == null ? null : context.getApplicationContext();
        preferences = appContext == null
                ? null
                : appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    Snapshot snapshot() {
        Map<String, ProviderConfig> configs = new LinkedHashMap<>();
        for (Provider provider : PROVIDERS) {
            configs.put(provider.id, new ProviderConfig(
                    provider,
                    readBoolean(KEY_ENABLED_PREFIX + provider.id, true),
                    readBoolean(typeKey(provider.id, TYPE_KARAOKE), true),
                    readBoolean(typeKey(provider.id, TYPE_SYNCED), true),
                    readBoolean(typeKey(provider.id, TYPE_PLAIN), true)
            ));
        }
        return new Snapshot(
                orderedProviderIds(),
                configs,
                readBoolean(KEY_PREFER_SYNC_DATA, true),
                readBoolean(KEY_TYPE_FIRST, true)
        );
    }

    void setProviderEnabled(String providerId, boolean enabled) {
        putBoolean(KEY_ENABLED_PREFIX + normalizeProviderId(providerId), enabled);
    }

    void setTypeAllowed(String providerId, String type, boolean allowed) {
        putBoolean(typeKey(normalizeProviderId(providerId), normalizeType(type)), allowed);
    }

    void setPreferSyncDataProvider(boolean enabled) {
        putBoolean(KEY_PREFER_SYNC_DATA, enabled);
    }

    void setTypeFirst(boolean enabled) {
        putBoolean(KEY_TYPE_FIRST, enabled);
    }

    void moveProvider(String providerId, int direction) {
        List<String> order = new ArrayList<>(orderedProviderIds());
        int index = order.indexOf(normalizeProviderId(providerId));
        int target = index + (direction < 0 ? -1 : 1);
        if (index < 0 || target < 0 || target >= order.size()) {
            return;
        }
        Collections.swap(order, index, target);
        writeOrder(order);
    }

    private List<String> orderedProviderIds() {
        Set<String> known = new LinkedHashSet<>();
        for (Provider provider : PROVIDERS) {
            known.add(provider.id);
        }

        List<String> result = new ArrayList<>();
        if (preferences != null) {
            String stored = preferences.getString(KEY_ORDER, "");
            if (stored != null && !stored.trim().isEmpty()) {
                try {
                    JSONArray array = new JSONArray(stored);
                    for (int index = 0; index < array.length(); index++) {
                        String id = normalizeProviderId(array.optString(index, ""));
                        if (known.contains(id) && !result.contains(id)) {
                            result.add(id);
                        }
                    }
                } catch (Exception ignored) {
                    // A malformed old preference falls back to the current default order.
                }
            }
        }
        for (Provider provider : PROVIDERS) {
            if (!result.contains(provider.id)) {
                result.add(provider.id);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private void writeOrder(List<String> order) {
        if (preferences == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (String providerId : order) {
            array.put(providerId);
        }
        preferences.edit().putString(KEY_ORDER, array.toString()).apply();
    }

    private boolean readBoolean(String key, boolean fallback) {
        return preferences == null ? fallback : preferences.getBoolean(key, fallback);
    }

    private void putBoolean(String key, boolean value) {
        if (preferences != null && key != null && !key.trim().isEmpty()) {
            preferences.edit().putBoolean(key, value).apply();
        }
    }

    private static String typeKey(String providerId, String type) {
        return KEY_TYPE_PREFIX + providerId + "_" + type;
    }

    private static String normalizeProviderId(String providerId) {
        return providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        if (TYPE_KARAOKE.equals(normalized) || TYPE_SYNCED.equals(normalized)) {
            return normalized;
        }
        return TYPE_PLAIN;
    }

    static final class Provider {
        final String id;
        final String label;
        final String author;
        final String projectUrl;
        final boolean nativeKaraoke;
        final boolean synced;
        final boolean plain;
        final boolean ivLyricsSync;

        Provider(
                String id,
                String label,
                String author,
                String projectUrl,
                boolean nativeKaraoke,
                boolean synced,
                boolean plain,
                boolean ivLyricsSync
        ) {
            this.id = id;
            this.label = label;
            this.author = author;
            this.projectUrl = projectUrl;
            this.nativeKaraoke = nativeKaraoke;
            this.synced = synced;
            this.plain = plain;
            this.ivLyricsSync = ivLyricsSync;
        }
    }

    static final class ProviderConfig {
        final Provider provider;
        final boolean enabled;
        final boolean karaoke;
        final boolean synced;
        final boolean plain;

        ProviderConfig(Provider provider, boolean enabled, boolean karaoke, boolean synced, boolean plain) {
            this.provider = provider;
            this.enabled = enabled;
            this.karaoke = karaoke;
            this.synced = synced;
            this.plain = plain;
        }

        boolean allows(String type) {
            if (TYPE_KARAOKE.equals(type)) return karaoke;
            if (TYPE_SYNCED.equals(type)) return synced;
            return plain;
        }
    }

    static final class Snapshot {
        final List<String> order;
        final Map<String, ProviderConfig> configs;
        final boolean preferSyncDataProvider;
        final boolean typeFirst;

        Snapshot(
                List<String> order,
                Map<String, ProviderConfig> configs,
                boolean preferSyncDataProvider,
                boolean typeFirst
        ) {
            this.order = Collections.unmodifiableList(new ArrayList<>(order));
            this.configs = Collections.unmodifiableMap(new LinkedHashMap<>(configs));
            this.preferSyncDataProvider = preferSyncDataProvider;
            this.typeFirst = typeFirst;
        }

        ProviderConfig config(String providerId) {
            return configs.get(normalizeProviderId(providerId));
        }

        List<ProviderConfig> enabledProviders() {
            List<ProviderConfig> result = new ArrayList<>();
            for (String providerId : order) {
                ProviderConfig config = configs.get(providerId);
                if (config != null && config.enabled) {
                    result.add(config);
                }
            }
            return result;
        }

        String cacheKey() {
            StringBuilder builder = new StringBuilder(typeFirst ? "type-first" : "provider-first");
            builder.append(preferSyncDataProvider ? ":sync-first" : ":normal-order");
            for (String providerId : order) {
                ProviderConfig config = configs.get(providerId);
                if (config == null) continue;
                builder.append('|').append(providerId)
                        .append(':').append(config.enabled ? '1' : '0')
                        .append(config.karaoke ? 'k' : '-')
                        .append(config.synced ? 's' : '-')
                        .append(config.plain ? 'p' : '-');
            }
            return builder.toString();
        }
    }
}
