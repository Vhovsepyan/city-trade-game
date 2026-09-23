package citytrade.engine.objective;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.FinalResult;
import citytrade.engine.state.FinalScore;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Step 4.8: final score = visible + hidden Prestige, tiebreakers of Concept 40, Money never counts. */
class FinalScoringTest {

    private static final long SEED = 21;
    /** Completed in the test ruleset unless the city was Strained (never here). */
    private static final ObjectiveCard DONE_A = new ObjectiveCard.SteadyCity("DONE_A");
    private static final ObjectiveCard DONE_B = new ObjectiveCard.SteadyCity("DONE_B");
    /** Never completed: nobody owns 99 buildings. */
    private static final ObjectiveCard FAILED = new ObjectiveCard.DiversifiedEconomy("FAILED", 99);

    private final Ruleset ruleset = TestRulesets.standard();

    /** The state in step 4.8 of the last round, with every player set to the same neutral values. */
    private GameState lastRoundResolution() {
        GameState s = readyForRoundOne(SEED, ruleset).withRoundAndPhase(ruleset.roundCount(), GamePhase.RESOLUTION);
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            s = with(s, seat, 5, 2, 0, List.of(DONE_A, FAILED), 6);
        }
        return s;
    }

    private static GameState with(GameState state, int seat, int visiblePrestige, int level, int contractsBroken,
            List<ObjectiveCard> kept, int money) {
        GameState s = withCity(state, seat, level, new ResourceBundle(1, 1, 1, 1, money));
        return s.withPlayer(s.player(seat).withPrestige(visiblePrestige).withContractsBroken(contractsBroken)
                .withKeptObjectives(kept));
    }

    private FinalResult score(GameState state) {
        return FinalScoring.scoreIfLastRound(state, ruleset, new ArrayList<>()).finalResult().orElseThrow();
    }

    @Test
    void finalScoreIsVisiblePlusTwoPrestigePerCompletedKeptObjective() {
        GameState s = lastRoundResolution();
        s = with(s, 0, 7, 2, 0, List.of(DONE_A, DONE_B), 6);
        s = with(s, 1, 7, 2, 0, List.of(FAILED, DONE_A), 6);
        s = with(s, 2, -2, 2, 0, List.of(FAILED, new ObjectiveCard.DiversifiedEconomy("ALSO_FAILED", 50)), 6);

        FinalResult result = score(s);

        assertEquals(new FinalScore(0, 7, List.of("DONE_A", "DONE_B"), List.of("DONE_A", "DONE_B"), 4, 11, 2, 0),
                result.scoreOf(0));
        assertEquals(new FinalScore(1, 7, List.of("FAILED", "DONE_A"), List.of("DONE_A"), 2, 9, 2, 0),
                result.scoreOf(1));
        assertEquals(-2, result.scoreOf(2).finalPrestige(), "D13: negative visible Prestige stays negative");
        assertEquals(List.of(0), result.winnerSeats());
    }

    @Test
    void morePrestigeWinsBeforeAnyTiebreaker() {
        GameState s = lastRoundResolution();
        s = with(s, 1, 6, 1, 3, List.of(DONE_A, FAILED), 0);
        s = with(s, 2, 5, 3, 0, List.of(DONE_A, FAILED), 99);

        assertEquals(List.of(1), score(s).winnerSeats());
    }

    @Test
    void firstTiebreakerIsTheHigherCityLevel() {
        GameState s = lastRoundResolution();
        // Seat 3: 7 visible + no objective = the others' 5 + 2, but a level-3 city (and more broken contracts).
        s = with(s, 3, 7, 3, 2, List.of(FAILED, new ObjectiveCard.DiversifiedEconomy("NONE", 99)), 0);

        assertEquals(List.of(3), score(s).winnerSeats());
    }

    @Test
    void secondTiebreakerIsMoreCompletedHiddenObjectives() {
        GameState s = lastRoundResolution();
        // Everyone else: 5 visible + 1 objective = 7. Seat 2: 3 visible + 2 objectives = 7, same level.
        s = with(s, 2, 3, 2, 1, List.of(DONE_A, DONE_B), 0);

        assertEquals(List.of(2), score(s).winnerSeats());
    }

    @Test
    void thirdTiebreakerIsFewerBrokenContracts() {
        GameState s = lastRoundResolution();
        s = with(s, 0, 5, 2, 1, List.of(DONE_A, FAILED), 6);
        s = with(s, 1, 5, 2, 2, List.of(DONE_A, FAILED), 6);
        s = with(s, 3, 5, 2, 1, List.of(DONE_A, FAILED), 6);

        assertEquals(List.of(2), score(s).winnerSeats());
    }

    @Test
    void playersEqualOnEveryTiebreakerShareTheVictoryWhateverTheirMoney() {
        GameState s = lastRoundResolution();
        s = with(s, 0, 5, 2, 0, List.of(DONE_A, FAILED), 0);
        s = with(s, 1, 5, 2, 1, List.of(DONE_A, FAILED), 500);
        s = with(s, 3, 5, 2, 0, List.of(FAILED, DONE_B), 200);

        assertEquals(List.of(0, 2, 3), score(s).winnerSeats(), "seat 1 broke a contract; Money is not compared");
    }

    @Test
    void nothingIsScoredBeforeTheLastRound() {
        GameState s = lastRoundResolution().withRoundAndPhase(ruleset.roundCount() - 1, GamePhase.RESOLUTION);
        List<DomainEvent> events = new ArrayList<>();

        assertEquals(s, FinalScoring.scoreIfLastRound(s, ruleset, events));
        assertEquals(List.of(), events);
    }

    @Test
    void theLastResolveRoundRevealsObjectivesThenScoresThenFinishes() {
        GameState s = readyForRoundOne(SEED, ruleset);
        s = accept(s, new StartRound(), ruleset).state();
        while (s.round() < ruleset.roundCount()) {
            GameState resolved = accept(s, new ResolveRound(), ruleset).state();
            assertTrue(resolved.finalResult().isEmpty(), "no result after round " + s.round());
            s = accept(resolved, new StartRound(), ruleset).state();
        }
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            s = with(s, seat, seat, 1, 0, List.of(DONE_A, FAILED), 6);
        }

        GameResult.Accepted last = accept(s, new ResolveRound(), ruleset);

        assertEquals(GamePhase.FINISHED, last.state().phase());
        FinalResult result = last.state().finalResult().orElseThrow();
        assertEquals(List.of(3), result.winnerSeats());
        assertEquals(List.of(2, 3, 4, 5), result.scores().stream().map(FinalScore::finalPrestige).toList());
        List<DomainEvent> events = last.events();
        int size = events.size();
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            assertEquals(new DomainEvent.ObjectivesRevealed(seat, List.of("DONE_A", "FAILED"), List.of("DONE_A"), 2),
                    events.get(size - 7 + seat));
        }
        assertEquals(new DomainEvent.FinalScoresRevealed(result), events.get(size - 3));
        assertInstanceOf(DomainEvent.RoundResolved.class, events.get(size - 2));
        assertInstanceOf(DomainEvent.GameFinished.class, events.get(size - 1));
        assertEquals(s.player(3).prestige(), last.state().player(3).prestige(), "hidden Prestige is not added "
                + "to the visible Prestige");
    }
}
