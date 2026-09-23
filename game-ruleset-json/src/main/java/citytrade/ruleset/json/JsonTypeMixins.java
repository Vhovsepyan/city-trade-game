package citytrade.ruleset.json;

import citytrade.engine.ruleset.BuildingEffect;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.ObjectiveCard;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Maps the JSON "type" names to the engine's sealed record kinds.
 * Mixins keep Jackson annotations out of game-engine.
 */
final class JsonTypeMixins {

    private JsonTypeMixins() {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = BuildingEffect.StorageBonus.class, name = "STORAGE_BONUS"),
            @JsonSubTypes.Type(value = BuildingEffect.ChosenNonSpecialtyProduction.class,
                    name = "CHOSEN_NON_SPECIALTY_PRODUCTION"),
            @JsonSubTypes.Type(value = BuildingEffect.SpecialtyProduction.class, name = "SPECIALTY_PRODUCTION"),
            @JsonSubTypes.Type(value = BuildingEffect.ProductionBonus.class, name = "PRODUCTION_BONUS"),
            @JsonSubTypes.Type(value = BuildingEffect.UpkeepReduction.class, name = "UPKEEP_REDUCTION"),
            @JsonSubTypes.Type(value = BuildingEffect.NoEffect.class, name = "NO_EFFECT")
    })
    interface BuildingEffectMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = EventCard.ResourceCrisis.class, name = "RESOURCE_CRISIS"),
            @JsonSubTypes.Type(value = EventCard.NonSpecialtyCrisis.class, name = "NON_SPECIALTY_CRISIS"),
            @JsonSubTypes.Type(value = EventCard.BuildingCostDiscount.class, name = "BUILDING_COST_DISCOUNT"),
            @JsonSubTypes.Type(value = EventCard.UpgradeCostDiscount.class, name = "UPGRADE_COST_DISCOUNT"),
            @JsonSubTypes.Type(value = EventCard.NoMoneyIncome.class, name = "NO_MONEY_INCOME"),
            @JsonSubTypes.Type(value = EventCard.MarketPriceShift.class, name = "MARKET_PRICE_SHIFT"),
            @JsonSubTypes.Type(value = EventCard.PrestigePurchase.class, name = "PRESTIGE_PURCHASE"),
            @JsonSubTypes.Type(value = EventCard.ProductionPurchase.class, name = "PRODUCTION_PURCHASE"),
            @JsonSubTypes.Type(value = EventCard.ProductionBoost.class, name = "PRODUCTION_BOOST")
    })
    interface EventCardMixin {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ObjectiveCard.ActiveTrader.class, name = "ACTIVE_TRADER"),
            @JsonSubTypes.Type(value = ObjectiveCard.DiversifiedEconomy.class, name = "DIVERSIFIED_ECONOMY"),
            @JsonSubTypes.Type(value = ObjectiveCard.ProjectPartner.class, name = "PROJECT_PARTNER"),
            @JsonSubTypes.Type(value = ObjectiveCard.ContractPlayer.class, name = "CONTRACT_PLAYER"),
            @JsonSubTypes.Type(value = ObjectiveCard.MarketIndependence.class, name = "MARKET_INDEPENDENCE"),
            @JsonSubTypes.Type(value = ObjectiveCard.RapidDevelopment.class, name = "RAPID_DEVELOPMENT"),
            @JsonSubTypes.Type(value = ObjectiveCard.SteadyCity.class, name = "STEADY_CITY"),
            @JsonSubTypes.Type(value = ObjectiveCard.OpportunityWinner.class, name = "OPPORTUNITY_WINNER"),
            @JsonSubTypes.Type(value = ObjectiveCard.CrisisResponder.class, name = "CRISIS_RESPONDER"),
            @JsonSubTypes.Type(value = ObjectiveCard.BalancedStock.class, name = "BALANCED_STOCK"),
            @JsonSubTypes.Type(value = ObjectiveCard.PatientInvestor.class, name = "PATIENT_INVESTOR"),
            @JsonSubTypes.Type(value = ObjectiveCard.BigDeal.class, name = "BIG_DEAL")
    })
    interface ObjectiveCardMixin {
    }
}
