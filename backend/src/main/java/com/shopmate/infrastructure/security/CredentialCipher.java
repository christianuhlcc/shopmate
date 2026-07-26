package com.shopmate.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-GCM encrypt/decrypt helper for the Picnic MD5 password digest (ADR-0014):
 * that digest is Picnic's own login wire format, but it functions as a bearer
 * credential for their API, so it is encrypted at rest with a standing secret
 * ({@code shopmate.picnic.credential-enc-key}) — the same env-var-only pattern
 * as {@code shopmate.jwt.secret}.
 *
 * <p>The configured secret can be any length; it is hashed with SHA-256 to
 * deterministically derive a 32-byte AES-256 key, so callers never have to
 * supply an exact key length.
 */
@Component
public class CredentialCipher {

    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public CredentialCipher(@Value("${shopmate.picnic.credential-enc-key}") String secret) {
        this.keySpec = new SecretKeySpec(deriveKey(secret), "AES");
    }

    private static byte[] deriveKey(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every standard JVM; this is unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Encrypts {@code plaintext} under a fresh random IV and returns {@code iv || ciphertext}
     * (the GCM auth tag is appended to the ciphertext by {@link Cipher} itself).
     */
    public byte[] encrypt(byte[] plaintext) {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    /**
     * Splits {@code ivAndCiphertext} into its leading 12-byte IV and the remaining ciphertext,
     * then decrypts. A tampered IV or ciphertext fails GCM's built-in auth-tag check and throws
     * rather than silently returning garbage.
     */
    public byte[] decrypt(byte[] ivAndCiphertext) {
        byte[] iv = Arrays.copyOfRange(ivAndCiphertext, 0, GCM_IV_LENGTH_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(ivAndCiphertext, GCM_IV_LENGTH_BYTES, ivAndCiphertext.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt credential", e);
        }
    }
}
