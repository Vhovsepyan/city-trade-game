package citytrade.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GameEngineTest {

    private static final long SEED = 42;

    private final Ruleset ruleset = TestRulesets.standard();

    @Test
    void newGameStartsInSetupBeforeRoundOne() {
        GameState setup = GameSetup.create(SEED, ruleset);
        assertEquals(GamePhase.SETUP, setup.phase());
        assertEquals(0, setup.round());
    }

    @Test
    void chooseObjectivesGoesThroughTheEngineInSetup() {
        GameState setup = GameSetup.create(SEED, ruleset);
        GameResult.Accepted accepted = accept(setup, chooseFirstTwo(setup, 3));
        assertEquals(List.of(new DomainEvent.ObjectivesChosen(3)), accepted.events());
        assertEquals(GamePhase.SETUP, accepted.state().phase());
    }

    @Test
    void roundOneDoesNotStartBeforeAllObjectivesAreChosen() {
        GameState state = GameSetup.create(SEED, ruleset);
        for (int seat = 0; seat < 3; seat++) {
            state = accept(state, chooseFirstTwo(state, seat)).state();
        }
        assertRejected(state, new StartRound(), RejectionCode.OBJECTIVES_NOT_CHOSEN);
    }

    @Test
    void startRoundOpensTheWindowOfTheNextRound() {
        GameResult.Accepted accepted = accept(readyForRoundOne(), new StartRound());
        assertEquals(GamePhase.WINDOW, accepted.state().phase());
        assertEquals(1, accepted.state().round());
        assertEquals(List.of(new DomainEvent.RoundStarted(1)), accepted.events());
    }

    @Test
    void resolveRoundEndsTheWindow() {
        GameState window = accept(readyForRoundOne(), new StartRound()).state();
        GameResult.Accepted accepted = accept(window, new ResolveRound());
        assertEquals(GamePhase.RESOLUTION, accepted.state().phase());
        assertEquals(1, accepted.state().round());
        assertEquals(List.of(new DomainEvent.RoundResolved(1)), accepted.events());
    }

    @Test
    void fullGameRunsAllRoundsThenFinishes() {
        GameState state = readyForRoundOne();
        for (int round = 1; round <= ruleset.roundCount(); round++) {
            state = accept(state, new StartRound()).state();
            assertEquals(round, state.round());
            assertEquals(GamePhase.WINDOW, state.phase());
            GameResult.Accepted resolved = accept(state, new ResolveRound());
            state = resolved.state();
            // Step events (e.g. discarded excess) come before; the round-flow events close the command.
            List<DomainEvent> flowEvents = resolved.events().stream()
                    .filter(e -> e instanceof DomainEvent.RoundResolved || e instanceof DomainEvent.GameFinished)
                    .toList();
            if (round < ruleset.roundCount()) {
                assertEquals(GamePhase.RESOLUTION, state.phase());
                assertEquals(List.of(new DomainEvent.RoundResolved(round)), flowEvents);
                assertEquals(new DomainEvent.RoundResolved(round), resolved.events().getLast());
            } else {
                assertEquals(GamePhase.FINISHED, state.phase());
                assertEquals(List.of(new DomainEvent.RoundResolved(round), new DomainEvent.GameFinished()), flowEvents);
                assertEquals(new DomainEvent.GameFinished(), resolved.events().getLast());
            }
        }
        assertEquals(ruleset.roundCount(), state.round());
    }

    @Test
    void wrongPhaseCommandsAreRejectedWithInvalidPhase() {
        GameState setup = readyForRoundOne();
        GameState window = accept(setup, new StartRound()).state();
        GameState resolution = accept(window, new ResolveRound()).state();
        GameState finished = finishedGame();

        // SETUP: no round to resolve yet.
        assertRejected(setup, new ResolveRound(), RejectionCode.INVALID_PHASE);
        // WINDOW: the round is already running; objectives are fixed.
        assertRejected(window, new StartRound(), RejectionCode.INVALID_PHASE);
        assertRejected(window, chooseFirstTwo(window, 0), RejectionCode.INVALID_PHASE);
        // RESOLUTION: the round is already resolved; objectives are fixed.
        assertRejected(resolution, new ResolveRound(), RejectionCode.INVALID_PHASE);
        assertRejected(resolution, chooseFirstTwo(resolution, 0), RejectionCode.INVALID_PHASE);
        // FINISHED: nothing is accepted.
        assertRejected(finished, new StartRound(), RejectionCode.INVALID_PHASE);
        assertRejected(finished, new ResolveRound(), RejectionCode.INVALID_PHASE);
        assertRejected(finished, chooseFirstTwo(finished, 0), RejectionCode.INVALID_PHASE);
    }

    @Test
    void chooseObjectivesIsCheckedForPhaseBeforeAnythingElse() {
        // In SETUP this command would be UNKNOWN_PLAYER; outside SETUP the phase check comes first.
        GameState window = accept(readyForRoundOne(), new StartRound()).state();
        assertRejected(window, new ChooseObjectives(7, List.of()), RejectionCode.INVALID_PHASE);
    }

    @Test
    void sameSeedAndCommandsGiveTheSameStates() {
        assertEquals(playedStates(), playedStates());
    }

    private List<GameState> playedStates() {
        List<GameState> states = new ArrayList<>();
        GameState state = readyForRoundOne();
        states.add(state);
        for (int round = 1; round <= 3; round++) {
            state = accept(state, new StartRound()).state();
            states.add(state);
            state = accept(state, new ResolveRound()).state();
            states.add(state);
        }
        return states;
    }

    private GameState finishedGame() {
        GameState state = readyForRoundOne();
        for (int round = 1; round <= ruleset.roundCount(); round++) {
            state = accept(state, new StartRound()).state();
            state = accept(state, new ResolveRound()).state();
        }
        assertEquals(GamePhase.FINISHED, state.phase());
        return state;
    }

    private GameState readyForRoundOne() {
        GameState state = GameSetup.create(SEED, ruleset);
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            state = accept(state, chooseFirstTwo(state, seat)).state();
        }
        return state;
    }

    /** Rejected: the right code, and the input state is still equal to a fresh copy (nothing changed). */
    private void assertRejected(GameState state, GameCommand command, RejectionCode expected) {
        GameState copyBefore = copyOf(state);
        GameResult result = GameEngine.apply(state, command, ruleset);
        assertEquals(expected, assertInstanceOf(GameResult.Rejected.class, result).code(), command.toString());
        assertEquals(copyBefore, state, "a rejected command leaves the state unchanged");
    }

    private static GameState copyOf(GameState s) {
        return new GameState(s.rulesetVersion(), s.round(), s.phase(), s.random(), new ArrayList<>(s.players()),
                s.market(), new ArrayList<>(s.eventDeck()), s.eventWarning(), s.activeEvent(), new ArrayList<>(s.projects()),
                new ArrayList<>(s.opportunityDeck()), new ArrayList<>(s.opportunities()),
                new ArrayList<>(s.tradeOffers()), s.nextOfferId(),
                new ArrayList<>(s.contracts()), s.nextContractId(), s.finalResult());
    }

    private GameResult.Accepted accept(GameState state, GameCommand command) {
        return assertInstanceOf(GameResult.Accepted.class, GameEngine.apply(state, command, ruleset));
    }

    private static ChooseObjectives chooseFirstTwo(GameState state, int seat) {
        List<ObjectiveCard> dealt = state.player(seat).dealtObjectives();
        return new ChooseObjectives(seat, List.of(dealt.get(0).id(), dealt.get(1).id()));
    }
}
