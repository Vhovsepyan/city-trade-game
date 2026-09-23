# PROGRESS.md

Short log for the next session. Newest entry on top. Max ~10 lines per entry.
Keep only the last 10 entries; summarize older ones in one line under "Earlier".

## Current state
- Milestone: M0
- Next task: T04
- Target ruleset: prototype-001 (`rulesets/prototype-001.json`)
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
- (none)

## Log

<!-- Template:
### T0X - <title> - DONE (review round N)
- What: ...
- Files: ...
- Tests: ...
- Notes / P3 items: ...
-->

### T03 - Core state, setup, deterministic random - DONE (review round 1)
- What: `CityType`, `GameRandom` (SplitMix64, immutable value inside `GameState`), `PlayerState`,
  `GameState`, `MarketPrices`, `EventWarning`, `GameSetup.create(seed, ruleset)` (random draws in
  Numbers Sheet 21 step order: cities, objectives, events, projects, opportunities), `ChooseObjectives`.
- For `ChooseObjectives` T03 already created minimal `GameCommand`, `GameResult`, `RejectionCode`,
  `DomainEvent` (package `citytrade.engine.command`); handler is `setup.ObjectiveChoice`. T04 extends
  these, adds `GameEngine.apply` and the phase check (`INVALID_PHASE`) for ChooseObjectives.
- Tests: GameRandomTest (SplitMix64 reference values), GameSetupTest, ObjectiveChoiceTest (engine,
  Java-built `TestRulesets`), PrototypeSetupTest (real prototype-001 file).

### T02 - Ruleset model + prototype-001.json + loader + validation - DONE (review round 2)
- What: `Ruleset` records in `citytrade.engine.ruleset` (sealed `BuildingEffect`, `EventCard`,
  `ObjectiveCard`: the kind picks the engine behavior, the fields hold values). Also `Resource`
  and `ResourceBundle` (F, E, M, T, Money) in `citytrade.engine`; T03 should reuse them.
- `rulesets/prototype-001.json` = all Numbers Sheet v2 values. Loader `RulesetLoader` (Jackson 2.22.3,
  mixins keep annotations out of the engine; strict: missing/null/unknown/mistyped values fail).
  `load(Path)` also checks that "version" matches the file name.
- Validation reports all errors with paths. D5 (deal 3, keep 2) is enforced as a fixed rule.
- Tests: one test per Numbers Sheet section (1-21), one failing ruleset per validation rule, strict parsing.
- Round 1 fix: D5 deal count enforced (was only configurable).

### T01 - Gradle multi-module skeleton - DONE (review round 2)
- What: Gradle 9.7.1 wrapper (with sha256), Groovy DSL, Java 25 toolchain + foojay 1.0.0,
  JUnit 5.14.4, modules game-engine, game-ruleset-json, game-bots, game-sim.
- Engine rule: task `:game-engine:verifyEngineHasNoDependencies` runs in `check` and fails on
  any non-test dependency (checked by hand: it fails with a project dep and a Jackson dep).
- Tests: one smoke test per module, asserts the test JVM is Java 25.
- Notes: round 1 finding (missing engine dep in game-ruleset-json) was a Codex misread; the
  dependency existed. Codex sandbox cannot run Gradle (no network, no cache access).

### T00 - Environment and repository check - DONE (review not needed)
- What: checked Java, git, node, codex, .gitignore, spec docs, scripts; Codex smoke test OK.
- Fix: scripts marked executable in the git index (100755).
- Files: docs/PROGRESS.md, docs/TASKS.md, scripts/*.sh (mode only).

## Earlier
- (none)
