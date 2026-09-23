package citytrade.engine.command;

/** The debtor breaks an active contract on purpose: nothing is delivered and the break penalties apply at once. */
public record BreakContract(int seat, int contractId) implements GameCommand {
}
