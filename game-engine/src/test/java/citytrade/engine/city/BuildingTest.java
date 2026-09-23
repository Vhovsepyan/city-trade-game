package citytrade.engine.city;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.seatOf;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.economy.Production;
import citytrade.engine.economy.Storage;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.BuiltBuilding;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BuildingTest {

    private static final long SEED = 13;
    private static final ResourceBundle RICH = new ResourceBundle(10, 10, 10, 10, 10);

    private final Ruleset ruleset = TestRulesets.standard();
    private final GameState ready = readyForRoundOne(SEED, ruleset);
    private final int seat = seatOf(ready, CityType.AGRICULTURAL);

    private GameState roundOneWindow(int level, ResourceBundle holdings) {
        return withCity(accept(ready, new StartRound(), ruleset).state(), seat, level, holdings);
    }

    /** Resolve the current round and open the next window. */
    private GameState nextWindow(GameState window) {
        return accept(accept(window, new ResolveRound(), ruleset).state(), new StartRound(), ruleset).state();
    }

    private GameState build(GameState state, String buildingId) {
        return accept(state, new BuildBuilding(seat, buildingId), ruleset).state();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "WAREHOUSE, 1, 0, 1, 1, 1, 0",
            "MARKET_HALL, 1, 1, 1, 0, 1, 2",
            "SPECIALTY_COMPLEX, 2, 1, 1, 2, 2, 0",
            "RESEARCH_LAB, 2, 2, 2, 1, 0, 0",
            "TRANSIT_NETWORK, 2, 0, 2, 2, 1, 0",
            "CIVIC_CENTER, 2, 2, 2, 2, 2, 0",
            "GRAND_LANDMARK, 3, 3, 3, 3, 3, 5"})
    void buildingPaysItsCostAndIsOwned(String id, int level, int f, int e, int m, int t, int money) {
        ResourceBundle cost = new ResourceBundle(f, e, m, t, money);
        GameState window = roundOneWindow(level, RICH);

        GameResult.Accepted result = accept(window, new BuildBuilding(seat, id), ruleset);

        PlayerState after = result.state().player(seat);
        assertEquals(RICH.minus(cost), after.holdings());
        assertEquals(List.of(new BuiltBuilding(id, 1, Optional.empty())), after.buildings());
        assertEquals(List.of(new DomainEvent.BuildingBuilt(seat, id, cost)), result.events());
    }

    @Test
    void eachBuildingOnlyOnceEvenInALaterRound() {
        GameState built = build(roundOneWindow(1, RICH), "WAREHOUSE");

        assertRejected(built, new BuildBuilding(seat, "WAREHOUSE"), ruleset, RejectionCode.BUILDING_ALREADY_BUILT);
        assertRejected(nextWindow(built), new BuildBuilding(seat, "WAREHOUSE"), ruleset,
                RejectionCode.BUILDING_ALREADY_BUILT);
    }

    @Test
    void differentBuildingsMayBeBuiltInTheSameRound() {
        GameState built = build(build(roundOneWindow(1, RICH), "WAREHOUSE"), "MARKET_HALL");

        assertEquals(2, built.player(seat).buildings().size());
    }

    @Test
    void buildingNeedsItsRequiredLevel() {
        GameState levelOne = roundOneWindow(1, RICH);
        assertRejected(levelOne, new BuildBuilding(seat, "CIVIC_CENTER"), ruleset, RejectionCode.LEVEL_TOO_LOW);

        GameState levelTwo = roundOneWindow(2, RICH);
        assertRejected(levelTwo, new BuildBuilding(seat, "GRAND_LANDMARK"), ruleset, RejectionCode.LEVEL_TOO_LOW);
    }

    @Test
    void levelReachedEarlierInTheWindowUnlocksBuildings() {
        GameState upgraded = accept(roundOneWindow(1, RICH), new UpgradeCity(seat), ruleset).state();

        assertTrue(build(upgraded, "CIVIC_CENTER").player(seat).hasBuilt("CIVIC_CENTER"));
    }

    @Test
    void transitNetworkCanBeBuiltAtLevelTwo() {
        assertTrue(build(roundOneWindow(2, RICH), "TRANSIT_NETWORK").player(seat).hasBuilt("TRANSIT_NETWORK"));
    }

    @Test
    void unknownBuildingIsRejected() {
        assertRejected(roundOneWindow(3, RICH), new BuildBuilding(seat, "CASTLE"), ruleset,
                RejectionCode.UNKNOWN_BUILDING);
    }

    @Test
    void missingResourcesOrMoneyReject() {
        assertRejected(roundOneWindow(1, new ResourceBundle(10, 0, 10, 10, 10)), new BuildBuilding(seat, "WAREHOUSE"),
                ruleset, RejectionCode.INSUFFICIENT_RESOURCES);
        assertRejected(roundOneWindow(1, new ResourceBundle(10, 10, 10, 10, 1)), new BuildBuilding(seat, "MARKET_HALL"),
                ruleset, RejectionCode.INSUFFICIENT_MONEY);
    }

    @Test
    void buildingIsOnlyAllowedInTheWindow() {
        assertRejected(ready, new BuildBuilding(seat, "WAREHOUSE"), ruleset, RejectionCode.INVALID_PHASE);
        GameState resolved = accept(roundOneWindow(1, RICH), new ResolveRound(), ruleset).state();
        assertRejected(resolved, new BuildBuilding(seat, "WAREHOUSE"), ruleset, RejectionCode.INVALID_PHASE);
    }

    @Test
    void unknownSeatIsRejected() {
        assertRejected(roundOneWindow(1, RICH), new BuildBuilding(9, "WAREHOUSE"), ruleset,
                RejectionCode.UNKNOWN_PLAYER);
    }

    @Test
    void workshopNeedsANonSpecialtyResourceChoice() {
        GameState window = roundOneWindow(1, RICH);

        assertRejected(window, new BuildBuilding(seat, "WORKSHOP"), ruleset, RejectionCode.INVALID_BUILDING_CHOICE);
        assertRejected(window, new BuildBuilding(seat, "WORKSHOP", Optional.of(Resource.FOOD)), ruleset,
                RejectionCode.INVALID_BUILDING_CHOICE);
        assertRejected(window, new BuildBuilding(seat, "WAREHOUSE", Optional.of(Resource.ENERGY)), ruleset,
                RejectionCode.INVALID_BUILDING_CHOICE);

        PlayerState after = accept(window, new BuildBuilding(seat, "WORKSHOP", Optional.of(Resource.ENERGY)), ruleset)
                .state().player(seat);
        assertEquals(List.of(new BuiltBuilding("WORKSHOP", 1, Optional.of(Resource.ENERGY))), after.buildings());
    }

    @Test
    void workshopAddsTheChosenResourceFromTheNextRound() {
        GameState built = accept(roundOneWindow(1, new ResourceBundle(5, 5, 5, 5, 10)),
                new BuildBuilding(seat, "WORKSHOP", Optional.of(Resource.ENERGY)), ruleset).state();
        assertEquals(new ResourceBundle(3, 1, 1, 1, 2), Production.productionOf(built.player(seat), 1, ruleset),
                "no effect in the round it is built");

        GameState roundTwo = nextWindow(built);

        // After building: F4 E5 M4 T4 $10. Round 2 production (Level 1 Agricultural): F3 E1+1 M1 T1 $2.
        assertEquals(new ResourceBundle(7, 7, 5, 5, 12), roundTwo.player(seat).holdings());
    }

    @Test
    void productionBuildingsAddFromTheNextRound() {
        GameState built = build(build(build(roundOneWindow(2, RICH.plus(RICH)), "MARKET_HALL"), "SPECIALTY_COMPLEX"),
                "RESEARCH_LAB");
        PlayerState player = built.player(seat);

        // Level 2 Agricultural: F4, others 1, Money 3.
        assertEquals(new ResourceBundle(4, 1, 1, 1, 3), Production.productionOf(player, 1, ruleset));
        // Market Hall +1 Money, Specialty Complex +1 Food, Research Lab +1 Technology.
        assertEquals(new ResourceBundle(5, 1, 1, 2, 4), Production.productionOf(player, 2, ruleset));
    }

    @Test
    void strainedPenaltyAppliesToTheWholeSpecialtyAndMoneyProduction() {
        GameState built = build(build(roundOneWindow(2, RICH.plus(RICH)), "MARKET_HALL"), "SPECIALTY_COMPLEX");
        PlayerState strained = built.player(seat).withStrained(false, true);

        // (4 + 1) - 2 Food, (3 + 1) - 1 Money.
        assertEquals(new ResourceBundle(3, 1, 1, 1, 3), Production.productionOf(strained, 2, ruleset));
    }

    @Test
    void warehouseRaisesTheStorageLimitFromTheNextRound() {
        GameState built = build(roundOneWindow(1, new ResourceBundle(13, 1, 1, 1, 0)), "WAREHOUSE");
        assertEquals(10, Storage.limitOf(built.player(seat), 1, ruleset));
        assertEquals(14, Storage.limitOf(built.player(seat), 2, ruleset));

        GameState afterRoundOne = accept(built, new ResolveRound(), ruleset).state();
        assertEquals(10, afterRoundOne.player(seat).holdings().food(), "not active in the round it is built");

        GameState roundTwo = accept(afterRoundOne, new StartRound(), ruleset).state();
        GameState rich = withCity(roundTwo, seat, 1, new ResourceBundle(15, 14, 13, 12, 0));
        GameResult.Accepted resolved = accept(rich, new ResolveRound(), ruleset);
        assertEquals(new ResourceBundle(14, 14, 13, 12, 0), resolved.state().player(seat).holdings());
        assertTrue(resolved.events().contains(
                new DomainEvent.ExcessDiscarded(seat, new ResourceBundle(1, 0, 0, 0, 0))));
    }

    @Test
    void transitNetworkHasNoEffectAtLevelTwo() {
        GameState built = build(roundOneWindow(2, RICH), "TRANSIT_NETWORK");

        GameResult.Accepted roundTwo = startNextRound(built);

        assertEquals(1, upkeepPaidBy(roundTwo), "Level 2 upkeep stays 1");
    }

    @Test
    void transitNetworkLowersLevelThreeUpkeepByOne() {
        GameState built = build(roundOneWindow(2, RICH), "TRANSIT_NETWORK");
        GameState roundTwo = startNextRound(built).state();
        GameState levelThree = accept(roundTwo, new UpgradeCity(seat), ruleset).state();

        assertEquals(1, upkeepPaidBy(startNextRound(levelThree)), "Level 3 upkeep 2 - 1");
    }

    @Test
    void levelThreeWithoutTransitNetworkPaysTwo() {
        GameState levelThree = accept(roundOneWindow(2, RICH), new UpgradeCity(seat), ruleset).state();

        assertEquals(2, upkeepPaidBy(startNextRound(levelThree)));
    }

    @Test
    void transitNetworkBuiltInTheSameRoundAsLevelThreeWorksFromTheNextRound() {
        GameState levelThree = accept(roundOneWindow(2, RICH), new UpgradeCity(seat), ruleset).state();
        GameState built = build(levelThree, "TRANSIT_NETWORK");

        assertEquals(1, upkeepPaidBy(startNextRound(built)));
    }

    @Test
    void buildingPrestigeAndLevelPrestigeAreAddedTogetherAtResolution() {
        GameState window = accept(roundOneWindow(1, RICH), new UpgradeCity(seat), ruleset).state();
        window = build(build(build(window, "CIVIC_CENTER"), "SPECIALTY_COMPLEX"), "WAREHOUSE");
        assertEquals(0, window.player(seat).prestige(), "nothing during the window");

        GameResult.Accepted resolved = accept(window, new ResolveRound(), ruleset);

        // Level 2 +1, Civic Center +2, Specialty Complex +1, Warehouse 0.
        assertEquals(4, resolved.state().player(seat).prestige());
        assertEquals(List.of(new DomainEvent.PrestigeGained(seat, 4)), resolved.events().stream()
                .filter(DomainEvent.PrestigeGained.class::isInstance).toList());

        GameResult.Accepted nextResolved = accept(accept(resolved.state(), new StartRound(), ruleset).state(),
                new ResolveRound(), ruleset);
        assertEquals(4, nextResolved.state().player(seat).prestige(), "building Prestige is given once");
    }

    @Test
    void grandLandmarkGivesThreePrestige() {
        GameState built = build(roundOneWindow(3, RICH), "GRAND_LANDMARK");

        assertEquals(3, accept(built, new ResolveRound(), ruleset).state().player(seat).prestige());
    }

    private GameResult.Accepted startNextRound(GameState window) {
        return accept(accept(window, new ResolveRound(), ruleset).state(), new StartRound(), ruleset);
    }

    private int upkeepPaidBy(GameResult.Accepted result) {
        DomainEvent.UpkeepPaid paid = result.events().stream()
                .filter(DomainEvent.UpkeepPaid.class::isInstance)
                .map(DomainEvent.UpkeepPaid.class::cast)
                .filter(event -> event.seat() == seat)
                .findFirst().orElseThrow();
        assertEquals(0, paid.missing());
        return Arrays.stream(Resource.values()).mapToInt(paid.paid()::amountOf).sum();
    }
}
