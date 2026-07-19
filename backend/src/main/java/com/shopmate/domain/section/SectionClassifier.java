package com.shopmate.domain.section;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pure-domain service classifying a German grocery item name into a
 * {@link Section} for zero-runtime-cost auto-grouping (ADR-0012).
 *
 * Pipeline for a single normalized token: exact dictionary lookup ->
 * plural-stem retries -> longest-suffix compound match. {@link #classify}
 * additionally retries the last meaningful word of a multiword name before
 * giving up to {@link Section#SONSTIGES}.
 */
public final class SectionClassifier {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Matches one leading quantity/unit token, e.g. "500g ", "2x ", "1,5l ",
     * "2 kg ". Applied repeatedly so multi-token quantities ("2 kg ") are
     * fully stripped.
     */
    private static final Pattern LEADING_QUANTITY = Pattern.compile(
        "^\\d+([.,]\\d+)?\\s*(x|kg|g|ml|l|stk|stück|stueck|pkg|packung|dose|glas|beutel|bund|st)?\\s+"
    );

    /** German plural suffixes to strip, tried in this order (Kleppmann-inspired minimal-first). */
    private static final String[] PLURAL_SUFFIXES = {"n", "en", "e", "er", "s"};

    private static final int MIN_COMPOUND_SUFFIX_LENGTH = 4;
    private static final int MIN_STEM_LENGTH = 2;

    private final SectionDictionary dictionary;

    public SectionClassifier(SectionDictionary dictionary) {
        this.dictionary = dictionary;
    }

    /**
     * Lowercases, trims, folds ß to ss, collapses internal whitespace, and
     * strips leading quantity/unit tokens ("500g", "2x", "1,5l", "2 kg").
     * Does not touch trailing tokens (e.g. "3,5%") — those are handled by
     * the last-word retry in {@link #classify}.
     */
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String result = name.toLowerCase(Locale.GERMAN).strip().replace("ß", "ss");
        result = WHITESPACE.matcher(result).replaceAll(" ").strip();
        String previous;
        do {
            previous = result;
            result = LEADING_QUANTITY.matcher(result).replaceFirst("").strip();
        } while (!result.equals(previous));
        return result;
    }

    public Section classify(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return Section.SONSTIGES;
        }

        Section direct = classifySingleToken(normalized);
        if (direct != null) {
            return direct;
        }

        if (normalized.indexOf(' ') >= 0) {
            String lastWord = lastMeaningfulWord(normalized);
            if (lastWord != null) {
                Section fromLastWord = classifySingleToken(lastWord);
                if (fromLastWord != null) {
                    return fromLastWord;
                }
            }
        }

        return Section.SONSTIGES;
    }

    private Section classifySingleToken(String token) {
        Section exact = dictionary.lookup(token);
        if (exact != null) {
            return exact;
        }
        Section pluralMatch = classifyByPluralStem(token);
        if (pluralMatch != null) {
            return pluralMatch;
        }
        return classifyByCompoundSuffix(token);
    }

    private Section classifyByPluralStem(String token) {
        for (String suffix : PLURAL_SUFFIXES) {
            if (token.endsWith(suffix) && token.length() - suffix.length() >= MIN_STEM_LENGTH) {
                String stem = token.substring(0, token.length() - suffix.length());
                Section match = dictionary.lookup(stem);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    /**
     * German compounds are head-final ("Vollkornbrot" -> "brot",
     * "Kirschtomaten" -> "tomaten"). Tries suffixes of the token from
     * longest to shortest (down to a minimum length) against the
     * dictionary, returning the first — i.e. longest — match.
     */
    private Section classifyByCompoundSuffix(String token) {
        int longestProperSuffix = token.length() - 1;
        for (int len = longestProperSuffix; len >= MIN_COMPOUND_SUFFIX_LENGTH; len--) {
            String suffix = token.substring(token.length() - len);
            Section match = dictionary.lookup(suffix);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    /**
     * Last non-numeric/non-punctuation word of a multiword name, e.g.
     * "milch 3,5%" -> "milch" (the trailing "3,5%" carries no letters and is
     * skipped). Returns {@code null} if no word contains a letter.
     */
    private String lastMeaningfulWord(String normalized) {
        String[] tokens = WHITESPACE.split(normalized);
        for (int i = tokens.length - 1; i >= 0; i--) {
            if (containsLetter(tokens[i])) {
                return tokens[i];
            }
        }
        return null;
    }

    private boolean containsLetter(String token) {
        for (int i = 0; i < token.length(); i++) {
            if (Character.isLetter(token.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
