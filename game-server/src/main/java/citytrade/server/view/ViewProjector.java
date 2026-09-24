package citytrade.server.view;

import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.ProjectStatus;
import citytrade.engine.state.PublicProject;
import citytrade.engine.state.RegionalOpportunity;
import citytrade.engine.state.TradeOffer;
import java.util.List;

/**
 * Builds the client-safe {@link PlayerGameView} of one seat (Architecture 6.5). A pure function of its
 * inputs: never mutates {@code state} or {@code room}, and the same inputs always produce an equal view.
 *
 * <p>Never send the full {@code GameState}. This is the only place that decides what one seat may see.
 */
public final class ViewProjector {

    private ViewProjector() {
    }

    public static PlayerGameView project(GameState state, RoomViewContext room, int seat) {
        if (!state.hasSeat(seat)) {
            throw new IllegalArgumentException("no such seat: " + seat);
        }
        PlayerState me = state.player(seat);

        List<PlayerPublicView> players = state.players().stream()
                .map(player -> new PlayerPublicView(player.seat(), player.city(), player.level(),
                        player.buildings(), player.prestige(), player.contractsBroken(),
                        room.isReady(player.seat()), room.isDisconnected(player.seat())))
                .toList();

        OwnView own = new OwnView(me.holdings(), state.reservedMoney(seat), me.dealtObjectives(),
                me.keptObjectives(), me.eventParticipation().crisisPolicy(), me.upkeepPriority(),
                me.strained(), me.strainedPenaltyActive());

        // Only projects that have been opened at least once (step 2.3): an UPCOMING card is not announced yet.
        List<PublicProject> projects = state.projects().stream()
                .filter(project -> project.status() != ProjectStatus.UPCOMING)
                .toList();

        List<OpportunityView> opportunities = state.opportunities().stream()
                .map(opportunity -> toView(opportunity, seat))
                .toList();

        // D7: only offers where this seat is proposer or recipient, whatever their status.
        List<TradeOffer> tradeOffers = state.tradeOffers().stream()
                .filter(offer -> offer.proposerSeat() == seat || offer.recipientSeat() == seat)
                .toList();

        return new PlayerGameView(
                seat,
                state.rulesetVersion(),
                state.round(),
                state.phase(),
                room.phaseEndsAt(),
                room.status(),
                players,
                own,
                state.market(),
                state.activeEvent().orElse(null),
                state.eventWarning().orElse(null),
                projects,
                opportunities,
                state.contracts(),
                tradeOffers,
                state.finalResult().orElse(null));
    }

    private static OpportunityView toView(RegionalOpportunity opportunity, int seat) {
        return new OpportunityView(opportunity.card(), opportunity.appearedRound(), opportunity.status(),
                opportunity.winnerSeat().orElse(null), opportunity.bidOf(seat));
    }
}
