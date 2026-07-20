package com.shopmate.adapter.out.persistence;

import com.shopmate.adapter.out.persistence.entity.ShoppingItemEntity;
import com.shopmate.adapter.out.persistence.entity.ShoppingListEntity;
import com.shopmate.adapter.out.persistence.repository.SpringDataShoppingListRepository;
import com.shopmate.domain.model.LwwField;
import com.shopmate.domain.model.ShoppingItem;
import com.shopmate.domain.model.ShoppingList;
import com.shopmate.domain.port.out.ShoppingListRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ShoppingListRepositoryAdapter implements ShoppingListRepository {

    private final SpringDataShoppingListRepository listJpa;

    public ShoppingListRepositoryAdapter(SpringDataShoppingListRepository listJpa) {
        this.listJpa = listJpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShoppingList> findById(UUID listId) {
        return listJpa.findById(listId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShoppingList> findAllByGroupId(UUID groupId) {
        return listJpa.findAllByGroupId(groupId).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public ShoppingList save(ShoppingList list) {
        ShoppingListEntity entity = listJpa.findById(list.id())
            .orElseGet(() -> new ShoppingListEntity(list.id(), list.name(), list.ownerId(), list.groupId(), list.createdAt()));

        entity.setName(list.name());

        // Sync items — merge-at-save using domain LWW logic
        Map<UUID, ShoppingItemEntity> existingById = entity.getItems().stream()
            .collect(Collectors.toMap(ShoppingItemEntity::getId, i -> i));

        for (ShoppingItem domainItem : list.items().values()) {
            ShoppingItemEntity itemEntity = existingById.get(domainItem.id());
            if (itemEntity == null) {
                itemEntity = toEntity(domainItem, entity);
                entity.getItems().add(itemEntity);
            } else {
                // Apply LWW merge: only update field if domain has higher timestamp
                mergeLwwFields(itemEntity, domainItem);
            }
        }

        listJpa.save(entity);
        return toDomain(entity);
    }

    private void mergeLwwFields(ShoppingItemEntity entity, ShoppingItem domain) {
        if (domain.name().timestamp() > entity.getNameTs()) {
            entity.setNameValue(domain.name().value());
            entity.setNameTs(domain.name().timestamp());
            entity.setNameModifiedBy(domain.name().modifiedBy());
        }
        if (domain.quantity().timestamp() > entity.getQuantityTs()) {
            entity.setQuantityValue(domain.quantity().value());
            entity.setQuantityTs(domain.quantity().timestamp());
            entity.setQuantityModifiedBy(domain.quantity().modifiedBy());
        }
        if (domain.checked().timestamp() > entity.getCheckedTs()) {
            entity.setCheckedValue(domain.checked().value());
            entity.setCheckedTs(domain.checked().timestamp());
            entity.setCheckedModifiedBy(domain.checked().modifiedBy());
        }
        if (domain.deleted().timestamp() > entity.getDeletedTs()) {
            entity.setDeletedValue(domain.deleted().value());
            entity.setDeletedTs(domain.deleted().timestamp());
            entity.setDeletedModifiedBy(domain.deleted().modifiedBy());
        }
        if (domain.sortKey().timestamp() > entity.getSortKeyTs()) {
            entity.setSortKeyValue(domain.sortKey().value());
            entity.setSortKeyTs(domain.sortKey().timestamp());
            entity.setSortKeyModifiedBy(domain.sortKey().modifiedBy());
        }
        if (domain.section().timestamp() > entity.getSectionTs()) {
            entity.setSectionValue(domain.section().value());
            entity.setSectionTs(domain.section().timestamp());
            entity.setSectionModifiedBy(domain.section().modifiedBy());
        }
    }

    ShoppingList toDomain(ShoppingListEntity e) {
        Map<UUID, ShoppingItem> items = new HashMap<>();
        for (ShoppingItemEntity item : e.getItems()) {
            ShoppingItem domainItem = toDomain(item);
            items.put(domainItem.id(), domainItem);
        }

        return new ShoppingList(e.getId(), e.getName(), e.getOwnerId(), e.getGroupId(),
            Map.copyOf(items), e.getCreatedAt());
    }

    private ShoppingItem toDomain(ShoppingItemEntity e) {
        return new ShoppingItem(
            e.getId(),
            e.getList().getId(),
            new LwwField<>(e.getNameValue(), e.getNameTs(), e.getNameModifiedBy()),
            new LwwField<>(e.getQuantityValue(), e.getQuantityTs(), e.getQuantityModifiedBy()),
            new LwwField<>(e.isCheckedValue(), e.getCheckedTs(), e.getCheckedModifiedBy()),
            new LwwField<>(e.isDeletedValue(), e.getDeletedTs(), e.getDeletedModifiedBy()),
            new LwwField<>(e.getSortKeyValue(), e.getSortKeyTs(), e.getSortKeyModifiedBy()),
            new LwwField<>(e.getSectionValue(), e.getSectionTs(), e.getSectionModifiedBy()),
            Map.of()
        );
    }

    private ShoppingItemEntity toEntity(ShoppingItem d, ShoppingListEntity list) {
        return new ShoppingItemEntity(
            d.id(), list,
            d.name().value(), d.name().timestamp(), d.name().modifiedBy(),
            d.quantity().value(), d.quantity().timestamp(), d.quantity().modifiedBy(),
            d.checked().value(), d.checked().timestamp(), d.checked().modifiedBy(),
            d.deleted().value(), d.deleted().timestamp(), d.deleted().modifiedBy(),
            d.sortKey().value(), d.sortKey().timestamp(), d.sortKey().modifiedBy(),
            d.section().value(), d.section().timestamp(), d.section().modifiedBy()
        );
    }
}
