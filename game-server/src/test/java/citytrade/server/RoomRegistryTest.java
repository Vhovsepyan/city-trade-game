package citytrade.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.ruleset.Ruleset;
import citytrade.ruleset.json.RulesetLoader;
import citytrade.server.room.BotType;
import citytrade.server.room.RoomCodeGenerator;
import citytrade.server.room.Room;
import citytrade.server.room.RoomCreation;
import citytrade.server.room.RoomErrorCode;
import citytrade.server.room.RoomException;
import citytrade.server.room.RoomRegistry;
import citytrade.server.room.RoomSnapshot;
import citytrade.server.room.RoomStatus;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class RoomRegistryTest {

    private static final Instant START = Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void createsHostJoinsFourSeatsAndKeepsTokenPrivate() {
        MutableClock clock = new MutableClock(START);
        RoomRegistry registry = registry(clock, () -> "ABCDEF");

        RoomCreation host = registry.create(" Host ");
        RoomCreation second = registry.join(host.roomCode(), "second");
        RoomCreation third = registry.join(host.roomCode(), "third");
        RoomCreation fourth = registry.join(host.roomCode(), "fourth");

        assertEquals(0, host.seat());
        assertEquals(List.of(1, 2, 3), List.of(second.seat(), third.seat(), fourth.seat()));
        assertThrows(RoomException.class, () -> registry.join(host.roomCode(), "fifth"));
        assertTrue(host.token().length() >= 32);

        RoomSnapshot snapshot = registry.snapshot(host.roomCode());
        assertEquals(RoomStatus.LOBBY, snapshot.status());
        assertEquals("Host", snapshot.seats().getFirst().nickname());
        assertNotNull(snapshot.createdAt());
        assertFalse(snapshot.toString().contains(host.token()));
        assertNotEquals(host.token(), registry.require(host.roomCode()).tokenHashForSeat(0));
        assertTrue(registry.require(host.roomCode()).tokenMatches(0, host.token()));
    }

    @Test
    void hostMayAddAndRemoveBotsButNonHostCannot() {
        MutableClock clock = new MutableClock(START);
        RoomRegistry registry = registry(clock, () -> "ABCDEF");
        RoomCreation host = registry.create("host");

        assertThrows(RoomException.class, () -> registry.addBot(host.roomCode(), "wrong", BotType.BASELINE));
        assertEquals(1, registry.addBot(host.roomCode(), host.token(), BotType.BASELINE));
        assertEquals(BotType.BASELINE, registry.snapshot(host.roomCode()).seats().get(1).botType());
        registry.removeSeat(host.roomCode(), host.token(), 1);
        assertFalse(registry.snapshot(host.roomCode()).seats().get(1).occupied());
    }

    @Test
    void findByTokenResolvesTheOwningRoomAndSeat() {
        MutableClock clock = new MutableClock(START);
        AtomicLong counter = new AtomicLong();
        RoomRegistry registry = new RoomRegistry(ruleset(), clock, () -> "ABCDEF",
                () -> "seat-token-" + counter.getAndIncrement(), () -> 1L, Duration.ofMinutes(30), Duration.ofHours(2));
        RoomCreation host = registry.create("host");
        RoomCreation second = registry.join(host.roomCode(), "second");

        RoomRegistry.SeatToken hostSeat = registry.findByToken(host.token()).orElseThrow();
        assertEquals(0, hostSeat.seat());
        assertEquals(host.roomCode(), hostSeat.room().roomCode());

        RoomRegistry.SeatToken secondSeat = registry.findByToken(second.token()).orElseThrow();
        assertEquals(1, secondSeat.seat());

        assertTrue(registry.findByToken("not-a-real-token").isEmpty());
        assertTrue(registry.findByToken(null).isEmpty());
    }

    @Test
    void findByTokenIgnoresAClosedRoomsToken() {
        MutableClock clock = new MutableClock(START);
        AtomicLong counter = new AtomicLong();
        RoomRegistry registry = new RoomRegistry(ruleset(), clock, () -> "ABCDEF",
                () -> "seat-token-" + counter.getAndIncrement(), () -> 1L, Duration.ofMinutes(30), Duration.ofHours(2));
        RoomCreation host = registry.create("host");
        assertTrue(registry.findByToken(host.token()).isPresent());

        // A room can be CLOSED while still present in the registry's map (e.g. cleanup's own two-step
        // close-then-remove, mid-way) - findByToken must not resolve a token for it either way.
        registry.require(host.roomCode()).close();

        assertTrue(registry.findByToken(host.token()).isEmpty());
    }

    @Test
    void startRequiresFourSeatsAndCreatesSeededEngineState() {
        MutableClock clock = new MutableClock(START);
        AtomicLong seeds = new AtomicLong(99);
        RoomRegistry registry = new RoomRegistry(ruleset(), clock, () -> "ABCDEF",
                () -> "token-token-token-token-token-token-token-token", seeds::getAndIncrement,
                Duration.ofMinutes(30), Duration.ofHours(2));
        RoomCreation host = registry.create("host");

        RoomException notFull = assertThrows(RoomException.class, () -> registry.start(host.roomCode(), host.token()));
        assertEquals(RoomErrorCode.ROOM_NOT_FULL, notFull.code());
        registry.addBot(host.roomCode(), host.token(), BotType.BASELINE);
        registry.addBot(host.roomCode(), host.token(), BotType.TRADER);
        registry.join(host.roomCode(), "human");

        RoomSnapshot started = registry.start(host.roomCode(), host.token());
        assertEquals(RoomStatus.ACTIVE, started.status());
        assertEquals("prototype-002", started.rulesetVersion());
        assertEquals(99, registry.require(host.roomCode()).seed().orElseThrow());
        assertEquals(4, registry.require(host.roomCode()).gameState().orElseThrow().players().size());
        RoomException notJoinable = assertThrows(RoomException.class, () -> registry.join(host.roomCode(), "late"));
        assertEquals(RoomErrorCode.ROOM_NOT_JOINABLE, notJoinable.code());
    }

    @Test
    void unknownRoomCodeIsRoomNotFound() {
        RoomRegistry registry = registry(new MutableClock(START), () -> "ABCDEF");

        assertRoomNotFound(() -> registry.snapshot("ZZZZZZ"));
        assertRoomNotFound(() -> registry.join("ZZZZZZ", "nick"));
        assertRoomNotFound(() -> registry.addBot("ZZZZZZ", "token", BotType.BASELINE));
        assertRoomNotFound(() -> registry.removeSeat("ZZZZZZ", "token", 1));
        assertRoomNotFound(() -> registry.start("ZZZZZZ", "token"));
    }

    @Test
    void onlyHostCanStartOrRemoveSeats() {
        MutableClock clock = new MutableClock(START);
        RoomRegistry registry = registry(clock, () -> "ABCDEF");
        RoomCreation host = registry.create("host");
        registry.join(host.roomCode(), "second");
        registry.join(host.roomCode(), "third");
        registry.join(host.roomCode(), "fourth");

        RoomException removeDenied = assertThrows(RoomException.class,
                () -> registry.removeSeat(host.roomCode(), "wrong", 1));
        assertEquals(RoomErrorCode.NOT_HOST, removeDenied.code());

        RoomException startDenied = assertThrows(RoomException.class,
                () -> registry.start(host.roomCode(), "wrong"));
        assertEquals(RoomErrorCode.NOT_HOST, startDenied.code());

        RoomSnapshot started = registry.start(host.roomCode(), host.token());
        assertEquals(RoomStatus.ACTIVE, started.status());
    }

    @Test
    void botActionsRejectedAfterStart() {
        MutableClock clock = new MutableClock(START);
        RoomRegistry registry = registry(clock, () -> "ABCDEF");
        RoomCreation host = registry.create("host");
        registry.join(host.roomCode(), "second");
        registry.join(host.roomCode(), "third");
        registry.join(host.roomCode(), "fourth");
        registry.start(host.roomCode(), host.token());

        RoomException addDenied = assertThrows(RoomException.class,
                () -> registry.addBot(host.roomCode(), host.token(), BotType.BASELINE));
        assertEquals(RoomErrorCode.ROOM_NOT_IN_LOBBY, addDenied.code());

        RoomException removeDenied = assertThrows(RoomException.class,
                () -> registry.removeSeat(host.roomCode(), host.token(), 1));
        assertEquals(RoomErrorCode.ROOM_NOT_IN_LOBBY, removeDenied.code());
    }

    @Test
    void closeRejectsInvalidTransitionsFromActiveOrClosed() {
        MutableClock clock = new MutableClock(START);
        RoomRegistry registry = registry(clock, () -> "ABCDEF");
        RoomCreation host = registry.create("host");
        registry.join(host.roomCode(), "one");
        registry.join(host.roomCode(), "two");
        registry.join(host.roomCode(), "three");
        registry.start(host.roomCode(), host.token());
        Room active = registry.require(host.roomCode());

        RoomException fromActive = assertThrows(RoomException.class, active::close);
        assertEquals(RoomErrorCode.ROOM_NOT_CLOSABLE, fromActive.code());
        assertEquals(RoomStatus.ACTIVE, active.status());

        active.finish();
        active.close();
        assertEquals(RoomStatus.CLOSED, active.status());
        RoomException fromClosed = assertThrows(RoomException.class, active::close);
        assertEquals(RoomErrorCode.ROOM_NOT_CLOSABLE, fromClosed.code());
    }

    @Test
    void cleanupCannotCloseARoomThatIsConcurrentlyStarted() throws Exception {
        for (int i = 0; i < 200; i++) {
            MutableClock clock = new MutableClock(START);
            RoomRegistry registry = registry(clock, () -> "ABCDEF");
            RoomCreation host = registry.create("host");
            registry.join(host.roomCode(), "one");
            registry.join(host.roomCode(), "two");
            registry.join(host.roomCode(), "three");
            clock.advance(Duration.ofHours(2));

            CyclicBarrier barrier = new CyclicBarrier(2);
            AtomicReference<RoomException> startFailure = new AtomicReference<>();
            Thread starter = new Thread(() -> {
                await(barrier);
                try {
                    registry.start(host.roomCode(), host.token());
                } catch (RoomException e) {
                    startFailure.set(e);
                }
            });
            Thread cleaner = new Thread(() -> {
                await(barrier);
                registry.cleanup(clock.instant());
            });
            starter.start();
            cleaner.start();
            starter.join();
            cleaner.join();

            if (startFailure.get() == null) {
                // The room must never be silently closed once a game has started on it.
                assertEquals(RoomStatus.ACTIVE, registry.require(host.roomCode()).status());
            } else {
                RoomErrorCode code = startFailure.get().code();
                assertTrue(code == RoomErrorCode.ROOM_NOT_FOUND || code == RoomErrorCode.ROOM_NOT_IN_LOBBY,
                        "unexpected failure code: " + code);
            }
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertRoomNotFound(Executable action) {
        RoomException exception = assertThrows(RoomException.class, action);
        assertEquals(RoomErrorCode.ROOM_NOT_FOUND, exception.code());
    }

    @Test
    void retriesRoomCodeCollision() {
        Iterator<String> codes = List.of("ABCDEF", "ABCDEF", "GHJKLM").iterator();
        RoomRegistry registry = registry(new MutableClock(START), codes::next);

        assertEquals("ABCDEF", registry.create("one").roomCode());
        assertEquals("GHJKLM", registry.create("two").roomCode());
    }

    @Test
    void cleanupClosesExpiredLobby() {
        MutableClock clock = new MutableClock(START);
        RoomRegistry registry = registry(clock, () -> "ABCDEF");
        RoomCreation lobby = registry.create("lobby");
        clock.advance(Duration.ofHours(2));
        registry.cleanup();
        assertEquals(0, registry.size());
        assertThrows(RoomException.class, () -> registry.snapshot(lobby.roomCode()));
    }

    @Test
    void cleanupClosesFinishedRoomAfterItsOwnTtl() {
        MutableClock clock = new MutableClock(START);
        RoomRegistry registry = registry(clock, () -> "ABCDEF");
        RoomCreation host = registry.create("host");
        registry.join(host.roomCode(), "one");
        registry.join(host.roomCode(), "two");
        registry.join(host.roomCode(), "three");
        registry.start(host.roomCode(), host.token());
        registry.require(host.roomCode()).finish();

        clock.advance(Duration.ofMinutes(30));
        registry.cleanup();
        assertEquals(0, registry.size());
    }

    private static RoomRegistry registry(MutableClock clock, RoomCodeGenerator codes) {
        return new RoomRegistry(ruleset(), clock, codes,
                () -> "token-token-token-token-token-token-token-token", () -> 1L,
                Duration.ofMinutes(30), Duration.ofHours(2));
    }

    private static Ruleset ruleset() {
        return RulesetLoader.load(Path.of(System.getProperty("rulesets.dir", "rulesets"), "prototype-002.json"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
