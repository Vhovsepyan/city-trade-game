package citytrade.engine.round;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.setup.ObjectiveChoice;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Numbers Sheet "ROUND ORDER": the steps run in the documented order, in the right phase and round. */
class RoundFlowTest {

    private final Ruleset ruleset = TestRulesets.standard();

    /** Records every step call and gives seat 0 one Money per step, to prove each step sees the previous step's state. */
    private static final class RecordingSteps implements RoundStepHandler {

        final List<String> calls = new ArrayList<>();

        @Override
        public GameState apply(RoundStep step, GameState state, Ruleset ruleset, List<DomainEvent> events) {
            calls.add(step.number() + " " + state.phase() + " round " + state.round());
            events.add(new DomainEvent.ObjectivesChosen(calls.size()));
            PlayerState p = state.player(0);
            ResourceBundle h = p.holdings();
            ResourceBundle more = new ResourceBundle(h.food(), h.energy(), h.materials(), h.technology(), h.money() + 1);
            return state.withPlayer(p.withHoldings(more));
        }
    }

    @Test
    void startRoundRunsAutomaticThenWorldStepsInOrder() {
        RecordingSteps steps = new RecordingSteps();
        GameState ready = readyForRoundOne();

        GameResult.Accepted accepted = assertInstanceOf(GameResult.Accepted.class,
                new RoundFlow(steps).startRound(ready, ruleset));

        assertEquals(List.of(
                "1.1 AUTOMATIC round 1",
                "1.2 AUTOMATIC round 1",
                "1.3 AUTOMATIC round 1",
                "1.4 AUTOMATIC round 1",
                "1.5 AUTOMATIC round 1",
                "2.1 WORLD round 1",
                "2.2 WORLD round 1",
                "2.3 WORLD round 1"), steps.calls);
        assertEquals(GamePhase.WINDOW, accepted.state().phase());
        assertEquals(1, accepted.state().round());
        assertEquals(ready.player(0).holdings().money() + 8, accepted.state().player(0).holdings().money());
        assertEquals(stepEventsThen(8, new DomainEvent.RoundStarted(1)), accepted.events());
    }

    @Test
    void resolveRoundRunsResolutionStepsInOrder() {
        GameState window = assertInstanceOf(GameResult.Accepted.class,
                new RoundFlow(RoundSteps.INSTANCE).startRound(readyForRoundOne(), ruleset)).state();
        RecordingSteps steps = new RecordingSteps();

        GameResult.Accepted accepted = assertInstanceOf(GameResult.Accepted.class,
                new RoundFlow(steps).resolveRound(window, ruleset));

        assertEquals(List.of(
                "4.1 RESOLUTION round 1",
                "4.2 RESOLUTION round 1",
                "4.3 RESOLUTION round 1",
                "4.4 RESOLUTION round 1",
                "4.5 RESOLUTION round 1",
                "4.6 RESOLUTION round 1",
                "4.7 RESOLUTION round 1",
                "4.8 RESOLUTION round 1"), steps.calls);
        assertEquals(GamePhase.RESOLUTION, accepted.state().phase());
        assertEquals(1, accepted.state().round());
        assertEquals(window.player(0).holdings().money() + 8, accepted.state().player(0).holdings().money());
        assertEquals(stepEventsThen(8, new DomainEvent.RoundResolved(1)), accepted.events());
    }

    @Test
    void theLastRoundRunsAllResolutionStepsBeforeTheGameFinishes() {
        RoundFlow real = new RoundFlow(RoundSteps.INSTANCE);
        GameState state = readyForRoundOne();
        for (int round = 1; round < ruleset.roundCount(); round++) {
            state = assertInstanceOf(GameResult.Accepted.class, real.startRound(state, ruleset)).state();
            state = assertInstanceOf(GameResult.Accepted.class, real.resolveRound(state, ruleset)).state();
        }
        state = assertInstanceOf(GameResult.Accepted.class, real.startRound(state, ruleset)).state();
        RecordingSteps steps = new RecordingSteps();

        GameResult.Accepted accepted = assertInstanceOf(GameResult.Accepted.class,
                new RoundFlow(steps).resolveRound(state, ruleset));

        assertEquals(8, steps.calls.size());
        assertEquals("4.8 RESOLUTION round 14", steps.calls.getLast());
        assertEquals(GamePhase.FINISHED, accepted.state().phase());
        assertEquals(14, accepted.state().round());
        List<DomainEvent> expected = new ArrayList<>(stepEventsThen(8, new DomainEvent.RoundResolved(14)));
        expected.add(new DomainEvent.GameFinished());
        assertEquals(expected, accepted.events());
    }

    private static List<DomainEvent> stepEventsThen(int stepCount, DomainEvent last) {
        List<DomainEvent> events = new ArrayList<>();
        for (int i = 1; i <= stepCount; i++) {
            events.add(new DomainEvent.ObjectivesChosen(i));
        }
        events.add(last);
        return events;
    }

    private GameState readyForRoundOne() {
        GameState state = GameSetup.create(5, ruleset);
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            List<ObjectiveCard> dealt = state.player(seat).dealtObjectives();
            ChooseObjectives choose = new ChooseObjectives(seat, List.of(dealt.get(0).id(), dealt.get(1).id()));
            state = assertInstanceOf(GameResult.Accepted.class, ObjectiveChoice.apply(state, choose, ruleset)).state();
        }
        return state;
    }
}
