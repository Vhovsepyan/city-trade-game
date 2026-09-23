package citytrade.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SimSmokeTest {

    @Test
    void testsRunOnJava25Toolchain() {
        assertEquals(25, Runtime.version().feature());
    }
}
