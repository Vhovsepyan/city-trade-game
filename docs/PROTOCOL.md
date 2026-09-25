# PROTOCOL.md - WebSocket protocol (T22, T23)

Endpoint: `/ws`. Plain `TextWebSocketHandler`, JSON text frames, no STOMP, no message broker
(Architecture 6.8). Built by `citytrade.server.ws.GameWebSocketHandler`.

M4 builds the React client from this file. See also `docs/VIEW.md` for every `PlayerGameView` field.

## Connecting

The first message on every connection must be HELLO. It carries the reconnect token returned by
`POST /rooms` or `POST /rooms/{code}/join` - never a room code or a seat number. The token alone
identifies both the room and the seat (Architecture 6.6: identity comes from the connection).

```json
{
  "protocolVersion": 1,
  "token": "the-bearer-token-returned-by-REST"
}
```

- Anything that is not a valid HELLO (wrong shape, an unknown token, a token for a room that is not
  currently live, or a token for a CLOSED room) -> the connection is closed, no message is sent first.
- `protocolVersion` other than `1` -> one `PROTOCOL_UNSUPPORTED` message, then the connection is closed.
- Success -> one `FULL_SNAPSHOT` message for that seat, then normal exchange begins.
- Reconnect (Architecture 6.10): HELLO with the same token any time later opens a NEW connection for the
  same seat and replaces the old one - the old connection is closed by the server. A seat with no live
  connection is treated as disconnected (D23): it is passive (no automatic commands are sent for it, only
  the automatic rules like upkeep/crisis/contract payments still apply) and counts as READY (D21) until it
  reconnects. Every other connected seat sees this in `ROOM_UPDATE`'s `view.players[].disconnected`.

## Client -> server (after HELLO)

Every later message is a `CommandEnvelope`:

```json
{
  "protocolVersion": 1,
  "commandId": "c-1",
  "commandType": "BUY_FROM_MARKET",
  "payload": { "resource": "FOOD", "quantity": 2 }
}
```

- No `playerId` or `seat` field anywhere, including inside `payload`: the seat always comes from the
  session (Architecture 6.6). Any unknown field, anywhere in the message or the payload, is rejected -
  in particular, a payload with a `seat` field is rejected the same way as any other unknown field.
- `protocolVersion` other than `1` (including missing) on any post-HELLO message -> one
  `PROTOCOL_UNSUPPORTED` message, then the connection is closed, exactly like a bad HELLO.
- `UPGRADE_CITY` and `SNAPSHOT_REQUEST` take no payload fields; a payload containing any field
  (including `{}`'s extra siblings) is rejected as `INVALID_PAYLOAD`.
- `commandId` is required on every player command (Architecture 6.7) and is the client's idempotency key:
  sending the exact same `commandId` again from the SAME seat never reaches the engine a second time - it
  gets back the exact same reply (`COMMAND_ACCEPTED`/`COMMAND_REJECTED`, same `stateVersion`) as the first
  time. A missing/blank `commandId` -> `INVALID_PAYLOAD`. The same `commandId` used by a DIFFERENT seat ->
  `COMMAND_REJECTED` with `rejectionCode: "COMMAND_ID_REUSED"` (this can only happen with a colliding
  client-generated id; a UUID per command avoids it in practice). `commandId` is not required for `READY`
  or `SNAPSHOT_REQUEST` (neither is an idempotency concern: both are naturally safe to repeat).
- `commandType` is one of:
  - `READY` - payload `{ "ready": true }` or `{ "ready": false }` (D21). Not an engine command: replies
    with `ROOM_UPDATE` to every connected seat (never `COMMAND_ACCEPTED`).
  - `SNAPSHOT_REQUEST` - no payload. Replies with one `FULL_SNAPSHOT` to the sender only; usable any
    time, e.g. after noticing a gap in `stateVersion` or `roomVersion`.
  - one of the player command types below.

### Player command types and payloads

| commandType | payload fields |
|---|---|
| `CHOOSE_OBJECTIVES` | `keptObjectiveIds: string[]` |
| `SET_UPKEEP_PRIORITY` | `order: Resource[]` |
| `BUY_FROM_MARKET` | `resource: Resource, quantity: int` |
| `SELL_TO_MARKET` | `resource: Resource, quantity: int` |
| `UPGRADE_CITY` | (none - send `{}` or omit `payload`) |
| `BUILD_BUILDING` | `buildingId: string, chosenResource: Resource or null` |
| `PROPOSE_TRADE` | `recipientSeat: int, offered: ResourceBundle, requested: ResourceBundle` |
| `ACCEPT_TRADE` | `offerId: int` |
| `REJECT_TRADE` | `offerId: int` |
| `CANCEL_TRADE` | `offerId: int` |
| `COUNTER_TRADE` | `offerId: int, offered: ResourceBundle, requested: ResourceBundle` |
| `PROPOSE_CONTRACT` | `creditorSeat: int, debtorSeat: int, givenNow: ResourceBundle, owed: ResourceBundle, dueRound: int` |
| `SIGN_CONTRACT` | `contractId: int` |
| `BREAK_CONTRACT` | `contractId: int` |
| `CANCEL_CONTRACT_MUTUALLY` | `contractId: int` |
| `SET_CRISIS_POLICY` | `policy: "PAY" or "SKIP"` |
| `USE_EVENT_OPTION` | `chosenResource: Resource or null` |
| `CONTRIBUTE_TO_PROJECT` | `projectId: string, contribution: ResourceBundle` |
| `PLACE_BID` | `opportunityId: string, amount: int` |

`Resource` is one of `FOOD`, `ENERGY`, `MATERIALS`, `TECHNOLOGY`. `ResourceBundle` is always all five
fields: `{ "food": int, "energy": int, "materials": int, "technology": int, "money": int }` (0 for the
ones you are not using).

Example - proposing a trade:

```json
{
  "protocolVersion": 1,
  "commandId": "c-2",
  "commandType": "PROPOSE_TRADE",
  "payload": {
    "recipientSeat": 1,
    "offered": { "food": 2, "energy": 0, "materials": 0, "technology": 0, "money": 0 },
    "requested": { "food": 0, "energy": 0, "materials": 0, "technology": 1, "money": 0 }
  }
}
```

## Server -> client

Every message carries `type`, `stateVersion` and `roomVersion`. Other fields are present only for the
types listed below (absent, not null-valued, for every other type).

```json
{
  "type": "FULL_SNAPSHOT",
  "stateVersion": 3,
  "roomVersion": 1,
  "commandId": null,
  "rejectionCode": null,
  "message": null,
  "view": { "seat": 0, "...": "the receiving seat's PlayerGameView, see docs/VIEW.md" },
  "eventKind": null,
  "event": null
}
```

- `FULL_SNAPSHOT`, `STATE_UPDATE`, `ROOM_UPDATE` carry `view`: the receiving seat's full
  `PlayerGameView` (`docs/VIEW.md`), never a delta. `STATE_UPDATE` means the authoritative `GameState`
  changed (`stateVersion` went up); `ROOM_UPDATE` means only server metadata changed - READY, connection
  status, `phaseEndsAt` - and `stateVersion` is unchanged. Sent to every connected seat in the room,
  each with its own `view`.
- `COMMAND_ACCEPTED` / `COMMAND_REJECTED` are sent only to the seat that sent the command, and carry
  `commandId` (echoed back). `COMMAND_REJECTED` also carries `rejectionCode` and `message`.
  `rejectionCode` is either one of the engine's `RejectionCode` values (the command reached the engine
  and was rejected there), or `UNSUPPORTED_COMMAND_TYPE` / `INVALID_PAYLOAD` / `COMMAND_ID_REUSED` for a
  message that never reached the engine at all (unknown `commandType`; a payload that does not parse,
  including one with an extra `seat` field, or a missing `commandId`; or a `commandId` already used by a
  different seat).
- `NOTICE`, `ROUND_WARNING`, `ROUND_RESOLVED`, `GAME_FINISHED` carry `eventKind` (the event's shape,
  e.g. `"MarketBought"`) and `event` (its fields, privacy-filtered by `NoticeProjector` - see
  `docs/VIEW.md` "Notices"). `ROUND_WARNING` is always `eventKind: "EventWarned"`, `ROUND_RESOLVED` is
  always `"RoundResolved"`, `GAME_FINISHED` is always `"GameFinished"`; every other event kind is a
  generic `NOTICE`. Sent only to the seat(s) `NoticeProjector` says may see that event.
- `PROTOCOL_UNSUPPORTED` carries `message` only; sent once, immediately before the connection is closed.

Examples:

```json
{
  "type": "COMMAND_ACCEPTED",
  "stateVersion": 4,
  "roomVersion": 1,
  "commandId": "c-2"
}
```

```json
{
  "type": "COMMAND_REJECTED",
  "stateVersion": 3,
  "roomVersion": 1,
  "commandId": "c-3",
  "rejectionCode": "INSUFFICIENT_MONEY",
  "message": "not enough Money"
}
```

```json
{
  "type": "NOTICE",
  "stateVersion": 5,
  "roomVersion": 1,
  "eventKind": "MarketBought",
  "event": { "seat": 0, "resource": "FOOD", "quantity": 2, "totalCost": 4 }
}
```

```json
{
  "type": "ROUND_RESOLVED",
  "stateVersion": 12,
  "roomVersion": 3,
  "eventKind": "RoundResolved",
  "event": { "round": 1 }
}
```

```json
{
  "type": "PROTOCOL_UNSUPPORTED",
  "stateVersion": 0,
  "roomVersion": 0,
  "message": "unsupported protocolVersion: 2"
}
```

## Versions

Three different version numbers, three different problems (Architecture 6.9):

- `rulesetVersion` (inside `view`) - game balance data.
- `protocolVersion` - this message shape.
- `stateVersion` / `roomVersion` - carried on every server message; a gap in either one means the
  client missed a message and should send `SNAPSHOT_REQUEST`.
