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
import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.security.interfaces.RSAPrivateKey;

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
//    public String encrypt(String plaintext, String recipientPublicKeyBase64) {
//        try {
//            byte[] pubBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP);
//            KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
//            PublicKey recipientKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
//
//            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
//            cipher.init(Cipher.ENCRYPT_MODE, recipientKey);
//            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
//            return Base64.encodeToString(encrypted, Base64.NO_WRAP);
//        } catch (Exception e) {
//            Log.e(TAG, "Encryption failed", e);
//            return null;
//        }
//    }


    public String encrypt(String plaintext, String recipientPublicKeyBase64) {
        try {
            // 1. Load recipient's public key
            byte[] pubBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP);
            KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey recipientKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            RSAPublicKey rsaRecipientKey = (RSAPublicKey) recipientKey;

            BigInteger eRecipient = rsaRecipientKey.getPublicExponent();
            BigInteger nRecipient = rsaRecipientKey.getModulus();

            // 2. Load our own keys
            RSAPrivateKey rsaOwnPrivateKey = (RSAPrivateKey) privateKey;
            BigInteger dOwn = rsaOwnPrivateKey.getPrivateExponent();
            BigInteger nOwn = rsaOwnPrivateKey.getModulus();

            // 3. Message as positive BigInteger
            BigInteger m = new BigInteger(1, plaintext.getBytes(StandardCharsets.UTF_8));

            // Verify bounds
            if (m.compareTo(nOwn) >= 0 || m.compareTo(nRecipient) >= 0) {
                Log.e(TAG, "Message too large for moduli");
                return null;
            }

            BigInteger c;
            Log.d(TAG, "========== DOUBLE RSA ENCRYPTION ==========");
            Log.d(TAG, "Plaintext = " + plaintext);
            Log.d(TAG, "M = " + m);
            Log.d(TAG, "nOwn (Sender) = " + nOwn);
            Log.d(TAG, "nRecipient (Recipient) = " + nRecipient);

            // Compare moduli to prevent info loss
            if (nOwn.compareTo(nRecipient) < 0) {
                // n_sender < n_recipient: Sign (own private) then Encrypt (recipient public)
                Log.d(TAG, "Order: Sign first, then Encrypt (n_sender < n_recipient)");
                BigInteger mSigned = m.modPow(dOwn, nOwn);
                Log.d(TAG, "M_signed = M^d_sender mod n_sender = " + mSigned);
                c = mSigned.modPow(eRecipient, nRecipient);
                Log.d(TAG, "C = M_signed^e_recipient mod n_recipient = " + c);
            } else {
                // n_recipient < n_sender: Encrypt (recipient public) then Sign (own private)
                Log.d(TAG, "Order: Encrypt first, then Sign (n_recipient <= n_sender)");
                BigInteger mEncrypted = m.modPow(eRecipient, nRecipient);
                Log.d(TAG, "M_encrypted = M^e_recipient mod n_recipient = " + mEncrypted);
                c = mEncrypted.modPow(dOwn, nOwn);
                Log.d(TAG, "C = M_encrypted^d_sender mod n_sender = " + c);
            }
            Log.d(TAG, "==========================================");

            return Base64.encodeToString(c.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ex) {
            Log.e(TAG, "Encryption failed", ex);
            return null;
        }
    }

    /**
     * Decrypts ciphertext using this user's private key and validates authenticity using the sender's public key.
     *
     * @param ciphertextBase64 Base64-encoded ciphertext
     * @param senderPublicKeyBase64 Sender's public key (Base64-encoded X.509)
     * @return Decrypted plaintext, or "[Decryption failed]" on error
     */
    public String decrypt(String ciphertextBase64, String senderPublicKeyBase64) {
        try {
            // 1. Decode ciphertext
            byte[] cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP);
            BigInteger c = new BigInteger(1, cipherBytes);

            // 2. Load sender's public key
            byte[] pubBytes = Base64.decode(senderPublicKeyBase64, Base64.NO_WRAP);
            KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
            PublicKey senderKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
            RSAPublicKey rsaSenderKey = (RSAPublicKey) senderKey;

            BigInteger eSender = rsaSenderKey.getPublicExponent();
            BigInteger nSender = rsaSenderKey.getModulus();

            // 3. Load our own keys (recipient)
            RSAPrivateKey rsaOwnPrivateKey = (RSAPrivateKey) privateKey;
            BigInteger dOwn = rsaOwnPrivateKey.getPrivateExponent();
            BigInteger nOwn = rsaOwnPrivateKey.getModulus();

            BigInteger m;
            Log.d(TAG, "========== DOUBLE RSA DECRYPTION ==========");
            Log.d(TAG, "Ciphertext C = " + c);
            Log.d(TAG, "nSender (Sender) = " + nSender);
            Log.d(TAG, "nOwn (Recipient) = " + nOwn);

            // Compare moduli to determine decryption order
            if (nSender.compareTo(nOwn) < 0) {
                // n_sender < n_recipient: Sign first, then Encrypt
                // Decryption order: Decrypt (own private) then Unsign (sender public)
                Log.d(TAG, "Order: Decrypt first, then Unsign (n_sender < n_recipient)");
                BigInteger mSigned = c.modPow(dOwn, nOwn);
                Log.d(TAG, "M_signed = C^d_recipient mod n_recipient = " + mSigned);
                m = mSigned.modPow(eSender, nSender);
                Log.d(TAG, "M = M_signed^e_sender mod n_sender = " + m);
            } else {
                // n_recipient < n_sender: Encrypt first, then Sign
                // Decryption order: Unsign (sender public) then Decrypt (own private)
                Log.d(TAG, "Order: Unsign first, then Decrypt (n_recipient <= n_sender)");
                BigInteger mEncrypted = c.modPow(eSender, nSender);
                Log.d(TAG, "M_encrypted = C^e_sender mod n_sender = " + mEncrypted);
                m = mEncrypted.modPow(dOwn, nOwn);
                Log.d(TAG, "M = M_encrypted^d_recipient mod n_recipient = " + m);
            }

            byte[] decryptedBytes = m.toByteArray();
            // Strip leading zero byte if present (BigInteger.toByteArray positive padding)
            if (decryptedBytes.length > 0 && decryptedBytes[0] == 0) {
                byte[] tmp = new byte[decryptedBytes.length - 1];
                System.arraycopy(decryptedBytes, 1, tmp, 0, tmp.length);
                decryptedBytes = tmp;
            }

            String plaintext = new String(decryptedBytes, StandardCharsets.UTF_8);
            Log.d(TAG, "Plaintext = " + plaintext);
            Log.d(TAG, "==========================================");

            return plaintext;
        } catch (Exception ex) {
            Log.e(TAG, "Decryption failed", ex);
            return "[Decryption failed]";
        }
    }

    /**
     * Helper decrypt method for backwards compatibility or self-encrypted messages.
     */
    public String decrypt(String ciphertextBase64) {
        return decrypt(ciphertextBase64, getPublicKeyBase64());
    }
}
