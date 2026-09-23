package citytrade.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.List;

/** Shared helpers for engine tests that play commands through {@link GameEngine}. */
public final class TestGames {

    private TestGames() {
    }

    /** A new game where every player has kept their first two objectives. */
    public static GameState readyForRoundOne(long seed, Ruleset ruleset) {
        GameState state = GameSetup.create(seed, ruleset);
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            List<ObjectiveCard> dealt = state.player(seat).dealtObjectives();
            state = accept(state, new ChooseObjectives(seat, List.of(dealt.get(0).id(), dealt.get(1).id())), ruleset)
                    .state();
        }
        return state;
    }

    public static GameResult.Accepted accept(GameState state, GameCommand command, Ruleset ruleset) {
        return assertInstanceOf(GameResult.Accepted.class, GameEngine.apply(state, command, ruleset));
    }

    /** Rejected with {@code expected}, and the returned result carries no new state. */
    public static void assertRejected(GameState state, GameCommand command, Ruleset ruleset, RejectionCode expected) {
        GameResult result = GameEngine.apply(state, command, ruleset);
        assertEquals(expected, assertInstanceOf(GameResult.Rejected.class, result).code(), command.toString());
    }

    /** {@code state} with the player at {@code seat} changed to the given level and holdings. */
    public static GameState withCity(GameState state, int seat, int level, ResourceBundle holdings) {
        PlayerState p = state.player(seat);
        return state.withPlayer(new PlayerState(p.seat(), p.city(), holdings, level, p.dealtObjectives(),
                p.keptObjectives(), p.strained(), p.strainedPenaltyActive(), p.upkeepPriority(), p.marketThisRound()));
    }

    /** The seat that plays {@code city}. */
    public static int seatOf(GameState state, CityType city) {
        return state.players().stream().filter(p -> p.city() == city).findFirst().orElseThrow().seat();
    }
}
