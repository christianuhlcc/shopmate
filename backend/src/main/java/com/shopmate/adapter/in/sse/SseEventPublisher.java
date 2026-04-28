package com.shopmate.adapter.in.sse;

import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.port.out.EventPublisher;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseEventPublisher implements EventPublisher {

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    @Override
    public void publishItemChange(UUID listId, ItemChange change) {
        List<SseEmitter> listEmitters = emitters.getOrDefault(listId, new CopyOnWriteArrayList<>());
        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : listEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("item-change")
                        .data(change));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        listEmitters.removeAll(dead);
    }

    public SseEmitter subscribe(UUID listId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.computeIfAbsent(listId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(listId);
            if (list != null) {
                list.remove(emitter);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    @PreDestroy
    public void shutdown() {
        emitters.values().forEach(list -> list.forEach(SseEmitter::complete));
        emitters.clear();
    }
}
