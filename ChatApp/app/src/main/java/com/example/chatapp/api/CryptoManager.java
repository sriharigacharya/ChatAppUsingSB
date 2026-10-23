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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

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

    // ── Streaming File Encryption & Decryption (Google Tink Streaming AEAD) ──

    /**
     * Generates a 256-bit (32-byte) random symmetric AES key for streaming file encryption.
     */
    public byte[] generateFileKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    /**
     * Wraps a 32-byte symmetric AES file key using the participant's RSA public key (Base64 X.509).
     */
    public String wrapKeyForUser(byte[] fileKey, String userPublicKeyBase64) throws Exception {
        byte[] pubBytes = Base64.decode(userPublicKeyBase64, Base64.NO_WRAP);
        KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
        PublicKey pubKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        byte[] encryptedKey = cipher.doFinal(fileKey);
        return Base64.encodeToString(encryptedKey, Base64.NO_WRAP);
    }

    /**
     * Unwraps the encrypted 32-byte symmetric AES file key using this device's RSA private key.
     */
    public byte[] unwrapKey(String encryptedKeyBase64) throws Exception {
        byte[] encryptedBytes = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedBytes);
    }

    /**
     * Encrypts a file in 64 KB segments using Tink's AesGcmHkdfStreaming to prevent heap exhaustion.
     */
    public void encryptFile(File inputFile, File outputFile, byte[] fileKey) throws Exception {
        com.google.crypto.tink.subtle.AesGcmHkdfStreaming streamingAead =
                new com.google.crypto.tink.subtle.AesGcmHkdfStreaming(
                        fileKey,
                        "HmacSha256",
                        32,
                        64 * 1024, // 64 KB segment chunks
                        0
                );

        byte[] associatedData = new byte[0];
        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile);
             OutputStream encStream = streamingAead.newEncryptingStream(fos, associatedData)) {

            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                encStream.write(buffer, 0, read);
            }
        }
    }

    /**
     * Decrypts a ciphertext stream directly to a destination file in 64 KB segments.
     */
    public void decryptFile(InputStream inputStream, File outputFile, byte[] fileKey) throws Exception {
        com.google.crypto.tink.subtle.AesGcmHkdfStreaming streamingAead =
                new com.google.crypto.tink.subtle.AesGcmHkdfStreaming(
                        fileKey,
                        "HmacSha256",
                        32,
                        64 * 1024, // 64 KB segment chunks
                        0
                );

        byte[] associatedData = new byte[0];
        try (InputStream decStream = streamingAead.newDecryptingStream(inputStream, associatedData);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = decStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
    }

    /**
     * Post-decryption magic byte verification on the client side.
     */
    public static String detectMimeTypeByMagicBytes(File file) {
        if (!file.exists() || file.length() < 4) return "application/octet-stream";
        byte[] header = new byte[12];
        try (FileInputStream fis = new FileInputStream(file)) {
            int read = fis.read(header);
            if (read < 4) return "application/octet-stream";

            // JPEG: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if ((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                return "image/png";
            }
            // GIF: 47 49 46 38 ("GIF8")
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') {
                return "image/gif";
            }
            // WEBP: "RIFF" .... "WEBP"
            if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                read >= 12 && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
                return "image/webp";
            }
            // PDF: %PDF (25 50 44 46)
            if (header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F') {
                return "application/pdf";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed reading magic bytes", e);
        }
        return "application/octet-stream";
    }
}
