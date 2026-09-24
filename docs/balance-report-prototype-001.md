# Balance report - prototype-001 (T18)

First simulation report. **The ruleset was not changed.** The owner decides which
suggestions (section 4) go into `prototype-002.json`.

## 1. How the numbers were made

3 runs x 1000 games, seeds 1-1000, ruleset `rulesets/prototype-001.json`, 0 rejected commands:

```
./gradlew :game-sim:run --args="--ruleset prototype-001 --games 1000 --seed 1 --bots baseline,baseline,baseline,baseline"
./gradlew :game-sim:run --args="--ruleset prototype-001 --games 1000 --seed 1 --bots baseline,trader,baseline,trader"
./gradlew :game-sim:run --args="--ruleset prototype-001 --games 1000 --seed 1 --bots trader,trader,trader,trader"
```

Output: `build/sim/prototype-001_<bots>_seed1_games1000.json` / `.csv`. Cities are assigned by seeded
shuffle (D11), so city results are not tied to one seat.

What the bots do NOT do (so these parts cannot be judged yet): public projects (0 contributions,
0 of 2000 projects succeeded in every run), formal contracts (0 signed), Public Festival /
Research Breakthrough options. Baseline also does not trade, bid, or prepare for crises.
Opportunity results and per-objective / per-building counts are not in the metrics.

## 2. Key numbers

### Win rate and final Prestige by city (shared victory = 1/winners)

| City | Baseline x4: win / avg P | Trader x4: win / avg P | Mix (trader seats only): win / avg P |
|------|------|------|------|
| Agricultural | **0.390** / 8.03 | **0.288** / 16.92 | 0.519 / 13.62 |
| Industrial   | 0.180 / 7.01 | 0.245 / 16.75 | 0.489 / 13.26 |
| Energy       | 0.185 / 6.97 | 0.270 / 16.83 | 0.458 / 13.05 |
| Technology   | 0.244 / 7.24 | **0.197** / 16.58 | 0.500 / 12.28 |

### Game-level numbers

| | Baseline x4 | Mix 2+2 | Trader x4 |
|---|---|---|---|
| Winning Prestige: mean (min-max) | 9.0 (5-13) | 14.5 (8-19) | 18.3 (16-19) |
| Winners with 19 Prestige | 0% | - | 57.5% |
| Shared victories | 24.9% | 7.9% | 34.6% |
| Level 2 reached (avg round) | 2.0 (100%) | 1.5-1.8 by city (both bots) | 1.0 (100%) |
| Level 3 reached (avg round) | 5.6 (100%) | baseline 5.7, trader 5.9 | 4.4 (100%) |
| Strained rounds per city | 2.27 | baseline 2.25, trader 0.34 | 0.40 |
| Player trades per game | 0 | 5.5 | 44.1 |
| Market units sold / bought per city | 36 / 5.4 | - | 12.0 / 2.1 |
| Money held per city at Round 14 | 65.1 | - | 49.5 |
| Resources discarded | 0 | 0 | 0 |
| Projects succeeded / contracts signed | 0 / 0 | 0 / 0 | 0 / 0 |

Mix run: trader seats win 98.2% of games (win share 0.491 per seat), baseline seats 1.8%.

### Over time (trader x4)

- Trades per game by round: R1 5.4, R4-R10 about 4.2-4.6, R11 3.0, R12 1.2, R13 0.15, R14 0.01.
- Resource demand (levels, buildings, upkeep, crises): early 149, mid 184, late 60 units per game.
- Average stock per city grows from about 1.5 of each resource (R5) to 7-10 of each (R14).
- Market step: baseline x4 falls to the lowest step (A) in almost every game by Round 8-10 and stays there.
  Trader x4 stays near B/C until Round 11, then falls to A (Technology first: step 0.93 already in R10).

### Seat order (bot runner only)

Trader x4: seat 0 wins 28.6%, seats 1-3 23.2-24.3% (seat 0 reaches Level 3 at 4.16 vs 4.5-4.6).
Baseline x4: 23.6-26.5%, no clear effect. The bot runner lets seats act in order, so seat 0 trades
first. Human players act at the same time, so this is a bot runner effect, not a rule problem.

## 3. Problems found

1. **Prestige ceiling is reached; "do everything" wins.** Without projects and the Festival, the
   maximum is 19 (levels 3 + buildings 7 + 5 crises + 2 objectives x 2). 57.5% of trader x4 winners
   reach exactly 19, the minimum winner has 16, and 34.6% of games are shared victories. There is no
   trade-off: a normal trading city can build every building and pay every crisis. The Numbers Sheet
   expects 14-19 for the winner in a real game; bots already reach the top of that range while
   ignoring projects (up to +6 more).
2. **Cities level up too fast.** Traders reach Level 2 in Round 1 in 100% of games (starting stock +
   Round 1 production + one round of trades is enough) and Level 3 by Round 4.4. Even baseline (no
   trading) reaches Level 3 by Round 5.6 in 100% of games. RAPID_DEVELOPMENT (Level 3 by Round 7) is
   almost automatic.
3. **Late game is empty; trading stops.** After Round 10 there is nothing left to build. Trades fall
   to ~0 in Rounds 13-14, stock piles up to 7-10 of each resource (Watch List 1).
4. **Money has almost no use.** Each city makes 46-56 Money per game, but the only fixed Money costs
   are Market Hall 2, Level 3 4 and Grand Landmark 5 (11 total). Cities end with 50-65 unspent Money.
   Late in the game Money makes market buying (step A, 3 Money) an easy bypass of trading.
5. **City balance: Agricultural strongest, Technology weakest.**
   - Baseline x4 (no trading): Agricultural wins 39% vs 18-24% for the others (+0.8 visible, +0.2
     hidden Prestige). Architecture 8.1: a city weak even with baseline bots points to a numbers problem.
   - Trader x4: Technology wins 19.7% vs 24.5-28.8%. Technology is the hardest resource to trade:
     fewest trades (19.9 vs 21.9-23.5), most market sales (16.3 vs 9.9-11.4 units), its market price
     falls first, and it produces the most specialty (76.7 vs 71.4-73.1); every other city also gets
     +1 Technology per round from RESEARCH_LAB, so fewer cities need to buy it.
   - Demand side (baseline): Food is the most used resource (early/mid/late 25.0/27.2/20.5 vs
     Technology 21.9/23.4/15.2).
   - The exact cause of the Agricultural lead is not proven. Candidates: Food is the most demanded
     resource; D1 upkeep ties and the bots' tie orders start with Food. Needs a metric (see 5.).
6. **Market sell value is almost nothing.** Steps A and B both sell for 1. Baseline cities sell
   ~36 surplus units per game; in baseline x4 all prices sit at A from mid game on. (Market buy/sell
   use by traders is low, so this matters mainly for non-trading players.)
7. **Round 2 crisis hits unprepared cities.** In trader x4, ~1.0 city per game is Strained in Round 2:
   traders go to Level 2 in Round 1 and then cannot pay the Round 2 crisis. Overall Strained is low
   for traders (0.4 rounds) and higher for baseline (2.3 rounds, it never prepares). This looks
   intended (warning + preparation matters); no change suggested.

Not measurable yet: projects (Watch List 5), contract penalty (Watch List 2), Transit Network
(Watch List 4), objective difficulty per card (Watch List 6), opportunities.

## 4. Suggested value changes (owner decides)

The Numbers Sheet says: change only 2-3 knobs per test. Suggestion: test S1 + S2 + S3 in
`prototype-002`, keep S4 for the test after that.

| # | Change | JSON path | Fixes problem | Reason |
|---|--------|-----------|---------------|--------|
| S1 | Level 2 cost 3 -> 4 of each resource | `levels[1].upgradeCost` | 2, 1 | Numbers Sheet knob "cities level up too fast". Level 2 in Round 1 for 100% of trader cities means the first trade window decides nothing. +4 units makes Round 1 upgrades need real trades and delays Level 3 as well. |
| S2 | Grand Landmark Money 5 -> 10 | `buildings[GRAND_LANDMARK].cost.money` | 4, 1, 3 | Adds a real Money sink where Money piles up (late game). Makes the last +3 Prestige a choice against market buys and bids instead of automatic. Uses no new mechanic. |
| S3 | Research Lab cost F2 E2 M1 T0 -> F1 E2 M1 T1 | `buildings[RESEARCH_LAB].cost` | 5 | Technology is the least demanded and most overproduced resource (the Lab itself adds +1 Technology to every city); Food is the most demanded. This moves 1 unit of building demand from Food to Technology. Small change, so the city effect can be measured. |
| S4 | Money income -1 per level (L1 2->1, L2 3->2, L3 4->3) | `levels[*].moneyProduction` | 4 | Numbers Sheet knob "Money has no use". Only if S2 does not remove the Money pile-up; test separately because it also changes bids, projects and market use. |

Not suggested yet: project and contract values (bots do not use them), market steps (problem 6 is
mostly a non-trading effect; re-check after S2/S4 change the Money supply).

## 5. Metrics to add before the next report (not built)

- Opportunity results: bids per card, win rate, winning price.
- Per-objective kept / completed rates (Watch List 6) and per-building build rates (Watch List 4).
- Upkeep and crisis payments per resource per city, to find the cause of the Agricultural lead.
- A bot that contributes to projects and uses contracts, so Watch List 2 and 5 can be measured.
