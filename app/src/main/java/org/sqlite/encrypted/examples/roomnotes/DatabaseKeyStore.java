package org.sqlite.encrypted.examples.roomnotes;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores the random SEE database key encrypted by a non-exportable Android Keystore key. */
public final class DatabaseKeyStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "sqlite-encrypted-room-notes-key-wrap";
    private static final String PREFERENCES = "encrypted-room-notes";
    private static final String CIPHERTEXT = "database-key-ciphertext";
    private static final String IV = "database-key-iv";

    private final SharedPreferences preferences;

    public DatabaseKeyStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public synchronized String getOrCreate() throws Exception {
        SecretKey wrappingKey = getOrCreateWrappingKey();
        String ciphertext = preferences.getString(CIPHERTEXT, null);
        String iv = preferences.getString(IV, null);
        if (ciphertext != null && iv != null) {
            return decrypt(wrappingKey, ciphertext, iv);
        }

        byte[] randomKey = new byte[32];
        new SecureRandom().nextBytes(randomKey);
        String databaseKey = toHex(randomKey);
        store(wrappingKey, databaseKey);
        return databaseKey;
    }

    private static SecretKey getOrCreateWrappingKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        java.security.Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) {
            return (SecretKey) existing;
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private void store(SecretKey wrappingKey, String databaseKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey);
        byte[] encrypted = cipher.doFinal(databaseKey.getBytes(StandardCharsets.UTF_8));
        boolean saved = preferences.edit()
                .putString(CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .commit();
        if (!saved) {
            throw new IllegalStateException("Could not persist the encrypted database key");
        }
    }

    private static String decrypt(SecretKey wrappingKey, String ciphertext, String iv)
            throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                wrappingKey,
                new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
        byte[] plaintext = cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP));
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }
}
