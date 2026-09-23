package citytrade.engine.ruleset;

import java.util.List;

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
}
