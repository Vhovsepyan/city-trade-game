package citytrade.engine.command;

import java.util.Objects;

/**
 * Set the secret Money bid of {@code seat} on an open regional opportunity (Numbers Sheet 17). A new bid
 * replaces the old one; {@code amount} 0 means pass and withdraws an earlier bid. The bid reserves the Money
 * until Round Resolution.
 */
public record PlaceBid(int seat, String opportunityId, int amount) implements GameCommand {

    public PlaceBid {
        Objects.requireNonNull(opportunityId);
    }
}
