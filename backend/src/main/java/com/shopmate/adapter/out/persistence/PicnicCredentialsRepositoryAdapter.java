package com.shopmate.adapter.out.persistence;

import com.shopmate.adapter.out.persistence.entity.PicnicCredentialsEntity;
import com.shopmate.adapter.out.persistence.repository.SpringDataPicnicCredentialsRepository;
import com.shopmate.domain.model.PicnicAccountLink;
import com.shopmate.domain.model.PicnicLinkState;
import com.shopmate.domain.model.PicnicSession;
import com.shopmate.domain.port.out.PicnicCredentialsRepository;
import com.shopmate.infrastructure.security.CredentialCipher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Encrypts the Picnic session key at rest via {@link CredentialCipher} (ADR-0014) — the
 * domain-facing {@link PicnicAccountLink} always carries the plaintext key; encryption is
 * strictly an adapter concern, applied on the way in and reversed on the way out.
 *
 * <p>The device id is stored in the clear: on its own it identifies nothing and it is useless
 * without the key it is paired with.
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
    public Optional<PicnicAccountLink> findByUserId(UUID userId) {
        return jpa.findById(userId).map(e -> new PicnicAccountLink(
            e.getEmail(),
            new PicnicSession(
                new String(cipher.decrypt(e.getAuthKeyEncrypted()), StandardCharsets.UTF_8),
                e.getDeviceId()),
            PicnicLinkState.valueOf(e.getStatus())));
    }

    @Override
    @Transactional
    public void save(UUID userId, PicnicAccountLink link) {
        byte[] encrypted = cipher.encrypt(link.session().authKey().getBytes(StandardCharsets.UTF_8));

        Optional<PicnicCredentialsEntity> existing = jpa.findById(userId);
        if (existing.isPresent()) {
            PicnicCredentialsEntity entity = existing.get();
            entity.setEmail(link.email());
            entity.setDeviceId(link.session().deviceId());
            entity.setAuthKeyEncrypted(encrypted);
            entity.setStatus(link.state().name());
            entity.setUpdatedAt(Instant.now());
            jpa.save(entity);
            return;
        }

        Instant now = Instant.now();
        jpa.save(new PicnicCredentialsEntity(userId, link.email(), link.session().deviceId(),
            encrypted, link.state().name(), now, now));
    }

    @Override
    @Transactional
    public void delete(UUID userId) {
        jpa.findById(userId).ifPresent(jpa::delete);
    }
}
