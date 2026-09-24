package citytrade.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationOptionsTest {

    @Test
    void onlyTheRulesetIsRequired() {
        SimulationOptions options = SimulationOptions.parse(new String[] {"--ruleset", "prototype-001"});

        assertEquals("prototype-001", options.ruleset());
        assertEquals(100, options.games());
        assertEquals(1, options.firstSeed());
        assertEquals(List.of(BotProfile.BASELINE), options.bots());
        assertEquals(Path.of("rulesets"), options.rulesetsDir());
        assertEquals(Path.of("build/sim"), options.outputDir());
        assertEquals(Path.of("rulesets", "prototype-001.json"), options.rulesetFile());
    }

    @Test
    void readsEveryOption() {
        SimulationOptions options = SimulationOptions.parse(new String[] {"--games", "1000", "--seed", "-5",
                "--bots", "baseline,TRADER,baseline,trader", "--ruleset", "prototype-001", "--rulesets-dir", "r",
                "--out", "o"});

        assertEquals(1000, options.games());
        assertEquals(-5, options.firstSeed());
        assertEquals(List.of(BotProfile.BASELINE, BotProfile.TRADER, BotProfile.BASELINE, BotProfile.TRADER),
                options.bots());
        assertEquals(Path.of("r", "prototype-001.json"), options.rulesetFile());
        assertEquals(Path.of("o"), options.outputDir());
    }

    @Test
    void aRulesetEndingInJsonIsUsedAsAFilePath() {
        SimulationOptions options = SimulationOptions.parse(new String[] {"--ruleset", "x/my-rules.json"});

        assertEquals(Path.of("x/my-rules.json"), options.rulesetFile());
    }

    @Test
    void oneBotFillsEverySeatAndAListMustMatchTheSeats() {
        SimulationOptions one = SimulationOptions.parse(new String[] {"--ruleset", "p", "--bots", "trader"});
        SimulationOptions two = SimulationOptions.parse(new String[] {"--ruleset", "p", "--bots", "trader,baseline"});

        assertEquals(Collections.nCopies(4, BotProfile.TRADER), one.botsPerSeat(4));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> two.botsPerSeat(4));
        assertTrue(error.getMessage().contains("1 or 4"), error.getMessage());
    }

    @Test
    void badArgumentsAreRejectedWithAMessage() {
        assertRejected("--ruleset is required", "--games", "5");
        assertRejected("every option needs a value", "--ruleset");
        assertRejected("unknown option '--speed'", "--ruleset", "p", "--speed", "3");
        assertRejected("given twice", "--ruleset", "p", "--ruleset", "q");
        assertRejected("--games must be a whole number", "--ruleset", "p", "--games", "many");
        assertRejected("--games must be at least 1", "--ruleset", "p", "--games", "0");
        assertRejected("--seed must be a whole number", "--ruleset", "p", "--seed", "1.5");
        assertRejected("unknown bot 'smart'", "--ruleset", "p", "--bots", "baseline,smart");
        assertRejected("unknown bot ''", "--ruleset", "p", "--bots", "baseline,");
    }

    private static void assertRejected(String expectedMessagePart, String... args) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SimulationOptions.parse(args));
        assertTrue(error.getMessage().contains(expectedMessagePart), error.getMessage());
    }
}
