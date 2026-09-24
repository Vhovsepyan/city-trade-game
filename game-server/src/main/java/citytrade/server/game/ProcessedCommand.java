package citytrade.server.game;

import citytrade.engine.command.GameCommand;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * One command processed by a {@link GameRoom}'s serial queue, in {@code sequence} order.
 * {@code T24} persists exactly this shape.
 *
 * @param sequence              the server-wide processing order for this room (1, 2, 3, ...)
 * @param origin                who caused the command
 * @param actorSeat             the seat for PLAYER and BOT origins, empty for SYSTEM
 * @param command                the engine command that was submitted
 * @param outcome                whether it reached the engine, and with what result
 * @param resultingStateVersion  the room's stateVersion right after this command was processed
 */
public record ProcessedCommand(
        long sequence,
        CommandOrigin origin,
        OptionalInt actorSeat,
        GameCommand command,
        CommandOutcome outcome,
        long resultingStateVersion) {

    public ProcessedCommand {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(actorSeat);
        Objects.requireNonNull(command);
        Objects.requireNonNull(outcome);
    }
}
