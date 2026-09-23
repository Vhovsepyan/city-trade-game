package citytrade.engine.round;

import citytrade.engine.city.CityDevelopment;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.contract.Contracts;
import citytrade.engine.economy.Production;
import citytrade.engine.economy.Storage;
import citytrade.engine.economy.Upkeep;
import citytrade.engine.event.Crises;
import citytrade.engine.event.EventOptions;
import citytrade.engine.event.EventSchedule;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.trade.Trading;
import java.util.List;

/**
 * The real round steps. Steps that return the state unchanged are filled in by later tasks
 * (T11 projects, T12 bids, T13 scoring). The switch is exhaustive, so a new step cannot be forgotten.
 */
public final class RoundSteps implements RoundStepHandler {

    public static final RoundSteps INSTANCE = new RoundSteps();

    private RoundSteps() {
    }

    @Override
    public GameState apply(RoundStep step, GameState state, Ruleset ruleset, List<DomainEvent> events) {
        return switch (step) {
            case EVENT_BECOMES_ACTIVE -> EventSchedule.activate(state, events);
            case STRAINED_PENALTY -> Production.applyStrainedPenalty(state);
            case PRODUCTION -> Production.produce(state, ruleset);
            case UPKEEP -> Upkeep.pay(state, ruleset, events);
            case CONTRACT_OBLIGATIONS -> Contracts.settleDueObligations(state, ruleset, events);
            case CRISIS_PAYMENTS -> Crises.pay(state, ruleset, events);
            case NEXT_EVENT_WARNING -> EventSchedule.warnNextEvent(state, ruleset, events);
            // Unsigned contract proposals end with the window, like open trade offers.
            case TRADE_OFFERS_EXPIRE -> Contracts.expireProposals(Trading.expireOpenOffers(state, events), events);
            case PRESTIGE -> EventOptions.awardPrestige(
                    Crises.awardPrestige(CityDevelopment.awardPrestige(state, ruleset, events), ruleset, events),
                    events);
            case STORAGE_LIMITS -> Storage.discardExcess(state, ruleset, events);
            case MARKET_PRICES_MOVE -> Market.movePrices(state, ruleset, events);
            case TEMPORARY_EVENT_EFFECTS_END -> EventSchedule.endTemporaryEffects(state, events);
            case NEW_PROJECT_OR_OPPORTUNITY,
                 BIDS_RESOLVE,
                 PROJECT_DEADLINE,
                 FINAL_SCORING -> state;
        };
    }
}
