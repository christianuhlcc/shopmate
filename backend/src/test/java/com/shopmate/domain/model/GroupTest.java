package com.shopmate.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GroupTest {

    @Test
    void exposesConstructorArgumentsViaAccessors() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-01T00:00:00Z");
        Group group = new Group(id, "ShopMate", createdAt);

        assertThat(group.id()).isEqualTo(id);
        assertThat(group.name()).isEqualTo("ShopMate");
        assertThat(group.createdAt()).isEqualTo(createdAt);
    }

    @Test
    void equalsAndHashCodeAreValueBased() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-07-01T00:00:00Z");
        Group a = new Group(id, "ShopMate", createdAt);
        Group b = new Group(id, "ShopMate", createdAt);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("ShopMate");
    }
}
