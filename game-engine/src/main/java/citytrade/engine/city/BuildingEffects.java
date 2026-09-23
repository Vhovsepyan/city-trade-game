package citytrade.engine.city;

import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.BuildingEffect;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.BuiltBuilding;
import citytrade.engine.state.PlayerState;

/**
 * What a player's buildings add in a round (Numbers Sheet 10). Only buildings built before
 * {@code round} count: effects start the round after building.
 */
public final class BuildingEffects {

    private BuildingEffects() {
    }

    /** Extra resources and Money produced in step 1.3 of {@code round}. */
    public static ResourceBundle productionBonus(PlayerState player, int round, Ruleset ruleset) {
        ResourceBundle bonus = ResourceBundle.EMPTY;
        for (BuiltBuilding building : player.activeBuildings(round)) {
            switch (effectOf(building, ruleset)) {
                case BuildingEffect.ChosenNonSpecialtyProduction e -> {
                    var resource = building.chosenResource().orElseThrow();
                    bonus = bonus.with(resource, bonus.amountOf(resource) + e.amount());
                }
                case BuildingEffect.SpecialtyProduction e -> {
                    var specialty = player.city().specialty();
                    bonus = bonus.with(specialty, bonus.amountOf(specialty) + e.amount());
                }
                case BuildingEffect.ProductionBonus e -> bonus = bonus.plus(e.production());
                case BuildingEffect.StorageBonus _, BuildingEffect.UpkeepReduction _, BuildingEffect.NoEffect _ -> {
                }
            }
        }
        return bonus;
    }

    /** Extra storage for each of F, E, M, T in step 4.5 of {@code round}. */
    public static int storageBonus(PlayerState player, int round, Ruleset ruleset) {
        int bonus = 0;
        for (BuiltBuilding building : player.activeBuildings(round)) {
            if (effectOf(building, ruleset) instanceof BuildingEffect.StorageBonus e) {
                bonus += e.amount();
            }
        }
        return bonus;
    }

    /** Fewer upkeep resources in step 1.4 of {@code round}; a reduction counts only at its city level. */
    public static int upkeepReduction(PlayerState player, int round, Ruleset ruleset) {
        int reduction = 0;
        for (BuiltBuilding building : player.activeBuildings(round)) {
            if (effectOf(building, ruleset) instanceof BuildingEffect.UpkeepReduction e
                    && e.cityLevel() == player.level()) {
                reduction += e.amount();
            }
        }
        return reduction;
    }

    private static BuildingEffect effectOf(BuiltBuilding building, Ruleset ruleset) {
        return ruleset.building(building.id())
                .orElseThrow(() -> new IllegalStateException("ruleset has no building " + building.id()))
                .effect();
    }
}
