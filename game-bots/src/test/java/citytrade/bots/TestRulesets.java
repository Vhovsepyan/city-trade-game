package citytrade.bots;

import citytrade.engine.ruleset.Ruleset;
import citytrade.ruleset.json.RulesetLoader;
import java.nio.file.Path;

final class TestRulesets {

    private TestRulesets() {
    }

    /** The real prototype-001 file (path from the {@code rulesets.dir} property in build.gradle). */
    static Ruleset prototype() {
        String dir = System.getProperty("rulesets.dir");
        if (dir == null) {
            throw new IllegalStateException("System property rulesets.dir is not set (see game-bots/build.gradle)");
        }
        return RulesetLoader.load(Path.of(dir).resolve("prototype-001.json"));
    }
}
