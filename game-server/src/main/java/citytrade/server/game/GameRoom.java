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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
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
    // Only ever read/written from process(), which always runs on this room's single executor thread
    // (Architecture 6.7): a plain HashMap needs no extra synchronization.
    private final Map<String, ProcessedCommand> outcomesByCommandId = new HashMap<>();

    private volatile GameState state;

    public GameRoom(GameState initialState, Ruleset ruleset) {
        this(initialState, ruleset, GameEngine::apply);
    }

    public GameRoom(GameState initialState, Ruleset ruleset, Engine engine) {
        this.state = Objects.requireNonNull(initialState);
        this.ruleset = Objects.requireNonNull(ruleset);
        this.engine = Objects.requireNonNull(engine);
    }

    /**
     * A command from a connected player, without idempotency tracking; {@code seat} is the caller's own seat,
     * injected into the command. Used only where no client {@code commandId} exists (tests, and callers
     * outside the WebSocket protocol). Every submission gets its own internal, never-reused id, so it never
     * collides with - or is deduplicated against - a real client {@code commandId}.
     */
    public Future<ProcessedCommand> submitPlayerCommand(int seat, GameCommand command) {
        return submit(CommandOrigin.PLAYER, OptionalInt.of(seat), Optional.of(UUID.randomUUID().toString()),
                command);
    }

    /**
     * A command from a connected player (Architecture 6.6, 6.7); {@code seat} is the caller's own seat,
     * injected into the command. {@code commandId} is the client's idempotency key: a duplicate {@code
     * commandId} from the SAME seat returns the exact same {@link ProcessedCommand} again - no new sequence
     * number, no engine call, no {@code stateVersion} change. The same {@code commandId} from a DIFFERENT seat
     * is refused before reaching the engine.
     */
    public Future<ProcessedCommand> submitPlayerCommand(int seat, String commandId, GameCommand command) {
        Objects.requireNonNull(commandId);
        return submit(CommandOrigin.PLAYER, OptionalInt.of(seat), Optional.of(commandId), command);
    }

    /** A command chosen by a bot coordinator acting for {@code seat}; {@code seat} is injected into the command. */
    public Future<ProcessedCommand> submitBotCommand(int seat, GameCommand command) {
        return submit(CommandOrigin.BOT, OptionalInt.of(seat), Optional.empty(), command);
    }

    /** An internal command (StartRound/ResolveRound) or a server-side fallback; never from a client. */
    public Future<ProcessedCommand> submitSystemCommand(GameCommand command) {
        return submit(CommandOrigin.SYSTEM, OptionalInt.empty(), Optional.empty(), command);
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

    private Future<ProcessedCommand> submit(CommandOrigin origin, OptionalInt actorSeat, Optional<String> commandId,
            GameCommand command) {
        Objects.requireNonNull(command);
        return executor.submit(() -> process(origin, actorSeat, commandId, command));
    }

    private ProcessedCommand process(CommandOrigin origin, OptionalInt actorSeat, Optional<String> commandId,
            GameCommand command) {
        if (origin == CommandOrigin.PLAYER && commandId.isPresent()) {
            ProcessedCommand existing = outcomesByCommandId.get(commandId.get());
            if (existing != null) {
                if (existing.actorSeat().equals(actorSeat)) {
                    // Duplicate commandId from the SAME seat (Architecture 6.7): the exact same outcome again,
                    // no engine call, no new sequence number, no stateVersion change.
                    return existing;
                }
                return refuse(origin, actorSeat, commandId, command,
                        "commandId " + commandId.get() + " was already used by a different seat");
            }
        }
        long sequence = commandSequence.incrementAndGet();
        GameCommand effectiveCommand = actorSeat.isPresent() ? withSeat(command, actorSeat.getAsInt()) : command;
        CommandOutcome outcome = refusalReason(origin, effectiveCommand)
                .<CommandOutcome>map(CommandOutcome.Refused::new)
                .orElseGet(() -> applyToEngine(effectiveCommand));
        ProcessedCommand record = new ProcessedCommand(sequence, origin, actorSeat, commandId, effectiveCommand,
                outcome, stateVersion.get());
        history.add(record);
        if (origin == CommandOrigin.PLAYER) {
            commandId.ifPresent(id -> outcomesByCommandId.put(id, record));
        }
        notifyListeners(record);
        return record;
    }

    /** A commandId reused by a different seat than the one that first used it: refused before the engine. */
    private ProcessedCommand refuse(CommandOrigin origin, OptionalInt actorSeat, Optional<String> commandId,
            GameCommand command, String reason) {
        long sequence = commandSequence.incrementAndGet();
        ProcessedCommand record = new ProcessedCommand(sequence, origin, actorSeat, commandId, command,
                new CommandOutcome.Refused(reason), stateVersion.get());
        history.add(record);
        notifyListeners(record);
        return record;
    }

    private void notifyListeners(ProcessedCommand record) {
        for (Consumer<ProcessedCommand> listener : listeners) {
            try {
                listener.accept(record);
            } catch (RuntimeException e) {
                // A listener must never break the room's own command processing; see addListener's contract.
            }
        }
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
