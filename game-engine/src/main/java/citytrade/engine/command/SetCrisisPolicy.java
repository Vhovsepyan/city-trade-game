package citytrade.engine.command;

import citytrade.engine.state.CrisisPolicy;
import java.util.Objects;

/**
 * D2: how the city answers the warned crisis. Only in the window of the warning round (the round before
 * the crisis) and only when the warned event is a crisis. Without it the policy is PAY.
 */
public record SetCrisisPolicy(int seat, CrisisPolicy policy) implements GameCommand {

    public SetCrisisPolicy {
        Objects.requireNonNull(policy);
    }
}
