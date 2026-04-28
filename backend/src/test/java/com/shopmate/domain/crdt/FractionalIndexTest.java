package com.shopmate.domain.crdt;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FractionalIndexTest {

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
    void appendProducesIncreasingKeys() {
        String k1 = FractionalIndex.append(null);
        String k2 = FractionalIndex.append(k1);
        String k3 = FractionalIndex.append(k2);
        assertThat(k1).isLessThan(k2);
        assertThat(k2).isLessThan(k3);
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
}
