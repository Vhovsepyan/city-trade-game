package citytrade.server.rest;

import citytrade.server.room.RoomErrorCode;
import citytrade.server.room.RoomException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps REST failures stable and safe to display in the client. */
@RestControllerAdvice
public class RestErrorHandler {

    @ExceptionHandler(RoomException.class)
    public ResponseEntity<Map<String, String>> roomError(RoomException exception) {
        return ResponseEntity.status(status(exception.code()))
                .body(Map.of("code", exception.code().name(), "message", exception.getMessage()));
    }

    private static HttpStatus status(RoomErrorCode code) {
        return switch (code) {
            case ROOM_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_HOST -> HttpStatus.FORBIDDEN;
            case ROOM_FULL, ROOM_NOT_JOINABLE, ROOM_NOT_IN_LOBBY, ROOM_NOT_FULL,
                    HOST_CANNOT_BE_REMOVED, ROOM_NOT_CLOSABLE -> HttpStatus.CONFLICT;
            case INVALID_NICKNAME, INVALID_BOT_TYPE, INVALID_SEAT -> HttpStatus.BAD_REQUEST;
        };
    }
}
