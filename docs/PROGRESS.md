# PROGRESS.md

Short log for the next session. Newest entry on top. Max ~10 lines per entry.
Keep only the last 10 entries; summarize older ones in one line under "Earlier".

## Current state
- Milestone: M0
- Next task: T02
- Target ruleset: prototype-001 (will be created in T02)

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
