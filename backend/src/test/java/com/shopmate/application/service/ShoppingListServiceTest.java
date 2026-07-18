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
import com.shopmate.domain.model.UserNotFoundException;
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
    void addItemFailsNullName() {
        assertThatThrownBy(() -> service.addItem(LIST_ID, null, "1", OWNER_ID))
            .isInstanceOf(InvalidItemException.class);
    }

    @Test
    void applyItemChangeAcceptsValidName() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var change = new ItemChange(UUID.randomUUID(), LIST_ID, ItemField.NAME, "Oats", 100L, MEMBER_ID);

        ShoppingList saved = service.applyItemChange(LIST_ID, change, MEMBER_ID);

        assertThat(saved.items().get(change.itemId()).name().value()).isEqualTo("Oats");
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
    void getListsForUserDelegatesToRepository() {
        List<ShoppingList> lists = List.of(emptyList());
        when(listRepository.findAllByMemberId(MEMBER_ID)).thenReturn(lists);
        assertThat(service.getListsForUser(MEMBER_ID)).isEqualTo(lists);
    }

    @Test
    void createListSavesListWithOwnerAsSoleMember() {
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList created = service.createList(OWNER_ID, "Weekend");

        assertThat(created.name()).isEqualTo("Weekend");
        assertThat(created.ownerId()).isEqualTo(OWNER_ID);
        assertThat(created.memberIds()).containsExactly(OWNER_ID);
        assertThat(created.items()).isEmpty();
    }

    @Test
    void getListReturnsListForMember() {
        ShoppingList list = emptyList();
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(list));
        assertThat(service.getList(LIST_ID, MEMBER_ID)).isEqualTo(list);
    }

    @Test
    void addItemAppendsAfterLastActiveItem() {
        ShoppingList list = listWithNItems(2);
        String lastKey = list.activeItems().getLast().sortKey().value();
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(list));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList saved = service.addItem(LIST_ID, "Butter", "1", MEMBER_ID);

        ShoppingItem added = saved.items().values().stream()
            .filter(i -> i.name().value().equals("Butter"))
            .findFirst().orElseThrow();
        assertThat(added.sortKey().value().compareTo(lastKey)).isGreaterThan(0);
    }

    @Test
    void applyItemChangeSavesAndPublishes() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var change = new ItemChange(UUID.randomUUID(), LIST_ID, ItemField.QUANTITY, "3", 100L, MEMBER_ID);

        ShoppingList saved = service.applyItemChange(LIST_ID, change, MEMBER_ID);

        assertThat(saved.items()).containsKey(change.itemId());
        verify(listRepository).save(any());
        verify(eventPublisher).publishItemChange(LIST_ID, change);
    }

    @Test
    void applyItemChangeRejectsBlankName() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        var change = new ItemChange(UUID.randomUUID(), LIST_ID, ItemField.NAME, "  ", 100L, MEMBER_ID);
        assertThatThrownBy(() -> service.applyItemChange(LIST_ID, change, MEMBER_ID))
            .isInstanceOf(InvalidItemException.class);
        verify(listRepository, never()).save(any());
    }

    @Test
    void deleteItemAppliesTombstoneAndPublishes() {
        ShoppingList list = listWithNItems(1);
        UUID itemId = list.items().keySet().iterator().next();
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(list));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList saved = service.deleteItem(LIST_ID, itemId, MEMBER_ID);

        assertThat(saved.items().get(itemId).deleted().value()).isTrue();
        ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
        verify(eventPublisher).publishItemChange(eq(LIST_ID), captor.capture());
        assertThat(captor.getValue().field()).isEqualTo(ItemField.DELETED);
        assertThat(captor.getValue().serializedValue()).isEqualTo("true");
    }

    @Test
    void addMemberAddsUserFoundByEmail() {
        UUID newMemberId = UUID.randomUUID();
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(userRepository.findByEmail("friend@example.com"))
            .thenReturn(Optional.of(new User(newMemberId, "friend@example.com", "Friend", null)));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList updated = service.addMember(LIST_ID, "friend@example.com", OWNER_ID);

        assertThat(updated.memberIds()).contains(OWNER_ID, MEMBER_ID, newMemberId);
    }

    @Test
    void addMemberThrowsWhenEmailUnknown() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addMember(LIST_ID, "ghost@example.com", OWNER_ID))
            .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void removeMemberIsIdempotentForNonMember() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        service.removeMember(LIST_ID, STRANGER_ID, OWNER_ID);
        verify(listRepository, never()).save(any());
    }

    @Test
    void ownerCanRemoveOtherMember() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.removeMember(LIST_ID, MEMBER_ID, OWNER_ID);

        ArgumentCaptor<ShoppingList> captor = ArgumentCaptor.forClass(ShoppingList.class);
        verify(listRepository).save(captor.capture());
        assertThat(captor.getValue().memberIds()).doesNotContain(MEMBER_ID);
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
