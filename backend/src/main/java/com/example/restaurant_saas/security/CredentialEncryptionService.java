package com.example.restaurant_saas.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts/decrypts third-party credentials at rest (e.g. a restaurant's Woovi AppID, see
 * PixIntegration) - reversible, unlike a password hash, because these values must be sent back
 * out to the third-party API later, not just compared. AES/256-GCM with a single master key from
 * {@code PIX_ENCRYPTION_KEY} (base64, 32 bytes), never stored in the database - same "secret only
 * lives in an env var" posture as {@code WAREHOUSE_SSO_SECRET}/{@code BREVO_API_KEY}. GCM is
 * authenticated: a tampered ciphertext fails to decrypt instead of silently returning garbage.
 * Not Pix-specific by name - reusable for any other per-restaurant secret the project needs later.
 */
@Service
public class CredentialEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec masterKey;

    public CredentialEncryptionService(@Value("${app.credential-encryption-key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        this.masterKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] ivAndCiphertext = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, ivAndCiphertext, 0, iv.length);
            System.arraycopy(ciphertext, 0, ivAndCiphertext, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(ivAndCiphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] ivAndCiphertext = Base64.getDecoder().decode(stored);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] ciphertext = new byte[ivAndCiphertext.length - IV_LENGTH_BYTES];
            System.arraycopy(ivAndCiphertext, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(ivAndCiphertext, IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt credential - wrong key or tampered value", e);
        }
    }
}
