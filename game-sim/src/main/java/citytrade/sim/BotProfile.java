package citytrade.sim;

import citytrade.bots.BaselineBot;
import citytrade.bots.Bot;
import citytrade.bots.TraderBot;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

/** The bot profiles of Architecture 8.1 that a simulation can seat. */
public enum BotProfile {
    BASELINE(BaselineBot::new),
    TRADER(TraderBot::new);

    private final Supplier<Bot> factory;

    BotProfile(Supplier<Bot> factory) {
        this.factory = factory;
    }

    public Bot newBot() {
        return factory.get();
    }

    /** The name used on the command line and in the output, e.g. "trader". */
    public String cliName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BotProfile fromCliName(String name) {
        return Arrays.stream(values())
                .filter(profile -> profile.cliName().equals(name.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown bot '" + name + "', expected one of "
                        + Arrays.stream(values()).map(BotProfile::cliName).toList()));
    }
}
