package citytrade.server.persistence;

import citytrade.engine.state.FinalResult;
import citytrade.server.game.ProcessedCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Durable command log for one match, for later replay (Architecture 7). {@link InMemoryMatchLog} is the
 * default, so the server runs with no database (Architecture 7.1, T19 "runs in memory by default");
 * {@link PostgresMatchLog}, active only under the {@code postgres} Spring profile, persists the same data in
 * PostgreSQL (Architecture 7.3).
 */
public interface MatchLog {

    /** One match begins; {@code players} is every seat (human or bot) it was created with. */
    void matchStarted(UUID matchId, long seed, String rulesetVersion, List<MatchPlayer> players, Instant startedAt);

    /**
     * Written in {@code commandSequence} order (Architecture 7.2), including SYSTEM commands
     * (StartRound/ResolveRound, objective-timeout choices) and rejected/refused commands - stored for
     * debugging, but skipped by {@link ReplayService}.
     */
    void commandProcessed(UUID matchId, ProcessedCommand command);

    /** Round 14 finished (step 4.8); the final scores and winners for the match. */
    void matchFinished(UUID matchId, FinalResult result);

    /** What {@code matchId} was created with (seed, rulesetVersion, players) - {@link ReplayService} needs this. */
    MatchRecord loadMatch(UUID matchId);

    /** Every stored command for {@code matchId}, ordered by {@code commandSequence} regardless of storage order. */
    List<LoggedCommand> loadCommands(UUID matchId);
}
