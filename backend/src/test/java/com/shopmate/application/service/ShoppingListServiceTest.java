package com.shopmate.application.service;

import com.shopmate.domain.model.AccessForbiddenException;
import com.shopmate.domain.model.InvalidItemException;
import com.shopmate.domain.model.ItemChange;
import com.shopmate.domain.model.ItemField;
import com.shopmate.domain.model.ListCapacityExceededException;
import com.shopmate.domain.model.ListNotFoundException;
import com.shopmate.domain.model.LwwField;
import com.shopmate.domain.model.NoGroupException;
import com.shopmate.domain.model.ShoppingItem;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.model.User;
import com.shopmate.domain.port.out.EventPublisher;
import com.shopmate.domain.port.out.SectionCorrectionRepository;
import com.shopmate.domain.port.out.ShoppingListRepository;
import com.shopmate.domain.port.out.UserRepository;
import com.shopmate.domain.section.Section;
import com.shopmate.domain.section.SectionClassifier;
import com.shopmate.domain.section.SectionDictionary;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingListServiceTest {

    @Mock ShoppingListRepository listRepository;
    @Mock UserRepository userRepository;
    @Mock EventPublisher eventPublisher;
    @Mock SectionCorrectionRepository sectionCorrectionRepository;

    ShoppingListService service;

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID STRANGER_ID = UUID.randomUUID();
    private static final UUID GROUPLESS_ID = UUID.randomUUID();
    private static final UUID LIST_ID = UUID.randomUUID();

    private static final UUID GROUP_ID = UUID.randomUUID();
    private static final UUID OTHER_GROUP_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SectionClassifier sectionClassifier = new SectionClassifier(new SectionDictionary());
        service = new ShoppingListService(listRepository, userRepository, eventPublisher, sectionClassifier,
            sectionCorrectionRepository);

        // Every public operation now resolves the caller's group first, so all four
        // personas must be stubbed. Lenient: individual tests only exercise some of them.
        // OWNER and MEMBER share a group — MEMBER is a non-owner peer, which is the
        // capability that replaces per-list sharing. STRANGER is in a different group.
        lenient().when(userRepository.findById(OWNER_ID))
            .thenReturn(Optional.of(user(OWNER_ID, "owner@example.com", GROUP_ID)));
        lenient().when(userRepository.findById(MEMBER_ID))
            .thenReturn(Optional.of(user(MEMBER_ID, "member@example.com", GROUP_ID)));
        lenient().when(userRepository.findById(STRANGER_ID))
            .thenReturn(Optional.of(user(STRANGER_ID, "stranger@example.com", OTHER_GROUP_ID)));
        lenient().when(userRepository.findById(GROUPLESS_ID))
            .thenReturn(Optional.of(user(GROUPLESS_ID, "nogroup@example.com", null)));
    }

    private static User user(UUID id, String email, UUID groupId) {
        return new User(id, email, "Test User", null, groupId);
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
                new LwwField<>("SONSTIGES", 100L, OWNER_ID),
                Map.of()));
        }
        return new ShoppingList(LIST_ID, "Test List", OWNER_ID,
            GROUP_ID, Map.copyOf(items), Instant.now());
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
    void addItemPublishesAllSixFieldChanges() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList saved = service.addItem(LIST_ID, "Milk", "2", OWNER_ID);
        ShoppingItem item = saved.items().values().iterator().next();

        ArgumentCaptor<ItemChange> captor = ArgumentCaptor.forClass(ItemChange.class);
        verify(eventPublisher, times(6)).publishItemChange(eq(LIST_ID), captor.capture());

        List<ItemChange> changes = captor.getAllValues();
        Map<ItemField, ItemChange> byField = changes.stream()
            .collect(Collectors.toMap(ItemChange::field, Function.identity()));

        assertThat(byField.keySet()).containsExactlyInAnyOrder(
            ItemField.NAME, ItemField.QUANTITY, ItemField.CHECKED, ItemField.DELETED, ItemField.SORT_KEY, ItemField.SECTION);
        assertThat(byField.get(ItemField.NAME).serializedValue()).isEqualTo("Milk");
        assertThat(byField.get(ItemField.QUANTITY).serializedValue()).isEqualTo("2");
        assertThat(byField.get(ItemField.CHECKED).serializedValue()).isEqualTo("false");
        assertThat(byField.get(ItemField.DELETED).serializedValue()).isEqualTo("false");
        assertThat(byField.get(ItemField.SORT_KEY).serializedValue()).isEqualTo(item.sortKey().value());
        assertThat(byField.get(ItemField.SECTION).serializedValue()).isEqualTo(item.section().value());

        // All six changes share the item's server-assigned timestamp, id, and author
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
        verify(eventPublisher, times(6)).publishItemChange(eq(LIST_ID), captor.capture());
        ItemChange qty = captor.getAllValues().stream()
            .filter(c -> c.field() == ItemField.QUANTITY)
            .findFirst().orElseThrow();
        assertThat(qty.serializedValue()).isEqualTo("1");
    }

    @Test
    void addItemClassifiesKirschtomatenAsObstGemuese() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList saved = service.addItem(LIST_ID, "Kirschtomaten", "1", OWNER_ID);

        ShoppingItem item = saved.items().values().iterator().next();
        assertThat(item.section().value()).isEqualTo("OBST_GEMUESE");
    }

    @Test
    void applyItemChangeRejectsUnknownSectionCode() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        var change = new ItemChange(UUID.randomUUID(), LIST_ID, ItemField.SECTION, "NOT_A_SECTION", 100L, OWNER_ID);
        assertThatThrownBy(() -> service.applyItemChange(LIST_ID, change, OWNER_ID))
            .isInstanceOf(InvalidItemException.class);
        verify(listRepository, never()).save(any());
    }

    @Test
    void applyItemChangeAcceptsValidSectionCode() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var change = new ItemChange(UUID.randomUUID(), LIST_ID, ItemField.SECTION, "GETRAENKE", 100L, OWNER_ID);

        ShoppingList saved = service.applyItemChange(LIST_ID, change, OWNER_ID);

        assertThat(saved.items().get(change.itemId()).section().value()).isEqualTo("GETRAENKE");
    }

    @Test
    void getListsForUserReturnsAllListsInCallersGroup() {
        List<ShoppingList> lists = List.of(emptyList());
        when(listRepository.findAllByGroupId(GROUP_ID)).thenReturn(lists);
        assertThat(service.getListsForUser(MEMBER_ID)).isEqualTo(lists);
    }

    // --- Group scoping (ADR-0013) -------------------------------------------------

    @Test
    void everyOperationRejectsGroupLessCaller() {
        // A signed-in user who has not redeemed an invite can do NOTHING with lists.
        // This is the NO_GROUP contract the frontend uses to route to onboarding, so
        // it must hold on every entry point, not just the read paths.
        var change = new ItemChange(UUID.randomUUID(), LIST_ID, ItemField.NAME, "Milk", 100L, GROUPLESS_ID);

        assertThatThrownBy(() -> service.getListsForUser(GROUPLESS_ID))
            .isInstanceOf(NoGroupException.class);
        assertThatThrownBy(() -> service.createList(GROUPLESS_ID, "Nope"))
            .isInstanceOf(NoGroupException.class);
        assertThatThrownBy(() -> service.getList(LIST_ID, GROUPLESS_ID))
            .isInstanceOf(NoGroupException.class);
        assertThatThrownBy(() -> service.addItem(LIST_ID, "Milk", "1", GROUPLESS_ID))
            .isInstanceOf(NoGroupException.class);
        assertThatThrownBy(() -> service.applyItemChange(LIST_ID, change, GROUPLESS_ID))
            .isInstanceOf(NoGroupException.class);
        assertThatThrownBy(() -> service.deleteItem(LIST_ID, UUID.randomUUID(), GROUPLESS_ID))
            .isInstanceOf(NoGroupException.class);
        assertThatThrownBy(() -> service.assertListAccess(LIST_ID, GROUPLESS_ID))
            .isInstanceOf(NoGroupException.class);

        verify(listRepository, never()).save(any());
    }

    @Test
    void sameGroupNonOwnerCanMutateList() {
        // The core capability that replaces per-list sharing: MEMBER does not own the
        // list and was never explicitly added to it, but shares OWNER's group.
        ShoppingList list = emptyList();
        assertThat(list.isOwner(MEMBER_ID)).isFalse();
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(list));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList saved = service.addItem(LIST_ID, "Butter", "1", MEMBER_ID);

        assertThat(saved.items()).hasSize(1);
        verify(listRepository).save(any());
    }

    @Test
    void assertListAccessPassesForSameGroup() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        service.assertListAccess(LIST_ID, MEMBER_ID);
    }

    @Test
    void assertListAccessRejectsOtherGroup() {
        // Guards SSE token issuance: without this a stranger could mint a token for
        // another household's list and stream it live.
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        assertThatThrownBy(() -> service.assertListAccess(LIST_ID, STRANGER_ID))
            .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void createListStampsCallersGroup() {
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList created = service.createList(OWNER_ID, "Weekend");

        assertThat(created.name()).isEqualTo("Weekend");
        assertThat(created.ownerId()).isEqualTo(OWNER_ID);
        assertThat(created.groupId()).isEqualTo(GROUP_ID);
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

    @Test
    void addItemUsesLearnedCorrectionOverClassifier() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sectionCorrectionRepository.find(LIST_ID, "hefe")).thenReturn(Optional.of(Section.BROT_BACKWAREN));

        ShoppingList saved = service.addItem(LIST_ID, "Hefe", "1", OWNER_ID);

        ShoppingItem item = saved.items().values().iterator().next();
        assertThat(item.section().value()).isEqualTo("BROT_BACKWAREN");
    }

    @Test
    void addItemFallsBackToClassifierWhenNoLearnedCorrection() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sectionCorrectionRepository.find(eq(LIST_ID), any())).thenReturn(Optional.empty());

        ShoppingList saved = service.addItem(LIST_ID, "Kirschtomaten", "1", OWNER_ID);

        assertThat(saved.items().values().iterator().next().section().value()).isEqualTo("OBST_GEMUESE");
    }

    @Test
    void addItemNeverWritesACorrection() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.addItem(LIST_ID, "Milch", "1", OWNER_ID);

        verify(sectionCorrectionRepository, never()).upsert(any(), any(), any(), anyLong(), any());
    }

    @Test
    void applyItemChangeUpsertsCorrectionKeyedByCurrentNameOnExplicitSectionChange() {
        ShoppingList list = listWithNItems(1); // seeded item named "Item 0"
        UUID itemId = list.items().keySet().iterator().next();
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(list));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var change = new ItemChange(itemId, LIST_ID, ItemField.SECTION, "BROT_BACKWAREN", 500L, OWNER_ID);
        service.applyItemChange(LIST_ID, change, OWNER_ID);

        verify(sectionCorrectionRepository).upsert(LIST_ID, "item 0", Section.BROT_BACKWAREN, 500L, OWNER_ID);
    }

    @Test
    void applyItemChangeDoesNotUpsertCorrectionForNonSectionField() {
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var change = new ItemChange(UUID.randomUUID(), LIST_ID, ItemField.QUANTITY, "3", 100L, MEMBER_ID);

        service.applyItemChange(LIST_ID, change, MEMBER_ID);

        verify(sectionCorrectionRepository, never()).upsert(any(), any(), any(), anyLong(), any());
    }

    @Test
    void explicitSectionCorrectionAppliesOnNextAddItemWithSameName() {
        // First add: no learned correction yet, so the item lands wherever the dictionary/fallback puts it.
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList afterAdd = service.addItem(LIST_ID, "Hefe", "1", OWNER_ID);
        ShoppingItem added = afterAdd.items().values().iterator().next();

        // User explicitly corrects the section.
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(afterAdd));
        var correction = new ItemChange(added.id(), LIST_ID, ItemField.SECTION, "BROT_BACKWAREN", 999L, OWNER_ID);
        service.applyItemChange(LIST_ID, correction, OWNER_ID);

        verify(sectionCorrectionRepository).upsert(LIST_ID, "hefe", Section.BROT_BACKWAREN, 999L, OWNER_ID);

        // Simulate the persisted correction being found on lookup, and the item being deleted + re-added.
        when(sectionCorrectionRepository.find(LIST_ID, "hefe")).thenReturn(Optional.of(Section.BROT_BACKWAREN));
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));

        ShoppingList afterReadd = service.addItem(LIST_ID, "Hefe", "1", OWNER_ID);
        ShoppingItem readded = afterReadd.items().values().iterator().next();

        assertThat(readded.section().value()).isEqualTo("BROT_BACKWAREN");
    }

    @Test
    void sectionCorrectionUpsertObeysLwwOrderingEndToEnd() {
        // A hand-rolled LWW-correct fake standing in for the persistence adapter, proving the
        // service composes correctly with correction storage that enforces the real ordering rule.
        SectionCorrectionRepository fakeCorrections = new InMemorySectionCorrectionRepository();
        ShoppingListService svc = new ShoppingListService(listRepository, userRepository, eventPublisher,
            new SectionClassifier(new SectionDictionary()), fakeCorrections);

        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(emptyList()));
        when(listRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShoppingList afterAdd = svc.addItem(LIST_ID, "Hefe", "1", OWNER_ID);
        UUID itemId = afterAdd.items().values().iterator().next().id();
        when(listRepository.findById(LIST_ID)).thenReturn(Optional.of(afterAdd));

        // Newer correction lands first, then a stale (older-timestamp) correction must not overwrite it.
        svc.applyItemChange(LIST_ID,
            new ItemChange(itemId, LIST_ID, ItemField.SECTION, "GETRAENKE", 500L, OWNER_ID), OWNER_ID);
        svc.applyItemChange(LIST_ID,
            new ItemChange(itemId, LIST_ID, ItemField.SECTION, "HAUSHALT", 100L, OWNER_ID), OWNER_ID);

        assertThat(fakeCorrections.find(LIST_ID, "hefe")).contains(Section.GETRAENKE);
    }

    /** Minimal in-memory LWW double for {@link SectionCorrectionRepository}, used only to prove
     *  the service composes correctly with LWW-ordered correction storage (mirrors the real
     *  persistence adapter's overwrite rule; the adapter itself is covered by
     *  SectionCorrectionRepositoryAdapterIT against real Postgres). */
    private static class InMemorySectionCorrectionRepository implements SectionCorrectionRepository {
        private final Map<String, Section> sections = new HashMap<>();
        private final Map<String, Long> timestamps = new HashMap<>();

        @Override
        public Optional<Section> find(UUID listId, String normalizedName) {
            return Optional.ofNullable(sections.get(normalizedName));
        }

        @Override
        public void upsert(UUID listId, String normalizedName, Section section, long timestamp, UUID modifiedBy) {
            if (timestamp > timestamps.getOrDefault(normalizedName, Long.MIN_VALUE)) {
                sections.put(normalizedName, section);
                timestamps.put(normalizedName, timestamp);
            }
        }
    }
}
