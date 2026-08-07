package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class CreatorSupportRepository {
    private static final String DISCORD_USER_ENDPOINT = "https://discord.ivl.is/v1/user/";
    private static final String DECORATIONS_ENDPOINT =
            "https://lyrics.api.ivl.is/user/creator-decorations";
    private static final String SUPPORTER_ROLE_ID = "1530978124073013478";
    private static final String MONTHLY_SUPPORTER_ROLE_ID = "1530978173590966282";
    private static final String PREFS_NAME = "creator_support_tier_cache";
    private static final String CACHE_KEY_PREFIX = "tier:";
    private static final long TIER_CACHE_TTL_MS = 60L * 60L * 1000L;
    private static final int MAX_CONTRIBUTORS = 3;

    interface Callback {
        void onLoaded(Map<String, Presentation> presentations);
    }

    static final class Presentation {
        final String tier;
        final String mode;
        final String solidColor;
        final String gradientStartColor;
        final String gradientEndColor;
        final int gradientAngle;

        Presentation(
                String tier,
                String mode,
                String solidColor,
                String gradientStartColor,
                String gradientEndColor,
                int gradientAngle
        ) {
            this.tier = normalizeTier(tier);
            this.mode = "monthly".equals(this.tier) && "gradient".equals(mode)
                    ? "gradient"
                    : "solid";
            this.solidColor = normalizeColor(solidColor);
            this.gradientStartColor = normalizeColor(gradientStartColor);
            this.gradientEndColor = normalizeColor(gradientEndColor);
            this.gradientAngle = Math.max(0, Math.min(360, gradientAngle));
        }

        boolean hasDecoration() {
            if ("none".equals(tier)) {
                return false;
            }
            if ("gradient".equals(mode)) {
                return !gradientStartColor.isEmpty() && !gradientEndColor.isEmpty();
            }
            return !solidColor.isEmpty();
        }

        boolean usesGradient() {
            return hasDecoration() && "gradient".equals(mode);
        }
    }

    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    CreatorSupportRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    void load(List<LyricsResult.SyncContributor> contributors, Callback callback) {
        Set<String> userHashes = visibleDiscordIds(contributors);
        if (userHashes.isEmpty()) {
            callback.onLoaded(Collections.emptyMap());
            return;
        }
        executor.execute(() -> callback.onLoaded(loadPresentations(userHashes)));
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private Map<String, Presentation> loadPresentations(Set<String> userHashes) {
        Map<String, String> tiers = new HashMap<>();
        for (String userHash : userHashes) {
            tiers.put(userHash, loadTier(userHash));
        }

        Map<String, JSONObject> decorations;
        try {
            decorations = fetchDecorations(userHashes);
        } catch (Exception ignored) {
            decorations = Collections.emptyMap();
        }

        Map<String, Presentation> result = new HashMap<>();
        for (String userHash : userHashes) {
            String tier = tiers.get(userHash);
            JSONObject decoration = decorations.get(userHash);
            if ("none".equals(tier) || decoration == null) {
                continue;
            }
            Presentation presentation = new Presentation(
                    tier,
                    decoration.optString("mode", "solid"),
                    decoration.optString("solidColor", ""),
                    decoration.optString("gradientStartColor", ""),
                    decoration.optString("gradientEndColor", ""),
                    decoration.optInt("gradientAngle", 90)
            );
            if (presentation.hasDecoration()) {
                result.put(userHash, presentation);
            }
        }
        return result;
    }

    private String loadTier(String userHash) {
        TierCacheEntry cached = readTierCache(userHash);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now) {
            return cached.tier;
        }
        try {
            String tier = fetchTier(userHash);
            preferences.edit()
                    .putString(CACHE_KEY_PREFIX + userHash, tier + ":" + (now + TIER_CACHE_TTL_MS))
                    .apply();
            return tier;
        } catch (Exception ignored) {
            return "none";
        }
    }

    private TierCacheEntry readTierCache(String userHash) {
        String raw = preferences.getString(CACHE_KEY_PREFIX + userHash, "");
        int separator = raw.lastIndexOf(':');
        if (separator <= 0 || separator >= raw.length() - 1) {
            return null;
        }
        String tier = normalizeTier(raw.substring(0, separator));
        try {
            return new TierCacheEntry(tier, Long.parseLong(raw.substring(separator + 1)));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String fetchTier(String userHash) throws Exception {
        JSONObject root = getJson(DISCORD_USER_ENDPOINT + Uri.encode(userHash));
        JSONObject data = root.optJSONObject("data");
        JSONArray roles = data == null ? null : data.optJSONArray("roles");
        boolean supporter = false;
        if (roles != null) {
            for (int index = 0; index < roles.length(); index++) {
                String roleId = roles.optJSONObject(index) == null
                        ? ""
                        : roles.optJSONObject(index).optString("id", "");
                if (MONTHLY_SUPPORTER_ROLE_ID.equals(roleId)) {
                    return "monthly";
                }
                if (SUPPORTER_ROLE_ID.equals(roleId)) {
                    supporter = true;
                }
            }
        }
        return supporter ? "supporter" : "none";
    }

    private Map<String, JSONObject> fetchDecorations(Set<String> userHashes) throws Exception {
        Uri uri = Uri.parse(DECORATIONS_ENDPOINT).buildUpon()
                .appendQueryParameter("userHashes", String.join(",", userHashes))
                .build();
        JSONObject root = getJson(uri.toString());
        JSONObject data = root.optJSONObject("data");
        JSONArray items = data == null ? null : data.optJSONArray("items");
        if (!root.optBoolean("success", false) || items == null) {
            return Collections.emptyMap();
        }
        Map<String, JSONObject> result = new HashMap<>();
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) {
                continue;
            }
            String userHash = item.optString("userHash", "").trim();
            JSONObject decoration = item.optJSONObject("decoration");
            if (userHashes.contains(userHash) && decoration != null) {
                result.put(userHash, decoration);
            }
        }
        return result;
    }

    private JSONObject getJson(String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(4_000);
        connection.setReadTimeout(6_000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android/1.1");
        connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
        connection.setRequestProperty("Pragma", "no-cache");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status);
            }
            return new JSONObject(readBody(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    private static String readBody(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
        }
        return builder.toString();
    }

    private static Set<String> visibleDiscordIds(List<LyricsResult.SyncContributor> contributors) {
        if (contributors == null || contributors.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        int count = Math.min(MAX_CONTRIBUTORS, contributors.size());
        for (int index = 0; index < count; index++) {
            LyricsResult.SyncContributor contributor = contributors.get(index);
            if (contributor == null || contributor.anonymous || contributor.isPrivate) {
                continue;
            }
            String userHash = contributor.userHash == null ? "" : contributor.userHash.trim();
            if (isDiscordId(userHash)) {
                result.add(userHash);
            }
        }
        return result;
    }

    private static boolean isDiscordId(String value) {
        if (value == null || value.length() < 15 || value.length() > 22) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeTier(String tier) {
        if ("monthly".equals(tier) || "supporter".equals(tier)) {
            return tier;
        }
        return "none";
    }

    private static String normalizeColor(String value) {
        String color = value == null ? "" : value.trim().toUpperCase();
        if (color.length() != 7 || color.charAt(0) != '#') {
            return "";
        }
        for (int index = 1; index < color.length(); index++) {
            char character = color.charAt(index);
            if (!((character >= '0' && character <= '9') || (character >= 'A' && character <= 'F'))) {
                return "";
            }
        }
        return color;
    }

    private static final class TierCacheEntry {
        final String tier;
        final long expiresAt;

        TierCacheEntry(String tier, long expiresAt) {
            this.tier = tier;
            this.expiresAt = expiresAt;
        }
    }
}
