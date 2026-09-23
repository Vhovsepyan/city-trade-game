package citytrade.engine.command;

import citytrade.engine.ResourceBundle;
import java.util.Objects;

/**
 * Propose a formal contract (D10): {@code creditorSeat} gives {@code givenNow} when it is signed, {@code debtorSeat}
 * owes {@code owed} in {@code dueRound}. {@code seat} must be one of the two parties; the other one signs.
 * Only during the trade window.
 */
public record ProposeContract(int seat, int creditorSeat, int debtorSeat, ResourceBundle givenNow,
        ResourceBundle owed, int dueRound) implements GameCommand {

    public ProposeContract {
        Objects.requireNonNull(givenNow);
        Objects.requireNonNull(owed);
    }
}
