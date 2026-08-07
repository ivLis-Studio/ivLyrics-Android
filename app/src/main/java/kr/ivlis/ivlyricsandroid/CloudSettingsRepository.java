package kr.ivlis.ivlyricsandroid;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Platform-specific OpenCloudSave client. Network methods run on a worker thread. */
final class CloudSettingsRepository {
    private static final String TOKEN_ENDPOINT = "https://lyrics.api.ivl.is/user/cloud-save-token";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 18_000;

    private final CreatorPrivacyRepository accountRepository;
    private final AiLyricsSettings aiLyricsSettings;
    private final LyricsProviderSettings lyricsProviderSettings;
    private final String appVersion;
    private Capability cachedSyncCapability;

    CloudSettingsRepository(
            Context context,
            CreatorPrivacyRepository accountRepository,
            AiLyricsSettings aiLyricsSettings,
            LyricsProviderSettings lyricsProviderSettings
    ) {
        this.accountRepository = accountRepository;
        this.aiLyricsSettings = aiLyricsSettings;
        this.lyricsProviderSettings = lyricsProviderSettings;
        this.appVersion = resolveAppVersion(context);
    }

    CloudRecord load(String languageTag) throws IOException {
        Capability capability = syncCapability(languageTag);
        Response response = request("GET", capability.apiBaseUrl + "/settings/android", null, capability.token, languageTag, appVersion);
        if (response.status == 404) {
            return CloudRecord.empty();
        }
        JSONObject data = requireSuccess(response, "Cloud settings could not be loaded");
        return CloudRecord.from(data);
    }

    CloudRecord save(long baseRevision, String languageTag) throws IOException {
        Capability capability = syncCapability(languageTag);
        try {
            JSONObject settings = new JSONObject();
            settings.put("aiLyrics", aiLyricsSettings.exportCloudSettings());
            settings.put("lyricsProviders", lyricsProviderSettings.exportCloudSettings());

            JSONObject body = new JSONObject();
            body.put("schemaVersion", 1);
            body.put("baseRevision", Math.max(0L, baseRevision));
            body.put("appVersion", appVersion);
            body.put("deviceId", accountRepository.cloudSaveDeviceId());
            body.put("settings", settings);

            Response response = request("PUT", capability.apiBaseUrl + "/settings/android", body, capability.token, languageTag, appVersion);
            JSONObject data = requireSuccess(response, "Cloud settings could not be saved");
            return CloudRecord.from(data);
        } catch (CloudSaveException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Cloud settings request could not be created", error);
        }
    }

    void apply(CloudRecord record) throws IOException {
        if (record == null || !record.exists) {
            throw new IOException("No Android cloud settings were found");
        }
        try {
            JSONObject ai = record.settings.optJSONObject("aiLyrics");
            JSONObject providers = record.settings.optJSONObject("lyricsProviders");
            if (ai != null) {
                aiLyricsSettings.importCloudSettings(ai);
            }
            if (providers != null) {
                lyricsProviderSettings.importCloudSettings(providers);
            }
        } catch (Exception error) {
            throw new IOException("Cloud settings are invalid", error);
        }
    }

    boolean delete(String languageTag) throws IOException {
        Capability capability = capability("delete", languageTag);
        Response response = request("DELETE", capability.apiBaseUrl + "/settings/android", null, capability.token, languageTag, appVersion);
        JSONObject data = requireSuccess(response, "Cloud settings could not be deleted");
        return data.optBoolean("deleted", false);
    }

    private synchronized Capability syncCapability(String languageTag) throws IOException {
        accountRepository.requireCloudAuthToken();
        long now = System.currentTimeMillis() / 1000L;
        String ownerUserHash = accountRepository.authenticatedUserHash();
        if (cachedSyncCapability != null
                && !ownerUserHash.isEmpty()
                && ownerUserHash.equals(cachedSyncCapability.ownerUserHash)
                && cachedSyncCapability.expiresAt > now + 30L) {
            return cachedSyncCapability;
        }
        cachedSyncCapability = capability("sync", languageTag);
        return cachedSyncCapability;
    }

    private Capability capability(String scope, String languageTag) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("scope", scope);
        } catch (Exception error) {
            throw new IOException("Cloud capability request could not be created", error);
        }
        Response response = request(
                "POST",
                TOKEN_ENDPOINT,
                body,
                accountRepository.requireCloudAuthToken(),
                languageTag,
                appVersion
        );
        JSONObject data = requireSuccess(response, "Cloud access could not be verified");
        String token = data.optString("token", "").trim();
        String apiBaseUrl = data.optString("apiBaseUrl", "").replaceAll("/+$", "");
        if (token.isEmpty() || apiBaseUrl.isEmpty()) {
            throw new IOException("Cloud capability response is incomplete");
        }
        return new Capability(
                token,
                apiBaseUrl,
                data.optLong("expiresAt", 0L),
                accountRepository.authenticatedUserHash()
        );
    }

    private static JSONObject requireSuccess(Response response, String fallback) throws IOException {
        JSONObject root = response.body;
        if (response.status >= 200 && response.status < 300 && root.optBoolean("success", false)) {
            JSONObject data = root.optJSONObject("data");
            if (data != null) {
                return data;
            }
        }
        String code = root.optString("code", "");
        String message = root.optString("error", "");
        JSONObject error = root.optJSONObject("error");
        if (error != null) {
            code = firstNonEmpty(error.optString("code", ""), code);
            message = firstNonEmpty(error.optString("message", ""), message);
        }
        throw new CloudSaveException(firstNonEmpty(message, fallback), code, response.status);
    }

    private static Response request(
            String method,
            String endpoint,
            JSONObject body,
            String bearerToken,
            String languageTag,
            String appVersion
    ) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android/" + appVersion);
        connection.setRequestProperty("Origin", "https://xpui.app.spotify.com");
        String locale = languageTag == null ? "" : languageTag.trim();
        if (!locale.isEmpty()) {
            connection.setRequestProperty("Accept-Language", locale);
        }
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken.trim());
        }
        if (body != null) {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
        }
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String text = readUtf8(stream);
            JSONObject root = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            return new Response(status, root);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Cloud response is invalid", error);
        } finally {
            connection.disconnect();
        }
    }

    private static String readUtf8(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                result.append(buffer, 0, count);
            }
        }
        return result.toString();
    }

    private static String firstNonEmpty(String first, String second) {
        String left = first == null ? "" : first.trim();
        return left.isEmpty() ? (second == null ? "" : second.trim()) : left;
    }

    private static String resolveAppVersion(Context context) {
        if (context == null) return "unknown";
        try {
            String version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return version == null || version.trim().isEmpty() ? "unknown" : version.trim();
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    static final class CloudRecord {
        final boolean exists;
        final long revision;
        final long updatedAt;
        final JSONObject settings;

        CloudRecord(boolean exists, long revision, long updatedAt, JSONObject settings) {
            this.exists = exists;
            this.revision = Math.max(0L, revision);
            this.updatedAt = Math.max(0L, updatedAt);
            this.settings = settings == null ? new JSONObject() : settings;
        }

        static CloudRecord empty() {
            return new CloudRecord(false, 0L, 0L, new JSONObject());
        }

        static CloudRecord from(JSONObject data) {
            return new CloudRecord(
                    true,
                    data.optLong("revision", 0L),
                    data.optLong("updatedAt", 0L),
                    data.optJSONObject("settings")
            );
        }
    }

    static final class CloudSaveException extends IOException {
        final String code;
        final int statusCode;

        CloudSaveException(String message, String code, int statusCode) {
            super(message);
            this.code = code == null ? "" : code;
            this.statusCode = statusCode;
        }
    }

    private static final class Capability {
        final String token;
        final String apiBaseUrl;
        final long expiresAt;
        final String ownerUserHash;

        Capability(String token, String apiBaseUrl, long expiresAt, String ownerUserHash) {
            this.token = token;
            this.apiBaseUrl = apiBaseUrl;
            this.expiresAt = expiresAt;
            this.ownerUserHash = ownerUserHash == null ? "" : ownerUserHash;
        }
    }

    private static final class Response {
        final int status;
        final JSONObject body;

        Response(int status, JSONObject body) {
            this.status = status;
            this.body = body;
        }
    }
}
