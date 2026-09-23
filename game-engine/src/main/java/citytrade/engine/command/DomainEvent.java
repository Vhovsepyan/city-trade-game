package citytrade.engine.command;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;

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

    /** A mandatory payment was not made in full; the Strained penalty applies next round. */
    record CityStrained(int seat) implements DomainEvent {
    }

    /** Step 4.5: resources above the storage limit were discarded. */
    record ExcessDiscarded(int seat, ResourceBundle discarded) implements DomainEvent {
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
