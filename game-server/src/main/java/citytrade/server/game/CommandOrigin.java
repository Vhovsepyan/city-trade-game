package citytrade.server.game;

/** Who caused a command to be submitted to a {@link GameRoom} (Architecture 6.2). */
public enum CommandOrigin {
    PLAYER,
    BOT,
    SYSTEM
}
