package citytrade.server.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.ruleset.json.RulesetLoader;
import citytrade.server.persistence.LoggedCommand;
import citytrade.server.persistence.LoggedOutcome;
import citytrade.server.persistence.MatchRecord;
import citytrade.server.persistence.PostgresMatchLog;
import citytrade.server.persistence.ReplayService;
import citytrade.server.room.BotType;
import citytrade.server.room.Room;
import citytrade.server.room.RoomCreation;
import citytrade.server.room.RoomRegistry;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T24 accept criteria that need a real database: a full bot game logged through the server and replayed FROM
 * POSTGRES, out-of-order row insertion, and rejected commands stored but skipped. Uses
 * {@code io.zonky.test:embedded-postgres} (a real PostgreSQL binary, no Docker) migrated with the same Flyway
 * script the production {@code postgres} profile uses.
 */
class PostgresMatchLogTest {

    private static final long SEED = 4242L;
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private static final Duration WINDOW = Duration.ofSeconds(120);
    private static final Duration OBJECTIVE_TIMEOUT = Duration.ofSeconds(60);

    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;

    private PostgresMatchLog matchLog;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.builder().start();
        dataSource = postgres.getPostgresDatabase();
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void freshTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("TRUNCATE match_results, match_commands, match_players, matches");
        matchLog = new PostgresMatchLog(jdbc);
    }

    @Test
    void fullBotGameThroughTheServerReplaysFromTheDatabaseToTheSameFinalState() throws Exception {
        Ruleset ruleset = ruleset();
        Room room = activeRoomWithThreeBots(ruleset);
        ManualRoundScheduler scheduler = new ManualRoundScheduler();
        ActiveGameCoordinator coordinator =
                new ActiveGameCoordinator(ruleset, FIXED_CLOCK, scheduler, WINDOW, OBJECTIVE_TIMEOUT, matchLog);

        RoundFlowDriver driver = coordinator.start(room);
        GameRoom gameRoom = driver.gameRoom();
        GameState initial = room.gameState().orElseThrow();
        gameRoom.submitPlayerCommand(0, chooseFirstObjectives(initial, ruleset, 0));
        settle(gameRoom);

        for (int round = 1; round <= ruleset.roundCount(); round++) {
            scheduler.fireLatest();
            settle(gameRoom);
        }
        assertEquals(GamePhase.FINISHED, gameRoom.state().phase());
        GameState liveFinal = gameRoom.state();

        MatchRecord match = matchLog.loadMatch(room.roomId());
        List<LoggedCommand> commands = matchLog.loadCommands(room.roomId());
        assertThat(commands).isNotEmpty();
        assertThat(match.players()).hasSize(4);

        GameState replayed = ReplayService.replay(match, commands, ruleset);
        assertEquals(liveFinal, replayed);
    }

    @Test
    void replayOrdersByCommandSequenceEvenWhenRowsAreStoredOutOfOrder() {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        UUID matchId = UUID.randomUUID();
        matchLog.matchStarted(matchId, SEED, ruleset.version(), List.of(), Instant.EPOCH);

        List<citytrade.engine.command.GameCommand> inSequenceOrder = List.of(
                chooseFirstObjectives(initial, ruleset, 0), chooseFirstObjectives(initial, ruleset, 1),
                chooseFirstObjectives(initial, ruleset, 2), chooseFirstObjectives(initial, ruleset, 3),
                new StartRound());
        GameState expected = applyDirectly(initial, inSequenceOrder, ruleset);

        // Store command_sequence 5, 3, 1, 4, 2, deliberately not in ascending order, to prove loadCommands
        // (and therefore ReplayService) orders by the sequence column, never by storage/insertion order.
        int[] storageOrder = {4, 2, 0, 3, 1};
        for (int index : storageOrder) {
            matchLog.commandProcessed(matchId, accepted(index + 1, inSequenceOrder.get(index)));
        }

        List<LoggedCommand> loaded = matchLog.loadCommands(matchId);
        assertThat(loaded).extracting(LoggedCommand::commandSequence).containsExactly(1L, 2L, 3L, 4L, 5L);

        GameState replayed = ReplayService.replay(matchLog.loadMatch(matchId), loaded, ruleset);
        assertEquals(expected, replayed);
    }

    @Test
    void rejectedCommandsAreStoredButSkippedInReplay() {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        UUID matchId = UUID.randomUUID();
        matchLog.matchStarted(matchId, SEED, ruleset.version(), List.of(), Instant.EPOCH);

        citytrade.engine.command.GameCommand chosen0 = chooseFirstObjectives(initial, ruleset, 0);
        GameState expected = applyDirectly(initial, List.of(chosen0), ruleset);

        matchLog.commandProcessed(matchId, accepted(1, chosen0));
        matchLog.commandProcessed(matchId, new ProcessedCommand(2, CommandOrigin.PLAYER, OptionalInt.of(1),
                Optional.of("bad"), new ChooseObjectives(1, List.of()), new CommandOutcome.Applied(
                        new GameResult.Rejected(RejectionCode.INVALID_OBJECTIVE_CHOICE, "wrong count")), 1));

        List<LoggedCommand> loaded = matchLog.loadCommands(matchId);
        assertThat(loaded).hasSize(2);
        LoggedCommand storedRejection = loaded.get(1);
        assertThat(storedRejection.outcome()).isEqualTo(LoggedOutcome.REJECTED);
        assertThat(storedRejection.rejectionCode()).contains(RejectionCode.INVALID_OBJECTIVE_CHOICE);

        GameState replayed = ReplayService.replay(matchLog.loadMatch(matchId), loaded, ruleset);
        assertEquals(expected, replayed, "the rejected command must not be applied on replay");
    }

    @Test
    void serverWorksWithoutThePostgresProfileAndWithIt() {
        // The default in-memory MatchLog is covered by InMemoryMatchLogTest; this documents that both
        // implementations satisfy the very same MatchLog contract exercised above with PostgresMatchLog.
        citytrade.server.persistence.MatchLog inMemory = new citytrade.server.persistence.InMemoryMatchLog();
        UUID matchId = UUID.randomUUID();
        inMemory.matchStarted(matchId, SEED, ruleset().version(), List.of(), Instant.EPOCH);
        assertThat(inMemory.loadMatch(matchId).seed()).isEqualTo(SEED);
    }

    private static GameState applyDirectly(GameState state, List<citytrade.engine.command.GameCommand> commands,
            Ruleset ruleset) {
        GameState current = state;
        for (citytrade.engine.command.GameCommand command : commands) {
            current = ((GameResult.Accepted) citytrade.engine.GameEngine.apply(current, command, ruleset)).state();
        }
        return current;
    }

    private static ProcessedCommand accepted(long sequence, citytrade.engine.command.GameCommand command) {
        return new ProcessedCommand(sequence, CommandOrigin.SYSTEM, OptionalInt.empty(), Optional.empty(), command,
                new CommandOutcome.Applied(new GameResult.Accepted(GameSetup.create(SEED, ruleset()), List.of())),
                sequence);
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

    /** See {@code RoundFlowDriverTest.settle} / {@code ActiveGameCoordinatorTest.settle}. */
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
