package citytrade.server.view;

import citytrade.engine.ruleset.EventCard;
import citytrade.engine.state.FinalResult;
import citytrade.engine.state.FormalContract;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.EventWarning;
import citytrade.engine.state.MarketPrices;
import citytrade.engine.state.PublicProject;
import citytrade.engine.state.TradeOffer;
import citytrade.server.room.RoomStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The client-safe view of the game for exactly one seat (Architecture 6.5, D6, D7). Built only by
 * {@link ViewProjector}, a pure function of {@code GameState} and {@link RoomViewContext}. Never carries the
 * full {@code GameState}, another seat's holdings, objectives, bids or trade offers it is not a party to.
 *
 * <p>Optional-ish fields are nullable rather than {@code Optional}, so the record serializes to JSON directly.
 *
 * @param seat            the seat this view was built for
 * @param rulesetVersion  the ruleset this game runs on
 * @param round           the current round
 * @param phase           where the game is in the round order
 * @param phaseEndsAt     the deadline of the current phase; null if none is running
 * @param roomStatus      the room's lifecycle status
 * @param players         public information about every seat, including this one (D6)
 * @param own             information visible only to {@code seat} (D6)
 * @param market          current market prices
 * @param activeEvent     this round's event; null if none
 * @param eventWarning    the warned event for the next event round; null if none
 * @param projects        public projects that have been opened at least once, with their contributions
 * @param opportunities   revealed regional opportunities, with only {@code seat}'s own bid
 * @param contracts       every formal contract; contracts are fully public (Concept section 37)
 * @param tradeOffers     trade offers where {@code seat} is proposer or recipient, only (D7)
 * @param finalResult     final scores and winners; null before the game ends
 */
public record PlayerGameView(
        int seat,
        String rulesetVersion,
        int round,
        GamePhase phase,
        Instant phaseEndsAt,
        RoomStatus roomStatus,
        List<PlayerPublicView> players,
        OwnView own,
        MarketPrices market,
        EventCard activeEvent,
        EventWarning eventWarning,
        List<PublicProject> projects,
        List<OpportunityView> opportunities,
        List<FormalContract> contracts,
        List<TradeOffer> tradeOffers,
        FinalResult finalResult) {

    public PlayerGameView {
        Objects.requireNonNull(rulesetVersion);
        Objects.requireNonNull(phase);
        Objects.requireNonNull(roomStatus);
        Objects.requireNonNull(own);
        Objects.requireNonNull(market);
        players = List.copyOf(players);
        projects = List.copyOf(projects);
        opportunities = List.copyOf(opportunities);
        contracts = List.copyOf(contracts);
        tradeOffers = List.copyOf(tradeOffers);
    }
}
