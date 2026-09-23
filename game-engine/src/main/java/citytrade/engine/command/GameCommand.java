package citytrade.engine.command;

/** A player or system action. T04 adds the other commands and the central {@code GameEngine.apply}. */
public sealed interface GameCommand permits ChooseObjectives {
}
