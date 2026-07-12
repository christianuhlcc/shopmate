package com.shopmate.application.service;

import com.shopmate.domain.model.AccessForbiddenException;
import com.shopmate.domain.model.InvalidItemException;
import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.model.ItemField;
import com.shopmate.domain.model.ListCapacityExceededException;
import com.shopmate.domain.model.ListNotFoundException;
import com.shopmate.domain.model.LwwField;
import com.shopmate.domain.model.ShoppingItem;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.EventPublisher;
import com.shopmate.domain.port.out.ShoppingListRepository;
import com.shopmate.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock ShoppingListRepository listRepository;
    @Mock UserRepository userRepository;
    @Mock EventPublisher eventPublisher;

    ShoppingListService service;

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID STRANGER_ID = UUID.randomUUID();
    private static final UUID LIST_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ShoppingListService(listRepository, userRepository, eventPublisher);
    }

    private ShoppingList listWithNItems(int n) {
        Map<UUID, ShoppingItem> items = new HashMap<>();
        for (int i = 0; i < n; i++) {
            UUID id = UUID.randomUUID();
            char c = (char) ('a' + (i % 26));
            items.put(id, new ShoppingItem(id, LIST_ID,
                new LwwField<>("Item " + i, 100L, OWNER_ID),
                new LwwField<>("1", 100L, OWNER_ID),
                new LwwField<>(false, 100L, OWNER_ID),
                new LwwField<>(false, 100L, OWNER_ID),
                new LwwField<>(c + "0", 100L, OWNER_ID),
                Map.of()));
        }
        return new ShoppingList(LIST_ID, "Test List", OWNER_ID,
            Set.of(OWNER_ID, MEMBER_ID), Map.copyOf(items), Instant.now());
    }

    private ShoppingList emptyList() {
        return listWithNItems(0);
    }

    @Test
    void getListForbiddenForNonMember() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        assertThatThrownBy(() -> service.getList(LIST_ID, STRANGER_ID))
            .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void getListThrowsWhenNotFound() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getList(LIST_ID, OWNER_ID))
            .isInstanceOf(ListNotFoundException.class);
    }

    @Test
    void addItemFailsAtCapacity() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(listWithNItems(100)));
        assertThatThrownBy(() -> service.addItem(LIST_ID, "One more", "1", OWNER_ID))
            .isInstanceOf(ListCapacityExceededException.class);
        verify(listRepository, never()).save(any());
    }

    @Test
    void addItemFailsNameTooLong() {
        String longName = "x".repeat(101);
        assertThatThrownBy(() -> service.addItem(LIST_ID, longName, "1", OWNER_ID))
            .isInstanceOf(InvalidItemException.class);
    }

    @Test
    void addItemFailsBlankName() {
        assertThatThrownBy(() -> service.addItem(LIST_ID, "  ", "1", OWNER_ID))
            .isInstanceOf(InvalidItemException.class);
    }

    @Test
    void addItemForbiddenForNonMember() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        assertThatThrownBy(() -> service.addItem(LIST_ID, "Milk", "1", STRANGER_ID))
            .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void applyItemChangeForbiddenForNonMember() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        var change = new ItemChange(UUID.randomUUID(), LIST_ID, ItemField.NAME, "Milk", 100L, STRANGER_ID);
        assertThatThrownBy(() -> service.applyItemChange(LIST_ID, change, STRANGER_ID))
            .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void applyItemChangeValidatesNameLength() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        String longName = "x".repeat(101);
        var change = new ItemChange(UUID.randomUUID(), LIST_ID, ItemField.NAME, longName, 100L, OWNER_ID);
        assertThatThrownBy(() -> service.applyItemChange(LIST_ID, change, OWNER_ID))
            .isInstanceOf(InvalidItemException.class);
    }

    @Test
    void addMemberRequiresOwner() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        assertThatThrownBy(() -> service.addMember(LIST_ID, "someone@example.com", MEMBER_ID))
            .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void removeMemberAllowsSelfRemoval() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.removeMember(LIST_ID, MEMBER_ID, MEMBER_ID);
        verify(listRepository).save(any());
    }

    @Test
    void removeMemberForbiddenForOtherMember() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        assertThatThrownBy(() -> service.removeMember(LIST_ID, OWNER_ID, MEMBER_ID))
            .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void addItemPublishesAllFiveFieldChanges() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList saved = service.addItem(LIST_ID, "Milk", "2", OWNER_ID);
        ShoppingItem item = saved.items().values().iterator().next();

        ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
        verify(eventPublisher, times(5)).publishItemChange(eq(LIST_ID), captor.capture());

        List<ItemChange> changes = captor.getAllValues();
        Map<ItemField, ItemChange> byField = changes.stream()
            .collect(Collectors.toMap(ItemChange::field, Function.identity()));

        assertThat(byField.keySet()).containsExactlyInAnyOrder(
            ItemField.NAME, ItemField.QUANTITY, ItemField.CHECKED, ItemField.DELETED, ItemField.SORT_KEY);
        assertThat(byField.get(ItemField.NAME).serializedValue()).isEqualTo("Milk");
        assertThat(byField.get(ItemField.QUANTITY).serializedValue()).isEqualTo("2");
        assertThat(byField.get(ItemField.CHECKED).serializedValue()).isEqualTo("false");
        assertThat(byField.get(ItemField.DELETED).serializedValue()).isEqualTo("false");
        assertThat(byField.get(ItemField.SORT_KEY).serializedValue()).isEqualTo(item.sortKey().value());

        // All five changes share the item's server-assigned timestamp, id, and author
        long ts = item.name().timestamp();
        assertThat(changes).allSatisfy(c -> {
            assertThat(c.timestamp()).isEqualTo(ts);
            assertThat(c.itemId()).isEqualTo(item.id());
            assertThat(c.listId()).isEqualTo(LIST_ID);
            assertThat(c.modifiedBy()).isEqualTo(OWNER_ID);
        });
    }

    @Test
    void addItemDefaultsQuantityToOneInBroadcast() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.addItem(LIST_ID, "Eggs", null, OWNER_ID);

        ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
        verify(eventPublisher, times(5)).publishItemChange(eq(LIST_ID), captor.capture());
        ItemChange qty = captor.getAllValues().stream()
            .filter(c -> c.field() == ItemField.QUANTITY)
            .findFirst().orElseThrow();
        assertThat(qty.serializedValue()).isEqualTo("1");
    }

    @Test
    void concurrentMovesOfSameItemConvergeToOnePosition() {
        // Validate Kleppmann §2: concurrent SORT_KEY moves must NOT duplicate the item
        var itemId = UUID.randomUUID();
        var moveA = new ItemChange(itemId, LIST_ID, ItemField.SORT_KEY, "b0", 200L, OWNER_ID);
        var moveB = new ItemChange(itemId, LIST_ID, ItemField.SORT_KEY, "c0", 300L, MEMBER_ID);

        // Apply both changes to the pure domain objects
        ShoppingList listAfterA = emptyList().applyChange(moveA);
        ShoppingList listAfterB = listAfterA.applyChange(moveB);

        // Exactly one item, not duplicated
        assertThat(listAfterB.items()).hasSize(1);
        assertThat(listAfterB.items().get(itemId).sortKey().value()).isEqualTo("c0"); // higher ts wins
    }
}
