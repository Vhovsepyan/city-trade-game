package citytrade.server.persistence;

import citytrade.engine.CityType;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.state.FinalResult;
import citytrade.server.game.CommandOrigin;
import citytrade.server.game.ProcessedCommand;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link MatchLog} backed by PostgreSQL (Architecture 7.3), active only under the {@code postgres} Spring
 * profile. Schema is created by the Flyway migration {@code db/migration/V1__init.sql}.
 */
@Component
@Profile("postgres")
public final class PostgresMatchLog implements MatchLog {

    /** {@code match_commands.command_schema_version}: bumped only if the stored payload shape ever changes. */
    static final int COMMAND_SCHEMA_VERSION = 1;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = JsonMapper.builder().build();

    public PostgresMatchLog(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    @Override
    public void matchStarted(UUID matchId, long seed, String rulesetVersion, List<MatchPlayer> players,
            java.time.Instant startedAt) {
        jdbc.update("INSERT INTO matches (id, seed, ruleset_version, status, created_at) VALUES (?, ?, ?, ?, ?)",
                matchId, seed, rulesetVersion, "ACTIVE", Timestamp.from(startedAt));
        for (MatchPlayer player : players) {
            jdbc.update(
                    "INSERT INTO match_players (match_id, seat, nickname, city, is_bot) VALUES (?, ?, ?, ?, ?)",
                    matchId, player.seat(), player.nickname(), player.city().name(), player.bot());
        }
    }

    @Override
    public void commandProcessed(UUID matchId, ProcessedCommand command) {
        MatchCommandCodec.EncodedCommand encoded = MatchCommandCodec.encode(command.command(), json);
        LoggedOutcome outcome = MatchLogs.outcomeOf(command);
        RejectionCode rejectionCode = MatchLogs.rejectionCodeOf(command).orElse(null);
        Integer actorSeat = command.actorSeat().isPresent() ? command.actorSeat().getAsInt() : null;
        String commandId = command.commandId().orElse(null);
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO match_commands
                        (match_id, command_sequence, command_id, origin, actor_seat, command_type,
                         command_schema_version, payload, outcome, rejection_code, resulting_state_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            statement.setObject(1, matchId);
            statement.setLong(2, command.sequence());
            setNullableString(statement, 3, commandId);
            statement.setString(4, command.origin().name());
            setNullableInt(statement, 5, actorSeat);
            statement.setString(6, encoded.commandType());
            statement.setInt(7, COMMAND_SCHEMA_VERSION);
            statement.setObject(8, jsonb(json.writeValueAsString(encoded.payload())));
            statement.setString(9, outcome.name());
            setNullableString(statement, 10, rejectionCode == null ? null : rejectionCode.name());
            statement.setLong(11, command.resultingStateVersion());
            return statement;
        });
    }

    @Override
    public void matchFinished(UUID matchId, FinalResult result) {
        jdbc.update("UPDATE matches SET status = ?, finished_at = ? WHERE id = ?", "FINISHED",
                Timestamp.from(java.time.Instant.now()), matchId);
        jdbc.update(connection -> {
            PreparedStatement statement = connection
                    .prepareStatement("INSERT INTO match_results (match_id, scores, winner_seats) VALUES (?, ?, ?)");
            statement.setObject(1, matchId);
            statement.setObject(2, jsonb(json.writeValueAsString(result.scores())));
            statement.setObject(3, jsonb(json.writeValueAsString(result.winnerSeats())));
            return statement;
        });
    }

    @Override
    public MatchRecord loadMatch(UUID matchId) {
        List<MatchRecord> found = jdbc.query(
                "SELECT id, seed, ruleset_version, created_at FROM matches WHERE id = ?",
                (rs, rowNum) -> new MatchRecord(matchId, rs.getLong("seed"), rs.getString("ruleset_version"),
                        players(matchId), rs.getTimestamp("created_at").toInstant()),
                matchId);
        if (found.isEmpty()) {
            throw new IllegalArgumentException("no match logged for " + matchId);
        }
        return found.getFirst();
    }

    private List<MatchPlayer> players(UUID matchId) {
        return jdbc.query("SELECT seat, nickname, city, is_bot FROM match_players WHERE match_id = ? ORDER BY seat",
                (rs, rowNum) -> new MatchPlayer(rs.getInt("seat"), rs.getString("nickname"),
                        CityType.valueOf(rs.getString("city")), rs.getBoolean("is_bot")),
                matchId);
    }

    @Override
    public List<LoggedCommand> loadCommands(UUID matchId) {
        return jdbc.query(
                "SELECT command_sequence, command_id, origin, actor_seat, command_type, payload, outcome, "
                        + "rejection_code, resulting_state_version FROM match_commands WHERE match_id = ? "
                        + "ORDER BY command_sequence",
                (rs, rowNum) -> toLoggedCommand(rs),
                matchId);
    }

    private LoggedCommand toLoggedCommand(ResultSet rs) throws SQLException {
        String commandId = rs.getString("command_id");
        int actorSeatValue = rs.getInt("actor_seat");
        OptionalInt actorSeat = rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(actorSeatValue);
        String rejectionCodeText = rs.getString("rejection_code");
        GameCommand command = MatchCommandCodec.decode(rs.getString("command_type"),
                json.readTree(rs.getString("payload")), json);
        return new LoggedCommand(rs.getLong("command_sequence"), CommandOrigin.valueOf(rs.getString("origin")),
                actorSeat, Optional.ofNullable(commandId), command,
                LoggedOutcome.valueOf(rs.getString("outcome")),
                Optional.ofNullable(rejectionCodeText).map(RejectionCode::valueOf),
                rs.getLong("resulting_state_version"));
    }

    private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static PGobject jsonb(String value) {
        PGobject object = new PGobject();
        object.setType("jsonb");
        try {
            object.setValue(value);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return object;
    }
}
