package citytrade.engine.command;

import citytrade.engine.Resource;
import java.util.Objects;

/**
 * Buy {@code quantity} units of {@code resource} from the global market at the current price step
 * (Numbers Sheet 11-12). Only during the trade window.
 */
public record BuyFromMarket(int seat, Resource resource, int quantity) implements GameCommand {

    public BuyFromMarket {
        Objects.requireNonNull(resource);
    }
}
