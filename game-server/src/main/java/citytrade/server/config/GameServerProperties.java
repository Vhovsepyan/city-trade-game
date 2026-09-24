package citytrade.server.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration shared by the room service and later game-flow services. */
@ConfigurationProperties(prefix = "game")
public class GameServerProperties {

    private String ruleset = "prototype-002";
    private Duration windowDuration = Duration.ofSeconds(120);
    private Duration objectiveChoiceTimeout = Duration.ofSeconds(60);
    private Duration finishedRoomTtl = Duration.ofMinutes(30);
    private Duration lobbyIdleTtl = Duration.ofHours(2);
    private String rulesetsDirectory = "rulesets";

    public String getRuleset() {
        return ruleset;
    }

    public void setRuleset(String ruleset) {
        this.ruleset = ruleset;
    }

    public Duration getWindowDuration() {
        return windowDuration;
    }

    public void setWindowDuration(Duration windowDuration) {
        this.windowDuration = windowDuration;
    }

    public Duration getObjectiveChoiceTimeout() {
        return objectiveChoiceTimeout;
    }

    public void setObjectiveChoiceTimeout(Duration objectiveChoiceTimeout) {
        this.objectiveChoiceTimeout = objectiveChoiceTimeout;
    }

    public Duration getFinishedRoomTtl() {
        return finishedRoomTtl;
    }

    public void setFinishedRoomTtl(Duration finishedRoomTtl) {
        this.finishedRoomTtl = finishedRoomTtl;
    }

    public Duration getLobbyIdleTtl() {
        return lobbyIdleTtl;
    }

    public void setLobbyIdleTtl(Duration lobbyIdleTtl) {
        this.lobbyIdleTtl = lobbyIdleTtl;
    }

    public String getRulesetsDirectory() {
        return rulesetsDirectory;
    }

    public void setRulesetsDirectory(String rulesetsDirectory) {
        this.rulesetsDirectory = rulesetsDirectory;
    }
}
