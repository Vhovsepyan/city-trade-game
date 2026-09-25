package citytrade.server.ws;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import citytrade.engine.ruleset.Ruleset;
import citytrade.server.room.Room;
import citytrade.server.room.RoomRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.CloseStatus;
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
 * T23 accept criteria: commandId idempotency, reconnect/session identity, and D23 disconnected-seat handling,
 * against the real {@code /ws} endpoint (same harness style as {@code GameWebSocketProtocolTest}, T22). Each
 * test gets a fresh room (2 humans, 2 baseline bots) driven to Round 1's WINDOW phase through real commands.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketReconnectAndIdempotencyTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private Ruleset ruleset;

    @Autowired
    private RoomRegistry rooms;

    private HttpClient http;
    private String roomCode;
    private String seat0Token;
    private String seat1Token;
    private StandardWebSocketClient client;
    private WebSocketSession seat0Session;
    private WebSocketSession seat1Session;
    private RecordingClientHandler seat0Handler;
    private RecordingClientHandler seat1Handler;
    private final List<WebSocketSession> openSessions = new ArrayList<>();

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

    @BeforeEach
    void createRoomAndReachWindow() throws Exception {
        http = HttpClient.newHttpClient();
        JsonNode host = postJson("/rooms", "{\"nickname\":\"host\"}", null);
        roomCode = host.get("roomCode").asString();
        seat0Token = host.get("token").asString();
        JsonNode second = postJson("/rooms/" + roomCode + "/join", "{\"nickname\":\"second\"}", null);
        seat1Token = second.get("token").asString();

        addBot(seat0Token);
        addBot(seat0Token);
        postJson("/rooms/" + roomCode + "/start", "{}", seat0Token);

        client = new StandardWebSocketClient();
        seat0Handler = new RecordingClientHandler();
        seat1Handler = new RecordingClientHandler();
        seat0Session = connect(seat0Handler);
        seat1Session = connect(seat1Handler);

        sendHello(seat0Session, seat0Token);
        sendHello(seat1Session, seat1Token);
        JsonNode snapshot0 = awaitMessage(seat0Handler, 0, node -> "FULL_SNAPSHOT".equals(type(node)), TIMEOUT);
        JsonNode snapshot1 = awaitMessage(seat1Handler, 0, node -> "FULL_SNAPSHOT".equals(type(node)), TIMEOUT);

        int from0 = seat0Handler.messages.size();
        int from1 = seat1Handler.messages.size();
        chooseObjectives(seat0Session, snapshot0.get("view"));
        chooseObjectives(seat1Session, snapshot1.get("view"));

        awaitMessage(seat0Handler, from0, node -> isPhase(node, "WINDOW"), TIMEOUT);
        awaitMessage(seat1Handler, from1, node -> isPhase(node, "WINDOW"), TIMEOUT);
    }

    @Test
    void duplicateCommandIdIsAppliedOnceAndBothRepliesAreIdentical() throws Exception {
        int from0 = seat0Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("resource", "FOOD");
        payload.put("quantity", 1);
        sendEnvelope(seat0Session, "dup-1", "BUY_FROM_MARKET", payload);

        JsonNode firstReply = awaitMessage(seat0Handler, from0,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "dup-1".equals(commandId(node)), TIMEOUT);
        long firstStateVersion = firstReply.get("stateVersion").asLong();

        int from0AfterFirst = seat0Handler.messages.size();
        sendEnvelope(seat0Session, "dup-1", "BUY_FROM_MARKET", payload);
        JsonNode secondReply = awaitMessage(seat0Handler, from0AfterFirst,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "dup-1".equals(commandId(node)), TIMEOUT);

        assertThat(secondReply.get("stateVersion").asLong()).isEqualTo(firstStateVersion);
        assertThat(secondReply.get("roomVersion").asLong()).isEqualTo(firstReply.get("roomVersion").asLong());

        // A genuinely new command proves the room only advanced ONCE in total: the duplicate never reached
        // the engine a second time (never bought FOOD twice).
        int from0AfterSecond = seat0Handler.messages.size();
        sendEnvelope(seat0Session, "dup-2", "BUY_FROM_MARKET", payload);
        JsonNode thirdReply = awaitMessage(seat0Handler, from0AfterSecond,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "dup-2".equals(commandId(node)), TIMEOUT);
        assertThat(thirdReply.get("stateVersion").asLong()).isEqualTo(firstStateVersion + 1);
    }

    @Test
    void duplicateCommandIdReplyStaysIdenticalEvenAfterRoomVersionChanges() throws Exception {
        int from0 = seat0Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("resource", "FOOD");
        payload.put("quantity", 1);
        sendEnvelope(seat0Session, "dup-room-version", "BUY_FROM_MARKET", payload);

        JsonNode firstReply = awaitMessage(seat0Handler, from0,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "dup-room-version".equals(commandId(node)),
                TIMEOUT);

        // Bump roomVersion via seat 1's READY, in between the two identical submissions from seat 0, so a
        // live re-read of roomVersion at retry time would disagree with the original reply (R2-P1-1).
        int from1 = seat1Handler.messages.size();
        ObjectNode ready = JSON.createObjectNode();
        ready.put("ready", true);
        sendEnvelope(seat1Session, "c-ready-seat1", "READY", ready);
        awaitMessage(seat1Handler, from1, node -> "ROOM_UPDATE".equals(type(node)), TIMEOUT);

        int from0AfterReady = seat0Handler.messages.size();
        sendEnvelope(seat0Session, "dup-room-version", "BUY_FROM_MARKET", payload);
        JsonNode secondReply = awaitMessage(seat0Handler, from0AfterReady,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "dup-room-version".equals(commandId(node)),
                TIMEOUT);

        assertThat(secondReply.get("stateVersion").asLong()).isEqualTo(firstReply.get("stateVersion").asLong());
        assertThat(secondReply.get("roomVersion").asLong()).isEqualTo(firstReply.get("roomVersion").asLong());
    }

    @Test
    void duplicateCommandIdFromADifferentSeatIsRejected() throws Exception {
        int from0 = seat0Handler.messages.size();
        ObjectNode payload0 = JSON.createObjectNode();
        payload0.put("resource", "FOOD");
        payload0.put("quantity", 1);
        sendEnvelope(seat0Session, "shared-1", "BUY_FROM_MARKET", payload0);
        awaitMessage(seat0Handler, from0,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "shared-1".equals(commandId(node)), TIMEOUT);

        int from1 = seat1Handler.messages.size();
        ObjectNode payload1 = JSON.createObjectNode();
        payload1.put("resource", "ENERGY");
        payload1.put("quantity", 1);
        sendEnvelope(seat1Session, "shared-1", "BUY_FROM_MARKET", payload1);

        JsonNode rejected = awaitMessage(seat1Handler, from1,
                node -> "COMMAND_REJECTED".equals(type(node)) && "shared-1".equals(commandId(node)), TIMEOUT);
        assertThat(rejected.get("rejectionCode").asString()).isEqualTo("COMMAND_ID_REUSED");
    }

    @Test
    void aNewConnectionForTheSameSeatReplacesAndClosesTheOldOne() throws Exception {
        RecordingClientHandler newHandler = new RecordingClientHandler();
        WebSocketSession newSession = connect(newHandler);

        sendHello(newSession, seat0Token);

        JsonNode snapshot = awaitMessage(newHandler, 0, node -> "FULL_SNAPSHOT".equals(type(node)), TIMEOUT);
        assertThat(snapshot.get("view").get("seat").asInt()).isZero();

        CloseStatus oldStatus = seat0Handler.closed.get(5, TimeUnit.SECONDS);
        assertThat(oldStatus).isNotNull();
    }

    @Test
    void disconnectThenReconnectWithTheSameTokenGetsTheSameSeatAndTheCurrentSnapshot() throws Exception {
        int from0 = seat0Handler.messages.size();
        seat1Session.close();

        awaitMessage(seat0Handler, from0,
                node -> "ROOM_UPDATE".equals(type(node)) && isDisconnected(node, 1), TIMEOUT);

        // Advance stateVersion once more while seat 1 is disconnected, so the reconnect snapshot must reflect
        // the CURRENT version, not whatever it was when seat 1 dropped.
        from0 = seat0Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("resource", "FOOD");
        payload.put("quantity", 1);
        sendEnvelope(seat0Session, "c-while-disconnected", "BUY_FROM_MARKET", payload);
        JsonNode accepted = awaitMessage(seat0Handler, from0,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "c-while-disconnected".equals(commandId(node)),
                TIMEOUT);
        long currentStateVersion = accepted.get("stateVersion").asLong();

        RecordingClientHandler reconnectHandler = new RecordingClientHandler();
        WebSocketSession reconnectSession = connect(reconnectHandler);
        sendHello(reconnectSession, seat1Token);

        JsonNode snapshot = awaitMessage(reconnectHandler, 0, node -> "FULL_SNAPSHOT".equals(type(node)),
                TIMEOUT);
        assertThat(snapshot.get("view").get("seat").asInt()).isEqualTo(1);
        assertThat(snapshot.get("stateVersion").asLong()).isEqualTo(currentStateVersion);
        assertThat(isDisconnected(snapshot, 1)).isFalse();
    }

    @Test
    void aDisconnectedSeatDoesNotBlockEarlyRoundEnd() throws Exception {
        int from0 = seat0Handler.messages.size();
        seat1Session.close();
        awaitMessage(seat0Handler, from0,
                node -> "ROOM_UPDATE".equals(type(node)) && isDisconnected(node, 1), TIMEOUT);

        from0 = seat0Handler.messages.size();
        ObjectNode ready = JSON.createObjectNode();
        ready.put("ready", true);
        sendEnvelope(seat0Session, "c-ready", "READY", ready);

        // Only 1 connected human (seat 0) plus 2 bots plus the disconnected seat 1: READY resolves the round
        // early without waiting for the 120s window timer.
        JsonNode resolved = awaitMessage(seat0Handler, from0,
                node -> "ROUND_RESOLVED".equals(type(node)), TIMEOUT);
        assertThat(resolved.get("eventKind").asString()).isEqualTo("RoundResolved");
    }

    @Test
    void closedRoomTokenIsRefused() throws Exception {
        JsonNode created = postJson("/rooms", "{\"nickname\":\"lonely-host\"}", null);
        String code = created.get("roomCode").asString();
        String token = created.get("token").asString();
        rooms.require(code).close();

        RecordingClientHandler handler = new RecordingClientHandler();
        WebSocketSession session = connect(handler);
        sendHello(session, token);

        CloseStatus status = handler.closed.get(5, TimeUnit.SECONDS);
        assertThat(status).isNotNull();
        assertThat(handler.messages).isEmpty();
    }

    @Test
    void logCaptureNeverContainsAToken() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        Level originalLevel = root.getLevel();
        root.setLevel(Level.DEBUG);
        try {
            RecordingClientHandler handler = new RecordingClientHandler();
            WebSocketSession session = connect(handler);
            sendHello(session, seat0Token);
            awaitMessage(handler, 0, node -> "FULL_SNAPSHOT".equals(type(node)), TIMEOUT);
            session.close();

            RecordingClientHandler reconnectHandler = new RecordingClientHandler();
            WebSocketSession reconnectSession = connect(reconnectHandler);
            sendHello(reconnectSession, seat0Token);
            awaitMessage(reconnectHandler, 0, node -> "FULL_SNAPSHOT".equals(type(node)), TIMEOUT);
        } finally {
            root.setLevel(originalLevel);
            root.detachAppender(appender);
            appender.stop();
        }

        for (ILoggingEvent event : appender.list) {
            String message = event.getFormattedMessage();
            assertThat(message).doesNotContain(seat0Token);
            assertThat(message).doesNotContain(seat1Token);
        }
    }

    private void addBot(String hostToken) throws Exception {
        postJson("/rooms/" + roomCode + "/bots", "{\"type\":\"BASELINE\"}", hostToken);
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

    private static final class RecordingClientHandler extends TextWebSocketHandler {

        final List<String> messages = new CopyOnWriteArrayList<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        // Guards awaitMessage's check-then-wait against this handler's add-then-notify, so a message that
        // arrives between a failed check and the wait call is never missed (Object monitor, not a data lock:
        // messages itself stays a CopyOnWriteArrayList so callers may still read it without holding this).
        final Object lock = new Object();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            synchronized (lock) {
                messages.add(message.getPayload());
                lock.notifyAll();
            }
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }
    }
}
