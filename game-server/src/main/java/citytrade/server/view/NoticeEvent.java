package citytrade.server.view;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.state.FinalResult;
import citytrade.engine.state.OfferCloseReason;
import java.util.List;

/**
 * The client-safe form of a {@link citytrade.engine.command.DomainEvent}, produced by
 * {@link NoticeProjector}. One record per event kind that is ever sent in a {@link Notice}, holding exactly
 * the fields a client may see. Raw domain events are never sent to clients as-is (Architecture 6.5): in
 * particular {@link OpportunityWon} has no {@code pricePaid} field, since the winning bid stays secret even
 * from the winner's own notice (the same rule as {@link OpportunityView}, which has no such field either).
 *
 * <p>Optional-ish fields are nullable rather than {@code Optional}, so the record serializes to JSON directly
 * (same convention as {@link PlayerGameView}).
 */
public sealed interface NoticeEvent {

    record ObjectivesChosen(int seat) implements NoticeEvent {
    }

    record UpkeepPrioritySet(int seat) implements NoticeEvent {
    }

    record ResourcesProduced(int seat, ResourceBundle produced) implements NoticeEvent {
    }

    record UpkeepPaid(int seat, ResourceBundle paid, int missing) implements NoticeEvent {
    }

    record MarketBought(int seat, Resource resource, int quantity, int totalCost) implements NoticeEvent {
    }

    record MarketSold(int seat, Resource resource, int quantity, int totalValue) implements NoticeEvent {
    }

    record MarketPriceMoved(Resource resource, int fromStepIndex, int toStepIndex) implements NoticeEvent {
    }

    record CityUpgraded(int seat, int newLevel, ResourceBundle cost) implements NoticeEvent {
    }

    record BuildingBuilt(int seat, String buildingId, ResourceBundle cost) implements NoticeEvent {
    }

    record PrestigeGained(int seat, int amount) implements NoticeEvent {
    }

    record CityStrained(int seat) implements NoticeEvent {
    }

    record ExcessDiscarded(int seat, ResourceBundle discarded) implements NoticeEvent {
    }

    record TradeProposed(int offerId, Integer parentOfferId, int proposerSeat, int recipientSeat,
            ResourceBundle offered, ResourceBundle requested) implements NoticeEvent {
    }

    record TradeExecuted(int offerId, int proposerSeat, int recipientSeat, ResourceBundle offered,
            ResourceBundle requested) implements NoticeEvent {
    }

    record TradeRejected(int offerId, OfferCloseReason closeReason) implements NoticeEvent {
    }

    record TradeCancelled(int offerId) implements NoticeEvent {
    }

    record TradeExpired(int offerId) implements NoticeEvent {
    }

    record TradeInvalidated(int offerId, int missingSeat) implements NoticeEvent {
    }

    record ContractProposed(int contractId, int proposerSeat, int creditorSeat, int debtorSeat,
            ResourceBundle givenNow, ResourceBundle owed, int dueRound) implements NoticeEvent {
    }

    record ContractSigned(int contractId, int creditorSeat, int debtorSeat, ResourceBundle givenNow)
            implements NoticeEvent {
    }

    record ContractInvalidated(int contractId, int creditorSeat) implements NoticeEvent {
    }

    record ContractExpired(int contractId) implements NoticeEvent {
    }

    record ContractFulfilled(int contractId, int debtorSeat, int creditorSeat, ResourceBundle delivered)
            implements NoticeEvent {
    }

    record ContractBroken(int contractId, int debtorSeat, int creditorSeat, boolean voluntary,
            ResourceBundle delivered, int compensationOwed, int compensationPaid, int prestigeLost)
            implements NoticeEvent {
    }

    record ContractCancelRequested(int contractId, int seat) implements NoticeEvent {
    }

    record ContractCancelled(int contractId) implements NoticeEvent {
    }

    record EventActivated(int round, String eventId) implements NoticeEvent {
    }

    record EventWarned(int round, String eventId) implements NoticeEvent {
    }

    record EventEnded(String eventId) implements NoticeEvent {
    }

    record CrisisPolicySet(int seat) implements NoticeEvent {
    }

    record CrisisPaid(int seat, String eventId, ResourceBundle paid) implements NoticeEvent {
    }

    record CrisisNotPaid(int seat, String eventId, boolean skipped) implements NoticeEvent {
    }

    record EventOptionUsed(int seat, String eventId, ResourceBundle cost, Resource chosenResource)
            implements NoticeEvent {
    }

    record ProjectOpened(String projectId, int deadlineRound) implements NoticeEvent {
    }

    record ProjectContributed(int seat, String projectId, ResourceBundle given, int points) implements NoticeEvent {
    }

    record ProjectSucceeded(String projectId, List<Integer> qualifyingSeats, ResourceBundle productionReward)
            implements NoticeEvent {

        public ProjectSucceeded {
            qualifyingSeats = List.copyOf(qualifyingSeats);
        }
    }

    record ProjectFailed(String projectId, ResourceBundle lost) implements NoticeEvent {
    }

    record OpportunityRevealed(String opportunityId) implements NoticeEvent {
    }

    record BidPlaced(int seat, String opportunityId) implements NoticeEvent {
    }

    /** {@code pricePaid} is deliberately absent: the winning bid stays secret (Concept section 37). */
    record OpportunityWon(String opportunityId, int winnerSeat, ResourceBundle productionReward)
            implements NoticeEvent {
    }

    record OpportunityNotWon(String opportunityId, boolean tied) implements NoticeEvent {
    }

    record OpportunityRemoved(String opportunityId) implements NoticeEvent {
    }

    record RoundStarted(int round) implements NoticeEvent {
    }

    record RoundResolved(int round) implements NoticeEvent {
    }

    record ObjectivesRevealed(int seat, List<String> keptObjectives, List<String> completedObjectives,
            int hiddenPrestige) implements NoticeEvent {

        public ObjectivesRevealed {
            keptObjectives = List.copyOf(keptObjectives);
            completedObjectives = List.copyOf(completedObjectives);
        }
    }

    record FinalScoresRevealed(FinalResult result) implements NoticeEvent {
    }

    record GameFinished() implements NoticeEvent {
    }
}
