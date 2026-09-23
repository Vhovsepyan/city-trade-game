package citytrade.engine.command;

/**
 * Internal command: closes the trade window and runs the round resolution
 * (Numbers Sheet "ROUND ORDER" section 4) in its fixed step order.
 */
public record ResolveRound() implements GameCommand {
}
