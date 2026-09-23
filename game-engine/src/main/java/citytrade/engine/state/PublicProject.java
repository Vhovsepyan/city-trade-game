package citytrade.engine.state;

import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.ProjectRules.ProjectCard;
import citytrade.engine.ruleset.ProjectRules.ProjectWindow;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A drawn public project card in its window (Numbers Sheet 16).
 *
 * @param card          the project card (needs and success reward)
 * @param window        the rounds the project is open; resolved in the Round Resolution of the deadline round
 * @param status        where the project is in its life
 * @param contributions every contribution in the order they were made
 */
public record PublicProject(ProjectCard card, ProjectWindow window, ProjectStatus status,
        List<ProjectContribution> contributions) {

    public PublicProject {
        Objects.requireNonNull(card);
        Objects.requireNonNull(window);
        Objects.requireNonNull(status);
        contributions = List.copyOf(contributions);
    }

    /** A project drawn at setup: not open yet, nothing contributed. */
    public static PublicProject upcoming(ProjectCard card, ProjectWindow window) {
        return new PublicProject(card, window, ProjectStatus.UPCOMING, List.of());
    }

    public String id() {
        return card.id();
    }

    /** Everything given so far by all players. */
    public ResourceBundle contributed() {
        return contributions.stream().map(ProjectContribution::given).reduce(ResourceBundle.EMPTY, ResourceBundle::plus);
    }

    /** Everything given so far by {@code seat}. */
    public ResourceBundle contributedBy(int seat) {
        return contributions.stream()
                .filter(contribution -> contribution.seat() == seat)
                .map(ProjectContribution::given)
                .reduce(ResourceBundle.EMPTY, ResourceBundle::plus);
    }

    /** What the project still needs; never negative because a contribution may not exceed it. */
    public ResourceBundle remainingNeed() {
        return card.needs().minus(contributed());
    }

    public PublicProject withStatus(ProjectStatus newStatus) {
        return new PublicProject(card, window, newStatus, contributions);
    }

    public PublicProject withContribution(ProjectContribution contribution) {
        List<ProjectContribution> updated = new ArrayList<>(contributions);
        updated.add(contribution);
        return new PublicProject(card, window, status, updated);
    }
}
