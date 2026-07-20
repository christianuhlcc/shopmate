package com.shopmate.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InviteCodeTest {

    private static final UUID ID = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID CREATED_BY = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-07-08T00:00:00Z");

    private InviteCode unusedCode() {
        return new InviteCode(ID, "ABCDEFGH", InviteType.JOIN_GROUP, GROUP_ID, CREATED_BY,
            CREATED_AT, EXPIRES_AT, null, null);
    }

    @Test
    void isNotExpiredBeforeExpiryInstant() {
        InviteCode code = unusedCode();
        assertThat(code.isExpired(EXPIRES_AT.minusSeconds(1))).isFalse();
    }

    @Test
    void isExpiredAfterExpiryInstant() {
        InviteCode code = unusedCode();
        assertThat(code.isExpired(EXPIRES_AT.plusSeconds(1))).isTrue();
    }

    @Test
    void isNotExpiredExactlyAtExpiryInstant() {
        // now.isAfter(expiresAt) is false when they're equal — the boundary instant
        // itself is still valid.
        InviteCode code = unusedCode();
        assertThat(code.isExpired(EXPIRES_AT)).isFalse();
    }

    @Test
    void isNotUsedWhenUsedByIsNull() {
        InviteCode code = unusedCode();
        assertThat(code.isUsed()).isFalse();
    }

    @Test
    void isUsedWhenUsedByIsSet() {
        InviteCode code = new InviteCode(ID, "ABCDEFGH", InviteType.NEW_GROUP, null, CREATED_BY,
            CREATED_AT, EXPIRES_AT, UUID.randomUUID(), Instant.parse("2026-07-02T00:00:00Z"));
        assertThat(code.isUsed()).isTrue();
    }
}
