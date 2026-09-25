package citytrade.server.ws;

import static org.assertj.core.api.Assertions.assertThat;

import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import citytrade.ruleset.json.RulesetLoader;
import citytrade.server.game.GameRoom;
import citytrade.server.game.ProcessedCommand;
import citytrade.server.game.RoundFlowDriver;
import citytrade.server.game.RoundScheduler;
import citytrade.server.room.Room;
import citytrade.server.room.RoomCreation;
import citytrade.server.room.RoomRegistry;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T22 accept criterion "accepted no-op -> COMMAND_ACCEPTED, stateVersion unchanged": since no real engine
 * command is a genuine no-op today (same reasoning as {@code GameRoomTest}'s T20 coverage), this uses a
 * double {@link GameRoom.Engine} to force one, then checks that {@link RoomBroadcaster} - which is what
 * decides whether a STATE_UPDATE goes out - stays silent for it. The companion test below covers the normal
 * case: a real, state-changing command reaches every connected seat.
 */
class RoomBroadcasterTest {

    private static final long SEED = 777L;
    private static final Clock CLOCK = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Test
    void anAcceptedNoOpBroadcastsNothing() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom.Engine noOpEngine = (state, command, rules) -> new GameResult.Accepted(state, List.of());
        GameRoom gameRoom = new GameRoom(initial, ruleset, noOpEngine);
        RoundFlowDriver driver = newDriver(gameRoom, ruleset);
        Room room = freshRoom(ruleset);
        RoomConnections connections = new RoomConnections();
        FakeWebSocketSession session = new FakeWebSocketSession();
        connections.register(room.roomId(), 0, session);
        RoomBroadcaster broadcaster = new RoomBroadcaster(gameRoom, driver, room, connections, JSON);
        gameRoom.addListener(broadcaster::onProcessed);

        gameRoom.submitPlayerCommand(0, new SetUpkeepPriority(0, List.of())).get();

        assertThat(session.sentMessages()).isEmpty();
        assertThat(gameRoom.stateVersion()).isZero();
    }

    @Test
    void aStateChangingCommandBroadcastsStateUpdateToEveryConnectedSeat() throws Exception {
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom gameRoom = new GameRoom(initial, ruleset);
        RoundFlowDriver driver = newDriver(gameRoom, ruleset);
        Room room = freshRoom(ruleset);
        RoomConnections connections = new RoomConnections();
        FakeWebSocketSession seat0 = new FakeWebSocketSession();
        FakeWebSocketSession seat1 = new FakeWebSocketSession();
        connections.register(room.roomId(), 0, seat0);
        connections.register(room.roomId(), 1, seat1);
        RoomBroadcaster broadcaster = new RoomBroadcaster(gameRoom, driver, room, connections, JSON);
        gameRoom.addListener(broadcaster::onProcessed);

        gameRoom.submitPlayerCommand(0, new ChooseObjectives(0, keptObjectiveIds(initial, ruleset, 0))).get();

        assertThat(gameRoom.stateVersion()).isEqualTo(1);
        // seat 0 gets its STATE_UPDATE plus the private ObjectivesChosen NOTICE (D5/D6: own choice only);
        // seat 1 gets only the STATE_UPDATE, since that NOTICE is not theirs to see.
        assertThat(seat0.sentMessages()).hasSize(2);
        assertThat(seat1.sentMessages()).hasSize(1);
        assertThat(seat0.sentMessages())
                .anySatisfy(m -> assertThat(m).contains("\"type\":\"STATE_UPDATE\"")
                        .contains("\"stateVersion\":1").contains("\"seat\":0"))
                .anySatisfy(m -> assertThat(m).contains("\"type\":\"NOTICE\"").contains("\"eventKind\":\"ObjectivesChosen\""));
        assertThat(seat1.sentMessages().getFirst())
                .contains("\"type\":\"STATE_UPDATE\"")
                .contains("\"seat\":1");
    }

    @Test
    void deliverSnapshotAndRegisterBlocksAConcurrentBroadcastUntilItCompletes() throws Exception {
        // R6-P1-1: a reconnecting seat must never be visible to `connections` while its FULL_SNAPSHOT is still
        // being computed/sent - otherwise a state change processed concurrently could reach every already
        // -connected seat while being missed by the reconnecting one, whose snapshot was captured before that
        // change. This proves the two are now mutually exclusive: a broadcast started while a snapshot delivery
        // is in flight blocks until that delivery (send + register) fully completes.
        Ruleset ruleset = ruleset();
        GameState initial = GameSetup.create(SEED, ruleset);
        GameRoom gameRoom = new GameRoom(initial, ruleset);
        RoundFlowDriver driver = newDriver(gameRoom, ruleset);
        Room room = freshRoom(ruleset);
        RoomConnections connections = new RoomConnections();
        FakeWebSocketSession seat1 = new FakeWebSocketSession();
        connections.register(room.roomId(), 1, seat1);
        RoomBroadcaster broadcaster = new RoomBroadcaster(gameRoom, driver, room, connections, JSON);
        gameRoom.addListener(broadcaster::onProcessed);

        CountDownLatch snapshotSendStarted = new CountDownLatch(1);
        CountDownLatch releaseSnapshotSend = new CountDownLatch(1);
        FakeWebSocketSession seat0 = new FakeWebSocketSession();
        seat0.beforeSend(() -> {
            snapshotSendStarted.countDown();
            try {
                assertThat(releaseSnapshotSend.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        // Fires only once broadcast() actually reaches seat 1's send - which requires the broadcast lock seat
        // 0's snapshot delivery is holding. Waiting on this latch (instead of sleeping a fixed duration and then
        // sampling state) proves the block by construction: the wait returns the instant the lock is free, and
        // times out only if it truly never fires within the window, with no reliance on scheduling luck.
        CountDownLatch seat1BroadcastAttempted = new CountDownLatch(1);
        seat1.beforeSend(seat1BroadcastAttempted::countDown);

        Thread reconnectThread = new Thread(() -> broadcaster.deliverSnapshotAndRegister(0, seat0));
        reconnectThread.start();
        try {
            assertThat(snapshotSendStarted.await(5, TimeUnit.SECONDS)).isTrue();

            // Seat 0's snapshot send is now in flight, holding the broadcast lock. A concurrent state-changing
            // command must not reach seat 1 (or finish processing) until that snapshot delivery releases it.
            Future<ProcessedCommand> commandFuture = gameRoom.submitPlayerCommand(1,
                    new ChooseObjectives(1, keptObjectiveIds(initial, ruleset, 1)));

            assertThat(seat1BroadcastAttempted.await(300, TimeUnit.MILLISECONDS))
                    .as("the broadcast for seat 1 must be blocked behind seat 0's in-flight snapshot delivery")
                    .isFalse();
            assertThat(commandFuture.isDone()).isFalse();
            assertThat(seat1.sentMessages()).isEmpty();

            releaseSnapshotSend.countDown();
            assertThat(seat1BroadcastAttempted.await(5, TimeUnit.SECONDS)).isTrue();
            commandFuture.get(5, TimeUnit.SECONDS);
        } finally {
            reconnectThread.join(5_000);
        }

        // Seat 0's FULL_SNAPSHOT is always its first message - it may also get the STATE_UPDATE broadcast right
        // after, if registration happened to finish before the blocked broadcast() got the lock, but that is a
        // harmless duplicate of what the snapshot already had, never a message arriving before the snapshot.
        // Seat 1 (never blocked) only received the broadcast after seat 0's snapshot delivery released the
        // lock - never a stale message missing seat 1's own update.
        assertThat(seat0.sentMessages()).isNotEmpty();
        assertThat(seat0.sentMessages().getFirst()).contains("\"type\":\"FULL_SNAPSHOT\"");
        assertThat(connections.sessionsOf(room.roomId())).containsEntry(0, seat0);
        assertThat(seat1.sentMessages()).isNotEmpty();
    }

    @Test
    void connectionsOfARoomWithAReusedCodeStayIsolatedFromTheOldRoomsSessions() {
        Ruleset ruleset = ruleset();
        // A fixed code generator forces RoomRegistry's real reuse path (create() recycles a CLOSED room's
        // code): roomA and roomB below end up with the SAME roomCode but different roomIds, exactly the
        // situation R4-P1-1 found broadcasters/connections keyed by roomCode getting wrong (a new room
        // reusing an old room's session/broadcaster state).
        RoomRegistry registry = fixedCodeRegistry(ruleset, "ABCDEF");
        Room roomA = registry.require(registry.create("hostA").roomCode());
        roomA.close();
        Room roomB = registry.require(registry.create("hostB").roomCode());

        assertThat(roomB.roomCode()).isEqualTo(roomA.roomCode());
        assertThat(roomB.roomId()).isNotEqualTo(roomA.roomId());

        RoomConnections connections = new RoomConnections();
        FakeWebSocketSession sessionA = new FakeWebSocketSession();
        FakeWebSocketSession sessionB = new FakeWebSocketSession();
        connections.register(roomA.roomId(), 0, sessionA);
        connections.register(roomB.roomId(), 0, sessionB);

        assertThat(connections.sessionsOf(roomA.roomId())).containsOnly(Map.entry(0, sessionA));
        assertThat(connections.sessionsOf(roomB.roomId())).containsOnly(Map.entry(0, sessionB));
    }

    private static RoomRegistry fixedCodeRegistry(Ruleset ruleset, String code) {
        AtomicInteger tokenCounter = new AtomicInteger();
        return new RoomRegistry(ruleset, CLOCK, () -> code, () -> "token-" + tokenCounter.incrementAndGet(),
                () -> 1L, Duration.ofMinutes(30), Duration.ofHours(2));
    }

    private static RoundFlowDriver newDriver(GameRoom gameRoom, Ruleset ruleset) {
        return new RoundFlowDriver(gameRoom, ruleset, CLOCK, noOpScheduler(),
                Duration.ofSeconds(120), Duration.ofSeconds(60), Map.of(), () -> {
                });
    }

    private static RoundScheduler noOpScheduler() {
        return (at, task) -> () -> {
        };
    }

    private static Room freshRoom(Ruleset ruleset) {
        RoomRegistry registry = RoomRegistry.secure(ruleset, CLOCK, new SecureRandom(),
                Duration.ofMinutes(30), Duration.ofHours(2));
        RoomCreation creation = registry.create("host");
        return registry.require(creation.roomCode());
    }

    private static List<String> keptObjectiveIds(GameState state, Ruleset ruleset, int seat) {
        return state.player(seat).dealtObjectives().stream()
                .limit(ruleset.objectives().keptPerPlayer())
                .map(ObjectiveCard::id)
                .toList();
    }

    private static Ruleset ruleset() {
        return RulesetLoader.load(Path.of(System.getProperty("rulesets.dir", "rulesets"), "prototype-002.json"));
    }
}
