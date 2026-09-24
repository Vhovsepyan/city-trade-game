package citytrade.server.room;

/** The only response that contains a newly generated bearer token. */
public record RoomCreation(String roomCode, int seat, String token) {
}
