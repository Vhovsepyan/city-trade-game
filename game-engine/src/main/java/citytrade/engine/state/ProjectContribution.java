package citytrade.engine.state;

import citytrade.engine.ResourceBundle;
import java.util.Objects;

/**
 * One accepted contribution to a public project. Contributions are public (D6).
 *
 * @param seat  the contributing player
 * @param round the round of the contribution (hidden objectives count contribution rounds)
 * @param given the resources and Money given
 */
public record ProjectContribution(int seat, int round, ResourceBundle given) {

    public ProjectContribution {
        Objects.requireNonNull(given);
    }
}
