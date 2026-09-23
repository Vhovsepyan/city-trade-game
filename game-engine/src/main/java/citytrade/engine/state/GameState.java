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
 * @param projects         the drawn project cards, one per project window in order (A, B)
 * @param opportunityDeck  face-down opportunity cards, top card first
 * @param tradeOffers      every direct trade offer of the game in creation order; closed ones stay as history
 * @param nextOfferId      the id the next trade offer gets
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
        List<ProjectCard> projects,
        List<OpportunityCard> opportunityDeck,
        List<TradeOffer> tradeOffers,
        int nextOfferId) {

    public GameState {
        Objects.requireNonNull(phase);
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
        return new GameState(rulesetVersion, round, phase, random, updated, market, eventDeck, eventWarning, projects,
                opportunityDeck, tradeOffers, nextOfferId);
    }

    public GameState withRoundAndPhase(int newRound, GamePhase newPhase) {
        return new GameState(rulesetVersion, newRound, newPhase, random, players, market, eventDeck, eventWarning,
                projects, opportunityDeck, tradeOffers, nextOfferId);
    }

    public GameState withMarket(MarketPrices newMarket) {
        return new GameState(rulesetVersion, round, phase, random, players, newMarket, eventDeck, eventWarning,
                projects, opportunityDeck, tradeOffers, nextOfferId);
    }

    public Optional<TradeOffer> tradeOffer(int offerId) {
        return tradeOffers.stream().filter(offer -> offer.id() == offerId).findFirst();
    }

    /** The state with the stored offer of the same id replaced by {@code offer}. */
    public GameState withTradeOffer(TradeOffer offer) {
        List<TradeOffer> updated = new ArrayList<>(tradeOffers);
        updated.replaceAll(existing -> existing.id() == offer.id() ? offer : existing);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                projects, opportunityDeck, updated, nextOfferId);
    }

    /** The state with a new offer, which must carry {@link #nextOfferId()}; the id counter moves on by one. */
    public GameState withNewTradeOffer(TradeOffer offer) {
        if (offer.id() != nextOfferId) {
            throw new IllegalArgumentException("new offer must have id " + nextOfferId + ", but has " + offer.id());
        }
        List<TradeOffer> updated = new ArrayList<>(tradeOffers);
        updated.add(offer);
        return new GameState(rulesetVersion, round, phase, random, players, market, eventDeck, eventWarning,
                projects, opportunityDeck, updated, nextOfferId + 1);
    }

    /** D5: Round 1 may not start before every player has chosen objectives. */
    public boolean allObjectivesChosen() {
        return players.stream().allMatch(PlayerState::hasChosenObjectives);
    }
}
