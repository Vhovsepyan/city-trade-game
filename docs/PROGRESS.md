# PROGRESS.md

Short log for the next session. Newest entry on top. Max ~10 lines per entry.
Keep only the last 10 entries; summarize older ones in one line under "Earlier".

## Current state
- Milestone: M1 complete (engine); next is M2
- Next task: T15
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
- (none)

## Log

<!-- Template:
### T0X - <title> - DONE (review round N)
- What: ...
- Files: ...
- Tests: ...
- Notes / P3 items: ...
-->

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

### T09 - Formal contracts - DONE (review round 3)
- What: package `contract`: `Contracts` (commands `ProposeContract`, `SignContract`, `BreakContract`,
  `CancelContractMutually`; step 1.5 `settleDueObligations`; step 4.1 `expireProposals`), `FormalContract`,
  `ContractStatus`. `PlayerState.contractsBroken` (public counter).
- Owner decisions D13-D16 applied: negative Prestige allowed; voluntary break delivers nothing; due contracts
  settled oldest first, each in full if possible; unsigned proposals expire at 4.1.
- Review: R1-P1-2 (compensation overflow) fixed with checked arithmetic; R1-P1-1 / R1-P2-1 resolved by D15 / D16.
- Tests: ContractsTest (incl. Numbers Sheet 13 example, D15 order), ContractStatusTest, ResourceBundleTest (overflow).

### T08 - Instant trades and offers - DONE (review round 1)
- What: package `trade`: `Trading` (commands `ProposeTrade`, `AcceptTrade`, `RejectTrade`, `CancelTrade`,
  `CounterTrade`, WINDOW only; step 4.1 `expireOpenOffers`). State: `TradeOffer`, `TradeOfferStatus`
  (only OPEN -> closed), `OfferCloseReason.COUNTEROFFER`; `GameState.tradeOffers` (kept as history) + `nextOfferId`.
- Choices: proposer must own the offered bundle when proposing (not reserved; checked again on accept).
  Accept with missing resources = Accepted result with offer INVALID + `TradeInvalidated` event (a Rejected
  result may not change state). Role is checked before status (D7: others learn nothing).
- Codes: `TRADE_WITH_SELF`, `EMPTY_TRADE`, `UNKNOWN_OFFER`, `OFFER_NOT_OPEN`, `NOT_OFFER_RECIPIENT`, `NOT_OFFER_PROPOSER`.
- For later: T12 must check FREE Money (minus bid reservations) in `Trading.accept`/`checkNewOffer`.
- Tests: TradingTest (34), TradeOfferStatusTest (12), ResourceBundleTest (isEmpty/hasNegativeAmount/covers).

### T07 - City levels and buildings - DONE (review round 1)
- What: package `city`: `CityDevelopment` (commands `UpgradeCity`, `BuildBuilding`, WINDOW only; step 4.4
  `awardPrestige`), `BuildingEffects` (production / storage / upkeep bonuses of ACTIVE buildings only).
- `PlayerState` new fields: `lastUpgradeRound` (one level per round), `buildings` (`BuiltBuilding`: id,
  roundBuilt, D4 chosenResource), `prestige` (visible). Effects start when `roundBuilt < round`.
- Level counts at once (unlocks buildings in the same window); production/upkeep change next round.
  Transit reduction only when city level == effect `cityLevel` (3). Prestige for level + buildings of this round in 4.4.
- Codes: `MAX_LEVEL_REACHED`, `ALREADY_UPGRADED_THIS_ROUND`, `UNKNOWN_BUILDING`, `BUILDING_ALREADY_BUILT`,
  `LEVEL_TOO_LOW`, `INVALID_BUILDING_CHOICE`. Events: `CityUpgraded`, `BuildingBuilt`, `PrestigeGained`.
- `Production.productionOf` now takes the round. Strained penalty on total production (see Questions).
- For later: T10 building cost discount event goes into `CityDevelopment.build`; T11+ add their Prestige in 4.4.
- Tests: CityUpgradeTest (9), BuildingTest (27 incl. parameterized); TestRulesets now has the 8 buildings.

### T06 - Global market - DONE (review round 1)
- What: package `market`: `Market` (buy/sell handlers, `buyCost`/`sellValue` at current step, step 4.6
  `movePrices`). Commands `BuyFromMarket`, `SellToMarket` (WINDOW only). Prices never change in the window.
- `PlayerState.marketThisRound` (`MarketActivity`: bought/sold units) for the per-round buy limit and the
  net count; cleared in 4.6. Codes: `INVALID_QUANTITY`, `INSUFFICIENT_MONEY`, `INSUFFICIENT_RESOURCES`,
  `MARKET_BUY_LIMIT_EXCEEDED`. Events: `MarketBought`, `MarketSold`, `MarketPriceMoved`.
- For later: T10 adds event price modifiers inside `Market.buyCost/sellValue`; T12 must check FREE Money
  (minus bid reservations) in `Market.buy`. Objective "Market Independence" (T13) needs a game-long count.
- Tests: MarketTest (22 tests: prices per step, limit, atomic rejections, phases, thresholds, min/max, per resource).

## Earlier
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
