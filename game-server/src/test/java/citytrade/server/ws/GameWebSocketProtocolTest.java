package citytrade.server.ws;

import static org.assertj.core.api.Assertions.assertThat;

import citytrade.engine.ruleset.Ruleset;
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
 * T22 accept criteria: end-to-end coverage of the real {@code /ws} endpoint with a real WebSocket client
 * (Architecture 6.6, 6.9). Each test gets a fresh room (2 humans, 2 baseline bots) driven to Round 1's WINDOW
 * phase entirely through real commands (no waiting on the objective-choice or window timers), so the tests
 * stay fast and are not tied to wall-clock timing.
 *
 * <p>Every {@code awaitMessage} call is given the message-log size right before it triggered the action it
 * is waiting on, and only ever looks at messages from that point forward: a connection's log is append-only
 * and never trimmed (so nothing is silently eaten from under a later assertion), which means an early,
 * unqualified "first match anywhere in the log" would instead find a stale message from setup (e.g. the
 * FULL_SNAPSHOT from HELLO, or the CHOOSE_OBJECTIVES ack) rather than this test's own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GameWebSocketProtocolTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private Ruleset ruleset;

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
        // Each test opens 1-2 real WebSocket connections against the (shared, cached) Spring context; closing
        // them keeps later tests in this class from accumulating open sockets against the same JVM-wide
        // container.
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
        JsonNode snapshot0 = awaitMessage(seat0Handler.messages, 0, node -> "FULL_SNAPSHOT".equals(type(node)), TIMEOUT);
        JsonNode snapshot1 = awaitMessage(seat1Handler.messages, 0, node -> "FULL_SNAPSHOT".equals(type(node)), TIMEOUT);

        int from0 = seat0Handler.messages.size();
        int from1 = seat1Handler.messages.size();
        chooseObjectives(seat0Session, snapshot0.get("view"));
        chooseObjectives(seat1Session, snapshot1.get("view"));

        awaitMessage(seat0Handler.messages, from0, node -> isPhase(node, "WINDOW"), TIMEOUT);
        awaitMessage(seat1Handler.messages, from1, node -> isPhase(node, "WINDOW"), TIMEOUT);
    }

    @Test
    void helloIsRequiredFirst() throws Exception {
        RecordingClientHandler handler = new RecordingClientHandler();
        WebSocketSession session = connect(handler);

        sendEnvelope(session, "c-early", "SNAPSHOT_REQUEST", null);

        CloseStatus status = handler.closed.get(5, TimeUnit.SECONDS);
        assertThat(status).isNotNull();
        assertThat(handler.messages).isEmpty();
    }

    @Test
    void wrongProtocolVersionIsRefused() throws Exception {
        RecordingClientHandler handler = new RecordingClientHandler();
        WebSocketSession session = connect(handler);

        ObjectNode hello = JSON.createObjectNode();
        hello.put("protocolVersion", 99);
        hello.put("token", seat0Token);
        session.sendMessage(new TextMessage(JSON.writeValueAsString(hello)));

        JsonNode unsupported = awaitMessage(handler.messages, 0, node -> true, TIMEOUT);
        assertThat(type(unsupported)).isEqualTo("PROTOCOL_UNSUPPORTED");
        handler.closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    void badTokenIsRefused() throws Exception {
        RecordingClientHandler handler = new RecordingClientHandler();
        WebSocketSession session = connect(handler);

        sendHello(session, "not-a-real-token");

        CloseStatus status = handler.closed.get(5, TimeUnit.SECONDS);
        assertThat(status).isNotNull();
        assertThat(handler.messages).isEmpty();
    }

    @Test
    void acceptedStateChangingCommandNotifiesSenderAndBroadcastsStateUpdateToAll() throws Exception {
        int from0 = seat0Handler.messages.size();
        int from1 = seat1Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("resource", "FOOD");
        payload.put("quantity", 1);
        sendEnvelope(seat0Session, "c-buy", "BUY_FROM_MARKET", payload);

        JsonNode accepted = awaitMessage(seat0Handler.messages, from0,
                node -> "COMMAND_ACCEPTED".equals(type(node)) && "c-buy".equals(commandId(node)), TIMEOUT);
        long newStateVersion = accepted.get("stateVersion").asLong();

        JsonNode update0 = awaitMessage(seat0Handler.messages, from0,
                node -> "STATE_UPDATE".equals(type(node)) && node.get("stateVersion").asLong() == newStateVersion,
                TIMEOUT);
        JsonNode update1 = awaitMessage(seat1Handler.messages, from1,
                node -> "STATE_UPDATE".equals(type(node)) && node.get("stateVersion").asLong() == newStateVersion,
                TIMEOUT);
        assertThat(update0.get("view").get("seat").asInt()).isZero();
        assertThat(update1.get("view").get("seat").asInt()).isEqualTo(1);
    }

    @Test
    void rejectedCommandRepliesWithTheEngineRejectionCodeAndNoStateChange() throws Exception {
        int from0 = seat0Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("resource", "FOOD");
        payload.put("quantity", -1);
        sendEnvelope(seat0Session, "c-bad-quantity", "BUY_FROM_MARKET", payload);

        JsonNode rejected = awaitMessage(seat0Handler.messages, from0,
                node -> "COMMAND_REJECTED".equals(type(node)) && "c-bad-quantity".equals(commandId(node)), TIMEOUT);
        assertThat(rejected.get("rejectionCode").asString()).isEqualTo("INVALID_QUANTITY");
    }

    @Test
    void readyBroadcastsRoomUpdateToAllWithoutChangingStateVersion() throws Exception {
        int from0 = seat0Handler.messages.size();
        sendEnvelope(seat0Session, "c-snapshot", "SNAPSHOT_REQUEST", null);
        JsonNode before = awaitMessage(seat0Handler.messages, from0, node -> "FULL_SNAPSHOT".equals(type(node)), TIMEOUT);
        long stateVersionBefore = before.get("stateVersion").asLong();

        from0 = seat0Handler.messages.size();
        int from1 = seat1Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("ready", true);
        sendEnvelope(seat0Session, "c-ready", "READY", payload);

        JsonNode update0 = awaitMessage(seat0Handler.messages, from0, node -> "ROOM_UPDATE".equals(type(node)), TIMEOUT);
        JsonNode update1 = awaitMessage(seat1Handler.messages, from1, node -> "ROOM_UPDATE".equals(type(node)), TIMEOUT);
        assertThat(update0.get("stateVersion").asLong()).isEqualTo(stateVersionBefore);
        assertThat(update1.get("stateVersion").asLong()).isEqualTo(stateVersionBefore);
        assertThat(update0.get("view").get("players").get(0).get("ready").asBoolean()).isTrue();
        assertThat(update1.get("view").get("players").get(0).get("ready").asBoolean()).isTrue();

        assertThat(seat0Handler.messages.subList(from0, seat0Handler.messages.size()).stream()
                        .noneMatch(raw -> "COMMAND_ACCEPTED".equals(type(JSON.readTree(raw)))
                                && "c-ready".equals(commandId(JSON.readTree(raw)))))
                .as("READY never gets a COMMAND_ACCEPTED reply")
                .isTrue();
    }

    @Test
    void aPayloadWithASeatFieldIsRejected() throws Exception {
        int from0 = seat0Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("resource", "FOOD");
        payload.put("quantity", 1);
        payload.put("seat", 1);
        sendEnvelope(seat0Session, "c-cheat", "BUY_FROM_MARKET", payload);

        JsonNode rejected = awaitMessage(seat0Handler.messages, from0,
                node -> "COMMAND_REJECTED".equals(type(node)) && "c-cheat".equals(commandId(node)), TIMEOUT);
        assertThat(rejected.get("rejectionCode").asString()).isEqualTo("INVALID_PAYLOAD");
    }

    @Test
    void anUpgradeCityPayloadWithAnUnknownFieldIsRejected() throws Exception {
        int from0 = seat0Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("seat", 1);
        sendEnvelope(seat0Session, "c-upgrade-cheat", "UPGRADE_CITY", payload);

        JsonNode rejected = awaitMessage(seat0Handler.messages, from0,
                node -> "COMMAND_REJECTED".equals(type(node)) && "c-upgrade-cheat".equals(commandId(node)), TIMEOUT);
        assertThat(rejected.get("rejectionCode").asString()).isEqualTo("INVALID_PAYLOAD");
    }

    @Test
    void aSnapshotRequestPayloadWithAnUnknownFieldIsRejected() throws Exception {
        int from0 = seat0Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("seat", 1);
        sendEnvelope(seat0Session, "c-snapshot-cheat", "SNAPSHOT_REQUEST", payload);

        JsonNode rejected = awaitMessage(seat0Handler.messages, from0,
                node -> "COMMAND_REJECTED".equals(type(node)) && "c-snapshot-cheat".equals(commandId(node)), TIMEOUT);
        assertThat(rejected.get("rejectionCode").asString()).isEqualTo("INVALID_PAYLOAD");
    }

    @Test
    void anEnvelopeWithAnUnsupportedProtocolVersionIsRefusedAndCloses() throws Exception {
        int from0 = seat0Handler.messages.size();
        ObjectNode envelope = JSON.createObjectNode();
        envelope.put("protocolVersion", 99);
        envelope.put("commandId", "c-badversion");
        envelope.put("commandType", "SNAPSHOT_REQUEST");
        seat0Session.sendMessage(new TextMessage(JSON.writeValueAsString(envelope)));

        JsonNode unsupported = awaitMessage(seat0Handler.messages, from0,
                node -> "PROTOCOL_UNSUPPORTED".equals(type(node)), TIMEOUT);
        assertThat(type(unsupported)).isEqualTo("PROTOCOL_UNSUPPORTED");
        seat0Handler.closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    void anEnvelopeWithAMissingProtocolVersionIsRefusedAndCloses() throws Exception {
        int from0 = seat0Handler.messages.size();
        ObjectNode envelope = JSON.createObjectNode();
        envelope.put("commandId", "c-noversion");
        envelope.put("commandType", "SNAPSHOT_REQUEST");
        seat0Session.sendMessage(new TextMessage(JSON.writeValueAsString(envelope)));

        JsonNode unsupported = awaitMessage(seat0Handler.messages, from0,
                node -> "PROTOCOL_UNSUPPORTED".equals(type(node)), TIMEOUT);
        assertThat(type(unsupported)).isEqualTo("PROTOCOL_UNSUPPORTED");
        seat0Handler.closed.get(5, TimeUnit.SECONDS);
    }

    @Test
    void stateUpdateForOneSeatNeverContainsAnotherSeatsPrivateData() throws Exception {
        int from1 = seat1Handler.messages.size();
        ObjectNode payload = JSON.createObjectNode();
        payload.put("resource", "FOOD");
        payload.put("quantity", 1);
        sendEnvelope(seat0Session, "c-buy2", "BUY_FROM_MARKET", payload);

        JsonNode update1 = awaitMessage(seat1Handler.messages, from1, node -> "STATE_UPDATE".equals(type(node)), TIMEOUT);
        JsonNode view1 = update1.get("view");
        assertThat(view1.get("seat").asInt()).isEqualTo(1);
        assertThat(view1.get("own")).isNotNull();
        // Public per-seat entries (players[]) never carry holdings/Money/objectives - only "own" does, and
        // "own" here is seat 1's, never seat 0's (D6, reusing T21's structural-absence technique on the wire).
        for (JsonNode player : view1.get("players")) {
            assertThat(player.has("holdings")).isFalse();
            assertThat(player.has("dealtObjectives")).isFalse();
            assertThat(player.has("keptObjectives")).isFalse();
        }
        // Seat 1's own dealtObjectives legitimately appear once, under "own" - never seat 0's.
        assertThat(view1.get("own").get("dealtObjectives")).isNotEmpty();
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

    /** Posts a JSON string body and returns the parsed response body (or an empty object if it has none). */
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
     * Scans {@code messages} (append-only, growing as more arrive) from index {@code fromIndex} onward for
     * the first entry matching {@code predicate}, without consuming/discarding anything. {@code fromIndex}
     * must be captured right before the action that is expected to produce the message, so an earlier,
     * unrelated message already in the log (e.g. a stale reply from an earlier step) can never be mistaken
     * for it.
     */
    private static JsonNode awaitMessage(List<String> messages, int fromIndex, Predicate<JsonNode> predicate,
            Duration timeout) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            int size = messages.size();
            for (int i = fromIndex; i < size; i++) {
                JsonNode node = JSON.readTree(messages.get(i));
                if (predicate.test(node)) {
                    return node;
                }
            }
            Thread.sleep(50);
        }
        throw new AssertionError("timed out waiting for a matching message; seen so far: " + messages);
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Captures every inbound text payload in order, and completes {@link #closed} when the socket closes. */
    private static final class RecordingClientHandler extends TextWebSocketHandler {

        final List<String> messages = new CopyOnWriteArrayList<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            closed.complete(status);
        }
    }
}
