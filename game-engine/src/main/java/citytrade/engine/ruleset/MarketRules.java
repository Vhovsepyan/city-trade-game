package citytrade.engine.ruleset;

import java.util.List;

/**
 * Numbers Sheet 11-12: one price table shared by F, E, M, T.
 *
 * @param steps                     price steps from cheapest to most expensive
 * @param startStep                 name of the step every resource starts at
 * @param priceMoveThreshold        net units (bought minus sold) that move a price one step up;
 *                                  the same amount negative moves it one step down
 * @param maxBuyPerResourcePerRound units of each resource one player may buy per round
 */
public record MarketRules(
        List<PriceStep> steps,
        String startStep,
        int priceMoveThreshold,
        int maxBuyPerResourcePerRound) {

    public MarketRules {
        steps = List.copyOf(steps);
    }

    /**
     * @param buyCost   Money the player pays to buy 1 unit ("Market SELLS")
     * @param sellValue Money the player gets for selling 1 unit ("Market BUYS")
     */
    public record PriceStep(String name, int buyCost, int sellValue) {
    }
}
