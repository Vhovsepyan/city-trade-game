package citytrade.engine.market;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.event.EventEffects;
import citytrade.engine.ruleset.MarketRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.MarketActivity;
import citytrade.engine.state.MarketPrices;
import citytrade.engine.state.PlayerState;
import java.util.List;

/**
 * The global market, Numbers Sheet 11-12 and Concept 18-19. Prices are fixed during the window:
 * trades only count units, and the price step moves once, in step 4.6, from the round's net demand.
 */
public final class Market {

    private Market() {
    }

    /** Money the player pays to buy 1 unit at the current step, with this round's event modifiers. */
    public static int buyCost(GameState state, Resource resource, Ruleset ruleset) {
        return EventEffects.buyCost(state.activeEvent(), currentStep(state, resource, ruleset).buyCost());
    }

    /** Money the player gets for selling 1 unit at the current step, with this round's event modifiers. */
    public static int sellValue(GameState state, Resource resource, Ruleset ruleset) {
        return EventEffects.sellValue(state.activeEvent(), currentStep(state, resource, ruleset).sellValue());
    }

    public static GameResult buy(GameState state, BuyFromMarket command, Ruleset ruleset) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        if (command.quantity() < 1) {
            return new GameResult.Rejected(RejectionCode.INVALID_QUANTITY,
                    "quantity must be at least 1, but was " + command.quantity());
        }
        PlayerState player = state.player(command.seat());
        Resource resource = command.resource();
        int limit = ruleset.market().maxBuyPerResourcePerRound();
        int alreadyBought = player.marketThisRound().bought().amountOf(resource);
        // Checked before the cost, so quantity is bounded and quantity * price cannot overflow.
        if (command.quantity() > limit - alreadyBought) {
            return new GameResult.Rejected(RejectionCode.MARKET_BUY_LIMIT_EXCEEDED, "may buy " + limit + " "
                    + resource + " per round, already bought " + alreadyBought + ", asked for " + command.quantity());
        }
        int totalCost = command.quantity() * buyCost(state, resource, ruleset);
        ResourceBundle holdings = player.holdings();
        if (holdings.money() < totalCost) {
            return new GameResult.Rejected(RejectionCode.INSUFFICIENT_MONEY,
                    "costs " + totalCost + " Money, has " + holdings.money());
        }
        ResourceBundle newHoldings = holdings
                .with(resource, holdings.amountOf(resource) + command.quantity())
                .withMoney(holdings.money() - totalCost);
        PlayerState updated = player.withHoldings(newHoldings)
                .withMarketThisRound(player.marketThisRound().withBought(resource, command.quantity()));
        return new GameResult.Accepted(state.withPlayer(updated),
                List.of(new DomainEvent.MarketBought(command.seat(), resource, command.quantity(), totalCost)));
    }

    public static GameResult sell(GameState state, SellToMarket command, Ruleset ruleset) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        if (command.quantity() < 1) {
            return new GameResult.Rejected(RejectionCode.INVALID_QUANTITY,
                    "quantity must be at least 1, but was " + command.quantity());
        }
        PlayerState player = state.player(command.seat());
        Resource resource = command.resource();
        ResourceBundle holdings = player.holdings();
        if (holdings.amountOf(resource) < command.quantity()) {
            return new GameResult.Rejected(RejectionCode.INSUFFICIENT_RESOURCES,
                    "selling " + command.quantity() + " " + resource + ", has " + holdings.amountOf(resource));
        }
        int totalValue = command.quantity() * sellValue(state, resource, ruleset);
        ResourceBundle newHoldings = holdings
                .with(resource, holdings.amountOf(resource) - command.quantity())
                .withMoney(holdings.money() + totalValue);
        PlayerState updated = player.withHoldings(newHoldings)
                .withMarketThisRound(player.marketThisRound().withSold(resource, command.quantity()));
        return new GameResult.Accepted(state.withPlayer(updated),
                List.of(new DomainEvent.MarketSold(command.seat(), resource, command.quantity(), totalValue)));
    }

    /**
     * Step 4.6: per resource, net = units bought from the market - units sold to it by all players this round.
     * Net at or above the threshold moves the price one step up, at or below minus the threshold one step
     * down, within the first and last step. Then the round's market activity is cleared.
     */
    public static GameState movePrices(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        MarketRules rules = ruleset.market();
        int highestStep = rules.steps().size() - 1;
        MarketPrices prices = state.market();
        for (Resource resource : Resource.values()) {
            int net = 0;
            for (PlayerState player : state.players()) {
                net += player.marketThisRound().bought().amountOf(resource)
                        - player.marketThisRound().sold().amountOf(resource);
            }
            int from = prices.stepIndexOf(resource);
            int to = from;
            if (net >= rules.priceMoveThreshold()) {
                to = Math.min(highestStep, from + 1);
            } else if (net <= -rules.priceMoveThreshold()) {
                to = Math.max(0, from - 1);
            }
            if (to != from) {
                prices = prices.withStepIndex(resource, to);
                events.add(new DomainEvent.MarketPriceMoved(resource, from, to));
            }
        }
        GameState next = state.withMarket(prices);
        for (PlayerState player : state.players()) {
            next = next.withPlayer(next.player(player.seat()).withMarketThisRound(MarketActivity.NONE));
        }
        return next;
    }

    /**
     * The price step used in this round's window: the stored step plus the event's step change, kept
     * within the first and last step (Numbers Sheet 11-12). The stored step itself is not changed.
     */
    private static MarketRules.PriceStep currentStep(GameState state, Resource resource, Ruleset ruleset) {
        List<MarketRules.PriceStep> steps = ruleset.market().steps();
        int index = state.market().stepIndexOf(resource) + EventEffects.marketStepChange(state.activeEvent(), resource);
        return steps.get(Math.clamp(index, 0, steps.size() - 1));
    }
}
