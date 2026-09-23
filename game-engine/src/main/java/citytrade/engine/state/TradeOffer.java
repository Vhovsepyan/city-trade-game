package citytrade.engine.state;

import citytrade.engine.ResourceBundle;
import java.util.Objects;
import java.util.Optional;

/**
 * A direct instant-trade offer (Architecture 5, Concept 14-15). Nothing is reserved while it is open;
 * the holdings are checked again when the recipient accepts. Only proposer and recipient may see it (D7);
 * the engine keeps all offers, hiding them is the job of the view layer.
 *
 * @param id             unique in the game, in creation order
 * @param parentOfferId  the offer this one counters, if it is a counteroffer
 * @param proposerSeat   who made the offer
 * @param recipientSeat  who may accept, reject or counter it
 * @param offered        what the proposer gives
 * @param requested      what the recipient gives
 * @param roundCreated   the round whose window the offer was made in
 * @param closeReason    set only when the status alone does not explain the close (counteroffer)
 */
public record TradeOffer(
        int id,
        Optional<Integer> parentOfferId,
        int proposerSeat,
        int recipientSeat,
        ResourceBundle offered,
        ResourceBundle requested,
        int roundCreated,
        TradeOfferStatus status,
        Optional<OfferCloseReason> closeReason) {

    public TradeOffer {
        Objects.requireNonNull(parentOfferId);
        Objects.requireNonNull(offered);
        Objects.requireNonNull(requested);
        Objects.requireNonNull(status);
        Objects.requireNonNull(closeReason);
    }

    public static TradeOffer open(int id, Optional<Integer> parentOfferId, int proposerSeat, int recipientSeat,
            ResourceBundle offered, ResourceBundle requested, int round) {
        return new TradeOffer(id, parentOfferId, proposerSeat, recipientSeat, offered, requested, round,
                TradeOfferStatus.OPEN, Optional.empty());
    }

    public boolean isOpen() {
        return status == TradeOfferStatus.OPEN;
    }

    /** This offer in {@code target} status. The only way a status changes; forbidden transitions throw. */
    public TradeOffer closedAs(TradeOfferStatus target, Optional<OfferCloseReason> reason) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("offer " + id + " cannot move from " + status + " to " + target);
        }
        return new TradeOffer(id, parentOfferId, proposerSeat, recipientSeat, offered, requested, roundCreated,
                target, reason);
    }
}
