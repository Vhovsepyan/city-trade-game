package citytrade.engine.command;

/**
 * Raise the city one level, paying the next level's cost (Numbers Sheet 6-7). Only during the trade
 * window and only once per round. New production and upkeep apply from the next round.
 */
public record UpgradeCity(int seat) implements GameCommand {
}
