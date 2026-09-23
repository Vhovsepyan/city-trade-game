package citytrade.engine.round;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import java.util.List;

/**
 * The real round steps. Each step is still empty; later tasks fill them in
 * (T05 production/upkeep/storage, T06 market, T08 offers, T09 contracts, T10 events,
 * T11 projects, T12 bids, T13 scoring). The switch is exhaustive, so a new step cannot be forgotten.
 */
public final class RoundSteps implements RoundStepHandler {

    public static final RoundSteps INSTANCE = new RoundSteps();

    private RoundSteps() {
    }

    @Override
    public GameState apply(RoundStep step, GameState state, Ruleset ruleset, List<DomainEvent> events) {
        return switch (step) {
            case EVENT_BECOMES_ACTIVE,
                 STRAINED_PENALTY,
                 PRODUCTION,
                 UPKEEP,
                 CONTRACT_OBLIGATIONS,
                 CRISIS_PAYMENTS,
                 NEXT_EVENT_WARNING,
                 NEW_PROJECT_OR_OPPORTUNITY,
                 TRADE_OFFERS_EXPIRE,
                 BIDS_RESOLVE,
                 PROJECT_DEADLINE,
                 PRESTIGE,
                 STORAGE_LIMITS,
                 MARKET_PRICES_MOVE,
                 TEMPORARY_EVENT_EFFECTS_END,
                 FINAL_SCORING -> state;
        };
    }
}
