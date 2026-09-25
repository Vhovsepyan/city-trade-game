package citytrade.server.persistence;

import citytrade.engine.GameEngine;
import citytrade.engine.command.GameResult;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Rebuilds a {@link GameState} from a {@link MatchLog} (Architecture 7.2):
 * {@code seed + rulesetVersion + accepted commands in commandSequence order -> GameState}.
 * Never replays by timestamp or storage order - {@link LoggedCommand#commandSequence()} is sorted here
 * regardless of the order {@code commands} arrives in.
 */
public final class ReplayService {

    private ReplayService() {
    }

    public static GameState replay(MatchRecord match, List<LoggedCommand> commands, Ruleset ruleset) {
        Objects.requireNonNull(match);
        Objects.requireNonNull(commands);
        Objects.requireNonNull(ruleset);
        if (!ruleset.version().equals(match.rulesetVersion())) {
            throw new IllegalArgumentException("match " + match.matchId() + " was created with ruleset "
                    + match.rulesetVersion() + ", not " + ruleset.version());
        }
        GameState state = GameSetup.create(match.seed(), ruleset);
        List<LoggedCommand> ordered = commands.stream()
                .sorted(Comparator.comparingLong(LoggedCommand::commandSequence))
                .toList();
        for (LoggedCommand logged : ordered) {
            if (logged.outcome() != LoggedOutcome.ACCEPTED) {
                // Rejected commands never changed state; refused ones never reached the engine at all.
                continue;
            }
            GameResult result = GameEngine.apply(state, logged.command(), ruleset);
            if (!(result instanceof GameResult.Accepted accepted)) {
                throw new IllegalStateException("command_sequence " + logged.commandSequence()
                        + " was stored as ACCEPTED but did not replay as Accepted: " + logged.command());
            }
            state = accepted.state();
        }
        return state;
    }
}
