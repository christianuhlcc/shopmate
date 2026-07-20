package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.User;
import com.shopmate.domain.model.UserNotFoundException;
import com.shopmate.domain.port.out.UserRepository;
import com.shopmate.generated.model.AuthCodeRequest;
import com.shopmate.infrastructure.security.AuthCodeService;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import com.shopmate.infrastructure.security.SseTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmallControllersTest {

    @Mock AuthCodeService authCodeService;
    @Mock UserRepository userRepository;
    @Mock SecurityContextHelper securityContextHelper;
    @Mock SseTokenService sseTokenService;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID LIST_ID = UUID.randomUUID();

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
        var controller = new UserController(userRepository, securityContextHelper);

        var response = controller.getCurrentUser();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getId()).isEqualTo(USER_ID);
        assertThat(response.getBody().getEmail()).isEqualTo("me@example.com");
        assertThat(response.getBody().getAvatarUrl()).isEqualTo("http://pic");
    }

    @Test
    void userControllerThrowsWhenUserMissing() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        var controller = new UserController(userRepository, securityContextHelper);

        assertThatThrownBy(controller::getCurrentUser)
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void sseTokenControllerIssuesScopedToken() {
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
        when(sseTokenService.issueSseToken(USER_ID, LIST_ID)).thenReturn("sse-jwt");
        var controller = new SseTokenController(sseTokenService, securityContextHelper);

        var response = controller.getSseToken(LIST_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().getToken()).isEqualTo("sse-jwt");
    }
}
