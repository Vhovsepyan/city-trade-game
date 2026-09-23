package citytrade.engine.ruleset;

/** One hidden objective card. The record kind selects the completion check; the fields hold its values. */
public sealed interface ObjectiveCard {

    String id();

    /** At least one instant trade with each opponent. */
    record ActiveTrader(String id) implements ObjectiveCard {
    }

    record DiversifiedEconomy(String id, int minBuildings) implements ObjectiveCard {
    }

    /** Qualifying contributor in every public project of the match. */
    record ProjectPartner(String id) implements ObjectiveCard {
    }

    /** Complete at least {@code minCompletedContracts} formal contracts and never break one. */
    record ContractPlayer(String id, int minCompletedContracts) implements ObjectiveCard {
    }

    /** Buy from the global market in no more than {@code maxBuyRounds} rounds. */
    record MarketIndependence(String id, int maxBuyRounds) implements ObjectiveCard {
    }

    /** Reach city level {@code level} by the end of round {@code byRound}. */
    record RapidDevelopment(String id, int level, int byRound) implements ObjectiveCard {
    }

    /** Never become Strained. */
    record SteadyCity(String id) implements ObjectiveCard {
    }

    record OpportunityWinner(String id, int minWins) implements ObjectiveCard {
    }

    /** Earn Crisis Prestige at least {@code minCrisesPaid} times. */
    record CrisisResponder(String id, int minCrisesPaid) implements ObjectiveCard {
    }

    /** After any Round Resolution, hold at least {@code minEachResource} of each of F, E, M, T. */
    record BalancedStock(String id, int minEachResource) implements ObjectiveCard {
    }

    /** Contribute to public projects in at least {@code minContributionRounds} different rounds. */
    record PatientInvestor(String id, int minContributionRounds) implements ObjectiveCard {
    }

    /** One instant trade in which the player gives at least {@code minResourcesGiven} resources (not Money). */
    record BigDeal(String id, int minResourcesGiven) implements ObjectiveCard {
    }
}
