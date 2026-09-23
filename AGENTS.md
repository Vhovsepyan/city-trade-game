# AGENTS.md - Untitled City Trade Game

Shared rules for all AI agents. Keep this file short; details live in `docs/`.

## 1. Roles

- **Human owner** makes product decisions (rules, scoring, balance values, scope, platforms).
- **Claude** (Claude Code) = implementation agent. Writes code, tests, fixes.
- **Codex** = independent review agent. Reviews the real repository, never only a summary.

The owner does not write code. Agents do not change the game design on their own.

## 2. Where things are

| File | What it is |
|------|------------|
| `docs/TASKS.md` | Ordered task list + approved product decisions (D1-D12). **Start here.** |
| `docs/PROGRESS.md` | Short log: what is done, what is next. Read it at the start of every session. |
| `docs/REVIEW.md` | Review rules and output format (for Codex). |
| `scripts/` | `codex-review.sh` (review), `run-until.sh` (autonomous loop), `usage-report.sh` (tokens). |
| `docs/last_city_standing_game_idea_GPT_4-final.txt` | Game concept (behavior and intent). |
| `docs/city_trade_game_architecture_planning_claude_v3-final.txt` | Architecture. |
| `docs/city_trade_game_numbers_sheet_claude_v2-final.txt` | Numbers Sheet v2 (prototype-001 values). |
| `rulesets/prototype-001.json` | Balance values as data (created in task T02). |

**Read only what the task needs.** Use the section index below instead of reading whole documents.

| Topic | Concept (sections) | Numbers Sheet (sections) | Architecture (sections) |
|-------|--------------------|--------------------------|-------------------------|
| Cities, resources, storage | 5, 6, 8 | 1-5 | - |
| Levels, upkeep, Strained | 9, 10, 11 | 6-9 | - |
| Round structure | 12, 13 | "ROUND ORDER (exact)" | 4.9 |
| Trading, offers | 14, 15 | - | 5 |
| Formal contracts | 16, 17 | 13 | 5.6 |
| Market | 18, 19 | 11-12 | - |
| Events, crises | 20, 21 | 14 | - |
| Public projects | 22, 23 | 16 | - |
| Bids, opportunities | 24, 25 | 17 | - |
| Objectives | 26, 27, 28 | 15 | - |
| Prestige, end, tiebreak | 4, 29, 30, 31, 40 | 18, 20 | - |
| Setup | - | 21 | - |
| Engine design | - | - | 4 |
| Server | - | - | 6, 7 |
| Bots, simulation | - | - | 8 |
| Tests | - | - | 13 |

**Priority when sources conflict:**
1. Latest explicit instruction from the owner.
2. Product decisions D1-D12 in `docs/TASKS.md`.
3. `rulesets/*.json` for numbers (after T02), otherwise the Numbers Sheet.
4. Numbers Sheet for exact values and round order.
5. Architecture for technical design.
6. Concept for game behavior and intent.

If a real contradiction remains and it affects game behavior: stop that part, name the exact sections, propose a fix, and write it under "Questions for owner" in `docs/PROGRESS.md`. Continue with other work if possible. Never invent a silent resolution.

## 3. Tech stack (fixed)

- Java 25, **stable features only, no preview features**.
- Gradle, **Groovy DSL** (`build.gradle`, not `.kts`), multi-module.
- JUnit 5 (+ AssertJ allowed).
- Later: Spring Boot, WebSocket, PostgreSQL, React + TypeScript + Vite, Docker.

Do not add without an explicit task: Kafka, Redis, Kubernetes, microservices, Unity/Godot, Electron/Tauri, accounts, matchmaking, Android code.

## 4. Modules

```
game-engine        pure Java rules (no Spring, no JSON, no DB, no network)
game-ruleset-json  loads + validates rulesets/*.json -> Ruleset (Jackson allowed here)
game-bots          simple deterministic bots (depends on engine)
game-sim           simulation runner + metrics (engine, bots, ruleset-json)
game-server        Spring Boot (later)
web-client         React (later)
```

`game-engine` must never depend on another module or on Spring, Jackson, HTTP, DB.

## 5. Engine rules (must always hold)

- Shape: `GameResult apply(GameState state, GameCommand command, Ruleset ruleset)`.
- `GameState` is a value: old state + command -> new state.
- `GameResult` is sealed: `Accepted(state, events)` or `Rejected(code, detail)`.
  Rejected = state unchanged, no events. Use `RejectionCode` enum values.
- **Atomic commands:** validate everything first, then apply the whole change once.
- **Deterministic:** same seed + ruleset + ordered commands = same state.
  One engine-owned seeded random source. No `Math.random`, no wall clock, no
  iteration over unordered collections that can change results.
- **No balance numbers in Java.** Every tunable value comes from `Ruleset`.
  Bad: `prestige += 3;` Good: `prestige += ruleset.projects().largestContributorPrestige();`
- **Values in JSON, behavior in Java.** No logic/scripting in JSON.
- `ResolveRound` is one command with one explicit, tested step order
  (Numbers Sheet "ROUND ORDER"). Never change this order as a side effect.
- Rulesets are immutable after use: new balance = new file (`prototype-002.json`).

## 6. Game invariants (short list)

14 rounds, 4 fixed cities, no elimination - one combined trade/build window -
traded resources usable immediately - storage checked only at Round Resolution -
no upkeep with own specialty, no upkeep with Money - market prices fixed during the
window - instant trades binding, verbal promises not enforced, formal contracts
enforced - no contract obligation after Round 14 - bid 0 = pass, tied highest bid =
no winner, only the winner pays, bids reserve Money - resources/Money never give
Prestige - hidden objectives score at the end - Money is not a tiebreaker.

Trade offer statuses: `OPEN, ACCEPTED, REJECTED, EXPIRED, CANCELLED, INVALID`.
Only transitions from `OPEN` are allowed. Revalidate resources on accept; if missing -> `INVALID`.
Counteroffer = old offer `REJECTED` (closeReason `COUNTEROFFER`) + new offer with `parentOfferId`.

## 7. Scope

Build only what the current task asks. No "useful extras", no hypothetical
future player counts, no speculative abstractions. Report ideas under
"Suggestions" in `docs/PROGRESS.md` instead of building them.

## 8. Tests

A task is not done because it compiles. Every rule change needs tests.
Never make tests pass by deleting, disabling or weakening them, adding sleeps,
or swallowing exceptions. If a spec change makes a test obsolete, explain why
and keep equal or stronger coverage.

## 9. Commands

```
./gradlew build                      # full build + all tests
./gradlew :game-engine:test          # engine tests only
./gradlew :game-engine:test --tests "*BidResolutionTest"   # one test class
./gradlew :game-sim:run --args="--ruleset prototype-001 --games 1000 --seed 1"   # after T17
scripts/codex-review.sh <TASK_ID> <ROUND>   # Codex review, ROUND = 1, 2, 3, ... until PASS
scripts/run-until.sh M1                      # owner: run tasks automatically until M1 is done
scripts/usage-report.sh [TASK_ID]            # token usage totals from .review/usage.csv
```

## 10. Git

- Inspect `git status` before and after work. Do not touch unrelated changes.
- After a task passes review: `git add` the task files and commit with a
  **short single-line message** (e.g. `T05 add production and upkeep`).
  No multi-line commit messages.
- Never: push, force push, rewrite history, delete branches, change remotes.
- Never commit secrets, tokens, passwords. `.review/` stays uncommitted.

## 11. Code style

Explicit domain names, records for immutable data, sealed types for closed
alternatives, enums for finite states, small cohesive classes, composition over
inheritance. No God classes, no reflection-based rule engines. Comments explain
*why*, not syntax.

## 12. What agents may and may not decide

May decide: helper methods, package layout, test structure, algorithms inside
the architecture, ordinary refactoring with tests.

Must NOT decide: new mechanics, changed scoring, changed economic values, new
resources or cities, removing mechanics, scope expansion, platform priorities.
Write these as questions in `docs/PROGRESS.md`.
