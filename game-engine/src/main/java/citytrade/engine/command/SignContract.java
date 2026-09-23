package citytrade.engine.command;

/** The party that did not propose signs; if the creditor still has the bundle, it moves to the debtor at once. */
public record SignContract(int seat, int contractId) implements GameCommand {
}
