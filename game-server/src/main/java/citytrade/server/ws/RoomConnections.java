package citytrade.server.ws;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.socket.WebSocketSession;

/** Thread-safe registry of the currently connected seats per room, so a room's {@link RoomBroadcaster}
 * knows who to push messages to. Read from the room's own worker thread (broadcasting) and written from
 * WebSocket container threads (HELLO, disconnect), so every map here is a {@link ConcurrentHashMap}. */
final class RoomConnections {

    private final Map<String, Map<Integer, WebSocketSession>> byRoom = new ConcurrentHashMap<>();

    void register(String roomCode, int seat, WebSocketSession session) {
        byRoom.computeIfAbsent(roomCode, code -> new ConcurrentHashMap<>()).put(seat, session);
    }

    /** Only removes {@code session} if it is still the one registered for {@code seat} (T23 will replace it). */
    void unregister(String roomCode, int seat, WebSocketSession session) {
        Map<Integer, WebSocketSession> seats = byRoom.get(roomCode);
        if (seats != null) {
            seats.remove(seat, session);
        }
    }

    Map<Integer, WebSocketSession> sessionsOf(String roomCode) {
        return byRoom.getOrDefault(roomCode, Map.of());
    }
}
