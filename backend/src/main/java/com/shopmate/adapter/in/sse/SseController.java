package com.shopmate.adapter.in.sse;

import com.shopmate.infrastructure.security.SseTokenService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/lists/{listId}/events")
public class SseController {

    private final SseEventPublisher sseEventPublisher;
    private final SseTokenService sseTokenService;

    public SseController(SseEventPublisher sseEventPublisher, SseTokenService sseTokenService) {
        this.sseEventPublisher = sseEventPublisher;
        this.sseTokenService = sseTokenService;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(
            @PathVariable UUID listId,
            @RequestParam String token) {
        try {
            sseTokenService.validateSseToken(token);
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(sseEventPublisher.subscribe(listId));
    }
}
