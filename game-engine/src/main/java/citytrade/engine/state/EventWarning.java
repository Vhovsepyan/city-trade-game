package citytrade.engine.state;

import citytrade.engine.ruleset.EventCard;

/** A revealed event card that becomes active in {@code round} (Concept 21: shown one round ahead). */
public record EventWarning(int round, EventCard card) {
}
