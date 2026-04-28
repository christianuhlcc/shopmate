package com.shopmate.domain.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable domain record for a shopping list item.
 * Each mutable field is a LWW register; ordering is via the sortKey field.
 * Per Kleppmann (PaPoC '20 §3): position is a separate LWW register — never move via delete+reinsert.
 */
public record ShoppingItem(
    UUID id,
    UUID listId,
    LwwField<String> name,
    LwwField<String> quantity,
    LwwField<Boolean> checked,
    LwwField<Boolean> deleted,
    LwwField<String> sortKey,
    Map<UUID, Long> vectorClock
) {

    public ShoppingItem merge(ShoppingItem other) {
        if (!this.id().equals(other.id())) {
            throw new IllegalArgumentException("Cannot merge items with different IDs");
        }
        return new ShoppingItem(
            id, listId,
            name.merge(other.name()),
            quantity.merge(other.quantity()),
            checked.merge(other.checked()),
            deleted.merge(other.deleted()),
            sortKey.merge(other.sortKey()),
            mergeVectorClocks(vectorClock, other.vectorClock())
        );
    }

    public ShoppingItem applyChange(ItemChange change) {
        return switch (change.field()) {
            case NAME -> new ShoppingItem(id, listId,
                name.merge(new LwwField<>(change.serializedValue(), change.timestamp(), change.modifiedBy())),
                quantity, checked, deleted, sortKey, vectorClock);
            case QUANTITY -> new ShoppingItem(id, listId, name,
                quantity.merge(new LwwField<>(change.serializedValue(), change.timestamp(), change.modifiedBy())),
                checked, deleted, sortKey, vectorClock);
            case CHECKED -> new ShoppingItem(id, listId, name, quantity,
                checked.merge(new LwwField<>(Boolean.parseBoolean(change.serializedValue()), change.timestamp(), change.modifiedBy())),
                deleted, sortKey, vectorClock);
            case DELETED -> new ShoppingItem(id, listId, name, quantity, checked,
                deleted.merge(new LwwField<>(Boolean.parseBoolean(change.serializedValue()), change.timestamp(), change.modifiedBy())),
                sortKey, vectorClock);
            case SORT_KEY -> new ShoppingItem(id, listId, name, quantity, checked, deleted,
                sortKey.merge(new LwwField<>(change.serializedValue(), change.timestamp(), change.modifiedBy())),
                vectorClock);
        };
    }

    public boolean isDeleted() {
        return deleted.value();
    }

    private static Map<UUID, Long> mergeVectorClocks(Map<UUID, Long> a, Map<UUID, Long> b) {
        Map<UUID, Long> result = new HashMap<>(a);
        b.forEach((key, val) -> result.merge(key, val, Math::max));
        return Map.copyOf(result);
    }
}
