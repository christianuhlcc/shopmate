package com.shopmate.infrastructure.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthCodeService {

    private record AuthCodeEntry(String jwt, Instant expiresAt, boolean used) {}

    private final ConcurrentHashMap<String, AuthCodeEntry> codes = new ConcurrentHashMap<>();

    public String issueCode(String jwt) {
        String code = UUID.randomUUID().toString();
        codes.put(code, new AuthCodeEntry(jwt, Instant.now().plusSeconds(60), false));
        return code;
    }

    public String exchange(String code) {
        AuthCodeEntry[] result = new AuthCodeEntry[1];
        codes.compute(code, (k, entry) -> {
            if (entry == null || entry.used() || Instant.now().isAfter(entry.expiresAt())) {
                throw new IllegalArgumentException("Invalid or expired auth code");
            }
            result[0] = entry;
            return new AuthCodeEntry(entry.jwt(), entry.expiresAt(), true);
        });
        return result[0].jwt();
    }
}
