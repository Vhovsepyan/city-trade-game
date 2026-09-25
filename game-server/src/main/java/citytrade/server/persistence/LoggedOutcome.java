package citytrade.server.persistence;

/**
 * What a stored {@link LoggedCommand} did. Only {@code ACCEPTED} is replayed (Architecture 7.2: "Rejected
 * commands are stored for debugging, but replay skips them"); {@code REFUSED} (a client-submitted internal
 * command, T20's guard) never reached the engine at all and is skipped for the same reason.
 */
public enum LoggedOutcome {
    ACCEPTED,
    REJECTED,
    REFUSED
}
