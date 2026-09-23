package citytrade.engine.command;

import citytrade.engine.ResourceBundle;
import java.util.Objects;

/**
 * The recipient of an open offer answers with a new offer to the original proposer (Architecture 5.3):
 * {@code seat} now gives {@code offered} and asks for {@code requested}. The old offer becomes REJECTED.
 */
public record CounterTrade(int seat, int offerId, ResourceBundle offered, ResourceBundle requested)
        implements GameCommand {

    public CounterTrade {
        Objects.requireNonNull(offered);
        Objects.requireNonNull(requested);
    }
}
