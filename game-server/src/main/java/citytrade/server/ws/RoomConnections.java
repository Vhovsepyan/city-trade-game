package citytrade.server.ws;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.WebSocketSession;

/** Thread-safe registry of the currently connected seats per room, so a room's {@link RoomBroadcaster}
 * knows who to push messages to. Read from the room's own worker thread (broadcasting) and written from
 * WebSocket container threads (HELLO, disconnect), so every map here is a {@link ConcurrentHashMap}.
 *
 * <p>Keyed by the room's stable {@code roomId}, not its reusable {@code roomCode}: a closed room's code can
 * later be reused for a new room (R3-P1-3), and keying by code would otherwise let a still-connected session
 * from the old room receive the new room's broadcasts, or vice versa (R4-P1-1). */
final class RoomConnections {

    private final Map<UUID, Map<Integer, WebSocketSession>> byRoom = new ConcurrentHashMap<>();

    void register(UUID roomId, int seat, WebSocketSession session) {
        byRoom.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>()).put(seat, session);
    }

    /**
     * Only removes {@code session} if it is still the one registered for {@code seat}: a reconnect already
     * overwrote the entry with the new session (T23), so the old session's own close callback must not
     * clobber it. Returns whether {@code session} was still current (and so was actually removed) - the
     * caller uses this to decide whether it may still be the one that gets to mark the seat disconnected
     * (R3-P1-1).
     */
    boolean unregister(UUID roomId, int seat, WebSocketSession session) {
        Map<Integer, WebSocketSession> seats = byRoom.get(roomId);
        return seats != null && seats.remove(seat, session);
    }

    Map<Integer, WebSocketSession> sessionsOf(UUID roomId) {
        return byRoom.getOrDefault(roomId, Map.of());
    }
}
