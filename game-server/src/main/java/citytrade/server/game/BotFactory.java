package citytrade.server.game;

import citytrade.bots.BaselineBot;
import citytrade.bots.Bot;
import citytrade.bots.TraderBot;
import citytrade.server.room.BotType;

/** D24: turns a room seat's chosen bot profile into the {@link Bot} that plays it. */
final class BotFactory {

    private BotFactory() {
    }

    static Bot create(BotType type) {
        return switch (type) {
            case BASELINE -> new BaselineBot();
            case TRADER -> new TraderBot();
        };
    }
}
