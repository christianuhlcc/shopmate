package com.shopmate.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InviteTypeTest {

    @Test
    void hasExactlyJoinAndNewGroupValues() {
        assertThat(InviteType.values()).containsExactly(InviteType.JOIN_GROUP, InviteType.NEW_GROUP);
    }

    @Test
    void valueOfRoundTrips() {
        assertThat(InviteType.valueOf("JOIN_GROUP")).isEqualTo(InviteType.JOIN_GROUP);
        assertThat(InviteType.valueOf("NEW_GROUP")).isEqualTo(InviteType.NEW_GROUP);
    }
}
