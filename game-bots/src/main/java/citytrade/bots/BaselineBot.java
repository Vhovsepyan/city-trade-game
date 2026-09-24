package citytrade.bots;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.economy.Storage;
import citytrade.engine.event.EventEffects;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.BuildingEffect;
import citytrade.engine.ruleset.BuildingRules;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.Comparator;
import java.util.List;
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
        List<String> kept = state.player(seat).dealtObjectives().stream()
                .limit(ruleset.objectives().keptPerPlayer())
                .map(ObjectiveCard::id)
                .toList();
        return new ChooseObjectives(seat, kept);
    }

    @Override
    public Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset) {
        Optional<GameCommand> development = nextDevelopment(state, seat, ruleset);
        if (development.isPresent()) {
            return development;
        }
        return sellExcess(state, seat, ruleset);
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
            return marketPurchaseFor(state, seat, cost, ruleset).map(GameCommand.class::cast);
        }
        for (BuildingRules building : ruleset.buildings()) {
            ResourceBundle cost = EventEffects.buildingCost(state.activeEvent(), building.cost());
            if (!player.hasBuilt(building.id()) && player.level() >= building.requiredLevel() && free.covers(cost)) {
                return Optional.of(new BuildBuilding(seat, building.id(), choiceFor(player, building)));
            }
        }
        return Optional.empty();
    }

    /**
     * One market purchase towards {@code cost}: the first missing resource, in full. Empty if nothing is missing,
     * or if the per-round buy limit or the free Money does not allow buying everything missing plus the Money part.
     */
    private static Optional<BuyFromMarket> marketPurchaseFor(GameState state, int seat, ResourceBundle cost,
            Ruleset ruleset) {
        PlayerState player = state.player(seat);
        ResourceBundle free = state.spendableHoldings(seat);
        int limit = ruleset.market().maxBuyPerResourcePerRound();
        long moneyNeeded = cost.money();
        Optional<BuyFromMarket> first = Optional.empty();
        for (Resource resource : Resource.values()) {
            int missing = Math.max(0, cost.amountOf(resource) - free.amountOf(resource));
            if (missing == 0) {
                continue;
            }
            if (missing > limit - player.marketThisRound().bought().amountOf(resource)) {
                return Optional.empty();
            }
            moneyNeeded += (long) missing * Market.buyCost(state, resource, ruleset);
            if (first.isEmpty()) {
                first = Optional.of(new BuyFromMarket(seat, resource, missing));
            }
        }
        return moneyNeeded <= free.money() ? first : Optional.empty();
    }

    /** D4: a chosen-production building produces the non-specialty resource the city holds least of. */
    private static Optional<Resource> choiceFor(PlayerState player, BuildingRules building) {
        if (!(building.effect() instanceof BuildingEffect.ChosenNonSpecialtyProduction)) {
            return Optional.empty();
        }
        return List.of(Resource.values()).stream()
                .filter(resource -> resource != player.city().specialty())
                .min(Comparator.comparingInt(player.holdings()::amountOf));
    }

    /** Sells the units of the first resource above the storage limit; step 4.5 would discard them anyway. */
    private static Optional<GameCommand> sellExcess(GameState state, int seat, Ruleset ruleset) {
        PlayerState player = state.player(seat);
        int limit = Storage.limitOf(player, state.round(), ruleset);
        for (Resource resource : Resource.values()) {
            int excess = player.holdings().amountOf(resource) - limit;
            if (excess > 0) {
                return Optional.of(new SellToMarket(seat, resource, excess));
            }
        }
        return Optional.empty();
    }
}
