# VIEW.md - PlayerGameView field reference

Built by `citytrade.server.view.ViewProjector.project(GameState, RoomViewContext, seat)`
(T21). A pure function: same inputs -> same (`.equals()`) view; never mutates `state` or `room`.
This is the ONLY place that decides what one seat may see (Architecture 6.5, D6, D7).

`RoomViewContext` carries server-side metadata that never lives in the engine `GameState`
(phase deadline, READY per seat, connection status, room status, room version).

Every field below is either:
- **own** - sent only in the view built for that seat (`seat` parameter equals the owner), or
- **public** - the same value appears in the view of every seat.

There is no third case: a field is never partially redacted, it is either fully present or
fully absent (see `ViewProjectorTest`, which asserts this by parsing the JSON, not just by
reading Java field names).

## PlayerGameView (top level)

| Field | Visibility | Notes |
|---|---|---|
| `seat` | own | the seat this view was built for |
| `rulesetVersion` | public | same for every seat |
| `round` | public | current round number |
| `phase` | public | `GamePhase`: SETUP, AUTOMATIC, WORLD, WINDOW, RESOLUTION, FINISHED |
| `phaseEndsAt` | public | window (D20) or objective-choice (D22) deadline; null if none is running; server metadata, not engine state |
| `roomStatus` | public | LOBBY, ACTIVE, FINISHED, CLOSED; server metadata |
| `players` | public | one `PlayerPublicView` per seat, including the viewer's own (see below) |
| `own` | own | see `OwnView` below; absent (not built) for any other seat |
| `market` | public | `MarketPrices`: current buy/sell step index per resource; prices are fixed during the window |
| `activeEvent` | public | this round's `EventCard`; null if none |
| `eventWarning` | public | the event warned for next event round (round + card); null if none |
| `projects` | public | `PublicProject`s that have been opened at least once (step 2.3); an UPCOMING card is not announced |
| `opportunities` | public/own mixed | see `OpportunityView` below - the card, status and winner are public, the bid amounts are not |
| `contracts` | public | every `FormalContract`; formal contracts are fully public (Concept section 37) |
| `tradeOffers` | own (per pair) | only offers where the viewing seat is proposer or recipient (D7) |
| `finalResult` | public, only after the game ends | null until Round 14 resolves; then every seat's `FinalScore`, including kept and completed hidden objective ids - hidden objectives are revealed at the end (Numbers Sheet 15/18) |

## PlayerPublicView (one entry per seat, inside `players`)

Never carries holdings, Money, reserved Money or objectives - that is only in `own`.

| Field | Visibility | Notes |
|---|---|---|
| `seat` | public | |
| `city` | public | assigned at setup (D11) |
| `level` | public | |
| `buildings` | public | built buildings |
| `prestige` | public | visible Prestige only; hidden objective Prestige is not included until `finalResult` |
| `contractsBroken` | public | Contracts Broken counter (D13, tiebreaker 3) |
| `ready` | public | server metadata: this seat sent READY this round (D21), or counts as ready (bot/disconnected, D23) |
| `disconnected` | public | server metadata (D23) |

## OwnView (only in the viewer's own `own` field)

| Field | Visibility | Notes |
|---|---|---|
| `holdings` | own | F, E, M, T and Money the player owns right now (D6) |
| `reservedMoney` | own | Money reserved by this seat's own active bids (Numbers Sheet 17) |
| `dealtObjectives` | own | the 3 hidden objective cards dealt at setup (D5) |
| `keptObjectives` | own | the 2 cards this seat kept |
| `crisisPolicy` | own | this seat's policy (PAY/SKIP, D2) for the currently warned crisis |
| `upkeepPriority` | own | this seat's upkeep payment order (D1); empty = default order |
| `strained` | own | a mandatory payment failed this round; the penalty applies next round |
| `strainedPenaltyActive` | own | the Strained penalty (D17) applies to this round's production |

## OpportunityView (one entry per revealed regional opportunity)

| Field | Visibility | Notes |
|---|---|---|
| `card` | public | the opportunity card |
| `appearedRound` | public | the round it was revealed in |
| `status` | public | OPEN, WON or REMOVED |
| `winnerSeat` | public | null while OPEN; the winner once WON |
| `ownBid` | own | the viewing seat's own active bid; 0 if it has none. Other seats' bids are never included - not zeroed out, structurally absent from the JSON |

## Notices (NoticeProjector)

`NoticeProjector.project(resultingState, events)` turns raw domain events into per-seat
`Notice(seat, event)`s, where `event` is a client-safe `NoticeEvent`, not the raw `DomainEvent`. Raw domain
events are never sent to clients as-is: `NoticeEvent.OpportunityWon` has no `pricePaid` field, so the
winning bid never reaches any seat's notice, including the winner's own (same rule as `OpportunityView`,
which has no such field either).

- **Private** (owner, or trade-offer proposer+recipient only): `ObjectivesChosen`,
  `UpkeepPrioritySet`, `ResourcesProduced`, `UpkeepPaid`, `MarketBought`, `MarketSold`,
  `CityStrained`, `ExcessDiscarded`, `CrisisPolicySet`, `CrisisPaid`, `CrisisNotPaid`,
  `EventOptionUsed`, `BidPlaced`, `TradeProposed`, `TradeExecuted`, `TradeRejected`,
  `TradeCancelled`, `TradeExpired`, `TradeInvalidated` (the last four are routed by looking
  the offer up in `resultingState`, since the event itself may not carry both seats).
- **Public** (every seat): `MarketPriceMoved`, `CityUpgraded`, `BuildingBuilt`, `PrestigeGained`,
  every `Contract*` event (contracts are fully public), every event-schedule event
  (`EventActivated`, `EventWarned`, `EventEnded`), every project event (`ProjectOpened`,
  `ProjectContributed`, `ProjectSucceeded`, `ProjectFailed`), every opportunity event except the
  bid amount itself (`OpportunityRevealed`, `OpportunityWon`, `OpportunityNotWon`,
  `OpportunityRemoved` - note `BidPlaced` carries no amount and is private anyway), and round/game
  progress (`RoundStarted`, `RoundResolved`, `ObjectivesRevealed`, `FinalScoresRevealed`,
  `GameFinished`).

A trade event for an offer that no longer exists in `resultingState` reaches nobody.
