package citytrade.engine.ruleset;

import java.util.List;
import java.util.Optional;

/**
 * All balance values of one ruleset version (Numbers Sheet). Immutable.
 * Behavior lives in the engine; this record only holds values.
 */
public record Ruleset(
        String version,
        int roundCount,
        int playerCount,
        StartingRules starting,
        List<LevelRules> levels,
        StorageRules storage,
        StrainedRules strained,
        List<BuildingRules> buildings,
        MarketRules market,
        ContractRules contracts,
        EventRules events,
        ObjectiveRules objectives,
        ProjectRules projects,
        OpportunityRules opportunities) {

    public Ruleset {
        levels = List.copyOf(levels);
        buildings = List.copyOf(buildings);
    }

    /** The rules of city level {@code level}. */
    public LevelRules level(int level) {
        return levels.stream()
                .filter(rules -> rules.level() == level)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ruleset has no level " + level));
    }

    public boolean hasLevel(int level) {
        return levels.stream().anyMatch(rules -> rules.level() == level);
    }

    /** The building with {@code id}, if this ruleset has one. */
    public Optional<BuildingRules> building(String id) {
        return buildings.stream().filter(rules -> rules.id().equals(id)).findFirst();
    }
}
