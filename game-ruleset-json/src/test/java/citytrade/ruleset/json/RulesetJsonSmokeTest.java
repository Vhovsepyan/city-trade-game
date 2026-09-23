package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RulesetJsonSmokeTest {

    @Test
    void testsRunOnJava25Toolchain() {
        assertEquals(25, Runtime.version().feature());
    }
}
