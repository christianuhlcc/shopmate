package com.shopmate.adapter.out.persistence.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for {@link SectionCorrectionEntity}: a learned
 * section correction is scoped to a single list and a single normalized
 * item name (ADR-0012).
 */
public class SectionCorrectionId implements Serializable {

    private UUID listId;
    private String normalizedName;

    public SectionCorrectionId() {}

    public SectionCorrectionId(UUID listId, String normalizedName) {
        this.listId = listId;
        this.normalizedName = normalizedName;
    }

    public UUID getListId() { return listId; }
    public String getNormalizedName() { return normalizedName; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SectionCorrectionId that)) return false;
        return Objects.equals(listId, that.listId) && Objects.equals(normalizedName, that.normalizedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listId, normalizedName);
    }
}
