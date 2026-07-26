package com.shopmate.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CredentialCipherTest {

    private final CredentialCipher cipher = new CredentialCipher("unit-test-secret-key-of-arbitrary-length");

    @Test
    void roundTripsPlaintext() {
        byte[] plaintext = "5f4dcc3b5aa765d61d8327deb882cf99".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = cipher.encrypt(plaintext);
        byte[] decrypted = cipher.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void samePlaintextEncryptsDifferentlyEachTimeDueToRandomIv() {
        byte[] plaintext = "same-password-digest".getBytes(StandardCharsets.UTF_8);

        byte[] first = cipher.encrypt(plaintext);
        byte[] second = cipher.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second);
        // But both still decrypt back to the same plaintext.
        assertThat(cipher.decrypt(first)).isEqualTo(plaintext);
        assertThat(cipher.decrypt(second)).isEqualTo(plaintext);
    }

    @Test
    void tamperedCiphertextByteFailsDecryption() {
        byte[] plaintext = "another-digest-value".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cipher.encrypt(plaintext);

        // Flip a bit well past the IV, inside the ciphertext/tag region.
        byte[] tampered = encrypted.clone();
        int lastIndex = tampered.length - 1;
        tampered[lastIndex] = (byte) (tampered[lastIndex] ^ 0xFF);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tamperedIvByteFailsDecryption() {
        byte[] plaintext = "yet-another-digest".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cipher.encrypt(plaintext);

        byte[] tampered = encrypted.clone();
        tampered[0] = (byte) (tampered[0] ^ 0xFF);

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }
}
