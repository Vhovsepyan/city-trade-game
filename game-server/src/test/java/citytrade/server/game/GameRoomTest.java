package citytrade.server.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.GameEngine;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import citytrade.ruleset.json.RulesetLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Covers the T20 rules: one serial queue per room, commandSequence, stateVersion, origin, seat injection. */
class GameRoomTest {

    private static final long SEED = 777L;

    @Test
    void fourThreadsSubmittingConcurrentlyKeepStrictSequenceAndReplayMatchesFinalState() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        int threadCount = 4;
        int perThread = 250;
        List<List<String>> keptIdsBySeat = new ArrayList<>();
        for (int seat = 0; seat < threadCount; seat++) {
            keptIdsBySeat.add(keptObjectiveIds(initial, ruleset, seat));
        }

        ExecutorService callers = Executors.newFixedThreadPool(threadCount);
        ConcurrentLinkedQueue<Future<ProcessedCommand>> futures = new ConcurrentLinkedQueue<>();
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        try {
            for (int seat = 0; seat < threadCount; seat++) {
                int actingSeat = seat;
                callers.submit(() -> {
                    awaitBarrier(barrier);
                    for (int i = 0; i < perThread; i++) {
                        GameCommand command = new ChooseObjectives(actingSeat, keptIdsBySeat.get(actingSeat));
                        futures.add(room.submitPlayerCommand(actingSeat, command));
                    }
                });
            }
        } finally {
            callers.shutdown();
        }
        assertTrue(callers.awaitTermination(30, TimeUnit.SECONDS));

        List<Long> sequences = new ArrayList<>();
        for (Future<ProcessedCommand> future : futures) {
            sequences.add(future.get(30, TimeUnit.SECONDS).sequence());
        }
        int total = threadCount * perThread;
        assertEquals(total, sequences.size());
        assertEquals(total, Set.copyOf(sequences).size(), "no lost update: every sequence number is unique");
        List<Long> sorted = new ArrayList<>(sequences);
        sorted.sort(Comparator.naturalOrder());
        for (int i = 0; i < sorted.size(); i++) {
            assertEquals(i + 1L, sorted.get(i), "sequence numbers are 1..N without gaps");
        }

        List<ProcessedCommand> history = room.history().stream()
                .sorted(Comparator.comparingLong(ProcessedCommand::sequence))
                .toList();
        assertEquals(total, history.size());
        GameState replayed = initial;
        for (ProcessedCommand processed : history) {
            GameResult result = GameEngine.apply(replayed, processed.command(), ruleset);
            if (result instanceof GameResult.Accepted accepted) {
                replayed = accepted.state();
            }
        }
        assertEquals(replayed, room.state(), "final state matches a single-threaded replay in sequence order");
        room.shutdown();
    }

    @Test
    void rejectedCommandLeavesStateAndVersionUnchangedButGetsASequenceNumber() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);

        // wrong number of kept ids -> engine rejects with INVALID_OBJECTIVE_CHOICE
        ProcessedCommand processed = room.submitPlayerCommand(0, new ChooseObjectives(0, List.of())).get();

        assertTrue(processed.sequence() > 0);
        assertEquals(0L, room.stateVersion());
        assertEquals(0L, processed.resultingStateVersion());
        assertEquals(initial, room.state());
        assertTrue(processed.outcome() instanceof CommandOutcome.Applied);
        CommandOutcome.Applied applied = (CommandOutcome.Applied) processed.outcome();
        assertTrue(applied.result() instanceof GameResult.Rejected);
        assertEquals(RejectionCode.INVALID_OBJECTIVE_CHOICE, ((GameResult.Rejected) applied.result()).code());
        room.shutdown();
    }

    @Test
    void acceptedNoOpResultDoesNotIncreaseStateVersion() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        // No real engine command is a no-op today, so this uses a test double that returns the SAME state.
        GameRoom.Engine noOpEngine = (state, command, r) -> new GameResult.Accepted(state, List.of());
        GameRoom room = new GameRoom(initial, ruleset, noOpEngine);

        ProcessedCommand processed = room.submitPlayerCommand(0,
                new ChooseObjectives(0, keptObjectiveIds(initial, ruleset, 0))).get();

        assertTrue(processed.outcome() instanceof CommandOutcome.Applied);
        assertEquals(0L, room.stateVersion());
        assertEquals(0L, processed.resultingStateVersion());
        assertEquals(initial, room.state());
        room.shutdown();
    }

    @Test
    void originIsRecordedCorrectlyForPlayerBotAndSystem() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);

        ProcessedCommand player = room.submitPlayerCommand(0,
                new ChooseObjectives(0, keptObjectiveIds(initial, ruleset, 0))).get();
        assertEquals(CommandOrigin.PLAYER, player.origin());
        assertEquals(0, player.actorSeat().orElseThrow());

        ProcessedCommand bot = room.submitBotCommand(1,
                new ChooseObjectives(1, keptObjectiveIds(initial, ruleset, 1))).get();
        assertEquals(CommandOrigin.BOT, bot.origin());
        assertEquals(1, bot.actorSeat().orElseThrow());

        ProcessedCommand system = room.submitSystemCommand(new StartRound()).get();
        assertEquals(CommandOrigin.SYSTEM, system.origin());
        assertTrue(system.actorSeat().isEmpty());

        room.shutdown();
    }

    @Test
    void clientInternalCommandsAreRefusedBeforeReachingTheEngine() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom.Engine poison = (state, command, r) -> {
            throw new AssertionError("the engine must not be called for an internal command from a client");
        };
        GameRoom room = new GameRoom(initial, ruleset, poison);

        ProcessedCommand fromPlayer = room.submitPlayerCommand(0, new StartRound()).get();
        assertTrue(fromPlayer.outcome() instanceof CommandOutcome.Refused);
        assertTrue(fromPlayer.sequence() > 0);

        ProcessedCommand fromBot = room.submitBotCommand(0, new ResolveRound()).get();
        assertTrue(fromBot.outcome() instanceof CommandOutcome.Refused);

        assertEquals(0L, room.stateVersion());
        assertEquals(initial, room.state());
        room.shutdown();
    }

    @Test
    void seatInTheEngineCommandIsAlwaysTheCallersSeat() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        List<GameCommand> seenByEngine = new ArrayList<>();
        GameRoom.Engine recording = (state, command, r) -> {
            seenByEngine.add(command);
            return GameEngine.apply(state, command, r);
        };
        GameRoom room = new GameRoom(initial, ruleset, recording);

        // caller seat 2 submits a command whose embedded seat is 1 -> the engine sees seat 2, not 1
        ProcessedCommand mismatched = room.submitPlayerCommand(2,
                new ChooseObjectives(1, keptObjectiveIds(initial, ruleset, 2))).get();
        assertTrue(mismatched.outcome() instanceof CommandOutcome.Applied);
        assertEquals(new ChooseObjectives(2, keptObjectiveIds(initial, ruleset, 2)), mismatched.command());
        assertEquals(1, seenByEngine.size());
        assertEquals(new ChooseObjectives(2, keptObjectiveIds(initial, ruleset, 2)), seenByEngine.get(0));
        room.shutdown();

        // a matching seat reaches the real engine and is applied normally
        GameRoom realRoom = new GameRoom(initial, ruleset);
        ProcessedCommand matched = realRoom.submitPlayerCommand(1,
                new ChooseObjectives(1, keptObjectiveIds(initial, ruleset, 1))).get();
        assertTrue(matched.outcome() instanceof CommandOutcome.Applied);
        assertTrue(((CommandOutcome.Applied) matched.outcome()).result() instanceof GameResult.Accepted);
        assertEquals(1L, realRoom.stateVersion());
        realRoom.shutdown();
    }

    @Test
    void systemOriginMaySubmitInternalCommandsAndTheyReachTheEngine() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        for (int seat = 0; seat < 4; seat++) {
            initial = ((GameResult.Accepted) GameEngine.apply(initial,
                    new ChooseObjectives(seat, keptObjectiveIds(initial, ruleset, seat)), ruleset)).state();
        }
        GameRoom room = new GameRoom(initial, ruleset);

        ProcessedCommand processed = room.submitSystemCommand(new StartRound()).get();

        assertTrue(processed.outcome() instanceof CommandOutcome.Applied);
        assertTrue(((CommandOutcome.Applied) processed.outcome()).result() instanceof GameResult.Accepted);
        assertEquals(1L, room.stateVersion());
        room.shutdown();
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
