package citytrade.server.room;

import citytrade.engine.ruleset.Ruleset;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry for the in-memory rooms used by the first server milestone. */
public final class RoomRegistry {

    private static final String ROOM_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TOKEN_BYTES = 32;

    private final Ruleset ruleset;
    private final Clock clock;
    private final RoomCodeGenerator codeGenerator;
    private final TokenGenerator tokenGenerator;
    private final java.util.function.LongSupplier seedGenerator;
    private final Duration finishedRoomTtl;
    private final Duration lobbyIdleTtl;
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public RoomRegistry(Ruleset ruleset, Clock clock, RoomCodeGenerator codeGenerator,
            TokenGenerator tokenGenerator, java.util.function.LongSupplier seedGenerator,
            Duration finishedRoomTtl, Duration lobbyIdleTtl) {
        this.ruleset = Objects.requireNonNull(ruleset);
        this.clock = Objects.requireNonNull(clock);
        this.codeGenerator = Objects.requireNonNull(codeGenerator);
        this.tokenGenerator = Objects.requireNonNull(tokenGenerator);
        this.seedGenerator = Objects.requireNonNull(seedGenerator);
        this.finishedRoomTtl = requireNonNegative(finishedRoomTtl, "finishedRoomTtl");
        this.lobbyIdleTtl = requireNonNegative(lobbyIdleTtl, "lobbyIdleTtl");
    }

    public static RoomRegistry secure(Ruleset ruleset, Clock clock, SecureRandom random,
            Duration finishedRoomTtl, Duration lobbyIdleTtl) {
        Objects.requireNonNull(random);
        RoomCodeGenerator codes = () -> {
            StringBuilder code = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                code.append(ROOM_ALPHABET.charAt(random.nextInt(ROOM_ALPHABET.length())));
            }
            return code.toString();
        };
        TokenGenerator tokens = () -> {
            byte[] bytes = new byte[TOKEN_BYTES];
            random.nextBytes(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        };
        return new RoomRegistry(ruleset, clock, codes, tokens, random::nextLong, finishedRoomTtl, lobbyIdleTtl);
    }

    public RoomCreation create(String nickname) {
        String validNickname = validNickname(nickname);
        synchronized (rooms) {
            String code;
            do {
                code = normalizeCode(codeGenerator.nextCode());
                Room closed = rooms.get(code);
                if (closed != null && closed.status() == RoomStatus.CLOSED) {
                    rooms.remove(code, closed);
                }
            } while (rooms.containsKey(code));
            String token = tokenGenerator.nextToken();
            Room room = new Room(UUID.randomUUID(), code, validNickname, TokenSecurity.hash(token), ruleset, clock);
            rooms.put(code, room);
            return new RoomCreation(code, 0, token);
        }
    }

    public RoomCreation join(String code, String nickname) {
        String validNickname = validNickname(nickname);
        Room room = require(code);
        String token = tokenGenerator.nextToken();
        int seat;
        synchronized (room) {
            if (room.status() != RoomStatus.LOBBY) {
                throw new RoomException(RoomErrorCode.ROOM_NOT_JOINABLE, "room is not accepting players");
            }
            seat = room.addHuman(validNickname, TokenSecurity.hash(token));
        }
        return new RoomCreation(room.roomCode(), seat, token);
    }

    public RoomSnapshot snapshot(String code) {
        return require(code).snapshot();
    }

    public int addBot(String code, String hostToken, BotType type) {
        Room room = require(code);
        requireHost(room, hostToken);
        return room.addBot(Objects.requireNonNull(type));
    }

    public void removeSeat(String code, String hostToken, int seat) {
        Room room = require(code);
        requireHost(room, hostToken);
        room.removeSeat(seat);
    }

    public RoomSnapshot start(String code, String hostToken) {
        Room room = require(code);
        requireHost(room, hostToken);
        room.start(seedGenerator);
        return room.snapshot();
    }

    /**
     * Finds the room and seat that own {@code token} (Architecture 6.6: identity comes only from the
     * connection). Scans every non-closed room's seats; {@code T22}'s WebSocket HELLO is the only caller,
     * since the reconnect token - not a room code - is all a client sends to open the session.
     */
    public Optional<SeatToken> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        for (Room room : rooms.values()) {
            if (room.status() == RoomStatus.CLOSED) {
                continue;
            }
            for (int seat = 0; seat < Room.SEAT_COUNT; seat++) {
                if (room.tokenMatches(seat, token)) {
                    return Optional.of(new SeatToken(room, seat));
                }
            }
        }
        return Optional.empty();
    }

    /** One seat's identity, resolved from a bearer token by {@link #findByToken}. */
    public record SeatToken(Room room, int seat) {
        public SeatToken {
            Objects.requireNonNull(room);
        }
    }

    public Room require(String code) {
        String normalized = normalizeCode(code);
        Room room = rooms.get(normalized);
        if (room == null || room.status() == RoomStatus.CLOSED) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND, "room '" + normalized + "' was not found");
        }
        return room;
    }

    /** Called by the scheduled cleanup task and directly by deterministic tests. */
    public void cleanup() {
        cleanup(clock.instant());
    }

    public void cleanup(Instant now) {
        for (Map.Entry<String, Room> entry : rooms.entrySet()) {
            Room room = entry.getValue();
            if (room.closeIfExpired(now, finishedRoomTtl, lobbyIdleTtl)) {
                rooms.remove(entry.getKey(), room);
            }
        }
    }

    public int size() {
        return rooms.size();
    }

    private void requireHost(Room room, String token) {
        if (!room.tokenMatches(room.hostSeat(), token)) {
            throw new RoomException(RoomErrorCode.NOT_HOST, "only the host may perform this action");
        }
    }

    private static String validNickname(String nickname) {
        if (nickname == null || nickname.isBlank() || nickname.trim().length() > 40) {
            throw new RoomException(RoomErrorCode.INVALID_NICKNAME,
                    "nickname must contain 1 to 40 non-whitespace characters");
        }
        return nickname.trim();
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND, "room code is required");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 6) {
            throw new RoomException(RoomErrorCode.ROOM_NOT_FOUND, "room code was not found");
        }
        return normalized;
    }

    private static Duration requireNonNegative(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isNegative()) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return duration;
    }
}
