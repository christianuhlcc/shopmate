package com.shopmate.adapter.out.persistence;

import com.shopmate.adapter.out.persistence.entity.SectionCorrectionEntity;
import com.shopmate.adapter.out.persistence.entity.SectionCorrectionId;
import com.shopmate.adapter.out.persistence.repository.SpringDataSectionCorrectionRepository;
import com.shopmate.domain.port.out.SectionCorrectionRepository;
import com.shopmate.domain.section.Section;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class SectionCorrectionRepositoryAdapter implements SectionCorrectionRepository {

    private final SpringDataSectionCorrectionRepository jpa;

    public SectionCorrectionRepositoryAdapter(SpringDataSectionCorrectionRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Section> find(UUID listId, String normalizedName) {
        return jpa.findById(new SectionCorrectionId(listId, normalizedName))
            .map(e -> Section.fromCode(e.getSection()));
    }

    @Override
    @Transactional
    public void upsert(UUID listId, String normalizedName, Section section, long timestamp, UUID modifiedBy) {
        SectionCorrectionId id = new SectionCorrectionId(listId, normalizedName);
        Optional<SectionCorrectionEntity> existing = jpa.findById(id);

        if (existing.isEmpty()) {
            jpa.save(new SectionCorrectionEntity(listId, normalizedName, section.name(), timestamp, modifiedBy));
            return;
        }

        // LWW: only overwrite when the incoming timestamp is strictly higher (mirrors mergeLwwFields).
        SectionCorrectionEntity entity = existing.get();
        if (timestamp > entity.getTs()) {
            entity.setSection(section.name());
            entity.setTs(timestamp);
            entity.setModifiedBy(modifiedBy);
            jpa.save(entity);
        }
    }
}
