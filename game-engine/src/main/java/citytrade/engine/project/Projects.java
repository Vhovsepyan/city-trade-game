package citytrade.engine.project;

import citytrade.engine.Payments;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.ContributeToProject;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.ruleset.ProjectRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.ProjectContribution;
import citytrade.engine.state.ProjectStatus;
import citytrade.engine.state.PublicProject;
import java.util.List;
import java.util.Optional;

/**
 * Public projects, Numbers Sheet 16 and D9. A project appears in step 2.3 of its open round, takes
 * contributions in the windows up to its deadline round, and is resolved in step 4.3 of that round.
 * Prestige is added in step 4.4 of the deadline round; the production reward starts the round after.
 */
public final class Projects {

    private Projects() {
    }

    /** Step 2.3: projects whose window starts this round open for contributions. */
    public static GameState open(GameState state, List<DomainEvent> events) {
        GameState next = state;
        for (PublicProject project : state.projects()) {
            if (project.status() == ProjectStatus.UPCOMING && project.window().openRound() == state.round()) {
                next = next.withProject(project.withStatus(ProjectStatus.OPEN));
                events.add(new DomainEvent.ProjectOpened(project.id(), project.window().deadlineRound()));
            }
        }
        return next;
    }

    public static GameResult contribute(GameState state, ContributeToProject command, Ruleset ruleset) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        Optional<PublicProject> found = state.project(command.projectId());
        if (found.isEmpty()) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PROJECT, "no project " + command.projectId());
        }
        PublicProject project = found.get();
        if (project.status() != ProjectStatus.OPEN) {
            return new GameResult.Rejected(RejectionCode.PROJECT_NOT_OPEN,
                    project.id() + " is " + project.status() + ", not OPEN");
        }
        ResourceBundle given = command.contribution();
        if (given.hasNegativeAmount()) {
            return new GameResult.Rejected(RejectionCode.INVALID_QUANTITY, "contribution must not be negative");
        }
        if (given.isEmpty()) {
            return new GameResult.Rejected(RejectionCode.EMPTY_CONTRIBUTION, "contribution must not be empty");
        }
        Optional<GameResult.Rejected> notFitting = checkFitsNeed(project, given);
        if (notFitting.isPresent()) {
            return notFitting.get();
        }
        PlayerState player = state.player(command.seat());
        Optional<GameResult.Rejected> unaffordable = Payments.checkAffordable(state.spendableHoldings(command.seat()), given);
        if (unaffordable.isPresent()) {
            return unaffordable.get();
        }
        GameState next = state.withPlayer(player.withHoldings(player.holdings().minus(given)))
                .withProject(project.withContribution(new ProjectContribution(command.seat(), state.round(), given)));
        return new GameResult.Accepted(next, List.of(new DomainEvent.ProjectContributed(
                command.seat(), project.id(), given, points(given, ruleset.projects()))));
    }

    /**
     * Step 4.3: an open project whose deadline is this round succeeds if it got everything it needs, otherwise
     * it fails and the contributions are lost. On success every qualifying contributor gets the production
     * reward as permanent extra production; it is first produced in step 1.3 of the next round.
     */
    public static GameState resolveDeadline(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        GameState next = state;
        for (PublicProject project : state.projects()) {
            if (project.status() != ProjectStatus.OPEN || project.window().deadlineRound() != state.round()) {
                continue;
            }
            if (!project.remainingNeed().isEmpty()) {
                next = next.withProject(project.withStatus(ProjectStatus.FAILED));
                events.add(new DomainEvent.ProjectFailed(project.id(), project.contributed()));
                continue;
            }
            next = next.withProject(project.withStatus(ProjectStatus.SUCCEEDED));
            List<Integer> qualifying = qualifyingSeats(project, next, ruleset.projects());
            ResourceBundle reward = project.card().productionReward();
            for (int seat : qualifying) {
                PlayerState player = next.player(seat);
                next = next.withPlayer(player.withExtraProduction(player.extraProduction().plus(reward)));
            }
            events.add(new DomainEvent.ProjectSucceeded(project.id(), qualifying, reward));
        }
        return next;
    }

    /**
     * Step 4.4: Prestige for projects that succeeded this round. Only qualifying contributors get Prestige;
     * the largest (by points, D9) among them gets the largest-contributor Prestige, or the tied Prestige if
     * several share the largest total.
     */
    public static GameState awardPrestige(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        ProjectRules rules = ruleset.projects();
        GameState next = state;
        for (PublicProject project : state.projects()) {
            if (project.status() != ProjectStatus.SUCCEEDED || project.window().deadlineRound() != state.round()) {
                continue;
            }
            List<Integer> qualifying = qualifyingSeats(project, state, rules);
            int largest = qualifying.stream().mapToInt(seat -> pointsOf(project, seat, rules)).max().orElse(0);
            long leaders = qualifying.stream().filter(seat -> pointsOf(project, seat, rules) == largest).count();
            for (int seat : qualifying) {
                int gained;
                if (pointsOf(project, seat, rules) < largest) {
                    gained = rules.otherQualifyingPrestige();
                } else if (leaders > 1) {
                    gained = rules.tiedLargestPrestige();
                } else {
                    gained = rules.largestContributorPrestige();
                }
                PlayerState player = next.player(seat);
                next = next.withPlayer(player.withPrestige(player.prestige() + gained));
                events.add(new DomainEvent.PrestigeGained(seat, gained));
            }
        }
        return next;
    }

    /** D9: contribution points of a bundle. Throws on int overflow instead of wrapping. */
    public static int points(ResourceBundle given, ProjectRules rules) {
        return Math.addExact(Math.multiplyExact(given.resourceUnits(), rules.pointsPerResource()),
                Math.multiplyExact(given.money(), rules.pointsPerMoney()));
    }

    /** Total contribution points of {@code seat} to {@code project}. */
    public static int pointsOf(PublicProject project, int seat, ProjectRules rules) {
        return points(project.contributedBy(seat), rules);
    }

    /** Seats (in seat order) whose total contribution reaches the qualifying minimum. */
    public static List<Integer> qualifyingSeats(PublicProject project, GameState state, ProjectRules rules) {
        return state.players().stream()
                .map(PlayerState::seat)
                .filter(seat -> pointsOf(project, seat, rules) >= rules.minQualifyingPoints())
                .toList();
    }

    /** Only the types printed on the card, and not more than the card still needs. */
    private static Optional<GameResult.Rejected> checkFitsNeed(PublicProject project, ResourceBundle given) {
        ResourceBundle needs = project.card().needs();
        ResourceBundle remaining = project.remainingNeed();
        for (Resource resource : Resource.values()) {
            Optional<GameResult.Rejected> rejected = checkAmount(project, resource.name(), given.amountOf(resource),
                    needs.amountOf(resource), remaining.amountOf(resource));
            if (rejected.isPresent()) {
                return rejected;
            }
        }
        return checkAmount(project, "Money", given.money(), needs.money(), remaining.money());
    }

    private static Optional<GameResult.Rejected> checkAmount(PublicProject project, String type, int given, int needed,
            int remaining) {
        if (given > 0 && needed == 0) {
            return Optional.of(new GameResult.Rejected(RejectionCode.RESOURCE_NOT_NEEDED,
                    project.id() + " does not take " + type));
        }
        if (given > remaining) {
            return Optional.of(new GameResult.Rejected(RejectionCode.CONTRIBUTION_EXCEEDS_NEED,
                    project.id() + " still needs " + remaining + " " + type + ", got " + given));
        }
        return Optional.empty();
    }
}
