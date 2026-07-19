package com.shopmate.domain.section;

/**
 * Canonical 14-section taxonomy for grouping shopping list items by
 * supermarket section, in default store-walk order (ADR-0012).
 * Display labels are a frontend concern; the domain only knows codes and walk order.
 */
public enum Section {
    OBST_GEMUESE(1),
    BROT_BACKWAREN(2),
    MOLKEREI_EIER(3),
    FLEISCH_FISCH(4),
    TIEFKUEHL(5),
    VORRAT(6),
    GEWUERZE_SOSSEN(7),
    FRUEHSTUECK(8),
    SUESSES_SNACKS(9),
    KAFFEE_TEE(10),
    GETRAENKE(11),
    DROGERIE(12),
    HAUSHALT(13),
    SONSTIGES(14);

    private final int walkOrder;

    Section(int walkOrder) {
        this.walkOrder = walkOrder;
    }

    public int walkOrder() {
        return walkOrder;
    }

    /**
     * Resolves a stored section code, falling back to {@link #SONSTIGES} for
     * unknown or null input. Taxonomy evolution is a code change; codes are
     * stored as plain strings, so old/foreign values must never blow up.
     */
    public static Section fromCode(String code) {
        if (code == null) {
            return SONSTIGES;
        }
        for (Section section : values()) {
            if (section.name().equals(code)) {
                return section;
            }
        }
        return SONSTIGES;
    }
}
