package com.shopmate.adapter.in.web;

import com.shopmate.generated.model.SseTokenResponse;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import com.shopmate.infrastructure.security.SseTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/lists/{listId}/sse-token")
public class SseTokenController {

    private final SseTokenService sseTokenService;
    private final SecurityContextHelper securityContextHelper;

    public SseTokenController(SseTokenService sseTokenService, SecurityContextHelper securityContextHelper) {
        this.sseTokenService = sseTokenService;
        this.securityContextHelper = securityContextHelper;
    }

    @PostMapping
    public ResponseEntity<SseTokenResponse> getSseToken(@PathVariable UUID listId) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        String token = sseTokenService.issueSseToken(currentUserId, listId);
        return ResponseEntity.ok(new SseTokenResponse(token));
    }
}
