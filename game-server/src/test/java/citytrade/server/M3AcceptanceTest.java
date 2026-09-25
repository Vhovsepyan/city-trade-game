package citytrade.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.server.game.ActiveGameCoordinator;
import citytrade.server.game.GameRoom;
import citytrade.server.game.ManualRoundScheduler;
import citytrade.server.game.RoundFlowDriver;
import citytrade.server.persistence.LoggedCommand;
import citytrade.server.persistence.MatchLog;
import citytrade.server.persistence.MatchRecord;
import citytrade.server.persistence.ReplayService;
import citytrade.server.room.RoomRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * T24a - M3 acceptance: one full 14-round game through the REAL REST + WebSocket server (random port),
 * covering every step of the task's Do list in order: create room (host = human 1) -> 2 humans join ->
 * host adds 1 bot -> start (4 seats) -> objectives chosen (one by the D22 timeout) -> Round 1 starts ->
 * 3 WebSocket clients send commands concurrently while the bot acts through the bot coordinator -> private
 * views verified for each seat -> one client disconnects and reconnects with its token -> FULL_SNAPSHOT ->
 * a duplicate command returns the same outcome -> all READY ends a round early -> the window timer ends
 * another round -> the game runs to FINISHED -> GAME_FINISHED is received -> a command-log replay
 * reproduces the exact final state.
 *
 * <p>The real REST + WebSocket server runs unmocked, but {@link TestSchedulingConfiguration} replaces the
 * production {@code Clock} and {@code RoundScheduler} beans with a fixed clock and a {@link
 * ManualRoundScheduler} (the same test double {@code ActiveGameCoordinatorTest} and {@code
 * RoundFlowDriverTest} use), so the D22 objective-choice deadline and every round's window deadline fire
 * only when this test calls {@code roundScheduler.fireLatest()}, instead of after a real wall-clock delay.
 * That removes any dependency on how fast this test's own REST/WebSocket round trips happen to run, so it
 * cannot be flaky the way racing a real timer against real network calls would be.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class M3AcceptanceTest {

    private static final Duration LONG_TIMEOUT = Duration.ofSeconds(20);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    /** Replaces the production {@code Clock}/{@code RoundScheduler} beans so timers never depend on real time. */
    @TestConfiguration
    static class TestSchedulingConfiguration {

        // Bean names deliberately differ from the production "gameClock"/"roundScheduler" beans (Spring Boot
        // rejects two beans sharing one name unless bean-definition-overriding is turned on); @Primary alone
        // is enough to make every injection point (including ServerConfiguration's own @Bean methods) resolve
        // to these instead, while the now-unused production beans are simply never injected anywhere.
        @Bean
        @Primary
        Clock fixedTestClock() {
            return Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        ManualRoundScheduler manualTestRoundScheduler() {
            return new ManualRoundScheduler();
        }
    }

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private Ruleset ruleset;

    @Autowired
    private RoomRegistry rooms;

    @Autowired
    private ActiveGameCoordinator activeGames;

    @Autowired
    private MatchLog matchLog;

    @Autowired
    private ManualRoundScheduler roundScheduler;

    private HttpClient http;
    private StandardWebSocketClient client;
    private final List<WebSocketSession> openSessions = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeSessions() {
        for (WebSocketSession session : openSessions) {
            try {
                session.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    @Test
    void fullGameThroughRestAndWebSocketReplaysToTheSameFinalState() throws Exception {
        http = HttpClient.newHttpClient();
        client = new StandardWebSocketClient();

        // create room (host = human 1) -> 2 humans join -> host adds 1 bot -> start (3 humans + 1 bot = 4 seats)
        JsonNode host = postJson("/rooms", "{\"nickname\":\"host\"}", null);
        String roomCode = host.get("roomCode").asString();
        String[] tokens = new String[3];
        tokens[0] = host.get("token").asString();
        tokens[1] = postJson("/rooms/" + roomCode + "/join", "{\"nickname\":\"p2\"}", null).get("token").asString();
        tokens[2] = postJson("/rooms/" + roomCode + "/join", "{\"nickname\":\"p3\"}", null).get("token").asString();
        postJson("/rooms/" + roomCode + "/bots", "{\"type\":\"BASELINE\"}", tokens[0]);
        postJson("/rooms/" + roomCode + "/start", "{}", tokens[0]);

        RoundFlowDriver driver = activeGames.driver(roomCode).orElseThrow();
        GameRoom liveGameRoom = driver.gameRoom();
        UUID roomId = rooms.require(roomCode).roomId();

        RecordingClientHandler[] handlers = new RecordingClientHandler[3];
        WebSocketSession[] sessions = new WebSocketSession[3];
        for (int seat = 0; seat < 3; seat++) {
            handlers[seat] = new RecordingClientHandler();
            sessions[seat] = connect(handlers[seat]);
            sendHello(sessions[seat], tokens[seat]);
        }
        JsonNode[] snapshots = new JsonNode[3];
        for (int seat = 0; seat < 3; seat++) {
            snapshots[seat] = awaitMessage(handlers[seat], 0, node -> "FULL_SNAPSHOT".equals(type(node)), LONG_TIMEOUT);
            assertThat(snapshots[seat].get("view").get("seat").asInt()).isEqualTo(seat);
        }

        // objectives chosen (one by timeout): seats 0 and 1 choose right away; seat 2 never does, so only the
        // D22 objective-choice timeout can make Round 1 start. Wait for seat 0's and seat 1's choices to be
        // applied before firing that timeout, so it does not race their commands and overwrite them too.
        int[] beforeObjectives = messageCounts(handlers);
        chooseObjectives(sessions[0], snapshots[0].get("view"));
        chooseObjectives(sessions[1], snapshots[1].get("view"));
        awaitMessage(handlers[0], beforeObjectives[0],
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "c-objectives-0".equals(commandId(node)),
                LONG_TIMEOUT);
        awaitMessage(handlers[1], beforeObjectives[1],
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "c-objectives-1".equals(commandId(node)),
                LONG_TIMEOUT);
        roundScheduler.fireLatest();

        // Round 1 starts
        int[] from = messageCounts(handlers);
        for (int seat = 0; seat < 3; seat++) {
            awaitMessage(handlers[seat], from[seat], node -> isPhase(node, "WINDOW") && roundOf(node) == 1, LONG_TIMEOUT);
        }

        // 3 WebSocket clients send commands concurrently while the bot acts through the bot coordinator
        from = messageCounts(handlers);
        String[] resources = {"FOOD", "ENERGY", "MATERIALS"};
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            CountDownLatch startLatch = new CountDownLatch(1);
            List<CompletableFuture<Void>> sends = new ArrayList<>();
            for (int seat = 0; seat < 3; seat++) {
                int s = seat;
                sends.add(CompletableFuture.runAsync(() -> {
                    await(startLatch);
                    ObjectNode payload = JSON.createObjectNode();
                    payload.put("resource", resources[s]);
                    payload.put("quantity", 1);
                    sendEnvelopeUnchecked(sessions[s], "c-concurrent-" + s, "BUY_FROM_MARKET", payload);
                }, pool));
            }
            startLatch.countDown();
            CompletableFuture.allOf(sends.toArray(new CompletableFuture<?>[0])).get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdown();
        }
        for (int seat = 0; seat < 3; seat++) {
            String expectedCommandId = "c-concurrent-" + seat;
            awaitMessage(handlers[seat], from[seat],
                    node -> "COMMAND_ACCEPTED".equals(type(node)) && expectedCommandId.equals(commandId(node)),
                    LONG_TIMEOUT);
        }

        // private views verified for each seat
        for (int seat = 0; seat < 3; seat++) {
            JsonNode update = awaitMessage(handlers[seat], from[seat],
                    node -> "STATE_UPDATE".equals(type(node)) && isPhase(node, "WINDOW"), LONG_TIMEOUT);
            assertPrivate(update.get("view"), seat);
        }

        // one client disconnects and reconnects with its token -> FULL_SNAPSHOT
        int from0BeforeDisconnect = handlers[0].messages.size();
        sessions[1].close();
        awaitMessage(handlers[0], from0BeforeDisconnect,
                node -> "ROOM_UPDATE".equals(type(node)) && isDisconnected(node, 1), LONG_TIMEOUT);

        RecordingClientHandler reconnectedHandler = new RecordingClientHandler();
        WebSocketSession reconnectedSession = connect(reconnectedHandler);
        sendHello(reconnectedSession, tokens[1]);
        JsonNode reconnectSnapshot =
                awaitMessage(reconnectedHandler, 0, node -> "FULL_SNAPSHOT".equals(type(node)), LONG_TIMEOUT);
        assertThat(reconnectSnapshot.get("view").get("seat").asInt()).isEqualTo(1);
        handlers[1] = reconnectedHandler;
        sessions[1] = reconnectedSession;

        // a duplicate command returns the same outcome
        int from1 = handlers[1].messages.size();
        ObjectNode duplicatePayload = JSON.createObjectNode();
        duplicatePayload.put("resource", "TECHNOLOGY");
        duplicatePayload.put("quantity", 1);
        sendEnvelope(sessions[1], "c-dup", "BUY_FROM_MARKET", duplicatePayload);
        JsonNode firstReply = awaitMessage(handlers[1], from1,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "c-dup".equals(commandId(node)), LONG_TIMEOUT);
        int from1AfterFirst = handlers[1].messages.size();
        sendEnvelope(sessions[1], "c-dup", "BUY_FROM_MARKET", duplicatePayload);
        JsonNode secondReply = awaitMessage(handlers[1], from1AfterFirst,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "c-dup".equals(commandId(node)), LONG_TIMEOUT);
        assertThat(secondReply.get("stateVersion").asLong()).isEqualTo(firstReply.get("stateVersion").asLong());
        assertThat(secondReply.get("roomVersion").asLong()).isEqualTo(firstReply.get("roomVersion").asLong());

        // From here on, rounds cascade automatically (StartRound follows ResolveRound with no further input),
        // sometimes faster than this test thread can re-read handlers[0].messages.size() in between two
        // awaits. So every remaining await shares ONE fixed anchor (never re-queried mid-cascade) and
        // disambiguates by the round number carried in the message itself (view.round for a WINDOW phase,
        // event.round for a RoundResolved notice) - correct no matter how far the cascade has already run by
        // the time this thread wakes up from the previous await.
        int anchor = handlers[0].messages.size();

        // all READY ends round 1 early: nobody fires the round scheduler this round, so only all-READY can
        // resolve it.
        sendReady(sessions[0], "c-ready-1-0");
        sendReady(sessions[1], "c-ready-1-1");
        sendReady(sessions[2], "c-ready-1-2");
        awaitMessage(handlers[0], anchor, node -> isRoundResolved(node, 1), LONG_TIMEOUT);

        // the window timer ends round 2: nobody sends READY this round, so only firing the round scheduler's
        // window deadline can resolve it.
        awaitMessage(handlers[0], anchor, node -> isPhase(node, "WINDOW") && roundOf(node) == 2, LONG_TIMEOUT);
        roundScheduler.fireLatest();
        awaitMessage(handlers[0], anchor, node -> isRoundResolved(node, 2), LONG_TIMEOUT);

        // the game runs to FINISHED: READY every remaining round so the rest of the game does not wait on
        // the window timer 12 more times.
        for (int round = 3; round <= ruleset.roundCount(); round++) {
            int expectedRound = round;
            awaitMessage(handlers[0], anchor, node -> isPhase(node, "WINDOW") && roundOf(node) == expectedRound,
                    LONG_TIMEOUT);
            sendReady(sessions[0], "c-ready-" + round + "-0");
            sendReady(sessions[1], "c-ready-" + round + "-1");
            sendReady(sessions[2], "c-ready-" + round + "-2");
            awaitMessage(handlers[0], anchor, node -> isRoundResolved(node, expectedRound), LONG_TIMEOUT);
        }
        JsonNode finished = awaitMessage(handlers[0], anchor, node -> "GAME_FINISHED".equals(type(node)), LONG_TIMEOUT);
        assertThat(finished.get("eventKind").asString()).isEqualTo("GameFinished");

        // log replay reproduces the final state
        GameState liveFinal = liveGameRoom.state();
        MatchRecord match = matchLog.loadMatch(roomId);
        List<LoggedCommand> commands = matchLog.loadCommands(roomId);
        assertThat(match.players()).hasSize(4);
        GameState replayed = ReplayService.replay(match, commands, ruleset);
        assertEquals(liveFinal, replayed);
    }

    private void chooseObjectives(WebSocketSession session, JsonNode view) throws Exception {
        int keep = ruleset.objectives().keptPerPlayer();
        JsonNode dealt = view.get("own").get("dealtObjectives");
        ObjectNode payload = JSON.createObjectNode();
        ArrayNode ids = payload.putArray("keptObjectiveIds");
        for (int i = 0; i < keep; i++) {
            ids.add(dealt.get(i).get("id").asString());
        }
        sendEnvelope(session, "c-objectives-" + view.get("seat").asInt(), "CHOOSE_OBJECTIVES", payload);
    }

    private void sendReady(WebSocketSession session, String commandId) throws Exception {
        ObjectNode payload = JSON.createObjectNode();
        payload.put("ready", true);
        sendEnvelope(session, commandId, "READY", payload);
    }

    private static void assertPrivate(JsonNode view, int seat) {
        assertThat(view.get("seat").asInt()).isEqualTo(seat);
        assertThat(view.get("own")).isNotNull();
        for (JsonNode player : view.get("players")) {
            assertThat(player.has("holdings")).isFalse();
            assertThat(player.has("dealtObjectives")).isFalse();
            assertThat(player.has("keptObjectives")).isFalse();
        }
    }

    private static int[] messageCounts(RecordingClientHandler[] handlers) {
        int[] counts = new int[handlers.length];
        for (int i = 0; i < handlers.length; i++) {
            counts[i] = handlers[i].messages.size();
        }
        return counts;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void sendEnvelopeUnchecked(WebSocketSession session, String commandId, String commandType,
            ObjectNode payload) {
        try {
            sendEnvelope(session, commandId, commandType, payload);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode postJson(String path, String jsonBody, String bearerToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (bearerToken != null) {
            builder.header("Authorization", "Bearer " + bearerToken);
        }
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("POST %s -> %s", path, response.body()).isLessThan(300);
        String body = response.body();
        return (body == null || body.isBlank()) ? JSON.createObjectNode() : JSON.readTree(body);
    }

    private WebSocketSession connect(RecordingClientHandler handler) throws Exception {
        WebSocketSession session = client.execute(handler, "ws://localhost:" + port + "/ws").get(5, TimeUnit.SECONDS);
        openSessions.add(session);
        return session;
    }

    private void sendHello(WebSocketSession session, String token) throws Exception {
        ObjectNode hello = JSON.createObjectNode();
        hello.put("protocolVersion", 1);
        hello.put("token", token);
        session.sendMessage(new TextMessage(JSON.writeValueAsString(hello)));
    }

    private void sendEnvelope(WebSocketSession session, String commandId, String commandType, ObjectNode payload)
            throws Exception {
        ObjectNode envelope = JSON.createObjectNode();
        envelope.put("protocolVersion", 1);
        envelope.put("commandId", commandId);
        envelope.put("commandType", commandType);
        if (payload != null) {
            envelope.set("payload", payload);
        }
        session.sendMessage(new TextMessage(JSON.writeValueAsString(envelope)));
    }

    /**
     * Waits for a message matching {@code predicate} among those {@code handler} has recorded from {@code
     * fromIndex} on. Coordinates via {@link RecordingClientHandler#lock} (notified by {@code
     * handleTextMessage}) instead of polling on a fixed sleep interval, so it wakes as soon as a new message
     * arrives rather than up to one sleep-interval late, and never claims a timeout before the deadline.
     */
    private static JsonNode awaitMessage(RecordingClientHandler handler, int fromIndex, Predicate<JsonNode> predicate,
            Duration timeout) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        synchronized (handler.lock) {
            while (true) {
                List<String> messages = handler.messages;
                int size = messages.size();
                for (int i = fromIndex; i < size; i++) {
                    JsonNode node = JSON.readTree(messages.get(i));
                    if (predicate.test(node)) {
                        return node;
                    }
                }
                long remainingMillis = (deadlineNanos - System.nanoTime()) / 1_000_000;
                if (remainingMillis <= 0) {
                    throw new AssertionError("timed out waiting for a matching message; seen so far: " + messages);
                }
                handler.lock.wait(remainingMillis);
            }
        }
    }

    private static String type(JsonNode node) {
        JsonNode type = node.get("type");
        return type == null ? null : type.asString();
    }

    private static String commandId(JsonNode node) {
        JsonNode commandId = node.get("commandId");
        return commandId == null ? null : commandId.asString();
    }

    private static boolean isPhase(JsonNode node, String phase) {
        JsonNode view = node.get("view");
        return view != null && view.has("phase") && phase.equals(view.get("phase").asString());
    }

    private static int roundOf(JsonNode node) {
        return node.get("view").get("round").asInt();
    }

    private static boolean isRoundResolved(JsonNode node, int round) {
        return "ROUND_RESOLVED".equals(type(node)) && node.get("event").get("round").asInt() == round;
    }

    private static boolean isDisconnected(JsonNode node, int seat) {
        JsonNode view = node.get("view");
        if (view == null) {
            return false;
        }
        for (JsonNode player : view.get("players")) {
            if (player.get("seat").asInt() == seat) {
                return player.get("disconnected").asBoolean();
            }
        }
        return false;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Captures every inbound text payload in order, notifying {@link #lock} so waiters never poll. */
    private static final class RecordingClientHandler extends TextWebSocketHandler {

        final List<String> messages = new CopyOnWriteArrayList<>();
        final Object lock = new Object();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            synchronized (lock) {
                messages.add(message.getPayload());
                lock.notifyAll();
            }
        }
    }
}
