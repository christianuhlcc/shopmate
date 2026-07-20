package com.shopmate.application.service;

import com.shopmate.domain.model.AlreadyInGroupException;
import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.GroupNameRequiredException;
import com.shopmate.domain.model.InviteCode;
import com.shopmate.domain.model.InviteExpiredException;
import com.shopmate.domain.model.InviteInvalidException;
import com.shopmate.domain.model.InviteType;
import com.shopmate.domain.model.NoGroupException;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.InviteCodeRepository;
import com.shopmate.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InviteServiceTest {

    @Mock InviteCodeRepository inviteCodeRepository;
    @Mock UserRepository userRepository;
    @Mock GroupRepository groupRepository;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final String BOOTSTRAP_CODE = "SETUP123";

    private InviteService service;
    private InviteService serviceWithBootstrap;

    @BeforeEach
    void setUp() {
        service = new InviteService(inviteCodeRepository, userRepository, groupRepository, "");
        serviceWithBootstrap = new InviteService(inviteCodeRepository, userRepository, groupRepository, BOOTSTRAP_CODE);
    }

    private User grouplessUser() {
        return new User(USER_ID, "user@example.com", "User", null, null);
    }

    private User groupedUser() {
        return new User(USER_ID, "user@example.com", "User", null, GROUP_ID);
    }

    private InviteCode joinInvite(UUID groupId, Instant expiresAt) {
        return new InviteCode(UUID.randomUUID(), "ABCDEFGH", InviteType.JOIN_GROUP, groupId,
            UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS), expiresAt, null, null);
    }

    private InviteCode newGroupInvite(Instant expiresAt) {
        return new InviteCode(UUID.randomUUID(), "ABCDEFGH", InviteType.NEW_GROUP, null,
            UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS), expiresAt, null, null);
    }

    // ---- createInvite ----

    @Test
    void createInviteThrowsNoGroupExceptionForGrouplessUser() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        assertThatThrownBy(() -> service.createInvite(USER_ID, InviteType.JOIN_GROUP))
            .isInstanceOf(NoGroupException.class);
    }

    @Test
    void createInviteJoinGroupCarriesIssuersGroupId() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));
        when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(inviteCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InviteCode invite = service.createInvite(USER_ID, InviteType.JOIN_GROUP);

        assertThat(invite.groupId()).isEqualTo(GROUP_ID);
        assertThat(invite.type()).isEqualTo(InviteType.JOIN_GROUP);
        assertThat(invite.createdBy()).isEqualTo(USER_ID);
    }

    @Test
    void createInviteNewGroupHasNullGroupId() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));
        when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(inviteCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InviteCode invite = service.createInvite(USER_ID, InviteType.NEW_GROUP);

        assertThat(invite.groupId()).isNull();
        assertThat(invite.type()).isEqualTo(InviteType.NEW_GROUP);
    }

    @Test
    void createInviteExpiresInSevenDays() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));
        when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(inviteCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InviteCode invite = service.createInvite(USER_ID, InviteType.JOIN_GROUP);

        Duration ttl = Duration.between(invite.createdAt(), invite.expiresAt());
        assertThat(ttl).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void createInviteCodeIsEightCharsFromAllowedAlphabet() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));
        when(inviteCodeRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(inviteCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        for (int i = 0; i < 50; i++) {
            InviteCode invite = service.createInvite(USER_ID, InviteType.JOIN_GROUP);
            assertThat(invite.code()).hasSize(8);
            assertThat(invite.code()).matches("[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}");
        }
    }

    @Test
    void createInviteRetriesOnCollision() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));
        InviteCode existing = joinInvite(GROUP_ID, Instant.now().plus(7, ChronoUnit.DAYS));
        // First lookup finds a collision, second finds none.
        when(inviteCodeRepository.findByCode(anyString()))
            .thenReturn(Optional.of(existing))
            .thenReturn(Optional.empty());
        when(inviteCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.createInvite(USER_ID, InviteType.JOIN_GROUP);

        verify(inviteCodeRepository, times(2)).findByCode(anyString());
    }

    // ---- redeemInvite: validation ordering ----

    @Test
    void redeemInviteThrowsAlreadyInGroupExceptionForGroupedCaller() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));
        assertThatThrownBy(() -> service.redeemInvite(USER_ID, "ABCDEFGH", null))
            .isInstanceOf(AlreadyInGroupException.class);
    }

    @Test
    void redeemInviteThrowsInvalidForUnknownCode() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        when(inviteCodeRepository.findByCode("NOPE0000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemInvite(USER_ID, "NOPE0000", null))
            .isInstanceOf(InviteInvalidException.class);
    }

    @Test
    void redeemInviteThrowsInvalidForAlreadyUsedCode() {
        InviteCode used = new InviteCode(UUID.randomUUID(), "ABCDEFGH", InviteType.JOIN_GROUP, GROUP_ID,
            UUID.randomUUID(), Instant.now().minus(2, ChronoUnit.DAYS), Instant.now().plus(5, ChronoUnit.DAYS),
            UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        when(inviteCodeRepository.findByCode("ABCDEFGH")).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.redeemInvite(USER_ID, "ABCDEFGH", null))
            .isInstanceOf(InviteInvalidException.class);
    }

    @Test
    void redeemInviteThrowsExpiredForExpiredCode() {
        InviteCode expired = joinInvite(GROUP_ID, Instant.now().minus(1, ChronoUnit.HOURS));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        when(inviteCodeRepository.findByCode("ABCDEFGH")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.redeemInvite(USER_ID, "ABCDEFGH", null))
            .isInstanceOf(InviteExpiredException.class);
    }

    @Test
    void redeemInviteThrowsGroupNameRequiredForNewGroupWithoutName() {
        InviteCode invite = newGroupInvite(Instant.now().plus(5, ChronoUnit.DAYS));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        when(inviteCodeRepository.findByCode("ABCDEFGH")).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> service.redeemInvite(USER_ID, "ABCDEFGH", "  "))
            .isInstanceOf(GroupNameRequiredException.class);
        verify(inviteCodeRepository, never()).markUsed(any(), any(), any());
    }

    @Test
    void redeemInviteTreatsLostMarkUsedRaceAsInvalid() {
        InviteCode invite = joinInvite(GROUP_ID, Instant.now().plus(5, ChronoUnit.DAYS));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        when(inviteCodeRepository.findByCode("ABCDEFGH")).thenReturn(Optional.of(invite));
        when(inviteCodeRepository.markUsed(eq(invite.id()), eq(USER_ID), any())).thenReturn(false);

        assertThatThrownBy(() -> service.redeemInvite(USER_ID, "ABCDEFGH", null))
            .isInstanceOf(InviteInvalidException.class);
        verify(userRepository, never()).save(any());
    }

    // ---- redeemInvite: happy paths ----

    @Test
    void redeemInviteJoinGroupHappyPath() {
        InviteCode invite = joinInvite(GROUP_ID, Instant.now().plus(5, ChronoUnit.DAYS));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        when(inviteCodeRepository.findByCode("ABCDEFGH")).thenReturn(Optional.of(invite));
        when(inviteCodeRepository.markUsed(eq(invite.id()), eq(USER_ID), any())).thenReturn(true);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User updated = service.redeemInvite(USER_ID, "ABCDEFGH", null);

        assertThat(updated.groupId()).isEqualTo(GROUP_ID);
        verify(groupRepository, never()).save(any());
    }

    @Test
    void redeemInviteNewGroupHappyPath() {
        InviteCode invite = newGroupInvite(Instant.now().plus(5, ChronoUnit.DAYS));
        Group newGroup = new Group(UUID.randomUUID(), "The Household", Instant.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        when(inviteCodeRepository.findByCode("ABCDEFGH")).thenReturn(Optional.of(invite));
        when(inviteCodeRepository.markUsed(eq(invite.id()), eq(USER_ID), any())).thenReturn(true);
        when(groupRepository.save(any())).thenReturn(newGroup);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User updated = service.redeemInvite(USER_ID, "ABCDEFGH", "The Household");

        assertThat(updated.groupId()).isEqualTo(newGroup.id());
        ArgumentCaptor<Group> captor = ArgumentCaptor.forClass(Group.class);
        verify(groupRepository).save(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("The Household");
    }

    // ---- bootstrap code ----

    @Test
    void redeemInviteBootstrapCodeCreatesReusableNewGroup() {
        Group newGroup = new Group(UUID.randomUUID(), "Bootstrapped", Instant.now());
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        when(groupRepository.save(any())).thenReturn(newGroup);
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User updated = serviceWithBootstrap.redeemInvite(USER_ID, BOOTSTRAP_CODE, "Bootstrapped");

        assertThat(updated.groupId()).isEqualTo(newGroup.id());
        verify(inviteCodeRepository, never()).findByCode(anyString());
        verify(inviteCodeRepository, never()).markUsed(any(), any(), any());
    }

    @Test
    void redeemInviteBootstrapCodeRequiresGroupName() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));

        assertThatThrownBy(() -> serviceWithBootstrap.redeemInvite(USER_ID, BOOTSTRAP_CODE, null))
            .isInstanceOf(GroupNameRequiredException.class);
    }

    @Test
    void redeemInviteBootstrapCodeStillRequiresGrouplessCaller() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(groupedUser()));

        assertThatThrownBy(() -> serviceWithBootstrap.redeemInvite(USER_ID, BOOTSTRAP_CODE, "Name"))
            .isInstanceOf(AlreadyInGroupException.class);
    }

    @Test
    void redeemInviteDoesNotTreatArbitraryCodeAsBootstrapWhenDisabled() {
        // Empty bootstrap code ("" as configured for `service`) must never match, even an empty submitted code.
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(grouplessUser()));
        when(inviteCodeRepository.findByCode("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeemInvite(USER_ID, "", null))
            .isInstanceOf(InviteInvalidException.class);
    }
}
