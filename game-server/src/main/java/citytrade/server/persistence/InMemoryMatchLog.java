package citytrade.server.persistence;

import citytrade.engine.state.FinalResult;
import citytrade.server.game.ProcessedCommand;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default {@link MatchLog}: keeps the server usable with no database (Architecture 7.1). Never persists
 * across a restart; {@code postgres}-profile durability is {@link PostgresMatchLog}'s job.
 */
public final class InMemoryMatchLog implements MatchLog {

    private final Map<UUID, MatchRecord> matches = new ConcurrentHashMap<>();
    private final Map<UUID, List<ProcessedCommand>> commands = new ConcurrentHashMap<>();
    private final Map<UUID, FinalResult> results = new ConcurrentHashMap<>();

    @Override
    public void matchStarted(UUID matchId, long seed, String rulesetVersion, List<MatchPlayer> players,
            Instant startedAt) {
        matches.put(matchId, new MatchRecord(matchId, seed, rulesetVersion, players, startedAt));
        commands.put(matchId, new CopyOnWriteArrayList<>());
    }

    @Override
    public void commandProcessed(UUID matchId, ProcessedCommand command) {
        Objects.requireNonNull(command);
        commands.computeIfAbsent(matchId, id -> new CopyOnWriteArrayList<>()).add(command);
    }

    @Override
    public void matchFinished(UUID matchId, FinalResult result) {
        results.put(matchId, Objects.requireNonNull(result));
    }

    @Override
    public MatchRecord loadMatch(UUID matchId) {
        MatchRecord record = matches.get(matchId);
        if (record == null) {
            throw new IllegalArgumentException("no match logged for " + matchId);
        }
        return record;
    }

    @Override
    public List<LoggedCommand> loadCommands(UUID matchId) {
        return commands.getOrDefault(matchId, List.of()).stream()
                .sorted(Comparator.comparingLong(ProcessedCommand::sequence))
                .map(InMemoryMatchLog::toLogged)
                .toList();
    }

    /** Result, if the match has finished; used for tests and diagnostics, no replay dependency. */
    public java.util.Optional<FinalResult> resultOf(UUID matchId) {
        return java.util.Optional.ofNullable(results.get(matchId));
    }

    private static LoggedCommand toLogged(ProcessedCommand command) {
        return new LoggedCommand(command.sequence(), command.origin(), command.actorSeat(), command.commandId(),
                command.command(), MatchLogs.outcomeOf(command), MatchLogs.rejectionCodeOf(command),
                command.resultingStateVersion());
    }
}
