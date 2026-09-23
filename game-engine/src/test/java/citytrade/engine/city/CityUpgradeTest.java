package citytrade.engine.city;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.seatOf;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.CityType;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.economy.Production;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.List;
import org.junit.jupiter.api.Test;

class CityUpgradeTest {

    private static final long SEED = 11;
    private static final ResourceBundle RICH = new ResourceBundle(10, 10, 10, 10, 10);

    private final Ruleset ruleset = TestRulesets.standard();
    private final GameState ready = readyForRoundOne(SEED, ruleset);
    private final int seat = seatOf(ready, CityType.INDUSTRIAL);

    private GameState roundOneWindow(int level, ResourceBundle holdings) {
        return withCity(accept(ready, new StartRound(), ruleset).state(), seat, level, holdings);
    }

    @Test
    void upgradePaysTheLevelCostAndRaisesTheLevelAtOnce() {
        GameState window = roundOneWindow(1, RICH);

        GameResult.Accepted result = accept(window, new UpgradeCity(seat), ruleset);

        PlayerState after = result.state().player(seat);
        assertEquals(2, after.level());
        assertEquals(new ResourceBundle(7, 7, 7, 7, 10), after.holdings());
        assertEquals(0, after.prestige(), "Prestige is added at resolution, not in the window");
        assertEquals(List.of(new DomainEvent.CityUpgraded(seat, 2, new ResourceBundle(3, 3, 3, 3, 0))),
                result.events());
    }

    @Test
    void levelThreeCostIncludesMoney() {
        GameState window = roundOneWindow(2, RICH);

        PlayerState after = accept(window, new UpgradeCity(seat), ruleset).state().player(seat);

        assertEquals(3, after.level());
        assertEquals(new ResourceBundle(6, 6, 6, 6, 6), after.holdings());
    }

    @Test
    void onlyOneLevelPerRound() {
        GameState upgraded = accept(roundOneWindow(1, RICH), new UpgradeCity(seat), ruleset).state();

        assertRejected(upgraded, new UpgradeCity(seat), ruleset, RejectionCode.ALREADY_UPGRADED_THIS_ROUND);

        GameState nextWindow = accept(accept(upgraded, new ResolveRound(), ruleset).state(), new StartRound(), ruleset)
                .state();
        assertEquals(3, accept(nextWindow, new UpgradeCity(seat), ruleset).state().player(seat).level());
    }

    @Test
    void cannotGoAboveTheHighestLevel() {
        assertRejected(roundOneWindow(3, RICH), new UpgradeCity(seat), ruleset, RejectionCode.MAX_LEVEL_REACHED);
    }

    @Test
    void missingResourcesOrMoneyRejectAndLeaveTheStateUnchanged() {
        GameState shortOfMaterials = roundOneWindow(1, new ResourceBundle(3, 3, 2, 3, 10));
        assertRejected(shortOfMaterials, new UpgradeCity(seat), ruleset, RejectionCode.INSUFFICIENT_RESOURCES);

        GameState shortOfMoney = roundOneWindow(2, new ResourceBundle(4, 4, 4, 4, 3));
        assertRejected(shortOfMoney, new UpgradeCity(seat), ruleset, RejectionCode.INSUFFICIENT_MONEY);
        assertEquals(2, shortOfMoney.player(seat).level());
    }

    @Test
    void upgradeIsOnlyAllowedInTheWindow() {
        assertRejected(ready, new UpgradeCity(seat), ruleset, RejectionCode.INVALID_PHASE);
        GameState resolved = accept(roundOneWindow(1, RICH), new ResolveRound(), ruleset).state();
        assertRejected(resolved, new UpgradeCity(seat), ruleset, RejectionCode.INVALID_PHASE);
    }

    @Test
    void unknownSeatIsRejected() {
        assertRejected(roundOneWindow(1, RICH), new UpgradeCity(7), ruleset, RejectionCode.UNKNOWN_PLAYER);
    }

    @Test
    void levelPrestigeIsAddedAtResolutionOfTheUpgradeRoundOnly() {
        GameState upgraded = accept(roundOneWindow(1, RICH), new UpgradeCity(seat), ruleset).state();

        GameResult.Accepted resolved = accept(upgraded, new ResolveRound(), ruleset);
        assertEquals(1, resolved.state().player(seat).prestige());
        assertTrue(resolved.events().contains(new DomainEvent.PrestigeGained(seat, 1)));

        GameState roundTwo = accept(resolved.state(), new StartRound(), ruleset).state();
        GameState roundTwoUpgraded = accept(roundTwo, new UpgradeCity(seat), ruleset).state();
        GameResult.Accepted resolvedTwo = accept(roundTwoUpgraded, new ResolveRound(), ruleset);
        assertEquals(1 + 2, resolvedTwo.state().player(seat).prestige(), "Level 3 adds 2");
        assertTrue(resolvedTwo.events().contains(new DomainEvent.PrestigeGained(seat, 2)));

        GameState roundThree = accept(resolvedTwo.state(), new StartRound(), ruleset).state();
        GameResult.Accepted resolvedThree = accept(roundThree, new ResolveRound(), ruleset);
        assertEquals(3, resolvedThree.state().player(seat).prestige(), "no Prestige again for the same level");
        assertTrue(resolvedThree.events().stream().noneMatch(DomainEvent.PrestigeGained.class::isInstance));
    }

    @Test
    void newProductionAndUpkeepStartNextRound() {
        GameState window = roundOneWindow(1, RICH);
        GameState upgraded = accept(window, new UpgradeCity(seat), ruleset).state();
        ResourceBundle before = upgraded.player(seat).holdings();

        GameResult.Accepted nextRound = accept(accept(upgraded, new ResolveRound(), ruleset).state(),
                new StartRound(), ruleset);

        // Level 2 Industrial: M 4, others 1, Money 3; upkeep 1 (Food: most held, first in the tie order).
        assertEquals(before.plus(new ResourceBundle(1, 1, 4, 1, 3)).minus(new ResourceBundle(1, 0, 0, 0, 0)),
                nextRound.state().player(seat).holdings());
        assertTrue(nextRound.events().contains(
                new DomainEvent.UpkeepPaid(seat, new ResourceBundle(1, 0, 0, 0, 0), 0)));
        assertEquals(new ResourceBundle(1, 1, 4, 1, 3),
                Production.productionOf(nextRound.state().player(seat), 2, ruleset));
    }
}
