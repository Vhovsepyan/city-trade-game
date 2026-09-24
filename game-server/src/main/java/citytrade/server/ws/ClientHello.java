package citytrade.server.ws;

/**
 * The mandatory first message on {@code /ws} (Architecture 6.6): the token identifies the seat, never a
 * playerId or room code sent by the client. Any other field is rejected (unknown property).
 */
record ClientHello(Integer protocolVersion, String token) {
}
