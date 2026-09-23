package citytrade.engine;

import citytrade.engine.ruleset.BuildingEffect;
import citytrade.engine.ruleset.BuildingRules;
import citytrade.engine.ruleset.ContractRules;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.EventRules;
import citytrade.engine.ruleset.LevelRules;
import citytrade.engine.ruleset.MarketRules;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.ObjectiveRules;
import citytrade.engine.ruleset.OpportunityRules;
import citytrade.engine.ruleset.ProjectRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.ruleset.StartingRules;
import citytrade.engine.ruleset.StorageRules;
import citytrade.engine.ruleset.StrainedRules;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Rulesets for engine tests. game-engine cannot use the JSON loader, so this builds the records in Java.
 * The setup-relevant values follow prototype-001; the real file is tested in game-ruleset-json.
 */
public final class TestRulesets {

    public static final ResourceBundle NOTHING = new ResourceBundle(0, 0, 0, 0, 0);

    private TestRulesets() {
    }

    public static Ruleset standard() {
        return withPlayerCount(4);
    }

    /** {@code base} with other level tables, e.g. to make upkeep unpayable. */
    public static Ruleset withLevels(Ruleset base, List<LevelRules> levels) {
        return new Ruleset(base.version(), base.roundCount(), base.playerCount(), base.starting(), levels,
                base.storage(), base.strained(), base.buildings(), base.market(), base.contracts(), base.events(),
                base.objectives(), base.projects(), base.opportunities());
    }

    /** {@code base} with another event deck; the event rounds stay (deck size must match them). */
    public static Ruleset withEventDeck(Ruleset base, List<EventCard> deck) {
        EventRules events = base.events();
        return new Ruleset(base.version(), base.roundCount(), base.playerCount(), base.starting(), base.levels(),
                base.storage(), base.strained(), base.buildings(), base.market(), base.contracts(),
                new EventRules(events.eventRounds(), events.crisisMinLevel(), events.crisisPrestige(), deck),
                base.objectives(), base.projects(), base.opportunities());
    }

    /** Numbers Sheet 14, as in prototype-001 (12 cards). */
    public static List<EventCard> prototypeEvents() {
        return List.of(
                new EventCard.ResourceCrisis("DROUGHT", Resource.FOOD, 2, 1),
                new EventCard.ResourceCrisis("ENERGY_SHORTAGE", Resource.ENERGY, 2, 1),
                new EventCard.ResourceCrisis("SUPPLY_SHOCK", Resource.MATERIALS, 2, 1),
                new EventCard.ResourceCrisis("EPIDEMIC", Resource.TECHNOLOGY, 2, 1),
                new EventCard.NonSpecialtyCrisis("INFRASTRUCTURE_FAILURE", 2),
                new EventCard.BuildingCostDiscount("CONSTRUCTION_BOOM", Resource.MATERIALS, 1, -1),
                new EventCard.UpgradeCostDiscount("TECHNOLOGY_BOOM", Resource.TECHNOLOGY, 2),
                new EventCard.NoMoneyIncome("RECESSION"),
                new EventCard.MarketPriceShift("TRADE_DISRUPTION", 2, -1, 1),
                new EventCard.PrestigePurchase("PUBLIC_FESTIVAL", new ResourceBundle(2, 0, 0, 0, 2), 1),
                new EventCard.ProductionPurchase("RESEARCH_BREAKTHROUGH", new ResourceBundle(0, 0, 0, 3, 0), 1),
                new EventCard.ProductionBoost("GOOD_HARVEST_YEAR", new ResourceBundle(1, 1, 0, 0, 0)));
    }

    /** Numbers Sheet 10, as in prototype-001. */
    public static List<BuildingRules> prototypeBuildings() {
        return List.of(
                new BuildingRules("WAREHOUSE", 1, new ResourceBundle(0, 1, 1, 1, 0), 0,
                        new BuildingEffect.StorageBonus(4)),
                new BuildingRules("WORKSHOP", 1, new ResourceBundle(1, 0, 1, 1, 0), 0,
                        new BuildingEffect.ChosenNonSpecialtyProduction(1)),
                new BuildingRules("MARKET_HALL", 1, new ResourceBundle(1, 1, 0, 1, 2), 0,
                        new BuildingEffect.ProductionBonus(new ResourceBundle(0, 0, 0, 0, 1))),
                new BuildingRules("SPECIALTY_COMPLEX", 2, new ResourceBundle(1, 1, 2, 2, 0), 1,
                        new BuildingEffect.SpecialtyProduction(1)),
                new BuildingRules("RESEARCH_LAB", 2, new ResourceBundle(2, 2, 1, 0, 0), 1,
                        new BuildingEffect.ProductionBonus(new ResourceBundle(0, 0, 0, 1, 0))),
                new BuildingRules("TRANSIT_NETWORK", 2, new ResourceBundle(0, 2, 2, 1, 0), 0,
                        new BuildingEffect.UpkeepReduction(3, 1)),
                new BuildingRules("CIVIC_CENTER", 2, new ResourceBundle(2, 2, 2, 2, 0), 2,
                        new BuildingEffect.NoEffect()),
                new BuildingRules("GRAND_LANDMARK", 3, new ResourceBundle(3, 3, 3, 3, 5), 3,
                        new BuildingEffect.NoEffect()));
    }

    public static Ruleset withPlayerCount(int playerCount) {
        return new Ruleset(
                "test-001",
                14,
                playerCount,
                new StartingRules(4, 1, 6),
                List.of(
                        new LevelRules(1, 3, 1, 2, NOTHING, 0, 0),
                        new LevelRules(2, 4, 1, 3, new ResourceBundle(3, 3, 3, 3, 0), 1, 1),
                        new LevelRules(3, 5, 1, 4, new ResourceBundle(4, 4, 4, 4, 4), 2, 2)),
                new StorageRules(10),
                new StrainedRules(2, 1),
                prototypeBuildings(),
                new MarketRules(
                        List.of(
                                new MarketRules.PriceStep("A", 3, 1),
                                new MarketRules.PriceStep("B", 4, 1),
                                new MarketRules.PriceStep("C", 5, 2),
                                new MarketRules.PriceStep("D", 6, 3)),
                        "B", 3, 4),
                new ContractRules(3, 2, 2, 2, 1),
                new EventRules(
                        IntStream.rangeClosed(2, 13).boxed().toList(),
                        2, 1,
                        // Events with no effect, so tests of other rules are not changed by random events.
                        IntStream.rangeClosed(1, 12)
                                .mapToObj(i -> (EventCard) new EventCard.ProductionBoost("EVENT_" + i, NOTHING))
                                .toList()),
                new ObjectiveRules(3, 2, 2,
                        IntStream.rangeClosed(1, 12)
                                .mapToObj(i -> (ObjectiveCard) new ObjectiveCard.SteadyCity("OBJECTIVE_" + i))
                                .toList()),
                new ProjectRules(
                        List.of(new ProjectRules.ProjectWindow(3, 6), new ProjectRules.ProjectWindow(7, 10)),
                        2, 1, 6, 3, 2, 1,
                        IntStream.rangeClosed(1, 3)
                                .mapToObj(i -> new ProjectRules.ProjectCard("PROJECT_" + i, NOTHING, NOTHING))
                                .toList()),
                new OpportunityRules(
                        List.of(3, 5, 7, 9, 11),
                        IntStream.rangeClosed(1, 5)
                                .mapToObj(i -> new OpportunityRules.OpportunityCard("OPPORTUNITY_" + i, NOTHING))
                                .toList()));
    }
}
