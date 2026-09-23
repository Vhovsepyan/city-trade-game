package citytrade.engine.ruleset;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;

/**
 * One event card. The record kind selects the engine behavior; the fields hold its values.
 * All effects last only for the event round.
 */
public sealed interface EventCard {

    String id();

    /**
     * Crisis: every city at or above the crisis level pays {@code amount} of {@code resource}
     * or becomes Strained. The market price of {@code resource} moves by {@code marketStepChange} steps.
     */
    record ResourceCrisis(String id, Resource resource, int amount, int marketStepChange) implements EventCard {
    }

    /** Neutral crisis: pay {@code differentResources} different non-specialty resources (1 each) or become Strained. */
    record NonSpecialtyCrisis(String id, int differentResources) implements EventCard {
    }

    /** Every building costs {@code discount} fewer of {@code resource} (min 0); its market price moves by steps. */
    record BuildingCostDiscount(String id, Resource resource, int discount, int marketStepChange) implements EventCard {
    }

    /** City upgrades cost {@code discount} fewer of {@code resource} (min 0). */
    record UpgradeCostDiscount(String id, Resource resource, int discount) implements EventCard {
    }

    /** No Money income this round. */
    record NoMoneyIncome(String id) implements EventCard {
    }

    /**
     * Market prices shift for all resources: buy cost changes by {@code buyCostChange},
     * sell value changes by {@code sellValueChange} but not below {@code minSellValue}.
     */
    record MarketPriceShift(String id, int buyCostChange, int sellValueChange, int minSellValue) implements EventCard {
    }

    /** Optional, once per player: pay {@code cost} during the window for {@code prestige}. */
    record PrestigePurchase(String id, ResourceBundle cost, int prestige) implements EventCard {
    }

    /** Optional, once per player: pay {@code cost} for permanent extra production of one chosen non-specialty resource (D4). */
    record ProductionPurchase(String id, ResourceBundle cost, int chosenNonSpecialtyProduction) implements EventCard {
    }

    /** Every city gets this extra production in the event round. */
    record ProductionBoost(String id, ResourceBundle production) implements EventCard {
    }
}
