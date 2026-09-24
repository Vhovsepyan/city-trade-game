package citytrade.server.room;

import java.util.Locale;

public enum BotType {
    BASELINE,
    TRADER;

    public static BotType parse(String value) {
        if (value == null) {
            throw new RoomException(RoomErrorCode.INVALID_BOT_TYPE, "bot type is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new RoomException(RoomErrorCode.INVALID_BOT_TYPE,
                    "bot type must be BASELINE or TRADER");
        }
    }
}
