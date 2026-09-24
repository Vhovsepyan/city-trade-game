package citytrade.server.room;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes expired lobby and finished-room entries without touching active games. */
@Component
public class RoomCleanupJob {

    private final RoomRegistry rooms;

    public RoomCleanupJob(RoomRegistry rooms) {
        this.rooms = rooms;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        rooms.cleanup();
    }
}
