package com.shopmate.domain.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShoppingItemMergeTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_B = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    private static final UUID LIST_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    private ShoppingItem baseItem(long ts) {
        return new ShoppingItem(
            ITEM_ID, LIST_ID,
            new LwwField<>("Milk", ts, USER_A),
            new LwwField<>("1", ts, USER_A),
            new LwwField<>(false, ts, USER_A),
            new LwwField<>(false, ts, USER_A),
            new LwwField<>("a0", ts, USER_A),
            new LwwField<>("SONSTIGES", ts, USER_A),
            Map.of()
        );
    }

    @Test
    void concurrentEditsToSameFieldHigherTimestampWins() {
        var itemA = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Oat Milk", 200L, USER_A));
        var itemB = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Soy Milk", 300L, USER_B));
        var merged = itemA.merge(itemB);
        assertThat(merged.name().value()).isEqualTo("Soy Milk");
    }

    @Test
    void concurrentEditsToSameFieldCommutative() {
        var itemA = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Oat Milk", 200L, USER_A));
        var itemB = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Soy Milk", 300L, USER_B));
        assertThat(itemA.merge(itemB).name().value()).isEqualTo(itemB.merge(itemA).name().value());
    }

    @Test
    void concurrentEditsToSameFieldIdempotent() {
        var item = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Oat Milk", 200L, USER_A));
        assertThat(item.merge(item).name().value()).isEqualTo("Oat Milk");
    }

    @Test
    void concurrentEditsToDifferentFieldsBothSurvive() {
        var itemA = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Oat Milk", 200L, USER_A));
        var itemB = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.CHECKED, "true", 200L, USER_B));
        var merged = itemA.merge(itemB);
        assertThat(merged.name().value()).isEqualTo("Oat Milk");
        assertThat(merged.checked().value()).isTrue();
    }

    @Test
    void tombstonePropagatesToMerge() {
        var deleted = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.DELETED, "true", 200L, USER_A));
        var concurrent = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "New Name", 150L, USER_B));
        var merged = deleted.merge(concurrent);
        assertThat(merged.isDeleted()).isTrue();
        assertThat(merged.name().value()).isEqualTo("New Name");
    }

    @Test
    void deleteAfterAddWins() {
        var added = baseItem(100L);
        var deleted = added.applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.DELETED, "true", 200L, USER_A));
        assertThat(deleted.isDeleted()).isTrue();
    }

    @Test
    void concurrentSortKeyMovesConvergeToSinglePosition() {
        var moveA = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.SORT_KEY, "b0", 200L, USER_A));
        var moveB = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.SORT_KEY, "c0", 300L, USER_B));
        var mergedAB = moveA.merge(moveB);
        var mergedBA = moveB.merge(moveA);
        // Both replicas converge to same position (no duplication — Kleppmann §2)
        assertThat(mergedAB.sortKey().value()).isEqualTo(mergedBA.sortKey().value());
        assertThat(mergedAB.sortKey().value()).isEqualTo("c0"); // higher timestamp wins
    }

    @Test
    void mergeThrowsForDifferentItemIds() {
        var itemA = baseItem(100L);
        var itemB = new ShoppingItem(UUID.randomUUID(), LIST_ID,
            new LwwField<>("Other", 100L, USER_A),
            new LwwField<>("1", 100L, USER_A),
            new LwwField<>(false, 100L, USER_A),
            new LwwField<>(false, 100L, USER_A),
            new LwwField<>("a0", 100L, USER_A),
            new LwwField<>("SONSTIGES", 100L, USER_A),
            Map.of());
        assertThatThrownBy(() -> itemA.merge(itemB))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentEditsToSectionHigherTimestampWins() {
        var itemA = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.SECTION, "OBST_GEMUESE", 200L, USER_A));
        var itemB = baseItem(100L).applyChange(new ItemChange(ITEM_ID, LIST_ID, ItemField.SECTION, "GETRAENKE", 300L, USER_B));
        var merged = itemA.merge(itemB);
        assertThat(merged.section().value()).isEqualTo("GETRAENKE");
    }
}
