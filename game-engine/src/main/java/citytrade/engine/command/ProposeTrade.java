package citytrade.engine.command;

import citytrade.engine.ResourceBundle;
import java.util.Objects;

/**
 * Offer an instant trade to one other player (Concept 14-15, D12): {@code seat} gives {@code offered},
 * {@code recipientSeat} gives {@code requested}. Only during the trade window.
 */
public record ProposeTrade(int seat, int recipientSeat, ResourceBundle offered, ResourceBundle requested)
        implements GameCommand {

    public ProposeTrade {
        Objects.requireNonNull(offered);
        Objects.requireNonNull(requested);
    }
}
