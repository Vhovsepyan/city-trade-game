package citytrade.engine.state;

/** One player's active bid on one opportunity: {@code amount} Money, always at least 1 (0 = no bid). */
public record SecretBid(int seat, int amount) {

    public SecretBid {
        if (amount < 1) {
            throw new IllegalArgumentException("an active bid must be at least 1, was " + amount);
        }
    }
}
