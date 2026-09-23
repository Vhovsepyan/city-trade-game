package citytrade.engine.command;

import citytrade.engine.Resource;
import java.util.Objects;
import java.util.Optional;

/**
 * Use the optional action of this round's event (Public Festival, Research Breakthrough), once per player.
 * Only during the window of the event round.
 *
 * @param chosenResource D4: required for an event that adds production of a chosen non-specialty resource,
 *                       must be empty for all other events
 */
public record UseEventOption(int seat, Optional<Resource> chosenResource) implements GameCommand {

    public UseEventOption {
        Objects.requireNonNull(chosenResource);
    }

    /** An event option without a resource choice. */
    public UseEventOption(int seat) {
        this(seat, Optional.empty());
    }
}
