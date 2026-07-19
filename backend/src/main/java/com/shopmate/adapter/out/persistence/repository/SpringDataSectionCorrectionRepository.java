package com.shopmate.adapter.out.persistence.repository;

import com.shopmate.adapter.out.persistence.entity.SectionCorrectionEntity;
import com.shopmate.adapter.out.persistence.entity.SectionCorrectionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataSectionCorrectionRepository
    extends JpaRepository<SectionCorrectionEntity, SectionCorrectionId> {
}
