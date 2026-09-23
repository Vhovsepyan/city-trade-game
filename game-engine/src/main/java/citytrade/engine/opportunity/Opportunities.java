package citytrade.engine.opportunity;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.OpportunityStatus;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.RegionalOpportunity;
import citytrade.engine.state.SecretBid;
import java.util.List;
import java.util.Optional;

/**
 * Regional opportunities and secret bids, Numbers Sheet 17 and 20, Concept 24-25. A card is revealed in step 2.3
 * of its appearance round, takes bids in the window, and the bids are resolved in step 4.2. A card that nobody
 * wins stays open and takes new bids in the next windows. The winner's production reward starts the next round.
 */
public final class Opportunities {

    private Opportunities() {
    }

    /** Step 2.3: in an appearance round the top card of the opportunity deck is revealed. */
    public static GameState reveal(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        if (!ruleset.opportunities().appearanceRounds().contains(state.round()) || state.opportunityDeck().isEmpty()) {
            return state;
        }
        RegionalOpportunity revealed = RegionalOpportunity.revealed(state.opportunityDeck().getFirst(), state.round());
        events.add(new DomainEvent.OpportunityRevealed(revealed.id()));
        return state.withRevealedOpportunity(revealed);
    }

    /**
     * A bid replaces the player's earlier bid on the same card; 0 passes. All active bids of a player together
     * may not be more than their Money, so every bid can always be paid at resolution.
     */
    public static GameResult placeBid(GameState state, PlaceBid command) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        Optional<RegionalOpportunity> found = state.opportunity(command.opportunityId());
        if (found.isEmpty()) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_OPPORTUNITY, "no revealed opportunity "
                    + command.opportunityId());
        }
        RegionalOpportunity opportunity = found.get();
        if (opportunity.status() != OpportunityStatus.OPEN) {
            return new GameResult.Rejected(RejectionCode.OPPORTUNITY_NOT_OPEN,
                    opportunity.id() + " is " + opportunity.status() + ", not OPEN");
        }
        if (command.amount() < 0) {
            return new GameResult.Rejected(RejectionCode.INVALID_QUANTITY, "bid must not be negative");
        }
        // Money not reserved by the player's other bids; the old bid on this card is replaced, so it counts as free.
        long free = (long) state.spendableHoldings(command.seat()).money() + opportunity.bidOf(command.seat());
        if (command.amount() > free) {
            return new GameResult.Rejected(RejectionCode.INSUFFICIENT_MONEY,
                    "bid " + command.amount() + " is more than the " + free + " unreserved Money");
        }
        GameState next = state.withOpportunity(opportunity.withBid(command.seat(), command.amount()));
        return new GameResult.Accepted(next, List.of(new DomainEvent.BidPlaced(command.seat(), opportunity.id())));
    }

    /**
     * Step 4.2: on every open card the single highest bid wins; the winner pays it and gets the card's production
     * reward from the next round on. A tied highest bid means nobody wins and nobody pays; the card stays open.
     * All other bids are released, so no Money stays reserved after this step. In the last round a card that
     * nobody wins is removed.
     */
    public static GameState resolveBids(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        boolean lastRound = state.round() == ruleset.roundCount();
        GameState next = state;
        for (RegionalOpportunity opportunity : state.opportunities()) {
            if (opportunity.status() != OpportunityStatus.OPEN) {
                continue;
            }
            int highest = opportunity.bids().stream().mapToInt(SecretBid::amount).max().orElse(0);
            List<SecretBid> leaders = opportunity.bids().stream().filter(bid -> bid.amount() == highest).toList();
            if (leaders.size() != 1) {
                next = next.withOpportunity(lastRound ? opportunity.removed() : opportunity.withoutBids());
                events.add(new DomainEvent.OpportunityNotWon(opportunity.id(), leaders.size() > 1));
                if (lastRound) {
                    events.add(new DomainEvent.OpportunityRemoved(opportunity.id()));
                }
                continue;
            }
            int winnerSeat = leaders.getFirst().seat();
            PlayerState winner = next.player(winnerSeat);
            if (winner.holdings().money() < highest) {
                // Bids never exceed Money and reserved Money cannot be spent, so this is a broken invariant.
                throw new IllegalStateException("seat " + winnerSeat + " cannot pay its reserved bid " + highest);
            }
            next = next.withOpportunity(opportunity.wonBy(winnerSeat))
                    .withPlayer(winner.withHoldings(winner.holdings().withMoney(winner.holdings().money() - highest))
                            .withExtraProduction(winner.extraProduction().plus(opportunity.card().productionReward())));
            events.add(new DomainEvent.OpportunityWon(opportunity.id(), winnerSeat, highest,
                    opportunity.card().productionReward()));
        }
        return next;
    }
}
