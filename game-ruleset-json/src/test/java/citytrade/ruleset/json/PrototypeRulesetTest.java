package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
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
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Every Numbers Sheet v2 section checked against rulesets/prototype-001.json. */
class PrototypeRulesetTest {

    private static final Ruleset RULESET = RulesetLoader.load(TestRulesets.PROTOTYPE_001);

    private static ResourceBundle bundle(int food, int energy, int materials, int technology, int money) {
        return new ResourceBundle(food, energy, materials, technology, money);
    }

    private static ResourceBundle none() {
        return bundle(0, 0, 0, 0, 0);
    }

    private static BuildingRules building(String id) {
        return RULESET.buildings().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    private static EventCard event(String id) {
        return RULESET.events().deck().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
    }

    private static ObjectiveCard objective(String id) {
        return RULESET.objectives().deck().stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void header() {
        assertEquals("prototype-001", RULESET.version());
        assertEquals(14, RULESET.roundCount());
        assertEquals(4, RULESET.playerCount());
    }

    @Test
    void section1to2StartingResourcesAndMoney() {
        assertEquals(4, RULESET.starting().specialtyResource());
        assertEquals(1, RULESET.starting().otherResource());
        assertEquals(6, RULESET.starting().money());
    }

    @Test
    void section3to4ProductionPerLevel() {
        List<LevelRules> levels = RULESET.levels();
        assertEquals(List.of(1, 2, 3), levels.stream().map(LevelRules::level).toList());
        assertEquals(List.of(3, 4, 5), levels.stream().map(LevelRules::specialtyProduction).toList());
        assertEquals(List.of(1, 1, 1), levels.stream().map(LevelRules::otherResourceProduction).toList());
        assertEquals(List.of(2, 3, 4), levels.stream().map(LevelRules::moneyProduction).toList());
    }

    @Test
    void section5Storage() {
        assertEquals(10, RULESET.storage().resourceLimit());
        BuildingRules warehouse = building("WAREHOUSE");
        assertEquals(new BuildingEffect.StorageBonus(4), warehouse.effect());
    }

    @Test
    void section6to7LevelCosts() {
        assertEquals(none(), RULESET.levels().get(0).upgradeCost());
        assertEquals(bundle(3, 3, 3, 3, 0), RULESET.levels().get(1).upgradeCost());
        assertEquals(bundle(4, 4, 4, 4, 4), RULESET.levels().get(2).upgradeCost());
    }

    @Test
    void section8Upkeep() {
        assertEquals(List.of(0, 1, 2), RULESET.levels().stream().map(LevelRules::upkeepResources).toList());
        assertEquals(new BuildingEffect.UpkeepReduction(3, 1), building("TRANSIT_NETWORK").effect());
    }

    @Test
    void section9Strained() {
        assertEquals(2, RULESET.strained().specialtyProductionPenalty());
        assertEquals(1, RULESET.strained().moneyIncomePenalty());
    }

    @Test
    void section10Buildings() {
        assertEquals(
                List.of("WAREHOUSE", "WORKSHOP", "MARKET_HALL", "SPECIALTY_COMPLEX", "RESEARCH_LAB",
                        "TRANSIT_NETWORK", "CIVIC_CENTER", "GRAND_LANDMARK"),
                RULESET.buildings().stream().map(BuildingRules::id).toList());

        assertEquals(new BuildingRules("WAREHOUSE", 1, bundle(0, 1, 1, 1, 0), 0,
                new BuildingEffect.StorageBonus(4)), building("WAREHOUSE"));
        assertEquals(new BuildingRules("WORKSHOP", 1, bundle(1, 0, 1, 1, 0), 0,
                new BuildingEffect.ChosenNonSpecialtyProduction(1)), building("WORKSHOP"));
        assertEquals(new BuildingRules("MARKET_HALL", 1, bundle(1, 1, 0, 1, 2), 0,
                new BuildingEffect.ProductionBonus(bundle(0, 0, 0, 0, 1))), building("MARKET_HALL"));
        assertEquals(new BuildingRules("SPECIALTY_COMPLEX", 2, bundle(1, 1, 2, 2, 0), 1,
                new BuildingEffect.SpecialtyProduction(1)), building("SPECIALTY_COMPLEX"));
        assertEquals(new BuildingRules("RESEARCH_LAB", 2, bundle(2, 2, 1, 0, 0), 1,
                new BuildingEffect.ProductionBonus(bundle(0, 0, 0, 1, 0))), building("RESEARCH_LAB"));
        assertEquals(new BuildingRules("TRANSIT_NETWORK", 2, bundle(0, 2, 2, 1, 0), 0,
                new BuildingEffect.UpkeepReduction(3, 1)), building("TRANSIT_NETWORK"));
        assertEquals(new BuildingRules("CIVIC_CENTER", 2, bundle(2, 2, 2, 2, 0), 2,
                new BuildingEffect.NoEffect()), building("CIVIC_CENTER"));
        assertEquals(new BuildingRules("GRAND_LANDMARK", 3, bundle(3, 3, 3, 3, 5), 3,
                new BuildingEffect.NoEffect()), building("GRAND_LANDMARK"));
    }

    @Test
    void section11to12Market() {
        MarketRules market = RULESET.market();
        assertEquals(List.of(
                new MarketRules.PriceStep("A", 3, 1),
                new MarketRules.PriceStep("B", 4, 1),
                new MarketRules.PriceStep("C", 5, 2),
                new MarketRules.PriceStep("D", 6, 3)), market.steps());
        assertEquals("B", market.startStep());
        assertEquals(3, market.priceMoveThreshold());
        assertEquals(4, market.maxBuyPerResourcePerRound());
    }

    @Test
    void section13ContractPenalties() {
        ContractRules contracts = RULESET.contracts();
        assertEquals(new ContractRules(3, 2, 2, 2, 1), contracts);

        // Numbers Sheet example: 3 Food unpaid -> 6$ owed; 3$ paid, 3$ unpaid -> 2P (round up), plus 1P for breaking.
        int owed = 3 * contracts.compensationPerUnpaidResource();
        int unpaid = owed - 3;
        int perPrestige = contracts.unpaidCompensationMoneyPerPrestige();
        int prestigeLost = (unpaid + perPrestige - 1) / perPrestige + contracts.breakPrestigePenalty();
        assertEquals(6, owed);
        assertEquals(3, prestigeLost);
    }

    @Test
    void section14Events() {
        EventRules events = RULESET.events();
        assertEquals(IntStream.rangeClosed(2, 13).boxed().toList(), events.eventRounds());
        assertEquals(2, events.crisisMinLevel());
        assertEquals(1, events.crisisPrestige());
        assertEquals(12, events.deck().size());

        assertEquals(new EventCard.ResourceCrisis("DROUGHT", Resource.FOOD, 2, 1), event("DROUGHT"));
        assertEquals(new EventCard.ResourceCrisis("ENERGY_SHORTAGE", Resource.ENERGY, 2, 1), event("ENERGY_SHORTAGE"));
        assertEquals(new EventCard.ResourceCrisis("SUPPLY_SHOCK", Resource.MATERIALS, 2, 1), event("SUPPLY_SHOCK"));
        assertEquals(new EventCard.ResourceCrisis("EPIDEMIC", Resource.TECHNOLOGY, 2, 1), event("EPIDEMIC"));
        assertEquals(new EventCard.NonSpecialtyCrisis("INFRASTRUCTURE_FAILURE", 2), event("INFRASTRUCTURE_FAILURE"));
        assertEquals(new EventCard.BuildingCostDiscount("CONSTRUCTION_BOOM", Resource.MATERIALS, 1, -1),
                event("CONSTRUCTION_BOOM"));
        assertEquals(new EventCard.UpgradeCostDiscount("TECHNOLOGY_BOOM", Resource.TECHNOLOGY, 2),
                event("TECHNOLOGY_BOOM"));
        assertEquals(new EventCard.NoMoneyIncome("RECESSION"), event("RECESSION"));
        assertEquals(new EventCard.MarketPriceShift("TRADE_DISRUPTION", 2, -1, 1), event("TRADE_DISRUPTION"));
        assertEquals(new EventCard.PrestigePurchase("PUBLIC_FESTIVAL", bundle(2, 0, 0, 0, 2), 1),
                event("PUBLIC_FESTIVAL"));
        assertEquals(new EventCard.ProductionPurchase("RESEARCH_BREAKTHROUGH", bundle(0, 0, 0, 3, 0), 1),
                event("RESEARCH_BREAKTHROUGH"));
        assertEquals(new EventCard.ProductionBoost("GOOD_HARVEST_YEAR", bundle(1, 1, 0, 0, 0)),
                event("GOOD_HARVEST_YEAR"));
    }

    @Test
    void section15HiddenObjectives() {
        ObjectiveRules objectives = RULESET.objectives();
        assertEquals(3, objectives.dealtPerPlayer());
        assertEquals(2, objectives.keptPerPlayer());
        assertEquals(2, objectives.completedPrestige());
        assertEquals(12, objectives.deck().size());

        assertEquals(new ObjectiveCard.ActiveTrader("ACTIVE_TRADER"), objective("ACTIVE_TRADER"));
        assertEquals(new ObjectiveCard.DiversifiedEconomy("DIVERSIFIED_ECONOMY", 4), objective("DIVERSIFIED_ECONOMY"));
        assertEquals(new ObjectiveCard.ProjectPartner("PROJECT_PARTNER"), objective("PROJECT_PARTNER"));
        assertEquals(new ObjectiveCard.ContractPlayer("CONTRACT_PLAYER", 2), objective("CONTRACT_PLAYER"));
        assertEquals(new ObjectiveCard.MarketIndependence("MARKET_INDEPENDENCE", 3), objective("MARKET_INDEPENDENCE"));
        assertEquals(new ObjectiveCard.RapidDevelopment("RAPID_DEVELOPMENT", 3, 7), objective("RAPID_DEVELOPMENT"));
        assertEquals(new ObjectiveCard.SteadyCity("STEADY_CITY"), objective("STEADY_CITY"));
        assertEquals(new ObjectiveCard.OpportunityWinner("OPPORTUNITY_WINNER", 1), objective("OPPORTUNITY_WINNER"));
        assertEquals(new ObjectiveCard.CrisisResponder("CRISIS_RESPONDER", 3), objective("CRISIS_RESPONDER"));
        assertEquals(new ObjectiveCard.BalancedStock("BALANCED_STOCK", 5), objective("BALANCED_STOCK"));
        assertEquals(new ObjectiveCard.PatientInvestor("PATIENT_INVESTOR", 3), objective("PATIENT_INVESTOR"));
        assertEquals(new ObjectiveCard.BigDeal("BIG_DEAL", 5), objective("BIG_DEAL"));
    }

    @Test
    void section16PublicProjects() {
        ProjectRules projects = RULESET.projects();
        assertEquals(List.of(new ProjectRules.ProjectWindow(3, 6), new ProjectRules.ProjectWindow(7, 10)),
                projects.windows());
        assertEquals(2, projects.pointsPerResource());
        assertEquals(1, projects.pointsPerMoney());
        assertEquals(6, projects.minQualifyingPoints());
        assertEquals(List.of(
                new ProjectRules.ProjectCard("REGIONAL_POWER_GRID", bundle(0, 6, 6, 0, 6), bundle(0, 1, 0, 0, 0)),
                new ProjectRules.ProjectCard("REGIONAL_RESEARCH_INITIATIVE", bundle(0, 0, 4, 6, 6),
                        bundle(0, 0, 0, 1, 0)),
                new ProjectRules.ProjectCard("FOOD_SECURITY_PROGRAM", bundle(6, 4, 0, 0, 6), bundle(1, 0, 0, 0, 0))),
                projects.cards());
    }

    @Test
    void section17RegionalOpportunities() {
        OpportunityRules opportunities = RULESET.opportunities();
        assertEquals(List.of(3, 5, 7, 9, 11), opportunities.appearanceRounds());
        assertEquals(List.of(
                new OpportunityRules.OpportunityCard("SOLAR_FIELD", bundle(0, 1, 0, 0, 0)),
                new OpportunityRules.OpportunityCard("FERTILE_VALLEY", bundle(1, 0, 0, 0, 0)),
                new OpportunityRules.OpportunityCard("INDUSTRIAL_ZONE", bundle(0, 0, 1, 0, 0)),
                new OpportunityRules.OpportunityCard("RESEARCH_CAMPUS", bundle(0, 0, 0, 1, 0)),
                new OpportunityRules.OpportunityCard("COMMERCIAL_HUB", bundle(0, 0, 0, 0, 2))),
                opportunities.cards());
    }

    @Test
    void section18PrestigeTable() {
        assertEquals(1, RULESET.levels().get(1).reachPrestige());
        assertEquals(2, RULESET.levels().get(2).reachPrestige());
        assertEquals(1, building("SPECIALTY_COMPLEX").prestige());
        assertEquals(1, building("RESEARCH_LAB").prestige());
        assertEquals(2, building("CIVIC_CENTER").prestige());
        assertEquals(3, building("GRAND_LANDMARK").prestige());
        assertEquals(3, RULESET.projects().largestContributorPrestige());
        assertEquals(2, RULESET.projects().tiedLargestPrestige());
        assertEquals(1, RULESET.projects().otherQualifyingPrestige());
        assertEquals(1, RULESET.events().crisisPrestige());
        assertEquals(1, assertInstanceOf(EventCard.PrestigePurchase.class, event("PUBLIC_FESTIVAL")).prestige());
        assertEquals(1, RULESET.contracts().breakPrestigePenalty());
        assertEquals(2, RULESET.contracts().unpaidCompensationMoneyPerPrestige());
        assertEquals(2, RULESET.objectives().completedPrestige());

        // Sheet: rough maximum = levels 3 + buildings 7 + projects 6 + crises 5 + festival 1 + objectives 4 = 26.
        int levels = RULESET.levels().stream().mapToInt(LevelRules::reachPrestige).sum();
        int buildings = RULESET.buildings().stream().mapToInt(BuildingRules::prestige).sum();
        int projects = RULESET.projects().windows().size() * RULESET.projects().largestContributorPrestige();
        long crisisCount = RULESET.events().deck().stream()
                .filter(e -> e instanceof EventCard.ResourceCrisis || e instanceof EventCard.NonSpecialtyCrisis)
                .count();
        int crises = (int) crisisCount * RULESET.events().crisisPrestige();
        int objectives = RULESET.objectives().keptPerPlayer() * RULESET.objectives().completedPrestige();
        assertEquals(26, levels + buildings + projects + crises + 1 + objectives);
    }

    @Test
    void section19ResourceDemandTable() {
        // Sum of all level and building costs: F 17, E 19, M 19, T 18, $ 11.
        int[] total = new int[5];
        RULESET.levels().forEach(level -> add(total, level.upgradeCost()));
        RULESET.buildings().forEach(building -> add(total, building.cost()));
        assertEquals(List.of(17, 19, 19, 18, 11), IntStream.of(total).boxed().toList());

        // Early game (Level 2 + the three Level 1 buildings): F 5, E 5, M 5, T 6.
        int[] early = new int[5];
        add(early, RULESET.levels().get(1).upgradeCost());
        RULESET.buildings().stream().filter(b -> b.requiredLevel() == 1).forEach(b -> add(early, b.cost()));
        assertEquals(List.of(5, 5, 5, 6), IntStream.of(early).limit(4).boxed().toList());
    }

    private static void add(int[] total, ResourceBundle bundle) {
        total[0] += bundle.food();
        total[1] += bundle.energy();
        total[2] += bundle.materials();
        total[3] += bundle.technology();
        total[4] += bundle.money();
    }

    @Test
    void section20TieAndEdgeRules() {
        // Values behind the tie/edge rules; the rules themselves are engine behavior (later tasks).
        assertEquals(2, RULESET.projects().tiedLargestPrestige());
        assertEquals(3, RULESET.contracts().maxDurationRounds());
        assertEquals(2, RULESET.events().crisisMinLevel());
        assertEquals(10, RULESET.storage().resourceLimit());
    }

    @Test
    void section21SetupAndRoundTracker() {
        assertEquals(12, RULESET.objectives().deck().size());
        assertEquals(12, RULESET.events().deck().size());
        assertEquals(3, RULESET.projects().cards().size());
        assertEquals(5, RULESET.opportunities().cards().size());
        assertEquals("B", RULESET.market().startStep());
        // Round tracker: no event in rounds 1 and 14, project A 3-6, B 7-10, opportunities in odd rounds 3-11.
        assertEquals(2, RULESET.events().eventRounds().getFirst());
        assertEquals(13, RULESET.events().eventRounds().getLast());
        assertEquals(10, RULESET.projects().windows().getLast().deadlineRound());
        assertEquals(11, RULESET.opportunities().appearanceRounds().getLast());
    }
}
