package com.shopmate.application.service;

import com.shopmate.domain.crdt.FractionalIndex;
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
import com.shopmate.domain.port.in.ShoppingListUseCase;
import com.shopmate.domain.port.out.EventPublisher;
import com.shopmate.domain.port.out.ShoppingListRepository;
import com.shopmate.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ShoppingListService implements ShoppingListUseCase {

    static final int MAX_ITEMS = 100;
    static final int MAX_NAME_LENGTH = 100;

    private final ShoppingListRepository listRepository;
    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;

    public ShoppingListService(ShoppingListRepository listRepository,
                                UserRepository userRepository,
                                EventPublisher eventPublisher) {
        this.listRepository = listRepository;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<ShoppingList> getListsForUser(UUID userId) {
        return listRepository.findAllByMemberId(userId);
    }

    @Override
    public ShoppingList createList(UUID ownerId, String name) {
        ShoppingList list = new ShoppingList(
            UUID.randomUUID(),
            name,
            ownerId,
            Set.of(ownerId),
            Map.of(),
            Instant.now()
        );
        return listRepository.save(list);
    }

    @Override
    public ShoppingList getList(UUID listId, UUID requestingUserId) {
        ShoppingList list = findListOrThrow(listId);
        requireMember(list, requestingUserId);
        return list;
    }

    @Override
    public ShoppingList addItem(UUID listId, String name, String quantity, UUID requestingUserId) {
        if (name == null || name.isBlank()) {
            throw new InvalidItemException("Item name must not be blank");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new InvalidItemException("Item name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        ShoppingList list = findListOrThrow(listId);
        requireMember(list, requestingUserId);

        if (list.activeItems().size() >= MAX_ITEMS) {
            throw new ListCapacityExceededException();
        }

        long ts = System.currentTimeMillis();
        UUID itemId = UUID.randomUUID();

        // Compute sort key: append after the last active item
        String lastSortKey = list.activeItems().isEmpty() ? null
            : list.activeItems().getLast().sortKey().value();
        String sortKey = FractionalIndex.append(lastSortKey);

        LwwField<String> nameFld = new LwwField<>(name, ts, requestingUserId);
        LwwField<String> qtyFld = new LwwField<>(quantity != null ? quantity : "1", ts, requestingUserId);
        LwwField<Boolean> checkedFld = new LwwField<>(false, ts, requestingUserId);
        LwwField<Boolean> deletedFld = new LwwField<>(false, ts, requestingUserId);
        LwwField<String> sortKeyFld = new LwwField<>(sortKey, ts, requestingUserId);

        ShoppingItem newItem = new ShoppingItem(itemId, listId,
            nameFld, qtyFld, checkedFld, deletedFld, sortKeyFld, Map.of());

        Map<UUID, ShoppingItem> newItems = new HashMap<>(list.items());
        newItems.put(itemId, newItem);
        ShoppingList updated = new ShoppingList(list.id(), list.name(), list.ownerId(),
            list.memberIds(), Map.copyOf(newItems), list.createdAt());

        ShoppingList saved = listRepository.save(updated);

        // Broadcast each field as a change event
        ItemChange nameChange = new ItemChange(itemId, listId, ItemField.NAME, name, ts, requestingUserId);
        eventPublisher.publishItemChange(listId, nameChange);

        return saved;
    }

    @Override
    public ShoppingList applyItemChange(UUID listId, ItemChange change, UUID requestingUserId) {
        ShoppingList list = findListOrThrow(listId);
        requireMember(list, requestingUserId);

        if (change.field() == ItemField.NAME) {
            String val = change.serializedValue();
            if (val == null || val.isBlank()) throw new InvalidItemException("Name must not be blank");
            if (val.length() > MAX_NAME_LENGTH) throw new InvalidItemException("Name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        ShoppingList updated = list.applyChange(change);
        ShoppingList saved = listRepository.save(updated);
        eventPublisher.publishItemChange(listId, change);
        return saved;
    }

    @Override
    public ShoppingList deleteItem(UUID listId, UUID itemId, UUID requestingUserId) {
        ShoppingList list = findListOrThrow(listId);
        requireMember(list, requestingUserId);

        long ts = System.currentTimeMillis();
        ItemChange tombstone = new ItemChange(itemId, listId, ItemField.DELETED, "true", ts, requestingUserId);
        ShoppingList updated = list.applyChange(tombstone);
        ShoppingList saved = listRepository.save(updated);
        eventPublisher.publishItemChange(listId, tombstone);
        return saved;
    }

    @Override
    public ShoppingList addMember(UUID listId, String memberEmail, UUID requestingUserId) {
        ShoppingList list = findListOrThrow(listId);
        requireOwner(list, requestingUserId);

        User newMember = userRepository.findByEmail(memberEmail)
            .orElseThrow(() -> new UserNotFoundException("No user with email: " + memberEmail));

        Set<UUID> updatedMembers = new HashSet<>(list.memberIds());
        updatedMembers.add(newMember.id());
        ShoppingList updated = new ShoppingList(list.id(), list.name(), list.ownerId(),
            Set.copyOf(updatedMembers), list.items(), list.createdAt());
        return listRepository.save(updated);
    }

    @Override
    public void removeMember(UUID listId, UUID memberId, UUID requestingUserId) {
        ShoppingList list = findListOrThrow(listId);
        // Owner can remove anyone; members can remove themselves only
        if (!list.isOwner(requestingUserId) && !requestingUserId.equals(memberId)) {
            throw new AccessForbiddenException("Only the list owner can remove other members");
        }
        if (!list.isMember(memberId)) {
            return; // idempotent
        }
        Set<UUID> updatedMembers = new HashSet<>(list.memberIds());
        updatedMembers.remove(memberId);
        ShoppingList updated = new ShoppingList(list.id(), list.name(), list.ownerId(),
            Set.copyOf(updatedMembers), list.items(), list.createdAt());
        listRepository.save(updated);
    }

    private ShoppingList findListOrThrow(UUID listId) {
        return listRepository.findById(listId)
            .orElseThrow(() -> new ListNotFoundException(listId));
    }

    private void requireMember(ShoppingList list, UUID userId) {
        if (!list.isMember(userId)) {
            throw new AccessForbiddenException("User is not a member of this list");
        }
    }

    private void requireOwner(ShoppingList list, UUID userId) {
        if (!list.isOwner(userId)) {
            throw new AccessForbiddenException("Only the list owner can perform this action");
        }
    }
}
