# IMPLEMENTER.md - how to implement a task

Used by whichever agent is the IMPLEMENTER (see `scripts/agents.conf`).
The script `scripts/run-until.sh` runs the build, the review and the commit.
You only implement and fix. You never review and never commit.

## Mode "implement"

1. Read `docs/PROGRESS.md`, then the task in `docs/TASKS.md`. Run `git status`.
2. Read only the doc sections listed in the task and in the AGENTS.md index.
3. Plan briefly: files, rules, tests, open questions.
   Product decision not covered by the Product decisions table in TASKS.md: write a question in
   PROGRESS.md "Questions for owner" and continue with the parts that do not depend on it.
4. Implement only what the task needs.
5. Write tests for every rule and edge case in the task's "Accept" list.
6. Run the smallest relevant tests, then `./gradlew build`. It must be green.
7. Self-check your `git diff`: scope creep, hardcoded balance numbers, duplicated rules,
   nondeterminism, module-boundary violations, missing tests.
8. Add a short entry to `docs/PROGRESS.md` (template inside the file).
9. Write ONE line commit message to `.review/<TASK_ID>-commit.txt`,
   e.g. `T15 add baseline bot`. Short, single line.
10. End with: `RESULT: <TASK_ID> READY`

## Mode "fix-review"

1. Read the review file you were given (all P0/P1/P2 findings).
2. Fix every P0/P1/P2. P3 is optional.
3. Never fix a finding by deleting/weakening tests or changing the spec.
4. If you believe a finding is WRONG because the spec says something else:
   - if the same finding was already NOT FIXED in the previous round too, write both
     views in PROGRESS.md "Questions for owner" and end with
     `RESULT: <TASK_ID> BLOCKED - reviewer disagreement on <finding id>`
   - otherwise fix it the way the reviewer asks, or explain in PROGRESS.md.
5. `./gradlew build` must be green. Update the PROGRESS entry and the commit message file.
6. End with: `RESULT: <TASK_ID> READY`

## Mode "fix-build"

Read the build output file, fix the cause (not the test), make `./gradlew build` green.
End with `RESULT: <TASK_ID> READY`.

## Always

- Nobody is watching: do not ask questions in the chat. Questions go to PROGRESS.md.
- Environment problems: fix them yourself (AGENTS.md section 3a).
- `BLOCKED` only if the task really cannot continue. Anything covered by the product
  decisions in TASKS.md is already decided and is NOT a blocker.
- Keep messages short and in simple English.
