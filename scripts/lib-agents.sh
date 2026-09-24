#!/usr/bin/env bash
# Runs Claude Code or Codex in a given role. Sourced by run-until.sh and review.sh.
# Needs: scripts/agents.conf, scripts/lib-usage.sh, node

source scripts/agents.conf
source scripts/lib-usage.sh

LIMIT_PATTERN='usage limit|rate limit|limit reached|limit exceeded|resets at|try again (later|in)|quota|429'

# agent_run <agent> <role: implement|review> <task> <label> <prompt>
# Writes the agent's final message to .review/<task>-<label>.out
# Returns: 0 ok, 10 usage/rate limit, 1 other failure
agent_run() {
  local agent="$1" role="$2" task="$3" label="$4" prompt="$5"
  local out=".review/${task}-${label}.out" raw=".review/${task}-${label}.raw" err=".review/${task}-${label}.err"
  local start rc dur
  mkdir -p .review; rm -f "$out"
  start=$(date +%s)

  case "$agent" in
    claude)
      local args=(-p "$prompt" --output-format json)
      [[ -n "${CLAUDE_MODEL:-}" ]] && args+=(--model "$CLAUDE_MODEL")
      if [[ "$role" == "review" ]]; then
        args+=(--allowedTools "Read,Grep,Glob,Bash(./gradlew:*),Bash(git status:*),Bash(git diff:*),Bash(git log:*),Bash(git ls-files:*),Bash(cat:*),Bash(ls:*)"
               --disallowedTools "Edit,Write,NotebookEdit")
      else
        args+=(--permission-mode acceptEdits)
      fi
      claude "${args[@]}" > "$raw" 2> "$err"; rc=$?
      usage_result_text "$raw" > "$out"
      usage_log_claude "$task" "$label" "$raw" "exit$rc"
      ;;
    codex)
      local args=(exec --json --sandbox danger-full-access -c approval_policy=never -o "$out")
      [[ -n "${CODEX_MODEL:-}" ]] && args+=(-m "$CODEX_MODEL")
      codex "${args[@]}" "$prompt" > "$raw" 2> "$err"; rc=$?
      dur=$(( $(date +%s) - start ))
      usage_log_codex "$task" "$label" "$raw" "$dur" "exit$rc"
      ;;
    *) echo "Unknown agent: $agent (check scripts/agents.conf)"; return 1 ;;
  esac

  [[ -f "$out" ]] && cat "$out"
  if (( rc != 0 )) || [[ ! -s "$out" ]]; then
    if grep -Eiq "$LIMIT_PATTERN" "$raw" "$err" "$out" 2>/dev/null; then
      echo ">>> $agent hit a usage/rate limit"; return 10
    fi
    echo ">>> $agent failed (exit $rc). See $err"; return 1
  fi
  return 0
}

# agent_run_with_wait: same as agent_run, but waits and retries on usage limits.
agent_run_with_wait() {
  local waits=0 rc
  while true; do
    agent_run "$@"; rc=$?
    (( rc != 10 )) && return $rc
    waits=$(( waits + 1 ))
    if (( waits > LIMIT_MAX_WAITS )); then
      echo ">>> Limit still active after $LIMIT_MAX_WAITS waits. Stopping."; return 1
    fi
    echo ">>> Waiting ${LIMIT_WAIT_MINUTES} min for the limit to reset (wait $waits/$LIMIT_MAX_WAITS, $(date '+%H:%M'))"
    sleep $(( LIMIT_WAIT_MINUTES * 60 ))
  done
}

# Fingerprint of project files (tracked changes + untracked non-ignored files).
if command -v sha256sum >/dev/null 2>&1; then _HASH="sha256sum"; else _HASH="shasum -a 256"; fi
tree_fingerprint() {
  { git diff HEAD --binary; git ls-files --others --exclude-standard | sort | while read -r f; do
      echo "$f"; cat "$f" 2>/dev/null; done; } | $_HASH | cut -d' ' -f1
}

# task_status <TASK_ID>  -> prints TODO / IN PROGRESS / DONE / BLOCKED
task_status() {
  awk -v id="$1" '$1 == "###" && $2 == id { if (match($0, /`[A-Z ]+`/)) print substr($0, RSTART + 1, RLENGTH - 2); exit }' docs/TASKS.md
}

# set_task_status <TASK_ID> <STATUS>
set_task_status() {
  local id="$1" st="$2" tmp
  tmp=$(mktemp)
  awk -v id="$id" -v st="$st" '$1 == "###" && $2 == id { sub(/`[A-Z ]+`/, "`" st "`") } { print }' docs/TASKS.md > "$tmp" && mv "$tmp" docs/TASKS.md
}

# task_needs_review <TASK_ID> -> exit 0 if review needed (default), 1 if the task says "Review: ... NOT needed"
task_needs_review() {
  ! awk -v id="$1" '
      $1 == "###" { inb = ($2 == id) ; next }
      inb && /^Review:.*NOT needed/ { found = 1 }
      END { exit !found }' docs/TASKS.md
}
