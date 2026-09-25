package citytrade.server.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What a match was created with: enough to replay it (Architecture 7.2)
 * {@code seed + rulesetVersion + commands in commandSequence order -> GameState}.
 */
public record MatchRecord(UUID matchId, long seed, String rulesetVersion, List<MatchPlayer> players,
        Instant startedAt) {

    public MatchRecord {
        Objects.requireNonNull(matchId);
        Objects.requireNonNull(rulesetVersion);
        players = List.copyOf(players);
        Objects.requireNonNull(startedAt);
    }
}
