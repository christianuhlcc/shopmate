package com.shopmate.domain.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ShoppingList(
    UUID id,
    String name,
    UUID ownerId,
    Set<UUID> memberIds,
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
        return new ShoppingList(id, name, ownerId, memberIds, Map.copyOf(newItems), createdAt);
    }

    public List<ShoppingItem> activeItems() {
        return items.values().stream()
            .filter(i -> !i.isDeleted())
            .sorted(Comparator.comparing(i -> i.sortKey().value()))
            .toList();
    }

    public boolean isMember(UUID userId) {
        return ownerId.equals(userId) || memberIds.contains(userId);
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
        ShoppingItem item = new ShoppingItem(change.itemId(), change.listId(),
            name, quantity, checked, deleted, sortKey, Map.of());
        return item.applyChange(change);
    }
}
