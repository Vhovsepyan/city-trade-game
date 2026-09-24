package citytrade.bots;

import citytrade.engine.GameEngine;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Plays one whole game with one bot per seat. In each window the seats act in seat order, one command per
 * turn, until a full pass over all seats sends nothing. A rejected command is recorded and ends that seat's
 * window, so a bot cannot loop on the same invalid command.
 */
public final class BotGame {

    /** Runaway protection only (a bot that never stops), not a game rule. */
    static final int MAX_COMMANDS_PER_WINDOW = 1_000;

    /** A bot command the engine rejected. */
    public record Rejection(int round, GameCommand command, RejectionCode code, String detail) {
    }

    /** The finished game: the final state, every accepted command in order, and the rejected ones. */
    public record Result(GameState finalState, List<GameCommand> commands, List<Rejection> rejections) {

        public Result {
            commands = List.copyOf(commands);
            rejections = List.copyOf(rejections);
        }
    }

    private final Ruleset ruleset;
    private final List<Bot> bots;
    private GameState state;
    private final List<GameCommand> commands = new ArrayList<>();
    private final List<Rejection> rejections = new ArrayList<>();

    private BotGame(long seed, Ruleset ruleset, List<Bot> bots) {
        if (bots.size() != ruleset.playerCount()) {
            throw new IllegalArgumentException(
                    "need " + ruleset.playerCount() + " bots, one per seat, but got " + bots.size());
        }
        this.ruleset = ruleset;
        this.bots = List.copyOf(bots);
        this.state = GameSetup.create(seed, ruleset);
    }

    /** Plays a new game with {@code seed}; {@code bots.get(seat)} plays that seat. */
    public static Result play(long seed, Ruleset ruleset, List<Bot> bots) {
        BotGame game = new BotGame(seed, ruleset, bots);
        game.playAll();
        return new Result(game.state, game.commands, game.rejections);
    }

    private void playAll() {
        for (int seat = 0; seat < bots.size(); seat++) {
            submit(bots.get(seat).chooseObjectives(state, seat, ruleset));
        }
        for (int round = 1; round <= ruleset.roundCount(); round++) {
            applyRequired(new StartRound());
            window();
            applyRequired(new ResolveRound());
        }
    }

    private void window() {
        boolean[] done = new boolean[bots.size()];
        int sent = 0;
        boolean anySent = true;
        while (anySent) {
            anySent = false;
            for (int seat = 0; seat < bots.size(); seat++) {
                if (done[seat]) {
                    continue;
                }
                Optional<GameCommand> command = bots.get(seat).nextWindowCommand(state, seat, ruleset);
                if (command.isEmpty()) {
                    done[seat] = true;
                    continue;
                }
                if (++sent > MAX_COMMANDS_PER_WINDOW) {
                    throw new IllegalStateException("bots sent more than " + MAX_COMMANDS_PER_WINDOW
                            + " commands in the window of round " + state.round());
                }
                anySent = true;
                if (!submit(command.get())) {
                    done[seat] = true;
                }
            }
        }
    }

    /** Applies a bot command; returns false and records the rejection if the engine rejects it. */
    private boolean submit(GameCommand command) {
        GameResult result = GameEngine.apply(state, command, ruleset);
        switch (result) {
            case GameResult.Accepted accepted -> {
                state = accepted.state();
                commands.add(command);
                return true;
            }
            case GameResult.Rejected rejected -> {
                rejections.add(new Rejection(state.round(), command, rejected.code(), rejected.detail()));
                return false;
            }
        }
    }

    /** Round commands of the runner itself must always be accepted; a rejection here is a runner bug. */
    private void applyRequired(GameCommand command) {
        if (!submit(command)) {
            throw new IllegalStateException(command.getClass().getSimpleName() + " was rejected: "
                    + rejections.getLast());
        }
    }
}
