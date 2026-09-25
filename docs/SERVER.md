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

`M3AcceptanceTest` (T24a) drives one full 14-round game through the real REST + WebSocket server on a
random port: room creation, join, add-bot and start over REST; objective choice (one seat via the D22
timeout); concurrent WebSocket commands; per-seat privacy; disconnect/reconnect; `commandId` idempotency;
an all-READY early round end; a window-timer-driven round end; the game running to FINISHED; and a
command-log replay (`ReplayService`) that reproduces the exact final `GameState`.

## Try it yourself: 1 human + 3 bots

No build step needed beyond the wrapper; every command below is copy-pasteable as-is (`curl` for REST,
`node` for the WebSocket connection - Node 22 has `fetch` and `WebSocket` built in, no `npm install`).

1. Start the server. `--game.window-duration`/`--game.objective-choice-timeout` are shortened here only so
   a full 14-round game finishes in a few minutes instead of the ~28 minutes the 120s/60s production
   defaults would take with nobody sending `READY`; drop them to use the real defaults instead.

   ```
   ./gradlew :game-server:bootRun --args='--game.window-duration=15s --game.objective-choice-timeout=15s'
   ```

2. In another terminal, create a room, add 3 bots, and start the game:

   ```
   curl -s -X POST http://localhost:8080/rooms -H "Content-Type: application/json" -d "{\"nickname\":\"you\"}"
   ```

   Copy the `roomCode` and `token` from the response into the next commands (`ROOM_CODE`, `TOKEN`):

   ```
   curl -s -X POST http://localhost:8080/rooms/ROOM_CODE/bots -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" -d "{\"type\":\"BASELINE\"}"
   curl -s -X POST http://localhost:8080/rooms/ROOM_CODE/bots -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" -d "{\"type\":\"BASELINE\"}"
   curl -s -X POST http://localhost:8080/rooms/ROOM_CODE/bots -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" -d "{\"type\":\"BASELINE\"}"
   curl -s -X POST http://localhost:8080/rooms/ROOM_CODE/start -H "Authorization: Bearer TOKEN"
   ```

3. Connect as the human seat and watch the game play out. Save the script below as `play.mjs` (replace
   `TOKEN` with your own), then run `node play.mjs`. It HELLOs, keeps the first 2 dealt objectives (the
   default ruleset's `objectives.keptPerPlayer`), sends `READY` every round so it never waits out the
   window timer, logs every server message, and exits once `GAME_FINISHED` arrives:

   ```js
   const token = "TOKEN";
   const ws = new WebSocket("ws://localhost:8080/ws");
   let n = 0;

   ws.addEventListener("open", () => {
     ws.send(JSON.stringify({ protocolVersion: 1, token }));
   });

   ws.addEventListener("message", (event) => {
     const msg = JSON.parse(event.data);
     console.log(msg.type, JSON.stringify(msg).slice(0, 200));

     if (msg.view && msg.view.phase === "SETUP" && msg.view.own.keptObjectives.length === 0) {
       const ids = msg.view.own.dealtObjectives.slice(0, 2).map((o) => o.id);
       ws.send(JSON.stringify({
         protocolVersion: 1, commandId: `c-${++n}`, commandType: "CHOOSE_OBJECTIVES",
         payload: { keptObjectiveIds: ids },
       }));
     }
     if (msg.view && msg.view.phase === "WINDOW") {
       ws.send(JSON.stringify({
         protocolVersion: 1, commandId: `c-${++n}`, commandType: "READY", payload: { ready: true },
       }));
     }
     if (msg.type === "GAME_FINISHED") {
       console.log("finished:", JSON.stringify(msg.event));
       ws.close();
     }
   });
   ```

   This script only ever chooses objectives and readies up; see `docs/PROTOCOL.md` for every other command
   type (buying/selling, trades, contracts, projects, bids) a real client would send instead.
