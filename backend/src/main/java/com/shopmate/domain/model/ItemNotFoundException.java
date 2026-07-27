package com.shopmate.domain.model;

import java.util.UUID;

/**
 * The requested item is not on the list, or is no longer exportable (checked off or deleted).
 * Both collapse to 404: from the caller's side there is nothing at that address to suggest for.
 */
public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(UUID itemId) {
        super("Item not found on list: " + itemId);
    }
}
