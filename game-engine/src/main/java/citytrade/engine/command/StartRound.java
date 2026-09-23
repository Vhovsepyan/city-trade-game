package citytrade.engine.command;

/**
 * Internal command: starts the next round and runs the automatic and world update
 * (Numbers Sheet "ROUND ORDER" sections 1 and 2), then opens the trade window.
 */
public record StartRound() implements GameCommand {
}
