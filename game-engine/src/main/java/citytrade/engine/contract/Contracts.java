package citytrade.engine.contract;

import citytrade.engine.ResourceBundle;
import citytrade.engine.command.BreakContract;
import citytrade.engine.command.CancelContractMutually;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ProposeContract;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.SignContract;
import citytrade.engine.ruleset.ContractRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.ContractStatus;
import citytrade.engine.state.FormalContract;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Formal contracts (Concept 16-17, Numbers Sheet 13, D3, D10). One party proposes, the other signs; signing moves
 * the creditor's bundle to the debtor at once. The debtor's obligation is paid automatically in step 1.5 of the due
 * round, or the contract breaks. A broken contract costs the debtor Money compensation to the creditor, Prestige
 * and one point on the public Contracts Broken counter.
 */
public final class Contracts {

    private Contracts() {
    }

    public static GameResult propose(GameState state, ProposeContract command, Ruleset ruleset) {
        Optional<GameResult.Rejected> invalid = checkProposal(state, command, ruleset);
        if (invalid.isPresent()) {
            return invalid.get();
        }
        FormalContract contract = FormalContract.proposed(state.nextContractId(), command.seat(),
                command.creditorSeat(), command.debtorSeat(), command.givenNow(), command.owed(), state.round(),
                command.dueRound());
        return new GameResult.Accepted(state.withNewContract(contract), List.of(new DomainEvent.ContractProposed(
                contract.id(), contract.proposerSeat(), contract.creditorSeat(), contract.debtorSeat(),
                contract.givenNow(), contract.owed(), contract.dueRound())));
    }

    public static GameResult sign(GameState state, SignContract command) {
        Optional<GameResult.Rejected> invalid = checkContract(state, command.seat(), command.contractId());
        if (invalid.isPresent()) {
            return invalid.get();
        }
        FormalContract contract = state.contract(command.contractId()).orElseThrow();
        if (contract.signerSeat() != command.seat()) {
            return new GameResult.Rejected(RejectionCode.NOT_CONTRACT_SIGNER, "only seat " + contract.signerSeat()
                    + " may sign contract " + contract.id());
        }
        if (contract.status() != ContractStatus.PROPOSED) {
            return new GameResult.Rejected(RejectionCode.CONTRACT_NOT_PROPOSED,
                    "contract " + contract.id() + " is " + contract.status());
        }
        PlayerState creditor = state.player(contract.creditorSeat());
        if (!creditor.holdings().covers(contract.givenNow())) {
            // Like an instant trade (Architecture 5.4): nothing moves and the proposal is closed for good. The
            // contract changes, so this is an accepted command with an event, not a Rejected result.
            GameState next = state.withContract(contract.movedTo(ContractStatus.INVALID));
            return new GameResult.Accepted(next,
                    List.of(new DomainEvent.ContractInvalidated(contract.id(), creditor.seat())));
        }
        PlayerState debtor = state.player(contract.debtorSeat());
        GameState next = state
                .withPlayer(creditor.withHoldings(creditor.holdings().minus(contract.givenNow())))
                .withPlayer(debtor.withHoldings(debtor.holdings().plus(contract.givenNow())))
                .withContract(contract.movedTo(ContractStatus.ACTIVE));
        return new GameResult.Accepted(next, List.of(new DomainEvent.ContractSigned(contract.id(),
                contract.creditorSeat(), contract.debtorSeat(), contract.givenNow())));
    }

    /** Voluntary break (D14): the debtor delivers nothing, so compensation is owed for the whole obligation. */
    public static GameResult breakVoluntarily(GameState state, BreakContract command, Ruleset ruleset) {
        Optional<GameResult.Rejected> invalid = checkContract(state, command.seat(), command.contractId());
        if (invalid.isPresent()) {
            return invalid.get();
        }
        FormalContract contract = state.contract(command.contractId()).orElseThrow();
        if (contract.debtorSeat() != command.seat()) {
            return new GameResult.Rejected(RejectionCode.NOT_CONTRACT_DEBTOR, "only the debtor (seat "
                    + contract.debtorSeat() + ") may break contract " + contract.id());
        }
        if (contract.status() != ContractStatus.ACTIVE) {
            return new GameResult.Rejected(RejectionCode.CONTRACT_NOT_ACTIVE,
                    "contract " + contract.id() + " is " + contract.status());
        }
        List<DomainEvent> events = new ArrayList<>();
        GameState next = settleBreak(state, contract, ResourceBundle.EMPTY, true, ruleset.contracts(), events);
        return new GameResult.Accepted(next, events);
    }

    /**
     * Mutual cancellation: the first party's agreement is stored, the second one cancels the contract without
     * penalty. Active contracts are always before their due round in the window (step 1.5 settles them first).
     */
    public static GameResult cancelMutually(GameState state, CancelContractMutually command) {
        Optional<GameResult.Rejected> invalid = checkContract(state, command.seat(), command.contractId());
        if (invalid.isPresent()) {
            return invalid.get();
        }
        FormalContract contract = state.contract(command.contractId()).orElseThrow();
        if (!contract.isParty(command.seat())) {
            return new GameResult.Rejected(RejectionCode.NOT_CONTRACT_PARTY,
                    "seat " + command.seat() + " is not a party of contract " + contract.id());
        }
        if (contract.status() != ContractStatus.ACTIVE) {
            return new GameResult.Rejected(RejectionCode.CONTRACT_NOT_ACTIVE,
                    "contract " + contract.id() + " is " + contract.status());
        }
        if (contract.cancelRequestedBy().equals(Optional.of(command.seat()))) {
            return new GameResult.Rejected(RejectionCode.CANCEL_ALREADY_REQUESTED,
                    "seat " + command.seat() + " already agreed to cancel contract " + contract.id());
        }
        if (contract.cancelRequestedBy().isPresent()) {
            GameState next = state.withContract(contract.movedTo(ContractStatus.CANCELLED));
            return new GameResult.Accepted(next, List.of(new DomainEvent.ContractCancelled(contract.id())));
        }
        GameState next = state.withContract(contract.withCancelRequestedBy(command.seat()));
        return new GameResult.Accepted(next,
                List.of(new DomainEvent.ContractCancelRequested(contract.id(), command.seat())));
    }

    /**
     * Step 1.5 (D3): every active contract due this round is paid in full if the debtor has the whole obligation;
     * otherwise the debtor delivers what they have of it and the contract breaks. D15: contracts are settled in
     * creation order (oldest first), each one in full if possible; a later contract gets what is left.
     */
    public static GameState settleDueObligations(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        GameState next = state;
        for (FormalContract due : state.contracts()) {
            if (due.status() != ContractStatus.ACTIVE || due.dueRound() != state.round()) {
                continue;
            }
            PlayerState debtor = next.player(due.debtorSeat());
            if (debtor.holdings().covers(due.owed())) {
                PlayerState creditor = next.player(due.creditorSeat());
                next = next
                        .withPlayer(debtor.withHoldings(debtor.holdings().minus(due.owed())))
                        .withPlayer(creditor.withHoldings(creditor.holdings().plus(due.owed())))
                        .withContract(due.movedTo(ContractStatus.FULFILLED));
                events.add(new DomainEvent.ContractFulfilled(due.id(), due.debtorSeat(), due.creditorSeat(),
                        due.owed()));
            } else {
                ResourceBundle delivered = due.owed().cappedBy(debtor.holdings());
                next = settleBreak(next, due, delivered, false, ruleset.contracts(), events);
            }
        }
        return next;
    }

    /**
     * Step 4.1 (D16): proposals not signed in this window expire, like open trade offers (D8). So a contract is
     * always signed in the round of its proposal, and "now" and the max duration count from that round.
     */
    public static GameState expireProposals(GameState state, List<DomainEvent> events) {
        GameState next = state;
        for (FormalContract contract : state.contracts()) {
            if (contract.status() == ContractStatus.PROPOSED) {
                next = next.withContract(contract.movedTo(ContractStatus.EXPIRED));
                events.add(new DomainEvent.ContractExpired(contract.id()));
            }
        }
        return next;
    }

    /**
     * Numbers Sheet 13: the debtor hands over {@code delivered} and owes the creditor Money compensation for every
     * unpaid unit. What they cannot pay costs Prestige (1 per started block of unpaid Money; the creditor does not
     * get it). Every break also costs a fixed Prestige penalty and adds one to the public Contracts Broken counter.
     * Prestige may go below zero (D13).
     */
    private static GameState settleBreak(GameState state, FormalContract contract, ResourceBundle delivered,
            boolean voluntary, ContractRules rules, List<DomainEvent> events) {
        PlayerState debtor = state.player(contract.debtorSeat());
        PlayerState creditor = state.player(contract.creditorSeat());
        ResourceBundle unpaid = contract.owed().minus(delivered);
        int compensationOwed = compensationFor(unpaid, rules);
        int moneyLeft = debtor.holdings().money() - delivered.money();
        int compensationPaid = Math.min(compensationOwed, moneyLeft);
        int prestigeLost = Math.addExact(Math.ceilDiv(compensationOwed - compensationPaid,
                rules.unpaidCompensationMoneyPerPrestige()), rules.breakPrestigePenalty());
        ResourceBundle transfer = delivered.plus(ResourceBundle.EMPTY.withMoney(compensationPaid));
        GameState next = state
                .withPlayer(debtor.withHoldings(debtor.holdings().minus(transfer))
                        .withPrestige(Math.subtractExact(debtor.prestige(), prestigeLost))
                        .withContractsBroken(debtor.contractsBroken() + 1))
                .withPlayer(creditor.withHoldings(creditor.holdings().plus(transfer)))
                .withContract(contract.movedTo(ContractStatus.BROKEN));
        events.add(new DomainEvent.ContractBroken(contract.id(), contract.debtorSeat(), contract.creditorSeat(),
                voluntary, delivered, compensationOwed, compensationPaid, prestigeLost));
        return next;
    }

    /** Money compensation for {@code unpaid}. Throws on int overflow; proposals are checked so that it cannot. */
    private static int compensationFor(ResourceBundle unpaid, ContractRules rules) {
        return Math.addExact(Math.multiplyExact(unpaid.resourceUnits(), rules.compensationPerUnpaidResource()),
                Math.multiplyExact(unpaid.money(), rules.compensationPerUnpaidMoney()));
    }

    /**
     * D10: the creditor gives a non-empty bundle now, the debtor owes a non-empty bundle later. The due round is
     * after this round, at most {@code maxDurationRounds} later and never after the last round, so a contract in
     * the last round is impossible (Numbers Sheet 20). A creditor who proposes must own the bundle; a debtor who
     * proposes cannot see the creditor's holdings (D6), so that is checked only when the creditor signs.
     */
    private static Optional<GameResult.Rejected> checkProposal(GameState state, ProposeContract command,
            Ruleset ruleset) {
        for (int seat : new int[] {command.seat(), command.creditorSeat(), command.debtorSeat()}) {
            if (!state.hasSeat(seat)) {
                return rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + seat);
            }
        }
        if (command.creditorSeat() == command.debtorSeat()) {
            return rejected(RejectionCode.CONTRACT_WITH_SELF,
                    "seat " + command.creditorSeat() + " cannot make a contract with itself");
        }
        if (command.seat() != command.creditorSeat() && command.seat() != command.debtorSeat()) {
            return rejected(RejectionCode.NOT_CONTRACT_PARTY,
                    "seat " + command.seat() + " must be the creditor or the debtor of its proposal");
        }
        if (command.givenNow().hasNegativeAmount() || command.owed().hasNegativeAmount()) {
            return rejected(RejectionCode.INVALID_QUANTITY, "amounts must not be negative: given now "
                    + command.givenNow() + ", owed " + command.owed());
        }
        try {
            // The largest possible break (nothing delivered) must be computable, or a settlement could wrap around.
            compensationFor(command.owed(), ruleset.contracts());
        } catch (ArithmeticException tooLarge) {
            return rejected(RejectionCode.INVALID_QUANTITY, "owed " + command.owed() + " is too large");
        }
        if (command.givenNow().isEmpty() || command.owed().isEmpty()) {
            return rejected(RejectionCode.EMPTY_CONTRACT, "a contract needs a bundle given now and a bundle owed");
        }
        int lastRound = ruleset.roundCount();
        if (state.round() >= lastRound || command.dueRound() > lastRound) {
            return rejected(RejectionCode.CONTRACT_AFTER_GAME_END, "due round " + command.dueRound()
                    + " is not allowed in round " + state.round() + ": no obligation after round " + lastRound);
        }
        int latestDueRound = state.round() + ruleset.contracts().maxDurationRounds();
        if (command.dueRound() <= state.round() || command.dueRound() > latestDueRound) {
            return rejected(RejectionCode.INVALID_DUE_ROUND, "due round must be " + (state.round() + 1) + "-"
                    + latestDueRound + ", was " + command.dueRound());
        }
        PlayerState proposer = state.player(command.seat());
        if (command.seat() == command.creditorSeat() && !proposer.holdings().covers(command.givenNow())) {
            return rejected(RejectionCode.INSUFFICIENT_RESOURCES,
                    "gives " + command.givenNow() + ", has " + proposer.holdings());
        }
        return Optional.empty();
    }

    private static Optional<GameResult.Rejected> checkContract(GameState state, int seat, int contractId) {
        if (!state.hasSeat(seat)) {
            return rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + seat);
        }
        if (state.contract(contractId).isEmpty()) {
            return rejected(RejectionCode.UNKNOWN_CONTRACT, "no contract " + contractId);
        }
        return Optional.empty();
    }

    private static Optional<GameResult.Rejected> rejected(RejectionCode code, String detail) {
        return Optional.of(new GameResult.Rejected(code, detail));
    }
}
