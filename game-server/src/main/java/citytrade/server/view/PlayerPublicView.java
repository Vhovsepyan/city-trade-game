package citytrade.server.view;

import citytrade.engine.CityType;
import citytrade.engine.state.BuiltBuilding;
import java.util.List;

/**
 * What every player sees about one seat, including its own (D6): city, level, buildings, visible Prestige,
 * Contracts Broken, plus the server-side READY and connection status (Architecture 6.4, D21, D23).
 * Never carries holdings, Money, objectives or anything else private to that seat.
 */
public record PlayerPublicView(
        int seat,
        CityType city,
        int level,
        List<BuiltBuilding> buildings,
        int prestige,
        int contractsBroken,
        boolean ready,
        boolean disconnected) {

    public PlayerPublicView {
        buildings = List.copyOf(buildings);
    }
}
