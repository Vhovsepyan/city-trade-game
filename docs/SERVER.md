# SERVER.md - running the server, command log persistence (T24)

`game-server` is a Spring Boot module. See also `docs/PROTOCOL.md` (WebSocket) and `docs/VIEW.md`
(`PlayerGameView`).

## Running in memory (default)

```
./gradlew :game-server:bootRun
```

No database is needed (Architecture 7.1: "First online private-room prototype: yes, PostgreSQL" is about
durability across restarts, not about being usable at all). With no `postgres` Spring profile active,
`ServerConfiguration.inMemoryMatchLog()` provides an `InMemoryMatchLog`: every match's command log
(Architecture 7) lives only in the running process and is lost on restart, exactly like every other
in-memory server state (rooms, `GameRoom`s).

## Running with PostgreSQL (`postgres` profile)

Activate the `postgres` Spring profile and point `spring.datasource.*` at a real PostgreSQL instance:

```
./gradlew :game-server:bootRun --args='--spring.profiles.active=postgres' \
    -Dspring.datasource.url=jdbc:postgresql://localhost:5432/citytrade \
    -Dspring.datasource.username=citytrade \
    -Dspring.datasource.password=citytrade
```

(or set `SPRING_PROFILES_ACTIVE=postgres` and the three `SPRING_DATASOURCE_*` environment variables).

Under this profile, `PostgresMatchLog` (`citytrade.server.persistence`, `@Profile("postgres")`,
component-scanned) replaces the in-memory default. Flyway migrates the schema automatically on startup from
`game-server/src/main/resources/db/migration/V1__init.sql` (Architecture 7.3):

- `matches` - id, seed, ruleset version, status, created/finished timestamps
- `match_players` - one row per seat: nickname (null for a bot), city, whether it is a bot
- `match_commands` - one row per processed command, in `command_sequence` order: origin (PLAYER/BOT/SYSTEM),
  actor seat (empty for SYSTEM), `command_type` + JSONB `payload` (the exact command, seat included),
  `command_schema_version` (currently `1`), outcome (ACCEPTED/REJECTED/REFUSED), rejection code, and the
  resulting `stateVersion`
- `match_results` - final scores and winner seats, written once the match finishes

`READY` is server-only metadata (Architecture 6.9) and is never logged; every other processed command is,
including SYSTEM commands (`StartRound`, `ResolveRound`, objective-timeout choices) and rejected or refused
ones - stored for debugging, but skipped on replay (Architecture 7.2).

## Replay

`ReplayService.replay(MatchRecord, List<LoggedCommand>, Ruleset)` rebuilds the final `GameState` for a
match purely from what `MatchLog` stored:

```
seed + rulesetVersion -> GameSetup.create(...)
then every ACCEPTED command, sorted by commandSequence, applied through GameEngine.apply
```

Never by timestamp or row-insertion order - `commandSequence` is the only order that matters, and
`MatchLog.loadCommands` always returns it sorted regardless of how the rows were stored. Rejected and
refused commands are skipped: they never changed state live, so replaying them would diverge from the
original game.

## Tests

`PostgresMatchLogTest` and any other `postgres`-profile test use `io.zonky.test:embedded-postgres`, a real
PostgreSQL binary downloaded and run per test JVM - no Docker, no external service required.
