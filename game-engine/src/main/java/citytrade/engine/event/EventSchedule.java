package citytrade.engine.event;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.EventWarning;
import citytrade.engine.state.GameState;
import java.util.List;
import java.util.Optional;

/**
 * When event cards are warned, become active and end (Numbers Sheet 14, Concept 21). Only the rounds in
 * the ruleset's event schedule have an event; the card is always warned one round before it is active.
 */
public final class EventSchedule {

    private EventSchedule() {
    }

    /** Step 1.1: the warned card for this round becomes the active event (before production). */
    public static GameState activate(GameState state, List<DomainEvent> events) {
        Optional<EventWarning> warning = state.eventWarning();
        if (warning.isEmpty() || warning.get().round() != state.round()) {
            return state;
        }
        EventCard card = warning.get().card();
        events.add(new DomainEvent.EventActivated(state.round(), card.id()));
        return state.withEvents(state.eventDeck(), Optional.empty(), Optional.of(card));
    }

    /**
     * Step 2.2: if next round is an event round, its card is revealed now. The first warning is revealed
     * at setup, so a warning that is still waiting is kept.
     */
    public static GameState warnNextEvent(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        int nextRound = state.round() + 1;
        if (state.eventWarning().isPresent() || state.eventDeck().isEmpty()
                || !ruleset.events().eventRounds().contains(nextRound)) {
            return state;
        }
        EventCard card = state.eventDeck().getFirst();
        events.add(new DomainEvent.EventWarned(nextRound, card.id()));
        return state.withEvents(state.eventDeck().subList(1, state.eventDeck().size()),
                Optional.of(new EventWarning(nextRound, card)), state.activeEvent());
    }

    /** Step 4.7: the event's temporary effects end with the round. */
    public static GameState endTemporaryEffects(GameState state, List<DomainEvent> events) {
        if (state.activeEvent().isEmpty()) {
            return state;
        }
        events.add(new DomainEvent.EventEnded(state.activeEvent().get().id()));
        return state.withEvents(state.eventDeck(), state.eventWarning(), Optional.empty());
    }
}
