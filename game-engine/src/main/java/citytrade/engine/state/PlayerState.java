package citytrade.engine.state;

import citytrade.engine.CityType;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.ObjectiveCard;
import java.util.List;

/**
 * One player's city. Hidden data (holdings, objectives) is kept here too; who may see what is
 * decided outside the engine (D6).
 *
 * @param seat              seat 0-3 (D11)
 * @param holdings          F, E, M, T and Money the player owns
 * @param dealtObjectives   the hidden objective cards dealt at setup
 * @param keptObjectives    the dealt cards the player keeps; empty until {@code ChooseObjectives}
 */
public record PlayerState(
        int seat,
        CityType city,
        ResourceBundle holdings,
        int level,
        List<ObjectiveCard> dealtObjectives,
        List<ObjectiveCard> keptObjectives) {

    public PlayerState {
        dealtObjectives = List.copyOf(dealtObjectives);
        keptObjectives = List.copyOf(keptObjectives);
    }

    public boolean hasChosenObjectives() {
        return !keptObjectives.isEmpty();
    }

    public PlayerState withKeptObjectives(List<ObjectiveCard> kept) {
        return new PlayerState(seat, city, holdings, level, dealtObjectives, kept);
    }
}
