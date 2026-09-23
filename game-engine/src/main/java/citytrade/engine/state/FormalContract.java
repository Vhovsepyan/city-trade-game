package citytrade.engine.state;

import citytrade.engine.ResourceBundle;
import java.util.Objects;
import java.util.Optional;

/**
 * A formal contract in the single v1 shape (D10): the creditor gives {@code givenNow} when the contract is signed;
 * the debtor owes {@code owed} in {@code dueRound}. It is paid or broken automatically in step 1.5 of that round (D3).
 *
 * @param id                 unique in the game, in creation order
 * @param proposerSeat       who proposed it; the other party signs
 * @param creditorSeat       gives {@code givenNow} at signing and receives {@code owed} (or compensation)
 * @param debtorSeat         owes {@code owed} in {@code dueRound}
 * @param givenNow           what the creditor gives at signing
 * @param owed               what the debtor must pay in the due round
 * @param roundCreated       the round whose window the contract was proposed (and signed) in
 * @param dueRound           the round whose step 1.5 settles the obligation
 * @param cancelRequestedBy  the party that asked for mutual cancellation and waits for the other one, if any
 */
public record FormalContract(
        int id,
        int proposerSeat,
        int creditorSeat,
        int debtorSeat,
        ResourceBundle givenNow,
        ResourceBundle owed,
        int roundCreated,
        int dueRound,
        ContractStatus status,
        Optional<Integer> cancelRequestedBy) {

    public FormalContract {
        Objects.requireNonNull(givenNow);
        Objects.requireNonNull(owed);
        Objects.requireNonNull(status);
        Objects.requireNonNull(cancelRequestedBy);
    }

    public static FormalContract proposed(int id, int proposerSeat, int creditorSeat, int debtorSeat,
            ResourceBundle givenNow, ResourceBundle owed, int round, int dueRound) {
        return new FormalContract(id, proposerSeat, creditorSeat, debtorSeat, givenNow, owed, round, dueRound,
                ContractStatus.PROPOSED, Optional.empty());
    }

    public boolean isParty(int seat) {
        return seat == creditorSeat || seat == debtorSeat;
    }

    /** The party that did not propose: the only one who may sign. */
    public int signerSeat() {
        return proposerSeat == creditorSeat ? debtorSeat : creditorSeat;
    }

    /** This contract in {@code target} status. The only way a status changes; forbidden transitions throw. */
    public FormalContract movedTo(ContractStatus target) {
        if (!status.canMoveTo(target)) {
            throw new IllegalStateException("contract " + id + " cannot move from " + status + " to " + target);
        }
        return new FormalContract(id, proposerSeat, creditorSeat, debtorSeat, givenNow, owed, roundCreated, dueRound,
                target, cancelRequestedBy);
    }

    public FormalContract withCancelRequestedBy(int seat) {
        return new FormalContract(id, proposerSeat, creditorSeat, debtorSeat, givenNow, owed, roundCreated, dueRound,
                status, Optional.of(seat));
    }
}
