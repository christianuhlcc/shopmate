package com.shopmate.domain.port.out;

import com.shopmate.domain.model.ShoppingList;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShoppingListRepository {
    Optional<ShoppingList> findById(UUID listId);
    List<ShoppingList> findAllByGroupId(UUID groupId);
    ShoppingList save(ShoppingList list);
}
