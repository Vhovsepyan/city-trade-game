package citytrade.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.GameEngine;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.economy.Storage;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.BuildingEffect;
import citytrade.engine.ruleset.BuildingRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.BuiltBuilding;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.state.MarketActivity;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.RegionalOpportunity;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BaselineBotTest {

    private static final int SEAT = 0;

    private final Ruleset ruleset = TestRulesets.prototype();
    private final BaselineBot bot = new BaselineBot();
    private GameState window;

    /** The window of Round 1 (no active event, so costs and prices are the plain ruleset values). */
    @BeforeEach
    void startRoundOne() {
        GameState state = GameSetup.create(3, ruleset);
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            state = accept(state, bot.chooseObjectives(state, seat, ruleset));
        }
        window = accept(state, new StartRound());
        assertEquals(GamePhase.WINDOW, window.phase());
        assertTrue(window.activeEvent().isEmpty());
    }

    @Test
    void keepsTheFirstDealtObjectives() {
        GameState state = GameSetup.create(3, ruleset);
        ChooseObjectives choice = bot.chooseObjectives(state, SEAT, ruleset);

        var dealt = state.player(SEAT).dealtObjectives();
        assertEquals(ruleset.objectives().keptPerPlayer(), choice.keptObjectiveIds().size());
        for (int i = 0; i < choice.keptObjectiveIds().size(); i++) {
            assertEquals(dealt.get(i).id(), choice.keptObjectiveIds().get(i));
        }
        accept(state, choice);
    }

    @Test
    void upgradesWhenHoldingsCoverTheCost() {
        GameState state = withHoldings(window, levelTwoCost());

        assertEquals(Optional.of(new UpgradeCity(SEAT)), next(state));
    }

    @Test
    void buysAllMissingUpgradeResourcesFromTheMarketThenUpgrades() {
        ResourceBundle cost = levelTwoCost();
        int money = cost.money() + Market.buyCost(window, Resource.FOOD, ruleset)
                + 2 * Market.buyCost(window, Resource.ENERGY, ruleset);
        ResourceBundle holdings = cost.with(Resource.FOOD, cost.food() - 1)
                .with(Resource.ENERGY, cost.energy() - 2).withMoney(money);
        GameState state = withHoldings(window, holdings);

        assertEquals(Optional.of(new BuyFromMarket(SEAT, Resource.FOOD, 1)), next(state));
        state = accept(state, next(state).orElseThrow());
        assertEquals(Optional.of(new BuyFromMarket(SEAT, Resource.ENERGY, 2)), next(state));
        state = accept(state, next(state).orElseThrow());
        assertEquals(Optional.of(new UpgradeCity(SEAT)), next(state));
        state = accept(state, next(state).orElseThrow());
        assertEquals(2, state.player(SEAT).level());
    }

    @Test
    void buysNothingWhenTheMoneyDoesNotCoverTheWholeUpgrade() {
        ResourceBundle cost = levelTwoCost();
        int money = cost.money() + Market.buyCost(window, Resource.FOOD, ruleset)
                + 2 * Market.buyCost(window, Resource.ENERGY, ruleset) - 1;
        ResourceBundle holdings = cost.with(Resource.FOOD, cost.food() - 1)
                .with(Resource.ENERGY, cost.energy() - 2).withMoney(money);

        assertEquals(Optional.empty(), next(withHoldings(window, holdings)));
    }

    @Test
    void buysNothingWhenTheBuyLimitLeftIsTooSmall() {
        ResourceBundle cost = levelTwoCost();
        ResourceBundle holdings = cost.with(Resource.FOOD, cost.food() - 2).withMoney(1_000);
        GameState state = withHoldings(window, holdings);
        int limit = ruleset.market().maxBuyPerResourcePerRound();
        PlayerState player = state.player(SEAT);
        state = state.withPlayer(player.withMarketThisRound(
                MarketActivity.NONE.withBought(Resource.FOOD, limit - 1)));

        assertEquals(Optional.empty(), next(state));
    }

    @Test
    void buildsNothingWhileAnUpgradeIsStillPossibleThisRound() {
        BuildingRules first = ruleset.buildings().getFirst();
        assertTrue(!first.cost().covers(levelTwoCost()));

        assertEquals(Optional.empty(), next(withHoldings(window, first.cost())));
    }

    @Test
    void buildsInRulesetOrderAfterUpgradingThisRound() {
        BuildingRules first = ruleset.buildings().getFirst();
        GameState state = withHoldings(window, levelTwoCost().plus(first.cost()));
        state = accept(state, next(state).orElseThrow());

        assertEquals(Optional.of(new BuildBuilding(SEAT, first.id(), Optional.empty())), next(state));
        state = accept(state, next(state).orElseThrow());
        assertTrue(state.player(SEAT).hasBuilt(first.id()));
    }

    @Test
    void skipsBuildingsAlreadyBuiltOrNotAffordable() {
        GameState upgraded = upgradedThisRound(window);
        int level = upgraded.player(SEAT).level();
        BuildingRules target = ruleset.buildings().stream()
                .filter(building -> building.requiredLevel() <= level)
                .reduce((a, b) -> b).orElseThrow();
        // Every unlocked building before the target is either built already or not covered by the target's cost.
        PlayerState player = upgraded.player(SEAT);
        boolean skipsUnaffordable = false;
        for (BuildingRules building : ruleset.buildings()) {
            if (building == target) {
                break;
            }
            if (building.requiredLevel() <= level && target.cost().covers(building.cost())) {
                player = player.withBuilding(new BuiltBuilding(building.id(), 1, choiceIfNeeded(building, player)));
            } else if (building.requiredLevel() <= level) {
                skipsUnaffordable = true;
            }
        }
        assertTrue(skipsUnaffordable, "the ruleset should have an unaffordable building before " + target.id());
        GameState state = withHoldings(upgraded.withPlayer(player), target.cost());

        assertEquals(Optional.of(new BuildBuilding(SEAT, target.id(), choiceFor(target, player))), next(state));
        accept(state, next(state).orElseThrow());
    }

    @Test
    void buildsNothingAboveItsLevel() {
        GameState upgraded = upgradedThisRound(window);
        int level = upgraded.player(SEAT).level();
        PlayerState player = upgraded.player(SEAT);
        for (BuildingRules building : ruleset.buildings()) {
            if (building.requiredLevel() <= level) {
                player = player.withBuilding(new BuiltBuilding(building.id(), 1, choiceIfNeeded(building, player)));
            }
        }
        BuildingRules tooHigh = ruleset.buildings().stream()
                .filter(building -> building.requiredLevel() > level)
                .findFirst().orElseThrow();

        assertEquals(Optional.empty(), next(withHoldings(upgraded.withPlayer(player), tooHigh.cost())));
    }

    @Test
    void chosenProductionBuildingTakesTheNonSpecialtyResourceHeldLeast() {
        BuildingRules chosenProduction = ruleset.buildings().stream()
                .filter(building -> building.effect() instanceof BuildingEffect.ChosenNonSpecialtyProduction)
                .findFirst().orElseThrow();
        GameState state = upgradedThisRound(window);
        PlayerState player = state.player(SEAT);
        for (BuildingRules building : ruleset.buildings()) {
            if (building != chosenProduction && building.requiredLevel() <= player.level()) {
                player = player.withBuilding(new BuiltBuilding(building.id(), 1, choiceIfNeeded(building, player)));
            }
        }
        state = state.withPlayer(player);
        Resource specialty = player.city().specialty();
        Resource least = Resource.values()[(specialty.ordinal() + 2) % Resource.values().length];
        ResourceBundle holdings = chosenProduction.cost().plus(new ResourceBundle(3, 3, 3, 3, 0))
                .with(least, chosenProduction.cost().amountOf(least))
                .with(specialty, chosenProduction.cost().amountOf(specialty));
        state = withHoldings(state, holdings);
        assertTrue(state.player(SEAT).holdings().covers(chosenProduction.cost()));

        BuildBuilding build = assertInstanceOf(BuildBuilding.class, next(state).orElseThrow());
        assertEquals(chosenProduction.id(), build.buildingId());
        assertEquals(Optional.of(least), build.chosenResource());
        accept(state, build);
    }

    @Test
    void sellsUnitsAboveTheStorageLimit() {
        PlayerState player = window.player(SEAT);
        Resource specialty = player.city().specialty();
        int limit = Storage.limitOf(player, window.round(), ruleset);
        GameState state = withHoldings(window, ResourceBundle.EMPTY.with(specialty, limit + 3));

        assertEquals(Optional.of(new SellToMarket(SEAT, specialty, 3)), next(state));
        state = accept(state, next(state).orElseThrow());
        assertEquals(limit, state.player(SEAT).holdings().amountOf(specialty));
        assertEquals(Optional.empty(), next(state));
    }

    @Test
    void neverSpendsMoneyReservedByBids() {
        ResourceBundle cost = levelTwoCost();
        ResourceBundle holdings = cost.with(Resource.FOOD, cost.food() - 1)
                .withMoney(cost.money() + Market.buyCost(window, Resource.FOOD, ruleset));
        GameState state = withHoldings(window, holdings);
        assertEquals(Optional.of(new BuyFromMarket(SEAT, Resource.FOOD, 1)), next(state));

        GameState reserved = withReservedMoney(state, 1);

        assertEquals(Optional.empty(), next(reserved));
    }

    @Test
    void doesNothingWithNothingToSpend() {
        assertEquals(Optional.empty(), next(withHoldings(window, ResourceBundle.EMPTY)));
    }

    private Optional<GameCommand> next(GameState state) {
        return bot.nextWindowCommand(state, SEAT, ruleset);
    }

    private ResourceBundle levelTwoCost() {
        return ruleset.level(2).upgradeCost();
    }

    private GameState upgradedThisRound(GameState state) {
        GameState upgraded = accept(withHoldings(state, levelTwoCost()), new UpgradeCity(SEAT));
        assertEquals(upgraded.round(), upgraded.player(SEAT).lastUpgradeRound());
        return upgraded;
    }

    /** Any valid D4 choice, for buildings the test marks as built directly. */
    private static Optional<Resource> choiceIfNeeded(BuildingRules building, PlayerState player) {
        if (!(building.effect() instanceof BuildingEffect.ChosenNonSpecialtyProduction)) {
            return Optional.empty();
        }
        return Arrays.stream(Resource.values())
                .filter(resource -> resource != player.city().specialty()).findFirst();
    }

    /** The bot's D4 choice for a building bought with exactly its cost: the non-specialty resource held least. */
    private static Optional<Resource> choiceFor(BuildingRules building, PlayerState player) {
        if (!(building.effect() instanceof BuildingEffect.ChosenNonSpecialtyProduction)) {
            return Optional.empty();
        }
        return Arrays.stream(Resource.values())
                .filter(resource -> resource != player.city().specialty())
                .min(Comparator.comparingInt(building.cost()::amountOf));
    }

    private static GameState withHoldings(GameState state, ResourceBundle holdings) {
        return state.withPlayer(state.player(SEAT).withHoldings(holdings));
    }

    /** Reserves {@code amount} Money with a real bid on an open opportunity, placed through the engine. */
    private GameState withReservedMoney(GameState state, int amount) {
        // Round 1 has no revealed opportunity yet, so the top card is put face up as step 2.3 would do.
        GameState withOpportunity = state.opportunities().isEmpty()
                ? state.withRevealedOpportunity(
                        RegionalOpportunity.revealed(state.opportunityDeck().getFirst(), state.round()))
                : state;
        String id = withOpportunity.opportunities().getFirst().id();
        GameState result = accept(withOpportunity, new PlaceBid(SEAT, id, amount));
        assertEquals(amount, result.reservedMoney(SEAT));
        return result;
    }

    private GameState accept(GameState state, GameCommand command) {
        GameResult result = GameEngine.apply(state, command, ruleset);
        return assertInstanceOf(GameResult.Accepted.class, result, () -> command + " -> " + result).state();
    }
}
