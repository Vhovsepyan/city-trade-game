package citytrade.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.PlayerState;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class BotGameTest {

    private final Ruleset ruleset = TestRulesets.prototype();

    private List<Bot> fourBaselineBots() {
        return Collections.nCopies(ruleset.playerCount(), new BaselineBot());
    }

    @Test
    void hundredGamesWithFourBaselineBotsFinishWithoutRejections() {
        for (long seed = 1; seed <= 100; seed++) {
            BotGame.Result result = BotGame.play(seed, ruleset, fourBaselineBots());

            assertEquals(List.of(), result.rejections(), "seed " + seed);
            assertEquals(GamePhase.FINISHED, result.finalState().phase(), "seed " + seed);
            assertEquals(ruleset.roundCount(), result.finalState().round(), "seed " + seed);
            assertTrue(result.finalState().finalResult().isPresent(), "seed " + seed);
            // The bots really play: cities grow and build, it is not a game of passes.
            assertTrue(result.commands().stream().anyMatch(UpgradeCity.class::isInstance), "seed " + seed);
            assertTrue(result.commands().stream().anyMatch(BuildBuilding.class::isInstance), "seed " + seed);
            for (PlayerState player : result.finalState().players()) {
                assertTrue(player.level() > 1, "seed " + seed + ", seat " + player.seat() + " never upgraded");
            }
        }
    }

    @Test
    void sameSeedPlaysTheSameGame() {
        BotGame.Result first = BotGame.play(7, ruleset, fourBaselineBots());
        BotGame.Result second = BotGame.play(7, ruleset, fourBaselineBots());

        assertEquals(first.commands(), second.commands());
        assertEquals(first.finalState(), second.finalState());
    }

    @Test
    void differentSeedsPlayDifferentGames() {
        BotGame.Result first = BotGame.play(1, ruleset, fourBaselineBots());
        BotGame.Result second = BotGame.play(2, ruleset, fourBaselineBots());

        assertTrue(!first.finalState().equals(second.finalState()));
    }

    @Test
    void needsOneBotPerSeat() {
        List<Bot> threeBots = Collections.nCopies(ruleset.playerCount() - 1, new BaselineBot());

        assertThrows(IllegalArgumentException.class, () -> BotGame.play(1, ruleset, threeBots));
    }
}
