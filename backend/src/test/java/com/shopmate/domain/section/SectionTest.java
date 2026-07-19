package com.shopmate.domain.section;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SectionTest {

    @Test
    void hasExactlyFourteenCanonicalSections() {
        assertThat(Section.values()).hasSize(14);
    }

    @Test
    void walkOrderIsDenseOneToFourteenWithNoDuplicates() {
        List<Integer> orders = Arrays.stream(Section.values())
            .map(Section::walkOrder)
            .sorted()
            .collect(Collectors.toList());
        assertThat(orders).containsExactlyElementsOf(
            java.util.stream.IntStream.rangeClosed(1, 14).boxed().collect(Collectors.toList())
        );
    }

    @Test
    void sonstigesIsAlwaysLastInWalkOrder() {
        int maxOrder = Arrays.stream(Section.values()).mapToInt(Section::walkOrder).max().orElseThrow();
        assertThat(Section.SONSTIGES.walkOrder()).isEqualTo(maxOrder);
    }

    @Test
    void walkOrderMatchesTaxonomyTable() {
        assertThat(Section.OBST_GEMUESE.walkOrder()).isEqualTo(1);
        assertThat(Section.BROT_BACKWAREN.walkOrder()).isEqualTo(2);
        assertThat(Section.MOLKEREI_EIER.walkOrder()).isEqualTo(3);
        assertThat(Section.FLEISCH_FISCH.walkOrder()).isEqualTo(4);
        assertThat(Section.TIEFKUEHL.walkOrder()).isEqualTo(5);
        assertThat(Section.VORRAT.walkOrder()).isEqualTo(6);
        assertThat(Section.GEWUERZE_SOSSEN.walkOrder()).isEqualTo(7);
        assertThat(Section.FRUEHSTUECK.walkOrder()).isEqualTo(8);
        assertThat(Section.SUESSES_SNACKS.walkOrder()).isEqualTo(9);
        assertThat(Section.KAFFEE_TEE.walkOrder()).isEqualTo(10);
        assertThat(Section.GETRAENKE.walkOrder()).isEqualTo(11);
        assertThat(Section.DROGERIE.walkOrder()).isEqualTo(12);
        assertThat(Section.HAUSHALT.walkOrder()).isEqualTo(13);
        assertThat(Section.SONSTIGES.walkOrder()).isEqualTo(14);
    }

    @Test
    void fromCodeResolvesKnownCodes() {
        assertThat(Section.fromCode("OBST_GEMUESE")).isEqualTo(Section.OBST_GEMUESE);
        assertThat(Section.fromCode("HAUSHALT")).isEqualTo(Section.HAUSHALT);
        assertThat(Section.fromCode("SONSTIGES")).isEqualTo(Section.SONSTIGES);
    }

    @Test
    void fromCodeFallsBackToSonstigesForUnknownCode() {
        assertThat(Section.fromCode("NOT_A_REAL_SECTION")).isEqualTo(Section.SONSTIGES);
        assertThat(Section.fromCode("")).isEqualTo(Section.SONSTIGES);
        assertThat(Section.fromCode("obst_gemuese")).isEqualTo(Section.SONSTIGES); // case-sensitive, no fuzzy match
    }

    @Test
    void fromCodeFallsBackToSonstigesForNull() {
        assertThat(Section.fromCode(null)).isEqualTo(Section.SONSTIGES);
    }
}
