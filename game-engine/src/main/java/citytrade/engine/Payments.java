package citytrade.engine;

import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import java.util.Optional;

/** The one affordability check for commands that pay a fixed cost (levels, buildings, event options). */
public final class Payments {

    private Payments() {
    }

    /** Empty if {@code holdings} cover {@code cost}; otherwise the rejection for the first missing part. */
    public static Optional<GameResult.Rejected> checkAffordable(ResourceBundle holdings, ResourceBundle cost) {
        for (Resource resource : Resource.values()) {
            if (holdings.amountOf(resource) < cost.amountOf(resource)) {
                return Optional.of(new GameResult.Rejected(RejectionCode.INSUFFICIENT_RESOURCES, "costs "
                        + cost.amountOf(resource) + " " + resource + ", has " + holdings.amountOf(resource)));
            }
        }
        if (holdings.money() < cost.money()) {
            return Optional.of(new GameResult.Rejected(RejectionCode.INSUFFICIENT_MONEY,
                    "costs " + cost.money() + " Money, has " + holdings.money()));
        }
        return Optional.empty();
    }
}
