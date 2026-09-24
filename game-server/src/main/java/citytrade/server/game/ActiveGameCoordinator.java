package citytrade.server.game;

import citytrade.bots.Bot;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.server.room.Room;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and starts one {@link GameRoom} + {@link RoundFlowDriver} for each room that becomes ACTIVE
 * (T20b: "create a GameRoom per ACTIVE room and drive it", left open by T20). Keeps them addressable by
 * room code for later tasks (T22 WebSocket routing, T23 reconnect).
 */
public final class ActiveGameCoordinator {

    private final Ruleset ruleset;
    private final Clock clock;
    private final RoundScheduler scheduler;
    private final Duration windowDuration;
    private final Duration objectiveChoiceTimeout;
    private final Map<String, RoundFlowDriver> drivers = new ConcurrentHashMap<>();

    public ActiveGameCoordinator(Ruleset ruleset, Clock clock, RoundScheduler scheduler,
            Duration windowDuration, Duration objectiveChoiceTimeout) {
        this.ruleset = Objects.requireNonNull(ruleset);
        this.clock = Objects.requireNonNull(clock);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.windowDuration = Objects.requireNonNull(windowDuration);
        this.objectiveChoiceTimeout = Objects.requireNonNull(objectiveChoiceTimeout);
    }

    /** Starts driving {@code room}'s game; {@code room} must already be ACTIVE with a {@code GameState}. */
    public RoundFlowDriver start(Room room) {
        GameState initial = room.gameState()
                .orElseThrow(() -> new IllegalStateException("room " + room.roomCode() + " has no game state"));
        GameRoom gameRoom = new GameRoom(initial, ruleset);
        // TreeMap: bot seat order must be deterministic (AGENTS.md "Deterministic").
        Map<Integer, Bot> bots = new TreeMap<>();
        room.botSeats().forEach((seat, type) -> bots.put(seat, BotFactory.create(type)));
        Runnable onFinished = () -> {
            room.finish();
            // A FINISHED room's GameRoom is done for good; release its worker thread and this entry
            // instead of leaking them for the rest of the server's lifetime (R1-P2-3).
            gameRoom.shutdown();
            drivers.remove(room.roomCode());
        };
        RoundFlowDriver driver = new RoundFlowDriver(gameRoom, ruleset, clock, scheduler, windowDuration,
                objectiveChoiceTimeout, bots, onFinished);
        drivers.put(room.roomCode(), driver);
        driver.start();
        return driver;
    }

    public Optional<RoundFlowDriver> driver(String roomCode) {
        return Optional.ofNullable(drivers.get(roomCode));
    }
}
