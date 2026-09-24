package citytrade.server.view;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.state.GameState;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns domain events into per-seat {@link Notice}s (Architecture 6.5). Private events - resource amounts,
 * bids, trade offers, crisis choices - reach only the seat(s) involved; everything explicitly public
 * (Concept section 37, D6, D7) reaches every seat. Raw domain events are never sent to clients as-is: each
 * one is translated into a {@link NoticeEvent} that carries only the client-safe fields (in particular,
 * {@link DomainEvent.OpportunityWon#pricePaid()} never leaves this class); this is the only place that
 * decides who may see one and what they may see of it.
 *
 * <p>Events about a trade offer that do not carry both seats (rejected/cancelled/expired/invalidated) are
 * routed by looking the offer up in {@code resultingState}, which still holds it as history.
 */
public final class NoticeProjector {

    private NoticeProjector() {
    }

    public static List<Notice> project(GameState resultingState, List<DomainEvent> events) {
        List<Notice> notices = new ArrayList<>();
        int seatCount = resultingState.players().size();
        for (DomainEvent event : events) {
            List<Integer> recipients = recipients(event, resultingState, seatCount);
            if (recipients.isEmpty()) {
                continue;
            }
            NoticeEvent sanitized = sanitize(event);
            for (int seat : recipients) {
                notices.add(new Notice(seat, sanitized));
            }
        }
        return notices;
    }

    private static List<Integer> recipients(DomainEvent event, GameState state, int seatCount) {
        return switch (event) {
            // Private: resource amounts, own choices and secret actions (D6) - the acting seat only.
            case DomainEvent.ObjectivesChosen e -> List.of(e.seat());
            case DomainEvent.UpkeepPrioritySet e -> List.of(e.seat());
            case DomainEvent.ResourcesProduced e -> List.of(e.seat());
            case DomainEvent.UpkeepPaid e -> List.of(e.seat());
            case DomainEvent.MarketBought e -> List.of(e.seat());
            case DomainEvent.MarketSold e -> List.of(e.seat());
            case DomainEvent.CityStrained e -> List.of(e.seat());
            case DomainEvent.ExcessDiscarded e -> List.of(e.seat());
            case DomainEvent.CrisisPolicySet e -> List.of(e.seat());
            case DomainEvent.CrisisPaid e -> List.of(e.seat());
            case DomainEvent.CrisisNotPaid e -> List.of(e.seat());
            case DomainEvent.EventOptionUsed e -> List.of(e.seat());
            case DomainEvent.BidPlaced e -> List.of(e.seat());

            // Private: direct trade offers (D7) - proposer and recipient only.
            case DomainEvent.TradeProposed e -> List.of(e.proposerSeat(), e.recipientSeat());
            case DomainEvent.TradeExecuted e -> List.of(e.proposerSeat(), e.recipientSeat());
            case DomainEvent.TradeRejected e -> tradeOfferParties(state, e.offerId());
            case DomainEvent.TradeCancelled e -> tradeOfferParties(state, e.offerId());
            case DomainEvent.TradeExpired e -> tradeOfferParties(state, e.offerId());
            case DomainEvent.TradeInvalidated e -> tradeOfferParties(state, e.offerId());

            // Public: city level, buildings, visible Prestige, market prices (Concept section 37).
            case DomainEvent.MarketPriceMoved e -> allSeats(seatCount);
            case DomainEvent.CityUpgraded e -> allSeats(seatCount);
            case DomainEvent.BuildingBuilt e -> allSeats(seatCount);
            case DomainEvent.PrestigeGained e -> allSeats(seatCount);

            // Public: formal contracts are fully public (Concept section 37, Architecture 6.5).
            case DomainEvent.ContractProposed e -> allSeats(seatCount);
            case DomainEvent.ContractSigned e -> allSeats(seatCount);
            case DomainEvent.ContractInvalidated e -> allSeats(seatCount);
            case DomainEvent.ContractExpired e -> allSeats(seatCount);
            case DomainEvent.ContractFulfilled e -> allSeats(seatCount);
            case DomainEvent.ContractBroken e -> allSeats(seatCount);
            case DomainEvent.ContractCancelRequested e -> allSeats(seatCount);
            case DomainEvent.ContractCancelled e -> allSeats(seatCount);

            // Public: the active/warned event.
            case DomainEvent.EventActivated e -> allSeats(seatCount);
            case DomainEvent.EventWarned e -> allSeats(seatCount);
            case DomainEvent.EventEnded e -> allSeats(seatCount);

            // Public: public projects and their contributions.
            case DomainEvent.ProjectOpened e -> allSeats(seatCount);
            case DomainEvent.ProjectContributed e -> allSeats(seatCount);
            case DomainEvent.ProjectSucceeded e -> allSeats(seatCount);
            case DomainEvent.ProjectFailed e -> allSeats(seatCount);

            // Public: regional opportunity ownership (Concept section 37); the secret bid amount is not here.
            case DomainEvent.OpportunityRevealed e -> allSeats(seatCount);
            case DomainEvent.OpportunityWon e -> allSeats(seatCount);
            case DomainEvent.OpportunityNotWon e -> allSeats(seatCount);
            case DomainEvent.OpportunityRemoved e -> allSeats(seatCount);

            // Public: round progress and the end of the game.
            case DomainEvent.RoundStarted e -> allSeats(seatCount);
            case DomainEvent.RoundResolved e -> allSeats(seatCount);
            case DomainEvent.ObjectivesRevealed e -> allSeats(seatCount);
            case DomainEvent.FinalScoresRevealed e -> allSeats(seatCount);
            case DomainEvent.GameFinished e -> allSeats(seatCount);
        };
    }

    private static List<Integer> tradeOfferParties(GameState state, int offerId) {
        return state.tradeOffer(offerId)
                .map(offer -> List.of(offer.proposerSeat(), offer.recipientSeat()))
                .orElse(List.of());
    }

    private static List<Integer> allSeats(int seatCount) {
        List<Integer> seats = new ArrayList<>(seatCount);
        for (int seat = 0; seat < seatCount; seat++) {
            seats.add(seat);
        }
        return seats;
    }

    private static NoticeEvent sanitize(DomainEvent event) {
        return switch (event) {
            case DomainEvent.ObjectivesChosen e -> new NoticeEvent.ObjectivesChosen(e.seat());
            case DomainEvent.UpkeepPrioritySet e -> new NoticeEvent.UpkeepPrioritySet(e.seat());
            case DomainEvent.ResourcesProduced e -> new NoticeEvent.ResourcesProduced(e.seat(), e.produced());
            case DomainEvent.UpkeepPaid e -> new NoticeEvent.UpkeepPaid(e.seat(), e.paid(), e.missing());
            case DomainEvent.MarketBought e ->
                    new NoticeEvent.MarketBought(e.seat(), e.resource(), e.quantity(), e.totalCost());
            case DomainEvent.MarketSold e ->
                    new NoticeEvent.MarketSold(e.seat(), e.resource(), e.quantity(), e.totalValue());
            case DomainEvent.CityStrained e -> new NoticeEvent.CityStrained(e.seat());
            case DomainEvent.ExcessDiscarded e -> new NoticeEvent.ExcessDiscarded(e.seat(), e.discarded());
            case DomainEvent.CrisisPolicySet e -> new NoticeEvent.CrisisPolicySet(e.seat());
            case DomainEvent.CrisisPaid e -> new NoticeEvent.CrisisPaid(e.seat(), e.eventId(), e.paid());
            case DomainEvent.CrisisNotPaid e -> new NoticeEvent.CrisisNotPaid(e.seat(), e.eventId(), e.skipped());
            case DomainEvent.EventOptionUsed e -> new NoticeEvent.EventOptionUsed(e.seat(), e.eventId(), e.cost(),
                    e.chosenResource().orElse(null));
            case DomainEvent.BidPlaced e -> new NoticeEvent.BidPlaced(e.seat(), e.opportunityId());

            case DomainEvent.TradeProposed e -> new NoticeEvent.TradeProposed(e.offerId(),
                    e.parentOfferId().orElse(null), e.proposerSeat(), e.recipientSeat(), e.offered(),
                    e.requested());
            case DomainEvent.TradeExecuted e -> new NoticeEvent.TradeExecuted(e.offerId(), e.proposerSeat(),
                    e.recipientSeat(), e.offered(), e.requested());
            case DomainEvent.TradeRejected e ->
                    new NoticeEvent.TradeRejected(e.offerId(), e.closeReason().orElse(null));
            case DomainEvent.TradeCancelled e -> new NoticeEvent.TradeCancelled(e.offerId());
            case DomainEvent.TradeExpired e -> new NoticeEvent.TradeExpired(e.offerId());
            case DomainEvent.TradeInvalidated e -> new NoticeEvent.TradeInvalidated(e.offerId(), e.missingSeat());

            case DomainEvent.MarketPriceMoved e ->
                    new NoticeEvent.MarketPriceMoved(e.resource(), e.fromStepIndex(), e.toStepIndex());
            case DomainEvent.CityUpgraded e -> new NoticeEvent.CityUpgraded(e.seat(), e.newLevel(), e.cost());
            case DomainEvent.BuildingBuilt e -> new NoticeEvent.BuildingBuilt(e.seat(), e.buildingId(), e.cost());
            case DomainEvent.PrestigeGained e -> new NoticeEvent.PrestigeGained(e.seat(), e.amount());

            case DomainEvent.ContractProposed e -> new NoticeEvent.ContractProposed(e.contractId(),
                    e.proposerSeat(), e.creditorSeat(), e.debtorSeat(), e.givenNow(), e.owed(), e.dueRound());
            case DomainEvent.ContractSigned e -> new NoticeEvent.ContractSigned(e.contractId(), e.creditorSeat(),
                    e.debtorSeat(), e.givenNow());
            case DomainEvent.ContractInvalidated e ->
                    new NoticeEvent.ContractInvalidated(e.contractId(), e.creditorSeat());
            case DomainEvent.ContractExpired e -> new NoticeEvent.ContractExpired(e.contractId());
            case DomainEvent.ContractFulfilled e -> new NoticeEvent.ContractFulfilled(e.contractId(),
                    e.debtorSeat(), e.creditorSeat(), e.delivered());
            case DomainEvent.ContractBroken e -> new NoticeEvent.ContractBroken(e.contractId(), e.debtorSeat(),
                    e.creditorSeat(), e.voluntary(), e.delivered(), e.compensationOwed(), e.compensationPaid(),
                    e.prestigeLost());
            case DomainEvent.ContractCancelRequested e ->
                    new NoticeEvent.ContractCancelRequested(e.contractId(), e.seat());
            case DomainEvent.ContractCancelled e -> new NoticeEvent.ContractCancelled(e.contractId());

            case DomainEvent.EventActivated e -> new NoticeEvent.EventActivated(e.round(), e.eventId());
            case DomainEvent.EventWarned e -> new NoticeEvent.EventWarned(e.round(), e.eventId());
            case DomainEvent.EventEnded e -> new NoticeEvent.EventEnded(e.eventId());

            case DomainEvent.ProjectOpened e -> new NoticeEvent.ProjectOpened(e.projectId(), e.deadlineRound());
            case DomainEvent.ProjectContributed e ->
                    new NoticeEvent.ProjectContributed(e.seat(), e.projectId(), e.given(), e.points());
            case DomainEvent.ProjectSucceeded e -> new NoticeEvent.ProjectSucceeded(e.projectId(),
                    e.qualifyingSeats(), e.productionReward());
            case DomainEvent.ProjectFailed e -> new NoticeEvent.ProjectFailed(e.projectId(), e.lost());

            case DomainEvent.OpportunityRevealed e -> new NoticeEvent.OpportunityRevealed(e.opportunityId());
            // pricePaid is deliberately dropped: the winning bid stays secret, even from the winner's notice.
            case DomainEvent.OpportunityWon e ->
                    new NoticeEvent.OpportunityWon(e.opportunityId(), e.winnerSeat(), e.productionReward());
            case DomainEvent.OpportunityNotWon e -> new NoticeEvent.OpportunityNotWon(e.opportunityId(), e.tied());
            case DomainEvent.OpportunityRemoved e -> new NoticeEvent.OpportunityRemoved(e.opportunityId());

            case DomainEvent.RoundStarted e -> new NoticeEvent.RoundStarted(e.round());
            case DomainEvent.RoundResolved e -> new NoticeEvent.RoundResolved(e.round());
            case DomainEvent.ObjectivesRevealed e -> new NoticeEvent.ObjectivesRevealed(e.seat(),
                    e.keptObjectives(), e.completedObjectives(), e.hiddenPrestige());
            case DomainEvent.FinalScoresRevealed e -> new NoticeEvent.FinalScoresRevealed(e.result());
            case DomainEvent.GameFinished e -> new NoticeEvent.GameFinished();
        };
    }
}
