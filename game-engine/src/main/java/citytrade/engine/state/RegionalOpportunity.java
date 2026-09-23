package citytrade.engine.state;

import citytrade.engine.ruleset.OpportunityRules.OpportunityCard;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A revealed regional opportunity card (Numbers Sheet 17).
 *
 * @param card          the opportunity card (production reward for the winner)
 * @param appearedRound the round the card was revealed in step 2.3
 * @param status        OPEN until someone wins it
 * @param winnerSeat    the seat that won it; empty while OPEN
 * @param bids          the active bids of this window, one per seat at most, sorted by seat. Sorting keeps the
 *                      state independent of the order in which bids arrive.
 */
public record RegionalOpportunity(OpportunityCard card, int appearedRound, OpportunityStatus status,
        Optional<Integer> winnerSeat, List<SecretBid> bids) {

    public RegionalOpportunity {
        Objects.requireNonNull(card);
        Objects.requireNonNull(status);
        Objects.requireNonNull(winnerSeat);
        if ((status == OpportunityStatus.WON) != winnerSeat.isPresent()) {
            throw new IllegalArgumentException("a winner exists exactly when the opportunity is WON");
        }
        List<SecretBid> sorted = new ArrayList<>(bids);
        sorted.sort(Comparator.comparingInt(SecretBid::seat));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).seat() == sorted.get(i - 1).seat()) {
                throw new IllegalArgumentException("seat " + sorted.get(i).seat() + " has two bids");
            }
        }
        bids = List.copyOf(sorted);
    }

    /** A card revealed in step 2.3: open, no bids. */
    public static RegionalOpportunity revealed(OpportunityCard card, int round) {
        return new RegionalOpportunity(card, round, OpportunityStatus.OPEN, Optional.empty(), List.of());
    }

    public String id() {
        return card.id();
    }

    /** The active bid of {@code seat}; 0 if it has none. */
    public int bidOf(int seat) {
        return bids.stream().filter(bid -> bid.seat() == seat).mapToInt(SecretBid::amount).findFirst().orElse(0);
    }

    /** The opportunity with the bid of {@code seat} set to {@code amount}; 0 removes the bid (pass / withdraw). */
    public RegionalOpportunity withBid(int seat, int amount) {
        List<SecretBid> updated = new ArrayList<>(bids.stream().filter(bid -> bid.seat() != seat).toList());
        if (amount > 0) {
            updated.add(new SecretBid(seat, amount));
        }
        return new RegionalOpportunity(card, appearedRound, status, winnerSeat, updated);
    }

    /** Every bid released: the reservations end with the Round Resolution. */
    public RegionalOpportunity withoutBids() {
        return new RegionalOpportunity(card, appearedRound, status, winnerSeat, List.of());
    }

    /** Never won and removed after the last round; all bids are released. */
    public RegionalOpportunity removed() {
        return new RegionalOpportunity(card, appearedRound, OpportunityStatus.REMOVED, Optional.empty(), List.of());
    }

    /** Won by {@code seat}; all bids are released. */
    public RegionalOpportunity wonBy(int seat) {
        return new RegionalOpportunity(card, appearedRound, OpportunityStatus.WON, Optional.of(seat), List.of());
    }
}
