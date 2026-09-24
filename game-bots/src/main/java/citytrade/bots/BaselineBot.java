package citytrade.bots;

import citytrade.engine.ResourceBundle;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.event.EventEffects;
import citytrade.engine.ruleset.BuildingRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.Optional;

/**
 * Architecture 8.1 A: no negotiation, the market as fallback, fixed build priorities.
 * <ol>
 *   <li>Next city level first. Missing resources are bought from the market, but only when the whole
 *       upgrade can be completed this round (no half-finished purchases).</li>
 *   <li>Buildings only when no upgrade is possible this round (already upgraded, or highest level):
 *       in ruleset order, each one when it is affordable without the market.</li>
 *   <li>Resources above the storage limit are sold instead of being discarded in step 4.5.</li>
 * </ol>
 * It never trades, signs contracts, bids, contributes to projects or uses event options; crises use the
 * default PAY policy (D2) and upkeep the default order (D1).
 */
public final class BaselineBot implements Bot {

    @Override
    public ChooseObjectives chooseObjectives(GameState state, int seat, Ruleset ruleset) {
        return BotActions.keepFirstDealtObjectives(state, seat, ruleset);
    }

    @Override
    public Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset) {
        Optional<GameCommand> development = nextDevelopment(state, seat, ruleset);
        if (development.isPresent()) {
            return development;
        }
        return BotActions.sellExcess(state, seat, ruleset);
    }

    private static Optional<GameCommand> nextDevelopment(GameState state, int seat, Ruleset ruleset) {
        PlayerState player = state.player(seat);
        ResourceBundle free = state.spendableHoldings(seat);
        int nextLevel = player.level() + 1;
        boolean canUpgradeThisRound = ruleset.hasLevel(nextLevel) && player.lastUpgradeRound() != state.round();
        if (canUpgradeThisRound) {
            ResourceBundle cost = EventEffects.upgradeCost(state.activeEvent(), ruleset.level(nextLevel).upgradeCost());
            if (free.covers(cost)) {
                return Optional.of(new UpgradeCity(seat));
            }
            return BotActions.marketPurchaseFor(state, seat, cost, free, ruleset).map(GameCommand.class::cast);
        }
        for (BuildingRules building : ruleset.buildings()) {
            ResourceBundle cost = EventEffects.buildingCost(state.activeEvent(), building.cost());
            if (!player.hasBuilt(building.id()) && player.level() >= building.requiredLevel() && free.covers(cost)) {
                return Optional.of(new BuildBuilding(seat, building.id(), BotActions.choiceFor(player, building)));
            }
        }
        return Optional.empty();
    }
}
