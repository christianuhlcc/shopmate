package com.shopmate.adapter.out.persistence.repository;

import com.shopmate.adapter.out.persistence.entity.ShoppingListEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataShoppingListRepository extends JpaRepository<ShoppingListEntity, UUID> {

    List<ShoppingListEntity> findAllByGroupId(UUID groupId);
}
