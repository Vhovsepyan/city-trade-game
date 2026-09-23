package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import org.junit.jupiter.api.Test;

/** GameSetup with the real prototype-001 file (Numbers Sheet 1-2 and 21). */
class PrototypeSetupTest {

    private final Ruleset ruleset = RulesetLoader.load(TestRulesets.PROTOTYPE_001);

    @Test
    void setupFromPrototype001MatchesTheNumbersSheet() {
        GameState state = GameSetup.create(2026, ruleset);

        assertEquals(state, GameSetup.create(2026, ruleset));
        for (PlayerState player : state.players()) {
            ResourceBundle holdings = player.holdings();
            for (Resource resource : Resource.values()) {
                assertEquals(resource == player.city().specialty() ? 4 : 1, holdings.amountOf(resource));
            }
            assertEquals(6, holdings.money());
            assertEquals(1, player.level());
            assertEquals(3, player.dealtObjectives().size());
        }
        assertEquals(2, state.eventWarning().orElseThrow().round());
        assertEquals(11, state.eventDeck().size());
        assertEquals(2, state.projects().size());
        assertEquals(5, state.opportunityDeck().size());
        for (Resource resource : Resource.values()) {
            assertEquals("B", ruleset.market().steps().get(state.market().stepIndexOf(resource)).name());
        }
    }
}
