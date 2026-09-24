package citytrade.server.room;

import java.time.Instant;
import java.util.List;

/** Lobby-safe room information returned by GET /rooms/{code}. */
public record RoomSnapshot(String roomCode, RoomStatus status, List<RoomSeat> seats,
        String rulesetVersion, Instant createdAt) {

    public RoomSnapshot {
        seats = List.copyOf(seats);
    }
}
