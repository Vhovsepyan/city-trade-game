# PROGRESS.md

Short log for the next session. Newest entry on top. Max ~10 lines per entry.
Keep only the last 10 entries; summarize older ones in one line under "Earlier".

## Current state
- Milestone: M1 complete (engine); M2 complete (bots, simulation, first balance report)
- Next task: T19 (M3 tasks not detailed yet). Owner: read `docs/balance-report-prototype-001.md`
  section 4 and decide which values go into `prototype-002`.
- Target ruleset: prototype-001 (`rulesets/prototype-001.json`)
- `.claude/settings.json` has an uncommitted owner change from BEFORE T02 (see git status at
  session start). It is not part of T02; agents do not touch or commit it.

## Environment (checked by T00, 2026-09-23)
- OK Java for Gradle: default `java` = OpenJDK 17.0.12 (OpenLogic),
  JAVA_HOME=`C:\Program Files\OpenLogic\jdk-17.0.12.7-hotspot\`.
- JDK 25 found: `C:\Users\vaheh\.jdks\corretto-25.0.4` (also `corretto-21.0.10`).
  Not the default; the T01 toolchain can use it.
- OK `git --version`: 2.45.1.windows.1
- OK `node --version`: v22.21.1
- OK `codex --version`: codex-cli 0.154.0
- Gradle: not installed globally (T01 creates the wrapper).
- OK `.gitignore` contains `.review/`, `build/`, `.gradle/`, `.idea/`.
- OK spec docs: all 3 files exist in `docs/` with the names from AGENTS.md.
- OK scripts executable. Fixed: git index mode set to 100755 (`git update-index --chmod=+x scripts/*.sh`).
- OK Codex smoke test (same flags as codex-review.sh): `.review/codex-smoke.txt` = `CODEX OK`,
  jsonl contains `input_tokens`.
- Changes outside the repository: none.
- T01: Gradle 9.7.1 zip downloaded into `.review/tools/` (git-ignored; user folder is blocked
  by the agent sandbox), used once to create the wrapper. Safe to delete.
- T01: JDK 25 is auto-detected by Gradle from `~/.jdks` (IntelliJ). No `~/.gradle/gradle.properties`
  change was needed. Foojay resolver downloads JDK 25 if it is missing.

## Questions for owner
- (none)

## Suggestions (not built)
- Sim metrics (T18 report section 5): opportunity results, per-objective and per-building rates,
  upkeep/crisis payments per resource; a bot that uses projects and contracts.

## Log

<!-- Template:
### T0X - <title> - DONE (review round N)
- What: ...
- Files: ...
- Tests: ...
- Notes / P3 items: ...
-->

### T18 - First balance report - DONE (review round 1)
- What: 3 x 1000 games (baseline x4, baseline/trader mix, trader x4), seed 1. `docs/balance-report-prototype-001.md`.
- Findings: "do everything" wins (57.5% of trader winners at max 19, 34.6% shared victories); Level 2 in Round 1,
  Level 3 by Round 4.4; trading stops after Round 11; 50-65 unspent Money; Agricultural strongest (39% baseline),
  Technology weakest (19.7% trader). Bots do not use projects/contracts, so those are not judged.
- Suggested (owner decides): Level 2 cost 4; Grand Landmark 10 Money; Research Lab F1 E2 M1 T1; later Money -1/level.
- Ruleset not changed. No code changed.

### T17 - Simulation runner + metrics - DONE (review round 1)
- What: `game-sim` CLI `SimulationMain` (`./gradlew :game-sim:run --args="--ruleset prototype-001 --games 1000
  --seed 1 --bots baseline,trader,baseline,trader"`; also `--rulesets-dir`, `--out` (default `build/sim`)).
  Game i uses seed + i. Writes `<ruleset>_<bots>_seed<S>_games<N>.json` (summary) and `.csv` (one row per city per game).
- Metrics: Arch 8.2 (Prestige by city, level 2/3 reach rate + round, produced/traded/discarded, market use,
  resource demand early/mid/late, contracts, project completion, Strained rounds, holdings + market steps by round)
  + win rate by city and bot (shared victory = 1/winners), Prestige distribution (all, per city, winner).
- Engine: new event `ResourcesProduced(seat, produced)` in step 1.3 (needed for "produced"); `BotGame.Observer`.
- Choice (metrics only): early/mid/late = rounds 1-5 / 6-10 / 11-14 (`GamePart`). Demand = resources paid for
  levels, buildings, upkeep, crises, projects, event options.
- Tests: sim (Options, Distribution, GamePart, Collector vs real game events, Report by hand, Main end-to-end +
  same args = same files); ProductionTest, BotGameTest observer; 3 event-list tests updated for the new event.

### T16 - Trader bot - DONE (review round 2)
- What: `TraderBot` (Arch 8.1 B): answers every offer to it (accept if value received >= threshold x value given at
  market buy costs, and it gives only resources above its needs = next level cost + crisis reserve); offers spare
  resources (specialty first) at equal value for a missing resource, specialty city first, max N per round, one per
  seat per round, cancels its own offer still open at its next turn; market fallback like baseline; warned crisis:
  keeps cost (+1 for upkeep) and buys it if missing, policy stays PAY; bounded bids (share of reward value over rounds
  left, share of unneeded Money). Bot settings in `TraderBot.Settings` (bot profile, not game rules).
- Shared bot helpers moved to `BotActions`. Review R1-P1-1: `BotGame` now asks a passed seat again in the next pass.
- Tests: TraderBotTest (18), BotGameTest (+5: 100 trader games / 100 mixed games without rejections, traders pay more
  crises than baseline, determinism, passed seat answers a later offer).

### T15 - Baseline bot - DONE (review round 1)
- What: `game-bots`: `Bot` interface (pure function of state), `BaselineBot` (Arch 8.1 A: next level first, buys
  missing units from the market only if the whole upgrade fits this round; buildings in ruleset order only when no
  upgrade is possible this round, without market; sells units above the storage limit; keeps first dealt objectives;
  no trades/contracts/bids/projects/event options), `BotGame` (plays a full game, seats in order, records rejections).
- Tests: BaselineBotTest (13), BotGameTest (4: 100 games 0 rejections + every city upgrades, same seed = same game).
- Notes: game-bots tests use `game-ruleset-json` (testImplementation) for the real prototype-001 file.

### T14a - Apply D18 - DONE (review round 1)
- What: `Contracts.breakVoluntarily` rejects with new `INSUFFICIENT_FREE_MONEY` when the debtor has reserved
  Money (active bids) and free Money < full compensation. No bids = Numbers Sheet 13 as before; step 1.5 unchanged.
- Tests: OpportunitiesTest: old `reservedMoneyIsNotPaidAsBreakCompensation` (pre-D18 behavior) replaced by 5 D18
  tests (rejected with bids, allowed after lowering / withdrawing, no bids = partial pay + Prestige, 1.5 unchanged).

### T14 - Full-game and determinism tests - DONE (review round 1)
- What: tests only, in `game-ruleset-json` (they need the real prototype-001 file). `ScriptedGame`: fixed 14-round
  script for seed 2026 that uses every command; every command must be accepted; logs commands, events, states.
- Tests: `FullGameTest` (8: all offer/contract/project/opportunity end states, storage + non-negative holdings after
  every command, final = visible + hidden, winners; snapshot 15/15/11/7, tie decided by level), `ReplayTest` (3),
  `RoundOrderTest` (3: events of every StartRound/ResolveRound in Numbers Sheet step order), `RulesetSensitivityTest`
  (6: one changed JSON value -> exactly the predicted change; objective, level, building, break penalty, market, start Money).
- Notes: no engine code changed.

### T13 - Hidden objectives, final scoring, tiebreakers - DONE (review round 1)
- What: package `objective`: `Objectives` (completion check per card; step 4.8 of EVERY round records level and
  smallest F/E/M/T amount), `FinalScoring` (step 4.8 of the last round: hidden = completed kept cards x
  `completedPrestige`, final = visible + hidden, ranking Prestige > level > completed objectives > fewer broken
  contracts, still equal = shared victory; Money never compared).
- State: `PlayerState.objectiveProgress` (`ObjectiveProgress`: market buy rounds from `Market.buy`, everStrained from
  `withStrained`, level after each round, best minimum stock); `GameState.finalResult` (`FinalResult`/`FinalScore`).
  Visible `prestige` is not changed by the hidden score. Events: `ObjectivesRevealed`, `FinalScoresRevealed`.
- Choices: see Questions (Project Partner, Contract Player, Patient Investor readings).
- Tests: ObjectivesTest (pass + fail for all 12, tracking through real commands), FinalScoringTest (8).
- Note: Codex review left a temp folder `%LOCALAPPDATA%\Temp\codex-t13-jdk25` (its delete was blocked). Safe to delete.

### T12 - Opportunities and secret bids - DONE (review round 2)
- What: package `opportunity`: `Opportunities` (step 2.3 `reveal`, command `PlaceBid`, step 4.2 `resolveBids`).
  State: `GameState.opportunities` (`RegionalOpportunity`: card, appearedRound, `OpportunityStatus` OPEN/WON/REMOVED,
  winnerSeat, `SecretBid`s sorted by seat, so arrival order never changes the state).
- Rules: `PlaceBid` replaces the old bid, 0 = pass/withdraw; total bids <= Money. `GameState.spendableHoldings`
  (holdings minus reserved Money) is now used by market buy, trades (propose + accept), contracts (propose, sign,
  break compensation), projects, levels, buildings, event options. Single highest bid wins, pays, gets the reward
  as `extraProduction` (next round); tie or no bid = card stays OPEN; unwon cards become REMOVED in Round 14.
- Codes: `UNKNOWN_OPPORTUNITY`, `OPPORTUNITY_NOT_OPEN`. Events: `OpportunityRevealed`, `BidPlaced` (no amount),
  `OpportunityWon` (with price), `OpportunityNotWon(tied)`, `OpportunityRemoved`.
- Review: R1-P2-1 (unwon cards not removed after Round 14) fixed. Owner question added (break compensation).
- For later: T13 Opportunity Winner objective can use `opportunities()` `winnerSeat`.
- Tests: OpportunitiesTest (28, incl. all 720 orders of 6 bids), EventOptionsTest (Festival with reserved Money).

### T11 - Public projects - DONE (review round 1)
- What: package `project`: `Projects` (step 2.3 `open`, command `ContributeToProject`, step 4.3 `resolveDeadline`,
  Prestige in 4.4 after buildings, before crises). State: `GameState.projects` is now `List<PublicProject>`
  (card, window, `ProjectStatus` UPCOMING/OPEN/SUCCEEDED/FAILED, public `ProjectContribution` log with round).
- Rules: points from ruleset (D9); only card types, never more than still needed; contributions add up over
  rounds; complete project stays OPEN until its deadline; failure keeps nothing. Reward goes into
  `extraProduction` of qualifying contributors at 4.3, so it is first produced next round.
- Choice: Prestige only for qualifying contributors; "largest" is ranked among them (with prototype values the
  largest always qualifies). Codes: `UNKNOWN_PROJECT`, `PROJECT_NOT_OPEN`, `EMPTY_CONTRIBUTION`,
  `RESOURCE_NOT_NEEDED`, `CONTRIBUTION_EXCEEDS_NEED`.
- For later: T12 must check FREE Money in `Projects.contribute`; T13 objectives can use `contributions()` rounds.
- Tests: ProjectsTest (16); GameSetupTest checks windows/status of drawn projects.

### T10 - Events and crises - DONE (review round 1)
- What: package `event`: `EventSchedule` (1.1 activate warned card, 2.2 warn next event round, 4.7 end),
  `Crises` (2.1 pay-in-full or Strained, command `SetCrisisPolicy`, Crisis Prestige in 4.4), `EventOptions`
  (command `UseEventOption` for Festival/Breakthrough, once per player; Festival Prestige in 4.4),
  `EventEffects` (market step/price shift, building/upgrade discounts, Recession, Good Harvest). Shared `Payments`.
- State: `GameState.activeEvent`; `PlayerState.eventParticipation` (policy, paid crisis rounds, used options)
  and `extraProduction` (Breakthrough, permanent from next round). Policy is back to PAY after the event round's 2.1.
- Choices: `SetCrisisPolicy` only when the warned event is a crisis (`NO_CRISIS_WARNED`); Infrastructure Failure
  uses the D1 order (`Upkeep.payment`). Watch List 3 (upkeep fail + SKIP = Strained once) recorded in a test.
- Test ruleset: placeholder events are now `ProductionBoost(NOTHING)` (no effect) instead of `NoMoneyIncome`,
  so older tests are not changed by Recession; `TestRulesets.prototypeEvents()` has the real 12 cards.
- Tests: CrisesTest (20), EconomyEventsTest (11), EventOptionsTest (11), EventScheduleTest (4).
- For later: T13 Crisis Responder can count `eventParticipation().crisisPaidRounds()`.

## Earlier
- T09 DONE: package `contract`: `Contracts` (propose/sign/break/mutual cancel, 1.5 settle, 4.1 expiry),
  `PlayerState.contractsBroken`; D13-D16 applied.
- T08 DONE: package `trade`: `Trading` (propose/accept/reject/cancel/counter, 4.1 expiry), `TradeOffer` history;
  accept with missing resources = Accepted + offer INVALID (`TradeInvalidated`).
- T07 DONE: package `city`: `CityDevelopment` (`UpgradeCity`, `BuildBuilding`, 4.4 Prestige), `BuildingEffects`
  (effects from the round after building).
- T06 DONE: package `market`: `Market` (buy/sell at current step, 4.6 `movePrices`), per-round `MarketActivity`.
- T05 DONE: package `economy`: `Production`, `Upkeep` (D1), `Storage`, command `SetUpkeepPriority`, Strained flag;
  with prototype values an L2+ city never fails upkeep in practice.
- T04 DONE: `GameEngine.apply`, `GamePhase`, `StartRound`/`ResolveRound`; round order in ONE place: enum
  `round.RoundStep` (declaration order = Numbers Sheet order), run by `round.RoundFlow`, bodies in `round.RoundSteps`.
- T03 DONE: core state, `GameRandom` (SplitMix64), `GameSetup.create` (Numbers Sheet 21 draw order),
  `ChooseObjectives` (`setup.ObjectiveChoice`).
- T00 DONE: environment checked (Java, git, node, codex, scripts executable, Codex smoke test OK).
- T01 DONE: Gradle 9.7.1 wrapper, Groovy DSL, Java 25 toolchain + foojay, 4 modules; `verifyEngineHasNoDependencies`
  in `check`. Codex sandbox cannot run Gradle (no network, no cache access).
- T02 DONE: `Ruleset` records in the engine, `rulesets/prototype-001.json`, strict Jackson loader (mixins) with
  validation that reports all errors; one test per Numbers Sheet section.
