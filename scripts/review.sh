#!/usr/bin/env bash
# One review round by the REVIEWER from scripts/agents.conf.
# Usage: scripts/review.sh <TASK_ID> <ROUND>
# Exit: 0 PASS, 1 CHANGES REQUIRED, 2 usage/limit error, 3 reviewer failed or invalid review
set -uo pipefail
TASK_ID="${1:?Usage: scripts/review.sh <TASK_ID> <ROUND>}"
ROUND="${2:?Usage: scripts/review.sh <TASK_ID> <ROUND>}"
cd "$(git rev-parse --show-toplevel)" || exit 2
source scripts/lib-agents.sh

[[ "$ROUND" =~ ^[0-9]+$ ]] && (( ROUND >= 1 )) || { echo "ROUND must be >= 1"; exit 2; }
if (( MAX_REVIEW_ROUNDS > 0 && ROUND > MAX_REVIEW_ROUNDS )); then
  echo "Review safety limit ($MAX_REVIEW_ROUNDS) reached"; exit 2
fi

OUT=".review/${TASK_ID}-round${ROUND}.md"
PREV="none"; (( ROUND > 1 )) && PREV=".review/${TASK_ID}-round$((ROUND - 1)).md"

PROMPT="You are the REVIEWER for this repository. You never edit files.
Follow docs/REVIEW.md exactly. Use AGENTS.md for project rules and the doc index.
Task ID: ${TASK_ID} (see docs/TASKS.md). Review round: ${ROUND}.
Previous review file: ${PREV}
Implementer's build output: .review/${TASK_ID}-tests.txt
Inspect the repository yourself (git status, git diff HEAD, untracked files, code, tests) and run ./gradlew build.
Return only the report in the output format from docs/REVIEW.md."

BEFORE="$(tree_fingerprint)"
agent_run_with_wait "$REVIEWER" review "$TASK_ID" "review${ROUND}" "$PROMPT"; rc=$?
(( rc != 0 )) && exit 3
cp ".review/${TASK_ID}-review${ROUND}.out" "$OUT"

if [[ "$(tree_fingerprint)" != "$BEFORE" ]]; then
  echo ">>> Reviewer changed project files. Review invalid."; exit 3
fi
if grep -Eq '^REVIEW RESULT: PASS[[:space:]]*$' "$OUT"; then
  echo ">>> ${TASK_ID} round ${ROUND}: PASS"; exit 0
elif grep -Eq '^REVIEW RESULT: CHANGES REQUIRED[[:space:]]*$' "$OUT"; then
  echo ">>> ${TASK_ID} round ${ROUND}: CHANGES REQUIRED"; exit 1
fi
echo ">>> No valid 'REVIEW RESULT' line in $OUT"; exit 3
