package com.shopmate.adapter.out.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Hibernate relies on the composite key's equals/hashCode for identity in the
 * persistence context, so the contract is pinned here rather than left to
 * whatever the ORM happens to exercise in the integration tests.
 */
class SectionCorrectionIdTest {

    private static final UUID LIST = UUID.randomUUID();

    @Test
    void equalWhenBothComponentsMatch() {
        SectionCorrectionId a = new SectionCorrectionId(LIST, "milch");
        SectionCorrectionId b = new SectionCorrectionId(LIST, "milch");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isEqualTo(a);
    }

    @Test
    void notEqualWhenListDiffers() {
        assertThat(new SectionCorrectionId(LIST, "milch"))
            .isNotEqualTo(new SectionCorrectionId(UUID.randomUUID(), "milch"));
    }

    @Test
    void notEqualWhenNameDiffers() {
        assertThat(new SectionCorrectionId(LIST, "milch"))
            .isNotEqualTo(new SectionCorrectionId(LIST, "butter"));
    }

    @Test
    void notEqualToOtherTypesOrNull() {
        SectionCorrectionId id = new SectionCorrectionId(LIST, "milch");

        assertThat(id).isNotEqualTo("milch");
        assertThat(id).isNotEqualTo(null);
    }

    @Test
    void exposesComponents() {
        SectionCorrectionId id = new SectionCorrectionId(LIST, "milch");

        assertThat(id.getListId()).isEqualTo(LIST);
        assertThat(id.getNormalizedName()).isEqualTo("milch");
        assertThat(new SectionCorrectionId().getListId()).isNull();
    }
}
