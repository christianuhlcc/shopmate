package com.shopmate.domain.port.out;

import com.shopmate.domain.section.Section;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for per-list learned section corrections (ADR-0012).
 * A correction is keyed by the normalized item name within a single list —
 * there is no household entity, so the shared list is the correction scope.
 */
public interface SectionCorrectionRepository {

    Optional<Section> find(UUID listId, String normalizedName);

    /**
     * LWW upsert: callers always pass the change's timestamp; only a strictly
     * higher timestamp than any previously stored correction may overwrite it
     * (mirrors the per-field compare in {@code ShoppingListRepositoryAdapter}).
     */
    void upsert(UUID listId, String normalizedName, Section section, long timestamp, UUID modifiedBy);
}
