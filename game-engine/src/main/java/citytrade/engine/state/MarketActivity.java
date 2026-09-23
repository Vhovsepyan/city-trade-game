package citytrade.engine.state;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;

/**
 * One player's market trades in the current round (Numbers Sheet 11-12). Used for the per-round buy
 * limit and for the price movement at step 4.6, then cleared. Money is always 0 in both bundles.
 *
 * @param bought units bought FROM the market this round
 * @param sold   units sold TO the market this round
 */
public record MarketActivity(ResourceBundle bought, ResourceBundle sold) {

    public static final MarketActivity NONE = new MarketActivity(ResourceBundle.EMPTY, ResourceBundle.EMPTY);

    public MarketActivity {
        if (bought.money() != 0 || sold.money() != 0) {
            throw new IllegalArgumentException("market activity counts resources only");
        }
    }

    public MarketActivity withBought(Resource resource, int quantity) {
        return new MarketActivity(bought.with(resource, bought.amountOf(resource) + quantity), sold);
    }

    public MarketActivity withSold(Resource resource, int quantity) {
        return new MarketActivity(bought, sold.with(resource, sold.amountOf(resource) + quantity));
    }
}
