package citytrade.engine.state;

import citytrade.engine.GameRandom;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.OpportunityRules.OpportunityCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The whole game as an immutable value: old state + command -> new state.
 *
 * @param rulesetVersion   version of the ruleset this game was created with
 * @param round            current round, 0 before Round 1 starts
 * @param phase            where the game is in the round order
 * @param random           the engine-owned random source; replaced after every draw
 * @param players          players by seat (index = seat)
 * @param eventDeck        face-down event cards, top card first
 * @param eventWarning     the revealed event card for the next event round, if any
 * @param activeEvent      the event card of this round, from step 1.1 until step 4.7
 * @param projects         the drawn public projects, one per project window in order (A, B)
 * @param opportunityDeck  face-down opportunity cards, top card first
 * @param opportunities    the revealed opportunity cards in order of appearance, with their secret bids
 * @param tradeOffers      every direct trade offer of the game in creation order; closed ones stay as history
 * @param nextOfferId      the id the next trade offer gets
 * @param contracts        every formal contract of the game in creation order; finished ones stay as history
 * @param nextContractId   the id the next formal contract gets
 * @param finalResult      final scores and winners; set in step 4.8 of the last round, empty before
 */
public record GameState(
        String rulesetVersion,
        int round,
        GamePhase phase,
        GameRandom random,
        List<PlayerState> players,
        MarketPrices market,
        List<EventCard> eventDeck,
        Optional<EventWarning> eventWarning,
        Optional<EventCard> activeEvent,
        List<PublicProject> projects,
        List<OpportunityCard> opportunityDeck,
        List<RegionalOpportunity> opportunities,
        List<TradeOffer> tradeOffers,
        int nextOfferId,
        List<FormalContract> contracts,
        int nextContractId,
        Optional<FinalResult> finalResult) {

    public GameState {
        Objects.requireNonNull(phase);
        Objects.requireNonNull(eventWarning);
        Objects.requireNonNull(activeEvent);
        Objects.requireNonNull(finalResult);
        if (round < 0) {
            throw new IllegalArgumentException("round must not be negative: " + round);
        }
        players = List.copyOf(players);
        for (int seat = 0; seat < players.size(); seat++) {
            if (players.get(seat).seat() != seat) {
                throw new IllegalArgumentException("player at index " + seat + " has seat " + players.get(seat).seat());
            }
        }
        eventDeck = List.copyOf(eventDeck);
        projects = List.copyOf(projects);
        opportunityDeck = List.copyOf(opportunityDeck);
        opportunities = List.copyOf(opportunities);
        tradeOffers = List.copyOf(tradeOffers);
        contracts = List.copyOf(contracts);
    }

    public boolean hasSeat(int seat) {
        return seat >= 0 && seat < players.size();
    }

    public PlayerState player(int seat) {
        return players.get(seat);
    }

    public GameState withPlayer(PlayerState player) {
        List<PlayerState> updated = new ArrayList<>(players);
        updated.set(player.seat(), player);
        return new GameState(rulesetVersion, round, phase, random, updated, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, opportunities, tradeOffers, nextOfferId, contracts,
                nextContractId, finalResult);
    }

    public GameState withRoundAndPhase(int newRound, GamePhase newPhase) {
        return new GameState(rulesetVersion, newRound, newPhase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, opportunities, tradeOffers, nextOfferId, contracts,
                nextContractId, finalResult);
    }

    public GameState withMarket(MarketPrices newMarket) {
        return new GameState(rulesetVersion, round, phase, random, players, newMarket, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, opportunities, tradeOffers, nextOfferId, contracts,
                nextContractId, finalResult);
    }

    /** The state with a new event deck, warning and active event (the three always change together). */
    public GameState withEvents(List<EventCard> newDeck, Optional<EventWarning> newWarning,
            Optional<EventCard> newActiveEvent) {
        return new GameState(rulesetVersion, round, phase, random, players, market, newDeck, newWarning,
                newActiveEvent, projects, opportunityDeck, opportunities, tradeOffers, nextOfferId, contracts,
                nextContractId, finalResult);
    }

    public Optional<PublicProject> project(String projectId) {
        return projects.stream().filter(project -> project.id().equals(projectId)).findFirst();
    }

    /** The state with the stored project of the same id replaced by {@code project}. */
    public GameState withProject(PublicProject project) {
        List<PublicProject> updated = new ArrayList<>(projects);
        updated.replaceAll(existing -> existing.id().equals(project.id()) ? project : existing);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, updated, opportunityDeck, opportunities, tradeOffers, nextOfferId, contracts,
                nextContractId, finalResult);
    }

    public Optional<RegionalOpportunity> opportunity(String opportunityId) {
        return opportunities.stream().filter(opportunity -> opportunity.id().equals(opportunityId)).findFirst();
    }

    /** The state with the stored opportunity of the same id replaced by {@code opportunity}. */
    public GameState withOpportunity(RegionalOpportunity opportunity) {
        List<RegionalOpportunity> updated = new ArrayList<>(opportunities);
        updated.replaceAll(existing -> existing.id().equals(opportunity.id()) ? opportunity : existing);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, updated, tradeOffers, nextOfferId, contracts, nextContractId,
                finalResult);
    }

    /** The state with the top card of the opportunity deck revealed as {@code opportunity}. */
    public GameState withRevealedOpportunity(RegionalOpportunity opportunity) {
        if (opportunityDeck.isEmpty() || !opportunityDeck.getFirst().equals(opportunity.card())) {
            throw new IllegalArgumentException("revealed opportunity must be the top card of the deck");
        }
        List<RegionalOpportunity> updated = new ArrayList<>(opportunities);
        updated.add(opportunity);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck.subList(1, opportunityDeck.size()), updated, tradeOffers,
                nextOfferId, contracts, nextContractId, finalResult);
    }

    /** Numbers Sheet 17: Money of {@code seat} reserved by its active bids on open opportunities. */
    public int reservedMoney(int seat) {
        return opportunities.stream()
                .filter(opportunity -> opportunity.status() == OpportunityStatus.OPEN)
                .mapToInt(opportunity -> opportunity.bidOf(seat))
                .reduce(0, Math::addExact);
    }

    /**
     * What {@code seat} may spend now: its holdings without the Money reserved by bids. Every command that
     * gives away Money checks against this, so reserved Money is never spent on anything else.
     */
    public ResourceBundle spendableHoldings(int seat) {
        ResourceBundle holdings = player(seat).holdings();
        return holdings.withMoney(holdings.money() - reservedMoney(seat));
    }

    public Optional<TradeOffer> tradeOffer(int offerId) {
        return tradeOffers.stream().filter(offer -> offer.id() == offerId).findFirst();
    }

    /** The state with the stored offer of the same id replaced by {@code offer}. */
    public GameState withTradeOffer(TradeOffer offer) {
        List<TradeOffer> updated = new ArrayList<>(tradeOffers);
        updated.replaceAll(existing -> existing.id() == offer.id() ? offer : existing);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, opportunities, updated, nextOfferId, contracts,
                nextContractId, finalResult);
    }

    /** The state with a new offer, which must carry {@link #nextOfferId()}; the id counter moves on by one. */
    public GameState withNewTradeOffer(TradeOffer offer) {
        if (offer.id() != nextOfferId) {
            throw new IllegalArgumentException("new offer must have id " + nextOfferId + ", but has " + offer.id());
        }
        List<TradeOffer> updated = new ArrayList<>(tradeOffers);
        updated.add(offer);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, opportunities, updated, nextOfferId + 1, contracts,
                nextContractId, finalResult);
    }

    public Optional<FormalContract> contract(int contractId) {
        return contracts.stream().filter(contract -> contract.id() == contractId).findFirst();
    }

    /** The state with the stored contract of the same id replaced by {@code contract}. */
    public GameState withContract(FormalContract contract) {
        List<FormalContract> updated = new ArrayList<>(contracts);
        updated.replaceAll(existing -> existing.id() == contract.id() ? contract : existing);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, opportunities, tradeOffers, nextOfferId, updated,
                nextContractId, finalResult);
    }

    /** The state with a new contract, which must carry {@link #nextContractId()}; the id counter moves on by one. */
    public GameState withNewContract(FormalContract contract) {
        if (contract.id() != nextContractId) {
            throw new IllegalArgumentException(
                    "new contract must have id " + nextContractId + ", but has " + contract.id());
        }
        List<FormalContract> updated = new ArrayList<>(contracts);
        updated.add(contract);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, opportunities, tradeOffers, nextOfferId, updated,
                nextContractId + 1, finalResult);
    }

    public GameState withFinalResult(FinalResult result) {
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, opportunities, tradeOffers, nextOfferId, contracts,
                nextContractId, Optional.of(result));
    }

    /** D5: Round 1 may not start before every player has chosen objectives. */
    public boolean allObjectivesChosen() {
        return players.stream().allMatch(PlayerState::hasChosenObjectives);
    }
}
