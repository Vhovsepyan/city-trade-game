package citytrade.server.view;

import citytrade.server.room.RoomStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of server metadata for one room, taken at the moment a view is projected
 * (Architecture 6.5). These values live only on the server side, never in the engine {@code GameState}.
 *
 * @param status             the room's lifecycle status
 * @param phaseEndsAt        the deadline of the current phase (window or objective choice); null if none is running
 * @param readySeats         seats that have sent READY this round (D21), or count as ready (D23)
 * @param disconnectedSeats  seats that are currently disconnected (D23)
 * @param roomVersion        the server metadata version (Architecture 6.9); increases on every change here
 */
public record RoomViewContext(
        RoomStatus status,
        Instant phaseEndsAt,
        Set<Integer> readySeats,
        Set<Integer> disconnectedSeats,
        long roomVersion) {

    public RoomViewContext {
        Objects.requireNonNull(status);
        readySeats = Set.copyOf(readySeats);
        disconnectedSeats = Set.copyOf(disconnectedSeats);
    }

    public boolean isReady(int seat) {
        return readySeats.contains(seat);
    }

    public boolean isDisconnected(int seat) {
        return disconnectedSeats.contains(seat);
    }
}
