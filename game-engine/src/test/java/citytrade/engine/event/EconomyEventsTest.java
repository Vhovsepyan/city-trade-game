package citytrade.engine.event;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.seatOf;
import static citytrade.engine.TestGames.withCity;
import static citytrade.engine.event.EventTestSupport.card;
import static citytrade.engine.event.EventTestSupport.nextRoundWith;
import static citytrade.engine.event.EventTestSupport.roundOneWindow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.MarketPrices;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Events 6-9 and 12 (Numbers Sheet 14): effects during the event round only. */
class EconomyEventsTest {

    private static final int STEP_A = 0;
    private static final int STEP_C = 2;
    private static final int STEP_D = 3;

    private final Ruleset ruleset = TestRulesets.standard();

    // --- 6 Construction Boom ---

    @Test
    void constructionBoomMakesEveryBuildingCostOneMaterialLess() {
        GameState window = nextRoundWith(roundOneWindow(ruleset), card("CONSTRUCTION_BOOM"), ruleset).state();
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 3, new ResourceBundle(3, 4, 2, 4, 5));

        // Warehouse 1E 1M 1T -> 1E 1T; Grand Landmark 3F 3E 3M 3T + 5$ -> 3M becomes 2M.
        GameResult.Accepted warehouse = accept(window, new BuildBuilding(seat, "WAREHOUSE"), ruleset);
        assertEquals(List.of(new DomainEvent.BuildingBuilt(seat, "WAREHOUSE", new ResourceBundle(0, 1, 0, 1, 0))),
                warehouse.events());
        GameResult.Accepted landmark = accept(warehouse.state(), new BuildBuilding(seat, "GRAND_LANDMARK"), ruleset);
        assertEquals(List.of(new DomainEvent.BuildingBuilt(seat, "GRAND_LANDMARK", new ResourceBundle(3, 3, 2, 3, 5))),
                landmark.events());
        assertEquals(ResourceBundle.EMPTY, landmark.state().player(seat).holdings());
    }

    @Test
    void constructionBoomNeverMakesACostNegative() {
        GameState window = nextRoundWith(roundOneWindow(ruleset), card("CONSTRUCTION_BOOM"), ruleset).state();
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 1, new ResourceBundle(1, 1, 0, 1, 2));

        // Market Hall has no Materials in its cost: it stays 1F 1E 1T + 2$.
        GameResult.Accepted built = accept(window, new BuildBuilding(seat, "MARKET_HALL"), ruleset);
        assertEquals(List.of(new DomainEvent.BuildingBuilt(seat, "MARKET_HALL", new ResourceBundle(1, 1, 0, 1, 2))),
                built.events());
    }

    @Test
    void constructionBoomDiscountEndsWithTheRound() {
        GameState round2 = nextRoundWith(roundOneWindow(ruleset), card("CONSTRUCTION_BOOM"), ruleset).state();
        GameState round3 = accept(accept(round2, new ResolveRound(), ruleset).state(), new StartRound(), ruleset)
                .state();
        int seat = seatOf(round3, CityType.AGRICULTURAL);
        round3 = withCity(round3, seat, 1, new ResourceBundle(0, 1, 0, 1, 0));

        assertRejected(round3, new BuildBuilding(seat, "WAREHOUSE"), ruleset, RejectionCode.INSUFFICIENT_RESOURCES);
    }

    @Test
    void constructionBoomLowersTheMaterialsPriceOneStepWithinTheLowestStep() {
        GameState round2 = nextRoundWith(roundOneWindow(ruleset), card("CONSTRUCTION_BOOM"), ruleset).state();
        assertEquals(3, Market.buyCost(round2, Resource.MATERIALS, ruleset), "step B -> A");
        assertEquals(1, Market.sellValue(round2, Resource.MATERIALS, ruleset));
        assertEquals(4, Market.buyCost(round2, Resource.FOOD, ruleset), "other resources stay at B");

        GameState atA = roundOneWindow(ruleset).withMarket(MarketPrices.allAt(STEP_A));
        GameState boomAtA = nextRoundWith(atA, card("CONSTRUCTION_BOOM"), ruleset).state();
        assertEquals(3, Market.buyCost(boomAtA, Resource.MATERIALS, ruleset), "already at A, stays A");
    }

    // --- 7 Technology Boom ---

    @Test
    void technologyBoomMakesCityUpgradesCostTwoTechnologyLess() {
        GameState window = nextRoundWith(roundOneWindow(ruleset), card("TECHNOLOGY_BOOM"), ruleset).state();
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 1, new ResourceBundle(3, 3, 3, 1, 0));

        GameResult.Accepted upgraded = accept(window, new UpgradeCity(seat), ruleset);
        assertEquals(List.of(new DomainEvent.CityUpgraded(seat, 2, new ResourceBundle(3, 3, 3, 1, 0))),
                upgraded.events());
        assertEquals(ResourceBundle.EMPTY, upgraded.state().player(seat).holdings());

        GameState levelTwo = withCity(window, seat, 2, new ResourceBundle(4, 4, 4, 2, 4));
        assertEquals(List.of(new DomainEvent.CityUpgraded(seat, 3, new ResourceBundle(4, 4, 4, 2, 4))),
                accept(levelTwo, new UpgradeCity(seat), ruleset).events());
    }

    @Test
    void technologyBoomDiscountEndsWithTheRound() {
        GameState round2 = nextRoundWith(roundOneWindow(ruleset), card("TECHNOLOGY_BOOM"), ruleset).state();
        GameState round3 = accept(accept(round2, new ResolveRound(), ruleset).state(), new StartRound(), ruleset)
                .state();
        int seat = seatOf(round3, CityType.AGRICULTURAL);
        round3 = withCity(round3, seat, 1, new ResourceBundle(3, 3, 3, 1, 0));

        assertRejected(round3, new UpgradeCity(seat), ruleset, RejectionCode.INSUFFICIENT_RESOURCES);
    }

    @Test
    void technologyBoomDoesNotChangeBuildingCosts() {
        GameState window = nextRoundWith(roundOneWindow(ruleset), card("TECHNOLOGY_BOOM"), ruleset).state();
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 1, new ResourceBundle(0, 1, 1, 1, 0));

        assertEquals(List.of(new DomainEvent.BuildingBuilt(seat, "WAREHOUSE", new ResourceBundle(0, 1, 1, 1, 0))),
                accept(window, new BuildBuilding(seat, "WAREHOUSE"), ruleset).events());
    }

    // --- 8 Recession ---

    @Test
    void recessionMeansNoMoneyIncomeInTheEventRoundOnly() {
        GameState window = roundOneWindow(ruleset);
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 1, ResourceBundle.EMPTY);

        // Active from step 1.1, so this round's production already has no Money; resources are still produced.
        GameState round2 = nextRoundWith(window, card("RECESSION"), ruleset).state();
        assertEquals(new ResourceBundle(3, 1, 1, 1, 0), round2.player(seat).holdings());

        GameState round3 = accept(accept(round2, new ResolveRound(), ruleset).state(), new StartRound(), ruleset)
                .state();
        assertEquals(new ResourceBundle(6, 2, 2, 2, 2), round3.player(seat).holdings());
    }

    // --- 9 Trade Disruption ---

    @Test
    void tradeDisruptionMakesBuyingTwoMoneyDearerAndSellingOneMoneyCheaperButAtLeastOne() {
        GameState round2 = nextRoundWith(roundOneWindow(ruleset), card("TRADE_DISRUPTION"), ruleset).state();
        for (Resource resource : Resource.values()) {
            assertEquals(6, Market.buyCost(round2, resource, ruleset), "B: 4 + 2");
            assertEquals(1, Market.sellValue(round2, resource, ruleset), "B: 1 - 1, min 1");
        }

        GameState atD = nextRoundWith(roundOneWindow(ruleset).withMarket(MarketPrices.allAt(STEP_D)),
                card("TRADE_DISRUPTION"), ruleset).state();
        assertEquals(8, Market.buyCost(atD, Resource.FOOD, ruleset), "D: 6 + 2");
        assertEquals(2, Market.sellValue(atD, Resource.FOOD, ruleset), "D: 3 - 1");

        GameState atC = nextRoundWith(roundOneWindow(ruleset).withMarket(MarketPrices.allAt(STEP_C)),
                card("TRADE_DISRUPTION"), ruleset).state();
        assertEquals(1, Market.sellValue(atC, Resource.FOOD, ruleset), "C: 2 - 1");
    }

    @Test
    void tradeDisruptionPricesAreUsedByMarketCommandsAndEndWithTheRound() {
        GameState round2 = nextRoundWith(roundOneWindow(ruleset), card("TRADE_DISRUPTION"), ruleset).state();
        int seat = seatOf(round2, CityType.AGRICULTURAL);
        round2 = withCity(round2, seat, 1, new ResourceBundle(2, 0, 0, 0, 12));

        GameResult.Accepted bought = accept(round2, new BuyFromMarket(seat, Resource.ENERGY, 2), ruleset);
        assertEquals(List.of(new DomainEvent.MarketBought(seat, Resource.ENERGY, 2, 12)), bought.events());
        GameResult.Accepted sold = accept(bought.state(), new SellToMarket(seat, Resource.FOOD, 2), ruleset);
        assertEquals(List.of(new DomainEvent.MarketSold(seat, Resource.FOOD, 2, 2)), sold.events());

        GameState resolved = accept(sold.state(), new ResolveRound(), ruleset).state();
        assertEquals(4, Market.buyCost(resolved, Resource.ENERGY, ruleset));
    }

    // --- 12 Good Harvest Year ---

    @Test
    void goodHarvestYearGivesEveryCityOneFoodAndOneEnergyInTheEventRoundOnly() {
        GameState window = roundOneWindow(ruleset);
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            window = withCity(window, seat, 1, ResourceBundle.EMPTY);
        }

        GameState round2 = nextRoundWith(window, card("GOOD_HARVEST_YEAR"), ruleset).state();
        int agricultural = seatOf(round2, CityType.AGRICULTURAL);
        int energy = seatOf(round2, CityType.ENERGY);
        assertEquals(new ResourceBundle(4, 2, 1, 1, 2), round2.player(agricultural).holdings());
        assertEquals(new ResourceBundle(2, 4, 1, 1, 2), round2.player(energy).holdings());

        GameState round3 = accept(accept(round2, new ResolveRound(), ruleset).state(), new StartRound(), ruleset)
                .state();
        assertEquals(new ResourceBundle(7, 3, 2, 2, 4), round3.player(agricultural).holdings());
    }
}
