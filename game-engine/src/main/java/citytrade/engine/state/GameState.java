package citytrade.engine.state;

import citytrade.engine.GameRandom;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.OpportunityRules.OpportunityCard;
import citytrade.engine.ruleset.ProjectRules.ProjectCard;
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
 * @param projects         the drawn project cards, one per project window in order (A, B)
 * @param opportunityDeck  face-down opportunity cards, top card first
 * @param tradeOffers      every direct trade offer of the game in creation order; closed ones stay as history
 * @param nextOfferId      the id the next trade offer gets
 * @param contracts        every formal contract of the game in creation order; finished ones stay as history
 * @param nextContractId   the id the next formal contract gets
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
        List<ProjectCard> projects,
        List<OpportunityCard> opportunityDeck,
        List<TradeOffer> tradeOffers,
        int nextOfferId,
        List<FormalContract> contracts,
        int nextContractId) {

    public GameState {
        Objects.requireNonNull(phase);
        Objects.requireNonNull(eventWarning);
        Objects.requireNonNull(activeEvent);
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
                activeEvent, projects, opportunityDeck, tradeOffers, nextOfferId, contracts, nextContractId);
    }

    public GameState withRoundAndPhase(int newRound, GamePhase newPhase) {
        return new GameState(rulesetVersion, newRound, newPhase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, tradeOffers, nextOfferId, contracts, nextContractId);
    }

    public GameState withMarket(MarketPrices newMarket) {
        return new GameState(rulesetVersion, round, phase, random, players, newMarket, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, tradeOffers, nextOfferId, contracts, nextContractId);
    }

    /** The state with a new event deck, warning and active event (the three always change together). */
    public GameState withEvents(List<EventCard> newDeck, Optional<EventWarning> newWarning,
            Optional<EventCard> newActiveEvent) {
        return new GameState(rulesetVersion, round, phase, random, players, market, newDeck, newWarning,
                newActiveEvent, projects, opportunityDeck, tradeOffers, nextOfferId, contracts, nextContractId);
    }

    public Optional<TradeOffer> tradeOffer(int offerId) {
        return tradeOffers.stream().filter(offer -> offer.id() == offerId).findFirst();
    }

    /** The state with the stored offer of the same id replaced by {@code offer}. */
    public GameState withTradeOffer(TradeOffer offer) {
        List<TradeOffer> updated = new ArrayList<>(tradeOffers);
        updated.replaceAll(existing -> existing.id() == offer.id() ? offer : existing);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, updated, nextOfferId, contracts, nextContractId);
    }

    /** The state with a new offer, which must carry {@link #nextOfferId()}; the id counter moves on by one. */
    public GameState withNewTradeOffer(TradeOffer offer) {
        if (offer.id() != nextOfferId) {
            throw new IllegalArgumentException("new offer must have id " + nextOfferId + ", but has " + offer.id());
        }
        List<TradeOffer> updated = new ArrayList<>(tradeOffers);
        updated.add(offer);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, updated, nextOfferId + 1, contracts, nextContractId);
    }

    public Optional<FormalContract> contract(int contractId) {
        return contracts.stream().filter(contract -> contract.id() == contractId).findFirst();
    }

    /** The state with the stored contract of the same id replaced by {@code contract}. */
    public GameState withContract(FormalContract contract) {
        List<FormalContract> updated = new ArrayList<>(contracts);
        updated.replaceAll(existing -> existing.id() == contract.id() ? contract : existing);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                activeEvent, projects, opportunityDeck, tradeOffers, nextOfferId, updated, nextContractId);
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
                activeEvent, projects, opportunityDeck, tradeOffers, nextOfferId, updated, nextContractId + 1);
    }

    /** D5: Round 1 may not start before every player has chosen objectives. */
    public boolean allObjectivesChosen() {
        return players.stream().allMatch(PlayerState::hasChosenObjectives);
    }
}
