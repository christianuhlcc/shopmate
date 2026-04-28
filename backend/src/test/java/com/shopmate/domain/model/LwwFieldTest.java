package com.shopmate.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LwwFieldTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_B = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Test
    void higherTimestampWins() {
        var old = new LwwField<>("old", 100L, USER_A);
        var newer = new LwwField<>("new", 200L, USER_B);
        assertThat(old.merge(newer).value()).isEqualTo("new");
        assertThat(newer.merge(old).value()).isEqualTo("new");
    }

    @Test
    void lowerTimestampLoses() {
        var old = new LwwField<>("old", 100L, USER_A);
        var newer = new LwwField<>("new", 200L, USER_B);
        assertThat(newer.merge(old).value()).isEqualTo("new");
    }

    @Test
    void tieBreakHigherUuidWins() {
        var fieldA = new LwwField<>("valueA", 100L, USER_A); // lower UUID
        var fieldB = new LwwField<>("valueB", 100L, USER_B); // higher UUID
        assertThat(fieldA.merge(fieldB).value()).isEqualTo("valueB");
        assertThat(fieldB.merge(fieldA).value()).isEqualTo("valueB");
    }

    @Test
    void mergeIsCommutative() {
        var f1 = new LwwField<>("v1", 100L, USER_A);
        var f2 = new LwwField<>("v2", 200L, USER_B);
        assertThat(f1.merge(f2).value()).isEqualTo(f2.merge(f1).value());
    }

    @Test
    void mergeIsIdempotent() {
        var f = new LwwField<>("v", 100L, USER_A);
        assertThat(f.merge(f).value()).isEqualTo(f.value());
        assertThat(f.merge(f).timestamp()).isEqualTo(f.timestamp());
    }

    @Test
    void mergeIsAssociative() {
        var f1 = new LwwField<>("v1", 100L, USER_A);
        var f2 = new LwwField<>("v2", 200L, USER_B);
        var f3 = new LwwField<>("v3", 300L, USER_A);
        // (f1 merge f2) merge f3  ==  f1 merge (f2 merge f3)
        assertThat(f1.merge(f2).merge(f3).value())
            .isEqualTo(f1.merge(f2.merge(f3)).value());
    }
}
