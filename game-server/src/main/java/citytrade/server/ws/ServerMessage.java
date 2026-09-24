package citytrade.server.ws;

import citytrade.server.view.NoticeEvent;
import citytrade.server.view.PlayerGameView;
import java.util.Objects;

/**
 * Every {@code /ws} server-to-client message (Architecture 6.9): one flat shape for all
 * {@link ServerMessageType}s, nullable fields for whichever ones a given type does not use (same convention
 * as {@link PlayerGameView}: nullable rather than {@code Optional}, so it serializes directly). Every message
 * carries {@code stateVersion} and {@code roomVersion}, even when the type does not need them (0/0 before a
 * room is known, e.g. {@code PROTOCOL_UNSUPPORTED}).
 *
 * @param commandId     echoes the client's {@code commandId}; only for COMMAND_ACCEPTED/COMMAND_REJECTED
 * @param rejectionCode the engine {@code RejectionCode} name, or a protocol-level code; only for COMMAND_REJECTED
 * @param message       human-readable detail; for COMMAND_REJECTED and PROTOCOL_UNSUPPORTED
 * @param view          the receiving seat's {@code PlayerGameView}; for FULL_SNAPSHOT/STATE_UPDATE/ROOM_UPDATE
 * @param eventKind     the {@code NoticeEvent} record's simple name, e.g. "MarketBought"; {@code NoticeEvent}
 *                      itself has no Jackson type discriminator (T21 only ever wrote it, never read it back),
 *                      so the client needs this to know which shape {@code event} is
 * @param event         the client-safe domain event; for NOTICE/ROUND_WARNING/ROUND_RESOLVED/GAME_FINISHED
 */
record ServerMessage(
        ServerMessageType type,
        long stateVersion,
        long roomVersion,
        String commandId,
        String rejectionCode,
        String message,
        PlayerGameView view,
        String eventKind,
        NoticeEvent event) {

    ServerMessage {
        Objects.requireNonNull(type);
    }

    static ServerMessage protocolUnsupported(String message) {
        return new ServerMessage(ServerMessageType.PROTOCOL_UNSUPPORTED, 0, 0, null, null, message, null, null,
                null);
    }

    static ServerMessage snapshot(long stateVersion, long roomVersion, PlayerGameView view) {
        return new ServerMessage(ServerMessageType.FULL_SNAPSHOT, stateVersion, roomVersion, null, null, null,
                view, null, null);
    }

    static ServerMessage stateUpdate(long stateVersion, long roomVersion, PlayerGameView view) {
        return new ServerMessage(ServerMessageType.STATE_UPDATE, stateVersion, roomVersion, null, null, null,
                view, null, null);
    }

    static ServerMessage roomUpdate(long stateVersion, long roomVersion, PlayerGameView view) {
        return new ServerMessage(ServerMessageType.ROOM_UPDATE, stateVersion, roomVersion, null, null, null,
                view, null, null);
    }

    static ServerMessage accepted(long stateVersion, long roomVersion, String commandId) {
        return new ServerMessage(ServerMessageType.COMMAND_ACCEPTED, stateVersion, roomVersion, commandId, null,
                null, null, null, null);
    }

    static ServerMessage rejected(long stateVersion, long roomVersion, String commandId, String rejectionCode,
            String message) {
        return new ServerMessage(ServerMessageType.COMMAND_REJECTED, stateVersion, roomVersion, commandId,
                rejectionCode, message, null, null, null);
    }

    static ServerMessage event(ServerMessageType type, long stateVersion, long roomVersion, NoticeEvent event) {
        return new ServerMessage(type, stateVersion, roomVersion, null, null, null, null,
                event.getClass().getSimpleName(), event);
    }
}
