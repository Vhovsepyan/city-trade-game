package citytrade.engine.setup;

import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** D5: handles {@link ChooseObjectives}. Each player chooses once; the choice cannot be changed. */
public final class ObjectiveChoice {

    private ObjectiveChoice() {
    }

    public static GameResult apply(GameState state, ChooseObjectives command, Ruleset ruleset) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        PlayerState player = state.player(command.seat());
        if (player.hasChosenObjectives()) {
            return new GameResult.Rejected(RejectionCode.OBJECTIVES_ALREADY_CHOSEN,
                    "seat " + command.seat() + " has already chosen objectives");
        }
        List<String> keptIds = command.keptObjectiveIds();
        int keptPerPlayer = ruleset.objectives().keptPerPlayer();
        if (keptIds.size() != keptPerPlayer) {
            return new GameResult.Rejected(RejectionCode.INVALID_OBJECTIVE_CHOICE,
                    "must keep exactly " + keptPerPlayer + " objectives, but chose " + keptIds.size());
        }
        Set<String> uniqueIds = new HashSet<>(keptIds);
        if (uniqueIds.size() != keptIds.size()) {
            return new GameResult.Rejected(RejectionCode.INVALID_OBJECTIVE_CHOICE,
                    "the same objective was chosen twice: " + keptIds);
        }
        Set<String> dealtIds = new HashSet<>(player.dealtObjectives().stream().map(ObjectiveCard::id).toList());
        for (String id : keptIds) {
            if (!dealtIds.contains(id)) {
                return new GameResult.Rejected(RejectionCode.INVALID_OBJECTIVE_CHOICE,
                        "objective " + id + " was not dealt to seat " + command.seat());
            }
        }

        // Kept cards stay in dealt order, so the resulting state does not depend on the order in the command.
        List<ObjectiveCard> kept = player.dealtObjectives().stream()
                .filter(card -> uniqueIds.contains(card.id()))
                .toList();
        GameState next = state.withPlayer(player.withKeptObjectives(kept));
        return new GameResult.Accepted(next, List.of(new DomainEvent.ObjectivesChosen(command.seat())));
    }
}
