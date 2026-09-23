package citytrade.engine.round;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import java.util.List;

/** Carries out one round step. {@link RoundFlow} decides WHEN a step runs; the handler decides WHAT it does. */
public interface RoundStepHandler {

    /**
     * @param events collects the events of the running command, in order
     * @return the state after this step
     */
    GameState apply(RoundStep step, GameState state, Ruleset ruleset, List<DomainEvent> events);
}
