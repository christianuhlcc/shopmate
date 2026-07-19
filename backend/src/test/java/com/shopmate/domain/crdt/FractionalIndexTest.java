package com.shopmate.domain.crdt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loads shared/fractional-index-vectors.json — the SAME file loaded by the
 * TypeScript suite (frontend/src/features/shopping-list/__tests__/fractionalIndex.test.ts)
 * — and asserts identical outputs. This pins the Java and TS FractionalIndex
 * implementations together (BUG-8: they used to be faithful mirrors of the
 * same buggy algorithm and could silently drift apart again without this).
 */
class FractionalIndexTest {

    private static final JsonNode VECTORS = loadVectors();

    private static JsonNode loadVectors() {
        try {
            Path dir = Path.of("").toAbsolutePath();
            for (int i = 0; i < 8; i++) {
                Path candidate = dir.resolve("shared/fractional-index-vectors.json");
                if (Files.exists(candidate)) {
                    return new ObjectMapper().readTree(candidate.toFile());
                }
                Path parent = dir.getParent();
                if (parent == null) break;
                dir = parent;
            }
            throw new IllegalStateException(
                "could not locate shared/fractional-index-vectors.json above "
                    + Path.of("").toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<JsonNode> vectorList(String field) {
        List<JsonNode> out = new ArrayList<>();
        VECTORS.get(field).forEach(out::add);
        return out;
    }

    static Stream<JsonNode> appendVectors() {
        return vectorList("append").stream();
    }

    static Stream<JsonNode> betweenVectors() {
        return vectorList("between").stream();
    }

    static Stream<JsonNode> tooTightVectors() {
        return vectorList("tooTight").stream();
    }

    @ParameterizedTest
    @MethodSource("appendVectors")
    void appendMatchesSharedVector(JsonNode vector) {
        String last = vector.get("last").isNull() ? null : vector.get("last").asText();
        String expected = vector.get("expect").asText();
        assertThat(FractionalIndex.append(last)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("betweenVectors")
    void betweenMatchesSharedVector(JsonNode vector) {
        String before = vector.get("before").isNull() ? null : vector.get("before").asText();
        String after = vector.get("after").isNull() ? null : vector.get("after").asText();
        String expected = vector.get("expect").asText();
        assertThat(FractionalIndex.between(before, after)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("tooTightVectors")
    void betweenThrowsForTooTightSharedVector(JsonNode vector) {
        String before = vector.get("before").asText();
        String after = vector.get("after").asText();
        assertThatThrownBy(() -> FractionalIndex.between(before, after))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reproducesPinnedNarrowingHiBisectionChain() {
        JsonNode chain = VECTORS.get("bisectionNarrowingHi");
        String lo = chain.get("lo").asText();
        String hi = chain.get("initialHi").asText();
        for (JsonNode expectedKeyNode : chain.get("keys")) {
            String expectedKey = expectedKeyNode.asText();
            String mid = FractionalIndex.between(lo, hi);
            assertThat(mid).isEqualTo(expectedKey).isGreaterThan(lo).isLessThan(hi);
            hi = mid;
        }
    }

    @Test
    void reproducesPinnedNarrowingLoBisectionChain() {
        JsonNode chain = VECTORS.get("bisectionNarrowingLo");
        String lo = chain.get("initialLo").asText();
        String hi = chain.get("hi").asText();
        for (JsonNode expectedKeyNode : chain.get("keys")) {
            String expectedKey = expectedKeyNode.asText();
            String mid = FractionalIndex.between(lo, hi);
            assertThat(mid).isEqualTo(expectedKey).isGreaterThan(lo).isLessThan(hi);
            lo = mid;
        }
    }

    // --- Direct behavioral tests (readable without cross-referencing the JSON) ---

    @Test
    void betweenIsStrictlyBetween() {
        String mid = FractionalIndex.between("a0", "z0");
        assertThat(mid).isGreaterThan("a0").isLessThan("z0");
    }

    @Test
    void betweenAdjacentCharacters() {
        String mid = FractionalIndex.between("a0", "b0");
        assertThat(mid).isGreaterThan("a0").isLessThan("b0");
    }

    @Test
    void betweenAllowsRepeatedBisection() {
        String k1 = "a0";
        String k2 = "b0";
        for (int i = 0; i < 20; i++) {
            String mid = FractionalIndex.between(k1, k2);
            assertThat(mid).isGreaterThan(k1).isLessThan(k2);
            k2 = mid;
        }
    }

    @Test
    void betweenAllowsRepeatedBisectionNarrowingTheWorstCaseGap() {
        // a0/z0 narrowing hi towards the fixed lo is the tightest, most
        // convergence-prone case (see shared vectors: bisectionNarrowingHi).
        String k1 = "a0";
        String k2 = "z0";
        for (int i = 0; i < 30; i++) {
            String mid = FractionalIndex.between(k1, k2);
            assertThat(mid).isGreaterThan(k1).isLessThan(k2);
            k2 = mid;
        }
    }

    @Test
    void betweenNullBeforeMeansStart() {
        String k = FractionalIndex.between(null, "m0");
        assertThat(k).isLessThan("m0");
    }

    @Test
    void betweenNullAfterMeansEnd() {
        String k = FractionalIndex.between("m0", null);
        assertThat(k).isGreaterThan("m0");
    }

    @Test
    void betweenNullBothMeansWholeRange() {
        String k = FractionalIndex.between(null, null);
        assertThat(k).isNotBlank();
    }

    @Test
    void appendProducesIncreasingKeys() {
        String k1 = FractionalIndex.append(null);
        String k2 = FractionalIndex.append(k1);
        String k3 = FractionalIndex.append(k2);
        assertThat(k1).isLessThan(k2);
        assertThat(k2).isLessThan(k3);
    }

    @Test
    void appendIsOrderSafeAgainstALongerPreviouslyBisectedKey() {
        // Simulates: an item gets reordered (sortKey becomes a
        // between()-generated multi-char key), then a brand new item is
        // appended after it.
        String reordered = FractionalIndex.between("a0", "z0"); // e.g. "m"
        String appended = FractionalIndex.append(reordered);
        assertThat(appended).isGreaterThan(reordered);
    }

    @Test
    void betweenEqualKeysThrows() {
        assertThatThrownBy(() -> FractionalIndex.between("m0", "m0"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void betweenReversedOrderThrows() {
        assertThatThrownBy(() -> FractionalIndex.between("z0", "a0"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void appendAfterHighestKeyExtendsInsteadOfOverflowing() {
        String next = FractionalIndex.append("z0");
        assertThat(next).isGreaterThan("z0");
    }

    @Test
    void bug8RegressionNoLongerSortsAfterAfter() {
        // The original bug: between("b0", "b0a") returned "b0am", which
        // sorts AFTER "b0a" — violating the strictly-between contract.
        String mid = FractionalIndex.between("b0", "b0a");
        assertThat(mid).isGreaterThan("b0").isLessThan("b0a");
    }

    @Test
    void betweenThrowsDescriptivelyForATooTightPairInsteadOfReturningAWrongKey() {
        // hi == lo + a single copy of the alphabet's minimum digit: provably
        // no string fits strictly between them under plain lexicographic
        // string comparison. Must fail loudly, never silently misorder.
        assertThatThrownBy(() -> FractionalIndex.between("b0", "b00"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidCharacters() {
        assertThatThrownBy(() -> FractionalIndex.between("b0", "b0!"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Property test: repeated random bisection always honors the contract ---

    @Test
    void propertyBetweenIsAlwaysStrictlyBetweenUnderRandomBisection() {
        Random random = new Random(20260719L);
        String alphabet = "0123456789abcdefghijklmnopqrstuvwxyz";

        for (int trial = 0; trial < 500; trial++) {
            String a = randomKey(random, alphabet);
            String b = randomKey(random, alphabet);
            while (a.equals(b)) {
                b = randomKey(random, alphabet);
            }
            String lo = a.compareTo(b) < 0 ? a : b;
            String hi = a.compareTo(b) < 0 ? b : a;

            int depth = 1 + random.nextInt(15);
            for (int d = 0; d < depth; d++) {
                String mid;
                try {
                    mid = FractionalIndex.between(lo, hi);
                } catch (IllegalArgumentException tooTight) {
                    // A too-tight pair is a legitimate terminal state for this
                    // trial (see betweenThrowsDescriptivelyForATooTightPair...).
                    break;
                }
                assertThat(mid).isGreaterThan(lo).isLessThan(hi);
                assertThat(mid.length()).isLessThan(60); // keys stay modest, never unbounded
                if (random.nextBoolean()) {
                    hi = mid;
                } else {
                    lo = mid;
                }
            }
        }
    }

    @Test
    void propertyAppendChainsStayStrictlyIncreasingAndModestInLength() {
        Random random = new Random(20260719L);
        String key = null;
        for (int i = 0; i < 200; i++) {
            String next = FractionalIndex.append(key);
            if (key != null) {
                assertThat(next).isGreaterThan(key);
            }
            assertThat(next.length()).isLessThan(600); // linear growth, not exponential
            key = random.nextBoolean() ? next : key; // occasionally skip appending onto the chain
        }
    }

    private static String randomKey(Random random, String alphabet) {
        int len = 1 + random.nextInt(4);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
