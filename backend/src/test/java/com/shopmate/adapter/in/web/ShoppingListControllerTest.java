package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.model.LwwField;
import com.shopmate.domain.model.ShoppingItem;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.port.in.ShoppingListUseCase;
import com.shopmate.generated.model.AddItemRequest;
import com.shopmate.generated.model.CreateListRequest;
import com.shopmate.generated.model.ItemChangeRequest;
import com.shopmate.generated.model.ItemField;
import com.shopmate.generated.model.ShoppingListWithItems;
import com.shopmate.infrastructure.security.SecurityContextHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListControllerTest {

    @Mock ShoppingListUseCase shoppingListUseCase;
    @Mock SecurityContextHelper securityContextHelper;

    ShoppingListController controller;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID LIST_ID = UUID.randomUUID();
    private static final UUID ITEM_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-07-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        controller = new ShoppingListController(shoppingListUseCase, securityContextHelper);
        when(securityContextHelper.getCurrentUserId()).thenReturn(USER_ID);
    }

    private ShoppingItem item(String name) {
        return new ShoppingItem(ITEM_ID, LIST_ID,
            new LwwField<>(name, 100L, USER_ID),
            new LwwField<>("2", 100L, USER_ID),
            new LwwField<>(false, 100L, USER_ID),
            new LwwField<>(false, 100L, USER_ID),
            new LwwField<>("a0", 100L, USER_ID),
            new LwwField<>("SONSTIGES", 100L, USER_ID),
            Map.of());
    }

    private ShoppingList list(Map<UUID, ShoppingItem> items) {
        return new ShoppingList(LIST_ID, "Groceries", USER_ID, GROUP_ID, items, CREATED_AT);
    }

    @Test
    void getListsMapsDomainListsToDtos() {
        when(shoppingListUseCase.getListsForUser(USER_ID)).thenReturn(List.of(list(Map.of())));

        ResponseEntity<List<com.shopmate.generated.model.ShoppingList>> response = controller.getLists();

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        com.shopmate.generated.model.ShoppingList dto = response.getBody().get(0);
        assertThat(dto.getId()).isEqualTo(LIST_ID);
        assertThat(dto.getName()).isEqualTo("Groceries");
        assertThat(dto.getOwnerId()).isEqualTo(USER_ID);
        assertThat(dto.getGroupId()).isEqualTo(GROUP_ID);
    }

    @Test
    void createListReturns201WithDto() {
        when(shoppingListUseCase.createList(USER_ID, "Weekend")).thenReturn(list(Map.of()));

        var response = controller.createList(new CreateListRequest("Weekend"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getId()).isEqualTo(LIST_ID);
        assertThat(response.getBody().getGroupId()).isEqualTo(GROUP_ID);
    }

    @Test
    void getListReturnsListWithItems() {
        ShoppingItem milk = item("Milk");
        when(shoppingListUseCase.getList(LIST_ID, USER_ID)).thenReturn(list(Map.of(ITEM_ID, milk)));

        ResponseEntity<ShoppingListWithItems> response = controller.getList(LIST_ID);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ShoppingListWithItems dto = response.getBody();
        assertThat(dto.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(dto.getItems()).hasSize(1);
        com.shopmate.generated.model.ShoppingItem itemDto = dto.getItems().get(0);
        assertThat(itemDto.getId()).isEqualTo(ITEM_ID);
        assertThat(itemDto.getName().getValue()).isEqualTo("Milk");
        assertThat(itemDto.getName().getTimestamp()).isEqualTo(100L);
        assertThat(itemDto.getName().getModifiedBy()).isEqualTo(USER_ID);
        assertThat(itemDto.getChecked().getValue()).isFalse();
        assertThat(itemDto.getSortKey().getValue()).isEqualTo("a0");
        assertThat(itemDto.getSection().getValue()).isEqualTo("SONSTIGES");
    }

    @Test
    void addItemReturns201WithCreatedItem() {
        ShoppingItem milk = item("Milk");
        UUID otherItemId = UUID.randomUUID();
        ShoppingItem bread = new ShoppingItem(otherItemId, LIST_ID,
            new LwwField<>("Bread", 50L, USER_ID),
            new LwwField<>("1", 50L, USER_ID),
            new LwwField<>(false, 50L, USER_ID),
            new LwwField<>(false, 50L, USER_ID),
            new LwwField<>("b0", 50L, USER_ID),
            new LwwField<>("BROT_BACKWAREN", 50L, USER_ID),
            Map.of());
        when(shoppingListUseCase.addItem(LIST_ID, "Milk", "3", USER_ID))
            .thenReturn(list(Map.of(ITEM_ID, milk, otherItemId, bread)));

        var response = controller.addItem(LIST_ID, new AddItemRequest("Milk").quantity("3"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getId()).isEqualTo(ITEM_ID);
        assertThat(response.getBody().getName().getValue()).isEqualTo("Milk");
    }

    @Test
    void addItemDefaultsQuantityToOne() {
        ShoppingItem milk = item("Milk");
        when(shoppingListUseCase.addItem(LIST_ID, "Milk", "1", USER_ID))
            .thenReturn(list(Map.of(ITEM_ID, milk)));

        var response = controller.addItem(LIST_ID, new AddItemRequest("Milk"));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        verify(shoppingListUseCase).addItem(LIST_ID, "Milk", "1", USER_ID);
    }

    @Test
    void updateItemStampsServerTimestampAndMapsEveryField() {
        for (ItemField field : ItemField.values()) {
            ShoppingItem updated = item("Milk");
            when(shoppingListUseCase.applyItemChange(eq(LIST_ID), any(), eq(USER_ID)))
                .thenReturn(list(Map.of(ITEM_ID, updated)));

            long before = System.currentTimeMillis();
            var response = controller.updateItem(LIST_ID, ITEM_ID,
                new ItemChangeRequest(field, "x", USER_ID));
            long after = System.currentTimeMillis();

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
            verify(shoppingListUseCase).applyItemChange(eq(LIST_ID), captor.capture(), eq(USER_ID));
            ItemChange change = captor.getValue();
            assertThat(change.field().name()).isEqualTo(field.getValue());
            assertThat(change.itemId()).isEqualTo(ITEM_ID);
            // Timestamp is server-assigned at receipt, never client-supplied
            assertThat(change.timestamp()).isBetween(before, after);
            org.mockito.Mockito.reset(shoppingListUseCase);
        }
    }

    @Test
    void deleteItemReturns204() {
        var response = controller.deleteItem(LIST_ID, ITEM_ID);
        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(shoppingListUseCase).deleteItem(LIST_ID, ITEM_ID, USER_ID);
    }
}
