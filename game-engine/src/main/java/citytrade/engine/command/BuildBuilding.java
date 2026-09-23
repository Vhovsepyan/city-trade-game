package citytrade.engine.command;

import citytrade.engine.Resource;
import java.util.Objects;
import java.util.Optional;

/**
 * Build one building (Numbers Sheet 10). Only during the trade window; each building once per player.
 *
 * @param buildingId     the building id from the ruleset
 * @param chosenResource D4: required for a building with a chosen production resource (Workshop),
 *                       must be empty for all other buildings
 */
public record BuildBuilding(int seat, String buildingId, Optional<Resource> chosenResource) implements GameCommand {

    public BuildBuilding {
        Objects.requireNonNull(buildingId);
        Objects.requireNonNull(chosenResource);
    }

    /** A building without a resource choice. */
    public BuildBuilding(int seat, String buildingId) {
        this(seat, buildingId, Optional.empty());
    }
}
