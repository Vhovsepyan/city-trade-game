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
        connections.register(room.roomCode(), 0, session);
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
        connections.register(room.roomCode(), 0, seat0);
        connections.register(room.roomCode(), 1, seat1);
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
