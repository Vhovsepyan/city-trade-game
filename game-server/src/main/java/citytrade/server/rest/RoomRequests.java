package citytrade.server.rest;

/** JSON request records for the lobby endpoints. */
public final class RoomRequests {

    private RoomRequests() {
    }

    public record Nickname(String nickname) {
    }

    public record Bot(String type) {
    }
}
