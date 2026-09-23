package citytrade.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The engine-owned seeded random source (SplitMix64). It is an immutable value stored in the game state:
 * every draw returns the result together with the next random state, so the same seed always gives
 * the same sequence and the state stays a plain value.
 */
public record GameRandom(long state) {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    public static GameRandom seeded(long seed) {
        return new GameRandom(seed);
    }

    public LongDraw nextLong() {
        long next = state + GOLDEN_GAMMA;
        long z = next;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return new LongDraw(z ^ (z >>> 31), new GameRandom(next));
    }

    /** Uniform value in {@code [0, bound)}. Rejection sampling avoids modulo bias. */
    public IntDraw nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, but is " + bound);
        }
        GameRandom random = this;
        while (true) {
            LongDraw draw = random.nextLong();
            random = draw.next();
            long bits = draw.value() >>> 1;
            long value = bits % bound;
            // Reject the incomplete last block of the 63-bit range (overflow makes the sum negative).
            if (bits - value + (bound - 1) >= 0) {
                return new IntDraw((int) value, random);
            }
        }
    }

    /** Fisher-Yates shuffle; the input list is not changed. */
    public <T> Shuffle<T> shuffle(List<T> items) {
        List<T> result = new ArrayList<>(items);
        GameRandom random = this;
        for (int i = result.size() - 1; i > 0; i--) {
            IntDraw draw = random.nextInt(i + 1);
            random = draw.next();
            T swap = result.get(i);
            result.set(i, result.get(draw.value()));
            result.set(draw.value(), swap);
        }
        return new Shuffle<>(result, random);
    }

    public record LongDraw(long value, GameRandom next) {
    }

    public record IntDraw(int value, GameRandom next) {
    }

    public record Shuffle<T>(List<T> items, GameRandom next) {

        public Shuffle {
            items = List.copyOf(items);
        }
    }
}
