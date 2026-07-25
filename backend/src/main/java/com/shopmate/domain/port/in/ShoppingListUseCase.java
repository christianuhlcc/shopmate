package com.shopmate.domain.port.in;

import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.model.ShoppingList;

import java.util.List;
import java.util.UUID;

public interface ShoppingListUseCase {

    List<ShoppingList> getListsForUser(UUID userId);

    ShoppingList createList(UUID ownerId, String name);

    /**
     * Copies the active (non-deleted) items of {@code sourceListId} into a new list named
     * {@code newName}, owned by the caller's group. Copied items get fresh ids and timestamps,
     * preserve name/quantity/section/order, and are reset to unchecked (a fresh shopping trip).
     */
    ShoppingList copyList(UUID sourceListId, String newName, UUID requestingUserId);

    ShoppingList getList(UUID listId, UUID requestingUserId);

    ShoppingList addItem(UUID listId, String name, String quantity, UUID requestingUserId);

    ShoppingList applyItemChange(UUID listId, ItemChange change, UUID requestingUserId);

    ShoppingList deleteItem(UUID listId, UUID itemId, UUID requestingUserId);

    /**
     * Verifies that {@code userId} may access {@code listId} (same group), throwing
     * {@link com.shopmate.domain.model.NoGroupException} or
     * {@link com.shopmate.domain.model.AccessForbiddenException} otherwise. Used by SSE
     * token issuance, which needs the access check without needing the list itself.
     */
    void assertListAccess(UUID listId, UUID userId);
}
