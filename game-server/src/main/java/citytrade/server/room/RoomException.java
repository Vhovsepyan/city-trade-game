package citytrade.server.room;

/** A deliberately small exception translated to the stable REST error shape. */
public class RoomException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final RoomErrorCode code;

    public RoomException(RoomErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public RoomErrorCode code() {
        return code;
    }
}
