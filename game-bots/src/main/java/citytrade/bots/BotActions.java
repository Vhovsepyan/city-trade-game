package citytrade.bots;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.economy.Storage;
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

/** Decisions shared by the bot profiles, so each rule of thumb is written once. */
final class BotActions {

    private BotActions() {
    }

    /** D5: keeps the first dealt objectives. */
    static ChooseObjectives keepFirstDealtObjectives(GameState state, int seat, Ruleset ruleset) {
        List<String> kept = state.player(seat).dealtObjectives().stream()
                .limit(ruleset.objectives().keptPerPlayer())
                .map(ObjectiveCard::id)
                .toList();
        return new ChooseObjectives(seat, kept);
    }

    /**
     * One market purchase towards {@code cost}, given what the bot may use ({@code available}): the first missing
     * resource, in full. Empty if nothing is missing, or if the per-round buy limit or the Money in
     * {@code available} does not allow buying everything missing plus the Money part.
     */
    static Optional<BuyFromMarket> marketPurchaseFor(GameState state, int seat, ResourceBundle cost,
            ResourceBundle available, Ruleset ruleset) {
        PlayerState player = state.player(seat);
        int limit = ruleset.market().maxBuyPerResourcePerRound();
        long moneyNeeded = cost.money();
        Optional<BuyFromMarket> first = Optional.empty();
        for (Resource resource : Resource.values()) {
            int missing = Math.max(0, cost.amountOf(resource) - available.amountOf(resource));
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
        return moneyNeeded <= available.money() ? first : Optional.empty();
    }

    /** D4: a chosen-production building produces the non-specialty resource the city holds least of. */
    static Optional<Resource> choiceFor(PlayerState player, BuildingRules building) {
        if (!(building.effect() instanceof BuildingEffect.ChosenNonSpecialtyProduction)) {
            return Optional.empty();
        }
        return List.of(Resource.values()).stream()
                .filter(resource -> resource != player.city().specialty())
                .min(Comparator.comparingInt(player.holdings()::amountOf));
    }

    /** Sells the units of the first resource above the storage limit; step 4.5 would discard them anyway. */
    static Optional<GameCommand> sellExcess(GameState state, int seat, Ruleset ruleset) {
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
