package citytrade.server.room;

/** Public seat data. It intentionally contains no token or private game data. */
public record RoomSeat(int seat, String nickname, BotType botType) {

    public boolean occupied() {
        return nickname != null || botType != null;
    }

    public boolean human() {
        return nickname != null;
    }
}
