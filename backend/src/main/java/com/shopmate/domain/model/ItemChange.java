package com.shopmate.domain.model;

import java.util.UUID;

/**
 * A single CRDT mutation event for one field of one item.
 * Timestamp is always server-assigned by the REST adapter at point of receipt.
 */
public record ItemChange(
    UUID itemId,
    UUID listId,
    ItemField field,
    String serializedValue,
    long timestamp,
    UUID modifiedBy
) {}
