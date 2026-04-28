package com.shopmate.adapter.out.persistence.repository;

import com.shopmate.adapter.out.persistence.entity.ShoppingItemEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataShoppingItemRepository extends JpaRepository<ShoppingItemEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ShoppingItemEntity i WHERE i.id = :id")
    Optional<ShoppingItemEntity> findByIdForUpdate(@Param("id") UUID id);
}
