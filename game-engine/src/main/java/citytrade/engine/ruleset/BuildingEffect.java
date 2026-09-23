package citytrade.engine.ruleset;

import citytrade.engine.ResourceBundle;

/** What a building does from the round after it is built. The kind selects the engine behavior. */
public sealed interface BuildingEffect {

    /** Raises the storage limit of each of F, E, M, T. */
    record StorageBonus(int amount) implements BuildingEffect {
    }

    /** Extra production of one non-specialty resource, chosen when built (D4). */
    record ChosenNonSpecialtyProduction(int amount) implements BuildingEffect {
    }

    /** Extra production of the city's specialty resource. */
    record SpecialtyProduction(int amount) implements BuildingEffect {
    }

    /** Fixed extra production of resources and/or Money. */
    record ProductionBonus(ResourceBundle production) implements BuildingEffect {
    }

    /** Fewer upkeep resources, only while the city is at {@code cityLevel}. */
    record UpkeepReduction(int cityLevel, int amount) implements BuildingEffect {
    }

    /** Prestige-only building. */
    record NoEffect() implements BuildingEffect {
    }
}
