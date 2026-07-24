package com.shopmate.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Service
public class SseTokenService {

    /**
     * Audience marking a token as stream-only. SSE tokens are signed with the
     * same secret as the 24-hour session JWT and carry the same {@code sub}, so
     * without this marker the resource server — which validates signature and
     * expiry only — accepts one as a bearer token on every {@code /api/**}
     * endpoint. That matters because SSE tokens travel in a query string
     * (EventSource cannot set headers) and therefore reach access logs.
     * {@code SecurityConfig} rejects any token carrying this audience.
     */
    public static final String SSE_AUDIENCE = "shopmate-sse";

    public record SseClaims(UUID userId, UUID listId) {}

    private final SecretKey secretKey;

    public SseTokenService(@Value("${shopmate.jwt.secret}") String secret) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issueSseToken(UUID userId, UUID listId) {
        long now = System.currentTimeMillis();
        long exp = now + 15L * 60 * 1000; // 15 minutes
        return Jwts.builder()
                .subject(userId.toString())
                .audience().add(SSE_AUDIENCE).and()
                .claim("listId", listId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public SseClaims validateSseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // Closes the confusion in the other direction: a session JWT must not
            // open a stream. It would already fail on the missing listId claim —
            // this makes the rule explicit rather than incidental.
            // getAudience() is null, not empty, when the claim is absent — which
            // is exactly the session-JWT case, so check before dereferencing.
            Set<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(SSE_AUDIENCE)) {
                throw new JwtException("not an SSE token");
            }
            UUID userId = UUID.fromString(claims.getSubject());
            UUID listId = UUID.fromString(claims.get("listId", String.class));
            return new SseClaims(userId, listId);
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("Invalid SSE token: " + e.getMessage(), e);
        }
    }
}
