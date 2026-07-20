package com.shopmate.adapter.out.persistence.repository;

import com.shopmate.adapter.out.persistence.entity.GroupEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataGroupRepository extends JpaRepository<GroupEntity, UUID> {
}
