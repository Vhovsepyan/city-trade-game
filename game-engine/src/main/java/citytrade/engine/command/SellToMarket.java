package citytrade.engine.command;

import citytrade.engine.Resource;
import java.util.Objects;

/**
 * Sell {@code quantity} units of {@code resource} to the global market at the current price step
 * (Numbers Sheet 11-12). Only during the trade window. There is no selling limit.
 */
public record SellToMarket(int seat, Resource resource, int quantity) implements GameCommand {

    public SellToMarket {
        Objects.requireNonNull(resource);
    }
}
