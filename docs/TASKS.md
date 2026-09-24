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

### T18 - First balance report  `TODO`
Depends on: T17
Do: run 1000+ games (baseline-only and trader mix) on prototype-001. Write `docs/balance-report-prototype-001.md`: key numbers, problems found, 3-5 suggested value changes with reasons. **Do not change the ruleset.** The owner decides.

---

## Milestone M3 - Server (tasks will be detailed when M2 is done)

T19 Spring Boot module + rooms REST (create/join, lobby lifecycle) -
T20 GameRoom serial queue, commandSequence, timer through the queue -
T21 WebSocket protocol (protocolVersion, stateVersion, snapshots) -
T22 View projector + privacy tests -
T23 commandId idempotency (stored outcomes) + reconnect tokens -
T24 Command log persistence (PostgreSQL) + replay.

## Milestone M4 - Web client (detailed later)

T25 React + Vite skeleton, connect to a room -
T26 Game screen: resources, market, buildings, events -
T27 Trade and contract UI -
T28 Projects, bids, objectives, end screen -
T29 First human test with 4 players (owner).
