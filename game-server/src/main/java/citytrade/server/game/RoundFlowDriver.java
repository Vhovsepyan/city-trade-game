package citytrade.server.game;

import citytrade.bots.Bot;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Drives one ACTIVE room's round flow automatically (Architecture 6.4; D20-D24; Numbers Sheet "ROUND ORDER"):
 * objective choice -> Round 1 -> ... -> Round 14 -> FINISHED, using only the room's own {@link GameRoom} queue.
 * {@code RoundFlowDriver} never touches {@code GameState} itself; it only decides WHEN to submit the next
 * {@code StartRound}/{@code ResolveRound}/{@code ChooseObjectives}, and when to ask a bot for its next command.
 *
 * <p>All reactions happen from {@link GameRoom#addListener}, which calls back on the room's own single worker
 * thread. Timer callbacks and {@link #setReady}/{@link #setDisconnected} run on whatever thread calls them
 * (a scheduler thread, or later a WebSocket/REST thread). Every method that touches the driver's own fields is
 * {@code synchronized}, and every submission into {@code GameRoom} is fire-and-forget (never {@code .get()}),
 * so the driver can never deadlock against the room's serial queue.
 */
public final class RoundFlowDriver {

    /**
     * Runaway protection (Do section): a broken bot cannot loop forever. Reuses the same order of magnitude as
     * {@code BotGame.MAX_COMMANDS_PER_WINDOW} (1000), applied per bot seat per round instead of per whole window.
     */
    static final int DEFAULT_MAX_ACTIONS_PER_BOT_PER_ROUND = 1000;

    private final GameRoom gameRoom;
    private final Ruleset ruleset;
    private final Clock clock;
    private final RoundScheduler scheduler;
    private final Duration windowDuration;
    private final Duration objectiveChoiceTimeout;
    private final Map<Integer, Bot> bots;
    private final Runnable onFinished;
    private final int maxActionsPerBotPerRound;

    private final int seatCount;
    private final boolean[] ready;
    private final boolean[] disconnected;
    private final boolean[] botPending;
    private final boolean[] botCapped;
    private final int[] botActionCount;

    private boolean started;
    private boolean finished;
    private boolean resolveEnqueuedThisRound;
    private int currentWindowRound = -1;
    private long lastSeenStateVersion;
    private Instant phaseEndsAt;
    private long roomVersion;

    public RoundFlowDriver(GameRoom gameRoom, Ruleset ruleset, Clock clock, RoundScheduler scheduler,
            Duration windowDuration, Duration objectiveChoiceTimeout, Map<Integer, Bot> bots, Runnable onFinished) {
        this(gameRoom, ruleset, clock, scheduler, windowDuration, objectiveChoiceTimeout, bots, onFinished,
                DEFAULT_MAX_ACTIONS_PER_BOT_PER_ROUND);
    }

    /** Package-visible: lets tests use a small cap so the runaway-protection rule can be tested without 1000 steps. */
    RoundFlowDriver(GameRoom gameRoom, Ruleset ruleset, Clock clock, RoundScheduler scheduler,
            Duration windowDuration, Duration objectiveChoiceTimeout, Map<Integer, Bot> bots, Runnable onFinished,
            int maxActionsPerBotPerRound) {
        this.gameRoom = Objects.requireNonNull(gameRoom);
        this.ruleset = Objects.requireNonNull(ruleset);
        this.clock = Objects.requireNonNull(clock);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.windowDuration = Objects.requireNonNull(windowDuration);
        this.objectiveChoiceTimeout = Objects.requireNonNull(objectiveChoiceTimeout);
        // TreeMap (not Map.copyOf, whose iteration order is unspecified): bot evaluation order must be
        // deterministic by seat (AGENTS.md "Deterministic"), since it decides command submission order.
        this.bots = new TreeMap<>(bots);
        this.onFinished = Objects.requireNonNull(onFinished);
        if (maxActionsPerBotPerRound <= 0) {
            throw new IllegalArgumentException("maxActionsPerBotPerRound must be positive");
        }
        this.maxActionsPerBotPerRound = maxActionsPerBotPerRound;
        this.seatCount = ruleset.playerCount();
        this.ready = new boolean[seatCount];
        this.disconnected = new boolean[seatCount];
        this.botPending = new boolean[seatCount];
        this.botCapped = new boolean[seatCount];
        this.botActionCount = new int[seatCount];
        gameRoom.addListener(this::onProcessed);
    }

    /** Starts the objective-choice phase: schedules its timeout (D22) and asks every bot to choose at once. */
    public synchronized void start() {
        if (started) {
            throw new IllegalStateException("RoundFlowDriver already started");
        }
        started = true;
        lastSeenStateVersion = gameRoom.stateVersion();
        GameState state = gameRoom.state();
        currentWindowRound = state.round();
        setPhaseEndsAt(clock.instant().plus(objectiveChoiceTimeout));
        scheduler.schedule(phaseEndsAt, this::onObjectiveTimeout);
        for (Map.Entry<Integer, Bot> entry : bots.entrySet()) {
            int seat = entry.getKey();
            gameRoom.submitBotCommand(seat, entry.getValue().chooseObjectives(state, seat, ruleset));
        }
    }

    /** D21: a human seat becomes ready or cancels READY; resets every round. Ignored outside the WINDOW phase. */
    public synchronized void setReady(int seat, boolean isReady) {
        validateSeat(seat);
        if (gameRoom.state().phase() != GamePhase.WINDOW) {
            return;
        }
        if (ready[seat] == isReady) {
            return;
        }
        ready[seat] = isReady;
        roomVersion++;
        if (isReady) {
            tryResolveIfAllReady(gameRoom.state());
        }
    }

    public synchronized boolean isReady(int seat) {
        validateSeat(seat);
        return ready[seat];
    }

    /** D23: whether {@code seat} is currently disconnected (server metadata; T22's views need this). */
    public synchronized boolean isDisconnected(int seat) {
        validateSeat(seat);
        return disconnected[seat];
    }

    /** D23: a disconnected human counts as ready immediately, until it reconnects. */
    public synchronized void setDisconnected(int seat, boolean isDisconnected) {
        validateSeat(seat);
        if (disconnected[seat] == isDisconnected) {
            return;
        }
        disconnected[seat] = isDisconnected;
        roomVersion++;
        if (isDisconnected) {
            tryResolveIfAllReady(gameRoom.state());
        }
    }

    public synchronized Instant phaseEndsAt() {
        return phaseEndsAt;
    }

    public synchronized long roomVersion() {
        return roomVersion;
    }

    public GameRoom gameRoom() {
        return gameRoom;
    }

    private synchronized void onProcessed(ProcessedCommand processed) {
        boolean botCommandCleared = false;
        if (processed.origin() == CommandOrigin.BOT) {
            botCommandCleared = processed.actorSeat().isPresent();
            processed.actorSeat().ifPresent(seat -> botPending[seat] = false);
        }
        if (processed.resultingStateVersion() == lastSeenStateVersion) {
            // A rejected or no-op bot command never changes stateVersion, but it did just clear botPending,
            // so a round that was otherwise all-ready must not wait for the window timer (R1-P2-2).
            if (botCommandCleared && gameRoom.state().phase() == GamePhase.WINDOW) {
                tryResolveIfAllReady(gameRoom.state());
            }
            return;
        }
        lastSeenStateVersion = processed.resultingStateVersion();
        GameState state = gameRoom.state();
        switch (state.phase()) {
            case SETUP -> {
                if (state.allObjectivesChosen()) {
                    gameRoom.submitSystemCommand(new StartRound());
                }
            }
            case WINDOW -> onWindowStateChanged(state);
            case RESOLUTION -> gameRoom.submitSystemCommand(new StartRound());
            case FINISHED -> onFinishedOnce();
            case AUTOMATIC, WORLD -> {
                // Never observed here: StartRound runs both phases internally before returning WINDOW.
            }
        }
    }

    private void onWindowStateChanged(GameState state) {
        if (state.round() != currentWindowRound) {
            currentWindowRound = state.round();
            resetForNewRound();
            setPhaseEndsAt(clock.instant().plus(windowDuration));
            scheduler.schedule(phaseEndsAt, this::onWindowTimerFired);
        }
        evaluateBots(state);
        tryResolveIfAllReady(state);
    }

    private void resetForNewRound() {
        Arrays.fill(ready, false);
        Arrays.fill(botPending, false);
        Arrays.fill(botCapped, false);
        Arrays.fill(botActionCount, 0);
        resolveEnqueuedThisRound = false;
        roomVersion++;
    }

    private void evaluateBots(GameState state) {
        for (Map.Entry<Integer, Bot> entry : bots.entrySet()) {
            int seat = entry.getKey();
            if (botPending[seat] || botCapped[seat]) {
                continue;
            }
            Optional<GameCommand> next = entry.getValue().nextWindowCommand(state, seat, ruleset);
            if (next.isEmpty()) {
                continue;
            }
            if (botActionCount[seat] >= maxActionsPerBotPerRound) {
                botCapped[seat] = true;
                continue;
            }
            botActionCount[seat]++;
            botPending[seat] = true;
            gameRoom.submitBotCommand(seat, next.get());
        }
    }

    /** D21: every seat ready (or done for the round) -> resolves now, without waiting for the timer. */
    private void tryResolveIfAllReady(GameState state) {
        for (int seat = 0; seat < seatCount; seat++) {
            if (!isSeatReady(seat)) {
                return;
            }
        }
        tryEnqueueResolve(state);
    }

    private boolean isSeatReady(int seat) {
        if (disconnected[seat]) {
            return true;
        }
        if (bots.containsKey(seat)) {
            return !botPending[seat];
        }
        return ready[seat];
    }

    private void onWindowTimerFired() {
        synchronized (this) {
            tryEnqueueResolve(gameRoom.state());
        }
    }

    /** The shared guard (Do section) that makes sure ResolveRound is enqueued exactly once per round. */
    private void tryEnqueueResolve(GameState state) {
        if (state.phase() != GamePhase.WINDOW || resolveEnqueuedThisRound) {
            return;
        }
        resolveEnqueuedThisRound = true;
        gameRoom.submitSystemCommand(new ResolveRound());
    }

    private void onObjectiveTimeout() {
        synchronized (this) {
            GameState state = gameRoom.state();
            if (state.phase() != GamePhase.SETUP) {
                return;
            }
            int kept = ruleset.objectives().keptPerPlayer();
            for (PlayerState player : state.players()) {
                if (!player.hasChosenObjectives()) {
                    List<String> keptIds = player.dealtObjectives().stream()
                            .limit(kept)
                            .map(ObjectiveCard::id)
                            .toList();
                    gameRoom.submitSystemCommand(new ChooseObjectives(player.seat(), keptIds));
                }
            }
        }
    }

    private void onFinishedOnce() {
        if (finished) {
            return;
        }
        finished = true;
        onFinished.run();
    }

    private void setPhaseEndsAt(Instant at) {
        phaseEndsAt = at;
        roomVersion++;
    }

    private void validateSeat(int seat) {
        if (seat < 0 || seat >= seatCount) {
            throw new IllegalArgumentException("seat must be between 0 and " + (seatCount - 1) + ": " + seat);
        }
    }
}
