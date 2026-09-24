#!/usr/bin/env bash
# Orchestrator: runs tasks up to a milestone with the roles from scripts/agents.conf.
#   implementer session -> build -> review -> (fix session -> build -> review)* -> commit
# The SCRIPT runs the build, the review and the commit. Agents never call each other.
# Usage: scripts/run-until.sh <MILESTONE>      e.g. scripts/run-until.sh M2
set -uo pipefail
TARGET="${1:?Usage: scripts/run-until.sh <MILESTONE, e.g. M2>}"
TNUM="${TARGET#M}"; [[ "$TNUM" =~ ^[0-9]+$ ]] || { echo "Milestone must look like M2"; exit 2; }
cd "$(git rev-parse --show-toplevel)" || exit 2
source scripts/lib-agents.sh
mkdir -p .review
LOG=".review/run-until-${TARGET}.log"
log() { echo "$*" | tee -a "$LOG"; }

log "=== run-until $TARGET  implementer=$IMPLEMENTER  reviewer=$REVIEWER  ($(date '+%Y-%m-%d %H:%M'))"

task_list() {
  awk -v t="$TNUM" '
    /^## Milestone M[0-9]+/ { match($0, /M[0-9]+/); m = substr($0, RSTART + 1, RLENGTH - 1) + 0; inm = (m <= t) }
    inm && /^### T[0-9]+[a-z]?/ { st = "UNKNOWN"; if (match($0, /`[A-Z ]+`/)) st = substr($0, RSTART + 1, RLENGTH - 2); print $2, st }' docs/TASKS.md
}

# Latest review round already done for a task (0 if none) - allows resuming.
last_round() {
  local n=0 f r
  for f in .review/"$1"-round*.md; do
    [[ -f "$f" ]] || continue
    r="${f##*-round}"; r="${r%.md}"; [[ "$r" =~ ^[0-9]+$ ]] && (( r > n )) && n=$r
  done
  echo "$n"
}

implementer_prompt() {  # <task> <mode: implement|fix-review|fix-build> <file>
  local task="$1" mode="$2" file="$3" what
  case "$mode" in
    implement)  what="Implement task ${task}." ;;
    fix-review) what="Fix ALL P0/P1/P2 findings of task ${task} listed in ${file}. Do not change anything unrelated." ;;
    fix-build)  what="The build for task ${task} is failing. Fix it. Build output: ${file}." ;;
  esac
  echo "You are the IMPLEMENTER. Follow docs/IMPLEMENTER.md and AGENTS.md.
${what}
Do NOT run a review and do NOT commit: the script does that.
End your final message with exactly one line:
RESULT: ${task} READY   or   RESULT: ${task} BLOCKED - <reason>"
}

run_implementer() {  # <task> <label> <mode> <file>  -> 0 READY, 1 failed, 2 BLOCKED
  local task="$1" label="$2"
  agent_run_with_wait "$IMPLEMENTER" implement "$task" "$label" "$(implementer_prompt "$task" "$3" "$4")" | tee -a "$LOG"
  local rc=${PIPESTATUS[0]} out=".review/${task}-${label}.out"
  grep -q "RESULT: ${task} BLOCKED" "$out" 2>/dev/null && { set_task_status "$task" "BLOCKED"; return 2; }
  (( rc != 0 )) && return 1
  return 0
}

build_ok() {  # <task>
  ./gradlew build > ".review/$1-tests.txt" 2>&1
}

commit_task() {  # <task>
  local task="$1" msg
  set_task_status "$task" "DONE"
  msg="$(head -n 1 ".review/${task}-commit.txt" 2>/dev/null | tr -d '\r')"
  [[ -z "$msg" ]] && msg="${task} done"
  git add -A && git commit -q -m "$msg" && log ">>> Committed: $msg"
}

SESSIONS=0
while true; do
  NEXT=""; BLOCKED=""
  while read -r id st; do
    case "$st" in
      BLOCKED) BLOCKED="$id"; break ;;
      TODO|"IN PROGRESS") [[ -z "$NEXT" ]] && NEXT="$id" ;;
    esac
  done < <(task_list)
  [[ -n "$BLOCKED" ]] && { log ">>> $BLOCKED is BLOCKED. See docs/PROGRESS.md."; exit 1; }
  [[ -z "$NEXT" ]] && { log ">>> All tasks up to $TARGET are DONE."; scripts/usage-report.sh | tee -a "$LOG"; exit 0; }

  T="$NEXT"; set_task_status "$T" "IN PROGRESS"
  ROUND=$(last_round "$T")
  log ">>> Task $T (previous review rounds: $ROUND)"

  # 1. Implement (or continue after the last review)
  if (( ROUND > 0 )) && grep -Eq '^REVIEW RESULT: PASS[[:space:]]*$' ".review/${T}-round${ROUND}.md"; then
    log ">>> $T already passed review round $ROUND; committing"
    build_ok "$T" && { commit_task "$T"; continue; }
  fi
  SESSIONS=$((SESSIONS + 1)); (( SESSIONS > MAX_SESSIONS )) && { log ">>> MAX_SESSIONS reached"; exit 1; }
  if (( ROUND == 0 )); then
    run_implementer "$T" "impl" implement ""; rc=$?
  else
    run_implementer "$T" "fix${ROUND}" fix-review ".review/${T}-round${ROUND}.md"; rc=$?
  fi
  (( rc == 2 )) && { log ">>> $T BLOCKED by implementer"; exit 1; }
  (( rc == 1 )) && { log ">>> Implementer session failed for $T. Stopping."; exit 1; }

  # 2. Build + review loop
  while true; do
    tries=0
    until build_ok "$T"; do
      tries=$((tries + 1))
      (( tries > MAX_BUILD_FIX_TRIES )) && { set_task_status "$T" "BLOCKED"; log ">>> $T: build still red after $MAX_BUILD_FIX_TRIES fixes"; exit 1; }
      log ">>> $T: build failed, implementer fix $tries"
      SESSIONS=$((SESSIONS + 1)); (( SESSIONS > MAX_SESSIONS )) && { log ">>> MAX_SESSIONS reached"; exit 1; }
      run_implementer "$T" "buildfix${ROUND}-${tries}" fix-build ".review/${T}-tests.txt"; rc=$?
      (( rc == 2 )) && { log ">>> $T BLOCKED by implementer"; exit 1; }
      (( rc == 1 )) && { log ">>> Implementer session failed. Stopping."; exit 1; }
    done

    if ! task_needs_review "$T"; then commit_task "$T"; break; fi

    ROUND=$((ROUND + 1))
    SESSIONS=$((SESSIONS + 1)); (( SESSIONS > MAX_SESSIONS )) && { log ">>> MAX_SESSIONS reached"; exit 1; }
    scripts/review.sh "$T" "$ROUND" | tee -a "$LOG"; rc=${PIPESTATUS[0]}
    case $rc in
      0) commit_task "$T"; scripts/usage-report.sh "$T" | tail -n 3 | tee -a "$LOG"; break ;;
      1) SESSIONS=$((SESSIONS + 1)); (( SESSIONS > MAX_SESSIONS )) && { log ">>> MAX_SESSIONS reached"; exit 1; }
         run_implementer "$T" "fix${ROUND}" fix-review ".review/${T}-round${ROUND}.md"; rc=$?
         (( rc == 2 )) && { log ">>> $T BLOCKED by implementer"; exit 1; }
         (( rc == 1 )) && { log ">>> Implementer session failed. Stopping."; exit 1; } ;;
      2) set_task_status "$T" "BLOCKED"; log ">>> $T: review limit reached"; exit 1 ;;
      *) log ">>> $T: reviewer failed. Stopping (rerun later continues here)."; exit 1 ;;
    esac
  done
done
