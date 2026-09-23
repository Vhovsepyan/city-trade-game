package citytrade.ruleset.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

final class TestRulesets {

    static final Path PROTOTYPE_001 = rulesetsDir().resolve("prototype-001.json");

    private TestRulesets() {
    }

    static Path rulesetsDir() {
        String dir = System.getProperty("rulesets.dir");
        if (dir == null) {
            throw new IllegalStateException("System property rulesets.dir is not set (see game-ruleset-json/build.gradle)");
        }
        return Path.of(dir);
    }

    /** A fresh, editable JSON tree of prototype-001, used to build broken rulesets. */
    static ObjectNode prototypeJson() {
        try {
            return (ObjectNode) new ObjectMapper().readTree(PROTOTYPE_001.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
