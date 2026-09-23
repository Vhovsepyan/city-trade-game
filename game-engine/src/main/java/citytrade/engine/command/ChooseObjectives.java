package citytrade.engine.command;

import java.util.List;

/**
 * D5: before Round 1 a player keeps {@code keptPerPlayer} of the objective cards dealt to them;
 * the others are discarded secretly.
 *
 * @param keptObjectiveIds ids of the dealt cards the player keeps
 */
public record ChooseObjectives(int seat, List<String> keptObjectiveIds) implements GameCommand {

    public ChooseObjectives {
        keptObjectiveIds = List.copyOf(keptObjectiveIds);
    }
}
