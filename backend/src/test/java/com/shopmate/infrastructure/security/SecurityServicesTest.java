package com.shopmate.infrastructure.security;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityServicesTest {

    private static final String SECRET = "test-secret-value-that-is-32-chars!!";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // --- SecurityContextHelper ---

    @Test
    void securityContextHelperParsesUserIdFromAuthenticationName() {
        UUID userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken(userId.toString(), "n/a"));

        assertThat(new SecurityContextHelper().getCurrentUserId()).isEqualTo(userId);
    }

    // --- AuthCodeService ---

    @Test
    void authCodeRoundTripReturnsJwt() {
        var service = new AuthCodeService();
        String code = service.issueCode("the-jwt");
        assertThat(service.exchange(code)).isEqualTo("the-jwt");
    }

    @Test
    void authCodeIsSingleUse() {
        var service = new AuthCodeService();
        String code = service.issueCode("the-jwt");
        service.exchange(code);
        assertThatThrownBy(() -> service.exchange(code))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownAuthCodeIsRejected() {
        var service = new AuthCodeService();
        assertThatThrownBy(() -> service.exchange("no-such-code"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- SseTokenService ---

    @Test
    void sseTokenRoundTripCarriesUserAndListScope() {
        var service = new SseTokenService(SECRET);
        UUID userId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();

        String token = service.issueSseToken(userId, listId);
        SseTokenService.SseClaims claims = service.validateSseToken(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.listId()).isEqualTo(listId);
    }

    @Test
    void garbageSseTokenIsRejected() {
        var service = new SseTokenService(SECRET);
        assertThatThrownBy(() -> service.validateSseToken("not-a-jwt"))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void sseTokenSignedWithDifferentSecretIsRejected() {
        var issuer = new SseTokenService("another-secret-that-is-also-32-chars");
        var validator = new SseTokenService(SECRET);
        String token = issuer.issueSseToken(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> validator.validateSseToken(token))
            .isInstanceOf(JwtException.class);
    }
}
