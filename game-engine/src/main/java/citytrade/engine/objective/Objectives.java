package citytrade.engine.objective;

import citytrade.engine.Resource;
import citytrade.engine.project.Projects;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.ContractStatus;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.ProjectContribution;
import citytrade.engine.state.PublicProject;
import citytrade.engine.state.TradeOffer;
import citytrade.engine.state.TradeOfferStatus;
import java.util.List;

/**
 * Hidden objectives, Numbers Sheet 15. The game checks them itself (Concept 28): from the game history kept
 * in the state (trade offers, contracts, projects, opportunities, crises) and from the player's
 * {@link citytrade.engine.state.ObjectiveProgress} for facts the state would otherwise forget.
 */
public final class Objectives {

    private Objectives() {
    }

    /**
     * Step 4.8 of every round, after all other resolution steps: remember each city's level and its smallest
     * F, E, M, T amount (Rapid Development, Balanced Stock).
     */
    public static GameState recordRoundEnd(GameState state) {
        GameState next = state;
        for (PlayerState player : state.players()) {
            int minimumStock = Integer.MAX_VALUE;
            for (Resource resource : Resource.values()) {
                minimumStock = Math.min(minimumStock, player.holdings().amountOf(resource));
            }
            next = next.withPlayer(player.withObjectiveProgress(
                    player.objectiveProgress().withRoundEnd(player.level(), minimumStock)));
        }
        return next;
    }

    /** The ids of {@code seat}'s kept objectives that are completed, in kept order. */
    public static List<String> completedObjectives(GameState state, int seat, Ruleset ruleset) {
        return state.player(seat).keptObjectives().stream()
                .filter(card -> isCompleted(card, state, seat, ruleset))
                .map(ObjectiveCard::id)
                .toList();
    }

    public static boolean isCompleted(ObjectiveCard card, GameState state, int seat, Ruleset ruleset) {
        PlayerState player = state.player(seat);
        return switch (card) {
            case ObjectiveCard.ActiveTrader c -> tradePartners(state, seat).size() == state.players().size() - 1;
            // A building can be built only once, so the count is the number of different buildings.
            case ObjectiveCard.DiversifiedEconomy c -> player.buildings().size() >= c.minBuildings();
            case ObjectiveCard.ProjectPartner c -> !state.projects().isEmpty() && state.projects().stream()
                    .allMatch(project -> Projects.qualifyingSeats(project, state, ruleset.projects()).contains(seat));
            // The Contracts Broken counter counts voluntary and automatic breaks.
            case ObjectiveCard.ContractPlayer c ->
                    player.contractsBroken() == 0 && fulfilledContracts(state, seat) >= c.minCompletedContracts();
            case ObjectiveCard.MarketIndependence c ->
                    player.objectiveProgress().marketBuyRounds().size() <= c.maxBuyRounds();
            case ObjectiveCard.RapidDevelopment c ->
                    player.objectiveProgress().levelAfter(c.byRound()).orElse(0) >= c.level();
            case ObjectiveCard.SteadyCity c -> !player.objectiveProgress().everStrained();
            case ObjectiveCard.OpportunityWinner c -> opportunitiesWon(state, seat) >= c.minWins();
            // Every crisis paid in full earns Crisis Prestige once.
            case ObjectiveCard.CrisisResponder c ->
                    player.eventParticipation().crisisPaidRounds().size() >= c.minCrisesPaid();
            // Only a finished Round Resolution counts, never the starting resources.
            case ObjectiveCard.BalancedStock c ->
                    !player.objectiveProgress().levelAfterRound().isEmpty()
                            && player.objectiveProgress().bestMinimumStock() >= c.minEachResource();
            case ObjectiveCard.PatientInvestor c -> contributionRounds(state, seat) >= c.minContributionRounds();
            case ObjectiveCard.BigDeal c -> completedTrades(state, seat).stream()
                    .anyMatch(offer -> givenBy(offer, seat) >= c.minResourcesGiven());
        };
    }

    /** Instant trades that were carried out (ACCEPTED) with {@code seat} on either side. */
    private static List<TradeOffer> completedTrades(GameState state, int seat) {
        return state.tradeOffers().stream()
                .filter(offer -> offer.status() == TradeOfferStatus.ACCEPTED)
                .filter(offer -> offer.proposerSeat() == seat || offer.recipientSeat() == seat)
                .toList();
    }

    private static List<Integer> tradePartners(GameState state, int seat) {
        return completedTrades(state, seat).stream()
                .map(offer -> offer.proposerSeat() == seat ? offer.recipientSeat() : offer.proposerSeat())
                .distinct()
                .toList();
    }

    /** Resource units (not Money) {@code seat} gave in the trade. */
    private static int givenBy(TradeOffer offer, int seat) {
        return (offer.proposerSeat() == seat ? offer.offered() : offer.requested()).resourceUnits();
    }

    /** Contracts with {@code seat} as a party whose obligation was paid in full. */
    private static long fulfilledContracts(GameState state, int seat) {
        return state.contracts().stream()
                .filter(contract -> contract.status() == ContractStatus.FULFILLED)
                .filter(contract -> contract.isParty(seat))
                .count();
    }

    private static long opportunitiesWon(GameState state, int seat) {
        return state.opportunities().stream()
                .filter(opportunity -> opportunity.winnerSeat().filter(winner -> winner == seat).isPresent())
                .count();
    }

    /** Different rounds in which {@code seat} contributed to any public project. */
    private static long contributionRounds(GameState state, int seat) {
        return state.projects().stream()
                .map(PublicProject::contributions)
                .flatMap(List::stream)
                .filter(contribution -> contribution.seat() == seat)
                .map(ProjectContribution::round)
                .distinct()
                .count();
    }
}
