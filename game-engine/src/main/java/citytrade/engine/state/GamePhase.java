package citytrade.engine.state;

/**
 * Where the game is (Numbers Sheet "ROUND ORDER").
 * AUTOMATIC and WORLD are passed through inside {@code StartRound}; commands only ever see
 * SETUP, WINDOW, RESOLUTION (round resolved, next round not started yet) or FINISHED.
 */
public enum GamePhase {
    /** Before Round 1: players choose objectives (D5). */
    SETUP,
    /** Section 1: automatic update. */
    AUTOMATIC,
    /** Section 2: world update. */
    WORLD,
    /** Section 3: development and trade window. */
    WINDOW,
    /** Section 4: round resolution; stays set until the next round starts. */
    RESOLUTION,
    /** After the last round is resolved. */
    FINISHED
}
