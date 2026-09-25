package citytrade.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import citytrade.engine.GameEngine;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import citytrade.ruleset.json.RulesetLoader;
import citytrade.server.game.CommandOrigin;
import citytrade.server.game.CommandOutcome;
import citytrade.server.game.ProcessedCommand;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Architecture 7.2: {@code seed + rulesetVersion + accepted commands in commandSequence order -> GameState}. */
class ReplayServiceTest {

    private static final long SEED = 909L;

    @Test
    void replayEqualsDirectlyApplyingTheSameCommandsInOrder() {
        Ruleset ruleset = ruleset();
        GameState state = GameSetup.create(SEED, ruleset);
        List<GameCommand> accepted = List.of(
                chooseFirstObjectives(state, ruleset, 0), chooseFirstObjectives(state, ruleset, 1),
                chooseFirstObjectives(state, ruleset, 2), chooseFirstObjectives(state, ruleset, 3),
                new StartRound());
        GameState expected = applyDirectly(state, accepted, ruleset);

        InMemoryMatchLog log = new InMemoryMatchLog();
        UUID matchId = UUID.randomUUID();
        log.matchStarted(matchId, SEED, ruleset.version(), List.of(), java.time.Instant.EPOCH);
        long sequence = 1;
        for (GameCommand command : accepted) {
            log.commandProcessed(matchId, acceptedCommand(sequence++, command));
        }

        GameState replayed = ReplayService.replay(log.loadMatch(matchId), log.loadCommands(matchId), ruleset);
        assertThat(replayed).isEqualTo(expected);
    }

    @Test
    void replayOrdersByCommandSequenceRegardlessOfInsertionOrder() {
        Ruleset ruleset = ruleset();
        GameState state = GameSetup.create(SEED, ruleset);
        List<GameCommand> accepted = List.of(
                chooseFirstObjectives(state, ruleset, 0), chooseFirstObjectives(state, ruleset, 1),
                chooseFirstObjectives(state, ruleset, 2), chooseFirstObjectives(state, ruleset, 3),
                new StartRound());
        GameState expected = applyDirectly(state, accepted, ruleset);

        // Build the (sequence, command) pairs, then insert them in a shuffled order.
        List<ProcessedCommand> inOrder = new ArrayList<>();
        for (int i = 0; i < accepted.size(); i++) {
            inOrder.add(acceptedCommand(i + 1, accepted.get(i)));
        }
        List<ProcessedCommand> shuffled = new ArrayList<>(inOrder);
        Collections.shuffle(shuffled, new java.util.Random(7));

        InMemoryMatchLog log = new InMemoryMatchLog();
        UUID matchId = UUID.randomUUID();
        log.matchStarted(matchId, SEED, ruleset.version(), List.of(), java.time.Instant.EPOCH);
        shuffled.forEach(command -> log.commandProcessed(matchId, command));

        GameState replayed = ReplayService.replay(log.loadMatch(matchId), log.loadCommands(matchId), ruleset);
        assertThat(replayed).isEqualTo(expected);
    }

    @Test
    void rejectedAndRefusedCommandsAreSkippedDuringReplay() {
        Ruleset ruleset = ruleset();
        GameState state = GameSetup.create(SEED, ruleset);
        GameCommand chosen0 = chooseFirstObjectives(state, ruleset, 0);
        GameState expected = applyDirectly(state, List.of(chosen0), ruleset);

        InMemoryMatchLog log = new InMemoryMatchLog();
        UUID matchId = UUID.randomUUID();
        log.matchStarted(matchId, SEED, ruleset.version(), List.of(), java.time.Instant.EPOCH);
        log.commandProcessed(matchId, acceptedCommand(1, chosen0));
        // A rejected command (wrong number of kept ids) and a refused one (client tried an internal command)
        // are both stored, but must never be applied on replay.
        log.commandProcessed(matchId, new ProcessedCommand(2, CommandOrigin.PLAYER, OptionalInt.of(1),
                Optional.of("bad"), new ChooseObjectives(1, List.of()), new CommandOutcome.Applied(
                        new GameResult.Rejected(RejectionCode.INVALID_OBJECTIVE_CHOICE, "wrong count")), 1));
        log.commandProcessed(matchId, new ProcessedCommand(3, CommandOrigin.PLAYER, OptionalInt.of(1),
                Optional.of("bad-2"), new StartRound(), new CommandOutcome.Refused("internal command"), 1));

        GameState replayed = ReplayService.replay(log.loadMatch(matchId), log.loadCommands(matchId), ruleset);
        assertThat(replayed).isEqualTo(expected);
    }

    @Test
    void aRulesetVersionMismatchIsRejected() {
        Ruleset ruleset = ruleset();
        InMemoryMatchLog log = new InMemoryMatchLog();
        UUID matchId = UUID.randomUUID();
        log.matchStarted(matchId, SEED, "some-other-version", List.of(), java.time.Instant.EPOCH);

        assertThatThrownBy(
                () -> ReplayService.replay(log.loadMatch(matchId), log.loadCommands(matchId), ruleset))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GameState applyDirectly(GameState state, List<GameCommand> commands, Ruleset ruleset) {
        GameState current = state;
        for (GameCommand command : commands) {
            current = ((GameResult.Accepted) GameEngine.apply(current, command, ruleset)).state();
        }
        return current;
    }

    private static ProcessedCommand acceptedCommand(long sequence, GameCommand command) {
        return new ProcessedCommand(sequence, CommandOrigin.SYSTEM, OptionalInt.empty(), Optional.empty(), command,
                new CommandOutcome.Applied(new GameResult.Accepted(GameSetup.create(SEED, ruleset()), List.of())),
                sequence);
    }

    private static ChooseObjectives chooseFirstObjectives(GameState state, Ruleset ruleset, int seat) {
        List<String> ids = state.player(seat).dealtObjectives().stream()
                .limit(ruleset.objectives().keptPerPlayer())
                .map(ObjectiveCard::id)
                .toList();
        return new ChooseObjectives(seat, ids);
    }

    private static Ruleset ruleset() {
        return RulesetLoader.load(Path.of(System.getProperty("rulesets.dir", "rulesets"), "prototype-002.json"));
    }
}
