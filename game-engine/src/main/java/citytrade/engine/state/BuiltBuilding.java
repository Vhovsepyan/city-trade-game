package citytrade.engine.state;

import citytrade.engine.Resource;
import java.util.Objects;
import java.util.Optional;

/**
 * A building a player owns (Numbers Sheet 10).
 *
 * @param id             the building id from the ruleset
 * @param roundBuilt     the round it was built in; its effect starts in the round after
 * @param chosenResource D4: the resource chosen for a Workshop-like building, empty for all others
 */
public record BuiltBuilding(String id, int roundBuilt, Optional<Resource> chosenResource) {

    public BuiltBuilding {
        Objects.requireNonNull(id);
        Objects.requireNonNull(chosenResource);
    }

    /** Effects start the round after the building was built. */
    public boolean isActiveIn(int round) {
        return roundBuilt < round;
    }
}
