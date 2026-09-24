package citytrade.server.ws;

import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.server.game.ActiveGameCoordinator;
import citytrade.server.game.CommandOutcome;
import citytrade.server.game.GameRoom;
import citytrade.server.game.ProcessedCommand;
import citytrade.server.game.RoundFlowDriver;
import citytrade.server.room.Room;
import citytrade.server.room.RoomRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The {@code /ws} endpoint (Architecture 6.6, 6.9). The first message on every connection must be
 * {@link ClientHello}; it identifies the seat from its reconnect token alone (never a room code or seat sent
 * by the client). Every later message is a {@link ClientEnvelope}: {@code commandType} READY,
 * SNAPSHOT_REQUEST, or a player command ({@code docs/PROTOCOL.md}).
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    static final int SEND_TIME_LIMIT_MS = 10_000;
    static final int BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;

    private final RoomRegistry rooms;
    private final ActiveGameCoordinator activeGames;
    // FAIL_ON_UNKNOWN_PROPERTIES enabled explicitly: this is what rejects an unexpected field anywhere in a
    // client message, in particular a "seat" field in a payload (Architecture 6.6: seat comes only from the
    // session, never from the client).
    private final ObjectMapper json =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    private final RoomConnections connections = new RoomConnections();
    private final Map<String, RoomBroadcaster> broadcasters = new ConcurrentHashMap<>();
    private final Map<String, Connected> established = new ConcurrentHashMap<>();

    public GameWebSocketHandler(RoomRegistry rooms, ActiveGameCoordinator activeGames) {
        this.rooms = Objects.requireNonNull(rooms);
        this.activeGames = Objects.requireNonNull(activeGames);
    }

    @Override
    protected void handleTextMessage(WebSocketSession rawSession, TextMessage message) {
        Connected ctx = established.get(rawSession.getId());
        if (ctx == null) {
            handleHello(rawSession, message);
        } else {
            handleEnvelope(ctx, message);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Connected ctx = established.remove(session.getId());
        if (ctx != null) {
            connections.unregister(ctx.room().roomCode(), ctx.seat(), ctx.session());
        }
    }

    private void handleHello(WebSocketSession rawSession, TextMessage message) {
        ClientHello hello;
        try {
            hello = json.readValue(message.getPayload(), ClientHello.class);
        } catch (RuntimeException e) {
            closeQuietly(rawSession);
            return;
        }
        if (hello.protocolVersion() == null || hello.protocolVersion() != ProtocolVersion.CURRENT) {
            sendRaw(rawSession, ServerMessage.protocolUnsupported(
                    "unsupported protocolVersion: " + hello.protocolVersion()));
            closeQuietly(rawSession);
            return;
        }
        Optional<RoomRegistry.SeatToken> found = rooms.findByToken(hello.token());
        if (found.isEmpty()) {
            closeQuietly(rawSession);
            return;
        }
        Room room = found.get().room();
        Optional<RoundFlowDriver> driverOpt = activeGames.driver(room.roomCode());
        if (driverOpt.isEmpty()) {
            // No live GameRoom for this room (not ACTIVE, or already FINISHED): T22 only serves live play.
            closeQuietly(rawSession);
            return;
        }
        int seat = found.get().seat();
        RoundFlowDriver driver = driverOpt.get();
        GameRoom gameRoom = driver.gameRoom();
        WebSocketSession session =
                new ConcurrentWebSocketSessionDecorator(rawSession, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT_BYTES);
        established.put(rawSession.getId(), new Connected(room, seat, gameRoom, driver, session));
        connections.register(room.roomCode(), seat, session);
        RoomBroadcaster broadcaster = broadcasters.computeIfAbsent(room.roomCode(), code -> {
            RoomBroadcaster created = new RoomBroadcaster(gameRoom, driver, room, connections, json);
            gameRoom.addListener(created::onProcessed);
            return created;
        });
        sendRaw(session, broadcaster.snapshotFor(seat));
    }

    private void handleEnvelope(Connected ctx, TextMessage message) {
        ClientEnvelope envelope;
        try {
            envelope = json.readValue(message.getPayload(), ClientEnvelope.class);
        } catch (RuntimeException e) {
            sendRaw(ctx.session(), ServerMessage.rejected(ctx.gameRoom().stateVersion(), ctx.driver().roomVersion(),
                    null, ProtocolErrorCode.INVALID_PAYLOAD.name(), "malformed message"));
            return;
        }
        if (envelope.protocolVersion() == null || envelope.protocolVersion() != ProtocolVersion.CURRENT) {
            sendRaw(ctx.session(), ServerMessage.protocolUnsupported(
                    "unsupported protocolVersion: " + envelope.protocolVersion()));
            closeQuietly(ctx.session());
            return;
        }
        String commandType = envelope.commandType();
        if ("SNAPSHOT_REQUEST".equals(commandType)) {
            handleSnapshotRequest(ctx, envelope);
        } else if ("READY".equals(commandType)) {
            handleReady(ctx, envelope);
        } else {
            handlePlayerCommand(ctx, envelope);
        }
    }

    private void handleSnapshotRequest(Connected ctx, ClientEnvelope envelope) {
        try {
            JsonNode node = envelope.payload() == null ? json.createObjectNode() : envelope.payload();
            json.treeToValue(node, CommandPayloads.EmptyPayload.class);
        } catch (RuntimeException e) {
            sendRaw(ctx.session(), ServerMessage.rejected(ctx.gameRoom().stateVersion(), ctx.driver().roomVersion(),
                    envelope.commandId(), ProtocolErrorCode.INVALID_PAYLOAD.name(), "invalid SNAPSHOT_REQUEST payload"));
            return;
        }
        sendRaw(ctx.session(), broadcasters.get(ctx.room().roomCode()).snapshotFor(ctx.seat()));
    }

    private void handleReady(Connected ctx, ClientEnvelope envelope) {
        CommandPayloads.ReadyPayload payload;
        try {
            JsonNode node = envelope.payload() == null ? json.createObjectNode() : envelope.payload();
            payload = json.treeToValue(node, CommandPayloads.ReadyPayload.class);
        } catch (RuntimeException e) {
            sendRaw(ctx.session(), ServerMessage.rejected(ctx.gameRoom().stateVersion(), ctx.driver().roomVersion(),
                    envelope.commandId(), ProtocolErrorCode.INVALID_PAYLOAD.name(), "invalid READY payload"));
            return;
        }
        long before = ctx.driver().roomVersion();
        ctx.driver().setReady(ctx.seat(), payload.ready());
        if (ctx.driver().roomVersion() != before) {
            broadcasters.get(ctx.room().roomCode()).broadcastRoomUpdate();
        }
    }

    private void handlePlayerCommand(Connected ctx, ClientEnvelope envelope) {
        GameCommand command;
        try {
            command = ClientCommands.toCommand(envelope.commandType(), envelope.payload(), json);
        } catch (UnsupportedCommandTypeException e) {
            sendRaw(ctx.session(), ServerMessage.rejected(ctx.gameRoom().stateVersion(), ctx.driver().roomVersion(),
                    envelope.commandId(), ProtocolErrorCode.UNSUPPORTED_COMMAND_TYPE.name(), e.getMessage()));
            return;
        } catch (InvalidPayloadException e) {
            sendRaw(ctx.session(), ServerMessage.rejected(ctx.gameRoom().stateVersion(), ctx.driver().roomVersion(),
                    envelope.commandId(), ProtocolErrorCode.INVALID_PAYLOAD.name(), e.getMessage()));
            return;
        }
        ProcessedCommand processed;
        try {
            processed = ctx.gameRoom().submitPlayerCommand(ctx.seat(), command).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException e) {
            return;
        }
        long stateVersion = ctx.gameRoom().stateVersion();
        long roomVersion = ctx.driver().roomVersion();
        switch (processed.outcome()) {
            case CommandOutcome.Applied applied -> {
                switch (applied.result()) {
                    case GameResult.Accepted ignored -> sendRaw(ctx.session(),
                            ServerMessage.accepted(stateVersion, roomVersion, envelope.commandId()));
                    case GameResult.Rejected rejected -> sendRaw(ctx.session(), ServerMessage.rejected(
                            stateVersion, roomVersion, envelope.commandId(), rejected.code().name(),
                            rejected.detail()));
                }
            }
            case CommandOutcome.Refused refused -> sendRaw(ctx.session(), ServerMessage.rejected(
                    stateVersion, roomVersion, envelope.commandId(),
                    ProtocolErrorCode.UNSUPPORTED_COMMAND_TYPE.name(), refused.reason()));
        }
    }

    private void sendRaw(WebSocketSession session, ServerMessage message) {
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(message)));
        } catch (IOException | RuntimeException e) {
            // Best-effort: the client may already be gone; afterConnectionClosed cleans up.
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
            // Already closed.
        }
    }

    private record Connected(Room room, int seat, GameRoom gameRoom, RoundFlowDriver driver,
            WebSocketSession session) {
    }
}
