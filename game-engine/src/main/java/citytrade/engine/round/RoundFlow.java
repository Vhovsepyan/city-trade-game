package citytrade.engine.round;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles {@code StartRound} and {@code ResolveRound}: phase checks, phase changes and running the
 * round steps in {@link RoundStep} order. This is the one place that encodes the round order.
 */
public final class RoundFlow {

    private final RoundStepHandler steps;

    public RoundFlow(RoundStepHandler steps) {
        this.steps = steps;
    }

    /** SETUP (all objectives chosen) or RESOLUTION -> AUTOMATIC -> WORLD -> WINDOW of the next round. */
    public GameResult startRound(GameState state, Ruleset ruleset) {
        if (state.phase() != GamePhase.SETUP && state.phase() != GamePhase.RESOLUTION) {
            return new GameResult.Rejected(RejectionCode.INVALID_PHASE,
                    "cannot start a round in phase " + state.phase());
        }
        if (state.phase() == GamePhase.SETUP && !state.allObjectivesChosen()) {
            return new GameResult.Rejected(RejectionCode.OBJECTIVES_NOT_CHOSEN,
                    "Round 1 starts only after every player has chosen objectives");
        }
        if (state.round() >= ruleset.roundCount()) {
            return new GameResult.Rejected(RejectionCode.INVALID_PHASE,
                    "round " + state.round() + " was the last round");
        }

        int round = state.round() + 1;
        List<DomainEvent> events = new ArrayList<>();
        GameState next = runPhase(state.withRoundAndPhase(round, GamePhase.AUTOMATIC), ruleset, events);
        next = runPhase(next.withRoundAndPhase(round, GamePhase.WORLD), ruleset, events);
        next = next.withRoundAndPhase(round, GamePhase.WINDOW);
        events.add(new DomainEvent.RoundStarted(round));
        return new GameResult.Accepted(next, events);
    }

    /** WINDOW -> RESOLUTION; after the last round -> FINISHED. */
    public GameResult resolveRound(GameState state, Ruleset ruleset) {
        if (state.phase() != GamePhase.WINDOW) {
            return new GameResult.Rejected(RejectionCode.INVALID_PHASE,
                    "cannot resolve a round in phase " + state.phase());
        }

        int round = state.round();
        List<DomainEvent> events = new ArrayList<>();
        GameState next = runPhase(state.withRoundAndPhase(round, GamePhase.RESOLUTION), ruleset, events);
        events.add(new DomainEvent.RoundResolved(round));
        if (round >= ruleset.roundCount()) {
            next = next.withRoundAndPhase(round, GamePhase.FINISHED);
            events.add(new DomainEvent.GameFinished());
        }
        return new GameResult.Accepted(next, events);
    }

    private GameState runPhase(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        GameState current = state;
        for (RoundStep step : RoundStep.of(state.phase())) {
            current = steps.apply(step, current, ruleset, events);
        }
        return current;
    }
}
