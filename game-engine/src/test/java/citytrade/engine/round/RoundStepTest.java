package citytrade.engine.round;

import static org.junit.jupiter.api.Assertions.assertEquals;

import citytrade.engine.state.GamePhase;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The step list must match Numbers Sheet "ROUND ORDER (exact)" one to one. */
class RoundStepTest {

    @Test
    void stepsAreDeclaredInTheDocumentedOrder() {
        List<String> numbers = Arrays.stream(RoundStep.values()).map(RoundStep::number).toList();
        assertEquals(List.of(
                "1.1", "1.2", "1.3", "1.4", "1.5",
                "2.1", "2.2", "2.3",
                "4.1", "4.2", "4.3", "4.4", "4.5", "4.6", "4.7", "4.8"), numbers);
    }

    @Test
    void stepsAreGroupedByPhase() {
        assertEquals(List.of(
                RoundStep.EVENT_BECOMES_ACTIVE,
                RoundStep.STRAINED_PENALTY,
                RoundStep.PRODUCTION,
                RoundStep.UPKEEP,
                RoundStep.CONTRACT_OBLIGATIONS), RoundStep.of(GamePhase.AUTOMATIC));
        assertEquals(List.of(
                RoundStep.CRISIS_PAYMENTS,
                RoundStep.NEXT_EVENT_WARNING,
                RoundStep.NEW_PROJECT_OR_OPPORTUNITY), RoundStep.of(GamePhase.WORLD));
        assertEquals(List.of(
                RoundStep.TRADE_OFFERS_EXPIRE,
                RoundStep.BIDS_RESOLVE,
                RoundStep.PROJECT_DEADLINE,
                RoundStep.PRESTIGE,
                RoundStep.STORAGE_LIMITS,
                RoundStep.MARKET_PRICES_MOVE,
                RoundStep.TEMPORARY_EVENT_EFFECTS_END,
                RoundStep.FINAL_SCORING), RoundStep.of(GamePhase.RESOLUTION));
        assertEquals(List.of(), RoundStep.of(GamePhase.SETUP));
        assertEquals(List.of(), RoundStep.of(GamePhase.WINDOW));
        assertEquals(List.of(), RoundStep.of(GamePhase.FINISHED));
    }
}
