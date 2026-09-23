package citytrade.engine.state;

/** Life cycle of a direct trade offer (Architecture 5.1). Only OPEN may change; every other status is final. */
public enum TradeOfferStatus {
    /** Waiting for the recipient. */
    OPEN,
    /** The recipient accepted and the trade was executed. */
    ACCEPTED,
    /** The recipient refused or countered. */
    REJECTED,
    /** The window ended while the offer was open (D8, step 4.1). */
    EXPIRED,
    /** The proposer withdrew. */
    CANCELLED,
    /** Could no longer be executed: a side lacked resources when the recipient accepted. */
    INVALID;

    public boolean canMoveTo(TradeOfferStatus target) {
        return this == OPEN && target != OPEN;
    }
}
