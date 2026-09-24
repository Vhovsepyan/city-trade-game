package citytrade.server.game;

import java.time.Instant;

/**
 * Schedules a single deadline-triggered action (Architecture 6.4). The server owns time; the engine never
 * checks a wall clock. Production code fires {@code task} at {@code at}; tests use a deterministic double
 * that fires it on demand instead of sleeping for real durations.
 */
public interface RoundScheduler {

    /** Runs {@code task} once, at or after {@code at}. Never blocks the caller. */
    Handle schedule(Instant at, Runnable task);

    /** Lets the caller cancel a scheduled task that has not fired yet; cancelling an already-fired task is a no-op. */
    @FunctionalInterface
    interface Handle {
        void cancel();
    }
}
