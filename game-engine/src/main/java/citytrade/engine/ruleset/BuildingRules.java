package citytrade.engine.ruleset;

import citytrade.engine.ResourceBundle;

/** Numbers Sheet 10: one building. Each player may build each building once. */
public record BuildingRules(String id, int requiredLevel, ResourceBundle cost, int prestige, BuildingEffect effect) {
}
