package citytrade.engine.round;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.economy.Production;
import citytrade.engine.economy.Storage;
import citytrade.engine.economy.Upkeep;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import java.util.List;

/**
 * The real round steps. Steps that return the state unchanged are filled in by later tasks
 * (T07 level/building Prestige, T08 offers, T09 contracts, T10 events,
 * T11 projects, T12 bids, T13 scoring). The switch is exhaustive, so a new step cannot be forgotten.
 */
public final class RoundSteps implements RoundStepHandler {

    public static final RoundSteps INSTANCE = new RoundSteps();

    private RoundSteps() {
    }

    @Override
    public GameState apply(RoundStep step, GameState state, Ruleset ruleset, List<DomainEvent> events) {
        return switch (step) {
            case STRAINED_PENALTY -> Production.applyStrainedPenalty(state);
            case PRODUCTION -> Production.produce(state, ruleset);
            case UPKEEP -> Upkeep.pay(state, ruleset, events);
            case STORAGE_LIMITS -> Storage.discardExcess(state, ruleset, events);
            case MARKET_PRICES_MOVE -> Market.movePrices(state, ruleset, events);
            case EVENT_BECOMES_ACTIVE,
                 CONTRACT_OBLIGATIONS,
                 CRISIS_PAYMENTS,
                 NEXT_EVENT_WARNING,
                 NEW_PROJECT_OR_OPPORTUNITY,
                 TRADE_OFFERS_EXPIRE,
                 BIDS_RESOLVE,
                 PROJECT_DEADLINE,
                 PRESTIGE,
                 TEMPORARY_EVENT_EFFECTS_END,
                 FINAL_SCORING -> state;
        };
    }
}
