package citytrade.engine.command;

import citytrade.engine.ResourceBundle;
import java.util.Objects;

/**
 * Give resources and Money to an open public project (Numbers Sheet 16). Only the types the card needs,
 * never more than it still needs. Contributions cannot be taken back.
 */
public record ContributeToProject(int seat, String projectId, ResourceBundle contribution) implements GameCommand {

    public ContributeToProject {
        Objects.requireNonNull(projectId);
        Objects.requireNonNull(contribution);
    }
}
