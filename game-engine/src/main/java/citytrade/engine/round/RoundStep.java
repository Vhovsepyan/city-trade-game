package citytrade.engine.round;

import citytrade.engine.state.GamePhase;
import java.util.Arrays;
import java.util.List;

/**
 * The steps of one round, declared in the exact Numbers Sheet "ROUND ORDER".
 * The declaration order IS the execution order; {@link RoundFlow} never reorders them.
 * Section 3 (the window) has no steps: it is the time in which player commands are accepted.
 */
public enum RoundStep {
    EVENT_BECOMES_ACTIVE("1.1", GamePhase.AUTOMATIC),
    STRAINED_PENALTY("1.2", GamePhase.AUTOMATIC),
    PRODUCTION("1.3", GamePhase.AUTOMATIC),
    UPKEEP("1.4", GamePhase.AUTOMATIC),
    CONTRACT_OBLIGATIONS("1.5", GamePhase.AUTOMATIC),

    CRISIS_PAYMENTS("2.1", GamePhase.WORLD),
    NEXT_EVENT_WARNING("2.2", GamePhase.WORLD),
    NEW_PROJECT_OR_OPPORTUNITY("2.3", GamePhase.WORLD),

    TRADE_OFFERS_EXPIRE("4.1", GamePhase.RESOLUTION),
    BIDS_RESOLVE("4.2", GamePhase.RESOLUTION),
    PROJECT_DEADLINE("4.3", GamePhase.RESOLUTION),
    PRESTIGE("4.4", GamePhase.RESOLUTION),
    STORAGE_LIMITS("4.5", GamePhase.RESOLUTION),
    MARKET_PRICES_MOVE("4.6", GamePhase.RESOLUTION),
    TEMPORARY_EVENT_EFFECTS_END("4.7", GamePhase.RESOLUTION),
    FINAL_SCORING("4.8", GamePhase.RESOLUTION);

    private final String number;
    private final GamePhase phase;

    RoundStep(String number, GamePhase phase) {
        this.number = number;
        this.phase = phase;
    }

    /** The step number in the Numbers Sheet, e.g. "1.3". */
    public String number() {
        return number;
    }

    /** The round phase this step belongs to (AUTOMATIC, WORLD or RESOLUTION). */
    public GamePhase phase() {
        return phase;
    }

    /** The steps of one phase in execution order. */
    public static List<RoundStep> of(GamePhase phase) {
        return Arrays.stream(values()).filter(step -> step.phase == phase).toList();
    }
}
