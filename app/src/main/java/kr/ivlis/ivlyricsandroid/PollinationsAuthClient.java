package kr.ivlis.ivlyricsandroid;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class PollinationsAuthClient {
    static final String AUTH_BASE_URL = "https://enter.pollinations.ai";
    static final String API_BASE_URL = "https://gen.pollinations.ai";

    private static final String CLIENT_ID = "pk_r7hWynUBrOgSV9SJ";
    private static final String AUTH_SCOPE = "generate";
    private static final String AUTH_MODEL = "openai";
    private static final int AUTH_BUDGET = 999;
    private static final int AUTH_EXPIRY_DAYS = 365;
    private static final long DEFAULT_POLL_INTERVAL_MS = 5_000L;

    DeviceCode requestDeviceCode() throws IOException {
        JSONObject body = new JSONObject();
        put(body, "client_id", CLIENT_ID);
        JSONObject data = postJson(AUTH_BASE_URL + "/api/device/code", body, "");
        String deviceCode = data.optString("device_code", "").trim();
        String userCode = data.optString("user_code", "").trim();
        if (deviceCode.isEmpty() || userCode.isEmpty()) {
            throw new IOException("Pollinations device authorization response is missing a code.");
        }
        long intervalMs = Math.max(DEFAULT_POLL_INTERVAL_MS, data.optLong("interval", 0L) * 1000L);
        long expiresInSeconds = Math.max(60L, data.optLong("expires_in", 600L));
        return new DeviceCode(
                deviceCode,
                userCode,
                buildAuthorizeUrl(userCode),
                intervalMs,
                System.currentTimeMillis() + expiresInSeconds * 1000L
        );
    }

    TokenPollResult pollDeviceToken(String deviceCode) throws IOException {
        JSONObject body = new JSONObject();
        put(body, "device_code", deviceCode == null ? "" : deviceCode.trim());
        JSONObject data = postJsonAllowPollPending(AUTH_BASE_URL + "/api/device/token", body);
        String error = data.optString("error", "").trim();
        if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
            return TokenPollResult.pending("slow_down".equals(error));
        }
        String accessToken = data.optString("access_token", "").trim();
        if (accessToken.isEmpty()) {
            throw new IOException("Pollinations login completed without an access token.");
        }
        return TokenPollResult.success(accessToken);
    }

    KeyInfo fetchKeyInfo(String accessToken) throws IOException {
        JSONObject data = getJson(API_BASE_URL + "/account/key", accessToken);
        return new KeyInfo(
                data.optBoolean("valid", true),
                data.optString("type", "API"),
                data.optLong("expiresIn", 0L)
        );
    }

    private String buildAuthorizeUrl(String userCode) throws IOException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("user_code", userCode);
        params.put("app_key", CLIENT_ID);
        params.put("scope", AUTH_SCOPE);
        params.put("models", AUTH_MODEL);
        params.put("budget", String.valueOf(AUTH_BUDGET));
        params.put("expiry", String.valueOf(AUTH_EXPIRY_DAYS));
        StringBuilder builder = new StringBuilder(AUTH_BASE_URL).append("/authorize?");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(urlQuery(entry.getKey())).append('=').append(urlQuery(entry.getValue()));
        }
        return builder.toString();
    }

    private JSONObject postJson(String endpoint, JSONObject body, String bearerToken) throws IOException {
        HttpURLConnection connection = openConnection(endpoint, "POST", bearerToken);
        try {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            String response = readResponse(connection, code >= 400);
            if (code < 200 || code >= 300) {
                throw new IOException(extractErrorMessage(response, code));
            }
            return new JSONObject(response);
        } catch (Exception error) {
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException(error.getMessage(), error);
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject postJsonAllowPollPending(String endpoint, JSONObject body) throws IOException {
        HttpURLConnection connection = openConnection(endpoint, "POST", "");
        try {
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            String response = readResponse(connection, code >= 400);
            JSONObject data = response.trim().isEmpty() ? new JSONObject() : new JSONObject(response);
            String error = data.optString("error", "");
            if ("authorization_pending".equals(error) || "slow_down".equals(error)) {
                return data;
            }
            if (code < 200 || code >= 300 || !error.trim().isEmpty()) {
                throw new IOException(extractErrorMessage(response, code));
            }
            return data;
        } catch (Exception error) {
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException(error.getMessage(), error);
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject getJson(String endpoint, String bearerToken) throws IOException {
        HttpURLConnection connection = openConnection(endpoint, "GET", bearerToken);
        try {
            int code = connection.getResponseCode();
            String response = readResponse(connection, code >= 400);
            if (code < 200 || code >= 300) {
                throw new IOException(extractErrorMessage(response, code));
            }
            return new JSONObject(response);
        } catch (Exception error) {
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException(error.getMessage(), error);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String endpoint, String method, String bearerToken) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android");
        if (bearerToken != null && !bearerToken.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + bearerToken.trim());
        }
        return connection;
    }

    private String readResponse(HttpURLConnection connection, boolean error) throws IOException {
        InputStream input = error ? connection.getErrorStream() : connection.getInputStream();
        if (input == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private void put(JSONObject object, String key, Object value) throws IOException {
        try {
            object.put(key, value);
        } catch (Exception error) {
            throw new IOException(error.getMessage(), error);
        }
    }

    private String extractErrorMessage(String raw, int code) {
        try {
            JSONObject data = new JSONObject(raw == null ? "" : raw);
            JSONObject error = data.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "").trim();
                if (!message.isEmpty()) {
                    return message;
                }
            }
            String description = data.optString("error_description", "").trim();
            if (!description.isEmpty()) {
                return description;
            }
            String message = data.optString("message", "").trim();
            if (!message.isEmpty()) {
                return message;
            }
            String errorText = data.optString("error", "").trim();
            if (!errorText.isEmpty()) {
                return errorText;
            }
        } catch (Exception ignored) {
        }
        return "Pollinations HTTP " + code;
    }

    private String urlQuery(String value) throws IOException {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    static final class DeviceCode {
        final String deviceCode;
        final String userCode;
        final String verificationUrl;
        final long intervalMs;
        final long expiresAtMs;

        DeviceCode(String deviceCode, String userCode, String verificationUrl, long intervalMs, long expiresAtMs) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUrl = verificationUrl;
            this.intervalMs = intervalMs;
            this.expiresAtMs = expiresAtMs;
        }
    }

    static final class TokenPollResult {
        final boolean pending;
        final boolean slowDown;
        final String accessToken;

        private TokenPollResult(boolean pending, boolean slowDown, String accessToken) {
            this.pending = pending;
            this.slowDown = slowDown;
            this.accessToken = accessToken == null ? "" : accessToken;
        }

        static TokenPollResult pending(boolean slowDown) {
            return new TokenPollResult(true, slowDown, "");
        }

        static TokenPollResult success(String accessToken) {
            return new TokenPollResult(false, false, accessToken);
        }
    }

    static final class KeyInfo {
        final boolean valid;
        final String type;
        final long expiresInSeconds;

        KeyInfo(boolean valid, String type, long expiresInSeconds) {
            this.valid = valid;
            this.type = type == null ? "" : type;
            this.expiresInSeconds = expiresInSeconds;
        }
    }
}
