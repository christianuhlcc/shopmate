package com.shopmate.domain.port.out;

import com.shopmate.domain.model.ItemChange;

import java.util.UUID;

public interface EventPublisher {
    void publishItemChange(UUID listId, ItemChange change);
}
