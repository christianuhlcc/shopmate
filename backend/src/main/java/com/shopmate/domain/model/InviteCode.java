package com.shopmate.domain.model;

import java.time.Instant;
import java.util.UUID;

public record InviteCode(
    UUID id,
    String code,
    InviteType type,
    UUID groupId,
    UUID createdBy,
    Instant createdAt,
    Instant expiresAt,
    UUID usedBy,
    Instant usedAt
) {

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedBy != null;
    }
}
