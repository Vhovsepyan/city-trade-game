package citytrade.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class DistributionTest {

    @Test
    void summarizesValuesWithNearestRankPercentiles() {
        Distribution distribution = Distribution.of(List.of(17, 12, 15, 15, 20, 11, 14, 18));

        assertEquals(8, distribution.count());
        assertEquals(15.25, distribution.mean());
        assertEquals(11, distribution.min());
        assertEquals(12, distribution.p25());
        assertEquals(15, distribution.median());
        assertEquals(17, distribution.p75());
        assertEquals(20, distribution.max());
        assertEquals(new TreeMap<>(Map.of(11, 1, 12, 1, 14, 1, 15, 2, 17, 1, 18, 1, 20, 1)),
                distribution.histogram());
    }

    @Test
    void negativePrestigeIsKept() {
        Distribution distribution = Distribution.of(List.of(-2));

        assertEquals(-2, distribution.min());
        assertEquals(-2, distribution.median());
        assertEquals(-2, distribution.max());
        assertEquals(-2.0, distribution.mean());
    }

    @Test
    void needsAtLeastOneValue() {
        assertThrows(IllegalArgumentException.class, () -> Distribution.of(List.of()));
    }
}
