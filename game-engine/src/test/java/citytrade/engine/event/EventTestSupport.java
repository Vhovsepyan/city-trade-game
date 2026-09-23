package citytrade.engine.event;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.readyForRoundOne;

import citytrade.engine.TestRulesets;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.EventWarning;
import citytrade.engine.state.GameState;
import java.util.Optional;

/** Helpers to play a round with a chosen event card instead of the shuffled one. */
final class EventTestSupport {

    static final long SEED = 5;

    private EventTestSupport() {
    }

    /** A prototype-001 event card (Numbers Sheet 14) by id. */
    static EventCard card(String id) {
        return TestRulesets.prototypeEvents().stream().filter(card -> card.id().equals(id)).findFirst().orElseThrow();
    }

    /** The window of Round 1 (no event in Round 1). */
    static GameState roundOneWindow(Ruleset ruleset) {
        return accept(readyForRoundOne(SEED, ruleset), new StartRound(), ruleset).state();
    }

    /** In a window: {@code card} is warned as next round's event (replaces the drawn warning). */
    static GameState warn(GameState window, EventCard card) {
        return window.withEvents(window.eventDeck(), Optional.of(new EventWarning(window.round() + 1, card)),
                window.activeEvent());
    }

    /** From a window: warn {@code card}, resolve this round and start the next one, in which {@code card} is active. */
    static GameResult.Accepted nextRoundWith(GameState window, EventCard card, Ruleset ruleset) {
        GameState resolved = accept(warn(window, card), new ResolveRound(), ruleset).state();
        return accept(resolved, new StartRound(), ruleset);
    }
}
