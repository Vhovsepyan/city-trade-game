@AGENTS.md

# Claude workflow

You are the implementation agent. For "do the next task" (or a named task):

1. **Start**: read `docs/PROGRESS.md`, then the task in `docs/TASKS.md`
   (first task with status `TODO` or `IN PROGRESS` whose dependencies are `DONE`).
   Set its status to `IN PROGRESS`. Run `git status`. Run `mkdir -p .review`.
2. **Read**: only the doc sections listed in the task and in the AGENTS.md index.
3. **Plan** briefly: files to change, rules affected, tests needed, open questions.
   Product decision not covered by D1-D12 in TASKS.md: write a question in
   PROGRESS.md and continue with the parts that do not depend on it.
4. **Implement** only what the task needs.
5. **Test**: run the smallest relevant tests, then save the full build:
   `mkdir -p .review && ./gradlew build > .review/<TASK_ID>-tests.txt 2>&1`
6. **Self-review** your `git diff`: scope creep, hardcoded balance numbers,
   duplicated rules, nondeterminism, module-boundary violations, missing tests.
7. **Codex review**: `scripts/codex-review.sh <TASK_ID> <ROUND>` (start with 1).
   It can take several minutes; use a long timeout.
   Exit 0 = PASS. Exit 1 = changes required (read `.review/<TASK_ID>-round<N>.md`).
   Exit 2 = usage/limit error. Exit 3 = Codex itself failed: retry once, then
   mark the task `BLOCKED` with the error in PROGRESS.md.
8. **Fix loop - repeat until PASS** (P0 = P1 = P2 = 0):
   fix every P0/P1/P2 -> rerun tests (step 5) -> review with ROUND + 1.
   Stop the loop ONLY when:
   - PASS, or
   - the same finding is `NOT FIXED` in 2 rounds in a row because you believe the
     spec says something different (a real disagreement): write both views in
     PROGRESS.md "Questions for owner" and mark the task `BLOCKED`, or
   - the safety limit in the script is reached (runaway protection): mark `BLOCKED`.
   Never fix a finding by deleting/weakening tests or changing the spec.
9. **Finish** (only after PASS; tasks marked "Review: NOT needed" skip steps 7-8):
   - set the task status to `DONE` in TASKS.md
   - add a short entry to PROGRESS.md (template inside the file)
   - `git add` the task files and commit with one short line: `<TASK_ID> <what was done>`

## Autonomous mode

When started by `scripts/run-until.sh` or asked to "work until <milestone>":
- Do exactly ONE task per session, fully (steps 1-9), then end the session.
  The script starts a fresh session for the next task (smaller context, fewer tokens).
- Do not ask the owner questions in the chat. Nobody is watching.
  Questions go to PROGRESS.md.
- `BLOCKED` is only for things you really cannot solve. Anything covered by
  D1-D12 in TASKS.md is already decided and is NOT a blocker.
- Final message of the session: one line, `RESULT: <TASK_ID> DONE` or
  `RESULT: <TASK_ID> BLOCKED - <reason>`.

Keep reports short and in simple English.
