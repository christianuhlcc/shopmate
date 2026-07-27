package com.shopmate.domain.port.out;

import com.shopmate.domain.model.PicnicAccountLink;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for Picnic link persistence. Storage-agnostic and deals in plaintext
 * {@link PicnicAccountLink}s — encryption of the session key at rest is an adapter concern.
 */
public interface PicnicCredentialsRepository {

    Optional<PicnicAccountLink> findByUserId(UUID userId);

    void save(UUID userId, PicnicAccountLink link);

    void delete(UUID userId);
}
