package citytrade.server.room;

import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GameState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Mutable server aggregate; game rules remain in {@code game-engine}. */
public final class Room {

    public static final int SEAT_COUNT = 4;

    private final UUID roomId;
    private final String roomCode;
    private final int hostSeat;
    private final String rulesetVersion;
    private final Ruleset ruleset;
    private final Clock clock;
    private final Instant createdAt;
    private final List<Seat> seats = new ArrayList<>();
    private final Map<Integer, String> tokenHashes = new HashMap<>();
    private RoomStatus status = RoomStatus.LOBBY;
    private Instant lastActivity;
    private Long seed;
    private GameState gameState;

    Room(UUID roomId, String roomCode, String hostNickname, String hostTokenHash, Ruleset ruleset, Clock clock) {
        this.roomId = Objects.requireNonNull(roomId);
        this.roomCode = Objects.requireNonNull(roomCode);
        this.hostSeat = 0;
        this.ruleset = Objects.requireNonNull(ruleset);
        this.rulesetVersion = ruleset.version();
        this.clock = Objects.requireNonNull(clock);
        this.createdAt = clock.instant();
        this.lastActivity = createdAt;
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            seats.add(null);
        }
        seats.set(hostSeat, Seat.human(hostNickname));
        tokenHashes.put(hostSeat, Objects.requireNonNull(hostTokenHash));
    }

    public synchronized UUID roomId() {
        return roomId;
    }

    public synchronized String roomCode() {
        return roomCode;
    }

    public synchronized int hostSeat() {
        return hostSeat;
    }

    public synchronized String rulesetVersion() {
        return rulesetVersion;
    }

    public synchronized RoomStatus status() {
        return status;
    }

    public synchronized Instant createdAt() {
        return createdAt;
    }

    public synchronized Instant lastActivity() {
        return lastActivity;
    }

    public synchronized OptionalLong seed() {
        return seed == null ? OptionalLong.empty() : OptionalLong.of(seed);
    }

    public synchronized Optional<GameState> gameState() {
        return Optional.ofNullable(gameState);
    }

    /** Seat -> bot type, for the seats currently occupied by a bot; used to build the bots that play them. */
    public synchronized Map<Integer, BotType> botSeats() {
        Map<Integer, BotType> result = new LinkedHashMap<>();
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            Seat value = seats.get(seat);
            if (value != null && value.botType != null) {
                result.put(seat, value.botType);
            }
        }
        return result;
    }

    public synchronized boolean isFull() {
        return seats.stream().allMatch(Objects::nonNull);
    }

    public synchronized int humanCount() {
        return (int) seats.stream().filter(seat -> seat != null && seat.botType == null).count();
    }

    public synchronized boolean tokenMatches(int seat, String token) {
        return seat >= 0 && seat < SEAT_COUNT && TokenSecurity.matches(token, tokenHashes.get(seat));
    }

    /** Used by security-focused tests; this exposes only the digest, never the bearer token. */
    public synchronized String tokenHashForSeat(int seat) {
        return tokenHashes.get(seat);
    }

    synchronized int addHuman(String nickname, String tokenHash) {
        requireLobby();
        int seat = nextEmptySeat();
        if (seat < 0) {
            throw new RoomException(RoomErrorCode.ROOM_FULL, "room is full");
        }
        seats.set(seat, Seat.human(nickname));
        tokenHashes.put(seat, tokenHash);
        touch();
        return seat;
    }

    synchronized int addBot(BotType type) {
        requireLobby();
        int seat = nextEmptySeat();
        if (seat < 0) {
            throw new RoomException(RoomErrorCode.ROOM_FULL, "room is full");
        }
        seats.set(seat, Seat.bot(type));
        touch();
        return seat;
    }

    synchronized void removeSeat(int seat) {
        requireLobby();
        validateSeat(seat);
        if (seat == hostSeat) {
            throw new RoomException(RoomErrorCode.HOST_CANNOT_BE_REMOVED, "the host cannot be removed");
        }
        if (seats.get(seat) == null) {
            throw new RoomException(RoomErrorCode.INVALID_SEAT, "seat " + seat + " is empty");
        }
        seats.set(seat, null);
        tokenHashes.remove(seat);
        touch();
    }

    synchronized void start(LongSupplier seedGenerator) {
        if (status != RoomStatus.LOBBY) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_IN_LOBBY, "room is not in LOBBY");
        }
        if (!isFull()) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FULL, "all four seats must be filled before starting");
        }
        if (humanCount() < 1) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FULL, "at least one human seat is required");
        }
        long newSeed = Objects.requireNonNull(seedGenerator).getAsLong();
        GameState newGameState = GameSetup.create(newSeed, ruleset);
        seed = newSeed;
        gameState = newGameState;
        status = RoomStatus.ACTIVE;
        touch();
    }

    public synchronized void finish() {
        if (status != RoomStatus.ACTIVE) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_IN_LOBBY, "only an ACTIVE room can finish");
        }
        status = RoomStatus.FINISHED;
        touch();
    }

    public synchronized void close() {
        if (status != RoomStatus.LOBBY && status != RoomStatus.FINISHED) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_CLOSABLE, "only a LOBBY or FINISHED room can be closed");
        }
        status = RoomStatus.CLOSED;
        touch();
    }

    /**
     * Atomically checks expiry against the current status and closes the room, so a concurrent
     * {@link #start(LongSupplier)} cannot race with cleanup and close an ACTIVE room.
     */
    synchronized boolean closeIfExpired(Instant now, Duration finishedTtl, Duration lobbyIdleTtl) {
        boolean finished = status == RoomStatus.FINISHED;
        boolean lobby = status == RoomStatus.LOBBY;
        if (!finished && !lobby) {
            return false;
        }
        Duration ttl = finished ? finishedTtl : lobbyIdleTtl;
        if (lastActivity.plus(ttl).isAfter(now)) {
            return false;
        }
        status = RoomStatus.CLOSED;
        touch();
        return true;
    }

    public synchronized RoomSnapshot snapshot() {
        List<RoomSeat> publicSeats = new ArrayList<>(SEAT_COUNT);
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            Seat value = seats.get(seat);
            publicSeats.add(value == null ? new RoomSeat(seat, null, null)
                    : new RoomSeat(seat, value.nickname, value.botType));
        }
        return new RoomSnapshot(roomCode, status, publicSeats, rulesetVersion, createdAt);
    }

    private int nextEmptySeat() {
        for (int seat = 0; seat < SEAT_COUNT; seat++) {
            if (seats.get(seat) == null) {
                return seat;
            }
        }
        return -1;
    }

    private void requireLobby() {
        if (status != RoomStatus.LOBBY) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_IN_LOBBY, "room is not in LOBBY");
        }
    }

    private static void validateSeat(int seat) {
        if (seat < 0 || seat >= SEAT_COUNT) {
            throw new RoomException(RoomErrorCode.INVALID_SEAT, "seat must be between 0 and 3");
        }
    }

    private void touch() {
        lastActivity = clock.instant();
    }

    private static final class Seat {
        private final String nickname;
        private final BotType botType;

        private Seat(String nickname, BotType botType) {
            this.nickname = nickname;
            this.botType = botType;
        }

        private static Seat human(String nickname) {
            return new Seat(nickname, null);
        }

        private static Seat bot(BotType type) {
            return new Seat(null, type);
        }
    }
}
