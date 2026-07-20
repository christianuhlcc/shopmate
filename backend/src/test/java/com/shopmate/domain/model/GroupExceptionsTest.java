package com.shopmate.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroupExceptionsTest {

    @Test
    void noGroupExceptionMessageIncludesUserId() {
        UUID userId = UUID.randomUUID();
        var ex = new NoGroupException(userId);
        assertThat(ex.getMessage()).contains(userId.toString());
    }

    @Test
    void inviteInvalidExceptionCarriesMessage() {
        var ex = new InviteInvalidException("Invite code not found: ABCDEFGH");
        assertThat(ex.getMessage()).isEqualTo("Invite code not found: ABCDEFGH");
    }

    @Test
    void inviteExpiredExceptionCarriesMessage() {
        var ex = new InviteExpiredException("Invite code expired: ABCDEFGH");
        assertThat(ex.getMessage()).isEqualTo("Invite code expired: ABCDEFGH");
    }

    @Test
    void alreadyInGroupExceptionMessageIncludesUserId() {
        UUID userId = UUID.randomUUID();
        var ex = new AlreadyInGroupException(userId);
        assertThat(ex.getMessage()).contains(userId.toString());
    }

    @Test
    void groupNameRequiredExceptionHasFixedMessage() {
        var ex = new GroupNameRequiredException();
        assertThat(ex.getMessage()).isEqualTo("Group name is required to redeem a NEW_GROUP invite");
    }
}
