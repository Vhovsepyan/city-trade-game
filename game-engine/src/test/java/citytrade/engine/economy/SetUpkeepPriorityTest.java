package citytrade.engine.economy;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.seatOf;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import java.util.List;
import org.junit.jupiter.api.Test;

class SetUpkeepPriorityTest {

    private static final long SEED = 9;
    private static final List<Resource> TMF = List.of(Resource.TECHNOLOGY, Resource.MATERIALS, Resource.FOOD);

    private final Ruleset ruleset = TestRulesets.standard();

    @Test
    void acceptedInSetupAndStoredForThePlayer() {
        GameState setup = GameSetup.create(SEED, ruleset);
        int seat = seatOf(setup, CityType.ENERGY);

        GameResult.Accepted accepted = accept(setup, new SetUpkeepPriority(seat, TMF), ruleset);

        assertEquals(TMF, accepted.state().player(seat).upkeepPriority());
        assertEquals(List.of(new DomainEvent.UpkeepPrioritySet(seat)), accepted.events());
    }

    @Test
    void acceptedInTheWindowAndUsedForTheNextRoundsUpkeep() {
        GameState state = readyForRoundOne(SEED, ruleset);
        int seat = seatOf(state, CityType.ENERGY);
        state = accept(state, new StartRound(), ruleset).state();
        state = withCity(state, seat, 2, new ResourceBundle(5, 0, 5, 0, 0));

        state = accept(state, new SetUpkeepPriority(seat, TMF), ruleset).state();
        state = accept(state, new ResolveRound(), ruleset).state();
        state = accept(state, new StartRound(), ruleset).state();

        // Production +1 F, +4 E, +1 M, +1 T, +3 $; then upkeep: Technology first in the priority.
        assertEquals(new ResourceBundle(6, 4, 6, 0, 3), state.player(seat).holdings());
    }

    @Test
    void priorityStaysUntilChanged() {
        GameState state = readyForRoundOne(SEED, ruleset);
        int seat = seatOf(state, CityType.ENERGY);
        state = accept(state, new StartRound(), ruleset).state();
        state = accept(state, new SetUpkeepPriority(seat, TMF), ruleset).state();
        for (int round = 0; round < 3; round++) {
            state = accept(state, new ResolveRound(), ruleset).state();
            state = accept(state, new StartRound(), ruleset).state();
        }
        assertEquals(TMF, state.player(seat).upkeepPriority());

        List<Resource> mft = List.of(Resource.MATERIALS, Resource.FOOD, Resource.TECHNOLOGY);
        state = accept(state, new SetUpkeepPriority(seat, mft), ruleset).state();
        assertEquals(mft, state.player(seat).upkeepPriority());
    }

    @Test
    void orderMustContainEveryNonSpecialtyResourceExactlyOnce() {
        GameState state = readyForRoundOne(SEED, ruleset);
        int seat = seatOf(state, CityType.ENERGY);

        assertRejected(state, new SetUpkeepPriority(seat, List.of()), ruleset, RejectionCode.INVALID_UPKEEP_PRIORITY);
        assertRejected(state, new SetUpkeepPriority(seat, List.of(Resource.FOOD, Resource.MATERIALS)), ruleset,
                RejectionCode.INVALID_UPKEEP_PRIORITY);
        assertRejected(state, new SetUpkeepPriority(seat,
                        List.of(Resource.FOOD, Resource.ENERGY, Resource.MATERIALS)), ruleset,
                RejectionCode.INVALID_UPKEEP_PRIORITY);
        assertRejected(state, new SetUpkeepPriority(seat,
                        List.of(Resource.FOOD, Resource.FOOD, Resource.MATERIALS)), ruleset,
                RejectionCode.INVALID_UPKEEP_PRIORITY);
        assertRejected(state, new SetUpkeepPriority(seat,
                        List.of(Resource.FOOD, Resource.MATERIALS, Resource.TECHNOLOGY, Resource.ENERGY)), ruleset,
                RejectionCode.INVALID_UPKEEP_PRIORITY);
    }

    @Test
    void unknownSeatIsRejected() {
        GameState state = readyForRoundOne(SEED, ruleset);
        assertRejected(state, new SetUpkeepPriority(4, TMF), ruleset, RejectionCode.UNKNOWN_PLAYER);
        assertRejected(state, new SetUpkeepPriority(-1, TMF), ruleset, RejectionCode.UNKNOWN_PLAYER);
    }

    @Test
    void rejectedOutsideSetupAndWindow() {
        GameState state = readyForRoundOne(SEED, ruleset);
        int seat = seatOf(state, CityType.ENERGY);
        state = accept(state, new StartRound(), ruleset).state();
        GameState resolution = accept(state, new ResolveRound(), ruleset).state();
        assertRejected(resolution, new SetUpkeepPriority(seat, TMF), ruleset, RejectionCode.INVALID_PHASE);

        GameState finished = resolution;
        for (int round = 2; round <= ruleset.roundCount(); round++) {
            finished = accept(finished, new StartRound(), ruleset).state();
            finished = accept(finished, new ResolveRound(), ruleset).state();
        }
        assertRejected(finished, new SetUpkeepPriority(seat, TMF), ruleset, RejectionCode.INVALID_PHASE);
    }
}
