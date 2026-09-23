package citytrade.engine.command;

import citytrade.engine.Resource;
import java.util.List;

/**
 * D1: the order in which upkeep is paid, from the next upkeep step on, until changed.
 * Without this command the city pays with the non-specialty resources it has most of.
 *
 * @param order each non-specialty resource of the player's city exactly once, most preferred first
 */
public record SetUpkeepPriority(int seat, List<Resource> order) implements GameCommand {

    public SetUpkeepPriority {
        order = List.copyOf(order);
    }
}
