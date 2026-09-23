package citytrade.engine.command;

/** The recipient accepts an open offer; if both sides still have the resources, the trade happens at once. */
public record AcceptTrade(int seat, int offerId) implements GameCommand {
}
