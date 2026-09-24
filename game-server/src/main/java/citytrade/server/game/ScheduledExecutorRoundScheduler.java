package citytrade.server.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Production {@link RoundScheduler}: fires each task, once, at real wall-clock {@code at} (measured via {@code clock}). */
public final class ScheduledExecutorRoundScheduler implements RoundScheduler {

    private final Clock clock;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(ScheduledExecutorRoundScheduler::daemonThread);

    public ScheduledExecutorRoundScheduler(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Handle schedule(Instant at, Runnable task) {
        long delayMillis = Math.max(0, Duration.between(clock.instant(), at).toMillis());
        ScheduledFuture<?> future = executor.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    public void shutdown() {
        executor.shutdown();
    }

    private static Thread daemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "round-scheduler");
        thread.setDaemon(true);
        return thread;
    }
}
