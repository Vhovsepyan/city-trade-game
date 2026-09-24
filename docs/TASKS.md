# TASKS.md

Status values: `TODO`, `IN PROGRESS`, `DONE`, `BLOCKED`.
Take the first `TODO` task whose dependencies are `DONE`.
Each task = one review cycle + one commit. Keep tasks small.

---

## Product decisions

The concept was written for a paper game with a Banker. For the digital engine,
these decisions apply. All are approved by the owner. Agents follow them without asking.

| ID | Topic | Decision |
|----|-------|----------|
| D1 | Upkeep payment | Optional `SetUpkeepPriority(order)` command. Without it: pay with the allowed non-specialty resources the player has MOST of; ties in fixed order Food, Energy, Materials, Technology. Level 3: the 2 resources must be different. |
| D2 | Crisis payment | `SetCrisisPolicy(PAY or SKIP)` command, sent during the window of the WARNING round (the round before the crisis). Default policy: PAY. PAY = pay in full if possible (Infrastructure Failure uses the D1 order), otherwise Strained. SKIP = do not pay, become Strained. |
| D3 | Contract obligations due | Paid automatically in step 1.5 if the debtor has the resources; otherwise the contract breaks automatically. |
| D4 | Workshop / Research Breakthrough resource choice | Given as a parameter in the command. |
| D5 | Hidden objectives (deal 3, keep 2) | `ChooseObjectives` command before Round 1. The game does not start until all 4 players have chosen. |
| D6 | Visibility of resources and Money | Own only. Others see level, buildings, visible Prestige, Contracts Broken, public projects. |
| D7 | Trade offer visibility | Only proposer and recipient. |
| D8 | Open trade offers at Round Resolution | Expire (step 4.1). |
| D9 | "Largest contributor" in projects | Measured in contribution points (1 resource = 2, 1 Money = 1). |
| D10 | Contract shape in v1 | One shape: party A gives a bundle now; party B owes a bundle in a due round (max 3 rounds later, never after Round 14). |
| D11 | Seat and city assignment | Seat order 0-3; cities assigned by seeded shuffle at setup. |
| D12 | Instant trade content | Any bundle of F, E, M, T and Money on each side; at least one side not empty; no "free gift" limit (concept section 32). |
| D13 | Prestige below zero | Allowed. Penalties can make visible Prestige negative. No floor. |
| D14 | Voluntary contract break | Nothing is delivered. Compensation is paid for the whole obligation. (The automatic break in step 1.5 delivers what the debtor has.) |
| D15 | Several contracts due, debtor cannot pay all | Settled in creation order (oldest first). Each one is paid in full if possible; the rest break with what is left. |
| D16 | Unsigned contract proposals | Expire at step 4.1, like trade offers (D8). "Now" and the max duration count from the signing round. |
| D17 | Strained penalty base | Applied to the TOTAL specialty production and Money income, bonuses included (min 0). |
| D18 | Voluntary break with active bids | Rejected (`INSUFFICIENT_FREE_MONEY`) if free Money does not cover the full compensation while the player has active bids. The player must withdraw or lower bids first. Without active bids, Numbers Sheet 13 applies as written. Automatic breaks (step 1.5) are unchanged. |
| D19 | Objective readings | PROJECT PARTNER = 6+ points in both projects, success not required. CONTRACT PLAYER = 2 fulfilled contracts as either party and Contracts Broken = 0. PATIENT INVESTOR = contributions to failed projects count. |
| D20 | Development & Trade window duration | 120 seconds. Server setting `game.window-duration` (tests use a few seconds). |
| D21 | Early round end | Every player may send READY during the window (and cancel it until the round resolves). When all seats are ready, the server resolves the round early. Bots and disconnected players count as ready. READY resets every round. |
| D22 | Objective choice timeout | 60 seconds (setting `game.objective-choice-timeout`). A player who has not chosen by then keeps the first 2 dealt cards automatically. |
| D23 | Disconnected player | Passive: the seat sends no commands; automatic rules still apply (upkeep D1, crisis policy D2, contract payments D3). The player can reconnect any time and continue. |
| D24 | Bots in the lobby | The host may add a bot (baseline or trader) to an empty seat, or remove it, while the room is in LOBBY. A room can start with any mix of humans and bots (4 seats filled, at least 1 human). |

New product decisions are added here by the owner only.
Agents write open questions in `docs/PROGRESS.md`.

---

## Milestone M0 - Project foundation

### T00 - Environment and repository check (self-fixing)  `DONE`
Depends on: -
Review: Codex review NOT needed (no product code). Commit directly after checks pass.
Rule: FIX problems yourself (AGENTS.md section 3a). Do not stop to ask the owner
for anything that can be done without admin rights.
Do:
- Java: find any JDK 17+ to run Gradle (`java -version`, `~/.jdks`, `JAVA_HOME`).
  JDK 25 is NOT required as the default: T01 uses a Gradle toolchain that finds or
  downloads JDK 25. If a JDK 25 already exists (e.g. `~/.jdks/corretto-25*`), note its path.
  If no JDK 17+ exists at all: install one user-level (e.g. `winget install --scope user EclipseAdoptium.Temurin.21.JDK`).
- `git --version`, `node --version`, `codex --version`. (jq is NOT needed.)
- Repository: `.gitignore` contains `.review/`, `build/`, `.gradle/`, `.idea/`.
- The 3 spec documents exist in `docs/` with the names listed in AGENTS.md.
- Scripts executable: `scripts/codex-review.sh`, `scripts/run-until.sh`, `scripts/usage-report.sh`
  (fix with `chmod +x` if needed).
- Smoke test the Codex call with the same flags as the review script:
  `codex exec --json --sandbox workspace-write -o .review/codex-smoke.txt "Reply with exactly: CODEX OK" > .review/codex-smoke.jsonl`
  Confirm `.review/codex-smoke.txt` contains `CODEX OK` and the .jsonl contains `input_tokens`.
  If a flag is wrong for the installed Codex version: check `codex exec --help`,
  fix `scripts/codex-review.sh`, and retest.
- `mkdir -p .review`.
- Write all versions, paths and any environment changes to PROGRESS.md "Environment".
Accept:
- All checks OK (fixed by the agent where needed).
- `BLOCKED` only for admin rights, logins (e.g. Codex not logged in) or paid purchases,
  with the exact one-line action for the owner.

### T01 - Gradle multi-module skeleton  `DONE`
Depends on: T00
Docs: AGENTS.md sections 3, 4, 9.
Do:
- Root `settings.gradle` + `build.gradle` (Groovy DSL), JUnit 5.
- Java 25 via Gradle toolchain: `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }`
  plus the foojay toolchain resolver plugin in `settings.gradle` (latest version), so JDK 25 is
  found or downloaded automatically. Gradle version must support Java 25 (Gradle 9.1+).
- If no global Gradle exists to create the wrapper: download the Gradle distribution zip into
  the user folder, run its `gradle wrapper` once, commit the wrapper. No admin rights needed.
- Modules: `game-engine`, `game-ruleset-json`, `game-bots`, `game-sim` (empty skeletons, one smoke test each).
- Dependency rules from AGENTS.md section 4. `game-engine` has no external dependencies except test libraries.
- `.gitignore` (build, .gradle, .idea, .review, *.iml).
- Gradle wrapper (`./gradlew`), committed to the repository, so no global Gradle is needed later.
- If a local JDK 25 path was found in T00, you may add it to the USER Gradle properties
  (`~/.gradle/gradle.properties`: `org.gradle.java.installations.paths=...`), never to the project file.
Accept:
- `./gradlew build` passes in a NEW terminal where the default `java` is NOT 25.
- `./gradlew -q javaToolchains` (or build output) shows JDK 25 is used for compiling.
- A test (or build check) proves `game-engine` has no dependency on other modules.

### T02 - Ruleset model + prototype-001.json + loader + validation  `DONE`
Depends on: T01
Docs: Numbers Sheet (all sections), AGENTS.md section 5.
Do:
- `Ruleset` records in `game-engine` (pure Java): production, starting resources, storage, level costs, upkeep, Strained penalty, buildings, market steps and thresholds and limits, contract penalties, events, objectives, projects, opportunities, Prestige values, schedule (event rounds, project windows, opportunity rounds), round count.
- `rulesets/prototype-001.json` with ALL values from Numbers Sheet v2. Include `"version": "prototype-001"`.
- Loader in `game-ruleset-json` (Jackson) -> `Ruleset`.
- Validation on load with clear errors, at least: market buy price < sell price for every step; objective count >= 3 x player count; event count matches event rounds; project windows inside the round count; no negative costs or production values.
  (Do NOT require storage >= a single cost: temporary overflow during the window is allowed.)
Accept:
- Loading prototype-001.json gives a complete `Ruleset`.
- Tests: every Numbers Sheet section has at least one value asserted; each validation rule has a failing-ruleset test.

---

## Milestone M1 - Game engine (pure Java)

### T03 - Core state, setup, deterministic random  `DONE`
Depends on: T02
Docs: Numbers Sheet 1-2, 21; Concept 5, 6; decisions D5, D11.
Do: `Resource`, `CityType`, `ResourceBundle`, `PlayerState`, `GameState`, engine-owned seeded random, `GameSetup.create(seed, ruleset)` (cities, starting resources, shuffled event/objective/project/opportunity decks, market start step, first event warning), `ChooseObjectives` command.
Accept: same seed -> identical state; different seeds -> different decks; tests for starting resources and objective choice rules.

### T04 - Command/result framework and round phases  `DONE`
Depends on: T03
Docs: Architecture 4; Numbers Sheet "ROUND ORDER"; AGENTS.md 5.
Do: sealed `GameCommand`, sealed `GameResult` (`Accepted`/`Rejected`), `RejectionCode` enum, `DomainEvent`, `GameEngine.apply(...)`, phases (SETUP, AUTOMATIC, WORLD, WINDOW, RESOLUTION, FINISHED), internal commands `StartRound` and `ResolveRound` as skeletons calling the steps in the exact documented order (steps may be empty for now).
Accept: wrong-phase commands rejected with `INVALID_PHASE`; rejected commands leave state unchanged (test); step order test exists.

### T05 - Production, upkeep, Strained, storage  `DONE`
Depends on: T04
Docs: Numbers Sheet 3-5, 8-9; Concept 8, 10, 11; D1.
Accept: `SetUpkeepPriority` command and the fallback order without it (D1); tests for L1/L2/L3 production, upkeep never uses specialty or Money, L3 needs 2 different resources, partial upkeep paid + Strained, Strained penalty next round only and not stacking, storage overflow allowed in window and discarded at resolution.

### T06 - Global market  `DONE`
Depends on: T05
Docs: Numbers Sheet 11-12; Concept 18, 19.
Accept: buy/sell at current step, per-player per-round buy limit, prices fixed during window, movement at resolution by net thresholds, min/max step, atomic rejection on insufficient Money.

### T07 - City levels and buildings  `DONE`
Depends on: T05
Docs: Numbers Sheet 6-7, 10; D4.
Accept: one level per round, level requirements for buildings, each building once, effects start next round, Prestige at resolution, Workshop resource parameter. Transit Network can be BUILT at Level 2; its upkeep reduction applies ONLY while the city is Level 3 (no effect at Level 2).

### T08 - Instant trades and offers  `DONE`
Depends on: T05
Docs: Architecture 5.1-5.5; Concept 14, 15; D7, D8, D12.
Accept: all allowed/forbidden status transitions tested; who may cancel/accept/reject/counter; revalidation on accept -> `INVALID`; counteroffer chain with `parentOfferId`; received resources usable immediately; offers expire at resolution.

### T09 - Formal contracts  `DONE`
Depends on: T08
Docs: Numbers Sheet 13; Concept 16, 17; D3, D10.
Accept: max duration, no obligation after round 14, automatic payment when due, automatic break, voluntary break, mutual cancel, Money compensation to victim, Prestige penalty for unpaid part (round up), public Contracts Broken counter; the Numbers Sheet example (section 13) as a test.

### T10 - Events and crises  `DONE`
Depends on: T06, T07
Docs: Numbers Sheet 14; Concept 20, 21; D2.
Accept: event active from step 1.1, warning one round ahead, no event in rounds 1 and 14, each of the 12 events tested, crisis only for L2+, `SetCrisisPolicy` (D2) with default policy PAY, PAY in full -> Prestige, cannot pay or SKIP -> Strained, optional events (Festival, Breakthrough) once per player. Test: a player already Strained from upkeep who SKIPs a crisis stays Strained once (no stacking) - record this behavior, do not "fix" it (Watch List item 3).

### T11 - Public projects  `DONE`
Depends on: T07
Docs: Numbers Sheet 16; Concept 22, 23; D9.
Accept: contribution points, only listed resources, cannot exceed need, qualifying minimum, success/failure at deadline, largest/tie/other Prestige, production reward from next round.

### T12 - Opportunities and secret bids  `DONE`
Depends on: T06
Docs: Numbers Sheet 17; Concept 24, 25.
Accept: Money reservation (total bids <= free Money), change/withdraw bid, highest wins and pays, tie -> nobody wins and card stays, 0 = pass, arrival order does not matter (test with shuffled command order).

### T13 - Hidden objectives, final scoring, tiebreakers  `DONE`
Depends on: T09, T10, T11, T12
Docs: Numbers Sheet 15, 18, 20; Concept 26-31, 40.
Accept: each of the 12 objectives has a pass and a fail test; final score = visible + hidden; tiebreakers in order; Money never a tiebreaker.

### T14 - Full-game and determinism tests  `DONE`
Depends on: T13
Do: scripted full 14-round game test; replay test (same seed + commands -> same final state); `ResolveRound` order test; no-hardcoded-balance check (for example: a test ruleset with changed values changes the results).
Accept: all green; engine milestone M1 complete.

---

## Milestone M2 - Bots and simulation

### T14a - Apply D18  `DONE`
Depends on: T14
Docs: Numbers Sheet 13, 17; decisions D14, D18.
Accept: voluntary break rejected when free Money < compensation and the player has active bids; allowed after bids are withdrawn or lowered; no change without active bids; step 1.5 automatic break unchanged.

### T15 - Baseline bot  `DONE`
Depends on: T14a
Docs: Architecture 8.1.
Accept: deterministic; never sends invalid commands in normal play (test: 100 games with 4 baseline bots finish without rejections except expected ones).

### T16 - Trader bot  `DONE`
Depends on: T15
Accept: proposes/accepts simple trades by a value threshold based on market prices; prepares for warned crises; bounded bids; deterministic.

### T17 - Simulation runner + metrics  `DONE`
Depends on: T16
Docs: Architecture 8.2.
Accept: CLI args (ruleset, games, seed, bot mix); JSON + CSV output; metrics from Architecture 8.2 plus win rate by city and Prestige distribution.

### T18 - First balance report  `DONE`
Depends on: T17
Do: run 1000+ games (baseline-only and trader mix) on prototype-001. Write `docs/balance-report-prototype-001.md`: key numbers, problems found, 3-5 suggested value changes with reasons. **Do not change the ruleset.** The owner decides.

### T18a - Ruleset upper bounds  `DONE`
Depends on: T18
Do: RulesetValidator rejects any numeric value above 100000 (prices, costs, production, Prestige, limits, storage) with a clear error.
Accept: one failing-ruleset test per value group; prototype-001 still loads; no engine arithmetic changes needed.

### T18b - prototype-002 and second balance report  `DONE`
Depends on: T18a
Do: create `rulesets/prototype-002.json` as a copy of prototype-001 with only these changes:
S1 Level 2 cost 4 of each resource; S2 Grand Landmark Money 10; S3 Research Lab cost F1 E2 M1 T1.
Set "version": "prototype-002". Do NOT change prototype-001.
Run the same 3 simulations as T18 for prototype-002.
Write `docs/balance-report-prototype-002.md`: a side-by-side table prototype-001 vs prototype-002, what improved, new problems, max 3 new suggestions. Do not change any ruleset.
Accept: prototype-002 loads and passes validation; prototype-001 is unchanged (test); the report has the comparison table.
---

## Milestone M3 - Server

Technical choices for all M3 tasks (not product decisions):
- New module `game-server`: Spring Boot 4.x, Groovy DSL. In T19 pick ONE concrete stable Spring Boot 4.x version that
  works with the Java 25 toolchain and PIN it exactly. No dynamic or "latest" versions. Upgrades are separate tasks.
  Depends on `game-engine`, `game-ruleset-json`, `game-bots`. The engine does not change for M3 unless a task says so.
- Plain Spring WebSocket (`TextWebSocketHandler`) with JSON messages. NO STOMP, no message broker.
- Jackson only in `game-server` and `game-ruleset-json`. Never in `game-engine`.
- Runs IN MEMORY by default (no database needed for friend tests). PostgreSQL only with profile `postgres` (T24).
- Tests with a database use embedded PostgreSQL (e.g. `io.zonky.test:embedded-postgres`) - NO Docker required.
- Settings in `application.yml` under `game.*`: `ruleset` (default `prototype-002`; if the configured ruleset is missing or
  invalid the server FAILS AT STARTUP with a clear error - never a silent fallback to another ruleset),
  `window-duration` (D20), `objective-choice-timeout` (D22), `finished-room-ttl` (default 30m), `lobby-idle-ttl` (default 2h).
- Time is injected (`java.time.Clock` + a scheduler abstraction) so tests never sleep for real durations.
- Command origin is explicit everywhere (model, logs, persistence): `PLAYER` (human, seat from the session),
  `BOT` (server-generated command for a bot seat), `SYSTEM` (StartRound, ResolveRound, objective-timeout choice).
- Two independent versions are sent to clients:
  `stateVersion` = engine GameState (+1 only when the authoritative GameState actually changes);
  `roomVersion` = public server metadata (READY flags, connection status, phaseEndsAt, room status; +1 on every change).
  A gap in either one -> the client requests a snapshot.
- Server error format for REST: `{ "code": "ROOM_FULL", "message": "..." }` with stable codes.
- Tokens, seeds of active games, hidden objectives and private resources are NEVER logged.

### T19 - Spring Boot module + rooms and lobby REST  `DONE`
Depends on: T18b
Docs: Architecture 6.1, 6.8, 6.10; decisions D11, D24.
Do:
- `game-server` module, Spring Boot application, `./gradlew :game-server:bootRun` starts it.
- In-memory `RoomRegistry`. Room: roomId, roomCode, hostSeat, seats (nickname or bot type), rulesetVersion,
  status (LOBBY -> ACTIVE -> FINISHED -> CLOSED), createdAt, seed (set at start).
- Room code: 6 characters from an unambiguous alphabet (no 0/O/1/I), unique among open rooms (generate -> check -> retry).
- Seat token: cryptographically random (SecureRandom, >= 32 bytes, base64url), returned ONCE on create/join.
  Stored only as a hash. Used for host actions now and for the WebSocket session in T22/T23.
- REST:
  - `POST /rooms {nickname}` -> creates room AND joins host as seat 0 -> `{roomCode, seat, token}`
  - `POST /rooms/{code}/join {nickname}` -> `{seat, token}`
  - `GET /rooms/{code}` -> public lobby info (status, seats with nickname or bot type). No tokens, no seed.
  - `POST /rooms/{code}/bots {type: BASELINE|TRADER}` (host, LOBBY only) -> fills the next empty seat (D24)
  - `DELETE /rooms/{code}/seats/{seat}` (host, LOBBY only) -> removes a player or bot (not the host)
  - `POST /rooms/{code}/start` (host) -> needs 4 filled seats and >= 1 human; creates seed (SecureRandom) and
    GameState via GameSetup with the configured ruleset; status ACTIVE.
- Host identified by the token in header `Authorization: Bearer <token>`.
- Cleanup job: FINISHED rooms closed after `finished-room-ttl`, idle LOBBY rooms after `lobby-idle-ttl`.
Accept (tests):
- create returns seat 0 + token; join fills seats 1-3; 5th join -> `ROOM_FULL`
- unknown code -> `ROOM_NOT_FOUND`; join ACTIVE room -> `ROOM_NOT_JOINABLE`
- only host can start / add bots / remove seats -> otherwise `NOT_HOST`
- start with < 4 seats -> `ROOM_NOT_FULL`; start with 0 humans impossible
- bots: add/remove in LOBBY only; after start -> `ROOM_NOT_IN_LOBBY`
- illegal status transitions rejected; codes unique among open rooms (test with a forced collision)
- GET never contains tokens or seed; token hash stored, not the token
- start creates a GameState with 4 players and the configured ruleset version

### T20 - GameRoom: serial command queue, commandSequence, stateVersion  `DONE`
Depends on: T19
Docs: Architecture 6.2, 6.3, 6.9 (stateVersion), 4.3-4.5.
Do:
- One `GameRoom` per ACTIVE room. ONE serial executor per room; `submit(actorSeat, command)` returns a future outcome.
  All state changes happen only inside this executor.
- Every processed command (accepted or rejected) gets the next `commandSequence` (1, 2, 3, ...).
- `stateVersion` increases by exactly 1 only when the authoritative GameState actually changes
  (Accepted AND new state not equal to the old state). No increment for: rejected commands, duplicates,
  accepted no-op results, server-only or transport messages.
- Every processed command records its origin (PLAYER / BOT / SYSTEM) and, for PLAYER and BOT, the seat.
- The application layer builds engine commands and INJECTS the seat from the caller identity.
  Clients can never create `StartRound` / `ResolveRound` (internal commands are only created by the server).
- Room keeps an in-memory list of processed commands (sequence, actor, command, outcome, resulting stateVersion)
  - T24 will persist exactly this.
Accept (tests):
- 4 threads submitting 1000 commands at the same time: no lost update, sequence strictly increasing without gaps,
  final state equals a single-threaded replay of the same commands in sequence order
- rejected command: state and stateVersion unchanged, but it has a sequence number
- accepted no-op (state equal before and after) -> stateVersion unchanged (if no engine command is a no-op today,
  test this rule with a test double of the engine)
- origin is recorded correctly for PLAYER, BOT and SYSTEM commands
- a client command with an internal type is refused before reaching the engine
- the seat in the engine command is always the caller's seat

### T20b - Round flow: timer, READY, objective timeout, bots in rooms  `TODO`
Depends on: T20
Docs: Architecture 6.4; decisions D20, D21, D22, D23, D24; Numbers Sheet "ROUND ORDER".
Do:
- Automatic flow per room: objective choice phase -> (all chosen OR timeout D22: server enqueues ChooseObjectives with
  the first 2 dealt cards for missing seats) -> server enqueues StartRound -> WINDOW with `phaseEndsAt` ->
  ResolveRound -> next StartRound, until Round 14 is resolved -> room FINISHED.
- Timer: at `phaseEndsAt` it ENQUEUES ResolveRound into the same queue. It never changes state directly.
- READY (D21): server-level command (not an engine command, not replayed, changes roomVersion only).
  When all seats are ready, the server enqueues ResolveRound early.
  A shared guard makes sure ResolveRound is enqueued EXACTLY ONCE per round (timer vs READY race).
- Who counts as ready: a human who sent READY; a disconnected human immediately (D23);
  a BOT only when it is QUIESCENT (see bot coordinator). A bot is not ready while it is evaluating or has a
  command queued; if a later state change needs a bot reaction, it becomes not ready again until that is processed.
- Queue order decides the deadline: commands queued before ResolveRound are processed; commands after it get
  `INVALID_PHASE` from the engine. No wall-clock checks in the engine.
- Bot coordinator (D24): bots are asked AGAIN after every state change, not only when the window opens:
  1. after a state-changing command is processed, evaluate each bot seat
  2. each bot may enqueue at most one next action (origin BOT) through the normal queue
  3. repeat until every bot returns "no action" -> bots are quiescent -> READY
  Reuse the existing bot runaway protection (max actions per bot per round), so a broken bot cannot loop forever.
  Deterministic given the state.
- Disconnected seats (D23): send nothing; automatic engine rules still apply.
Accept (tests, with an injected clock):
- full game with 1 scripted human + 3 bots runs from start to FINISHED with only timers
- a bot passes first, then gets a human trade offer later -> the bot gets a new decision and answers it
- early READY resolution cannot overtake pending bot actions (humans all READY while a bot still has an action)
- a bot that keeps producing actions is stopped by the runaway limit and the round still resolves
- all 4 READY -> round resolves before the timer; READY can be cancelled; READY resets next round
- timer and last READY at the same moment -> exactly one ResolveRound
- command queued just before ResolveRound is accepted; command queued just after -> INVALID_PHASE
- objective timeout keeps the first 2 dealt cards; game then starts Round 1
- after Round 14 resolution no StartRound is enqueued and the room is FINISHED

### T21 - PlayerGameView + privacy projector  `TODO`
Depends on: T20b
Docs: Architecture 6.5; decisions D6, D7; Concept section 37 (information rules).
Do:
- `PlayerGameView` records (client-safe, JSON-friendly) built by
  `ViewProjector.project(GameState state, RoomViewContext room, int seat)` - a PURE function of immutable inputs.
  `RoomViewContext` (immutable snapshot of server metadata) holds phaseEndsAt, READY per seat, connection status per
  seat, room status, roomVersion. These values stay OUT of the engine GameState.
  Own: resources, Money, reserved Money, own bids, dealt/kept objectives, own offers, own contracts, crisis policy,
  upkeep priority. Public: round, phase, phaseEndsAt, READY flags, levels, buildings, visible Prestige,
  Contracts Broken counters, market prices, current event + warning, public projects with contributions,
  opportunity cards (NOT bids), formal contracts (as Architecture 6.5), final result after the game.
  Not visible: other players' resources, Money, reserved Money, objectives, bids, offers they are not part of.
- `NoticeProjector`: domain events -> per-seat, client-safe notices. Private events only to the seats involved.
  Raw domain events are never sent to clients.
- `docs/VIEW.md`: every field of PlayerGameView with "who sees it".
Accept (tests):
- for every seat and states from real bot games (several rounds, incl. bids, offers, contracts, final result):
  serialize the view to JSON and assert that hidden data is PHYSICALLY absent (no opponent holdings, objective ids,
  bid amounts, private offers) - not just flagged
- a trade offer between seats 1 and 2 appears only in the views of seats 1 and 2
- notices: a private event never reaches an uninvolved seat
- the projector never mutates its inputs; same inputs -> same view

### T22 - WebSocket protocol  `TODO`
Depends on: T21
Docs: Architecture 6.6, 6.9; `docs/VIEW.md`.
Do:
- Endpoint `/ws`. The first client message must be `HELLO {protocolVersion, token}` (token NOT in the URL,
  so it is not logged by proxies). Wrong protocolVersion -> `PROTOCOL_UNSUPPORTED` and close. Bad token -> close.
- Client -> server: `{protocolVersion, commandId, commandType, payload}`. No playerId or seat field; unknown fields
  rejected. `commandType` maps to player commands only (+ READY).
- Server -> client: FULL_SNAPSHOT, STATE_UPDATE, ROOM_UPDATE, COMMAND_ACCEPTED, COMMAND_REJECTED, ROUND_WARNING,
  ROUND_RESOLVED, GAME_FINISHED, NOTICE. Every message carries `stateVersion` and `roomVersion`.
  ROOM_UPDATE is sent when only server metadata changes (READY, connection status, phaseEndsAt).
- v1: STATE_UPDATE contains the full PlayerGameView of that seat (no deltas). After each accepted command every
  connected seat gets its own STATE_UPDATE.
- Client may send `SNAPSHOT_REQUEST` any time (e.g. after a stateVersion gap).
- `docs/PROTOCOL.md`: all message types with JSON examples (M4 builds the React client from this file).
Accept (tests with a real WebSocket client):
- HELLO required; wrong version and bad token are refused
- accepted command that changes the state -> COMMAND_ACCEPTED to the sender + STATE_UPDATE to all, stateVersion +1
- accepted no-op -> COMMAND_ACCEPTED, stateVersion unchanged
- READY -> ROOM_UPDATE to all, roomVersion +1, stateVersion unchanged
- rejected command -> COMMAND_REJECTED with the engine rejection code, stateVersion unchanged
- STATE_UPDATE for seat X never contains another seat's private data (reuse T21 checks on the wire)
- a payload with a "seat" field is rejected
- PROTOCOL.md examples are valid (parsed in a test)

### T23 - commandId idempotency + reconnect and session identity  `TODO`
Depends on: T22
Docs: Architecture 6.6, 6.7, 6.10; decision D23.
Do:
- Room stores an outcome per processed commandId: result type, rejection code, resulting stateVersion.
- Duplicate commandId (same seat): return the SAME outcome. No engine call, no new sequence, no stateVersion change.
  Same commandId from a different seat -> rejected.
- Session identity: seat comes only from the authenticated WebSocket session (HELLO token).
- Reconnect: HELLO with the same token -> same seat, FULL_SNAPSHOT, continue. A new connection for a seat replaces the
  old one (old one closed).
- Disconnect -> seat is passive (D23) and counts as READY (D21) until it reconnects.
- Tokens invalid after the room is CLOSED. Tokens never appear in logs (test with a log capture).
Accept (tests):
- send the same BuyFromMarket twice with one commandId -> bought once, both replies identical
- disconnect, reconnect with token -> same seat, FULL_SNAPSHOT with current stateVersion
- a client cannot act for another seat in any way (no seat field; other seat's token not known)
- disconnected seat does not block early round end
- closed room -> token refused
- log capture contains no token

### T24 - Command log persistence (PostgreSQL profile) + replay  `TODO`
Depends on: T23
Docs: Architecture 7.
Do:
- `MatchLog` interface. Default: in-memory implementation (no database). Profile `postgres`: PostgreSQL implementation
  with Flyway migrations for `matches`, `match_players`, `match_commands`, `match_results` (Architecture 7.3,
  incl. commandSchemaVersion = 1, payload JSONB).
- Every processed command is written in commandSequence order, INCLUDING SYSTEM commands (StartRound, ResolveRound,
  objective-timeout choices) and rejected commands (marked; replay skips them). Each row stores the origin
  (PLAYER / BOT / SYSTEM) and the seat when there is one. READY is not logged.
- Replay is driven ONLY by commandSequence - never by timestamp or database insertion order.
- `ReplayService`: seed + rulesetVersion + accepted commands ordered by commandSequence -> GameState.
- Tests use embedded PostgreSQL (no Docker).
Accept (tests):
- full bot game through the server, then load the log FROM THE DATABASE and replay -> final state equals the live final state
- replay orders by commandSequence even if rows were inserted in a different order (test inserts shuffled rows)
- rejected commands stored but skipped in replay
- server works without the `postgres` profile (in-memory) and with it
- `docs/SERVER.md`: how to run in memory and with PostgreSQL

### T24a - M3 acceptance: full multiplayer server test  `TODO`
Depends on: T24
Do: one end-to-end test through REST + WebSocket (real server on a random port, injected clock):
create room (host = human 1) -> 2 humans join -> host adds 1 bot -> start (3 humans + 1 bot = 4 seats) ->
objectives chosen (one by timeout) -> Round 1 starts -> 3 WebSocket clients send commands concurrently while the bot
acts through the bot coordinator -> private views verified for each seat -> one client disconnects and reconnects
with its token -> FULL_SNAPSHOT -> a duplicate command returns the same outcome -> all READY ends a round early ->
timer ends another round -> game runs to FINISHED -> GAME_FINISHED received -> log replay reproduces the final state.
Also: `docs/SERVER.md` section "Try it yourself" - start the server and play 1 human + 3 bots with a simple
WebSocket tool (exact commands).
Accept: the test passes 20 times in a row (no flakiness); SERVER.md steps work.

## Milestone M4 - Web client (detailed later)

T25 React + Vite skeleton, connect to a room -
T26 Game screen: resources, market, buildings, events -
T27 Trade and contract UI -
T28 Projects, bids, objectives, end screen -
T29 First human test with 4 players (owner).
