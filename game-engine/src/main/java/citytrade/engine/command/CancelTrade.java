package citytrade.engine.command;

/** The proposer withdraws their open offer. */
public record CancelTrade(int seat, int offerId) implements GameCommand {
}
