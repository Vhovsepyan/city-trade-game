package citytrade.server.view;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.state.CrisisPolicy;
import java.util.List;
import java.util.Objects;

/**
 * What only the owning seat sees (D6): its exact holdings, its hidden objectives, and its own private
 * settings (crisis policy D2, upkeep priority D1). Never sent for any other seat.
 *
 * @param holdings                 F, E, M, T and Money the player owns right now
 * @param reservedMoney            Money reserved by this seat's own active bids (Numbers Sheet 17)
 * @param dealtObjectives          the hidden objective cards dealt at setup
 * @param keptObjectives           the dealt cards this seat kept
 * @param crisisPolicy             this seat's policy for the currently warned crisis (D2)
 * @param upkeepPriority           this seat's upkeep payment order (D1); empty = default order
 * @param strained                 a mandatory payment failed this round; the penalty applies next round
 * @param strainedPenaltyActive    the Strained penalty applies to this round's production
 */
public record OwnView(
        ResourceBundle holdings,
        int reservedMoney,
        List<ObjectiveCard> dealtObjectives,
        List<ObjectiveCard> keptObjectives,
        CrisisPolicy crisisPolicy,
        List<Resource> upkeepPriority,
        boolean strained,
        boolean strainedPenaltyActive) {

    public OwnView {
        Objects.requireNonNull(holdings);
        Objects.requireNonNull(crisisPolicy);
        dealtObjectives = List.copyOf(dealtObjectives);
        keptObjectives = List.copyOf(keptObjectives);
        upkeepPriority = List.copyOf(upkeepPriority);
    }
}
