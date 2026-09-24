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
import citytrade.engine.objective.FinalScoring;
import citytrade.engine.objective.Objectives;
import citytrade.engine.opportunity.Opportunities;
import citytrade.engine.project.Projects;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.trade.Trading;
import java.util.List;

/** The real round steps. The switch is exhaustive, so a new step cannot be forgotten. */
public final class RoundSteps implements RoundStepHandler {

    public static final RoundSteps INSTANCE = new RoundSteps();

    private RoundSteps() {
    }

    @Override
    public GameState apply(RoundStep step, GameState state, Ruleset ruleset, List<DomainEvent> events) {
        return switch (step) {
            case EVENT_BECOMES_ACTIVE -> EventSchedule.activate(state, events);
            case STRAINED_PENALTY -> Production.applyStrainedPenalty(state);
            case PRODUCTION -> Production.produce(state, ruleset, events);
            case UPKEEP -> Upkeep.pay(state, ruleset, events);
            case CONTRACT_OBLIGATIONS -> Contracts.settleDueObligations(state, ruleset, events);
            case CRISIS_PAYMENTS -> Crises.pay(state, ruleset, events);
            case NEXT_EVENT_WARNING -> EventSchedule.warnNextEvent(state, ruleset, events);
            // Unsigned contract proposals end with the window, like open trade offers.
            case TRADE_OFFERS_EXPIRE -> Contracts.expireProposals(Trading.expireOpenOffers(state, events), events);
            case NEW_PROJECT_OR_OPPORTUNITY -> Opportunities.reveal(Projects.open(state, events), ruleset, events);
            case BIDS_RESOLVE -> Opportunities.resolveBids(state, ruleset, events);
            case PROJECT_DEADLINE -> Projects.resolveDeadline(state, ruleset, events);
            // Numbers Sheet 4.4 order: levels, buildings, projects, crises, festival.
            case PRESTIGE -> EventOptions.awardPrestige(
                    Crises.awardPrestige(
                            Projects.awardPrestige(CityDevelopment.awardPrestige(state, ruleset, events), ruleset, events),
                            ruleset, events),
                    events);
            case STORAGE_LIMITS -> Storage.discardExcess(state, ruleset, events);
            case MARKET_PRICES_MOVE -> Market.movePrices(state, ruleset, events);
            case TEMPORARY_EVENT_EFFECTS_END -> EventSchedule.endTemporaryEffects(state, events);
            // Every round records what objectives need at the end of a Round Resolution; after the last
            // round the objectives are revealed and the game is scored.
            case FINAL_SCORING ->
                    FinalScoring.scoreIfLastRound(Objectives.recordRoundEnd(state), ruleset, events);
        };
    }
}
