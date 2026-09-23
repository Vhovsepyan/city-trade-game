package citytrade.engine.event;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.readyForRoundOne;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.TestRulesets;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.EventWarning;
import citytrade.engine.state.GameState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Event timing (Numbers Sheet 14, Concept 21) over a whole game with the 12 prototype-001 cards. */
class EventScheduleTest {

    private static final int ROUNDS = 14;

    private final Ruleset ruleset = TestRulesets.withEventDeck(TestRulesets.standard(), TestRulesets.prototypeEvents());

    @Test
    void noEventInRoundsOneAndFourteenAndEachCardOnceInRoundsTwoToThirteen() {
        GameState state = readyForRoundOne(EventTestSupport.SEED, ruleset);
        List<EventCard> active = new ArrayList<>();
        for (int round = 1; round <= ROUNDS; round++) {
            state = accept(state, new StartRound(), ruleset).state();
            if (round == 1 || round == ROUNDS) {
                assertEquals(Optional.empty(), state.activeEvent(), "round " + round);
            } else {
                active.add(state.activeEvent().orElseThrow());
            }
            state = accept(state, new ResolveRound(), ruleset).state();
        }
        assertEquals(12, active.size());
        assertEquals(new HashSet<>(TestRulesets.prototypeEvents()), new HashSet<>(active));
    }

    @Test
    void eachEventIsWarnedOneRoundBeforeItBecomesActiveAtTheStartOfItsRound() {
        GameState state = readyForRoundOne(EventTestSupport.SEED, ruleset);
        EventWarning warning = state.eventWarning().orElseThrow();
        assertEquals(2, warning.round(), "the first warning is shown at setup, for Round 2");

        for (int round = 1; round <= ROUNDS; round++) {
            GameResult.Accepted started = accept(state, new StartRound(), ruleset);
            state = started.state();
            if (round >= 2 && round <= 13) {
                assertEquals(Optional.of(warning.card()), state.activeEvent(), "the warned card, round " + round);
                assertEquals(new DomainEvent.EventActivated(round, warning.card().id()), started.events().getFirst(),
                        "step 1.1 is the first step of the round");
            }
            if (round <= 12) {
                warning = state.eventWarning().orElseThrow();
                assertEquals(round + 1, warning.round(), "warning in round " + round + " is for the next round");
                if (round >= 2) {
                    assertTrue(started.events().contains(new DomainEvent.EventWarned(round + 1, warning.card().id())));
                }
            } else {
                assertEquals(Optional.empty(), state.eventWarning(), "no event in round 14, so no warning");
            }
            state = accept(state, new ResolveRound(), ruleset).state();
        }
        assertTrue(state.eventDeck().isEmpty());
    }

    @Test
    void roundOneShowsNoNewWarningBecauseTheSetupWarningIsStillWaiting() {
        GameState setup = readyForRoundOne(EventTestSupport.SEED, ruleset);
        GameResult.Accepted round1 = accept(setup, new StartRound(), ruleset);

        assertEquals(setup.eventWarning(), round1.state().eventWarning());
        assertEquals(setup.eventDeck(), round1.state().eventDeck());
        assertFalse(round1.events().stream().anyMatch(DomainEvent.EventWarned.class::isInstance));
    }

    @Test
    void activeEventEndsAtResolution() {
        GameState state = readyForRoundOne(EventTestSupport.SEED, ruleset);
        state = accept(accept(state, new StartRound(), ruleset).state(), new ResolveRound(), ruleset).state();
        state = accept(state, new StartRound(), ruleset).state();
        EventCard card = state.activeEvent().orElseThrow();

        GameResult.Accepted resolved = accept(state, new ResolveRound(), ruleset);
        assertEquals(Optional.empty(), resolved.state().activeEvent());
        assertTrue(resolved.events().contains(new DomainEvent.EventEnded(card.id())));
    }
}
