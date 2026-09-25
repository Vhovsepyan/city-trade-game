package citytrade.server.persistence;

import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.server.game.CommandOutcome;
import citytrade.server.game.ProcessedCommand;
import java.util.Optional;

/** Shared mapping from a {@link ProcessedCommand}'s outcome to what a {@link MatchLog} stores. */
final class MatchLogs {

    private MatchLogs() {
    }

    static LoggedOutcome outcomeOf(ProcessedCommand command) {
        return switch (command.outcome()) {
            case CommandOutcome.Applied applied -> switch (applied.result()) {
                case GameResult.Accepted ignored -> LoggedOutcome.ACCEPTED;
                case GameResult.Rejected ignored -> LoggedOutcome.REJECTED;
            };
            case CommandOutcome.Refused ignored -> LoggedOutcome.REFUSED;
        };
    }

    static Optional<RejectionCode> rejectionCodeOf(ProcessedCommand command) {
        if (command.outcome() instanceof CommandOutcome.Applied applied
                && applied.result() instanceof GameResult.Rejected rejected) {
            return Optional.of(rejected.code());
        }
        return Optional.empty();
    }
}
