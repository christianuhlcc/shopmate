package com.shopmate.infrastructure.security;

import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2SuccessHandlerTest {

    private static final String SECRET = "test-secret-value-that-is-32-chars!!";
    private static final String FRONTEND = "http://localhost:3000";

    @Mock UserRepository userRepository;
    @Mock AuthCodeService authCodeService;
    @Mock Authentication authentication;

    private OAuth2User googleUser(String email) {
        return new DefaultOAuth2User(
            List.of(),
            Map.of("email", email, "name", "Alice", "picture", "http://pic"),
            "email");
    }

    @Test
    void newUserIsCreatedAndRedirectedWithAuthCode() throws Exception {
        var handler = new GoogleOAuth2SuccessHandler(userRepository, authCodeService, SECRET, FRONTEND);
        when(authentication.getPrincipal()).thenReturn(googleUser("new@example.com"));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(i -> i.getArgument(0));
        when(authCodeService.issueCode(org.mockito.ArgumentMatchers.anyString())).thenReturn("code-42");

        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().email()).isEqualTo("new@example.com");
        assertThat(saved.getValue().displayName()).isEqualTo("Alice");
        assertThat(response.getRedirectedUrl()).isEqualTo(FRONTEND + "/auth/callback?code=code-42");
    }

    @Test
    void mintedJwtIsAcceptedByResourceServerDecoderEvenWithLongSecret() throws Exception {
        // Prod secrets are 64 bytes (openssl rand -base64 48). jjwt's
        // signWith(key) would auto-select HS512 for such a key, which the
        // NimbusJwtDecoder built by SecurityConfig rejects (HS256 default) —
        // every authenticated request then fails with 401.
        String longSecret = "x".repeat(64);
        var handler = new GoogleOAuth2SuccessHandler(userRepository, authCodeService, longSecret, FRONTEND);
        UUID userId = UUID.randomUUID();
        when(authentication.getPrincipal()).thenReturn(googleUser("new@example.com"));
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(i -> {
                User u = i.getArgument(0);
                return new User(userId, u.email(), u.displayName(), u.avatarUrl());
            });
        when(authCodeService.issueCode(org.mockito.ArgumentMatchers.anyString())).thenReturn("code-1");

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication);

        ArgumentCaptor<String> jwt = ArgumentCaptor.forClass(String.class);
        verify(authCodeService).issueCode(jwt.capture());
        var decoder = new SecurityConfig(null, longSecret).jwtDecoder();
        assertThat(decoder.decode(jwt.getValue()).getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void existingUserKeepsIdAndGetsUpdatedProfile() throws Exception {
        var handler = new GoogleOAuth2SuccessHandler(userRepository, authCodeService, SECRET, FRONTEND);
        UUID existingId = UUID.randomUUID();
        when(authentication.getPrincipal()).thenReturn(googleUser("known@example.com"));
        when(userRepository.findByEmail("known@example.com"))
            .thenReturn(Optional.of(new User(existingId, "known@example.com", "Old Name", null)));
        when(userRepository.save(org.mockito.ArgumentMatchers.any()))
            .thenAnswer(i -> i.getArgument(0));
        when(authCodeService.issueCode(org.mockito.ArgumentMatchers.anyString())).thenReturn("code-7");

        var response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(existingId);
        assertThat(saved.getValue().displayName()).isEqualTo("Alice");
        assertThat(response.getRedirectedUrl()).endsWith("?code=code-7");
    }
}
