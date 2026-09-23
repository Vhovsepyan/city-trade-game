#!/usr/bin/env bash
# Runs Claude Code in a loop, one fresh session per task, until every task
# up to the given milestone is DONE, or a task is BLOCKED.
# Usage: scripts/run-until.sh <MILESTONE>        e.g.  scripts/run-until.sh M1
# Env:   MAX_SESSIONS (default 60)  - runaway protection
set -uo pipefail

TARGET="${1:?Usage: scripts/run-until.sh <MILESTONE, e.g. M1>}"
TNUM="${TARGET#M}"
[[ "$TNUM" =~ ^[0-9]+$ ]] || { echo "Milestone must look like M1"; exit 2; }
MAX_SESSIONS="${MAX_SESSIONS:-60}"

command -v claude >/dev/null 2>&1 || { echo "claude CLI not found in PATH"; exit 2; }
ROOT="$(git rev-parse --show-toplevel)" || { echo "not a git repository"; exit 2; }
cd "$ROOT"
source scripts/lib-usage.sh
mkdir -p .review
LOG=".review/run-until-${TARGET}.log"

# Prints "<TASK_ID> <STATUS>" for every task in milestones M0..TARGET, in file order.
task_list() {
  awk -v t="$TNUM" '
    /^## Milestone M[0-9]+/ { match($0, /M[0-9]+/); m = substr($0, RSTART + 1, RLENGTH - 1) + 0; inm = (m <= t) }
    inm && /^### T[0-9]+/ {
      st = "UNKNOWN"
      if (match($0, /`[A-Z ]+`/)) st = substr($0, RSTART + 1, RLENGTH - 2)
      print $2, st
    }' docs/TASKS.md
}

LAST=""
TRIES=0
for (( i = 1; i <= MAX_SESSIONS; i++ )); do
  NEXT=""; BLOCKED=""
  while read -r id st; do
    case "$st" in
      BLOCKED) BLOCKED="$id"; break ;;
      TODO|"IN PROGRESS") [[ -z "$NEXT" ]] && NEXT="$id" ;;
    esac
  done < <(task_list)

  if [[ -n "$BLOCKED" ]]; then
    echo ">>> $BLOCKED is BLOCKED. See docs/PROGRESS.md. Stopping." | tee -a "$LOG"; exit 1
  fi
  if [[ -z "$NEXT" ]]; then
    echo ">>> All tasks up to $TARGET are DONE." | tee -a "$LOG"
    scripts/usage-report.sh | tee -a "$LOG"; exit 0
  fi

  if [[ "$NEXT" == "$LAST" ]]; then
    TRIES=$(( TRIES + 1 ))
    if (( TRIES >= 3 )); then
      echo ">>> $NEXT not finished after 3 sessions. Stopping for the owner." | tee -a "$LOG"; exit 1
    fi
  else
    TRIES=0
  fi
  LAST="$NEXT"

  echo ">>> Session $i: $NEXT  ($(date '+%Y-%m-%d %H:%M'))" | tee -a "$LOG"
  RESULT_JSON=".review/${NEXT}-session${i}.json"
  claude -p "Autonomous mode (see CLAUDE.md). Do task ${NEXT} completely: steps 1-9 including the Codex review loop until PASS and the commit. One task only. If the task was IN PROGRESS, continue from the current repository state and docs/PROGRESS.md. End with the RESULT line." \
    --permission-mode acceptEdits --output-format json > "$RESULT_JSON" 2>> "$LOG"
  CLAUDE_EXIT=$?

  # Show and log Claude's final message, then log token usage.
  usage_result_text "$RESULT_JSON" | tee -a "$LOG"
  STATUS="exit${CLAUDE_EXIT}"
  grep -q "RESULT: ${NEXT} DONE" "$RESULT_JSON" && STATUS="DONE"
  grep -q "RESULT: ${NEXT} BLOCKED" "$RESULT_JSON" && STATUS="BLOCKED"
  usage_log_claude "$NEXT" "$i" "$RESULT_JSON" "$STATUS"
  scripts/usage-report.sh "$NEXT" | tail -n 3 | tee -a "$LOG"
done

echo ">>> MAX_SESSIONS ($MAX_SESSIONS) reached. Stopping." | tee -a "$LOG"
exit 1
