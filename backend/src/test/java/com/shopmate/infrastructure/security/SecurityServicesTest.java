package com.shopmate.infrastructure.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
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

    // --- Session/SSE token separation -------------------------------------
    // Both are HS256 over the same secret with the same `sub`, so nothing but
    // the audience keeps a stream token — which travels in a URL, and so
    // reaches logs — from working as a bearer token on /api/**.

    /** Mirrors the token GoogleOAuth2SuccessHandler issues: no audience claim. */
    private static String sessionJwt(UUID userId) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .subject(userId.toString())
            .issuedAt(new Date(now))
            .expiration(new Date(now + 60_000))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
    }

    @Test
    void resourceServerRejectsAnSseTokenPresentedAsABearerToken() {
        JwtDecoder decoder = new SecurityConfig(null, SECRET).jwtDecoder();
        String sseToken = new SseTokenService(SECRET)
            .issueSseToken(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> decoder.decode(sseToken))
            .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
    }

    @Test
    void resourceServerStillAcceptsAnAudienceLessSessionJwt() {
        JwtDecoder decoder = new SecurityConfig(null, SECRET).jwtDecoder();
        UUID userId = UUID.randomUUID();

        // Sessions live 24 h and carry no `aud`; rejecting by audience rather
        // than requiring one is what keeps this deploy from logging everyone out.
        assertThat(decoder.decode(sessionJwt(userId)).getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void sseEndpointRejectsASessionJwt() {
        var service = new SseTokenService(SECRET);

        assertThatThrownBy(() -> service.validateSseToken(sessionJwt(UUID.randomUUID())))
            .isInstanceOf(JwtException.class)
            .hasMessageContaining("not an SSE token");
    }
}
