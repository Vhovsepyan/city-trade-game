package citytrade.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GameRandomTest {

    @Test
    void matchesTheSplitMix64ReferenceSequence() {
        // Pins the algorithm: saved games and simulations must replay the same way in later versions.
        GameRandom.LongDraw first = GameRandom.seeded(0).nextLong();
        GameRandom.LongDraw second = first.next().nextLong();
        assertEquals(0xE220A8397B1DCDAFL, first.value());
        assertEquals(0x6E789E6AA1B965F4L, second.value());
    }

    @Test
    void sameSeedGivesTheSameSequence() {
        assertEquals(draws(GameRandom.seeded(42), 50), draws(GameRandom.seeded(42), 50));
    }

    @Test
    void differentSeedsGiveDifferentSequences() {
        assertNotEquals(draws(GameRandom.seeded(1), 50), draws(GameRandom.seeded(2), 50));
    }

    @Test
    void nextIntStaysInRangeAndHitsEveryValue() {
        GameRandom random = GameRandom.seeded(7);
        int[] counts = new int[3];
        for (int i = 0; i < 3000; i++) {
            GameRandom.IntDraw draw = random.nextInt(3);
            random = draw.next();
            counts[draw.value()]++;
        }
        for (int count : counts) {
            assertTrue(count > 800 && count < 1200, "roughly uniform, got " + count);
        }
    }

    @Test
    void nextIntRejectsNonPositiveBound() {
        assertThrows(IllegalArgumentException.class, () -> GameRandom.seeded(1).nextInt(0));
    }

    @Test
    void shuffleIsAPermutationAndDoesNotChangeTheInput() {
        List<Integer> input = List.of(1, 2, 3, 4, 5, 6, 7, 8);
        GameRandom.Shuffle<Integer> shuffle = GameRandom.seeded(3).shuffle(input);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), input);
        assertEquals(new HashSet<>(input), new HashSet<>(shuffle.items()));
        assertEquals(input.size(), shuffle.items().size());
        assertNotEquals(GameRandom.seeded(3), shuffle.next());
    }

    @Test
    void shuffleReachesManyOrders() {
        Set<List<Integer>> orders = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            orders.add(GameRandom.seeded(seed).shuffle(List.of(1, 2, 3, 4)).items());
        }
        assertEquals(24, orders.size(), "all 4! orders appear across 200 seeds");
    }

    private static List<Long> draws(GameRandom random, int count) {
        List<Long> values = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            GameRandom.LongDraw draw = random.nextLong();
            values.add(draw.value());
            random = draw.next();
        }
        return values;
    }
}
