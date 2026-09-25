package citytrade.server.persistence;

import citytrade.engine.command.GameCommand;
import citytrade.engine.command.RejectionCode;
import citytrade.server.game.CommandOrigin;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * One command loaded back from a {@link MatchLog} (Architecture 7.3 {@code match_commands}), already decoded
 * into the real {@link GameCommand} it was stored as.
 *
 * @param commandSequence        the server-wide processing order for this match (never reused, may have gaps
 *                                only if commands were deleted, which never happens)
 * @param origin                 who caused it
 * @param actorSeat              the seat for PLAYER and BOT origins, empty for SYSTEM
 * @param commandId              the client-supplied idempotency key, only ever present for PLAYER
 * @param command                the decoded command, with its own seat field intact
 * @param outcome                whether {@link ReplayService} must apply it
 * @param rejectionCode          present only when {@code outcome} is {@code REJECTED}
 * @param resultingStateVersion  the room's stateVersion right after this command was processed
 */
public record LoggedCommand(long commandSequence, CommandOrigin origin, OptionalInt actorSeat,
        Optional<String> commandId, GameCommand command, LoggedOutcome outcome,
        Optional<RejectionCode> rejectionCode, long resultingStateVersion) {

    public LoggedCommand {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(actorSeat);
        Objects.requireNonNull(commandId);
        Objects.requireNonNull(command);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(rejectionCode);
    }
}
