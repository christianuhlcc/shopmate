package com.shopmate.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingListApplyChangeTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID LIST_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();

    private ShoppingList emptyList() {
        return new ShoppingList(LIST_ID, "Groceries", OWNER, Set.of(OWNER), Map.of(), Instant.now());
    }

    @Test
    void applyChangeToNewItemCreatesIt() {
        var list = emptyList();
        var change = new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Milk", 100L, OWNER);
        var updated = list.applyChange(change);
        assertThat(updated.items()).containsKey(ITEM_ID);
        assertThat(updated.items().get(ITEM_ID).name().value()).isEqualTo("Milk");
    }

    @Test
    void applyChangeToExistingItemMerges() {
        var list = emptyList();
        var first = new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Milk", 100L, OWNER);
        var second = new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Oat Milk", 200L, OWNER);
        var updated = list.applyChange(first).applyChange(second);
        assertThat(updated.items().get(ITEM_ID).name().value()).isEqualTo("Oat Milk");
    }

    @Test
    void activeItemsExcludesDeleted() {
        var list = emptyList();
        var uuid2 = UUID.randomUUID();
        var add1 = new ItemChange(ITEM_ID, LIST_ID, ItemField.NAME, "Milk", 100L, OWNER);
        var add2 = new ItemChange(uuid2, LIST_ID, ItemField.NAME, "Eggs", 100L, OWNER);
        var del1 = new ItemChange(ITEM_ID, LIST_ID, ItemField.DELETED, "true", 200L, OWNER);
        var updated = list.applyChange(add1).applyChange(add2).applyChange(del1);
        assertThat(updated.activeItems()).hasSize(1);
        assertThat(updated.activeItems().get(0).name().value()).isEqualTo("Eggs");
    }

    @Test
    void activeItemsSortedBySortKey() {
        var list = emptyList();
        var idA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var idB = UUID.fromString("00000000-0000-0000-0000-000000000002");
        // Add item with later sort key first
        var itemA = new ShoppingItem(idA, LIST_ID,
            new LwwField<>("Milk", 100L, OWNER),
            new LwwField<>("1", 100L, OWNER),
            new LwwField<>(false, 100L, OWNER),
            new LwwField<>(false, 100L, OWNER),
            new LwwField<>("b0", 100L, OWNER),
            Map.of());
        var itemB = new ShoppingItem(idB, LIST_ID,
            new LwwField<>("Eggs", 100L, OWNER),
            new LwwField<>("1", 100L, OWNER),
            new LwwField<>(false, 100L, OWNER),
            new LwwField<>(false, 100L, OWNER),
            new LwwField<>("a0", 100L, OWNER),
            Map.of());
        Map<UUID, ShoppingItem> items = new HashMap<>();
        items.put(idA, itemA);
        items.put(idB, itemB);
        var listWithItems = new ShoppingList(LIST_ID, "Groceries", OWNER, Set.of(OWNER), Map.copyOf(items), Instant.now());
        var active = listWithItems.activeItems();
        assertThat(active.get(0).name().value()).isEqualTo("Eggs");   // "a0" < "b0"
        assertThat(active.get(1).name().value()).isEqualTo("Milk");
    }

    @Test
    void activeItemsBreaksEqualSortKeysByIdString() {
        var list = emptyList();
        // Ids chosen so lexicographic string order is the deciding factor.
        var idFirst = UUID.fromString("0aaaaaaa-0000-0000-0000-000000000000");
        var idSecond = UUID.fromString("0bbbbbbb-0000-0000-0000-000000000000");
        var addSecond = new ItemChange(idSecond, LIST_ID, ItemField.NAME, "Bananas", 100L, OWNER);
        var addFirst = new ItemChange(idFirst, LIST_ID, ItemField.NAME, "Apples", 200L, OWNER);
        // Both keep the default sort key "a0"; insertion order is second-then-first.
        var updated = list.applyChange(addSecond).applyChange(addFirst);
        var active = updated.activeItems();
        assertThat(active.get(0).id()).isEqualTo(idFirst);
        assertThat(active.get(1).id()).isEqualTo(idSecond);
    }
}
