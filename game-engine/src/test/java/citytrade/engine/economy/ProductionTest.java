package citytrade.engine.economy;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.seatOf;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import citytrade.engine.CityType;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.ruleset.StrainedRules;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class ProductionTest {

    private static final long SEED = 7;

    private final Ruleset ruleset = TestRulesets.standard();

    @ParameterizedTest(name = "level {0}: specialty {1}, others {2}, Money {3}")
    @CsvSource({"1, 3, 1, 2, 0", "2, 4, 1, 3, 1", "3, 5, 1, 4, 2"})
    void everyCityProducesTheValuesOfItsLevel(int level, int specialty, int other, int money, int upkeep) {
        GameState state = readyForRoundOne(SEED, ruleset);
        ResourceBundle start = new ResourceBundle(1, 1, 1, 1, 0);
        for (int seat = 0; seat < 4; seat++) {
            state = withCity(state, seat, level, start);
        }

        GameState window = accept(state, new StartRound(), ruleset).state();

        for (PlayerState player : window.players()) {
            ResourceBundle expected = new ResourceBundle(other, other, other, other, money)
                    .with(player.city().specialty(), specialty);
            assertEquals(expected, Production.productionOf(player, 1, ruleset), player.city().toString());
            // Upkeep (step 1.4) runs after production but never touches the specialty or Money.
            assertEquals(1 + specialty, player.holdings().amountOf(player.city().specialty()));
            assertEquals(money, player.holdings().money());
            int othersHeld = Upkeep.allowedResources(player).stream().mapToInt(player.holdings()::amountOf).sum();
            assertEquals(3 * (1 + other) - upkeep, othersHeld, player.city().toString());
        }
    }

    @Test
    void levelOneAgriculturalCityGetsThreeFoodOneOfEachOtherAndTwoMoney() {
        GameState state = readyForRoundOne(SEED, ruleset);
        int seat = seatOf(state, CityType.AGRICULTURAL);

        GameState window = accept(state, new StartRound(), ruleset).state();

        // Start F4 E1 M1 T1 $6 + production F3 E1 M1 T1 $2 (level 1 has no upkeep).
        assertEquals(new ResourceBundle(7, 2, 2, 2, 8), window.player(seat).holdings());
    }

    @Test
    void activeStrainedPenaltyLowersSpecialtyAndMoneyOnly() {
        GameState state = readyForRoundOne(SEED, ruleset);
        PlayerState industrial = state.player(seatOf(state, CityType.INDUSTRIAL)).withStrained(false, true);

        // Level 1: specialty 3 - 2, Money 2 - 1; other resources unchanged.
        assertEquals(new ResourceBundle(1, 1, 1, 1, 1), Production.productionOf(industrial, 1, ruleset));
    }

    @Test
    void strainedFlagAloneDoesNotLowerThisRoundsProduction() {
        GameState state = readyForRoundOne(SEED, ruleset);
        PlayerState industrial = state.player(seatOf(state, CityType.INDUSTRIAL)).withStrained(true, false);

        assertEquals(new ResourceBundle(1, 1, 3, 1, 2), Production.productionOf(industrial, 1, ruleset));
    }

    @Test
    void penaltyNeverMakesProductionNegative() {
        Ruleset harsh = new Ruleset(ruleset.version(), ruleset.roundCount(), ruleset.playerCount(), ruleset.starting(),
                ruleset.levels(), ruleset.storage(), new StrainedRules(9, 9), ruleset.buildings(), ruleset.market(),
                ruleset.contracts(), ruleset.events(), ruleset.objectives(), ruleset.projects(), ruleset.opportunities());
        GameState state = readyForRoundOne(SEED, harsh);
        PlayerState energy = state.player(seatOf(state, CityType.ENERGY)).withStrained(false, true);

        assertEquals(new ResourceBundle(1, 0, 1, 1, 0), Production.productionOf(energy, 1, harsh));
    }

    @Test
    void strainedPenaltyStepMovesTheFlagToThisRoundAndClearsIt() {
        GameState state = readyForRoundOne(SEED, ruleset);
        state = state.withPlayer(state.player(0).withStrained(true, false));
        state = state.withPlayer(state.player(1).withStrained(false, true));

        GameState after = Production.applyStrainedPenalty(state);

        assertEquals(false, after.player(0).strained());
        assertEquals(true, after.player(0).strainedPenaltyActive());
        // Last round's penalty ends: it applies for one round only.
        assertEquals(false, after.player(1).strained());
        assertEquals(false, after.player(1).strainedPenaltyActive());
    }
}
