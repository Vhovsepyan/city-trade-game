# PROGRESS.md

Short log for the next session. Newest entry on top. Max ~10 lines per entry.
Keep only the last 10 entries; summarize older ones in one line under "Earlier".

## Current state
- Milestone: M3 server started (T19 room/lobby REST; T20 GameRoom serial queue; T20b round flow;
  T21 PlayerGameView + privacy projector; T22 WebSocket protocol; T23 commandId idempotency + reconnect added)
- Next task: T22/T23 review; then T24 command log persistence + replay
- Target rulesets: prototype-001 and prototype-002 (`rulesets/`)
- `.claude/settings.json` has an uncommitted owner change from BEFORE T02 (see git status at
  session start). It is not part of T02; agents do not touch or commit it.

## Environment (checked by T00, 2026-09-23)
- OK Java for Gradle: default `java` = OpenJDK 17.0.12 (OpenLogic),
  JAVA_HOME=`C:\Program Files\OpenLogic\jdk-17.0.12.7-hotspot\`.
- JDK 25 found: `C:\Users\vaheh\.jdks\corretto-25.0.4` (also `corretto-21.0.10`).
  Not the default; the T01 toolchain can use it.
- OK `git --version`: 2.45.1.windows.1
- OK `node --version`: v22.21.1
- OK `codex --version`: codex-cli 0.154.0
- Gradle: not installed globally (T01 creates the wrapper).
- OK `.gitignore` contains `.review/`, `build/`, `.gradle/`, `.idea/`.
- OK spec docs: all 3 files exist in `docs/` with the names from AGENTS.md.
- OK scripts executable. Fixed: git index mode set to 100755 (`git update-index --chmod=+x scripts/*.sh`).
- OK Codex smoke test (same flags as codex-review.sh): `.review/codex-smoke.txt` = `CODEX OK`,
  jsonl contains `input_tokens`.
- Changes outside the repository: none.
- T01: Gradle 9.7.1 zip downloaded into `.review/tools/` (git-ignored; user folder is blocked
  by the agent sandbox), used once to create the wrapper. Safe to delete.
- T01: JDK 25 is auto-detected by Gradle from `~/.jdks` (IntelliJ). No `~/.gradle/gradle.properties`
  change was needed. Foojay resolver downloads JDK 25 if it is missing.

## Questions for owner
- (none)

## Suggestions (not built)
- Sim metrics (T18 report section 5): opportunity results, per-objective and per-building rates,
  upkeep/crisis payments per resource; a bot that uses projects and contracts.

## Log

<!-- Template:
### T0X - <title> - DONE (review round N)
- What: ...
- Files: ...
- Tests: ...
- Notes / P3 items: ...
-->

### T23 - commandId idempotency + reconnect and session identity - READY (review pending)
- What: `GameRoom` now stores an outcome per `commandId` (Architecture 6.7): a new
  `submitPlayerCommand(seat, commandId, command)` overload; a duplicate `commandId` from the SAME seat returns
  the exact same `ProcessedCommand` again (no new sequence, no engine call, no `stateVersion` change - checked
  inside `process()`, which already runs single-threaded, so a plain `HashMap` needs no locking); the same
  `commandId` from a DIFFERENT seat is refused (new `CommandOutcome.Refused` path, new `ProtocolErrorCode.
  COMMAND_ID_REUSED`) without touching the original seat's stored outcome. The old 2-arg
  `submitPlayerCommand(seat, command)` (used by tests and non-WS callers) now delegates with a fresh random
  id per call, so it is never deduplicated - existing T20/T20b tests needed no changes. `ProcessedCommand`
  gained a `commandId` component (empty for BOT/SYSTEM).
- `GameWebSocketHandler`: a player command with a missing/blank `commandId` -> `INVALID_PAYLOAD`; replies now
  echo `processed.resultingStateVersion()` (this command's own outcome), not the room's live `stateVersion()`,
  so a duplicate's reply is byte-for-byte identical to the original even if the room moved on since then.
- Reconnect (Architecture 6.10): HELLO with a token whose seat already has a live connection replaces it - the
  previous `established` entry is evicted and the old session closed (`CloseStatus.NORMAL`) BEFORE its own
  `afterConnectionClosed` callback can run, so that callback finds nothing and never marks the seat
  disconnected right after it just reconnected. Genuine disconnects call `RoundFlowDriver.setDisconnected`
  (D23; the driver already had this method from T20b, just never wired to anything) and, like `READY`,
  broadcast `ROOM_UPDATE` to the room when `roomVersion` actually changed.
- Tokens invalid after CLOSED already worked (T22's `findByToken` skips CLOSED rooms); added end-to-end
  coverage of it here.
- Files: `GameRoom`, `ProcessedCommand`, `GameWebSocketHandler`, `ProtocolErrorCode`, `RoomConnections` (doc
  only), `docs/PROTOCOL.md` (commandId requirement/dedup, reconnect, `COMMAND_ID_REUSED`).
- Tests: `GameRoomCommandIdTest` (3, GameRoom-only: duplicate same-seat commandId calls the engine once and
  returns the same instance incl. for a Rejected outcome; a different-seat reuse is refused and does not
  disturb the original), `GameWebSocketReconnectAndIdempotencyTest` (7, real WebSocket client: duplicate
  commandId bought once with identical replies; cross-seat reuse rejected; a new connection for the same seat
  closes the old one; disconnect+reconnect gets the same seat and the CURRENT stateVersion; a disconnected
  seat does not block an all-READY early round end; a CLOSED room's token is refused; a Logback `ListAppender`
  capture at DEBUG across HELLO/reconnect never contains either seat's token).
- Verification: `./gradlew build` green (all modules); `:game-server:test` rerun clean twice in a row.
- Round 1 fix (review R1-P2-1): `handleHello` registered the reconnecting session and broadcast `ROOM_UPDATE`
  (disconnected-status change) before sending that same session its `FULL_SNAPSHOT`, so a reconnecting client
  could receive `ROOM_UPDATE` first. Reordered so `sendRaw(session, snapshotFor(seat))` happens before the
  `broadcastRoomUpdate()` call.
- Round 2 fix (review R2-P1-1): a duplicate `commandId`'s reply reused `processed.resultingStateVersion()`
  (correctly cached at the original processing) but still re-read `ctx.driver().roomVersion()` live, so a
  READY/disconnect/round-metadata change between the original call and the retry made the two replies differ -
  `roomVersion` is `RoundFlowDriver` state, not part of `ProcessedCommand`. Added a
  `GameWebSocketHandler`-local cache, `Map<CommandReplyKey(roomCode, seat, commandId), CommandReply(sequence,
  ServerMessage)>`: the first reply for a `commandId` is cached together with its `ProcessedCommand.sequence()`;
  a later call whose `ProcessedCommand` has that same `sequence()` (i.e. GameRoom's own dedup returned the
  identical record) replays the cached `ServerMessage` byte-for-byte instead of recomputing `roomVersion`.
  A different seat reusing the `commandId` gets its own cache key, so the existing `COMMAND_ID_REUSED` refusal
  path is unaffected.
- Tests (+1): `GameWebSocketReconnectAndIdempotencyTest.duplicateCommandIdReplyStaysIdenticalEvenAfterRoomVersionChanges`
  - bumps `roomVersion` via seat 1's READY in between two identical submissions from seat 0, asserts the
  second reply's `roomVersion` still equals the first's.
- Round 3 fixes (review R3-P1-1/2/3): three concurrency/identity gaps that only reproduce under real races
  (existing tests are sequential, so none caught them):
  - R3-P1-1: `handleHello`'s reconnect swap (evict previous session, register the new one,
    `setDisconnected(false)`) and `afterConnectionClosed`'s own "mark seat disconnected" path were not
    atomic with each other. A genuine close of the JUST-REPLACED old session, landing between the new
    session's registration and its eviction, could run `setDisconnected(seat, true)` right after the
    reconnect had set it `false`. Fixed by adding a per-`(roomCode, seat)` lock (`seatLocks` +
    `seatLock()`); both `handleHello`'s swap and `afterConnectionClosed`'s check-and-mark now run inside
    that lock, so one always completes in full before the other starts. `afterConnectionClosed` also now
    checks `RoomConnections.unregister`'s return value (changed from `void` to `boolean`: true only if the
    closing session was still the one currently registered for the seat) instead of ignoring it, and skips
    `setDisconnected(true)` entirely when a reconnect already replaced it.
  - R3-P1-2: two concurrent duplicate submissions of the same `commandId` could both miss the (then-empty)
    `commandReplies` cache and independently read `ctx.driver().roomVersion()`, producing two different
    "canonical" replies for what must be one identical outcome, with the later `.put()` silently winning.
    Fixed by installing the reply with `commandReplies.computeIfAbsent(replyKey, ...)`: only the winning
    thread's mapping function runs, so exactly one `roomVersion` read is ever cached, and every caller
    (winner and duplicates) gets that same `ServerMessage` back.
  - R3-P1-3: `commandReplies` (and `CommandReplyKey`) was keyed by `roomCode`, but `RoomRegistry.create`
    reuses a CLOSED room's code for a brand-new room. A stale reply could theoretically resurface if a new
    room happened to reuse a code together with a colliding seat+commandId. Switched the key to `Room`'s
    stable `roomId` (`UUID`, already existed, never reused) instead of `roomCode`.
  - Files: `GameWebSocketHandler`, `RoomConnections`.
  - Verification: `./gradlew build` green; `GameWebSocketReconnectAndIdempotencyTest` +
    `GameRoomCommandIdTest` + `GameWebSocketProtocolTest` rerun clean.
  - Note: none of the three races have a deterministic regression test (they need genuine thread
    interleaving, which the existing single-threaded-per-session WebSocket test harness cannot force); the
    fixes were verified by code inspection of the now-atomic critical sections instead, per the reviewer's
    own TEST ASSESSMENT ("tests do not cover concurrent reconnect/duplicate requests or room-code reuse").
- Round 4 fix (review R4-P1-1): `GameWebSocketHandler`'s `broadcasters` map and `RoomConnections` (both new
  in T22) were still keyed by `roomCode`, unlike `commandReplies` (already fixed to `roomId` in round 3 -
  R3-P1-3). Since `RoomRegistry.create` reuses a CLOSED room's code for a brand-new room, a HELLO into that
  new room's `computeIfAbsent` found the OLD room's `RoomBroadcaster` still there (never removed) and reused
  it unchanged: the new room's clients would get the old, finished game's stale/private state from
  `snapshotFor`, and would never see their own game's updates at all, since the old broadcaster was never
  wired as a listener on the new `GameRoom`. Fixed by keying both `broadcasters` and `RoomConnections` by
  `Room.roomId()` (`UUID`, stable, never reused) instead of `roomCode`, the same pattern R3-P1-3 already
  established for `commandReplies`.
  - Files: `GameWebSocketHandler`, `RoomConnections`, `RoomBroadcaster`.
  - Tests: `RoomBroadcasterTest.connectionsOfARoomWithAReusedCodeStayIsolatedFromTheOldRoomsSessions` - a
    `RoomRegistry` built with a fixed `RoomCodeGenerator` forces the real `create()` reuse path (close one
    room, create another with the same generator), producing two `Room`s with the same `roomCode` but
    different `roomId`s; asserts `RoomConnections` keeps their registered sessions fully separate.
  - Verification: `./gradlew build` green (all modules); `game-server:test` rerun clean.
- Round 5 fix (review R5-P1-1): `handleHello` registered the reconnecting session in `RoomConnections`
  (making it visible to `RoomBroadcaster`, which reads live from that registry) BEFORE sending it its own
  `FULL_SNAPSHOT`. A concurrent broadcast triggered by another seat's command in between - `onProcessed`'s
  `STATE_UPDATE`, or another READY's `ROOM_UPDATE` - could reach the just-registered session first, violating
  the reconnect protocol (snapshot must always be the first message a reconnecting client sees). Fixed by
  moving `connections.register(...)` to run AFTER `sendRaw(session, broadcaster.snapshotFor(seat))`: the new
  session stays completely absent from `RoomConnections` - and so invisible to every broadcast path - until
  its `FULL_SNAPSHOT` has already been sent directly to it. No extra locking needed: this is plain sequential
  ordering within `handleHello`'s own thread, and `RoomConnections` is a `ConcurrentHashMap` already safe for
  another thread to read mid-update. `established.remove(previous.getId())` (which is what actually protects
  against R3-P1-1) already happens earlier, under `seatLock`, so this reordering does not touch that
  guarantee.
  - Files: `GameWebSocketHandler`.
  - Verification: `./gradlew build` green (all modules); `GameWebSocketReconnectAndIdempotencyTest` rerun
    clean. No new deterministic regression test added: like the round-3 races, this needs genuine thread
    interleaving between `handleHello` and a concurrent `GameRoom` worker-thread broadcast, which the existing
    single-threaded-per-session WebSocket test harness cannot force; verified by code inspection of the now
    strictly-ordered `sendRaw` / `connections.register` calls instead.
- Round 6 fix (review R6-P1-1): round 5's fix removed the "registered before snapshot sent" ordering bug but
  left the reconnecting session absent from `RoomConnections` for a real gap between `sendRaw(snapshot)` and
  `connections.register(...)`. Two problems in that gap: (1) a state change processed concurrently on the
  room's worker thread would call `RoomBroadcaster.broadcast()`, which reads sessions live from
  `RoomConnections` - since the reconnecting session was not yet registered, it would miss that broadcast
  entirely, leaving its just-sent snapshot stale with no way to catch up; (2) if the brand-new session itself
  closed during that same gap, `afterConnectionClosed` would call `RoomConnections.unregister(...)`, which is
  a no-op for a session not yet registered, so it would return early WITHOUT calling `setDisconnected(true)` -
  and `handleHello` would then go on to register the already-closed session anyway, leaving the seat marked
  connected while genuinely closed. Fixed both gaps with one change: `RoomBroadcaster` gained a
  `deliverSnapshotAndRegister(seat, session)` method that sends the snapshot and registers the session
  atomically under a new per-room `broadcastLock`, which every `broadcast()`/`broadcastNotices()` call now also
  holds - so a concurrent broadcast and a snapshot-delivery-plus-registration can never interleave (whichever
  finishes first, the other sees a fully consistent, non-stale state). `handleHello` now calls this new method
  from INSIDE its existing per-`(roomCode, seat)` `seatLock` block (previously that block ended before the
  snapshot/registration step), so a concurrent close of the same brand-new session - `afterConnectionClosed`
  also takes that lock - can no longer interleave with registration either: it now always sees the session
  already registered by the time it runs, so `unregister()` correctly returns true and `setDisconnected(true)`
  fires.
  - Files: `RoomBroadcaster`, `GameWebSocketHandler`.
  - Tests (+1): `RoomBroadcasterTest.deliverSnapshotAndRegisterBlocksAConcurrentBroadcastUntilItCompletes` -
    blocks a snapshot delivery for seat 0 mid-send (via a new `FakeWebSocketSession.beforeSend` test hook),
    submits a state-changing command for seat 1 while that's in flight, and asserts the command's processing
    (which broadcasts on the room's worker thread) stays blocked and seat 1 receives nothing until the
    snapshot delivery is released - proving the two are now mutually exclusive. The second gap (own-session
    close during registration) is closed by construction (same `seatLock` now spans the whole critical
    section, matching the existing R3-P1-1 pattern) but is not separately regression-tested: forcing that exact
    interleaving would need a production-code test hook inside `handleHello` itself, which seemed like more
    risk than value given the fix is a straightforward lock-scope widening.
  - Verification: `./gradlew build` green (all modules); `RoomBroadcasterTest` rerun clean 3x in a row (new
    test uses real thread blocking/latches, not sleeps-as-synchronization, so it is not timing-flaky).
- Round 7 fix (review R7-P2-1): two round-6/T23 tests used a fixed `Thread.sleep(...)` to give a race a window
  before sampling state, which the project rule bans (tests must not sleep) and which is inherently flaky - too
  short under CI load gives a false pass, too long just wastes time.
  - `RoomBroadcasterTest.deliverSnapshotAndRegisterBlocksAConcurrentBroadcastUntilItCompletes`: replaced
    `Thread.sleep(300)` + `commandFuture.isDone()` with a `CountDownLatch` (`seat1BroadcastAttempted`) armed via
    `FakeWebSocketSession.beforeSend` on seat 1 - it can only fire once `broadcast()` acquires the lock seat 0's
    snapshot delivery holds, so `latch.await(300, MILLISECONDS)` returning `false` proves the block by
    construction (and returns immediately once the lock frees, instead of always waiting the full window).
  - `GameWebSocketReconnectAndIdempotencyTest`'s `awaitMessage` polled `Thread.sleep(50)` between checks.
    Replaced with an `Object lock` on `RecordingClientHandler`, notified from `handleTextMessage` right after
    each message is recorded; `awaitMessage` now does its check-then-wait inside `synchronized (handler.lock)`
    so a message arriving between a failed check and the wait call is never missed, and returns as soon as a
    matching message is recorded instead of up to 50ms late.
  - Files: `RoomBroadcasterTest`, `GameWebSocketReconnectAndIdempotencyTest` (test-only; no production code
    changed).
  - Verification: `./gradlew build` green (all modules); both test classes rerun clean.
- Notes / P3 items: none.

### T22 - WebSocket protocol - READY (review pending)
- What: new `citytrade.server.ws` package + `/ws` endpoint (plain `TextWebSocketHandler`, no STOMP).
  `GameWebSocketHandler`: HELLO {protocolVersion, token} identifies room+seat from the token alone
  (Architecture 6.6, new `RoomRegistry.findByToken` scanning open rooms' seats); wrong version ->
  PROTOCOL_UNSUPPORTED + close; bad token or no live `GameRoom` for that room -> close. Every later message
  is a `ClientEnvelope` {commandId, commandType, payload}; `commandType` READY/SNAPSHOT_REQUEST/a player
  command (`ClientCommands` maps 18 player commandTypes -> engine `GameCommand`, always with a placeholder
  seat - `GameRoom.submitPlayerCommand` already replaces it with the caller's real seat, per T20 - so a
  payload can never claim another seat; a strict Jackson mapper, `FAIL_ON_UNKNOWN_PROPERTIES` on, rejects any
  extra field, in particular a "seat" field in a payload). `RoomBroadcaster` (one per room, registered as a
  `GameRoom` listener on first HELLO into it): STATE_UPDATE to every connected seat when `stateVersion`
  actually changed, plus one `NoticeEvent` per event to the seat(s) `NoticeProjector` says may see it -
  ROUND_WARNING/ROUND_RESOLVED/GAME_FINISHED for `EventWarned`/`RoundResolved`/`GameFinished`, NOTICE for
  everything else. `ServerMessage` carries a new `eventKind` (the event record's simple name): `NoticeEvent`
  itself has no Jackson type discriminator (T21 only ever wrote it), so this is how the client tells one
  event shape from another without touching that already-reviewed T21 file.
- READY is server-level, not an engine command: calls `RoundFlowDriver.setReady` directly and broadcasts
  ROOM_UPDATE (not COMMAND_ACCEPTED) only if `roomVersion` actually changed. Added
  `RoundFlowDriver.isDisconnected(seat)` (mirrors the existing `isReady`): views need it and there was no
  getter yet.
- Files: `game-server/.../ws/{ProtocolVersion,ClientHello,ClientEnvelope,ServerMessageType,ServerMessage,
  ProtocolErrorCode,CommandPayloads,ClientCommands,UnsupportedCommandTypeException,InvalidPayloadException,
  RoomConnections,RoomBroadcaster,GameWebSocketHandler}.java`, `config/WebSocketConfig.java`,
  `RoomRegistry.findByToken` + `SeatToken`, `docs/PROTOCOL.md` (every message type, JSON examples, all 18
  command payload shapes).
- Tests: `GameWebSocketProtocolTest` (8, real `StandardWebSocketClient` against a real `RANDOM_PORT` server:
  HELLO required/wrong version/bad token refused; an accepted state-changing command gets COMMAND_ACCEPTED
  to the sender and STATE_UPDATE to every connected seat with its own view; a rejected command gets the
  engine's `RejectionCode`; READY gets ROOM_UPDATE to all with `stateVersion` unchanged and never a
  COMMAND_ACCEPTED; a payload with a "seat" field is rejected; a seat's STATE_UPDATE never structurally
  carries another seat's private data), `ClientCommandsTest` (7: payload mapping, unknown command type,
  unknown/seat field rejected), `RoomBroadcasterTest` (2: a genuine accepted-no-op, via a test-double engine
  as T20 did, broadcasts nothing; a real state-changing command reaches every connected seat, including the
  seat's own private NOTICE), `ProtocolDocumentationTest` (every JSON example in `docs/PROTOCOL.md` parses;
  no example payload has a "seat" field), `RoomRegistryTest`/`RoundFlowDriverTest` additions for
  `findByToken`/`isDisconnected`.
- Round 1 fix (own, before review): the WS test's own `awaitMessage` helper first drained/discarded messages
  while scanning a `BlockingQueue`, which could silently eat a STATE_UPDATE while looking for the
  COMMAND_ACCEPTED that (correctly) arrives after it - `RoomBroadcaster` runs synchronously inside
  `GameRoom`'s queue, before the direct ack is sent. Rewrote the client-side log as an append-only list
  scanned from a per-call starting index, so nothing already delivered is ever lost from under a later
  assertion.
- Round 1 fix (review R1-P1-1): `UPGRADE_CITY` (in `ClientCommands`) and `SNAPSHOT_REQUEST` (in
  `GameWebSocketHandler`) had no payload type to validate against, so `FAIL_ON_UNKNOWN_PROPERTIES` never
  triggered for them - a payload with a "seat" field (or any other field) went through silently. Added
  `CommandPayloads.EmptyPayload` (no fields) and validate both against it before proceeding.
- Round 1 fix (review R1-P2-1): post-HELLO envelopes never checked `ClientEnvelope.protocolVersion`, so a
  missing/unsupported version still routed the command. `handleEnvelope` now checks it first (same
  PROTOCOL_UNSUPPORTED + close as HELLO) before routing to SNAPSHOT_REQUEST/READY/a player command.
- Tests: `ClientCommandsTest` (+1: UPGRADE_CITY with an unknown field rejected), `GameWebSocketProtocolTest`
  (+4: UPGRADE_CITY/SNAPSHOT_REQUEST unknown-field rejection, unsupported/missing post-HELLO protocolVersion
  refused and closes).
- Verification: `./gradlew build` green (all modules); `GameWebSocketProtocolTest` run 3x in a row clean.
- Notes / P3 items: reconnect (same token -> same seat, old connection replaced) and `commandId` idempotency
  are explicitly T23's job; T22 only requires an ACTIVE room's `GameRoom` to exist for HELLO to succeed.

### T21 - PlayerGameView + privacy projector - READY (review pending)
- What: new `citytrade.server.view` package. `ViewProjector.project(GameState, RoomViewContext, seat)` builds a
  `PlayerGameView` (own resources/Money/reserved Money/objectives/crisis policy/upkeep priority; public
  players/market/event/projects/opportunities(no other bids)/contracts; trade offers filtered to the viewing
  seat's own, D7). `RoomViewContext` (server metadata: room status, phaseEndsAt, READY/disconnected seats,
  roomVersion) stays out of the engine `GameState`, per Architecture 6.4/6.5. `NoticeProjector.project` turns
  domain events into per-seat `Notice`s: private events (resource amounts, bids, own choices, direct trade
  events) to the seat(s) involved only, everything else (contracts, projects, opportunities minus bid amounts,
  market, round/game progress) to every seat, via an exhaustive switch over every `DomainEvent` kind.
- Formal contracts are fully public (Architecture 6.5 "public contracts"), and hidden objectives are
  intentionally revealed to every seat in `finalResult` only, once the game ends (Numbers Sheet 15/18) - both
  match the task's explicit "(as Architecture 6.5)" / accept-list wording despite the shorthand "own contracts"
  earlier in the same task bullet.
- Files: `game-server/.../view/{PlayerGameView,OwnView,PlayerPublicView,OpportunityView,RoomViewContext,
  ViewProjector,Notice,NoticeEvent,NoticeProjector}.java`, `docs/VIEW.md` (every field, who sees it).
- Tests: `ViewProjectorTest` (11: own data correct per seat; other seats' holdings/Money/objectives
  structurally absent from JSON before the reveal; other seats' objective ids absent from JSON before the
  reveal, then present for everyone in `finalResult`; a trade offer visible only to its two parties; a bid
  visible only to its own seat, other bids structurally absent; contracts and project contributions public;
  UPCOMING projects not announced; `finalResult` null until FINISHED then public; same inputs -> equal view),
  `NoticeProjectorTest` (6: every private `DomainEvent` kind reaches only the seat(s) involved, every public
  kind reaches everyone, an uninvolved seat never receives a trade event, a trade event for an offer no longer
  in state reaches nobody, the projector never mutates its input state, `OpportunityWon`'s JSON never carries
  `pricePaid` for any seat including the winner). Real 14-round games played through `GameEngine.apply`
  directly (not mocked), scripted to hit every private-data category (trade, formal contract, secret bids, a
  project contribution) since baseline/trader bots do not use contracts or projects (see T18 balance report).
- Round 1 fix (own, before review): `otherPlayersObjectiveIdsNeverAppearInAnotherSeatsJson` originally checked
  ALL states including the final one, which correctly fails once hidden objectives are revealed in
  `finalResult` - split into a before-reveal test and an explicit after-reveal test instead of weakening the
  assertion.
- Round 1 fix (review R1-P0-1): `Notice` wrapped the raw `DomainEvent`, so broadcasting `OpportunityWon` to
  every seat exposed the winning secret bid (`pricePaid`) to everyone, not just the winner - itself not
  supposed to see it either, since `OpportunityView` never carries it. Added `NoticeEvent`, a client-safe
  sealed DTO mirroring `DomainEvent` one-for-one, with `NoticeProjector.sanitize(DomainEvent)` translating
  every kind through an exhaustive switch (new kind -> compile error here, same pattern as `recipients`); only
  `NoticeEvent.OpportunityWon` differs from its `DomainEvent` counterpart, by omitting `pricePaid` entirely.
  `Notice.event()` is now `NoticeEvent`, never `DomainEvent` - raw domain events can no longer reach `Notice`
  at all, enforced by the type system, not just convention. Added serialization coverage (JSON string does not
  contain `"pricePaid"` or the secret value, for every recipient seat).
- Verification: `./gradlew build` green (all modules).
- Notes / P3 items: none.

### T20b - Round flow: timer, READY, objective timeout, bots in rooms - READY (round 2 pending)
- Round 1 fixes: bot maps are now `TreeMap`s (ascending seat order, not `HashMap`/`Map.of`'s unspecified
  order) so bot command submission stays deterministic; `setReady` is a no-op outside WINDOW (phase guard);
  `onProcessed` now rechecks all-READY right after a rejected/no-op bot command clears its pending flag
  instead of only on state-changing commands, so a quiescent-by-rejection bot no longer stalls a
  ready round until the timer; `ActiveGameCoordinator`'s `onFinished` now shuts the `GameRoom` down and
  unregisters its driver, instead of leaking a worker thread + map entry per finished room.
- New tests: `RoundFlowDriverTest` (+3: READY ignored outside WINDOW, a rejected bot command still lets
  an all-READY round resolve, ascending bot evaluation order survives a randomized input map),
  `ActiveGameCoordinatorTest` (new file: a finished room's driver is unregistered and its `GameRoom`
  executor rejects further submissions).
- Fixing R1-P2-2 (more work per processed bot command) exposed a pre-existing race in the test helper
  `settle()`: comparing a barrier's own sequence number against `room.commandSequence()` read from the test
  thread can race the room's single worker thread finishing a still-pending cascading command. Rewrote
  `settle()` in both test files to compare two *consecutively submitted* barriers' sequence numbers instead
  (self-correcting: a false "settled" reading just makes the loop try again, never returns a stale answer).
  `./gradlew :game-server:test --rerun` clean on 6+ repeated runs after the fix (was flaky ~1-in-6 before).
- What: new `RoundFlowDriver` (one per ACTIVE room) drives the automatic flow entirely through its
  `GameRoom`'s own serial queue: objective choice (asks every bot at once, schedules the D22 timeout) ->
  StartRound once `state.allObjectivesChosen()` -> WINDOW (schedules the D20 window timer, asks every bot
  again) -> ResolveRound (timer OR all-READY, guarded so it is enqueued exactly once per round) -> next
  StartRound, until Round 14 resolves -> `onFinished` runs once. `GameRoom` gained `addListener`: called with
  every `ProcessedCommand`, still on the room's own worker thread, so the driver reacts to state changes
  (Architecture 6.4) without polling; a listener must never block (would deadlock the room's own queue) and a
  throwing listener is caught so it can never break command processing.
- Bot coordinator (D24): after every state-changing command, every bot seat not already "pending" or capped
  is asked again (`Bot.nextWindowCommand`); an action is submitted with origin BOT and the seat is "pending"
  until that command is processed. A bot seat counts as READY only while it has no pending action. A per-seat
  cap (`maxActionsPerBotPerRound`, default 1000 - same order of magnitude as `BotGame.MAX_COMMANDS_PER_WINDOW`)
  stops a broken bot; a package-private constructor lets tests use a small cap.
- Timing: `RoundScheduler` interface + `ScheduledExecutorRoundScheduler` (production, delay computed from the
  injected `Clock`). New `ActiveGameCoordinator` builds the `GameRoom` + bots (`Room.botSeats()`, D24) + driver
  for a room and calls `driver.start()`; wired from `RoomController.start()` right after `RoomRegistry.start()`
  and from new `ServerConfiguration` beans.
- Files: `game-server/.../game/{RoundScheduler,ScheduledExecutorRoundScheduler,RoundFlowDriver,BotFactory,
  ActiveGameCoordinator}.java`, `GameRoom` (+listener), `Room` (+`botSeats()`), `RoomController`,
  `ServerConfiguration`; tests `RoundFlowDriverTest` (9, with test double `ManualRoundScheduler`: full 14-round
  game with 1 human + 3 bots driven only by timers; a quiescent bot reacting to a later trade offer; READY
  cannot overtake a pending bot action; the runaway cap; all-READY resolves before the timer, READY cancel and
  reset; a genuine concurrent race between the timer and the last READY resolves exactly once; the D22 timeout;
  a disconnected seat counting as READY; queue order around ResolveRound), `GameRoomTest` (+1: listener).
- Verification: `./gradlew build` green (all modules).
- Notes / P3 items: connection status (D23) is currently only `setDisconnected`/`setReady` on the driver
  itself, called directly (as tests do); wiring it to real WebSocket session state is T22/T23's job.

### T20 - GameRoom: serial command queue, commandSequence, stateVersion - READY (review pending)
- What: new `citytrade.server.game` package: `GameRoom` (one per ACTIVE room, one single-thread
  `ExecutorService` so all state changes for a game are strictly serial), `CommandOrigin`
  (PLAYER/BOT/SYSTEM), `CommandOutcome` (`Applied(GameResult)` or `Refused(reason)` for commands that
  never reach the engine), `ProcessedCommand` (sequence, origin, actorSeat, command, outcome,
  resultingStateVersion) - the shape T24 will persist.
- Rules: `commandSequence` assigned inside the executor task in processing order (1, 2, 3, ...), so it
  is always gap-free even under concurrent callers. `stateVersion` increases by exactly 1 only when the
  engine returns `Accepted` with a state not `.equals()` the previous one. `submitPlayerCommand` /
  `submitBotCommand` take the caller's seat explicitly; `GameRoom` refuses (never calls the engine) when
  the command is `StartRound`/`ResolveRound` from a non-SYSTEM origin. For PLAYER/BOT origin, `GameRoom`
  rebuilds the submitted command with the caller's seat injected into its `seat` component before it is
  checked or sent to the engine (an exhaustive switch over the sealed `GameCommand` types, so a new
  command type without a case fails to compile) - a command whose embedded seat disagrees with the
  caller is corrected, never refused, per T20's "seat is injected from the caller identity" rule.
  `submitSystemCommand` has no seat and may submit internal commands unchanged. `GameEngine::apply` is
  the default engine; a 3-arg constructor accepts a test double (`GameRoom.Engine`) for the no-op-result
  rule, since no real engine command is a no-op today.
- Not wired yet: `Room`/`RoomRegistry` still stop at `start()`; T20b (timer, READY, bot coordinator)
  is what will create a `GameRoom` per ACTIVE room and drive it. Idempotent `commandId` replay
  (Architecture 6.7) is a later, separate concern - no accept test asked for it here.
- Files: `game-server/.../game/{CommandOrigin,CommandOutcome,ProcessedCommand,GameRoom}.java`,
  `GameRoomTest`.
- Tests: `GameRoomTest` (7): 4 threads x 250 commands concurrently -> 1000 unique gap-free sequence
  numbers and final state equals a single-threaded replay of the recorded history in sequence order;
  rejected command keeps state/stateVersion but gets a sequence; no-op `Accepted` (test double engine)
  leaves stateVersion unchanged; origin recorded for PLAYER/BOT/SYSTEM; client-submitted
  `StartRound`/`ResolveRound` refused before the engine is called (poison-engine double asserts it is
  never invoked); a command whose embedded seat disagrees with the caller reaches the engine with the
  caller's seat injected (recording-engine double asserts what the engine actually saw); SYSTEM may
  submit `StartRound` and it reaches the engine normally.
- Verification: `./gradlew build` green (all modules).
- Round 1 review fix: R1-P1-1 - the caller's seat is now injected into the command (rebuilt via
  `GameRoom.withSeat`) instead of refusing on a seat mismatch, matching T20's "application layer
  injects the seat" rule.

### T18b - prototype-002 and second balance report - READY (review pending)
- What: Added `rulesets/prototype-002.json` with only S1 Level 2 cost 4, S2 Grand Landmark Money 10,
  and S3 Research Lab F1 E2 M1 T1. Added `docs/balance-report-prototype-002.md` with the three 1000-game
  comparisons and three follow-up suggestions.
- Files: prototype-002 ruleset, loader regression test for exact approved changes, second balance report.
- Tests: prototype-002 loaded in all three simulation runs; all had 0 rejected commands. Standard Gradle build
  is blocked in this sandbox by the known Java 25 `java.security` access error; source compilation and simulation
  runs completed with the temporary external-JDK workaround.
- Notes / P3 items: none.

### T19 - Spring Boot module + rooms and lobby REST - READY (review round 2)
- What: Added pinned Spring Boot 4.1.1 `game-server` module with startup ruleset validation, in-memory room registry,
  LOBBY/ACTIVE/FINISHED/CLOSED lifecycle, collision-safe six-character room codes, hashed 32-byte seat tokens,
  host-only bot/add-remove/start REST actions, public lobby snapshots, and injected-clock cleanup.
- Round 1 fixes: `Room.close()` now rejects transitions from ACTIVE or already-CLOSED (new `ROOM_NOT_CLOSABLE`
  code, mapped to 409). `RoomRegistry.cleanup()` no longer does a check-then-close in two steps; it calls a new
  atomic `Room.closeIfExpired(now, ttl, ttl)` (single `synchronized` method) so a concurrent `start()` can no
  longer race with cleanup and close an ACTIVE room. Added MockMvc coverage of the full REST contract
  (`RoomControllerTest`) and a startup-validation test for missing/blank ruleset config (`ServerConfigurationTest`).
- Files: `game-server` (`Room`, `RoomRegistry`, `RoomErrorCode`, `RestErrorHandler`, `build.gradle`),
  `RoomRegistryTest`, new `RoomControllerTest`, new `ServerConfigurationTest`.
- Tests: `RoomRegistryTest` (11, incl. invalid-close-transition and a 200-iteration concurrent
  start-vs-cleanup race test), `RoomControllerTest` (MockMvc: create/join/full/not-found/not-joinable/
  not-host/not-full/not-in-lobby/no-token-or-seed-in-GET), `ServerConfigurationTest` (context fails to start on
  missing or blank `game.ruleset`, succeeds with a valid one).
- Build note: Spring Boot 4.1.1 moved `@AutoConfigureMockMvc` out of `spring-boot-test-autoconfigure` into a new
  `org.springframework.boot:spring-boot-starter-webmvc-test` artifact (package
  `org.springframework.boot.webmvc.test.autoconfigure`); added that test dependency. Jackson 3 also moved
  `ObjectMapper` to `tools.jackson.databind`.
- Verification: `./gradlew build` green (all modules, including `game-server`).
- Notes / P3 items: R1-P2-1 (REST/config test coverage) addressed above; no remaining P3 items.

### T18a - Ruleset upper bounds - READY (review pending)
- What: `RulesetValidator` rejects every ruleset integer above 100000 with a path-specific error;
  signed market/event deltas and direct objective counts are covered too. No engine arithmetic changed.
- Files: `game-ruleset-json` validator and `RulesetValidationTest`.
- Tests: six failing-ruleset tests for prices, costs, production, Prestige, limits, and storage;
  existing prototype-load coverage remains in place.
- Verification: Gradle test/build attempts were blocked in this sandbox by the JDK 25 worker's
  `java.security` access error; no source/test failure was reached.

### T18 - First balance report - DONE (review round 1)
- What: 3 x 1000 games (baseline x4, baseline/trader mix, trader x4), seed 1. `docs/balance-report-prototype-001.md`.
- Findings: "do everything" wins (57.5% of trader winners at max 19, 34.6% shared victories); Level 2 in Round 1,
  Level 3 by Round 4.4; trading stops after Round 11; 50-65 unspent Money; Agricultural strongest (39% baseline),
  Technology weakest (19.7% trader). Bots do not use projects/contracts, so those are not judged.
- Suggested (owner decides): Level 2 cost 4; Grand Landmark 10 Money; Research Lab F1 E2 M1 T1; later Money -1/level.
- Ruleset not changed. No code changed.

### T17 - Simulation runner + metrics - DONE (review round 1)
- What: `game-sim` CLI `SimulationMain` (`./gradlew :game-sim:run --args="--ruleset prototype-001 --games 1000
  --seed 1 --bots baseline,trader,baseline,trader"`; also `--rulesets-dir`, `--out` (default `build/sim`)).
  Game i uses seed + i. Writes `<ruleset>_<bots>_seed<S>_games<N>.json` (summary) and `.csv` (one row per city per game).
- Metrics: Arch 8.2 (Prestige by city, level 2/3 reach rate + round, produced/traded/discarded, market use,
  resource demand early/mid/late, contracts, project completion, Strained rounds, holdings + market steps by round)
  + win rate by city and bot (shared victory = 1/winners), Prestige distribution (all, per city, winner).
- Engine: new event `ResourcesProduced(seat, produced)` in step 1.3 (needed for "produced"); `BotGame.Observer`.
- Choice (metrics only): early/mid/late = rounds 1-5 / 6-10 / 11-14 (`GamePart`). Demand = resources paid for
  levels, buildings, upkeep, crises, projects, event options.
- Tests: sim (Options, Distribution, GamePart, Collector vs real game events, Report by hand, Main end-to-end +
  same args = same files); ProductionTest, BotGameTest observer; 3 event-list tests updated for the new event.

## Earlier
- T16 DONE: `TraderBot` (Arch 8.1 B) - value-threshold trading, spare-resource offers, warned-crisis prep,
  bounded bids; shared helpers in `BotActions`.
- T15 DONE: `game-bots` `Bot`/`BaselineBot` (Arch 8.1 A, market fallback, no negotiation), `BotGame` runner.
- T14a DONE: D18 - `Contracts.breakVoluntarily` rejects `INSUFFICIENT_FREE_MONEY` with active bids and free Money short.
- T14 DONE: `ScriptedGame`/`FullGameTest`/`ReplayTest`/`RoundOrderTest`/`RulesetSensitivityTest` (M1 acceptance, no engine change).
- T13 DONE: package `objective`: `Objectives`, `FinalScoring` (hidden score, final ranking, tiebreakers).
- T12 DONE: package `opportunity`: `Opportunities` (secret bids, `PlaceBid`, `GameState.spendableHoldings`).
- T11 DONE: package `project`: `Projects` (contribution points, deadline success/failure, Prestige reward).
- T10 DONE: package `event`: `EventSchedule`, `Crises` (D2), `EventOptions`, `EventEffects`.
- T09 DONE: package `contract`: `Contracts` (propose/sign/break/mutual cancel, 1.5 settle, 4.1 expiry),
  `PlayerState.contractsBroken`; D13-D16 applied.
- T08 DONE: package `trade`: `Trading` (propose/accept/reject/cancel/counter, 4.1 expiry), `TradeOffer` history;
  accept with missing resources = Accepted + offer INVALID (`TradeInvalidated`).
- T07 DONE: package `city`: `CityDevelopment` (`UpgradeCity`, `BuildBuilding`, 4.4 Prestige), `BuildingEffects`
  (effects from the round after building).
- T06 DONE: package `market`: `Market` (buy/sell at current step, 4.6 `movePrices`), per-round `MarketActivity`.
- T05 DONE: package `economy`: `Production`, `Upkeep` (D1), `Storage`, command `SetUpkeepPriority`, Strained flag;
  with prototype values an L2+ city never fails upkeep in practice.
- T04 DONE: `GameEngine.apply`, `GamePhase`, `StartRound`/`ResolveRound`; round order in ONE place: enum
  `round.RoundStep` (declaration order = Numbers Sheet order), run by `round.RoundFlow`, bodies in `round.RoundSteps`.
- T03 DONE: core state, `GameRandom` (SplitMix64), `GameSetup.create` (Numbers Sheet 21 draw order),
  `ChooseObjectives` (`setup.ObjectiveChoice`).
- T00 DONE: environment checked (Java, git, node, codex, scripts executable, Codex smoke test OK).
- T01 DONE: Gradle 9.7.1 wrapper, Groovy DSL, Java 25 toolchain + foojay, 4 modules; `verifyEngineHasNoDependencies`
  in `check`. Codex sandbox cannot run Gradle (no network, no cache access).
- T02 DONE: `Ruleset` records in the engine, `rulesets/prototype-001.json`, strict Jackson loader (mixins) with
  validation that reports all errors; one test per Numbers Sheet section.
