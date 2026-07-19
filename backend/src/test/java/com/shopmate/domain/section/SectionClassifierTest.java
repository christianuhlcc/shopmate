package com.shopmate.domain.section;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * NOTE ON TEST ORDERING (see docs/plans/section-grouping.md Phase 1): the
 * basket below was picked from realistic household shopping-list items and
 * their genuinely correct section *before* the dictionary was built out to
 * ~1,000+ entries. It is the quality gate for the dictionary, not a mirror
 * of it.
 */
class SectionClassifierTest {

    // ---- normalize() -------------------------------------------------

    @Nested
    class NormalizeTests {

        @Test
        void lowercasesAndTrims() {
            assertThat(SectionClassifier.normalize("  Milch  ")).isEqualTo("milch");
        }

        @Test
        void foldsEszettToDoubleS() {
            assertThat(SectionClassifier.normalize("Straße")).isEqualTo("strasse");
            assertThat(SectionClassifier.normalize("Süßigkeiten")).isEqualTo("süssigkeiten");
        }

        @Test
        void preservesUmlauts() {
            assertThat(SectionClassifier.normalize("Gemüse")).isEqualTo("gemüse");
        }

        @Test
        void collapsesInternalWhitespace() {
            assertThat(SectionClassifier.normalize("Frische   Petersilie")).isEqualTo("frische petersilie");
        }

        @Test
        void stripsLeadingGramQuantity() {
            assertThat(SectionClassifier.normalize("500g Mehl")).isEqualTo("mehl");
        }

        @Test
        void stripsLeadingMultiplierQuantity() {
            assertThat(SectionClassifier.normalize("2x Vollkornbrot")).isEqualTo("vollkornbrot");
        }

        @Test
        void stripsLeadingDecimalLiterQuantity() {
            assertThat(SectionClassifier.normalize("1,5l Milch")).isEqualTo("milch");
        }

        @Test
        void stripsLeadingTwoTokenQuantity() {
            assertThat(SectionClassifier.normalize("2 kg Kartoffeln")).isEqualTo("kartoffeln");
        }

        @Test
        void stripsBarePlainNumberQuantity() {
            assertThat(SectionClassifier.normalize("3 Bananen")).isEqualTo("bananen");
        }

        @Test
        void doesNotStripTrailingQuantity() {
            assertThat(SectionClassifier.normalize("Milch 3,5%")).isEqualTo("milch 3,5%");
        }

        @Test
        void nullNameNormalizesToEmptyString() {
            assertThat(SectionClassifier.normalize(null)).isEmpty();
        }

        @Test
        void blankNameNormalizesToEmptyString() {
            assertThat(SectionClassifier.normalize("   ")).isEmpty();
        }
    }

    // ---- pipeline stages, isolated via a small hand-controlled fixture ----

    @Nested
    class PipelineStageTests {

        private final SectionClassifier classifier =
            new SectionClassifier(new SectionDictionary("/sections/test-fixture-dictionary.tsv"));

        @Test
        void exactDictionaryMatchWins() {
            assertThat(classifier.classify("Milch")).isEqualTo(Section.MOLKEREI_EIER);
        }

        @Test
        void pluralStemRetryMatchesSingularEntry() {
            // fixture has "tomate", not "tomaten"; strip trailing "n" -> "tomate"
            assertThat(classifier.classify("Tomaten")).isEqualTo(Section.OBST_GEMUESE);
        }

        @Test
        void compoundSuffixMatchesHeadNoun() {
            // fixture has "brot", not "vollkornbrot"
            assertThat(classifier.classify("Vollkornbrot")).isEqualTo(Section.BROT_BACKWAREN);
        }

        @Test
        void compoundSuffixPrefersTheLongestMatchingSuffix() {
            // fixture has both "wurst" (5 chars) and "erwurst" (7 chars) mapped
            // to different sections; "leberwurst" must resolve via the longer
            // "erwurst" suffix, proving longest-match-first behavior.
            assertThat(classifier.classify("Leberwurst")).isEqualTo(Section.HAUSHALT);
        }

        @Test
        void compoundSuffixShorterThanMinimumLengthIsNotTried() {
            // fixture has "rot" (3 chars) mapped to SUESSES_SNACKS; the minimum
            // compound suffix length is 4, so it must never be tried and the
            // word must fall through to SONSTIGES.
            assertThat(classifier.classify("Xyzrot")).isEqualTo(Section.SONSTIGES);
        }

        @Test
        void lastWordRetryHandlesTrailingNonAlphabeticToken() {
            assertThat(classifier.classify("Milch 3,5%")).isEqualTo(Section.MOLKEREI_EIER);
        }

        @Test
        void unknownWordFallsBackToSonstiges() {
            assertThat(classifier.classify("Fahrradschlauch")).isEqualTo(Section.SONSTIGES);
        }

        @Test
        void veryShortTokenNeverMatchesPluralStemDueToMinStemLengthGuard() {
            // "en" ends with plural suffixes "n" and "en", but both leave a
            // stem shorter than MIN_STEM_LENGTH, so no dictionary.lookup is
            // even attempted; must fall through (and be too short for the
            // compound-suffix stage too) to SONSTIGES.
            assertThat(classifier.classify("en")).isEqualTo(Section.SONSTIGES);
        }

        @Test
        void lastWordRetryFindsAWordThatStillDoesNotMatch() {
            // Multiword name whose last (letter-containing) word is itself
            // unknown to the dictionary: exercises the "found a candidate
            // last word, but it still doesn't classify" branch.
            assertThat(classifier.classify("Blaues Nichtsvorhanden")).isEqualTo(Section.SONSTIGES);
        }

        @Test
        void multiwordNameWithNoLetterContainingWordFallsBackToSonstiges() {
            // Neither token contains a letter, so lastMeaningfulWord finds no
            // candidate at all (returns null) — a different branch than the
            // "found one, but it didn't match" case above.
            assertThat(classifier.classify("?? 123")).isEqualTo(Section.SONSTIGES);
        }

        @Test
        void blankNameFallsBackToSonstiges() {
            assertThat(classifier.classify("   ")).isEqualTo(Section.SONSTIGES);
        }

        @Test
        void nullNameFallsBackToSonstiges() {
            assertThat(classifier.classify(null)).isEqualTo(Section.SONSTIGES);
        }
    }

    // ---- the basket: ~100+ realistic German household shopping items -----

    @Nested
    class BasketTests {

        private final SectionClassifier classifier = new SectionClassifier(new SectionDictionary());

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("com.shopmate.domain.section.SectionClassifierTest#basket")
        void classifiesBasketItem(String itemName, Section expected) {
            assertThat(classifier.classify(itemName))
                .as("classify(\"%s\")", itemName)
                .isEqualTo(expected);
        }

        /**
         * Documented, accepted limitation (ADR-0012 notes ~85-90% first-pass
         * accuracy, converging via learned corrections in Phase 3): "Glühbirne"
         * (lightbulb) is not groceries, but it coincidentally ends in "birne"
         * (pear) — a real German compound-noun suffix the dictionary must
         * carry for words like "Glühbirne"'s namesake fruit. The suffix
         * matcher has no way to tell these apart from the word alone. Kept
         * out of the basket (whose "unknown -> SONSTIGES" slot must stay
         * genuinely unambiguous) and asserted here instead, so the trade-off
         * is visible rather than silently swept under an easier test item.
         */
        @Test
        void knownLimitation_suffixMatchCanFalsePositiveOnHomonymCompounds() {
            assertThat(classifier.classify("Glühbirne")).isEqualTo(Section.OBST_GEMUESE);
        }
    }

    static Stream<Arguments> basket() {
        return Stream.of(
            // Obst & Gemüse
            Arguments.of("Bananen", Section.OBST_GEMUESE),
            Arguments.of("Äpfel", Section.OBST_GEMUESE),
            Arguments.of("Kirschtomaten", Section.OBST_GEMUESE),
            Arguments.of("2 kg Kartoffeln", Section.OBST_GEMUESE),
            Arguments.of("Zwiebeln", Section.OBST_GEMUESE),
            Arguments.of("Salatgurken", Section.OBST_GEMUESE),
            Arguments.of("Paprika", Section.OBST_GEMUESE),
            Arguments.of("Blumenkohl", Section.OBST_GEMUESE),
            Arguments.of("Zitronen", Section.OBST_GEMUESE),
            Arguments.of("Möhren", Section.OBST_GEMUESE),
            Arguments.of("3 Bananen", Section.OBST_GEMUESE),
            Arguments.of("Frische Petersilie", Section.OBST_GEMUESE),

            // Brot & Backwaren
            Arguments.of("2x Vollkornbrot", Section.BROT_BACKWAREN),
            Arguments.of("Brötchen", Section.BROT_BACKWAREN),
            Arguments.of("Toastbrot", Section.BROT_BACKWAREN),
            Arguments.of("Baguette", Section.BROT_BACKWAREN),
            Arguments.of("Croissants", Section.BROT_BACKWAREN),
            Arguments.of("Semmel", Section.BROT_BACKWAREN),

            // Molkerei & Eier
            Arguments.of("Milch", Section.MOLKEREI_EIER),
            Arguments.of("Milch 3,5%", Section.MOLKEREI_EIER),
            Arguments.of("1,5l Milch", Section.MOLKEREI_EIER),
            Arguments.of("Eier", Section.MOLKEREI_EIER),
            Arguments.of("Bio Eier", Section.MOLKEREI_EIER),
            Arguments.of("Joghurt", Section.MOLKEREI_EIER),
            Arguments.of("Butter", Section.MOLKEREI_EIER),
            Arguments.of("Käse", Section.MOLKEREI_EIER),
            Arguments.of("Schmand", Section.MOLKEREI_EIER),
            Arguments.of("Quark", Section.MOLKEREI_EIER),
            Arguments.of("Sahne", Section.MOLKEREI_EIER),
            Arguments.of("Frischkäse", Section.MOLKEREI_EIER),

            // Fleisch, Wurst & Fisch
            Arguments.of("Hähnchenbrust", Section.FLEISCH_FISCH),
            Arguments.of("Hackfleisch", Section.FLEISCH_FISCH),
            Arguments.of("Lachsfilet", Section.FLEISCH_FISCH),
            Arguments.of("Salami", Section.FLEISCH_FISCH),
            Arguments.of("Leberwurst", Section.FLEISCH_FISCH),
            Arguments.of("Schinken", Section.FLEISCH_FISCH),
            Arguments.of("Rinderhack", Section.FLEISCH_FISCH),
            Arguments.of("Forelle", Section.FLEISCH_FISCH),

            // Tiefkühl
            Arguments.of("TK-Pizza", Section.TIEFKUEHL),
            Arguments.of("Tiefkühlgemüse", Section.TIEFKUEHL),
            Arguments.of("Fischstäbchen", Section.TIEFKUEHL),
            Arguments.of("Pommes", Section.TIEFKUEHL),
            Arguments.of("Eis", Section.TIEFKUEHL),
            Arguments.of("Rahmspinat", Section.TIEFKUEHL),

            // Vorrat (Nudeln, Reis & Konserven)
            Arguments.of("Spaghetti", Section.VORRAT),
            Arguments.of("Reis", Section.VORRAT),
            Arguments.of("Linsen", Section.VORRAT),
            Arguments.of("Kichererbsen", Section.VORRAT),
            Arguments.of("Kokosmilch", Section.VORRAT),
            Arguments.of("Tomatenmark", Section.VORRAT),
            Arguments.of("Gemüsebrühe", Section.VORRAT),
            Arguments.of("Zucker", Section.VORRAT),
            Arguments.of("Mehl", Section.VORRAT),
            Arguments.of("Vollkornnudeln", Section.VORRAT),
            Arguments.of("Kidneybohnen", Section.VORRAT),
            Arguments.of("Hefe", Section.VORRAT),

            // Gewürze, Öle & Soßen
            Arguments.of("Salz", Section.GEWUERZE_SOSSEN),
            Arguments.of("Pfeffer", Section.GEWUERZE_SOSSEN),
            Arguments.of("Olivenöl", Section.GEWUERZE_SOSSEN),
            Arguments.of("Sojasauce", Section.GEWUERZE_SOSSEN),
            Arguments.of("Ketchup", Section.GEWUERZE_SOSSEN),
            Arguments.of("Senf", Section.GEWUERZE_SOSSEN),
            Arguments.of("Essig", Section.GEWUERZE_SOSSEN),
            Arguments.of("Currypulver", Section.GEWUERZE_SOSSEN),
            Arguments.of("Paprikapulver", Section.GEWUERZE_SOSSEN),
            Arguments.of("Balsamico", Section.GEWUERZE_SOSSEN),

            // Frühstück & Aufstrich
            Arguments.of("Marmelade", Section.FRUEHSTUECK),
            Arguments.of("Nutella", Section.FRUEHSTUECK),
            Arguments.of("Honig", Section.FRUEHSTUECK),
            Arguments.of("Müsli", Section.FRUEHSTUECK),
            Arguments.of("Cornflakes", Section.FRUEHSTUECK),
            Arguments.of("Haferflocken", Section.FRUEHSTUECK),
            Arguments.of("Erdnussbutter", Section.FRUEHSTUECK),

            // Süßes & Snacks
            Arguments.of("Schokolade", Section.SUESSES_SNACKS),
            Arguments.of("Gummibärchen", Section.SUESSES_SNACKS),
            Arguments.of("Chips", Section.SUESSES_SNACKS),
            Arguments.of("Kekse", Section.SUESSES_SNACKS),
            Arguments.of("Salzstangen", Section.SUESSES_SNACKS),
            Arguments.of("Erdnussflips", Section.SUESSES_SNACKS),
            Arguments.of("Süßigkeiten", Section.SUESSES_SNACKS),

            // Kaffee & Tee
            Arguments.of("Kaffee", Section.KAFFEE_TEE),
            Arguments.of("Kaffeebohnen", Section.KAFFEE_TEE),
            Arguments.of("Tee", Section.KAFFEE_TEE),
            Arguments.of("Kräutertee", Section.KAFFEE_TEE),
            Arguments.of("Kaffeepads", Section.KAFFEE_TEE),
            Arguments.of("Teebeutel", Section.KAFFEE_TEE),

            // Getränke
            Arguments.of("Apfelsaft", Section.GETRAENKE),
            Arguments.of("Mineralwasser", Section.GETRAENKE),
            Arguments.of("Cola", Section.GETRAENKE),
            Arguments.of("Orangensaft", Section.GETRAENKE),
            Arguments.of("Bier", Section.GETRAENKE),
            Arguments.of("Wein", Section.GETRAENKE),
            Arguments.of("Eistee", Section.GETRAENKE),

            // Drogerie & Hygiene
            Arguments.of("Klopapier", Section.DROGERIE),
            Arguments.of("Zahnpasta", Section.DROGERIE),
            Arguments.of("Duschgel", Section.DROGERIE),
            Arguments.of("Shampoo", Section.DROGERIE),
            Arguments.of("Deo", Section.DROGERIE),
            Arguments.of("Windeln", Section.DROGERIE),
            Arguments.of("Rasierschaum", Section.DROGERIE),

            // Haushalt & Reinigung
            Arguments.of("Spüli", Section.HAUSHALT),
            Arguments.of("Müllbeutel", Section.HAUSHALT),
            Arguments.of("Waschmittel", Section.HAUSHALT),
            Arguments.of("Schwämme", Section.HAUSHALT),
            Arguments.of("Alufolie", Section.HAUSHALT),
            Arguments.of("Frischhaltefolie", Section.HAUSHALT),
            Arguments.of("Küchenrolle", Section.HAUSHALT),

            // Unknown -> Sonstiges
            Arguments.of("Bohrmaschine", Section.SONSTIGES),
            Arguments.of("Autozeitschrift", Section.SONSTIGES),
            Arguments.of("asdkfjqwer", Section.SONSTIGES)
        );
    }
}
