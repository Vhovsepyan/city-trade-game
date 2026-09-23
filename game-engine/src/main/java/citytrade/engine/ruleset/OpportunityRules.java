package citytrade.engine.ruleset;

import citytrade.engine.ResourceBundle;
import java.util.List;

/**
 * Numbers Sheet 17: regional opportunities won by secret Money bids.
 *
 * @param appearanceRounds rounds in which one new card appears, in order
 * @param cards            the opportunity deck
 */
public record OpportunityRules(List<Integer> appearanceRounds, List<OpportunityCard> cards) {

    public OpportunityRules {
        appearanceRounds = List.copyOf(appearanceRounds);
        cards = List.copyOf(cards);
    }

    /** @param productionReward permanent extra production for the winner, from the next round */
    public record OpportunityCard(String id, ResourceBundle productionReward) {
    }
}
