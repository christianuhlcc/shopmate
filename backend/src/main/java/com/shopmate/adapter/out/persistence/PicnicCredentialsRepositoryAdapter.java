package com.shopmate.adapter.out.persistence;

import com.shopmate.adapter.out.persistence.entity.PicnicCredentialsEntity;
import com.shopmate.adapter.out.persistence.repository.SpringDataPicnicCredentialsRepository;
import com.shopmate.domain.model.PicnicCredentials;
import com.shopmate.domain.port.out.PicnicCredentialsRepository;
import com.shopmate.infrastructure.security.CredentialCipher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Encrypts the MD5 password digest at rest via {@link CredentialCipher} (ADR-0014) — the
 * domain-facing {@link PicnicCredentials} record always carries the plaintext digest; encryption
 * is strictly an adapter concern, applied on the way in and reversed on the way out.
 */
@Component
public class PicnicCredentialsRepositoryAdapter implements PicnicCredentialsRepository {

    private final SpringDataPicnicCredentialsRepository jpa;
    private final CredentialCipher cipher;

    public PicnicCredentialsRepositoryAdapter(SpringDataPicnicCredentialsRepository jpa, CredentialCipher cipher) {
        this.jpa = jpa;
        this.cipher = cipher;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PicnicCredentials> findByUserId(UUID userId) {
        return jpa.findById(userId).map(e -> new PicnicCredentials(
            e.getEmail(),
            new String(cipher.decrypt(e.getPasswordMd5Encrypted()), StandardCharsets.UTF_8)));
    }

    @Override
    @Transactional
    public void save(UUID userId, PicnicCredentials credentials) {
        byte[] encrypted = cipher.encrypt(credentials.passwordMd5Hex().getBytes(StandardCharsets.UTF_8));

        Optional<PicnicCredentialsEntity> existing = jpa.findById(userId);
        if (existing.isPresent()) {
            PicnicCredentialsEntity entity = existing.get();
            entity.setEmail(credentials.email());
            entity.setPasswordMd5Encrypted(encrypted);
            entity.setUpdatedAt(Instant.now());
            jpa.save(entity);
            return;
        }

        Instant now = Instant.now();
        jpa.save(new PicnicCredentialsEntity(userId, credentials.email(), encrypted, now, now));
    }

    @Override
    @Transactional
    public void delete(UUID userId) {
        jpa.findById(userId).ifPresent(jpa::delete);
    }
}
