package citytrade.engine.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.TestRulesets;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectiveChoiceTest {

    private final Ruleset ruleset = TestRulesets.standard();
    private final GameState setup = GameSetup.create(21, ruleset);

    @Test
    void keepsTwoDealtObjectives() {
        List<ObjectiveCard> dealt = setup.player(1).dealtObjectives();
        GameResult.Accepted accepted = accept(setup, new ChooseObjectives(1, List.of(dealt.get(0).id(), dealt.get(2).id())));

        PlayerState player = accepted.state().player(1);
        assertEquals(List.of(dealt.get(0), dealt.get(2)), player.keptObjectives());
        assertEquals(dealt, player.dealtObjectives());
        assertTrue(player.hasChosenObjectives());
        assertEquals(List.of(new DomainEvent.ObjectivesChosen(1)), accepted.events());
    }

    @Test
    void changesOnlyTheChoosingPlayer() {
        GameState next = accept(setup, chooseFirstTwo(setup, 2)).state();
        for (int seat : List.of(0, 1, 3)) {
            assertEquals(setup.player(seat), next.player(seat));
        }
        assertEquals(setup.eventDeck(), next.eventDeck());
        assertEquals(setup.random(), next.random());
    }

    @Test
    void orderOfIdsInTheCommandDoesNotChangeTheResult() {
        List<ObjectiveCard> dealt = setup.player(0).dealtObjectives();
        GameState forward = accept(setup, new ChooseObjectives(0, List.of(dealt.get(0).id(), dealt.get(1).id()))).state();
        GameState backward = accept(setup, new ChooseObjectives(0, List.of(dealt.get(1).id(), dealt.get(0).id()))).state();
        assertEquals(forward, backward);
    }

    @Test
    void gameIsReadyOnlyAfterAllFourPlayersChose() {
        GameState state = setup;
        for (int seat = 0; seat < 4; seat++) {
            assertFalse(state.allObjectivesChosen(), "not ready before seat " + seat + " chose");
            state = accept(state, chooseFirstTwo(state, seat)).state();
        }
        assertTrue(state.allObjectivesChosen());
    }

    @Test
    void rejectsUnknownSeat() {
        assertRejected(new ChooseObjectives(4, List.of("OBJECTIVE_1", "OBJECTIVE_2")), RejectionCode.UNKNOWN_PLAYER);
        assertRejected(new ChooseObjectives(-1, List.of("OBJECTIVE_1", "OBJECTIVE_2")), RejectionCode.UNKNOWN_PLAYER);
    }

    @Test
    void rejectsASecondChoice() {
        GameState chosen = accept(setup, chooseFirstTwo(setup, 0)).state();
        List<ObjectiveCard> dealt = chosen.player(0).dealtObjectives();
        ChooseObjectives again = new ChooseObjectives(0, List.of(dealt.get(1).id(), dealt.get(2).id()));
        GameResult result = ObjectiveChoice.apply(chosen, again, ruleset);
        assertEquals(RejectionCode.OBJECTIVES_ALREADY_CHOSEN, assertInstanceOf(GameResult.Rejected.class, result).code());
    }

    @Test
    void rejectsKeepingTooFewOrTooMany() {
        List<ObjectiveCard> dealt = setup.player(0).dealtObjectives();
        assertRejected(new ChooseObjectives(0, List.of(dealt.get(0).id())), RejectionCode.INVALID_OBJECTIVE_CHOICE);
        assertRejected(new ChooseObjectives(0, dealt.stream().map(ObjectiveCard::id).toList()),
                RejectionCode.INVALID_OBJECTIVE_CHOICE);
        assertRejected(new ChooseObjectives(0, List.of()), RejectionCode.INVALID_OBJECTIVE_CHOICE);
    }

    @Test
    void rejectsTheSameObjectiveTwice() {
        String id = setup.player(0).dealtObjectives().getFirst().id();
        assertRejected(new ChooseObjectives(0, List.of(id, id)), RejectionCode.INVALID_OBJECTIVE_CHOICE);
    }

    @Test
    void rejectsAnObjectiveDealtToAnotherPlayer() {
        String own = setup.player(0).dealtObjectives().getFirst().id();
        String foreign = setup.player(1).dealtObjectives().getFirst().id();
        assertRejected(new ChooseObjectives(0, List.of(own, foreign)), RejectionCode.INVALID_OBJECTIVE_CHOICE);
    }

    @Test
    void rejectsAnUnknownObjectiveId() {
        String own = setup.player(0).dealtObjectives().getFirst().id();
        assertRejected(new ChooseObjectives(0, List.of(own, "NO_SUCH_OBJECTIVE")), RejectionCode.INVALID_OBJECTIVE_CHOICE);
    }

    private void assertRejected(ChooseObjectives command, RejectionCode expected) {
        GameState before = GameSetup.create(21, ruleset);
        GameResult result = ObjectiveChoice.apply(setup, command, ruleset);
        assertEquals(expected, assertInstanceOf(GameResult.Rejected.class, result).code());
        assertEquals(before, setup, "a rejected command leaves the state unchanged");
    }

    private GameResult.Accepted accept(GameState state, ChooseObjectives command) {
        return assertInstanceOf(GameResult.Accepted.class, ObjectiveChoice.apply(state, command, ruleset));
    }

    private static ChooseObjectives chooseFirstTwo(GameState state, int seat) {
        List<ObjectiveCard> dealt = state.player(seat).dealtObjectives();
        return new ChooseObjectives(seat, List.of(dealt.get(0).id(), dealt.get(1).id()));
    }
}
