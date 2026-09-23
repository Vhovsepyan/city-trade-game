package citytrade.engine.command;

/** The recipient refuses an open offer. */
public record RejectTrade(int seat, int offerId) implements GameCommand {
}
