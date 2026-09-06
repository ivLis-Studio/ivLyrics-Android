package kr.ivlis.ivlyricsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureStringStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "ivlyrics.settings.secrets.v1";
    private static final String PREFS_NAME = "ai_lyrics_secure_values_v1";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SharedPreferences preferences;

    SecureStringStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    synchronized String getString(String key, String defaultValue) {
        String encoded = preferences.getString(key, "");
        if (encoded == null || encoded.isEmpty()) return defaultValue;
        try {
            byte[] payload = Base64.decode(encoded, Base64.NO_WRAP);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            int ivLength = buffer.getInt();
            if (ivLength < 12 || ivLength > 32 || buffer.remaining() <= ivLength) {
                throw new IllegalArgumentException("Invalid encrypted value");
            }
            byte[] iv = new byte[ivLength];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            preferences.edit().remove(key).apply();
            return defaultValue;
        }
    }

    synchronized void putString(String key, String value) {
        String normalized = value == null ? "" : value;
        if (normalized.isEmpty()) {
            remove(key);
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(4 + iv.length + ciphertext.length);
            payload.putInt(iv.length);
            payload.put(iv);
            payload.put(ciphertext);
            preferences.edit().putString(
                    key,
                    Base64.encodeToString(payload.array(), Base64.NO_WRAP)
            ).apply();
        } catch (Exception error) {
            throw new IllegalStateException("Unable to protect ivLyrics credentials", error);
        }
    }

    synchronized void remove(String key) {
        preferences.edit().remove(key).apply();
    }

    synchronized void migrateFrom(SharedPreferences source, String... keys) {
        if (source == null || keys == null) return;
        SharedPreferences.Editor editor = source.edit();
        boolean changed = false;
        for (String key : keys) {
            if (key == null || !source.contains(key)) continue;
            String value = source.getString(key, "");
            if (value != null && !value.isEmpty()) {
                putString(key, value);
            }
            editor.remove(key);
            changed = true;
        }
        if (changed) editor.apply();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
