package com.shopmate.domain.section;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loader behavior (fail-fast on malformed input) plus a dictionary-integrity
 * check over the real bundled TSV: parses cleanly, every code is a valid
 * {@link Section}, and no two lines normalize to the same key.
 */
class SectionDictionaryTest {

    @Test
    void loadsWellFormedFixture() {
        SectionDictionary dictionary = new SectionDictionary("/sections/test-fixture-dictionary.tsv");
        assertThat(dictionary.lookup("milch")).isEqualTo(Section.MOLKEREI_EIER);
        assertThat(dictionary.lookup("brot")).isEqualTo(Section.BROT_BACKWAREN);
        assertThat(dictionary.size()).isGreaterThan(0);
    }

    @Test
    void unknownTermLooksUpToNull() {
        SectionDictionary dictionary = new SectionDictionary("/sections/test-fixture-dictionary.tsv");
        assertThat(dictionary.lookup("nonexistent-term")).isNull();
    }

    @Test
    void missingResourceThrows() {
        assertThatThrownBy(() -> new SectionDictionary("/sections/does-not-exist.tsv"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void malformedLineThrows() {
        assertThatThrownBy(() -> new SectionDictionary("/sections/test-fixture-malformed.tsv"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Malformed");
    }

    @Test
    void unknownSectionCodeThrows() {
        assertThatThrownBy(() -> new SectionDictionary("/sections/test-fixture-unknown-code.tsv"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unknown section code");
    }

    @Test
    void duplicateKeyAfterNormalizationThrows() {
        // "milch" and "Milch" normalize to the same key.
        assertThatThrownBy(() -> new SectionDictionary("/sections/test-fixture-duplicate.tsv"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate dictionary key");
    }

    @Test
    void realDictionaryLoadsWithoutError() {
        SectionDictionary dictionary = new SectionDictionary();
        assertThat(dictionary.size()).isGreaterThanOrEqualTo(1000);
    }

    @Test
    void realDictionaryHasNoDuplicateKeysAndOnlyValidCodesAndIsReasonablySized() throws IOException {
        Map<String, String> seen = new HashMap<>();
        Set<String> validCodes = new HashSet<>();
        for (Section section : Section.values()) {
            validCodes.add(section.name());
        }

        int entryCount = 0;
        try (InputStream in = SectionDictionary.class.getResourceAsStream("/sections/dictionary.tsv")) {
            assertThat(in).as("dictionary.tsv resource must exist").isNotNull();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    String raw = line.strip();
                    if (raw.isEmpty() || raw.startsWith("#")) {
                        continue;
                    }
                    String[] parts = raw.split("\t");
                    assertThat(parts)
                        .as("line %d must be term<TAB>CODE: \"%s\"", lineNumber, line)
                        .hasSize(2);
                    String term = parts[0].strip().toLowerCase(java.util.Locale.GERMAN).replace("ß", "ss");
                    String code = parts[1].strip();
                    assertThat(validCodes)
                        .as("line %d has an unknown section code \"%s\"", lineNumber, code)
                        .contains(code);
                    assertThat(seen)
                        .as("line %d: \"%s\" duplicates a key already defined at an earlier line", lineNumber, term)
                        .doesNotContainKey(term);
                    seen.put(term, code);
                    entryCount++;
                }
            }
        }

        // Target ~1,000-1,200 entries (plan Phase 1).
        assertThat(entryCount).isBetween(900, 1500);
    }
}
