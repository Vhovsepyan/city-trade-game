package citytrade.server.ws;

/**
 * A COMMAND_REJECTED that never reached the engine, so it has no {@code RejectionCode}. Distinct from the
 * engine's {@code RejectionCode} enum (game-engine has no knowledge of the wire protocol).
 */
enum ProtocolErrorCode {
    /** {@code commandType} is not READY, SNAPSHOT_REQUEST, or a known player command. */
    UNSUPPORTED_COMMAND_TYPE,
    /** The payload could not be parsed into that command type's shape, e.g. an extra "seat" field. */
    INVALID_PAYLOAD,
    /** {@code commandId} was already used by a different seat (Architecture 6.7). */
    COMMAND_ID_REUSED
}
