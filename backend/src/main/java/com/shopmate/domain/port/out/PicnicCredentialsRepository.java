package com.shopmate.domain.port.out;

import com.shopmate.domain.model.PicnicCredentials;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for Picnic credential persistence. Storage-agnostic and deals in
 * plaintext-digest {@link PicnicCredentials} — encryption at rest is an adapter concern.
 */
public interface PicnicCredentialsRepository {

    Optional<PicnicCredentials> findByUserId(UUID userId);

    void save(UUID userId, PicnicCredentials credentials);

    void delete(UUID userId);
}
