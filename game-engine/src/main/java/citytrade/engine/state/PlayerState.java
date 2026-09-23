package citytrade.engine.state;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.ObjectiveCard;
import java.util.List;
import java.util.Objects;

/**
 * One player's city. Hidden data (holdings, objectives) is kept here too; who may see what is
 * decided outside the engine (D6).
 *
 * @param seat                    seat 0-3 (D11)
 * @param holdings                F, E, M, T and Money the player owns
 * @param dealtObjectives         the hidden objective cards dealt at setup
 * @param keptObjectives          the dealt cards the player keeps; empty until {@code ChooseObjectives}
 * @param strained                a mandatory payment failed this round; the penalty applies next round
 *                                (Numbers Sheet 9). A flag, not a counter: it does not stack.
 * @param strainedPenaltyActive   the Strained penalty applies to this round's production (set in step 1.2)
 * @param upkeepPriority          D1: the player's upkeep payment order (all non-specialty resources);
 *                                empty = default order (most held first)
 * @param marketThisRound         units bought from / sold to the market this round (cleared in step 4.6)
 */
public record PlayerState(
        int seat,
        CityType city,
        ResourceBundle holdings,
        int level,
        List<ObjectiveCard> dealtObjectives,
        List<ObjectiveCard> keptObjectives,
        boolean strained,
        boolean strainedPenaltyActive,
        List<Resource> upkeepPriority,
        MarketActivity marketThisRound) {

    public PlayerState {
        dealtObjectives = List.copyOf(dealtObjectives);
        keptObjectives = List.copyOf(keptObjectives);
        upkeepPriority = List.copyOf(upkeepPriority);
        Objects.requireNonNull(marketThisRound);
    }

    /** A new city at setup: not Strained, default upkeep order, objectives not chosen yet. */
    public static PlayerState starting(int seat, CityType city, ResourceBundle holdings, int level,
            List<ObjectiveCard> dealtObjectives) {
        return new PlayerState(seat, city, holdings, level, dealtObjectives, List.of(), false, false, List.of(),
                MarketActivity.NONE);
    }

    public boolean hasChosenObjectives() {
        return !keptObjectives.isEmpty();
    }

    public PlayerState withKeptObjectives(List<ObjectiveCard> kept) {
        return new PlayerState(seat, city, holdings, level, dealtObjectives, kept, strained, strainedPenaltyActive,
                upkeepPriority, marketThisRound);
    }

    public PlayerState withHoldings(ResourceBundle newHoldings) {
        return new PlayerState(seat, city, newHoldings, level, dealtObjectives, keptObjectives, strained,
                strainedPenaltyActive, upkeepPriority, marketThisRound);
    }

    public PlayerState withStrained(boolean newStrained, boolean newPenaltyActive) {
        return new PlayerState(seat, city, holdings, level, dealtObjectives, keptObjectives, newStrained,
                newPenaltyActive, upkeepPriority, marketThisRound);
    }

    public PlayerState withUpkeepPriority(List<Resource> order) {
        return new PlayerState(seat, city, holdings, level, dealtObjectives, keptObjectives, strained,
                strainedPenaltyActive, order, marketThisRound);
    }

    public PlayerState withMarketThisRound(MarketActivity activity) {
        return new PlayerState(seat, city, holdings, level, dealtObjectives, keptObjectives, strained,
                strainedPenaltyActive, upkeepPriority, activity);
    }
}
