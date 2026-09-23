package citytrade.engine.command;

/** A player or internal action. {@code GameEngine.apply} is the single entry point. */
public sealed interface GameCommand permits ChooseObjectives, SetUpkeepPriority, BuyFromMarket, SellToMarket, StartRound,
        ResolveRound {
}
