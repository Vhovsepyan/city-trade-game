package citytrade.engine.command;

/** A player or internal action. {@code GameEngine.apply} is the single entry point. */
public sealed interface GameCommand permits ChooseObjectives, StartRound, ResolveRound {
}
