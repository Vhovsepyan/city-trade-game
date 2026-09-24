package citytrade.server.config;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Startup must fail fast on a missing or invalid ruleset instead of a silent fallback. */
class ServerConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ServerConfiguration.class)
            .withPropertyValues("game.rulesets-directory=" + rulesetsDir());

    @Test
    void startsWhenTheConfiguredRulesetExists() {
        runner.withPropertyValues("game.ruleset=prototype-002")
                .run(context -> Assertions.assertThat(context).hasNotFailed());
    }

    @Test
    void failsAtStartupWhenTheConfiguredRulesetFileIsMissing() {
        runner.withPropertyValues("game.ruleset=does-not-exist")
                .run(context -> {
                    Assertions.assertThat(context).hasFailed();
                    Assertions.assertThat(context.getStartupFailure())
                            .hasMessageContaining("does-not-exist");
                });
    }

    @Test
    void failsAtStartupWhenTheRulesetPropertyIsBlank() {
        runner.withPropertyValues("game.ruleset=")
                .run(context -> Assertions.assertThat(context).hasFailed());
    }

    private static String rulesetsDir() {
        return System.getProperty("rulesets.dir", "rulesets");
    }
}
