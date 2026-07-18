package com.shopmate.adapter.in.sse;

import com.shopmate.infrastructure.security.SseTokenService;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseControllerTest {

    @Mock SseEventPublisher sseEventPublisher;
    @Mock SseTokenService sseTokenService;

    private static final UUID LIST_ID = UUID.randomUUID();

    @Test
    void subscribeWithValidTokenReturnsEmitter() {
        var controller = new SseController(sseEventPublisher, sseTokenService);
        SseEmitter emitter = new SseEmitter();
        when(sseTokenService.validateSseToken("good"))
            .thenReturn(new SseTokenService.SseClaims(UUID.randomUUID(), LIST_ID));
        when(sseEventPublisher.subscribe(LIST_ID)).thenReturn(emitter);

        var response = controller.subscribe(LIST_ID, "good");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(emitter);
    }

    @Test
    void subscribeWithInvalidTokenReturns401() {
        var controller = new SseController(sseEventPublisher, sseTokenService);
        when(sseTokenService.validateSseToken("bad")).thenThrow(new JwtException("invalid"));

        var response = controller.subscribe(LIST_ID, "bad");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(sseEventPublisher, never()).subscribe(LIST_ID);
    }
}
