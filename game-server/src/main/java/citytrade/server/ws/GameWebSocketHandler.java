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
import java.util.UUID;
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
    // Keyed by the room's stable roomId, not its reusable roomCode: a closed room's code can later be reused
    // for a new room (R3-P1-3), and keying by code would otherwise hand that new room the OLD room's
    // RoomBroadcaster - with its stale GameRoom/driver and never wired to the new game's own listener,
    // meaning the new room's clients would get stale/private state and no live updates (R4-P1-1).
    private final Map<UUID, RoomBroadcaster> broadcasters = new ConcurrentHashMap<>();
    private final Map<String, Connected> established = new ConcurrentHashMap<>();
    // One lock per (room, seat): serializes a reconnecting HELLO's session swap against the replaced
    // session's own afterConnectionClosed, so the two can never interleave and mark a freshly reconnected
    // seat disconnected again (R3-P1-1). Keyed by roomCode, not room identity: RoomRegistry never has two
    // live rooms sharing a code at once, so reusing the same lock object across a later, unrelated room with
    // the same code is harmless.
    private final Map<RoomSeatKey, Object> seatLocks = new ConcurrentHashMap<>();
    // Keyed by (roomId, seat, commandId): caches the exact wire reply sent for a PLAYER command's first
    // processing, so a retry that reuses the same commandId replays the identical reply - including
    // roomVersion, which (unlike stateVersion) is live driver metadata and can otherwise change between the
    // original call and a later retry (R2-P1-1). Keyed by the room's stable roomId, not its reusable roomCode
    // (R3-P1-3), and installed with computeIfAbsent so two concurrent duplicate submissions can never each
    // compute and cache a different roomVersion for what must be one identical reply (R3-P1-2).
    private final Map<CommandReplyKey, ServerMessage> commandReplies = new ConcurrentHashMap<>();

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
        if (ctx == null) {
            return;
        }
        // Reached both for a genuine client/network disconnect AND for a server-initiated replacement (a new
        // HELLO for the same seat, which also removes this session's `established` entry). Take the same lock
        // handleHello uses for its swap, so whichever of the two actually runs first for this seat completes
        // in full - including the driver.setDisconnected call - before the other can act (R3-P1-1).
        synchronized (seatLock(ctx.room().roomCode(), ctx.seat())) {
            // unregister() only removes (and returns true) if this session is still the one currently
            // registered for the seat. If a reconnect already replaced it, this is false and the seat must
            // stay connected: D23 must not mark a seat disconnected right after it successfully reconnected.
            if (!connections.unregister(ctx.room().roomId(), ctx.seat(), ctx.session())) {
                return;
            }
            long before = ctx.driver().roomVersion();
            ctx.driver().setDisconnected(ctx.seat(), true);
            broadcastRoomUpdateIfChanged(ctx.room().roomId(), ctx.driver(), before);
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
        RoomBroadcaster broadcaster = broadcasters.computeIfAbsent(room.roomId(), id -> {
            RoomBroadcaster created = new RoomBroadcaster(gameRoom, driver, room, connections, json);
            gameRoom.addListener(created::onProcessed);
            return created;
        });
        // Reconnect (Architecture 6.10): a new connection for a seat that already has one replaces it. The
        // established-map swap, setDisconnected(false), the FULL_SNAPSHOT send and the connections.register
        // all run under this seat's lock, the same one the replaced session's own afterConnectionClosed takes,
        // so that callback can never interleave with any of this and mark the seat disconnected right after it
        // just reconnected (R3-P1-1; see afterConnectionClosed). Registration is deliberately kept inside
        // RoomBroadcaster.deliverSnapshotAndRegister, which sends the snapshot and registers atomically with
        // respect to every broadcast, so a concurrent state change can never reach every other seat while being
        // missed by this reconnecting one (R6-P1-1). Keeping registration under THIS seat's lock too closes the
        // second gap R6-P1-1 found: if this brand-new session itself closes before registration finishes, its
        // afterConnectionClosed blocks on the same lock and only proceeds - by which point the session is
        // already registered, so unregister() correctly reports it as current and the seat is marked
        // disconnected, instead of racing unregister() as a no-op and then registering a closed session anyway.
        WebSocketSession previous;
        long before;
        synchronized (seatLock(room.roomCode(), seat)) {
            previous = connections.sessionsOf(room.roomId()).get(seat);
            established.put(rawSession.getId(), new Connected(room, seat, gameRoom, driver, session));
            before = driver.roomVersion();
            driver.setDisconnected(seat, false);
            if (previous != null) {
                established.remove(previous.getId());
            }
            broadcaster.deliverSnapshotAndRegister(seat, session);
        }
        if (previous != null) {
            closeQuietly(previous, CloseStatus.NORMAL);
        }
        if (driver.roomVersion() != before) {
            broadcaster.broadcastRoomUpdate();
        }
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
        sendRaw(ctx.session(), broadcasters.get(ctx.room().roomId()).snapshotFor(ctx.seat()));
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
            broadcasters.get(ctx.room().roomId()).broadcastRoomUpdate();
        }
    }

    private void handlePlayerCommand(Connected ctx, ClientEnvelope envelope) {
        if (envelope.commandId() == null || envelope.commandId().isBlank()) {
            sendRaw(ctx.session(), ServerMessage.rejected(ctx.gameRoom().stateVersion(), ctx.driver().roomVersion(),
                    envelope.commandId(), ProtocolErrorCode.INVALID_PAYLOAD.name(), "commandId is required"));
            return;
        }
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
            // commandId idempotency (Architecture 6.7): a duplicate from this same seat returns the very same
            // ProcessedCommand again, with no new engine call - GameRoom does the deduplication.
            processed = ctx.gameRoom().submitPlayerCommand(ctx.seat(), envelope.commandId(), command).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (ExecutionException e) {
            return;
        }
        // resultingStateVersion() (not the room's live stateVersion()): this reply describes THIS command's
        // own outcome (Architecture 6.9), which for a duplicate commandId must be identical to the original
        // reply even if the room has since moved on to a later stateVersion. roomId (not roomCode) scopes the
        // key to this exact room instance, since a closed room's code can later be reused for a new room
        // (R3-P1-3). computeIfAbsent installs the first reply atomically: two concurrent duplicate
        // submissions that both reach here race to compute a reply (their roomVersion reads could otherwise
        // disagree), but only the winner's reply is ever cached or sent to either caller (R3-P1-2).
        CommandReplyKey replyKey = new CommandReplyKey(ctx.room().roomId(), ctx.seat(), envelope.commandId());
        long stateVersion = processed.resultingStateVersion();
        ServerMessage reply = commandReplies.computeIfAbsent(replyKey,
                key -> buildReply(ctx, envelope.commandId(), processed, stateVersion));
        sendRaw(ctx.session(), reply);
    }

    private ServerMessage buildReply(Connected ctx, String commandId, ProcessedCommand processed,
            long stateVersion) {
        long roomVersion = ctx.driver().roomVersion();
        return switch (processed.outcome()) {
            case CommandOutcome.Applied applied -> switch (applied.result()) {
                case GameResult.Accepted ignored -> ServerMessage.accepted(stateVersion, roomVersion, commandId);
                case GameResult.Rejected rejected -> ServerMessage.rejected(stateVersion, roomVersion, commandId,
                        rejected.code().name(), rejected.detail());
            };
            // The only way a PLAYER command reaches GameRoom's internal-command guard is a commandId reused by
            // a different seat: ClientCommands never maps a client commandType to StartRound/ResolveRound.
            case CommandOutcome.Refused refused -> ServerMessage.rejected(stateVersion, roomVersion, commandId,
                    ProtocolErrorCode.COMMAND_ID_REUSED.name(), refused.reason());
        };
    }

    private void broadcastRoomUpdateIfChanged(UUID roomId, RoundFlowDriver driver, long roomVersionBefore) {
        if (driver.roomVersion() == roomVersionBefore) {
            return;
        }
        RoomBroadcaster broadcaster = broadcasters.get(roomId);
        if (broadcaster != null) {
            broadcaster.broadcastRoomUpdate();
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
        closeQuietly(session, CloseStatus.POLICY_VIOLATION);
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            // Already closed.
        }
    }

    private Object seatLock(String roomCode, int seat) {
        return seatLocks.computeIfAbsent(new RoomSeatKey(roomCode, seat), key -> new Object());
    }

    private record Connected(Room room, int seat, GameRoom gameRoom, RoundFlowDriver driver,
            WebSocketSession session) {
    }

    private record RoomSeatKey(String roomCode, int seat) {
    }

    private record CommandReplyKey(UUID roomId, int seat, String commandId) {
    }
}
