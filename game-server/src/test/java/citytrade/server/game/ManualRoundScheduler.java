package citytrade.server.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic {@link RoundScheduler} test double: records what got scheduled instead of firing it in real
 * time, so tests drive the round flow (Architecture 6.4) by calling {@link #fireLatest()} instead of sleeping.
 */
final class ManualRoundScheduler implements RoundScheduler {

    private final List<Runnable> pending = new ArrayList<>();

    @Override
    public synchronized Handle schedule(Instant at, Runnable task) {
        pending.add(task);
        return () -> {
            synchronized (ManualRoundScheduler.this) {
                pending.remove(task);
            }
        };
    }

    /**
     * Fires (and forgets) the most recently scheduled task. The driver has at most one active timer at a time
     * (the objective-choice deadline, then one window deadline per round), so "the current timer" is always
     * the last one scheduled that has not fired yet.
     */
    synchronized void fireLatest() {
        if (pending.isEmpty()) {
            throw new IllegalStateException("no task is currently scheduled");
        }
        pending.remove(pending.size() - 1).run();
    }
}
