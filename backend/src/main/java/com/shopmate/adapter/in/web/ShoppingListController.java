package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.model.ItemField;
import com.shopmate.domain.model.ShoppingItem;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.port.in.ShoppingListUseCase;
import com.shopmate.generated.api.ItemsApi;
import com.shopmate.generated.api.ListsApi;
import com.shopmate.generated.model.AddItemRequest;
import com.shopmate.generated.model.CreateListRequest;
import com.shopmate.generated.model.ItemChangeRequest;
import com.shopmate.generated.model.LwwFieldBoolean;
import com.shopmate.generated.model.LwwFieldString;
import com.shopmate.generated.model.ShoppingListWithItems;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// The OpenAPI spec declares `servers: /api`, but the generator does not include
// that base path in the interface mappings — it must be added at class level.
@RestController
@RequestMapping("/api")
public class ShoppingListController implements ListsApi, ItemsApi {

    private final ShoppingListUseCase shoppingListUseCase;
    private final SecurityContextHelper securityContextHelper;

    public ShoppingListController(ShoppingListUseCase shoppingListUseCase,
                                   SecurityContextHelper securityContextHelper) {
        this.shoppingListUseCase = shoppingListUseCase;
        this.securityContextHelper = securityContextHelper;
    }

    @Override
    public ResponseEntity<List<com.shopmate.generated.model.ShoppingList>> getLists() {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        List<ShoppingList> lists = shoppingListUseCase.getListsForUser(currentUserId);
        List<com.shopmate.generated.model.ShoppingList> dtos = lists.stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @Override
    public ResponseEntity<com.shopmate.generated.model.ShoppingList> createList(
            @Valid @RequestBody CreateListRequest createListRequest) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        ShoppingList created = shoppingListUseCase.createList(currentUserId, createListRequest.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @Override
    public ResponseEntity<ShoppingListWithItems> getList(@PathVariable UUID listId) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        ShoppingList list = shoppingListUseCase.getList(listId, currentUserId);
        return ResponseEntity.ok(toDtoWithItems(list));
    }

    @Override
    public ResponseEntity<com.shopmate.generated.model.ShoppingItem> addItem(
            @PathVariable UUID listId,
            @Valid @RequestBody AddItemRequest addItemRequest) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        ShoppingList updated = shoppingListUseCase.addItem(
                listId,
                addItemRequest.getName(),
                addItemRequest.getQuantity() != null ? addItemRequest.getQuantity() : "1",
                currentUserId);
        // Find the newly added item: it is the one with the highest createdAt proxy —
        // since addItem returns the full list, find the item whose name matches.
        com.shopmate.generated.model.ShoppingItem addedItem = updated.items().values().stream()
                .filter(i -> i.name().value().equals(addItemRequest.getName()))
                .findFirst()
                .map(this::toItemDto)
                .orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED).body(addedItem);
    }

    @Override
    public ResponseEntity<com.shopmate.generated.model.ShoppingItem> updateItem(
            @PathVariable UUID listId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ItemChangeRequest itemChangeRequest) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        ItemField domainField = mapField(itemChangeRequest.getField());
        ItemChange change = new ItemChange(
                itemId,
                listId,
                domainField,
                itemChangeRequest.getValue(),
                System.currentTimeMillis(),
                currentUserId);
        ShoppingList updated = shoppingListUseCase.applyItemChange(listId, change, currentUserId);
        com.shopmate.generated.model.ShoppingItem itemDto = toItemDto(updated.items().get(itemId));
        return ResponseEntity.ok(itemDto);
    }

    @Override
    public ResponseEntity<Void> deleteItem(@PathVariable UUID listId, @PathVariable UUID itemId) {
        UUID currentUserId = securityContextHelper.getCurrentUserId();
        shoppingListUseCase.deleteItem(listId, itemId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // --- Mapping helpers ---

    private com.shopmate.generated.model.ShoppingList toDto(ShoppingList list) {
        return new com.shopmate.generated.model.ShoppingList(
                list.id(),
                list.name(),
                list.ownerId(),
                list.groupId(),
                OffsetDateTime.ofInstant(list.createdAt(), ZoneOffset.UTC));
    }

    private ShoppingListWithItems toDtoWithItems(ShoppingList list) {
        List<com.shopmate.generated.model.ShoppingItem> items = list.activeItems().stream()
                .map(this::toItemDto)
                .toList();
        return new ShoppingListWithItems(
                list.id(),
                list.name(),
                list.ownerId(),
                list.groupId(),
                OffsetDateTime.ofInstant(list.createdAt(), ZoneOffset.UTC),
                items);
    }

    private com.shopmate.generated.model.ShoppingItem toItemDto(ShoppingItem item) {
        return new com.shopmate.generated.model.ShoppingItem(
                item.id(),
                item.listId(),
                toLwwStringDto(item.name()),
                toLwwStringDto(item.quantity()),
                toLwwBooleanDto(item.checked()),
                toLwwBooleanDto(item.deleted()),
                toLwwStringDto(item.sortKey()),
                toLwwStringDto(item.section()));
    }

    private LwwFieldString toLwwStringDto(com.shopmate.domain.model.LwwField<String> f) {
        return new LwwFieldString(f.value(), f.timestamp(), f.modifiedBy());
    }

    private LwwFieldBoolean toLwwBooleanDto(com.shopmate.domain.model.LwwField<Boolean> f) {
        return new LwwFieldBoolean(f.value(), f.timestamp(), f.modifiedBy());
    }

    private ItemField mapField(com.shopmate.generated.model.ItemField generated) {
        return switch (generated) {
            case NAME -> ItemField.NAME;
            case QUANTITY -> ItemField.QUANTITY;
            case CHECKED -> ItemField.CHECKED;
            case DELETED -> ItemField.DELETED;
            case SORT_KEY -> ItemField.SORT_KEY;
            case SECTION -> ItemField.SECTION;
        };
    }
}
