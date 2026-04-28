package com.shopmate.domain.model;

import java.util.UUID;

/**
 * Last-Write-Wins register for a single field.
 * Timestamps are always server-assigned; clients never supply them.
 * Tie-break on equal timestamps uses UUID string comparison for deterministic convergence.
 */
public record LwwField<T>(T value, long timestamp, UUID modifiedBy) {

    public LwwField<T> merge(LwwField<T> other) {
        if (other.timestamp() > this.timestamp()) return other;
        if (other.timestamp() == this.timestamp()) {
            // Deterministic tie-break: higher UUID string wins (commutativity guaranteed)
            return other.modifiedBy().toString().compareTo(this.modifiedBy().toString()) > 0
                ? other : this;
        }
        return this;
    }
}
