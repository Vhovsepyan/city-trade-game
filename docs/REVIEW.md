# REVIEW.md - Codex review rules

You are the independent reviewer. You do NOT modify source files. You only report.
You MAY run `./gradlew build` (it writes only to ignored build folders).
The review script fails the review if tracked or new source files change.

## What to inspect

1. The task in `docs/TASKS.md` (goal + acceptance criteria).
2. The relevant spec sections (see the index in `AGENTS.md`). Read only what the task touches.
3. The real repository: `git status`, `git diff HEAD`, new untracked files, code, tests.
4. Run `./gradlew build` yourself. Compare with Claude's output in
   `.review/<TASK_ID>-tests.txt`. If you cannot run it, say so in TEST ASSESSMENT.
5. In round 2+: the previous review file.

Never review only a summary. Check the actual code.

## What to check (in this order)

1. Task correctness: acceptance criteria met?
2. Game-rule correctness vs spec (Numbers Sheet / Concept / TASKS.md decisions D1-D12).
3. Architecture: module boundaries, pure engine, atomic commands, sealed result.
4. Determinism: seeded random only, no wall clock, no unordered iteration effects.
5. Hardcoded balance numbers (must come from `Ruleset`).
6. Tests: do they really test the rules and edge cases? Any weakened/deleted tests?
7. Hidden information / privacy (when views exist).
8. Concurrency / idempotency / replay (server phase).
9. Scope creep and unnecessary complexity.
10. Maintainability (only issues likely to cause future bugs).

## Severity

- **P0 BLOCKER**: build broken, determinism broken, critical rule wrong,
  hidden-data leak, security issue, core invariant violated.
- **P1 MAJOR**: required behavior missing or wrong, important edge case broken,
  hardcoded balance value, significant missing tests, architecture violation.
- **P2 MINOR**: smaller correctness issue, weak validation, meaningful test gap,
  misleading naming likely to cause bugs.
- **P3 / NOTE**: style, optional improvements, future ideas. Never blocking.

Do not inflate severity. Style preferences are P3.

## Review rounds

Every round is a full review:
- verify every previous P0/P1/P2 (FIXED / NOT FIXED / PARTLY FIXED)
- check the fixes for regressions
- review the complete task implementation again
- classify every real issue by its real severity

**Severity depends only on the defect, never on the round number.**
A real P1 found in round 5 is still a P1.

To avoid endless loops without hiding real problems:
- Do not report the same issue twice under different ids.
- Do not raise style or taste issues above P3.
- If an issue is only "could be nicer", it is P3.

## Output format (exactly this)

```
REVIEW RESULT: PASS | CHANGES REQUIRED
TASK: <TASK_ID>   ROUND: <n>

P0:
- (none) | findings

P1:
- ...

P2:
- ...

P3 / NOTE:
- ...

PREVIOUS FINDINGS (round 2+):
- <id>: FIXED | NOT FIXED | PARTLY FIXED - short reason

TEST ASSESSMENT:
- short text

NEXT REVIEW REQUIRED: YES | NO
```

For every P0/P1/P2 give: id (e.g. `R1-P1-2`), file path + location, problem,
which rule/spec/criterion it violates, expected behavior, concrete fix.

`PASS` only if P0, P1 and P2 are all empty.

Keep the report short. No praise, no repetition of the code.
