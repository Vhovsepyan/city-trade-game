package citytrade.server.config;

import citytrade.engine.ruleset.Ruleset;
import citytrade.ruleset.json.RulesetLoader;
import citytrade.server.room.RoomRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Constructs validated configuration and the in-memory room registry. */
@Configuration
@EnableConfigurationProperties(GameServerProperties.class)
public class ServerConfiguration {

    @Bean
    public Clock gameClock() {
        return Clock.systemUTC();
    }

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public Ruleset ruleset(GameServerProperties properties) {
        String version = requireText(properties.getRuleset(), "game.ruleset");
        Path file = rulesetFile(properties.getRulesetsDirectory(), version);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Configured ruleset '" + version + "' was not found at " + file);
        }
        try {
            return RulesetLoader.load(file);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Configured ruleset '" + version + "' is invalid: " + e.getMessage(), e);
        }
    }

    private static Path rulesetFile(String directory, String version) {
        String validDirectory = requireText(directory, "game.rulesets-directory");
        Path file = Path.of(validDirectory, version + ".json");
        // Gradle bootRun may use either the repository or the game-server project as its working directory.
        if (!Files.isRegularFile(file) && !Path.of(validDirectory).isAbsolute()
                && validDirectory.equals("rulesets")) {
            file = Path.of("..", validDirectory, version + ".json");
        }
        return file;
    }

    @Bean
    public RoomRegistry roomRegistry(Ruleset ruleset, GameServerProperties properties, Clock gameClock,
            SecureRandom secureRandom) {
        return RoomRegistry.secure(ruleset, gameClock, secureRandom, properties.getFinishedRoomTtl(),
                properties.getLobbyIdleTtl());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must not be blank");
        }
        return value.trim();
    }
}
