package citytrade.engine.ruleset;

import java.util.List;

/**
 * Numbers Sheet 15: hidden objectives (deal {@code dealtPerPlayer}, keep {@code keptPerPlayer}).
 *
 * @param completedPrestige Prestige for each completed objective, revealed at the end
 */
public record ObjectiveRules(int dealtPerPlayer, int keptPerPlayer, int completedPrestige, List<ObjectiveCard> deck) {

    public ObjectiveRules {
        deck = List.copyOf(deck);
    }
}
