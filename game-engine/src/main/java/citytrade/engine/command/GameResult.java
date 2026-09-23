package citytrade.engine.command;

import citytrade.engine.state.GameState;
import java.util.List;
import java.util.Objects;

/** Outcome of one command. Rejected means: state unchanged, no events. */
public sealed interface GameResult {

    record Accepted(GameState state, List<DomainEvent> events) implements GameResult {

        public Accepted {
            Objects.requireNonNull(state);
            events = List.copyOf(events);
        }
    }

    record Rejected(RejectionCode code, String detail) implements GameResult {

        public Rejected {
            Objects.requireNonNull(code);
            Objects.requireNonNull(detail);
        }
    }
}
