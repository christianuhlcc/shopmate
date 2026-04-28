package com.shopmate.domain.model;

import java.util.UUID;

public class ListNotFoundException extends RuntimeException {
    public ListNotFoundException(UUID listId) {
        super("Shopping list not found: " + listId);
    }
}
