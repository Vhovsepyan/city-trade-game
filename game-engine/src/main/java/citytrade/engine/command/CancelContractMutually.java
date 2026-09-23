package citytrade.engine.command;

/**
 * One party agrees to cancel an active contract. When both parties have sent it, the contract is cancelled
 * without penalty (Concept 16). Possible only before the due round, i.e. while the contract is still active.
 */
public record CancelContractMutually(int seat, int contractId) implements GameCommand {
}
