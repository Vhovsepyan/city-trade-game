package citytrade.server.game;

import citytrade.engine.command.GameResult;
import java.util.Objects;

/** What happened to one command submitted to a {@link GameRoom}. */
public sealed interface CommandOutcome {

    /** The command reached the engine and got this result (accepted or rejected). */
    record Applied(GameResult result) implements CommandOutcome {
        public Applied {
            Objects.requireNonNull(result);
        }
    }

    /** The command never reached the engine: it was an internal command from a client (StartRound/ResolveRound). */
    record Refused(String reason) implements CommandOutcome {
        public Refused {
            Objects.requireNonNull(reason);
        }
    }
}
