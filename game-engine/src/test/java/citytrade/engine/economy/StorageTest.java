package citytrade.engine.economy;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.seatOf;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import citytrade.engine.CityType;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.ruleset.StorageRules;
import citytrade.engine.state.GameState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageTest {

    private static final long SEED = 5;

    private final Ruleset ruleset = TestRulesets.standard();

    @Test
    void holdingsMayExceedTheLimitDuringTheWindowAndTheExcessIsDiscardedAtResolution() {
        GameState state = readyForRoundOne(SEED, ruleset);
        int seat = seatOf(state, CityType.AGRICULTURAL);
        state = withCity(state, seat, 1, new ResourceBundle(9, 1, 1, 1, 0));

        GameState window = accept(state, new StartRound(), ruleset).state();
        assertEquals(new ResourceBundle(12, 2, 2, 2, 2), window.player(seat).holdings(), "no limit in the window");

        GameResult.Accepted resolved = accept(window, new ResolveRound(), ruleset);
        assertEquals(new ResourceBundle(10, 2, 2, 2, 2), resolved.state().player(seat).holdings());
        assertEquals(List.of(new DomainEvent.ExcessDiscarded(seat, new ResourceBundle(2, 0, 0, 0, 0)),
                new DomainEvent.RoundResolved(1)), resolved.events());
    }

    @Test
    void eachResourceIsCappedSeparatelyAndMoneyHasNoLimit() {
        GameState state = readyForRoundOne(SEED, ruleset);
        state = withCity(state, 2, 1, new ResourceBundle(10, 14, 11, 3, 99));
        List<DomainEvent> events = new ArrayList<>();

        GameState after = Storage.discardExcess(state, ruleset, events);

        assertEquals(new ResourceBundle(10, 10, 10, 3, 99), after.player(2).holdings());
        assertEquals(List.of(new DomainEvent.ExcessDiscarded(2, new ResourceBundle(0, 4, 1, 0, 0))), events);
    }

    @Test
    void nothingIsDiscardedWithinTheLimit() {
        GameState state = readyForRoundOne(SEED, ruleset);
        List<DomainEvent> events = new ArrayList<>();

        GameState after = Storage.discardExcess(state, ruleset, events);

        assertEquals(state, after);
        assertEquals(List.of(), events);
    }

    @Test
    void theLimitComesFromTheRuleset() {
        Ruleset small = new Ruleset(ruleset.version(), ruleset.roundCount(), ruleset.playerCount(),
                ruleset.starting(), ruleset.levels(), new StorageRules(3), ruleset.strained(),
                ruleset.buildings(), ruleset.market(), ruleset.contracts(), ruleset.events(), ruleset.objectives(),
                ruleset.projects(), ruleset.opportunities());
        GameState state = readyForRoundOne(SEED, small);
        state = withCity(state, 0, 1, new ResourceBundle(4, 4, 4, 4, 4));

        GameState after = Storage.discardExcess(state, small, new ArrayList<>());

        assertEquals(new ResourceBundle(3, 3, 3, 3, 4), after.player(0).holdings());
    }
}
