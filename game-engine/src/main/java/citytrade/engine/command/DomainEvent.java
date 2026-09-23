package citytrade.engine.command;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.state.OfferCloseReason;
import java.util.Optional;

/** Something that happened in the game, produced by an accepted command. */
public sealed interface DomainEvent {

    /** The player kept their objectives. Which cards stays hidden, so the event carries only the seat. */
    record ObjectivesChosen(int seat) implements DomainEvent {
    }

    /** The player set their upkeep payment order (D1). The order itself is private. */
    record UpkeepPrioritySet(int seat) implements DomainEvent {
    }

    /** Step 1.4: the city paid {@code paid} as upkeep; {@code missing} resources could not be paid. */
    record UpkeepPaid(int seat, ResourceBundle paid, int missing) implements DomainEvent {
    }

    /** The player bought {@code quantity} units of {@code resource} from the market for {@code totalCost} Money. */
    record MarketBought(int seat, Resource resource, int quantity, int totalCost) implements DomainEvent {
    }

    /** The player sold {@code quantity} units of {@code resource} to the market for {@code totalValue} Money. */
    record MarketSold(int seat, Resource resource, int quantity, int totalValue) implements DomainEvent {
    }

    /** Step 4.6: the price step of {@code resource} moved (indexes into the ruleset's market steps). */
    record MarketPriceMoved(Resource resource, int fromStepIndex, int toStepIndex) implements DomainEvent {
    }

    /** The city went up to {@code newLevel} for {@code cost}. Its Prestige is added at resolution (step 4.4). */
    record CityUpgraded(int seat, int newLevel, ResourceBundle cost) implements DomainEvent {
    }

    /** The player built {@code buildingId} for {@code cost}. Its effect starts next round. */
    record BuildingBuilt(int seat, String buildingId, ResourceBundle cost) implements DomainEvent {
    }

    /** Step 4.4: {@code amount} visible Prestige was added to the player. */
    record PrestigeGained(int seat, int amount) implements DomainEvent {
    }

    /** A mandatory payment was not made in full; the Strained penalty applies next round. */
    record CityStrained(int seat) implements DomainEvent {
    }

    /** Step 4.5: resources above the storage limit were discarded. */
    record ExcessDiscarded(int seat, ResourceBundle discarded) implements DomainEvent {
    }

    /*
     * Trade offer events concern only proposer and recipient (D7); the view layer shows them to those two only.
     */

    /** A new offer: {@code proposerSeat} gives {@code offered} for {@code requested}; a counteroffer has a parent. */
    record TradeProposed(int offerId, Optional<Integer> parentOfferId, int proposerSeat, int recipientSeat,
            ResourceBundle offered, ResourceBundle requested) implements DomainEvent {
    }

    /** The recipient accepted and both sides were moved at once. */
    record TradeExecuted(int offerId, int proposerSeat, int recipientSeat, ResourceBundle offered,
            ResourceBundle requested) implements DomainEvent {
    }

    /** The recipient refused the offer, or countered it ({@code closeReason} COUNTEROFFER). */
    record TradeRejected(int offerId, Optional<OfferCloseReason> closeReason) implements DomainEvent {
    }

    /** The proposer withdrew the offer. */
    record TradeCancelled(int offerId) implements DomainEvent {
    }

    /** Step 4.1 (D8): the offer was still open when the window ended. */
    record TradeExpired(int offerId) implements DomainEvent {
    }

    /**
     * The recipient accepted, but a side no longer had the resources (Architecture 5.4): nothing was traded
     * and the offer is INVALID. {@code missingSeat} is the seat that lacked them (the proposer if both did).
     */
    record TradeInvalidated(int offerId, int missingSeat) implements DomainEvent {
    }

    /** The automatic and world update of {@code round} are done; the trade window is open. */
    record RoundStarted(int round) implements DomainEvent {
    }

    /** All resolution steps of {@code round} are done. */
    record RoundResolved(int round) implements DomainEvent {
    }

    /** The last round is resolved; no more commands are accepted. */
    record GameFinished() implements DomainEvent {
    }
}
