package citytrade.engine.ruleset;

import java.util.List;

/**
 * Numbers Sheet 14: event schedule, crisis values and the event deck.
 *
 * @param eventRounds    rounds that draw one event card each, in order
 * @param crisisMinLevel lowest city level that must pay crises
 * @param crisisPrestige Prestige for paying a crisis in full
 * @param deck           all event cards (one per event round)
 */
public record EventRules(List<Integer> eventRounds, int crisisMinLevel, int crisisPrestige, List<EventCard> deck) {

    public EventRules {
        eventRounds = List.copyOf(eventRounds);
        deck = List.copyOf(deck);
    }
}
