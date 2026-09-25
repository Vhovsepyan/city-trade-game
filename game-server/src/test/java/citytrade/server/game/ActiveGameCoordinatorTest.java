package citytrade.server.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.ruleset.json.RulesetLoader;
import citytrade.server.persistence.InMemoryMatchLog;
import citytrade.server.room.BotType;
import citytrade.server.room.Room;
import citytrade.server.room.RoomCreation;
import citytrade.server.room.RoomRegistry;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Covers T20b's {@link ActiveGameCoordinator}: creates one GameRoom+driver per ACTIVE room and cleans it up. */
class ActiveGameCoordinatorTest {

    private static final long SEED = 4242L;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private static final Duration WINDOW = Duration.ofSeconds(120);
    private static final Duration OBJECTIVE_TIMEOUT = Duration.ofSeconds(60);

    @Test
    void finishedGameShutsDownItsGameRoomAndIsUnregistered() throws Exception {
        Ruleset ruleset = ruleset();
        Room room = activeRoomWithThreeBots(ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        ActiveGameCoordinator coordinator = new ActiveGameCoordinator(ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, new InMemoryMatchLog());

        RoundFlowDriver driver = coordinator.start(room);
        GameRoom gameRoom = driver.gameRoom();
        GameState initial = room.gameState().orElseThrow();
        gameRoom.submitPlayerCommand(0, chooseFirstObjectives(initial, ruleset, 0));
        settle(gameRoom);
        assertEquals(GamePhase.WINDOW, gameRoom.state().phase());

        for (int round = 1; round <= ruleset.roundCount(); round++) {
            scheduler.fireLatest();
            settle(gameRoom);
        }

        assertEquals(GamePhase.FINISHED, gameRoom.state().phase());
        assertTrue(coordinator.driver(room.roomCode()).isEmpty(),
                "a finished room's driver must be unregistered instead of leaking forever (R1-P2-3)");
        assertThrows(RejectedExecutionException.class, () -> gameRoom.submitSystemCommand(new StartRound()),
                "a finished room's GameRoom executor must be shut down instead of leaking a worker thread");
    }

    private static Room activeRoomWithThreeBots(Ruleset ruleset) {
        RoomRegistry registry = new RoomRegistry(ruleset, FIXED_CLOCK, () -> "ABCDEF", () -> "test-token",
                () -> SEED, Duration.ofMinutes(10), Duration.ofMinutes(10));
        RoomCreation created = registry.create("host");
        registry.addBot(created.roomCode(), created.token(), BotType.BASELINE);
        registry.addBot(created.roomCode(), created.token(), BotType.BASELINE);
        registry.addBot(created.roomCode(), created.token(), BotType.BASELINE);
        registry.start(created.roomCode(), created.token());
        return registry.require(created.roomCode());
    }

    private static ChooseObjectives chooseFirstObjectives(GameState state, Ruleset ruleset, int seat) {
        List<String> ids = state.player(seat).dealtObjectives().stream()
                .limit(ruleset.objectives().keptPerPlayer())
                .map(ObjectiveCard::id)
                .toList();
        return new ChooseObjectives(seat, ids);
    }

    /**
     * See {@code RoundFlowDriverTest.settle} for why comparing two consecutive barriers is race-free. Unlike
     * that test, this room's own driver can shut its executor down mid-wait (R1-P2-3, once FINISHED is
     * reached); a probe rejected for that reason proves the room is quiescent for good, same as two adjacent
     * sequence numbers would.
     */
    private static void settle(GameRoom room) throws Exception {
        long previous;
        try {
            previous = room.submitPlayerCommand(0, new StartRound()).get(10, TimeUnit.SECONDS).sequence();
        } catch (RejectedExecutionException e) {
            return;
        }
        while (true) {
            long next;
            try {
                next = room.submitPlayerCommand(0, new StartRound()).get(10, TimeUnit.SECONDS).sequence();
            } catch (RejectedExecutionException e) {
                return;
            }
            if (next == previous + 1) {
                return;
            }
            previous = next;
        }
    }

    private static Ruleset ruleset() {
        return RulesetLoader.load(Path.of(System.getProperty("rulesets.dir", "rulesets"), "prototype-002.json"));
    }
}
