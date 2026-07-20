package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.AccessForbiddenException;
import com.shopmate.domain.model.Group;
import com.shopmate.domain.model.InviteCode;
import com.shopmate.domain.model.InviteType;
import com.shopmate.domain.model.NoGroupException;
import com.shopmate.domain.model.User;
import com.shopmate.domain.model.UserNotFoundException;
import com.shopmate.domain.port.in.GroupUseCase;
import com.shopmate.domain.port.in.InviteUseCase;
import com.shopmate.domain.port.in.ShoppingListUseCase;
import com.shopmate.domain.port.out.GroupRepository;
import com.shopmate.domain.port.out.UserRepository;
import com.shopmate.generated.model.AuthCodeRequest;
import com.shopmate.generated.model.CreateInviteRequest;
import com.shopmate.generated.model.RedeemInviteRequest;
import com.shopmate.infrastructure.security.AuthCodeService;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import com.shopmate.infrastructure.security.SseTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmallControllersTest {

    @Mock AuthCodeService authCodeService;
    @Mock UserRepository userRepository;
    @Mock GroupRepository groupRepository;
    @Mock GroupUseCase groupUseCase;
    @Mock InviteUseCase inviteUseCase;
    @Mock SecurityContextHelper securityContextHelper;
    @Mock SseTokenService sseTokenService;
    @Mock ShoppingListUseCase shoppingListUseCase;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LIST_ID = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-07-01T10:00:00Z");

    @Test
    void authControllerExchangesCodeForJwt() {
        when(authCodeService.exchange("code-1")).thenReturn("jwt-1");
        var controller = new AuthController(authCodeService);

        var response = controller.exchangeAuthCode(new AuthCodeRequest("code-1"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getToken()).isEqualTo("jwt-1");
    }

    @Test
    void userControllerReturnsCurrentUserProfile() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(new User(USER_ID, "me@example.com", "Me", "http://pic", null)));
        var controller = new UserController(userRepository, groupRepository, securityContextHelper);

        var response = controller.getCurrentUser();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getId()).isEqualTo(USER_ID);
        assertThat(response.getBody().getEmail()).isEqualTo("me@example.com");
        assertThat(response.getBody().getAvatarUrl()).isEqualTo("http://pic");
        assertThat(response.getBody().getGroup()).isNull();
    }

    @Test
    void userControllerPopulatesGroupWhenPresent() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID))
            .thenReturn(Optional.of(new User(USER_ID, "me@example.com", "Me", "http://pic", GROUP_ID)));
        when(groupRepository.findById(GROUP_ID))
            .thenReturn(Optional.of(new Group(GROUP_ID, "The Household", CREATED_AT)));
        var controller = new UserController(userRepository, groupRepository, securityContextHelper);

        var response = controller.getCurrentUser();

        assertThat(response.getBody().getGroup()).isNotNull();
        assertThat(response.getBody().getGroup().getId()).isEqualTo(GROUP_ID);
        assertThat(response.getBody().getGroup().getName()).isEqualTo("The Household");
    }

    @Test
    void userControllerThrowsWhenUserMissing() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        var controller = new UserController(userRepository, groupRepository, securityContextHelper);

        assertThatThrownBy(controller::getCurrentUser)
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void sseTokenControllerIssuesScopedToken() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        when(sseTokenService.issueSseToken(USER_ID, LIST_ID)).thenReturn("sse-jwt");
        var controller = new SseTokenController(sseTokenService, securityContextHelper, shoppingListUseCase);

        var response = controller.getSseToken(LIST_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getToken()).isEqualTo("sse-jwt");
        verify(shoppingListUseCase).assertListAccess(LIST_ID, USER_ID);
    }

    @Test
    void sseTokenControllerRejectsAccessToAnotherGroupsList() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        doThrow(new AccessForbiddenException("not your list"))
            .when(shoppingListUseCase).assertListAccess(LIST_ID, USER_ID);
        var controller = new SseTokenController(sseTokenService, securityContextHelper, shoppingListUseCase);

        assertThatThrownBy(() -> controller.getSseToken(LIST_ID))
            .isInstanceOf(AccessForbiddenException.class);

        verify(sseTokenService, never()).issueSseToken(USER_ID, LIST_ID);
    }

    @Test
    void sseTokenControllerRejectsGroupLessCaller() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        doThrow(new NoGroupException(USER_ID))
            .when(shoppingListUseCase).assertListAccess(LIST_ID, USER_ID);
        var controller = new SseTokenController(sseTokenService, securityContextHelper, shoppingListUseCase);

        assertThatThrownBy(() -> controller.getSseToken(LIST_ID))
            .isInstanceOf(NoGroupException.class);

        verify(sseTokenService, never()).issueSseToken(USER_ID, LIST_ID);
    }

    @Test
    void groupControllerReturnsGroupWithMembers() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        Group group = new Group(GROUP_ID, "The Household", CREATED_AT);
        when(groupUseCase.getGroupForUser(USER_ID)).thenReturn(group);
        User member = new User(USER_ID, "me@example.com", "Me", "http://pic", GROUP_ID);
        when(groupUseCase.getGroupMembers(USER_ID)).thenReturn(List.of(member));
        var controller = new GroupController(groupUseCase, securityContextHelper);

        var response = controller.getMyGroup();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getId()).isEqualTo(GROUP_ID);
        assertThat(response.getBody().getName()).isEqualTo("The Household");
        assertThat(response.getBody().getMembers()).hasSize(1);
        assertThat(response.getBody().getMembers().get(0).getEmail()).isEqualTo("me@example.com");
    }

    @Test
    void inviteControllerCreatesInvite() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        Instant expiresAt = CREATED_AT.plusSeconds(604800);
        InviteCode invite = new InviteCode(UUID.randomUUID(), "ABCD2345", InviteType.JOIN_GROUP,
            GROUP_ID, USER_ID, CREATED_AT, expiresAt, null, null);
        when(inviteUseCase.createInvite(USER_ID, InviteType.JOIN_GROUP)).thenReturn(invite);
        var controller = new InviteController(inviteUseCase, groupRepository, securityContextHelper);

        var response = controller.createInvite(
            new CreateInviteRequest(com.shopmate.generated.model.InviteType.JOIN_GROUP));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getCode()).isEqualTo("ABCD2345");
        assertThat(response.getBody().getType()).isEqualTo(com.shopmate.generated.model.InviteType.JOIN_GROUP);
    }

    @Test
    void inviteControllerRedeemReturnsUpdatedProfileWithGroup() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        User updated = new User(USER_ID, "me@example.com", "Me", "http://pic", GROUP_ID);
        when(inviteUseCase.redeemInvite(USER_ID, "ABCD2345", null)).thenReturn(updated);
        when(groupRepository.findById(GROUP_ID))
            .thenReturn(Optional.of(new Group(GROUP_ID, "The Household", CREATED_AT)));
        var controller = new InviteController(inviteUseCase, groupRepository, securityContextHelper);

        var response = controller.redeemInvite(new RedeemInviteRequest("ABCD2345"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getId()).isEqualTo(USER_ID);
        assertThat(response.getBody().getGroup()).isNotNull();
        assertThat(response.getBody().getGroup().getName()).isEqualTo("The Household");
    }
}
