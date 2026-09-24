package citytrade.server.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.bots.BaselineBot;
import citytrade.bots.Bot;
import citytrade.engine.GameEngine;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.AcceptTrade;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.ruleset.json.RulesetLoader;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Covers the T20b rules: objective timeout, timer, READY, and the bot coordinator, with an injected clock. */
class RoundFlowDriverTest {

    private static final long SEED = 4242L;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private static final Duration WINDOW = Duration.ofSeconds(120);
    private static final Duration OBJECTIVE_TIMEOUT = Duration.ofSeconds(60);

    @Test
    void fullGameWithOneScriptedHumanAndThreeBotsRunsToFinishedUsingOnlyTimers() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        Map<Integer, Bot> bots = Map.of(1, new BaselineBot(), 2, new BaselineBot(), 3, new BaselineBot());
        boolean[] finished = new boolean[1];
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, bots, () -> finished[0] = true);

        driver.start();
        // The scripted human (seat 0) chooses its objectives right away and never acts in any window.
        room.submitPlayerCommand(0, chooseFirstObjectives(initial, ruleset, 0));
        settle(room);
        assertEquals(GamePhase.WINDOW, room.state().phase());
        assertEquals(1, room.state().round());

        for (int round = 1; round <= ruleset.roundCount(); round++) {
            assertEquals(GamePhase.WINDOW, room.state().phase());
            assertEquals(round, room.state().round());
            scheduler.fireLatest();
            settle(room);
        }

        assertEquals(GamePhase.FINISHED, room.state().phase());
        assertTrue(finished[0]);
        // Only SYSTEM-origin StartRound commands are real round starts; settle()'s barrier submits harmless
        // (refused) PLAYER-origin StartRound commands too, which must not be counted here.
        long startRounds = room.history().stream()
                .filter(p -> p.origin() == CommandOrigin.SYSTEM && p.command() instanceof StartRound)
                .count();
        assertEquals(ruleset.roundCount(), startRounds, "no StartRound is enqueued after the last round resolves");
        room.shutdown();
    }

    @Test
    void botThatPassesFirstAnswersALaterHumanTradeOffer() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        Map<Integer, Bot> bots = Map.of(1, new PassThenAnswerBot());
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, bots, () -> {
                });

        driver.start();
        for (int seat : new int[] {0, 2, 3}) {
            room.submitPlayerCommand(seat, chooseFirstObjectives(initial, ruleset, seat));
        }
        settle(room);
        assertEquals(GamePhase.WINDOW, room.state().phase());
        assertFalse(botAccepted(room, 1), "the bot has nothing to answer yet");

        Resource gift = state(room).player(0).city().specialty();
        room.submitPlayerCommand(0,
                new ProposeTrade(0, 1, ResourceBundle.EMPTY.with(gift, 1), ResourceBundle.EMPTY));
        settle(room);

        assertTrue(botAccepted(room, 1), "the bot is asked again once the offer to it exists, and answers it");
        room.shutdown();
    }

    @Test
    void earlyReadyResolutionCannotOvertakeAPendingBotAction() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        Map<Integer, Bot> bots = Map.of(3, new OnceThenQuietBot());
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, bots, () -> {
                });

        driver.start();
        for (int seat : new int[] {0, 1, 2}) {
            room.submitPlayerCommand(seat, chooseFirstObjectives(initial, ruleset, seat));
        }
        settle(room);
        assertEquals(GamePhase.WINDOW, room.state().phase());

        driver.setReady(0, true);
        driver.setReady(1, true);
        driver.setReady(2, true);
        assertFalse(hasResolveRound(room), "seat 3's bot still has a pending action");

        settle(room);
        assertTrue(hasResolveRound(room), "once the bot goes quiescent, the already-READY humans resolve it");
        room.shutdown();
    }

    @Test
    void aRunawayBotIsStoppedByTheCapAndTheRoundStillResolves() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        Map<Integer, Bot> bots = Map.of(3, new RunawayBot());
        int cap = 6;
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, bots, () -> {
                }, cap);

        driver.start();
        for (int seat : new int[] {0, 1, 2}) {
            room.submitPlayerCommand(seat, chooseFirstObjectives(initial, ruleset, seat));
        }
        settle(room);
        assertEquals(GamePhase.WINDOW, room.state().phase());

        long botActions = room.history().stream()
                .filter(p -> p.origin() == CommandOrigin.BOT && p.actorSeat().orElse(-1) == 3)
                .filter(p -> p.command() instanceof SetUpkeepPriority)
                .count();
        assertEquals(cap, botActions, "the bot is capped instead of looping forever");

        driver.setReady(0, true);
        driver.setReady(1, true);
        driver.setReady(2, true);
        settle(room);
        assertTrue(hasResolveRound(room), "the capped bot counts as quiescent, so the round still resolves");
        room.shutdown();
    }

    @Test
    void allFourReadyResolvesBeforeTheTimerReadyCanBeCancelledAndResetsNextRound() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, Map.of(), () -> {
                });

        driver.start();
        for (int seat = 0; seat < 4; seat++) {
            room.submitPlayerCommand(seat, chooseFirstObjectives(initial, ruleset, seat));
        }
        settle(room);
        assertEquals(1, room.state().round());

        driver.setReady(0, true);
        driver.setReady(0, false);
        driver.setReady(1, true);
        driver.setReady(2, true);
        driver.setReady(3, true);
        assertFalse(hasResolveRound(room), "seat 0 cancelled READY, so 3 of 4 seats are ready");
        assertFalse(driver.isReady(0));

        driver.setReady(0, true);
        settle(room);
        assertEquals(2, room.state().round(), "all 4 READY resolves before the (never-fired) timer");
        assertFalse(driver.isReady(0), "READY resets every round");
        assertFalse(driver.isReady(1));
        room.shutdown();
    }

    @Test
    void timerAndTheLastReadyAtTheSameMomentResolveExactlyOnce() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, Map.of(), () -> {
                });

        driver.start();
        for (int seat = 0; seat < 4; seat++) {
            room.submitPlayerCommand(seat, chooseFirstObjectives(initial, ruleset, seat));
        }
        settle(room);
        driver.setReady(0, true);
        driver.setReady(1, true);
        driver.setReady(2, true);

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService race = Executors.newFixedThreadPool(2);
        try {
            Future<?> timer = race.submit(() -> {
                await(barrier);
                scheduler.fireLatest();
            });
            Future<?> lastReady = race.submit(() -> {
                await(barrier);
                driver.setReady(3, true);
            });
            timer.get(10, TimeUnit.SECONDS);
            lastReady.get(10, TimeUnit.SECONDS);
        } finally {
            race.shutdown();
        }
        settle(room);

        long resolves = room.history().stream().filter(p -> p.command() instanceof ResolveRound).count();
        assertEquals(1, resolves, "the shared guard lets exactly one ResolveRound through");
        room.shutdown();
    }

    @Test
    void objectiveTimeoutKeepsTheFirstDealtCardsThenStartsRoundOne() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        Map<Integer, Bot> bots = Map.of(1, new BaselineBot(), 2, new BaselineBot(), 3, new BaselineBot());
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, bots, () -> {
                });

        driver.start();
        settle(room);
        assertEquals(GamePhase.SETUP, room.state().phase());
        assertFalse(room.state().player(0).hasChosenObjectives());

        scheduler.fireLatest();
        settle(room);

        assertEquals(GamePhase.WINDOW, room.state().phase());
        assertEquals(1, room.state().round());
        List<String> expectedIds = initial.player(0).dealtObjectives().stream()
                .limit(ruleset.objectives().keptPerPlayer())
                .map(ObjectiveCard::id)
                .toList();
        List<String> actualIds = room.state().player(0).keptObjectives().stream().map(ObjectiveCard::id).toList();
        assertEquals(expectedIds, actualIds);
        room.shutdown();
    }

    @Test
    void disconnectedSeatCountsAsReadyImmediately() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, Map.of(), () -> {
                });

        driver.start();
        for (int seat = 0; seat < 4; seat++) {
            room.submitPlayerCommand(seat, chooseFirstObjectives(initial, ruleset, seat));
        }
        settle(room);
        driver.setReady(0, true);
        driver.setReady(1, true);
        driver.setReady(2, true);
        assertFalse(hasResolveRound(room));

        driver.setDisconnected(3, true);
        settle(room);
        assertTrue(hasResolveRound(room), "a disconnected seat counts as READY (D23)");
        room.shutdown();
    }

    @Test
    void readyIsIgnoredOutsideTheWindowPhase() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, Map.of(), () -> {
                });

        driver.start();
        assertEquals(GamePhase.SETUP, room.state().phase());
        long versionBeforeReady = driver.roomVersion();

        driver.setReady(0, true);

        assertFalse(driver.isReady(0), "READY during SETUP must have no effect (R1-P2-1)");
        assertEquals(versionBeforeReady, driver.roomVersion(), "an ignored READY must not change roomVersion");

        for (int seat = 0; seat < 4; seat++) {
            room.submitPlayerCommand(seat, chooseFirstObjectives(initial, ruleset, seat));
        }
        settle(room);
        assertEquals(GamePhase.WINDOW, room.state().phase());
        assertFalse(hasResolveRound(room), "the ignored READY from SETUP must not carry over into round 1");
        room.shutdown();
    }

    @Test
    void rejectedBotCommandStillLetsAnAlreadyReadyRoundResolve() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        Map<Integer, Bot> bots = Map.of(3, new OnceThenRejectedBot());
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, bots, () -> {
                });

        driver.start();
        for (int seat : new int[] {0, 1, 2}) {
            room.submitPlayerCommand(seat, chooseFirstObjectives(initial, ruleset, seat));
        }
        settle(room);
        assertEquals(GamePhase.WINDOW, room.state().phase());

        driver.setReady(0, true);
        driver.setReady(1, true);
        driver.setReady(2, true);
        settle(room);

        assertTrue(hasResolveRound(room),
                "the bot's rejected command still clears its pending flag, so the round must resolve (R1-P2-2)");
        room.shutdown();
    }

    @Test
    void botsAreEvaluatedInAscendingSeatOrder() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom room = new GameRoom(initial, ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        // Map.of() deliberately randomizes its own iteration order, so this only passes if the driver
        // re-sorts by seat before every evaluation instead of trusting the caller's map (R1-P1-1).
        Map<Integer, Bot> bots = Map.of(3, new OnceThenQuietBot(), 1, new OnceThenQuietBot(), 2, new OnceThenQuietBot());
        RoundFlowDriver driver = new RoundFlowDriver(room, ruleset, FIXED_CLOCK, scheduler, WINDOW,
                OBJECTIVE_TIMEOUT, bots, () -> {
                });

        driver.start();
        room.submitPlayerCommand(0, chooseFirstObjectives(initial, ruleset, 0));
        settle(room);
        assertEquals(GamePhase.WINDOW, room.state().phase());

        List<Integer> botActionSeats = room.history().stream()
                .filter(p -> p.origin() == CommandOrigin.BOT)
                .map(p -> p.actorSeat().orElseThrow())
                .toList();
        assertEquals(List.of(1, 2, 3, 1, 2, 3), botActionSeats,
                "bots must be evaluated by ascending seat every time, for both objectives and window commands");
        room.shutdown();
    }

    @Test
    void commandsQueuedBeforeResolveRoundAreAcceptedCommandsAfterGetInvalidPhase() throws Exception {
        Ruleset ruleset = ruleset();
        GameState state = GameSetup.create(SEED, ruleset);
        for (int seat = 0; seat < 4; seat++) {
            GameCommand choose = chooseFirstObjectives(state, ruleset, seat);
            state = ((GameResult.Accepted) GameEngine.apply(state, choose, ruleset)).state();
        }
        state = ((GameResult.Accepted) GameEngine.apply(state, new StartRound(), ruleset)).state();
        GameRoom room = new GameRoom(state, ruleset);
        List<Resource> order = nonSpecialty(state, 0);

        Future<ProcessedCommand> before = room.submitPlayerCommand(0, new SetUpkeepPriority(0, order));
        Future<ProcessedCommand> resolve = room.submitSystemCommand(new ResolveRound());
        Future<ProcessedCommand> after = room.submitPlayerCommand(0, new SetUpkeepPriority(0, order));

        assertTrue(accepted(before.get()));
        assertTrue(accepted(resolve.get()));
        CommandOutcome.Applied afterOutcome = (CommandOutcome.Applied) after.get().outcome();
        assertTrue(afterOutcome.result() instanceof GameResult.Rejected rejected
                && rejected.code() == RejectionCode.INVALID_PHASE);
        room.shutdown();
    }

    private static boolean accepted(ProcessedCommand processed) {
        return processed.outcome() instanceof CommandOutcome.Applied applied
                && applied.result() instanceof GameResult.Accepted;
    }

    private static boolean hasResolveRound(GameRoom room) {
        return room.history().stream().anyMatch(p -> p.command() instanceof ResolveRound);
    }

    private static boolean botAccepted(GameRoom room, int seat) {
        return room.history().stream().anyMatch(p -> p.origin() == CommandOrigin.BOT
                && p.actorSeat().orElse(-1) == seat && p.command() instanceof AcceptTrade);
    }

    private static GameState state(GameRoom room) {
        return room.state();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Blocks until every command submitted before this call (including whatever they cascade into through the
     * driver) has been fully processed. A refused internal command from a PLAYER origin never reaches the
     * engine and never changes state, so it is a harmless barrier at the tail of the room's serial queue.
     * Comparing two consecutively submitted barriers' own sequence numbers (instead of racing an outside read
     * of {@code room.commandSequence()} against the room's single worker thread) is what actually closes the
     * race against the driver's own cascading submissions, without sleeping for real time: two barriers can
     * only come back adjacent once nothing else was interleaved between them.
     */
    private static void settle(GameRoom room) throws Exception {
        long previous = room.submitPlayerCommand(0, new StartRound()).get(10, TimeUnit.SECONDS).sequence();
        long next;
        do {
            next = room.submitPlayerCommand(0, new StartRound()).get(10, TimeUnit.SECONDS).sequence();
            if (next == previous + 1) {
                return;
            }
            previous = next;
        } while (true);
    }

    private static ChooseObjectives chooseFirstObjectives(GameState state, Ruleset ruleset, int seat) {
        List<String> ids = state.player(seat).dealtObjectives().stream()
                .limit(ruleset.objectives().keptPerPlayer())
                .map(ObjectiveCard::id)
                .toList();
        return new ChooseObjectives(seat, ids);
    }

    private static List<Resource> nonSpecialty(GameState state, int seat) {
        Resource specialty = state.player(seat).city().specialty();
        return Arrays.stream(Resource.values()).filter(r -> r != specialty).toList();
    }

    private static Ruleset ruleset() {
        return RulesetLoader.load(Path.of(System.getProperty("rulesets.dir", "rulesets"), "prototype-002.json"));
    }

    /** Returns empty on its first call for every seat, then answers any open offer addressed to it. */
    private static final class PassThenAnswerBot implements Bot {
        @Override
        public ChooseObjectives chooseObjectives(GameState state, int seat, Ruleset ruleset) {
            return chooseFirstObjectives(state, ruleset, seat);
        }

        @Override
        public Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset) {
            return state.tradeOffers().stream()
                    .filter(offer -> offer.isOpen() && offer.recipientSeat() == seat)
                    .findFirst()
                    .map(offer -> new AcceptTrade(seat, offer.id()));
        }
    }

    /** Returns exactly one valid, state-changing command, then nothing for the rest of the round. */
    private static final class OnceThenQuietBot implements Bot {
        private boolean used;

        @Override
        public ChooseObjectives chooseObjectives(GameState state, int seat, Ruleset ruleset) {
            return chooseFirstObjectives(state, ruleset, seat);
        }

        @Override
        public synchronized Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset) {
            if (used) {
                return Optional.empty();
            }
            used = true;
            return Optional.of(new SetUpkeepPriority(seat, nonSpecialty(state, seat)));
        }
    }

    /** Returns exactly one command that the engine rejects (unknown offer), then nothing for the rest of the round. */
    private static final class OnceThenRejectedBot implements Bot {
        private boolean used;

        @Override
        public ChooseObjectives chooseObjectives(GameState state, int seat, Ruleset ruleset) {
            return chooseFirstObjectives(state, ruleset, seat);
        }

        @Override
        public synchronized Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset) {
            if (used) {
                return Optional.empty();
            }
            used = true;
            return Optional.of(new AcceptTrade(seat, -1));
        }
    }

    /** Always has another valid, state-changing action: exercises the per-bot-per-round runaway cap. */
    private static final class RunawayBot implements Bot {
        private int calls;

        @Override
        public ChooseObjectives chooseObjectives(GameState state, int seat, Ruleset ruleset) {
            return chooseFirstObjectives(state, ruleset, seat);
        }

        @Override
        public synchronized Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset) {
            List<Resource> order = new ArrayList<>(nonSpecialty(state, seat));
            if (calls % 2 == 1) {
                Collections.reverse(order);
            }
            calls++;
            return Optional.of(new SetUpkeepPriority(seat, order));
        }
    }
}
