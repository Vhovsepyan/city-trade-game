package citytrade.engine.market;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import citytrade.engine.state.MarketActivity;
import citytrade.engine.state.MarketPrices;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarketTest {

    private static final long SEED = 11;
    private static final int STEP_A = 0;
    private static final int STEP_B = 1;
    private static final int STEP_C = 2;
    private static final int STEP_D = 3;
    private static final ResourceBundle RICH = new ResourceBundle(10, 10, 10, 10, 100);

    private final Ruleset ruleset = TestRulesets.standard();

    /** Round 1 window; every player has plenty of resources and Money. */
    private GameState window() {
        GameState state = accept(readyForRoundOne(SEED, ruleset), new StartRound(), ruleset).state();
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            state = withCity(state, seat, 1, RICH);
        }
        return state;
    }

    private GameState apply(GameState state, GameCommand... commands) {
        for (GameCommand command : commands) {
            state = accept(state, command, ruleset).state();
        }
        return state;
    }

    @Test
    void allResourcesStartAtStepB() {
        GameState state = GameSetup.create(SEED, ruleset);
        for (Resource resource : Resource.values()) {
            assertEquals(STEP_B, state.market().stepIndexOf(resource));
            assertEquals(4, Market.buyCost(state, resource, ruleset));
            assertEquals(1, Market.sellValue(state, resource, ruleset));
        }
    }

    @Test
    void buyPaysTheCurrentStepCostPerUnit() {
        GameResult.Accepted result = accept(window(), new BuyFromMarket(0, Resource.ENERGY, 3), ruleset);

        assertEquals(new ResourceBundle(10, 13, 10, 10, 88), result.state().player(0).holdings());
        assertEquals(List.of(new DomainEvent.MarketBought(0, Resource.ENERGY, 3, 12)), result.events());
    }

    @Test
    void sellGetsTheCurrentStepValuePerUnit() {
        GameState state = window().withMarket(MarketPrices.allAt(STEP_D));

        GameResult.Accepted result = accept(state, new SellToMarket(2, Resource.FOOD, 5), ruleset);

        assertEquals(new ResourceBundle(5, 10, 10, 10, 115), result.state().player(2).holdings());
        assertEquals(List.of(new DomainEvent.MarketSold(2, Resource.FOOD, 5, 15)), result.events());
    }

    @Test
    void eachStepHasItsOwnPrices() {
        int[] buyCosts = {3, 4, 5, 6};
        int[] sellValues = {1, 1, 2, 3};
        for (int step = STEP_A; step <= STEP_D; step++) {
            GameState state = window().withMarket(MarketPrices.allAt(step));
            GameState afterBuy = apply(state, new BuyFromMarket(0, Resource.MATERIALS, 1));
            assertEquals(100 - buyCosts[step], afterBuy.player(0).holdings().money());
            GameState afterSell = apply(state, new SellToMarket(0, Resource.MATERIALS, 1));
            assertEquals(100 + sellValues[step], afterSell.player(0).holdings().money());
        }
    }

    @Test
    void buyLimitIsPerPlayerPerResourcePerRound() {
        GameState state = apply(window(), new BuyFromMarket(0, Resource.FOOD, 3));
        assertRejected(state, new BuyFromMarket(0, Resource.FOOD, 2), ruleset, RejectionCode.MARKET_BUY_LIMIT_EXCEEDED);

        state = apply(state, new BuyFromMarket(0, Resource.FOOD, 1));
        assertRejected(state, new BuyFromMarket(0, Resource.FOOD, 1), ruleset, RejectionCode.MARKET_BUY_LIMIT_EXCEEDED);

        // Other resources and other players have their own limit.
        state = apply(state, new BuyFromMarket(0, Resource.ENERGY, 4), new BuyFromMarket(1, Resource.FOOD, 4));
        assertEquals(14, state.player(0).holdings().food());
        assertEquals(14, state.player(1).holdings().food());
    }

    @Test
    void buyLimitStartsAgainNextRound() {
        GameState state = apply(window(), new BuyFromMarket(0, Resource.FOOD, 4));
        state = apply(state, new ResolveRound(), new StartRound());
        state = withCity(state, 0, 1, RICH);

        GameState next = apply(state, new BuyFromMarket(0, Resource.FOOD, 4));
        assertEquals(14, next.player(0).holdings().food());
    }

    @Test
    void sellingHasNoLimitAndDoesNotUseTheBuyLimit() {
        GameState state = apply(window(), new SellToMarket(0, Resource.FOOD, 10), new BuyFromMarket(0, Resource.FOOD, 4));
        assertEquals(4, state.player(0).holdings().food());
    }

    @Test
    void buyWithTooLittleMoneyIsRejectedAsAWholeAndUsesNoLimit() {
        GameState state = withCity(window(), 0, 1, new ResourceBundle(0, 0, 0, 0, 11));

        assertRejected(state, new BuyFromMarket(0, Resource.TECHNOLOGY, 3), ruleset, RejectionCode.INSUFFICIENT_MONEY);
        assertEquals(MarketActivity.NONE, state.player(0).marketThisRound());

        // Exactly enough Money is fine; the rejected attempt did not count toward the limit.
        state = withCity(state, 0, 1, new ResourceBundle(0, 0, 0, 0, 16));
        state = apply(state, new BuyFromMarket(0, Resource.TECHNOLOGY, 4));
        assertEquals(new ResourceBundle(0, 0, 0, 4, 0), state.player(0).holdings());
    }

    @Test
    void sellingMoreThanOwnedIsRejected() {
        GameState state = withCity(window(), 0, 1, new ResourceBundle(2, 0, 0, 0, 0));
        assertRejected(state, new SellToMarket(0, Resource.FOOD, 3), ruleset, RejectionCode.INSUFFICIENT_RESOURCES);
        assertRejected(state, new SellToMarket(0, Resource.ENERGY, 1), ruleset, RejectionCode.INSUFFICIENT_RESOURCES);
    }

    @Test
    void resourcesBoughtThisWindowCanBeSoldRightAway() {
        GameState state = withCity(window(), 0, 1, new ResourceBundle(0, 0, 0, 0, 4));
        state = apply(state, new BuyFromMarket(0, Resource.ENERGY, 1), new SellToMarket(0, Resource.ENERGY, 1));
        assertEquals(new ResourceBundle(0, 0, 0, 0, 1), state.player(0).holdings());
    }

    @Test
    void quantityMustBePositive() {
        GameState state = window();
        for (int quantity : new int[] {0, -1, Integer.MIN_VALUE}) {
            assertRejected(state, new BuyFromMarket(0, Resource.FOOD, quantity), ruleset, RejectionCode.INVALID_QUANTITY);
            assertRejected(state, new SellToMarket(0, Resource.FOOD, quantity), ruleset, RejectionCode.INVALID_QUANTITY);
        }
    }

    @Test
    void hugeQuantitiesAreRejectedWithoutOverflow() {
        GameState state = window();
        assertRejected(state, new BuyFromMarket(0, Resource.FOOD, Integer.MAX_VALUE), ruleset,
                RejectionCode.MARKET_BUY_LIMIT_EXCEEDED);
        assertRejected(state, new SellToMarket(0, Resource.FOOD, Integer.MAX_VALUE), ruleset,
                RejectionCode.INSUFFICIENT_RESOURCES);
    }

    @Test
    void unknownSeatIsRejected() {
        GameState state = window();
        assertRejected(state, new BuyFromMarket(4, Resource.FOOD, 1), ruleset, RejectionCode.UNKNOWN_PLAYER);
        assertRejected(state, new SellToMarket(-1, Resource.FOOD, 1), ruleset, RejectionCode.UNKNOWN_PLAYER);
    }

    @Test
    void marketIsOnlyOpenDuringTheWindow() {
        GameState setup = readyForRoundOne(SEED, ruleset);
        assertRejected(setup, new BuyFromMarket(0, Resource.FOOD, 1), ruleset, RejectionCode.INVALID_PHASE);
        assertRejected(setup, new SellToMarket(0, Resource.FOOD, 1), ruleset, RejectionCode.INVALID_PHASE);

        GameState resolution = apply(window(), new ResolveRound());
        assertRejected(resolution, new BuyFromMarket(0, Resource.FOOD, 1), ruleset, RejectionCode.INVALID_PHASE);
        assertRejected(resolution, new SellToMarket(0, Resource.FOOD, 1), ruleset, RejectionCode.INVALID_PHASE);
    }

    @Test
    void pricesStayFixedDuringTheWindowAndMoveAtResolution() {
        GameState state = window();
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            state = apply(state, new BuyFromMarket(seat, Resource.FOOD, 4));
            assertEquals(STEP_B, state.market().stepIndexOf(Resource.FOOD));
            assertEquals(100 - 16, state.player(seat).holdings().money(), "every buyer pays the same price");
        }

        GameResult.Accepted resolved = accept(state, new ResolveRound(), ruleset);
        assertEquals(STEP_C, resolved.state().market().stepIndexOf(Resource.FOOD), "moves only one step");
        assertTrue(resolved.events().contains(new DomainEvent.MarketPriceMoved(Resource.FOOD, STEP_B, STEP_C)));
    }

    @Test
    void netAtThresholdMovesUpAndBelowDoesNot() {
        GameState up = apply(window(), new BuyFromMarket(0, Resource.ENERGY, 2), new BuyFromMarket(1, Resource.ENERGY, 1));
        assertEquals(STEP_C, apply(up, new ResolveRound()).market().stepIndexOf(Resource.ENERGY));

        GameState same = apply(window(), new BuyFromMarket(0, Resource.ENERGY, 2));
        GameResult.Accepted resolved = accept(same, new ResolveRound(), ruleset);
        assertEquals(STEP_B, resolved.state().market().stepIndexOf(Resource.ENERGY));
        assertTrue(resolved.events().stream().noneMatch(DomainEvent.MarketPriceMoved.class::isInstance));
    }

    @Test
    void netAtMinusThresholdMovesDownAndAboveDoesNot() {
        GameState down = apply(window(), new SellToMarket(0, Resource.MATERIALS, 1),
                new SellToMarket(3, Resource.MATERIALS, 2));
        GameResult.Accepted resolved = accept(down, new ResolveRound(), ruleset);
        assertEquals(STEP_A, resolved.state().market().stepIndexOf(Resource.MATERIALS));
        assertTrue(resolved.events().contains(new DomainEvent.MarketPriceMoved(Resource.MATERIALS, STEP_B, STEP_A)));

        GameState same = apply(window(), new SellToMarket(0, Resource.MATERIALS, 2));
        assertEquals(STEP_B, apply(same, new ResolveRound()).market().stepIndexOf(Resource.MATERIALS));
    }

    @Test
    void buyingAndSellingCancelOutInTheNet() {
        // 4 bought - 3 sold = +1: no movement, although both sides alone would move the price.
        GameState state = apply(window(), new BuyFromMarket(0, Resource.TECHNOLOGY, 4),
                new SellToMarket(1, Resource.TECHNOLOGY, 3));
        assertEquals(STEP_B, apply(state, new ResolveRound()).market().stepIndexOf(Resource.TECHNOLOGY));
    }

    @Test
    void eachResourceMovesSeparately() {
        GameState state = apply(window(), new BuyFromMarket(0, Resource.FOOD, 3), new SellToMarket(0, Resource.ENERGY, 3),
                new BuyFromMarket(1, Resource.MATERIALS, 1));
        MarketPrices prices = apply(state, new ResolveRound()).market();
        assertEquals(STEP_C, prices.stepIndexOf(Resource.FOOD));
        assertEquals(STEP_A, prices.stepIndexOf(Resource.ENERGY));
        assertEquals(STEP_B, prices.stepIndexOf(Resource.MATERIALS));
        assertEquals(STEP_B, prices.stepIndexOf(Resource.TECHNOLOGY));
    }

    @Test
    void priceStaysWithinTheFirstAndLastStep() {
        GameState top = window().withMarket(MarketPrices.allAt(STEP_D));
        top = apply(top, new BuyFromMarket(0, Resource.FOOD, 4));
        GameResult.Accepted resolvedTop = accept(top, new ResolveRound(), ruleset);
        assertEquals(STEP_D, resolvedTop.state().market().stepIndexOf(Resource.FOOD));
        assertTrue(resolvedTop.events().stream().noneMatch(DomainEvent.MarketPriceMoved.class::isInstance));

        GameState bottom = window().withMarket(MarketPrices.allAt(STEP_A));
        bottom = apply(bottom, new SellToMarket(0, Resource.FOOD, 10));
        assertEquals(STEP_A, apply(bottom, new ResolveRound()).market().stepIndexOf(Resource.FOOD));
    }

    @Test
    void newPriceAppliesInTheNextRoundsWindow() {
        GameState state = apply(window(), new BuyFromMarket(0, Resource.FOOD, 3));
        state = apply(state, new ResolveRound(), new StartRound());
        state = withCity(state, 0, 1, RICH);

        GameResult.Accepted result = accept(state, new BuyFromMarket(0, Resource.FOOD, 1), ruleset);
        assertEquals(List.of(new DomainEvent.MarketBought(0, Resource.FOOD, 1, 5)), result.events());
    }

    @Test
    void roundActivityIsClearedAtResolutionSoItCountsOnlyOnce() {
        GameState state = apply(window(), new BuyFromMarket(0, Resource.FOOD, 3));
        state = apply(state, new ResolveRound());
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            assertEquals(MarketActivity.NONE, state.player(seat).marketThisRound());
        }

        // A round without market trades does not move the price again.
        state = apply(state, new StartRound(), new ResolveRound());
        assertEquals(STEP_C, state.market().stepIndexOf(Resource.FOOD));
    }
}
