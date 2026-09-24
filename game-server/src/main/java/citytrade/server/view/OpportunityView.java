package citytrade.server.view;

import citytrade.engine.ruleset.OpportunityRules.OpportunityCard;
import citytrade.engine.state.OpportunityStatus;
import java.util.Objects;

/**
 * A revealed regional opportunity (Numbers Sheet 17), without other players' secret bids: only the viewing
 * seat's own bid ({@code ownBid}, 0 if none) is included.
 *
 * @param card          the opportunity card
 * @param appearedRound the round it was revealed in
 * @param status        OPEN, WON or REMOVED
 * @param winnerSeat    the winning seat; null while OPEN
 * @param ownBid        the viewing seat's own active bid; 0 if it has none
 */
public record OpportunityView(
        OpportunityCard card,
        int appearedRound,
        OpportunityStatus status,
        Integer winnerSeat,
        int ownBid) {

    public OpportunityView {
        Objects.requireNonNull(card);
        Objects.requireNonNull(status);
    }
}
