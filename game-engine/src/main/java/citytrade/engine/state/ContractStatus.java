package citytrade.engine.state;

import java.util.Set;

/**
 * Life cycle of a formal contract (Concept 16). A proposal is PROPOSED until the partner signs it or the window
 * ends; a signed contract is ACTIVE until its obligation is paid, it is broken or both parties cancel it.
 */
public enum ContractStatus {
    /** Waiting for the partner's signature. Nothing has moved yet. */
    PROPOSED,
    /** Signed: the creditor gave the bundle, the debtor owes the obligation in the due round. */
    ACTIVE,
    /** The debtor paid the obligation in full in step 1.5 of the due round. */
    FULFILLED,
    /** The debtor broke it on purpose, or could not pay in full when it was due (D3). */
    BROKEN,
    /** Both parties agreed to cancel before the due round. */
    CANCELLED,
    /** The window ended before the partner signed (step 4.1, like open trade offers). */
    EXPIRED,
    /** The partner signed, but the creditor no longer had the bundle to give; nothing moved. */
    INVALID;

    public boolean canMoveTo(ContractStatus target) {
        return switch (this) {
            case PROPOSED -> Set.of(ACTIVE, EXPIRED, INVALID).contains(target);
            case ACTIVE -> Set.of(FULFILLED, BROKEN, CANCELLED).contains(target);
            case FULFILLED, BROKEN, CANCELLED, EXPIRED, INVALID -> false;
        };
    }
}
