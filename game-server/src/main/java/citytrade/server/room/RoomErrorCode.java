package citytrade.server.room;

/** Stable machine-readable REST errors for room and lobby actions. */
public enum RoomErrorCode {
    ROOM_NOT_FOUND,
    ROOM_FULL,
    ROOM_NOT_JOINABLE,
    ROOM_NOT_IN_LOBBY,
    ROOM_NOT_FULL,
    NOT_HOST,
    INVALID_NICKNAME,
    INVALID_BOT_TYPE,
    INVALID_SEAT,
    HOST_CANNOT_BE_REMOVED,
    ROOM_NOT_CLOSABLE
}
