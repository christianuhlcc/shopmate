package com.shopmate.domain.section;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads the bundled German grocery-term -> Section dictionary from the
 * classpath (`sections/dictionary.tsv`). Plain java.io/java.nio only —
 * ArchUnit forbids framework imports in domain code.
 *
 * Format: {@code term<TAB>CODE}, blank lines and lines starting with '#'
 * are ignored. Fails fast (throws) on malformed lines or unknown codes so a
 * bad dictionary edit is caught at startup, not silently misclassified.
 */
public final class SectionDictionary {

    private static final String DEFAULT_RESOURCE = "/sections/dictionary.tsv";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final Map<String, Section> terms;

    public SectionDictionary() {
        this(DEFAULT_RESOURCE);
    }

    /** Package-private: lets tests load alternate fixture dictionaries. */
    SectionDictionary(String resourcePath) {
        this.terms = load(resourcePath);
    }

    /** Returns the section for an already-normalized term, or {@code null} if absent. */
    public Section lookup(String normalizedTerm) {
        return terms.get(normalizedTerm);
    }

    public int size() {
        return terms.size();
    }

    private static Map<String, Section> load(String resourcePath) {
        Map<String, Section> result = new LinkedHashMap<>();
        try (InputStream in = SectionDictionary.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Section dictionary resource not found: " + resourcePath);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    String raw = line.strip();
                    if (raw.isEmpty() || raw.startsWith("#")) {
                        continue;
                    }
                    // A blank term or code field is impossible here: `raw` is
                    // already whole-line-stripped above, so a leading/trailing
                    // all-whitespace field would have been consumed by that
                    // strip() and collapsed parts.length below 2 instead.
                    String[] parts = raw.split("\t");
                    if (parts.length != 2) {
                        throw new IllegalStateException(
                            "Malformed dictionary line " + lineNumber + ": \"" + line + "\"");
                    }
                    String term = normalizeKey(parts[0]);
                    String code = parts[1].strip();
                    Section section;
                    try {
                        section = Section.valueOf(code);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalStateException(
                            "Unknown section code \"" + code + "\" at line " + lineNumber, e);
                    }
                    Section previous = result.put(term, section);
                    if (previous != null) {
                        throw new IllegalStateException(
                            "Duplicate dictionary key \"" + term + "\" at line " + lineNumber
                                + " (normalizes to an existing entry)");
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load section dictionary: " + resourcePath, e);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Safety-net normalization applied to dictionary keys at load time.
     * Keys are expected to already be normalized in the TSV; this just
     * guards against stray casing/whitespace/ß without pulling in the full
     * quantity-stripping logic of {@link SectionClassifier#normalize}, which
     * makes no sense for curated dictionary terms.
     */
    private static String normalizeKey(String raw) {
        String lower = raw.toLowerCase(Locale.GERMAN).strip().replace("ß", "ss");
        return WHITESPACE.matcher(lower).replaceAll(" ");
    }
}
