package citytrade.engine.state;

/** Where a revealed regional opportunity is in its life (Numbers Sheet 17). Only OPEN ones take bids. */
public enum OpportunityStatus {
    /** Revealed and not won yet; takes bids in every window until someone wins it. */
    OPEN,
    /** One player had the single highest bid at Round Resolution (step 4.2) and paid it. */
    WON,
    /** Never won: removed at the Round Resolution of the last round (Numbers Sheet 17). */
    REMOVED
}
