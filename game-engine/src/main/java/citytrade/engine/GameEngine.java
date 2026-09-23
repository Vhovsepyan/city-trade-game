package citytrade.engine;

import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.command.StartRound;
import citytrade.engine.economy.UpkeepPriorityChoice;
import citytrade.engine.round.RoundFlow;
import citytrade.engine.round.RoundSteps;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.ObjectiveChoice;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;

/**
 * The single entry point of the rules: old state + command -> new state and events, or a rejection
 * with the state unchanged. Pure function: no I/O, no clock, randomness only from {@link GameState#random()}.
 */
public final class GameEngine {

    private static final RoundFlow ROUND_FLOW = new RoundFlow(RoundSteps.INSTANCE);

    private GameEngine() {
    }

    public static GameResult apply(GameState state, GameCommand command, Ruleset ruleset) {
        return switch (command) {
            case ChooseObjectives choose -> state.phase() == GamePhase.SETUP
                    ? ObjectiveChoice.apply(state, choose, ruleset)
                    : invalidPhase(command, state);
            // Upkeep is paid in StartRound, so the order is set before Round 1 or in a window for later rounds.
            case SetUpkeepPriority priority -> state.phase() == GamePhase.SETUP || state.phase() == GamePhase.WINDOW
                    ? UpkeepPriorityChoice.apply(state, priority)
                    : invalidPhase(command, state);
            case StartRound _ -> ROUND_FLOW.startRound(state, ruleset);
            case ResolveRound _ -> ROUND_FLOW.resolveRound(state, ruleset);
        };
    }

    private static GameResult invalidPhase(GameCommand command, GameState state) {
        return new GameResult.Rejected(RejectionCode.INVALID_PHASE,
                command.getClass().getSimpleName() + " is not allowed in phase " + state.phase());
    }
}
