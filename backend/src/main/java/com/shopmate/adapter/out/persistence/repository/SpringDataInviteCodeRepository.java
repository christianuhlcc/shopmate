package com.shopmate.adapter.out.persistence.repository;

import com.shopmate.adapter.out.persistence.entity.InviteCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataInviteCodeRepository extends JpaRepository<InviteCodeEntity, UUID> {

    Optional<InviteCodeEntity> findByCode(String code);

    /**
     * Conditional update — only succeeds while {@code used_by} is still NULL, so concurrent
     * redemptions of the same single-use code race safely at the DB level. Returns the number
     * of rows updated (0 or 1); the caller maps that to a boolean.
     */
    @Modifying
    @Query("UPDATE InviteCodeEntity i SET i.usedBy = :usedBy, i.usedAt = :usedAt "
        + "WHERE i.id = :id AND i.usedBy IS NULL")
    int markUsed(@Param("id") UUID id, @Param("usedBy") UUID usedBy, @Param("usedAt") Instant usedAt);
}
