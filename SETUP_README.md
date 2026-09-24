# Project setup and running

## 1. Install tools (once per computer)

| Tool | Why | Check |
|------|-----|-------|
| Any JDK 17+ | runs Gradle (JDK 25 is downloaded automatically by the Gradle toolchain) | `java -version` |
| Git | version control | `git --version` |
| Node.js (LTS) | needed by Claude Code and Codex CLI | `node --version` |
| Claude Code CLI | agent (role in scripts/agents.conf) | `claude --version` |
| Codex CLI | agent (role in scripts/agents.conf) | `codex --version` |
| IntelliJ IDEA (optional) | to look at the code | - |

Gradle does NOT need to be installed: task T01 creates the Gradle wrapper
(`./gradlew`). If T01 cannot create it, install Gradle once, then rerun.

Log in once: run `claude` and `codex` one time each and finish the login.

**Windows:** run everything in **Git Bash** or **WSL** (the scripts are bash).
Claude Code on Windows uses Git Bash anyway.

## 2. Create the repository

```
mkdir city-trade-game
cd city-trade-game
git init
# unzip city_trade_game_agent_setup.zip here (AGENTS.md must be in this folder)
chmod +x scripts/*.sh
git add .
git commit -m "Add agent setup and specs"
```

Files after unzip:

```
AGENTS.md, CLAUDE.md, .gitignore, .claude/settings.json
scripts/agents.conf, scripts/lib-agents.sh, scripts/run-until.sh, scripts/review.sh
scripts/lib-usage.sh, scripts/usage-report.sh
docs/TASKS.md, docs/PROGRESS.md, docs/REVIEW.md
docs/last_city_standing_game_idea_GPT_4-final.txt         (concept v4)
docs/city_trade_game_architecture_planning_claude_v3-final.txt
docs/city_trade_game_numbers_sheet_claude_v2-final.txt
rulesets/.gitkeep
```

## 3. Product decisions

All product decisions in `docs/TASKS.md` are approved. Nothing to do.
If you change your mind later, edit the table; agents follow it from the next task.

## 4. Roles and running

Roles are set in `scripts/agents.conf`:

```
IMPLEMENTER=codex      # writes code
REVIEWER=claude        # reviews, never edits
CLAUDE_MODEL=sonnet
```

Switch roles any time by editing these two lines (e.g. when one tool's limit is low).
Nothing else changes.

Run:

```
scripts/run-until.sh M2
```

For every task the SCRIPT does:
implementer session -> `./gradlew build` -> review -> (fix session -> build -> review)*
-> mark DONE -> commit (message written by the implementer).
Agents never call each other and never commit.

The loop stops only when:
- all tasks up to the milestone are `DONE`, or
- a task is `BLOCKED` (read `docs/PROGRESS.md` -> "Questions for owner"), or
- safety limits are reached (see `scripts/agents.conf`).

Usage limits: if an agent reports a usage/rate limit, the script waits
`LIMIT_WAIT_MINUTES` and retries the same step (up to `LIMIT_MAX_WAITS` times).

Rerun the same command after a stop; it continues where it stopped
(it reuses existing review rounds in `.review/`).

## 5. Token usage

Every agent run adds one line to `.review/usage.csv`:
- One line per agent session (implement, fix, review), for both Claude and Codex.
- Claude lines include cost in USD; Codex reports tokens only.

See totals any time:

```
scripts/usage-report.sh          # all tasks
scripts/usage-report.sh T05      # one task
```

`run-until.sh` also prints the task total after each session and the full
report at the end.

Notes:
- Only runs through the scripts are counted. For your own interactive Claude
  sessions use `/cost` (or `/usage`) inside Claude Code.
- On a subscription plan (Claude Max, ChatGPT Plus/Pro) the USD cost is what the
  same tokens would cost on the API; you are not billed extra. Tokens still show
  how heavy each task was.

## 6. Your job (owner)

- Answer questions in `docs/PROGRESS.md`.
- After M1: `scripts/run-until.sh M2` (bots + simulation + balance report).
- After T18: read `docs/balance-report-prototype-001.md` and decide value changes,
  e.g. "Create prototype-002 with project winner Prestige 2 and rerun the simulation."

## 7. Check once (tool versions differ)

- `codex exec --help`: `scripts/lib-agents.sh` uses `--json`, `--sandbox workspace-write`,
  `-c sandbox_workspace_write.network_access=true` (Gradle needs network) and `-o <file>`.
- `claude --help`: it uses `-p`, `--output-format json`, `--model`, `--permission-mode acceptEdits`
  (implementer) and `--allowedTools` / `--disallowedTools` (reviewer, read-only).
- If a flag has another name, change it in `scripts/lib-agents.sh` only.
- If Claude stops long commands too early, check the timeout setting names in
  `.claude/settings.json` against the current Claude Code docs.

## 8. Notes

- `.claude/settings.json` allows gradle, git add/commit, codex and the review script
  without asking, and blocks push / hard reset / rebase / `rm -rf`.
- The reviewer may run Gradle, but the script fails the review if the reviewer
  changes any project file.
- Tokens: AGENTS.md + CLAUDE.md load every session (~2.5k tokens). A fresh session
  per task keeps each context small. Codex reads REVIEW.md only.
