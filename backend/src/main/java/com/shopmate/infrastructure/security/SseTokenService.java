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
import java.util.UUID;

@Service
public class SseTokenService {

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
            UUID userId = UUID.fromString(claims.getSubject());
            UUID listId = UUID.fromString(claims.get("listId", String.class));
            return new SseClaims(userId, listId);
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtException("Invalid SSE token: " + e.getMessage(), e);
        }
    }
}
