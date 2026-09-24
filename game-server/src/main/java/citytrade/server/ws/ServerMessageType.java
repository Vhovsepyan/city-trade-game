package citytrade.server.ws;

/** Every {@code /ws} server-to-client message shape (Architecture 6.9, T22's Do section). */
enum ServerMessageType {
    /** HELLO or a later envelope had an unsupported {@code protocolVersion}; sent once, then the session is closed. */
    PROTOCOL_UNSUPPORTED,
    /** The full {@code PlayerGameView} of the receiving seat, sent right after a successful HELLO. */
    FULL_SNAPSHOT,
    /** The full {@code PlayerGameView} again: the authoritative {@code GameState} changed. */
    STATE_UPDATE,
    /** The full {@code PlayerGameView} again: only server metadata changed (READY, connection, deadline). */
    ROOM_UPDATE,
    /** A command from this connection was accepted by the engine. */
    COMMAND_ACCEPTED,
    /** A command from this connection was rejected, or never reached the engine. */
    COMMAND_REJECTED,
    /** The event warned for the next event round ({@code NoticeEvent.EventWarned}). */
    ROUND_WARNING,
    /** A round was resolved ({@code NoticeEvent.RoundResolved}). */
    ROUND_RESOLVED,
    /** Round 14 resolved and the game ended ({@code NoticeEvent.GameFinished}). */
    GAME_FINISHED,
    /** Any other privacy-filtered domain event ({@code NoticeProjector}). */
    NOTICE
}
