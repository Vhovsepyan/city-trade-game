package citytrade.engine.event;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.EventCard;
import java.util.Optional;

/**
 * The temporary effects of the active event card (Numbers Sheet 14). Other rules ask here instead of
 * checking event kinds themselves, so each effect is defined once. All effects end in step 4.7.
 */
public final class EventEffects {

    private EventEffects() {
    }

    /** Crises are the events every city at the crisis level must pay in step 2.1. */
    public static boolean isCrisis(EventCard card) {
        return card instanceof EventCard.ResourceCrisis || card instanceof EventCard.NonSpecialtyCrisis;
    }

    /** Steps the market price of {@code resource} moves this round, on top of the current step. */
    public static int marketStepChange(Optional<EventCard> active, Resource resource) {
        return switch (active.orElse(null)) {
            case EventCard.ResourceCrisis c when c.resource() == resource -> c.marketStepChange();
            case EventCard.BuildingCostDiscount d when d.resource() == resource -> d.marketStepChange();
            case null, default -> 0;
        };
    }

    /** Money the player pays to buy 1 unit, given the price of the (event-shifted) step. Never below 0. */
    public static int buyCost(Optional<EventCard> active, int stepBuyCost) {
        if (active.orElse(null) instanceof EventCard.MarketPriceShift shift) {
            return Math.max(0, stepBuyCost + shift.buyCostChange());
        }
        return stepBuyCost;
    }

    /** Money the player gets for 1 unit, given the value of the (event-shifted) step. */
    public static int sellValue(Optional<EventCard> active, int stepSellValue) {
        if (active.orElse(null) instanceof EventCard.MarketPriceShift shift) {
            return Math.max(shift.minSellValue(), stepSellValue + shift.sellValueChange());
        }
        return stepSellValue;
    }

    /** A building's cost this round: a building cost discount lowers one resource (min 0). */
    public static ResourceBundle buildingCost(Optional<EventCard> active, ResourceBundle cost) {
        if (active.orElse(null) instanceof EventCard.BuildingCostDiscount d) {
            return discounted(cost, d.resource(), d.discount());
        }
        return cost;
    }

    /** A city upgrade's cost this round: an upgrade cost discount lowers one resource (min 0). */
    public static ResourceBundle upgradeCost(Optional<EventCard> active, ResourceBundle cost) {
        if (active.orElse(null) instanceof EventCard.UpgradeCostDiscount d) {
            return discounted(cost, d.resource(), d.discount());
        }
        return cost;
    }

    /** Step 1.3: {@code produced} changed by the event of this round (no Money income, extra production). */
    public static ResourceBundle production(Optional<EventCard> active, ResourceBundle produced) {
        return switch (active.orElse(null)) {
            case EventCard.NoMoneyIncome _ -> produced.withMoney(0);
            case EventCard.ProductionBoost boost -> produced.plus(boost.production());
            case null, default -> produced;
        };
    }

    private static ResourceBundle discounted(ResourceBundle cost, Resource resource, int discount) {
        return cost.with(resource, Math.max(0, cost.amountOf(resource) - discount));
    }
}
