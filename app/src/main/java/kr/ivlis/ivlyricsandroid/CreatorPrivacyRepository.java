package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Owns the authenticated sync-creator session used by the profile privacy setting.
 * Network methods are synchronous and must be called from a worker thread.
 */
final class CreatorPrivacyRepository {
    private static final String API_BASE_URL = "https://lyrics.api.ivl.is";
    private static final String DISCORD_START_ENDPOINT = API_BASE_URL + "/user/discord/start";
    private static final String DISCORD_SESSION_ENDPOINT = API_BASE_URL + "/user/discord/session";
    private static final String PRIVACY_ENDPOINT = API_BASE_URL + "/user/creator-profile/privacy";
    private static final String LOGOUT_ENDPOINT = API_BASE_URL + "/user/logout";
    private static final String PREFS_NAME = "sync_creator_account";
    private static final String KEY_DEVICE_USER_HASH = "device_user_hash";
    private static final String KEY_AUTH_TOKEN_ENCRYPTED = "auth_token_encrypted";
    // Kept only to migrate sessions created by the first privacy implementation.
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_AUTH_USER_HASH = "auth_user_hash";
    private static final String KEY_AUTH_EXPIRES_AT = "auth_expires_at";
    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEYSTORE_ALIAS = "ivlyrics_creator_session_v1";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 18_000;
    private static final int CLIENT_NONCE_BYTES = 32;

    private final SharedPreferences preferences;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Object pendingLoginLock = new Object();
    private String pendingClientNonce = "";

    CreatorPrivacyRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    boolean hasAuthenticatedSession() {
        String token = authToken();
        if (token.isEmpty()) {
            return false;
        }
        long expiresAt = preferences.getLong(KEY_AUTH_EXPIRES_AT, 0L);
        if (expiresAt > 0L && expiresAt <= (System.currentTimeMillis() / 1000L) + 30L) {
            clearSession();
            return false;
        }
        return true;
    }

    String authenticatedUserHash() {
        return preferences.getString(KEY_AUTH_USER_HASH, "").trim();
    }

    LoginStart startDiscordLogin(String languageTag) throws IOException {
        String clientNonce = generateClientNonce();
        setPendingClientNonce(clientNonce);
        boolean started = false;
        JSONObject body = new JSONObject();
        try {
            body.put("currentUserHash", deviceUserHash());
            body.put("clientNonce", clientNonce);
            JSONObject root = requestJson("POST", DISCORD_START_ENDPOINT, body, "", languageTag);
            String authorizeUrl = root.optString("authorizeUrl", "").trim();
            if (!root.optBoolean("success", false) || authorizeUrl.isEmpty()) {
                throw new IOException(errorMessage(root, "Discord login could not be started"));
            }
            if (!isPendingClientNonce(clientNonce)) {
                throw new IOException("Discord login was cancelled");
            }
            started = true;
            return new LoginStart(authorizeUrl);
        } catch (Exception error) {
            if (error instanceof IOException) {
                throw (IOException) error;
            }
            throw new IOException("Discord login request could not be created", error);
        } finally {
            if (!started) {
                clearPendingClientNonce(clientNonce);
            }
        }
    }

    Session finishDiscordLogin(String loginToken, String languageTag) throws IOException {
        String expectedClientNonce = currentPendingClientNonce();
        if (expectedClientNonce.isEmpty()) {
            throw new IOException("Discord login session is no longer pending");
        }
        String safeToken = loginToken == null ? "" : loginToken.trim();
        if (safeToken.isEmpty()) {
            clearPendingClientNonce(expectedClientNonce);
            throw new IOException("Discord login token is missing");
        }
        try {
            String endpoint = DISCORD_SESSION_ENDPOINT + "?loginToken=" + Uri.encode(safeToken);
            JSONObject root = requestJson("GET", endpoint, null, "", languageTag);
            JSONObject data = root.optJSONObject("data");
            if (!root.optBoolean("success", false) || data == null) {
                throw new IOException(errorMessage(root, "Discord login session could not be loaded"));
            }
            String responseClientNonce = data.optString("clientNonce", "");
            if (!isMatchingClientNonce(expectedClientNonce, responseClientNonce)) {
                throw new IOException("Discord login response did not match this login attempt");
            }
            String authToken = data.optString("authToken", "").trim();
            String userHash = firstNonEmpty(data.optString("userHash", ""), data.optString("discordId", ""));
            long expiresAt = data.optLong("authExpiresAt", 0L);
            if (authToken.isEmpty() || userHash.isEmpty()) {
                throw new IOException("Discord login response did not include an authenticated session");
            }
            String encryptedAuthToken = encryptAuthToken(authToken);
            synchronized (pendingLoginLock) {
                if (!isMatchingClientNonce(pendingClientNonce, expectedClientNonce)) {
                    throw new IOException("Discord login was cancelled");
                }
                preferences.edit()
                        .putString(KEY_AUTH_TOKEN_ENCRYPTED, encryptedAuthToken)
                        .remove(KEY_AUTH_TOKEN)
                        .putString(KEY_AUTH_USER_HASH, userHash)
                        .putLong(KEY_AUTH_EXPIRES_AT, Math.max(0L, expiresAt))
                        .apply();
                pendingClientNonce = "";
            }
            return new Session(userHash, expiresAt);
        } finally {
            clearPendingClientNonce(expectedClientNonce);
        }
    }

    void cancelDiscordLogin() {
        synchronized (pendingLoginLock) {
            pendingClientNonce = "";
        }
    }

    static boolean isMatchingClientNonce(String expected, String actual) {
        String safeExpected = expected == null ? "" : expected;
        String safeActual = actual == null ? "" : actual;
        if (safeExpected.isEmpty() || safeActual.isEmpty()) {
            return false;
        }
        return MessageDigest.isEqual(
                safeExpected.getBytes(StandardCharsets.UTF_8),
                safeActual.getBytes(StandardCharsets.UTF_8)
        );
    }

    Privacy getPrivacy(String languageTag) throws IOException {
        return privacyFromResponse(requestJson("GET", PRIVACY_ENDPOINT, null, requireAuthToken(), languageTag));
    }

    Privacy setPrivacy(boolean isPrivate, String languageTag) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("isPrivate", isPrivate);
        } catch (Exception error) {
            throw new IOException("Creator privacy request could not be created", error);
        }
        return privacyFromResponse(requestJson("PUT", PRIVACY_ENDPOINT, body, requireAuthToken(), languageTag));
    }

    void logout(String languageTag) throws IOException {
        String token = authToken();
        if (token.isEmpty()) {
            clearSession();
            return;
        }
        try {
            requestJson("POST", LOGOUT_ENDPOINT, null, token, languageTag);
        } catch (AuthenticationException error) {
            if (!isCompletedLogoutStatus(error.statusCode)) {
                throw error;
            }
        }
        clearSession();
    }

    static boolean isCompletedLogoutStatus(int statusCode) {
        return (statusCode >= 200 && statusCode < 300)
                || statusCode == 401
                || statusCode == 403;
    }

    void clearSession() {
        preferences.edit()
                .remove(KEY_AUTH_TOKEN_ENCRYPTED)
                .remove(KEY_AUTH_TOKEN)
                .remove(KEY_AUTH_USER_HASH)
                .remove(KEY_AUTH_EXPIRES_AT)
                .apply();
    }

    private Privacy privacyFromResponse(JSONObject root) throws IOException {
        JSONObject data = root.optJSONObject("data");
        if (!root.optBoolean("success", false) || data == null) {
            throw new IOException(errorMessage(root, "Creator profile privacy could not be loaded"));
        }
        boolean isPrivate = data.optBoolean("isPrivate", !data.optBoolean("profilePublic", true));
        return new Privacy(isPrivate, data.optBoolean("profilePublic", !isPrivate));
    }

    private String requireAuthToken() throws IOException {
        if (!hasAuthenticatedSession()) {
            throw new AuthenticationException("Discord login is required");
        }
        return authToken();
    }

    private String authToken() {
        String encrypted = preferences.getString(KEY_AUTH_TOKEN_ENCRYPTED, "").trim();
        if (!encrypted.isEmpty()) {
            try {
                return decryptAuthToken(encrypted).trim();
            } catch (IOException error) {
                clearSession();
                return "";
            }
        }

        String legacyPlaintext = preferences.getString(KEY_AUTH_TOKEN, "").trim();
        if (legacyPlaintext.isEmpty()) {
            return "";
        }
        try {
            preferences.edit()
                    .putString(KEY_AUTH_TOKEN_ENCRYPTED, encryptAuthToken(legacyPlaintext))
                    .remove(KEY_AUTH_TOKEN)
                    .apply();
            return legacyPlaintext;
        } catch (IOException error) {
            clearSession();
            return "";
        }
    }

    private String encryptAuthToken(String token) throws IOException {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, sessionSecretKey());
            byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                    + "."
                    + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        } catch (Exception error) {
            throw new IOException("Creator session could not be encrypted", error);
        }
    }

    private String decryptAuthToken(String encoded) throws IOException {
        try {
            String[] parts = encoded.split("\\.", 2);
            if (parts.length != 2) {
                throw new IOException("Invalid encrypted creator session");
            }
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, sessionSecretKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException("Creator session could not be decrypted", error);
        }
    }

    private synchronized SecretKey sessionSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEYSTORE_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER);
        keyGenerator.init(new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return keyGenerator.generateKey();
    }

    private String deviceUserHash() {
        String current = preferences.getString(KEY_DEVICE_USER_HASH, "").trim();
        if (current.matches("[A-Za-z0-9-]{8,64}")) {
            return current;
        }
        String generated = "android-" + UUID.randomUUID().toString();
        preferences.edit().putString(KEY_DEVICE_USER_HASH, generated).apply();
        return generated;
    }

    private String generateClientNonce() {
        byte[] nonce = new byte[CLIENT_NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }

    private void setPendingClientNonce(String clientNonce) {
        synchronized (pendingLoginLock) {
            pendingClientNonce = clientNonce == null ? "" : clientNonce;
        }
    }

    private String currentPendingClientNonce() {
        synchronized (pendingLoginLock) {
            return pendingClientNonce;
        }
    }

    private boolean isPendingClientNonce(String clientNonce) {
        synchronized (pendingLoginLock) {
            return isMatchingClientNonce(pendingClientNonce, clientNonce);
        }
    }

    private void clearPendingClientNonce(String clientNonce) {
        synchronized (pendingLoginLock) {
            if (isMatchingClientNonce(pendingClientNonce, clientNonce)) {
                pendingClientNonce = "";
            }
        }
    }

    private JSONObject requestJson(
            String method,
            String endpoint,
            JSONObject body,
            String bearerToken,
            String languageTag
    ) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "ivLyrics-Android/1");
        connection.setRequestProperty("Origin", "https://xpui.app.spotify.com");
        connection.setRequestProperty("Referer", "https://xpui.app.spotify.com/");
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
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String responseBody = readUtf8(input);
            JSONObject root = responseBody.isEmpty() ? new JSONObject() : new JSONObject(responseBody);
            if (status == 401 || status == 403) {
                clearSession();
                throw new AuthenticationException(
                        errorMessage(root, "Discord login is required"),
                        status
                );
            }
            if (status < 200 || status >= 300) {
                throw new IOException(errorMessage(root, "HTTP " + status));
            }
            return root;
        } catch (AuthenticationException error) {
            throw error;
        } catch (IOException error) {
            throw error;
        } catch (Exception error) {
            throw new IOException(error.getMessage(), error);
        } finally {
            connection.disconnect();
        }
    }

    private static String readUtf8(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                result.append(buffer, 0, read);
            }
        }
        return result.toString();
    }

    private static String errorMessage(JSONObject root, String fallback) {
        if (root != null) {
            String message = firstNonEmpty(root.optString("message", ""), root.optString("error", ""));
            if (!message.isEmpty()) {
                return message;
            }
        }
        return fallback;
    }

    private static String firstNonEmpty(String first, String second) {
        String left = first == null ? "" : first.trim();
        return left.isEmpty() ? (second == null ? "" : second.trim()) : left;
    }

    static final class LoginStart {
        final String authorizeUrl;

        LoginStart(String authorizeUrl) {
            this.authorizeUrl = authorizeUrl == null ? "" : authorizeUrl.trim();
        }
    }

    static final class Session {
        final String userHash;
        final long expiresAt;

        Session(String userHash, long expiresAt) {
            this.userHash = userHash == null ? "" : userHash.trim();
            this.expiresAt = Math.max(0L, expiresAt);
        }
    }

    static final class Privacy {
        final boolean isPrivate;
        final boolean profilePublic;

        Privacy(boolean isPrivate, boolean profilePublic) {
            this.isPrivate = isPrivate;
            this.profilePublic = profilePublic;
        }
    }

    static final class AuthenticationException extends IOException {
        final int statusCode;

        AuthenticationException(String message) {
            this(message, 0);
        }

        AuthenticationException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
