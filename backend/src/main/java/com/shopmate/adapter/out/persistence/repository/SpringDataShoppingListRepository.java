package com.shopmate.adapter.out.persistence.repository;

import com.shopmate.adapter.out.persistence.entity.ShoppingListEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SpringDataShoppingListRepository extends JpaRepository<ShoppingListEntity, UUID> {

    @Query("SELECT l FROM ShoppingListEntity l JOIN l.members m WHERE m.id = :userId")
    List<ShoppingListEntity> findAllByMemberId(@Param("userId") UUID userId);
}
