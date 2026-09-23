package citytrade.engine.command;

/** Why a command was rejected. */
public enum RejectionCode {
    INVALID_PHASE,
    UNKNOWN_PLAYER,
    OBJECTIVES_ALREADY_CHOSEN,
    INVALID_OBJECTIVE_CHOICE,
    OBJECTIVES_NOT_CHOSEN
}
