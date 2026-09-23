package citytrade.engine.state;

/** Extra detail on why an offer was closed, where the status alone does not say it. */
public enum OfferCloseReason {
    /** REJECTED because the recipient answered with a counteroffer (Architecture 5.3). */
    COUNTEROFFER
}
