package com.shopmate.adapter.out.persistence.repository;

import com.shopmate.adapter.out.persistence.entity.PicnicCredentialsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataPicnicCredentialsRepository extends JpaRepository<PicnicCredentialsEntity, UUID> {
}
