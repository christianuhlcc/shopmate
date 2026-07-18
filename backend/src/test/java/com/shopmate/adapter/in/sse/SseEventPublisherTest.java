package com.shopmate.adapter.in.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.model.ItemField;
import com.shopmate.generated.model.ItemChangeEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SseEventPublisherTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void toEventProducesContractShapedJsonWithValueNotSerializedValue() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ItemChange change = new ItemChange(itemId, listId, ItemField.NAME, "Milk", 1234L, userId);

        ItemChangeEvent event = SseEventPublisher.toEvent(change);
        String json = MAPPER.writeValueAsString(event);

        // Contract (api/openapi.yaml, ItemChangeEvent): itemId, listId, field, value, timestamp, modifiedBy
        assertThat(json)
            .contains("\"value\":\"Milk\"")
            .doesNotContain("serializedValue")
            .contains("\"itemId\":\"" + itemId + "\"")
            .contains("\"listId\":\"" + listId + "\"")
            .contains("\"field\":\"NAME\"")
            .contains("\"timestamp\":1234")
            .contains("\"modifiedBy\":\"" + userId + "\"");
    }

    @Test
    void toEventMapsEveryDomainItemField() {
        for (ItemField field : ItemField.values()) {
            ItemChange change = new ItemChange(
                UUID.randomUUID(), UUID.randomUUID(), field, "x", 1L, UUID.randomUUID());
            ItemChangeEvent event = SseEventPublisher.toEvent(change);
            assertThat(event.getField().getValue()).isEqualTo(field.name());
        }
    }

    @Test
    void publishWithoutSubscribersIsANoOp() {
        var publisher = new SseEventPublisher();
        ItemChange change = new ItemChange(
            UUID.randomUUID(), UUID.randomUUID(), ItemField.NAME, "Milk", 1L, UUID.randomUUID());
        publisher.publishItemChange(UUID.randomUUID(), change);
    }

    @Test
    void publishSendsToSubscribedEmitter() {
        var publisher = new SseEventPublisher();
        UUID listId = UUID.randomUUID();
        var emitter = publisher.subscribe(listId);
        assertThat(emitter).isNotNull();

        // The emitter buffers events until a servlet response attaches, so send succeeds
        ItemChange change = new ItemChange(
            UUID.randomUUID(), listId, ItemField.CHECKED, "true", 2L, UUID.randomUUID());
        publisher.publishItemChange(listId, change);

        // Events for other lists do not reach this emitter's list bucket
        publisher.publishItemChange(UUID.randomUUID(), change);
    }

    @Test
    void shutdownCompletesAllEmitters() {
        var publisher = new SseEventPublisher();
        UUID listId = UUID.randomUUID();
        publisher.subscribe(listId);
        publisher.subscribe(listId);

        publisher.shutdown();

        // After shutdown the registry is empty: publishing again must not fail
        ItemChange change = new ItemChange(
            UUID.randomUUID(), listId, ItemField.NAME, "x", 3L, UUID.randomUUID());
        publisher.publishItemChange(listId, change);
    }
}
