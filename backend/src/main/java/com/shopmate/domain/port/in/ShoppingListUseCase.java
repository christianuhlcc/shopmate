package com.shopmate.domain.port.in;

import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.model.ShoppingList;

import java.util.List;
import java.util.UUID;

public interface ShoppingListUseCase {

    List<ShoppingList> getListsForUser(UUID userId);

    ShoppingList createList(UUID ownerId, String name);

    ShoppingList getList(UUID listId, UUID requestingUserId);

    ShoppingList addItem(UUID listId, String name, String quantity, UUID requestingUserId);

    ShoppingList applyItemChange(UUID listId, ItemChange change, UUID requestingUserId);

    ShoppingList deleteItem(UUID listId, UUID itemId, UUID requestingUserId);

    ShoppingList addMember(UUID listId, String memberEmail, UUID requestingUserId);

    void removeMember(UUID listId, UUID memberId, UUID requestingUserId);
}
