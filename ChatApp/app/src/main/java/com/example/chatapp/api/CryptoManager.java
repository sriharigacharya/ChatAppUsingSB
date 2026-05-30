package com.example.chatapp.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * Manages RSA 2048-bit keypair for end-to-end encryption.
 *
 * Key storage:
 *   - Public key:  SharedPreferences (also sent to server via friend requests)
 *   - Private key: SharedPreferences on device ONLY — never leaves the device
 *
 * You can inspect the keys during a demo via Android Studio's Device File Explorer:
 *   /data/data/com.example.chatapp/shared_prefs/CryptoPrefs.xml
 */
public class CryptoManager {
    private static final String TAG = "CryptoManager";
    private static final String PREF_NAME = "CryptoPrefs";
    private static final String KEY_PUBLIC = "rsa_public_key";
    private static final String KEY_PRIVATE = "rsa_private_key";
    private static final String RSA_ALGORITHM = "RSA";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding";

    private final SharedPreferences prefs;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    public CryptoManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadOrGenerateKeys();
    }

    private void loadOrGenerateKeys() {
        String pubStr = prefs.getString(KEY_PUBLIC, null);
        String privStr = prefs.getString(KEY_PRIVATE, null);

        if (pubStr != null && privStr != null) {
            try {
                byte[] pubBytes = Base64.decode(pubStr, Base64.NO_WRAP);
                byte[] privBytes = Base64.decode(privStr, Base64.NO_WRAP);
                KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
                publicKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
                privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
                Log.d(TAG, "Loaded existing RSA keypair from SharedPreferences");
            } catch (Exception e) {
                Log.e(TAG, "Failed to load keys, generating new ones", e);
                generateNewKeyPair();
            }
        } else {
            generateNewKeyPair();
        }
    }

    private void generateNewKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            publicKey = kp.getPublic();
            privateKey = kp.getPrivate();

            String pubStr = Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
            String privStr = Base64.encodeToString(privateKey.getEncoded(), Base64.NO_WRAP);

            prefs.edit()
                    .putString(KEY_PUBLIC, pubStr)
                    .putString(KEY_PRIVATE, privStr)
                    .apply();

            Log.d(TAG, "Generated new RSA 2048-bit keypair");
            Log.d(TAG, "Public key (first 50 chars): " + pubStr.substring(0, Math.min(50, pubStr.length())) + "...");
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate RSA keypair", e);
        }
    }

    /** Returns the user's public key as a Base64-encoded string (X.509 format). */
    public String getPublicKeyBase64() {
        return Base64.encodeToString(publicKey.getEncoded(), Base64.NO_WRAP);
    }

    /**
     * Encrypts plaintext using the recipient's RSA public key.
     * Max plaintext length: ~245 bytes for RSA 2048-bit with PKCS1Padding.
     *
     * @param plaintext                The message to encrypt
     * @param recipientPublicKeyBase64 Recipient's public key (Base64-encoded X.509)
     * @return Base64-encoded ciphertext, or null on failure
     */
    public String encrypt(String plaintext, String recipientPublicKeyBase64) {
        try {
            byte[] pubBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP);
            KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey recipientKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, recipientKey);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    /**
     * Decrypts ciphertext using this user's private key.
     *
     * @param ciphertextBase64 Base64-encoded ciphertext
     * @return Decrypted plaintext, or "[Decryption failed]" on error
     */
    public String decrypt(String ciphertextBase64) {
        try {
            byte[] encrypted = Base64.decode(ciphertextBase64, Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return "[Decryption failed]";
        }
    }
}
