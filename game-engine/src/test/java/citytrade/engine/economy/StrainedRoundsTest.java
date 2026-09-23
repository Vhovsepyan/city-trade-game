package citytrade.engine.economy;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.seatOf;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.CityType;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.LevelRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Strained over several rounds through the engine. With prototype values a Level 2+ city always
 * produces 1 of each other resource before upkeep, so upkeep cannot fail; this ruleset removes that
 * production to make failed upkeep possible.
 */
class StrainedRoundsTest {

    private static final long SEED = 3;
    private static final ResourceBundle NONE = ResourceBundle.EMPTY;

    private final Ruleset ruleset = TestRulesets.withLevels(TestRulesets.standard(), List.of(
            new LevelRules(1, 3, 1, 2, NONE, 0, 0),
            new LevelRules(2, 4, 0, 3, new ResourceBundle(3, 3, 3, 3, 0), 1, 1),
            new LevelRules(3, 5, 0, 4, new ResourceBundle(4, 4, 4, 4, 4), 2, 2)));

    @Test
    void failedUpkeepGivesThePenaltyInTheNextRoundOnlyAndItDoesNotStack() {
        GameState state = readyForRoundOne(SEED, ruleset);
        int seat = seatOf(state, CityType.INDUSTRIAL);
        state = withCity(state, seat, 2, new ResourceBundle(0, 0, 0, 0, 0));

        // Round 1: full production (Materials 4, Money 3); upkeep cannot be paid -> Strained.
        GameResult.Accepted round1 = accept(state, new StartRound(), ruleset);
        PlayerState p = round1.state().player(seat);
        assertEquals(new ResourceBundle(0, 0, 4, 0, 3), p.holdings());
        assertTrue(p.strained());
        assertFalse(p.strainedPenaltyActive(), "production is not reduced in the round of the failure");
        assertEquals(List.of(new DomainEvent.UpkeepPaid(seat, NONE, 1), new DomainEvent.CityStrained(seat),
                new DomainEvent.RoundStarted(1)), round1.events());
        state = accept(round1.state(), new ResolveRound(), ruleset).state();

        // Round 2: penalty (Materials 4 - 2, Money 3 - 1); upkeep fails again.
        state = accept(state, new StartRound(), ruleset).state();
        p = state.player(seat);
        assertEquals(new ResourceBundle(0, 0, 6, 0, 5), p.holdings());
        assertTrue(p.strainedPenaltyActive());
        assertTrue(p.strained());
        state = accept(state, new ResolveRound(), ruleset).state();

        // Round 3: the second failure in a row still gives only -2 / -1, not -4 / -2. Upkeep is paid now.
        state = withCity(state, seat, 2, state.player(seat).holdings().plus(new ResourceBundle(1, 0, 0, 0, 0)));
        state = accept(state, new StartRound(), ruleset).state();
        p = state.player(seat);
        assertEquals(new ResourceBundle(0, 0, 8, 0, 7), p.holdings());
        assertTrue(p.strainedPenaltyActive());
        assertFalse(p.strained());
        state = accept(state, new ResolveRound(), ruleset).state();

        // Round 4: no failure in Round 3, so full production again.
        state = accept(state, new StartRound(), ruleset).state();
        p = state.player(seat);
        assertEquals(new ResourceBundle(0, 0, 12, 0, 10), p.holdings());
        assertFalse(p.strainedPenaltyActive());
        assertTrue(p.strained(), "no Food left, so Round 4 upkeep fails again");
    }

    @Test
    void strainedCityKeepsItsLevel() {
        GameState state = readyForRoundOne(SEED, ruleset);
        int seat = seatOf(state, CityType.ENERGY);
        state = withCity(state, seat, 3, NONE);

        state = accept(state, new StartRound(), ruleset).state();
        state = accept(state, new ResolveRound(), ruleset).state();
        state = accept(state, new StartRound(), ruleset).state();

        assertTrue(state.player(seat).strainedPenaltyActive());
        assertEquals(3, state.player(seat).level());
    }
}
