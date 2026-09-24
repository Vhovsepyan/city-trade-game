package citytrade.sim;

import citytrade.engine.ruleset.Ruleset;
import citytrade.ruleset.json.RulesetLoader;
import java.nio.file.Path;

final class TestRulesets {

    private TestRulesets() {
    }

    /** The repository's rulesets directory (from the {@code rulesets.dir} property in build.gradle). */
    static Path dir() {
        String dir = System.getProperty("rulesets.dir");
        if (dir == null) {
            throw new IllegalStateException("System property rulesets.dir is not set (see game-sim/build.gradle)");
        }
        return Path.of(dir);
    }

    /** The real prototype-001 file. */
    static Ruleset prototype() {
        return RulesetLoader.load(dir().resolve("prototype-001.json"));
    }
}
