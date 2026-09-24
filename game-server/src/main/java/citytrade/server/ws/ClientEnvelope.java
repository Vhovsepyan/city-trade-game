package citytrade.server.ws;

import tools.jackson.databind.JsonNode;

/**
 * Every client-to-server message after HELLO (Architecture 6.9). No {@code playerId} or {@code seat} field:
 * the seat comes only from the session (Architecture 6.6). Any other unknown top-level field is rejected.
 *
 * @param commandType READY, SNAPSHOT_REQUEST, or one of the player command types ({@code docs/PROTOCOL.md})
 * @param payload     command-specific fields; never a "seat" field (rejected as an unknown property)
 */
record ClientEnvelope(Integer protocolVersion, String commandId, String commandType, JsonNode payload) {
}
