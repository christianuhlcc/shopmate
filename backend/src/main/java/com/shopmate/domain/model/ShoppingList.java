package com.shopmate.domain.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ShoppingList(
    UUID id,
    String name,
    UUID ownerId,
    UUID groupId,
    Map<UUID, ShoppingItem> items,
    Instant createdAt
) {

    public ShoppingList applyChange(ItemChange change) {
        ShoppingItem existing = items.get(change.itemId());
        ShoppingItem updated = existing == null
            ? newItemFromChange(change)
            : existing.applyChange(change);
        Map<UUID, ShoppingItem> newItems = new HashMap<>(items);
        newItems.put(updated.id(), updated);
        return new ShoppingList(id, name, ownerId, groupId, Map.copyOf(newItems), createdAt);
    }

    public List<ShoppingItem> activeItems() {
        return items.values().stream()
            .filter(i -> !i.isDeleted())
            // Tie-break on the id's string form so equal sort keys order the same
            // everywhere: UUID.compareTo (signed longs) differs from the client's
            // lexicographic string comparison.
            .sorted(Comparator.comparing((ShoppingItem i) -> i.sortKey().value())
                .thenComparing(i -> i.id().toString()))
            .toList();
    }

    public boolean isOwner(UUID userId) {
        return ownerId.equals(userId);
    }

    private static ShoppingItem newItemFromChange(ItemChange change) {
        // Use timestamp 0 so the incoming change always wins the LWW merge regardless of field.
        UUID by = change.modifiedBy();
        LwwField<String> name = new LwwField<>("", 0L, by);
        LwwField<String> quantity = new LwwField<>("1", 0L, by);
        LwwField<Boolean> checked = new LwwField<>(false, 0L, by);
        LwwField<Boolean> deleted = new LwwField<>(false, 0L, by);
        LwwField<String> sortKey = new LwwField<>("a0", 0L, by);
        LwwField<String> section = new LwwField<>("SONSTIGES", 0L, by);
        ShoppingItem item = new ShoppingItem(change.itemId(), change.listId(),
            name, quantity, checked, deleted, sortKey, section, Map.of());
        return item.applyChange(change);
    }
}
