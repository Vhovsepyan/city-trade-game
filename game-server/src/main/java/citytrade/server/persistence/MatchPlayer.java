package citytrade.server.persistence;

import citytrade.engine.CityType;
import java.util.Objects;

/** One seat of a logged match (Architecture 7.3 {@code match_players}). */
public record MatchPlayer(int seat, String nickname, CityType city, boolean bot) {

    public MatchPlayer {
        Objects.requireNonNull(city);
    }
}
