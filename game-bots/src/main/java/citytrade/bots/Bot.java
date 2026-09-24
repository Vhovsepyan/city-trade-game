package citytrade.bots;

import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import java.util.Optional;

/**
 * A computer player for one seat. A bot is a pure function of the state: same state + ruleset = same
 * decision, so games between bots stay deterministic.
 */
public interface Bot {

    /** D5: the objectives this seat keeps before Round 1. */
    ChooseObjectives chooseObjectives(GameState state, int seat, Ruleset ruleset);

    /** The next command of this seat in the current window, or empty when the seat is done for this window. */
    Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset);
}
