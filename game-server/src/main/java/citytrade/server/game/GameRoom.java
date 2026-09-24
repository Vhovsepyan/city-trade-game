package citytrade.server.game;

import citytrade.engine.GameEngine;
import citytrade.engine.command.AcceptTrade;
import citytrade.engine.command.BreakContract;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.CancelContractMutually;
import citytrade.engine.command.CancelTrade;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.ContributeToProject;
import citytrade.engine.command.CounterTrade;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.ProposeContract;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.RejectTrade;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.SetCrisisPolicy;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.command.SignContract;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.command.UseEventOption;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * One serial command queue for an ACTIVE room (Architecture 4.10, 6.2). All state changes for the
 * room's game happen only inside this queue's single worker, so concurrent submissions from many
 * players never race. {@code GameRoom} contains no game rules; it only sequences and versions calls
 * into {@link GameEngine#apply}.
 */
public final class GameRoom {

    /** Lets tests replace {@link GameEngine#apply} with a double, e.g. to force a no-op Accepted result. */
    @FunctionalInterface
    public interface Engine {
        GameResult apply(GameState state, GameCommand command, Ruleset ruleset);
    }

    private final Ruleset ruleset;
    private final Engine engine;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<ProcessedCommand> history = new CopyOnWriteArrayList<>();
    private final List<Consumer<ProcessedCommand>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicLong commandSequence = new AtomicLong();
    private final AtomicLong stateVersion = new AtomicLong();

    private volatile GameState state;

    public GameRoom(GameState initialState, Ruleset ruleset) {
        this(initialState, ruleset, GameEngine::apply);
    }

    public GameRoom(GameState initialState, Ruleset ruleset, Engine engine) {
        this.state = Objects.requireNonNull(initialState);
        this.ruleset = Objects.requireNonNull(ruleset);
        this.engine = Objects.requireNonNull(engine);
    }

    /** A command from a connected player; {@code seat} is the caller's own seat, injected into the command. */
    public Future<ProcessedCommand> submitPlayerCommand(int seat, GameCommand command) {
        return submit(CommandOrigin.PLAYER, OptionalInt.of(seat), command);
    }

    /** A command chosen by a bot coordinator acting for {@code seat}; {@code seat} is injected into the command. */
    public Future<ProcessedCommand> submitBotCommand(int seat, GameCommand command) {
        return submit(CommandOrigin.BOT, OptionalInt.of(seat), command);
    }

    /** An internal command (StartRound/ResolveRound) or a server-side fallback; never from a client. */
    public Future<ProcessedCommand> submitSystemCommand(GameCommand command) {
        return submit(CommandOrigin.SYSTEM, OptionalInt.empty(), command);
    }

    /**
     * Called with every {@link ProcessedCommand}, right after it is appended to {@link #history()}, still on
     * this room's own worker thread. {@code T20b}'s round-flow driver uses this to react to state changes
     * (schedule the next timer, ask bots again, check READY) without polling. A listener must never block:
     * it runs inside the single worker that also processes every future command, so calling {@code .get()} on
     * a {@link Future} returned by this room from within a listener would deadlock. A listener that throws is
     * caught and ignored, so a driver bug can never corrupt command processing for the room.
     */
    public void addListener(Consumer<ProcessedCommand> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    private Future<ProcessedCommand> submit(CommandOrigin origin, OptionalInt actorSeat, GameCommand command) {
        Objects.requireNonNull(command);
        return executor.submit(() -> process(origin, actorSeat, command));
    }

    private ProcessedCommand process(CommandOrigin origin, OptionalInt actorSeat, GameCommand command) {
        long sequence = commandSequence.incrementAndGet();
        GameCommand effectiveCommand = actorSeat.isPresent() ? withSeat(command, actorSeat.getAsInt()) : command;
        CommandOutcome outcome = refusalReason(origin, effectiveCommand)
                .<CommandOutcome>map(CommandOutcome.Refused::new)
                .orElseGet(() -> applyToEngine(effectiveCommand));
        ProcessedCommand record =
                new ProcessedCommand(sequence, origin, actorSeat, effectiveCommand, outcome, stateVersion.get());
        history.add(record);
        for (Consumer<ProcessedCommand> listener : listeners) {
            try {
                listener.accept(record);
            } catch (RuntimeException e) {
                // A listener must never break the room's own command processing; see addListener's contract.
            }
        }
        return record;
    }

    private CommandOutcome applyToEngine(GameCommand command) {
        GameResult result = engine.apply(state, command, ruleset);
        if (result instanceof GameResult.Accepted accepted && !accepted.state().equals(state)) {
            state = accepted.state();
            stateVersion.incrementAndGet();
        }
        return new CommandOutcome.Applied(result);
    }

    /** Empty when the command may go to the engine; otherwise the reason it is refused first. */
    private static Optional<String> refusalReason(CommandOrigin origin, GameCommand command) {
        if (origin != CommandOrigin.SYSTEM && isInternal(command)) {
            return Optional.of(command.getClass().getSimpleName()
                    + " is an internal command; only the server may submit it");
        }
        return Optional.empty();
    }

    private static boolean isInternal(GameCommand command) {
        return command instanceof StartRound || command instanceof ResolveRound;
    }

    /** Rebuilds {@code command} with its {@code seat} field replaced by the caller's authenticated seat. */
    private static GameCommand withSeat(GameCommand command, int seat) {
        return switch (command) {
            case ChooseObjectives c -> new ChooseObjectives(seat, c.keptObjectiveIds());
            case SetUpkeepPriority c -> new SetUpkeepPriority(seat, c.order());
            case BuyFromMarket c -> new BuyFromMarket(seat, c.resource(), c.quantity());
            case SellToMarket c -> new SellToMarket(seat, c.resource(), c.quantity());
            case UpgradeCity ignored -> new UpgradeCity(seat);
            case BuildBuilding c -> new BuildBuilding(seat, c.buildingId(), c.chosenResource());
            case ProposeTrade c -> new ProposeTrade(seat, c.recipientSeat(), c.offered(), c.requested());
            case AcceptTrade c -> new AcceptTrade(seat, c.offerId());
            case RejectTrade c -> new RejectTrade(seat, c.offerId());
            case CancelTrade c -> new CancelTrade(seat, c.offerId());
            case CounterTrade c -> new CounterTrade(seat, c.offerId(), c.offered(), c.requested());
            case ProposeContract c ->
                    new ProposeContract(seat, c.creditorSeat(), c.debtorSeat(), c.givenNow(), c.owed(), c.dueRound());
            case SignContract c -> new SignContract(seat, c.contractId());
            case BreakContract c -> new BreakContract(seat, c.contractId());
            case CancelContractMutually c -> new CancelContractMutually(seat, c.contractId());
            case SetCrisisPolicy c -> new SetCrisisPolicy(seat, c.policy());
            case UseEventOption c -> new UseEventOption(seat, c.chosenResource());
            case ContributeToProject c -> new ContributeToProject(seat, c.projectId(), c.contribution());
            case PlaceBid c -> new PlaceBid(seat, c.opportunityId(), c.amount());
            case StartRound ignored -> command;
            case ResolveRound ignored -> command;
        };
    }

    public GameState state() {
        return state;
    }

    public long stateVersion() {
        return stateVersion.get();
    }

    public long commandSequence() {
        return commandSequence.get();
    }

    /** Every processed command in sequence order; {@code T24} persists exactly this. */
    public List<ProcessedCommand> history() {
        return List.copyOf(history);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
