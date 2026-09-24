package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import org.junit.jupiter.api.Test;

/** Determinism: the same seed, ruleset and ordered commands always give the same states and events. */
class ReplayTest {

    private final Ruleset ruleset = RulesetLoader.load(TestRulesets.PROTOTYPE_001);

    @Test
    void replayingTheCommandLogGivesTheSameStatesAndEvents() {
        ScriptedGame.Played original = ScriptedGame.play(FullGameTest.SEED, ruleset);

        ScriptedGame.Played replayed = ScriptedGame.replay(FullGameTest.SEED, ruleset, original.commands());

        assertEquals(original.finalState(), replayed.finalState());
        assertEquals(original.states(), replayed.states());
        assertEquals(original.events(), replayed.events());
    }

    @Test
    void replayWithAFreshlyLoadedRulesetGivesTheSameFinalState() {
        ScriptedGame.Played original = ScriptedGame.play(FullGameTest.SEED, ruleset);
        Ruleset reloaded = RulesetLoader.load(TestRulesets.PROTOTYPE_001);

        ScriptedGame.Played replayed = ScriptedGame.replay(FullGameTest.SEED, reloaded, original.commands());

        assertEquals(original.finalState(), replayed.finalState());
    }

    @Test
    void anotherSeedDealsAnotherGame() {
        // Guards the replay test against a seed that is ignored: then every game would trivially be equal.
        assertNotEquals(GameSetup.create(FullGameTest.SEED, ruleset), GameSetup.create(FullGameTest.SEED + 1, ruleset));
    }
}
