#!/usr/bin/env bash
# Runs a non-interactive Codex review for one task.
# Usage: scripts/codex-review.sh <TASK_ID> <ROUND>
# Exit codes:
#   0 = PASS
#   1 = CHANGES REQUIRED
#   2 = usage error or safety limit reached
#   3 = Codex failed (CLI error, no valid report, or Codex changed source files)
set -uo pipefail

TASK_ID="${1:?Usage: scripts/codex-review.sh <TASK_ID> <ROUND>}"
ROUND="${2:?Usage: scripts/codex-review.sh <TASK_ID> <ROUND>}"
# Runaway protection only. Normal work stops at PASS long before this.
SAFETY_LIMIT="${REVIEW_SAFETY_LIMIT:-10}"

if ! [[ "$ROUND" =~ ^[0-9]+$ ]] || (( ROUND < 1 )); then
  echo "ROUND must be a positive number"; exit 2
fi
if (( ROUND > SAFETY_LIMIT )); then
  echo "Safety limit of $SAFETY_LIMIT review rounds reached. Mark the task BLOCKED and report to the owner."
  exit 2
fi
command -v codex >/dev/null 2>&1 || { echo "codex CLI not found in PATH"; exit 3; }

ROOT="$(git rev-parse --show-toplevel)" || { echo "not a git repository"; exit 2; }
cd "$ROOT"
source scripts/lib-usage.sh

OUT_DIR=".review"
mkdir -p "$OUT_DIR"
OUT="$OUT_DIR/${TASK_ID}-round${ROUND}.md"
EVENTS="$OUT_DIR/${TASK_ID}-round${ROUND}.events.jsonl"
TESTS="$OUT_DIR/${TASK_ID}-tests.txt"

PREV="none"
if (( ROUND > 1 )); then
  PREV="$OUT_DIR/${TASK_ID}-round$((ROUND - 1)).md"
  [[ -f "$PREV" ]] || { echo "Missing previous review: $PREV"; exit 2; }
fi

if command -v sha256sum >/dev/null 2>&1; then HASH="sha256sum"; else HASH="shasum -a 256"; fi

# Fingerprint of the working tree (tracked changes + untracked, non-ignored files).
fingerprint() {
  { git diff HEAD --binary; git ls-files --others --exclude-standard | sort | while read -r f; do
      echo "$f"; cat "$f" 2>/dev/null; done; } | $HASH | cut -d' ' -f1
}
BEFORE="$(fingerprint)"

PROMPT=$(cat <<PROMPT_END
You are the independent reviewer (Codex) for this repository.
Follow docs/REVIEW.md exactly. Use AGENTS.md for project rules and the doc index.

Task ID: ${TASK_ID} (find it in docs/TASKS.md)
Review round: ${ROUND}
Previous review file: ${PREV}
Claude's test output: ${TESTS} (may be missing)

Inspect the repository yourself: git status, git diff HEAD, new untracked files,
the changed code and tests. Run ./gradlew build.
Do NOT modify, create or delete any source, doc or config file.
Return only the report in the output format from docs/REVIEW.md.
PROMPT_END
)

# workspace-write: Codex can run Gradle (build/ and .gradle/ are git-ignored).
# The fingerprint check below fails the review if Codex changes project files.
# --json: events (incl. token usage) go to $EVENTS
# -o:     the final review text goes to $OUT
# If your Codex version uses other flag names, check: codex exec --help
START=$(date +%s)
codex exec --json --sandbox workspace-write -o "$OUT" "$PROMPT" > "$EVENTS"
CODEX_EXIT=$?
DURATION=$(( $(date +%s) - START ))
[[ -f "$OUT" ]] && cat "$OUT"

if (( CODEX_EXIT != 0 )) || [[ ! -s "$OUT" ]]; then
  usage_log_codex "$TASK_ID" "$ROUND" "$EVENTS" "$DURATION" "CODEX_FAILED"
  echo ">>> Codex CLI failed (exit $CODEX_EXIT) or wrote no review"; exit 3
fi
if [[ "$(fingerprint)" != "$BEFORE" ]]; then
  usage_log_codex "$TASK_ID" "$ROUND" "$EVENTS" "$DURATION" "INVALID_CHANGED_FILES"
  echo ">>> Codex modified project files during review. Review is invalid."; exit 3
fi
if grep -Eq '^REVIEW RESULT: PASS[[:space:]]*$' "$OUT"; then
  usage_log_codex "$TASK_ID" "$ROUND" "$EVENTS" "$DURATION" "PASS"
  echo ">>> ${TASK_ID} round ${ROUND}: PASS"; exit 0
elif grep -Eq '^REVIEW RESULT: CHANGES REQUIRED[[:space:]]*$' "$OUT"; then
  usage_log_codex "$TASK_ID" "$ROUND" "$EVENTS" "$DURATION" "CHANGES_REQUIRED"
  echo ">>> ${TASK_ID} round ${ROUND}: CHANGES REQUIRED (see $OUT)"; exit 1
else
  usage_log_codex "$TASK_ID" "$ROUND" "$EVENTS" "$DURATION" "NO_RESULT_LINE"
  echo ">>> No valid 'REVIEW RESULT' line in the Codex output."; exit 3
fi
