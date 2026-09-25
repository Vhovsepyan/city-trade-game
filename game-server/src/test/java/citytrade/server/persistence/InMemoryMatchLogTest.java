package citytrade.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import citytrade.engine.CityType;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.FinalResult;
import citytrade.engine.state.GameState;
import citytrade.ruleset.json.RulesetLoader;
import citytrade.server.game.CommandOrigin;
import citytrade.server.game.CommandOutcome;
import citytrade.server.game.ProcessedCommand;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The default, no-database {@link MatchLog} (Architecture 7.1). */
class InMemoryMatchLogTest {

    private static final GameState SOME_STATE = GameSetup.create(1L, ruleset());

    @Test
    void loadsBackWhatWasStartedAndProcessedInSequenceOrder() {
        InMemoryMatchLog log = new InMemoryMatchLog();
        UUID matchId = UUID.randomUUID();
        List<MatchPlayer> players = List.of(new MatchPlayer(0, "host", CityType.AGRICULTURAL, false),
                new MatchPlayer(1, null, CityType.ENERGY, true));
        Instant startedAt = Instant.parse("2026-01-01T00:00:00Z");

        log.matchStarted(matchId, 42L, "prototype-002", players, startedAt);
        log.commandProcessed(matchId, processed(2, new ChooseObjectives(0, List.of("a"))));
        log.commandProcessed(matchId, processed(1, new StartRound()));

        MatchRecord record = log.loadMatch(matchId);
        assertThat(record.seed()).isEqualTo(42L);
        assertThat(record.rulesetVersion()).isEqualTo("prototype-002");
        assertThat(record.players()).isEqualTo(players);
        assertThat(record.startedAt()).isEqualTo(startedAt);

        List<LoggedCommand> commands = log.loadCommands(matchId);
        assertThat(commands).extracting(LoggedCommand::commandSequence).containsExactly(1L, 2L);
    }

    @Test
    void loadingAnUnknownMatchFails() {
        assertThatThrownBy(() -> new InMemoryMatchLog().loadMatch(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectedAndRefusedOutcomesAreClassifiedCorrectly() {
        InMemoryMatchLog log = new InMemoryMatchLog();
        UUID matchId = UUID.randomUUID();
        log.matchStarted(matchId, 1L, "prototype-002", List.of(), Instant.EPOCH);

        ProcessedCommand accepted = new ProcessedCommand(1, CommandOrigin.SYSTEM, OptionalInt.empty(),
                Optional.empty(), new StartRound(),
                new CommandOutcome.Applied(new GameResult.Accepted(SOME_STATE, List.of())), 1);
        ProcessedCommand rejected = new ProcessedCommand(2, CommandOrigin.PLAYER, OptionalInt.of(0),
                Optional.of("cmd-1"), new ChooseObjectives(0, List.of()),
                new CommandOutcome.Applied(new GameResult.Rejected(RejectionCode.INVALID_OBJECTIVE_CHOICE, "bad")), 1);
        ProcessedCommand refused = new ProcessedCommand(3, CommandOrigin.PLAYER, OptionalInt.of(0),
                Optional.of("cmd-2"), new StartRound(), new CommandOutcome.Refused("internal command"), 1);
        log.commandProcessed(matchId, accepted);
        log.commandProcessed(matchId, rejected);
        log.commandProcessed(matchId, refused);

        List<LoggedCommand> commands = log.loadCommands(matchId);
        assertThat(commands.get(0).outcome()).isEqualTo(LoggedOutcome.ACCEPTED);
        assertThat(commands.get(0).rejectionCode()).isEmpty();
        assertThat(commands.get(1).outcome()).isEqualTo(LoggedOutcome.REJECTED);
        assertThat(commands.get(1).rejectionCode()).contains(RejectionCode.INVALID_OBJECTIVE_CHOICE);
        assertThat(commands.get(2).outcome()).isEqualTo(LoggedOutcome.REFUSED);
        assertThat(commands.get(2).rejectionCode()).isEmpty();
    }

    @Test
    void matchFinishedRecordsTheFinalResult() {
        InMemoryMatchLog log = new InMemoryMatchLog();
        UUID matchId = UUID.randomUUID();
        log.matchStarted(matchId, 1L, "prototype-002", List.of(), Instant.EPOCH);
        assertThat(log.resultOf(matchId)).isEmpty();

        FinalResult result = new FinalResult(List.of(), List.of(0));
        log.matchFinished(matchId, result);

        assertThat(log.resultOf(matchId)).contains(result);
    }

    private static ProcessedCommand processed(long sequence, citytrade.engine.command.GameCommand command) {
        return new ProcessedCommand(sequence, CommandOrigin.SYSTEM, OptionalInt.empty(), Optional.empty(), command,
                new CommandOutcome.Applied(new GameResult.Accepted(SOME_STATE, List.of())), sequence);
    }

    private static Ruleset ruleset() {
        return RulesetLoader.load(Path.of(System.getProperty("rulesets.dir", "rulesets"), "prototype-002.json"));
    }
}
