# Project setup (one time, about 20 minutes)

## 1. Install tools (once per computer)

| Tool | Why | Check |
|------|-----|-------|
| Any JDK 17+ | runs Gradle (JDK 25 is downloaded automatically by the Gradle toolchain) | `java -version` |
| Git | version control | `git --version` |
| Node.js (LTS) | needed by Claude Code and Codex CLI | `node --version` |
| Claude Code CLI | implementation agent | `claude --version` |
| Codex CLI | review agent | `codex --version` |
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
scripts/codex-review.sh, scripts/run-until.sh
scripts/lib-usage.sh, scripts/usage-report.sh
docs/TASKS.md, docs/PROGRESS.md, docs/REVIEW.md
docs/last_city_standing_game_idea_GPT_4-final.txt         (concept v4)
docs/city_trade_game_architecture_planning_claude_v3-final.txt
docs/city_trade_game_numbers_sheet_claude_v2-final.txt
rulesets/.gitkeep
```

## 3. Product decisions

D1-D12 in `docs/TASKS.md` are already approved. Nothing to do.
If you change your mind later, edit the table; agents follow it from the next task.

## 4. Run until M1 is done

```
scripts/run-until.sh M1
```

What happens:
- One fresh Claude Code session per task (T00, T01, ... T14).
- Each session: implement -> test -> Codex review -> fix -> review ... until PASS -> commit.
- The loop stops only when:
  - all tasks up to M1 are `DONE` (success), or
  - a task is `BLOCKED` (read `docs/PROGRESS.md` -> "Questions for owner"), or
  - the same task did not finish in 3 sessions, or
  - runaway limits are reached (60 sessions, 10 review rounds per task).
- Full log: `.review/run-until-M1.log`. Reviews: `.review/T05-round2.md`, etc.

After fixing a blocker (answer the question in PROGRESS.md, set the task back to
`TODO` in TASKS.md), just run `scripts/run-until.sh M1` again. It continues
where it stopped.

Tip: start it in the evening and check the log in the morning.
To keep the computer awake: macOS `caffeinate -i scripts/run-until.sh M1`,
Linux `systemd-inhibit scripts/run-until.sh M1`.

## 5. Token usage

Every agent run adds one line to `.review/usage.csv`:
- Claude: one line per task session (tokens + cost in USD + minutes).
- Codex: one line per review round (tokens + minutes; Codex reports no cost).

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

- `codex exec --help`: the scripts use `--json`, `--sandbox workspace-write` and
  `-o <file>` (write the final message to a file). If a flag has another name,
  change it in `scripts/codex-review.sh` (1 line) and in task T00.
- `claude --help`: the loop uses
  `claude -p "<prompt>" --permission-mode acceptEdits --output-format json`.
- If Claude stops long commands too early, check the timeout setting names in
  `.claude/settings.json` against the current Claude Code docs.

## 8. Notes

- `.claude/settings.json` allows gradle, git add/commit, codex and the review script
  without asking, and blocks push / hard reset / rebase / `rm -rf`.
- Codex may run Gradle during review, but the script fails the review if Codex
  changes any project file.
- Tokens: AGENTS.md + CLAUDE.md load every session (~2.5k tokens). A fresh session
  per task keeps each context small. Codex reads REVIEW.md only.
