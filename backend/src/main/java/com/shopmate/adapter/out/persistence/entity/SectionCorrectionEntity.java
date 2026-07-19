package com.shopmate.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A single learned per-list section correction (ADR-0012): the section a
 * user explicitly assigned to items normalizing to {@code normalizedName}
 * within {@code listId}, LWW by {@code ts}.
 */
@Entity
@Table(name = "section_corrections")
@IdClass(SectionCorrectionId.class)
public class SectionCorrectionEntity {

    @Id
    @Column(name = "list_id")
    private UUID listId;

    @Id
    @Column(name = "normalized_name")
    private String normalizedName;

    @Column(nullable = false)
    private String section;

    @Column(nullable = false)
    private long ts;

    @Column(name = "modified_by", nullable = false)
    private UUID modifiedBy;

    protected SectionCorrectionEntity() {}

    public SectionCorrectionEntity(UUID listId, String normalizedName, String section, long ts, UUID modifiedBy) {
        this.listId = listId;
        this.normalizedName = normalizedName;
        this.section = section;
        this.ts = ts;
        this.modifiedBy = modifiedBy;
    }

    public UUID getListId() { return listId; }
    public String getNormalizedName() { return normalizedName; }
    public String getSection() { return section; }
    public long getTs() { return ts; }
    public UUID getModifiedBy() { return modifiedBy; }

    public void setSection(String section) { this.section = section; }
    public void setTs(long ts) { this.ts = ts; }
    public void setModifiedBy(UUID modifiedBy) { this.modifiedBy = modifiedBy; }
}
