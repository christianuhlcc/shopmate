package com.shopmate.application.service;

import com.shopmate.domain.crdt.FractionalIndex;
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
import com.shopmate.domain.model.UserNotFoundException;
import com.shopmate.domain.port.in.ShoppingListUseCase;
import com.shopmate.domain.port.out.EventPublisher;
import com.shopmate.domain.port.out.SectionCorrectionRepository;
import com.shopmate.domain.port.out.ShoppingListRepository;
import com.shopmate.domain.port.out.UserRepository;
import com.shopmate.domain.section.Section;
import com.shopmate.domain.section.SectionClassifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ShoppingListService implements ShoppingListUseCase {

    static final int MAX_ITEMS = 100;
    static final int MAX_NAME_LENGTH = 100;

    private final ShoppingListRepository listRepository;
    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;
    private final SectionClassifier sectionClassifier;
    private final SectionCorrectionRepository sectionCorrectionRepository;

    public ShoppingListService(ShoppingListRepository listRepository,
                                UserRepository userRepository,
                                EventPublisher eventPublisher,
                                SectionClassifier sectionClassifier,
                                SectionCorrectionRepository sectionCorrectionRepository) {
        this.listRepository = listRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.sectionClassifier = sectionClassifier;
        this.sectionCorrectionRepository = sectionCorrectionRepository;
    }

    @Override
    public List<ShoppingList> getListsForUser(UUID userId) {
        User user = requireUserWithGroup(userId);
        return listRepository.findAllByGroupId(user.groupId());
    }

    @Override
    public ShoppingList createList(UUID ownerId, String name) {
        User owner = requireUserWithGroup(ownerId);
        ShoppingList list = new ShoppingList(
            UUID.randomUUID(),
            name,
            ownerId,
            owner.groupId(),
            Map.of(),
            Instant.now()
        );
        return listRepository.save(list);
    }

    @Override
    public ShoppingList getList(UUID listId, UUID requestingUserId) {
        User user = requireUserWithGroup(requestingUserId);
        ShoppingList list = findListOrThrow(listId);
        requireSameGroup(list, user);
        return list;
    }

    @Override
    public ShoppingList addItem(UUID listId, String name, String quantity, UUID requestingUserId) {
        User user = requireUserWithGroup(requestingUserId);
        if (name == null || name.isBlank()) {
            throw new InvalidItemException("Item name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new InvalidItemException("Item name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        ShoppingList list = findListOrThrow(listId);
        requireSameGroup(list, user);

        if (list.activeItems().size() >= MAX_ITEMS) {
            throw new ListCapacityExceededException();
        }

        long ts = System.currentTimeMillis();
        UUID itemId = UUID.randomUUID();

        // Compute sort key: append after the last active item
        String lastSortKey = list.activeItems().isEmpty() ? null
            : list.activeItems().getLast().sortKey().value();
        String sortKey = FractionalIndex.append(lastSortKey);

        Section section = sectionCorrectionRepository.find(listId, SectionClassifier.normalize(name))
            .orElseGet(() -> sectionClassifier.classify(name));

        LwwField<String> nameFld = new LwwField<>(name, ts, requestingUserId);
        LwwField<String> qtyFld = new LwwField<>(quantity != null ? quantity : "1", ts, requestingUserId);
        LwwField<Boolean> checkedFld = new LwwField<>(false, ts, requestingUserId);
        LwwField<Boolean> deletedFld = new LwwField<>(false, ts, requestingUserId);
        LwwField<String> sortKeyFld = new LwwField<>(sortKey, ts, requestingUserId);
        LwwField<String> sectionFld = new LwwField<>(section.name(), ts, requestingUserId);

        ShoppingItem newItem = new ShoppingItem(itemId, listId,
            nameFld, qtyFld, checkedFld, deletedFld, sortKeyFld, sectionFld, Map.of());

        Map<UUID, ShoppingItem> newItems = new HashMap<>(list.items());
        newItems.put(itemId, newItem);
        ShoppingList updated = new ShoppingList(list.id(), list.name(), list.ownerId(),
            list.groupId(), Map.copyOf(newItems), list.createdAt());

        ShoppingList saved = listRepository.save(updated);

        // Broadcast each field as a change event so subscribers materialize the complete item
        eventPublisher.publishItemChange(listId,
            new ItemChange(itemId, listId, ItemField.NAME, nameFld.value(), ts, requestingUserId));
        eventPublisher.publishItemChange(listId,
            new ItemChange(itemId, listId, ItemField.QUANTITY, qtyFld.value(), ts, requestingUserId));
        eventPublisher.publishItemChange(listId,
            new ItemChange(itemId, listId, ItemField.CHECKED, String.valueOf(checkedFld.value()), ts, requestingUserId));
        eventPublisher.publishItemChange(listId,
            new ItemChange(itemId, listId, ItemField.DELETED, String.valueOf(deletedFld.value()), ts, requestingUserId));
        eventPublisher.publishItemChange(listId,
            new ItemChange(itemId, listId, ItemField.SORT_KEY, sortKeyFld.value(), ts, requestingUserId));
        eventPublisher.publishItemChange(listId,
            new ItemChange(itemId, listId, ItemField.SECTION, sectionFld.value(), ts, requestingUserId));

        return saved;
    }

    @Override
    public ShoppingList applyItemChange(UUID listId, ItemChange change, UUID requestingUserId) {
        User user = requireUserWithGroup(requestingUserId);
        ShoppingList list = findListOrThrow(listId);
        requireSameGroup(list, user);

        if (change.field() == ItemField.NAME) {
            String val = change.serializedValue();
            if (val == null || val.isBlank()) throw new InvalidItemException("Name must not be blank");
            if (val.length() > MAX_NAME_LENGTH) throw new InvalidItemException("Name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        if (change.field() == ItemField.SECTION) {
            String val = change.serializedValue();
            if (val == null || !Section.fromCode(val).name().equals(val)) {
                throw new InvalidItemException("Unknown section code: " + val);
            }
        }

        ShoppingList updated = list.applyChange(change);
        ShoppingList saved = listRepository.save(updated);
        eventPublisher.publishItemChange(listId, change);

        if (change.field() == ItemField.SECTION) {
            // Explicit reassignment: remember it per-list, keyed by the item's current name.
            // Auto-classification (addItem) never reaches this path, so it never writes a correction.
            String normalizedName = SectionClassifier.normalize(saved.items().get(change.itemId()).name().value());
            sectionCorrectionRepository.upsert(listId, normalizedName, Section.fromCode(change.serializedValue()),
                change.timestamp(), change.modifiedBy());
        }

        return saved;
    }

    @Override
    public ShoppingList deleteItem(UUID listId, UUID itemId, UUID requestingUserId) {
        User user = requireUserWithGroup(requestingUserId);
        ShoppingList list = findListOrThrow(listId);
        requireSameGroup(list, user);

        long ts = System.currentTimeMillis();
        ItemChange tombstone = new ItemChange(itemId, listId, ItemField.DELETED, "true", ts, requestingUserId);
        ShoppingList updated = list.applyChange(tombstone);
        ShoppingList saved = listRepository.save(updated);
        eventPublisher.publishItemChange(listId, tombstone);
        return saved;
    }

    @Override
    public void assertListAccess(UUID listId, UUID userId) {
        User user = requireUserWithGroup(userId);
        ShoppingList list = findListOrThrow(listId);
        requireSameGroup(list, user);
    }

    private ShoppingList findListOrThrow(UUID listId) {
        return listRepository.findById(listId)
            .orElseThrow(() -> new ListNotFoundException(listId));
    }

    private User requireUserWithGroup(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException("No user with id: " + userId));
        if (user.groupId() == null) {
            throw new NoGroupException(userId);
        }
        return user;
    }

    private void requireSameGroup(ShoppingList list, User user) {
        if (!list.groupId().equals(user.groupId())) {
            throw new AccessForbiddenException("User's group does not have access to this list");
        }
    }

    private void requireOwner(ShoppingList list, UUID userId) {
        if (!list.isOwner(userId)) {
            throw new AccessForbiddenException("Only the list owner can perform this action");
        }
    }
}
