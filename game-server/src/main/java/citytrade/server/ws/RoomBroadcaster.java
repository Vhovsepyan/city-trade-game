package citytrade.server.ws;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.server.game.CommandOutcome;
import citytrade.server.game.GameRoom;
import citytrade.server.game.ProcessedCommand;
import citytrade.server.game.RoundFlowDriver;
import citytrade.server.room.Room;
import citytrade.server.view.Notice;
import citytrade.server.view.NoticeEvent;
import citytrade.server.view.NoticeProjector;
import citytrade.server.view.PlayerGameView;
import citytrade.server.view.RoomViewContext;
import citytrade.server.view.ViewProjector;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

/**
 * The single {@link GameRoom} listener for one ACTIVE room's WebSocket clients (Architecture 6.9). Registered
 * once per room (by {@link GameWebSocketHandler} on the first successful HELLO into it). Reacts to every
 * processed command, whatever its origin (PLAYER, BOT or SYSTEM): when the authoritative {@code GameState}
 * actually changed, every connected seat gets a fresh {@link PlayerGameView} (STATE_UPDATE), plus one
 * privacy-filtered {@link NoticeEvent} per event ({@link NoticeProjector}) to the seat(s) it concerns.
 *
 * <p>{@link #onProcessed} runs on the room's own single worker thread (see {@link GameRoom#addListener}), so
 * {@code lastBroadcastStateVersion} needs no synchronization there. {@link #broadcastRoomUpdate()} is called
 * from a different thread (the WebSocket handler, right after READY changes {@code roomVersion}) but only
 * reads live snapshots (state, driver getters) and never touches that field, so the two never race.
 */
final class RoomBroadcaster {

    private final GameRoom gameRoom;
    private final RoundFlowDriver driver;
    private final Room room;
    private final RoomConnections connections;
    private final ObjectMapper json;

    private long lastBroadcastStateVersion;

    RoomBroadcaster(GameRoom gameRoom, RoundFlowDriver driver, Room room, RoomConnections connections,
            ObjectMapper json) {
        this.gameRoom = Objects.requireNonNull(gameRoom);
        this.driver = Objects.requireNonNull(driver);
        this.room = Objects.requireNonNull(room);
        this.connections = Objects.requireNonNull(connections);
        this.json = Objects.requireNonNull(json);
        // Same pattern as RoundFlowDriver.start()'s lastSeenStateVersion: capture whatever stateVersion the
        // room already has right now, so a room that advanced before this listener was attached does not
        // cause the very next processed command to look like a change when it is not.
        this.lastBroadcastStateVersion = gameRoom.stateVersion();
    }

    void onProcessed(ProcessedCommand processed) {
        if (processed.resultingStateVersion() == lastBroadcastStateVersion) {
            return;
        }
        lastBroadcastStateVersion = processed.resultingStateVersion();
        broadcast(ServerMessageType.STATE_UPDATE);
        if (processed.outcome() instanceof CommandOutcome.Applied applied
                && applied.result() instanceof GameResult.Accepted accepted) {
            broadcastNotices(accepted.events());
        }
    }

    /** Called by the WebSocket handler right after READY changed {@code roomVersion} (state did not change). */
    void broadcastRoomUpdate() {
        broadcast(ServerMessageType.ROOM_UPDATE);
    }

    ServerMessage snapshotFor(int seat) {
        return ServerMessage.snapshot(gameRoom.stateVersion(), driver.roomVersion(), viewFor(seat));
    }

    private void broadcast(ServerMessageType type) {
        long stateVersion = gameRoom.stateVersion();
        long roomVersion = driver.roomVersion();
        for (Map.Entry<Integer, WebSocketSession> entry : connections.sessionsOf(room.roomCode()).entrySet()) {
            PlayerGameView view = viewFor(entry.getKey());
            ServerMessage message = type == ServerMessageType.ROOM_UPDATE
                    ? ServerMessage.roomUpdate(stateVersion, roomVersion, view)
                    : ServerMessage.stateUpdate(stateVersion, roomVersion, view);
            send(entry.getValue(), message);
        }
    }

    private void broadcastNotices(List<DomainEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        long stateVersion = gameRoom.stateVersion();
        long roomVersion = driver.roomVersion();
        Map<Integer, WebSocketSession> sessions = connections.sessionsOf(room.roomCode());
        for (Notice notice : NoticeProjector.project(gameRoom.state(), events)) {
            WebSocketSession session = sessions.get(notice.seat());
            if (session == null) {
                continue;
            }
            ServerMessageType type = switch (notice.event()) {
                case NoticeEvent.EventWarned ignored -> ServerMessageType.ROUND_WARNING;
                case NoticeEvent.RoundResolved ignored -> ServerMessageType.ROUND_RESOLVED;
                case NoticeEvent.GameFinished ignored -> ServerMessageType.GAME_FINISHED;
                // Not exhaustive on purpose: every other NoticeEvent kind is a plain, generic notice.
                default -> ServerMessageType.NOTICE;
            };
            send(session, ServerMessage.event(type, stateVersion, roomVersion, notice.event()));
        }
    }

    private PlayerGameView viewFor(int seat) {
        Set<Integer> ready = new HashSet<>();
        Set<Integer> disconnected = new HashSet<>();
        for (int s = 0; s < Room.SEAT_COUNT; s++) {
            if (driver.isReady(s)) {
                ready.add(s);
            }
            if (driver.isDisconnected(s)) {
                disconnected.add(s);
            }
        }
        RoomViewContext context =
                new RoomViewContext(room.status(), driver.phaseEndsAt(), ready, disconnected, driver.roomVersion());
        return ViewProjector.project(gameRoom.state(), context, seat);
    }

    private void send(WebSocketSession session, ServerMessage message) {
        try {
            session.sendMessage(new TextMessage(json.writeValueAsString(message)));
        } catch (IOException | RuntimeException e) {
            // The client is gone or the write failed; afterConnectionClosed cleans up the registration.
        }
    }
}
