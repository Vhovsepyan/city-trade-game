package citytrade.engine.ruleset;

import citytrade.engine.ResourceBundle;

/**
 * Numbers Sheet 3-4 (production), 6-7 (level cost), 8 (upkeep) and 18 (level Prestige) for one city level.
 *
 * @param upgradeCost    cost to reach this level from the level below (all zero for Level 1)
 * @param upkeepResources number of non-specialty resources paid as upkeep each round
 * @param reachPrestige  Prestige for reaching this level (0 for Level 1)
 */
public record LevelRules(
        int level,
        int specialtyProduction,
        int otherResourceProduction,
        int moneyProduction,
        ResourceBundle upgradeCost,
        int upkeepResources,
        int reachPrestige) {
}
