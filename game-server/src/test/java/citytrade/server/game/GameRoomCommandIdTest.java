package citytrade.server.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.GameEngine;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import citytrade.ruleset.json.RulesetLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * T23: {@code GameRoom} stores an outcome per processed {@code commandId} (Architecture 6.7). Complements the
 * end-to-end {@code GameWebSocketReconnectAndIdempotencyTest} with fast, GameRoom-only coverage of the
 * dedup mechanism itself.
 */
class GameRoomCommandIdTest {

    private static final long SEED = 555L;

    @Test
    void duplicateCommandIdFromTheSameSeatReturnsTheSameOutcomeWithoutCallingTheEngineAgain() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        AtomicInteger engineCalls = new AtomicInteger();
        GameRoom.Engine counting = (state, command, r) -> {
            engineCalls.incrementAndGet();
            return GameEngine.apply(state, command, r);
        };
        GameRoom room = new GameRoom(initial, ruleset, counting);
        GameCommand command = new ChooseObjectives(0, keptObjectiveIds(initial, ruleset, 0));

        ProcessedCommand first = room.submitPlayerCommand(0, "cmd-1", command).get();
        ProcessedCommand second = room.submitPlayerCommand(0, "cmd-1", command).get();

        assertEquals(1, engineCalls.get(), "the engine must be called only once for a duplicate commandId");
        assertSame(first, second, "a duplicate commandId from the same seat returns the exact same outcome");
        assertEquals(first.sequence(), second.sequence(), "no new sequence number for a duplicate");
        assertEquals(1L, room.stateVersion(), "stateVersion only advanced for the first, real application");
        room.shutdown();
    }

    @Test
    void sameCommandIdFromADifferentSeatIsRefusedAndDoesNotDisturbTheOriginalOutcome() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);

        ProcessedCommand original = room.submitPlayerCommand(0, "shared",
                new ChooseObjectives(0, keptObjectiveIds(initial, ruleset, 0))).get();
        assertTrue(original.outcome() instanceof CommandOutcome.Applied);

        ProcessedCommand impostor = room.submitPlayerCommand(1, "shared",
                new ChooseObjectives(1, keptObjectiveIds(initial, ruleset, 1))).get();
        assertTrue(impostor.outcome() instanceof CommandOutcome.Refused,
                "a commandId reused by a different seat is refused before reaching the engine");
        assertTrue(impostor.sequence() > original.sequence(), "the refusal still consumes its own sequence number");

        // The interloper must not have clobbered seat 0's stored outcome.
        ProcessedCommand replay = room.submitPlayerCommand(0, "shared",
                new ChooseObjectives(0, keptObjectiveIds(initial, ruleset, 0))).get();
        assertSame(original, replay);
        room.shutdown();
    }

    @Test
    void aRejectedOutcomeIsAlsoReturnedIdenticallyOnADuplicateCommandId() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);

        // Wrong number of kept ids -> the engine rejects it (INVALID_OBJECTIVE_CHOICE), still a valid outcome
        // to be deduplicated.
        ProcessedCommand first = room.submitPlayerCommand(0, "bad-1", new ChooseObjectives(0, List.of())).get();
        assertTrue(((CommandOutcome.Applied) first.outcome()).result() instanceof GameResult.Rejected);

        ProcessedCommand second = room.submitPlayerCommand(0, "bad-1", new ChooseObjectives(0, List.of())).get();
        assertSame(first, second);
        room.shutdown();
    }

    private static List<String> keptObjectiveIds(GameState state, Ruleset ruleset, int seat) {
        int kept = ruleset.objectives().keptPerPlayer();
        return state.player(seat).dealtObjectives().stream()
                .map(ObjectiveCard::id)
                .limit(kept)
                .toList();
    }

    private static Ruleset ruleset() {
        return RulesetLoader.load(Path.of(System.getProperty("rulesets.dir", "rulesets"), "prototype-002.json"));
    }
}
