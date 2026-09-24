# Balance report - prototype-002 (T18b)

Second simulation report. Prototype-002 is prototype-001 with only the three
approved changes below. Prototype-001 was not changed.

## 1. How the numbers were made

The same three 1000-game runs as T18 were made with seeds 1-1000 and zero
rejected commands:

```
./gradlew :game-sim:run --args="--ruleset prototype-002 --games 1000 --seed 1 --bots baseline,baseline,baseline,baseline"
./gradlew :game-sim:run --args="--ruleset prototype-002 --games 1000 --seed 1 --bots baseline,trader,baseline,trader"
./gradlew :game-sim:run --args="--ruleset prototype-002 --games 1000 --seed 1 --bots trader,trader,trader,trader"
```

Prototype-002 changes:

| Change | prototype-001 | prototype-002 |
|---|---:|---:|
| Level 2 upgrade cost (each resource) | 3 | 4 |
| Grand Landmark Money cost | 5 | 10 |
| Research Lab cost (F/E/M/T) | 2/2/1/0 | 1/2/1/1 |

The report compares the committed T18 prototype-001 JSON outputs with the
prototype-002 outputs. City assignment remains the seeded shuffle, so city
results are not tied to one seat.

## 2. Side-by-side results

### Game-level and pacing metrics

Values are `prototype-001 / prototype-002`. Level rounds are averaged over all
cities; every city reached Level 3 in every run for both rulesets.

| Metric | Baseline x4 | Mix 2+2 | Trader x4 |
|---|---:|---:|---:|
| Winning Prestige mean | 8.999 / 8.505 | 14.510 / 13.394 | 18.321 / 18.047 |
| Winning Prestige min-max | 5-13 / 5-12 | 8-19 / 8-19 | 16-19 / 15-19 |
| Shared victory rate | 0.249 / 0.215 | 0.079 / 0.096 | 0.346 / 0.395 |
| Level 2 average round | 2.000 / 2.864 | 1.760 / 2.676 | 1.000 / 2.008 |
| Level 3 average round | 5.578 / 6.087 | 5.786 / 6.336 | 4.452 / 4.967 |
| Trades per game | 0.000 / 0.000 | 5.516 / 5.069 | 44.076 / 43.438 |
| Money held per city after Round 14 | 65.083 / 59.993 | 60.780 / 56.065 | 49.531 / 39.930 |
| Strained rounds per city | 2.274 / 2.191 | 1.292 / 1.199 | 0.404 / 0.256 |

For trader x4, winners at exactly 19 Prestige fell from 57.5% (575/1000) to
38.5% (385/1000). The maximum is still 19, and all cities still reach Level 3.

### Win share by city

| City | Baseline x4 | Mix 2+2 | Trader x4 |
|---|---:|---:|---:|
| Agricultural | 0.390 / 0.367 | 0.262 / 0.228 | 0.288 / 0.285 |
| Industrial | 0.180 / 0.190 | 0.253 / 0.265 | 0.245 / 0.245 |
| Energy | 0.185 / 0.203 | 0.236 / 0.252 | 0.270 / 0.256 |
| Technology | 0.244 / 0.240 | 0.249 / 0.257 | 0.197 / 0.213 |

Technology improves in the trader run but remains the weakest city there.
Agricultural remains strongest for baseline bots.

## 3. What improved

1. S1 fixed the most visible pacing problem. Trader cities no longer upgrade to
   Level 2 in Round 1 on average; Level 2 moved from Round 1.000 to 2.008 and
   Level 3 from Round 4.452 to 4.967. Baseline Level 2 moved from Round 2.000
   to 2.864.
2. S2 created a larger Money sink. Trader cities ended with 39.930 Money each
   instead of 49.531, and baseline cities with 59.993 instead of 65.083.
   Market sales in trader x4 also fell from 16.936 to 13.296 units per game
   for Technology, showing less surplus pressure.
3. S3 modestly improved the Technology city in trader x4: its win share rose
   from 0.197 to 0.213. The Prestige ceiling is less common: 19-point winners
   fell by 19 percentage points in that run.
4. Trader cities were Strained less often (0.404 to 0.256 rounds per city),
   consistent with the delayed Level 2 and better crisis timing.

## 4. New problems and limits

1. The late game is still mostly empty. Trader x4 averaged 0.338 trades in
   Round 13 and 0.023 in Round 14 under prototype-002. Every city still
   reached Level 3, so the higher Level 2 cost delayed the build-out without
   keeping meaningful choices until the end.
2. The Prestige ceiling is reduced, not removed. 38.5% of trader-x4 games
   still had a 19-point winner, and shared victories rose from 0.346 to 0.395.
3. City balance is improved only slightly. Agricultural still led baseline
   wins (0.367), while Technology still trailed trader wins (0.213).
4. These bots still make no formal contracts or public-project contributions,
   so this report cannot evaluate those parts of the balance. In all three
   prototype-002 runs, projects and contracts remained untested by the bots.

## 5. Suggestions for the next test (maximum three)

1. Test S4 from the first report: reduce Level 1/2/3 Money production from
   2/3/4 to 1/2/3. Prototype-002 still leaves 39.930 Money per trader city at
   Round 14 even after doubling the Landmark cost.
2. Test a separate Level 3 pacing change, such as adding one to each Level 3
   resource cost. Prototype-002 still reaches Level 3 in every game and leaves
   almost no trader activity in the final two rounds.
3. Add project- and contract-aware bot behavior before making further changes
   to those values. Their effects are currently absent from all three reports,
   so more balance conclusions about them would be speculative.
