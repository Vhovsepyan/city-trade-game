package citytrade.engine.project;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.ContributeToProject;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.economy.Production;
import citytrade.engine.ruleset.ProjectRules;
import citytrade.engine.ruleset.ProjectRules.ProjectCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.ProjectContribution;
import citytrade.engine.state.ProjectStatus;
import citytrade.engine.state.PublicProject;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Public projects, Numbers Sheet 16, Concept 22-23 and D9. */
class ProjectsTest {

    private static final long SEED = 7;
    private static final String GRID = "REGIONAL_POWER_GRID";
    private static final String RESEARCH = "REGIONAL_RESEARCH_INITIATIVE";
    private static final ResourceBundle PLENTY = new ResourceBundle(9, 9, 9, 9, 9);

    private final Ruleset ruleset = TestRulesets.withProjects(TestRulesets.standard(),
            projectRules(TestRulesets.prototypeProjects()));

    private static ProjectRules projectRules(List<ProjectCard> cards) {
        return new ProjectRules(
                List.of(new ProjectRules.ProjectWindow(3, 6), new ProjectRules.ProjectWindow(7, 10)),
                2, 1, 6, 3, 2, 1, cards);
    }

    private static ProjectCard card(String id) {
        return TestRulesets.prototypeProjects().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    /** A game before Round 1 with Power Grid as Project A (Rounds 3-6) and Research as Project B (Rounds 7-10). */
    private GameState fixedProjects() {
        GameState s = readyForRoundOne(SEED, ruleset);
        List<PublicProject> projects = List.of(
                PublicProject.upcoming(card(GRID), ruleset.projects().windows().get(0)),
                PublicProject.upcoming(card(RESEARCH), ruleset.projects().windows().get(1)));
        return new GameState(s.rulesetVersion(), s.round(), s.phase(), s.random(), s.players(), s.market(),
                s.eventDeck(), s.eventWarning(), s.activeEvent(), projects, s.opportunityDeck(), s.opportunities(), s.tradeOffers(),
                s.nextOfferId(), s.contracts(), s.nextContractId(), s.finalResult());
    }

    /** From any state before a StartRound: play on until the window of {@code round} is open. */
    private GameState windowOf(GameState state, int round) {
        GameState s = accept(state, new StartRound(), ruleset).state();
        while (s.round() < round) {
            s = accept(accept(s, new ResolveRound(), ruleset).state(), new StartRound(), ruleset).state();
        }
        return s;
    }

    /** The window of {@code round}, every player holding {@link #PLENTY}. */
    private GameState richWindowOf(int round) {
        GameState s = windowOf(fixedProjects(), round);
        for (int seat = 0; seat < 4; seat++) {
            s = withCity(s, seat, 1, PLENTY);
        }
        return s;
    }

    private GameState contribute(GameState state, int seat, String projectId, ResourceBundle given) {
        return accept(state, new ContributeToProject(seat, projectId, given), ruleset).state();
    }

    private static ResourceBundle bundle(int food, int energy, int materials, int technology, int money) {
        return new ResourceBundle(food, energy, materials, technology, money);
    }

    // --- opening ---

    @Test
    void projectOpensInStepTwoThreeOfItsOpenRound() {
        GameState round2 = windowOf(fixedProjects(), 2);
        assertEquals(ProjectStatus.UPCOMING, round2.project(GRID).orElseThrow().status());
        assertRejected(withCity(round2, 0, 1, PLENTY), new ContributeToProject(0, GRID, bundle(0, 1, 0, 0, 0)),
                ruleset, RejectionCode.PROJECT_NOT_OPEN);

        GameResult.Accepted round3 = accept(accept(round2, new ResolveRound(), ruleset).state(), new StartRound(),
                ruleset);
        assertTrue(round3.events().contains(new DomainEvent.ProjectOpened(GRID, 6)));
        assertEquals(ProjectStatus.OPEN, round3.state().project(GRID).orElseThrow().status());
        assertEquals(ProjectStatus.UPCOMING, round3.state().project(RESEARCH).orElseThrow().status());
    }

    @Test
    void projectBOpensInRoundSeven() {
        GameResult.Accepted round7 = accept(accept(windowOf(fixedProjects(), 6), new ResolveRound(), ruleset).state(),
                new StartRound(), ruleset);
        assertTrue(round7.events().contains(new DomainEvent.ProjectOpened(RESEARCH, 10)));
        assertEquals(ProjectStatus.OPEN, round7.state().project(RESEARCH).orElseThrow().status());
    }

    // --- contributing ---

    @Test
    void contributionCostsHoldingsAndCountsPointsResourceTwoMoneyOne() {
        GameState window = richWindowOf(3);
        GameResult.Accepted result = accept(window, new ContributeToProject(1, GRID, bundle(0, 2, 1, 0, 3)), ruleset);

        assertEquals(List.of(new DomainEvent.ProjectContributed(1, GRID, bundle(0, 2, 1, 0, 3), 9)), result.events());
        assertEquals(bundle(9, 7, 8, 9, 6), result.state().player(1).holdings());
        PublicProject project = result.state().project(GRID).orElseThrow();
        assertEquals(List.of(new ProjectContribution(1, 3, bundle(0, 2, 1, 0, 3))), project.contributions());
        assertEquals(bundle(0, 4, 5, 0, 3), project.remainingNeed());
        assertEquals(9, Projects.pointsOf(project, 1, ruleset.projects()));
    }

    @Test
    void pointsComeFromTheRuleset() {
        ProjectRules changed = new ProjectRules(ruleset.projects().windows(), 3, 2, 6, 3, 2, 1,
                ruleset.projects().cards());
        assertEquals(3 * 3 + 2 * 4, Projects.points(bundle(0, 2, 1, 0, 4), changed));
        assertEquals(2 * 3 + 4, Projects.points(bundle(0, 2, 1, 0, 4), ruleset.projects()));
    }

    @Test
    void severalContributionsAddUpAcrossRounds() {
        GameState window = contribute(richWindowOf(3), 0, GRID, bundle(0, 1, 0, 0, 0));
        window = contribute(window, 0, GRID, bundle(0, 0, 0, 0, 1));
        GameState round4 = windowOf(accept(window, new ResolveRound(), ruleset).state(), 4);
        round4 = contribute(withCity(round4, 0, 1, PLENTY), 0, GRID, bundle(0, 0, 1, 0, 0));

        PublicProject project = round4.project(GRID).orElseThrow();
        assertEquals(List.of(3, 3, 4), project.contributions().stream().map(ProjectContribution::round).toList());
        assertEquals(bundle(0, 1, 1, 0, 1), project.contributedBy(0));
        assertEquals(5, Projects.pointsOf(project, 0, ruleset.projects()));
    }

    @Test
    void onlyResourceTypesPrintedOnTheCard() {
        GameState window = richWindowOf(3);
        assertRejected(window, new ContributeToProject(0, GRID, bundle(1, 0, 0, 0, 0)), ruleset,
                RejectionCode.RESOURCE_NOT_NEEDED);
        assertRejected(window, new ContributeToProject(0, GRID, bundle(0, 1, 0, 1, 0)), ruleset,
                RejectionCode.RESOURCE_NOT_NEEDED);
    }

    @Test
    void cannotGiveMoreThanTheCardStillNeeds() {
        GameState window = richWindowOf(3);
        assertRejected(window, new ContributeToProject(0, GRID, bundle(0, 7, 0, 0, 0)), ruleset,
                RejectionCode.CONTRIBUTION_EXCEEDS_NEED);
        assertRejected(window, new ContributeToProject(0, GRID, bundle(0, 0, 0, 0, 7)), ruleset,
                RejectionCode.CONTRIBUTION_EXCEEDS_NEED);

        window = contribute(window, 0, GRID, bundle(0, 4, 0, 0, 0));
        assertRejected(window, new ContributeToProject(1, GRID, bundle(0, 3, 1, 0, 0)), ruleset,
                RejectionCode.CONTRIBUTION_EXCEEDS_NEED);
        window = contribute(window, 1, GRID, bundle(0, 2, 1, 0, 0));
        assertEquals(bundle(0, 0, 5, 0, 6), window.project(GRID).orElseThrow().remainingNeed());
    }

    @Test
    void completeProjectTakesNothingMoreButStaysOpenUntilTheDeadline() {
        GameState window = completeGrid(richWindowOf(3));
        PublicProject project = window.project(GRID).orElseThrow();
        assertTrue(project.remainingNeed().isEmpty());
        assertEquals(ProjectStatus.OPEN, project.status());
        assertRejected(window, new ContributeToProject(3, GRID, bundle(0, 0, 0, 0, 1)), ruleset,
                RejectionCode.CONTRIBUTION_EXCEEDS_NEED);

        GameState resolved = accept(window, new ResolveRound(), ruleset).state();
        assertEquals(ProjectStatus.OPEN, resolved.project(GRID).orElseThrow().status(), "resolved only at Round 6");
    }

    @Test
    void invalidContributionsAreRejectedAndChangeNothing() {
        GameState window = richWindowOf(3);
        assertRejected(window, new ContributeToProject(4, GRID, bundle(0, 1, 0, 0, 0)), ruleset,
                RejectionCode.UNKNOWN_PLAYER);
        assertRejected(window, new ContributeToProject(0, "FOOD_SECURITY_PROGRAM", bundle(1, 0, 0, 0, 0)), ruleset,
                RejectionCode.UNKNOWN_PROJECT);
        assertRejected(window, new ContributeToProject(0, RESEARCH, bundle(0, 0, 1, 0, 0)), ruleset,
                RejectionCode.PROJECT_NOT_OPEN);
        assertRejected(window, new ContributeToProject(0, GRID, ResourceBundle.EMPTY), ruleset,
                RejectionCode.EMPTY_CONTRIBUTION);
        assertRejected(window, new ContributeToProject(0, GRID, bundle(0, 2, -1, 0, 0)), ruleset,
                RejectionCode.INVALID_QUANTITY);

        GameState poor = withCity(window, 0, 1, bundle(0, 1, 0, 0, 1));
        assertRejected(poor, new ContributeToProject(0, GRID, bundle(0, 2, 0, 0, 0)), ruleset,
                RejectionCode.INSUFFICIENT_RESOURCES);
        assertRejected(poor, new ContributeToProject(0, GRID, bundle(0, 1, 0, 0, 2)), ruleset,
                RejectionCode.INSUFFICIENT_MONEY);
        assertEquals(bundle(0, 1, 0, 0, 1), poor.player(0).holdings());
        assertTrue(poor.project(GRID).orElseThrow().contributions().isEmpty());
    }

    @Test
    void contributingOnlyInTheWindow() {
        GameState resolution = accept(richWindowOf(3), new ResolveRound(), ruleset).state();
        assertRejected(resolution, new ContributeToProject(0, GRID, bundle(0, 1, 0, 0, 0)), ruleset,
                RejectionCode.INVALID_PHASE);
    }

    // --- deadline ---

    /** Seat 0: 4E+4M = 16 points, seat 1: 2E+2M = 8, seat 2: 5$ = 5 (not qualifying), seat 3: 1$ = 1. */
    private GameState completeGrid(GameState window) {
        GameState s = contribute(window, 0, GRID, bundle(0, 4, 4, 0, 0));
        s = contribute(s, 1, GRID, bundle(0, 2, 2, 0, 0));
        s = contribute(s, 2, GRID, bundle(0, 0, 0, 0, 5));
        return contribute(s, 3, GRID, bundle(0, 0, 0, 0, 1));
    }

    private static int prestigeGained(List<DomainEvent> events, int seat) {
        return events.stream()
                .filter(e -> e instanceof DomainEvent.PrestigeGained g && g.seat() == seat)
                .mapToInt(e -> ((DomainEvent.PrestigeGained) e).amount())
                .sum();
    }

    @Test
    void successAtTheDeadlineGivesLargestThreeOthersOneNonQualifyingNothing() {
        GameState deadlineWindow = windowOf(accept(completeGrid(richWindowOf(3)), new ResolveRound(), ruleset).state(),
                6);
        GameResult.Accepted resolved = accept(deadlineWindow, new ResolveRound(), ruleset);

        assertEquals(ProjectStatus.SUCCEEDED, resolved.state().project(GRID).orElseThrow().status());
        assertTrue(resolved.events().contains(
                new DomainEvent.ProjectSucceeded(GRID, List.of(0, 1), bundle(0, 1, 0, 0, 0))));
        assertEquals(List.of(3, 1, 0, 0), resolved.state().players().stream().map(PlayerState::prestige).toList());
        assertEquals(3, prestigeGained(resolved.events(), 0));
        assertEquals(1, prestigeGained(resolved.events(), 1));
        assertEquals(0, prestigeGained(resolved.events(), 2));
        assertEquals(0, prestigeGained(resolved.events(), 3));
    }

    @Test
    void tiedLargestContributorsGetTwoEachAndExactlyTheMinimumQualifies() {
        GameState s = richWindowOf(6);
        s = contribute(s, 0, GRID, bundle(0, 3, 3, 0, 0));
        s = contribute(s, 1, GRID, bundle(0, 3, 3, 0, 0));
        s = contribute(s, 2, GRID, bundle(0, 0, 0, 0, 6));
        GameResult.Accepted resolved = accept(s, new ResolveRound(), ruleset);

        assertTrue(resolved.events().contains(
                new DomainEvent.ProjectSucceeded(GRID, List.of(0, 1, 2), bundle(0, 1, 0, 0, 0))));
        assertEquals(List.of(2, 2, 1, 0), resolved.state().players().stream().map(PlayerState::prestige).toList());
    }

    @Test
    void singleContributorIsTheLargest() {
        GameState s = withCity(richWindowOf(6), 0, 1, bundle(0, 6, 6, 0, 6));
        s = contribute(s, 0, GRID, bundle(0, 6, 6, 0, 6));
        GameResult.Accepted resolved = accept(s, new ResolveRound(), ruleset);
        assertEquals(List.of(3, 0, 0, 0), resolved.state().players().stream().map(PlayerState::prestige).toList());
    }

    @Test
    void incompleteProjectFailsAtTheDeadlineAndContributionsAreLost() {
        GameState s = richWindowOf(6);
        s = contribute(s, 0, GRID, bundle(0, 6, 6, 0, 0));
        s = contribute(s, 1, GRID, bundle(0, 0, 0, 0, 5));
        GameResult.Accepted resolved = accept(s, new ResolveRound(), ruleset);

        assertEquals(ProjectStatus.FAILED, resolved.state().project(GRID).orElseThrow().status());
        assertTrue(resolved.events().contains(new DomainEvent.ProjectFailed(GRID, bundle(0, 6, 6, 0, 5))));
        assertFalse(resolved.events().stream().anyMatch(e -> e instanceof DomainEvent.PrestigeGained));
        assertEquals(List.of(0, 0, 0, 0), resolved.state().players().stream().map(PlayerState::prestige).toList());
        assertEquals(bundle(9, 3, 3, 9, 9), resolved.state().player(0).holdings(), "nothing is given back");
        assertEquals(ResourceBundle.EMPTY, resolved.state().player(0).extraProduction(), "no reward");

        GameState round7 = accept(resolved.state(), new StartRound(), ruleset).state();
        assertRejected(withCity(round7, 0, 1, PLENTY), new ContributeToProject(0, GRID, bundle(0, 0, 0, 0, 1)),
                ruleset, RejectionCode.PROJECT_NOT_OPEN);
    }

    @Test
    void untouchedProjectFailsWithNothingLost() {
        GameResult.Accepted resolved = accept(richWindowOf(6), new ResolveRound(), ruleset);
        assertTrue(resolved.events().contains(new DomainEvent.ProjectFailed(GRID, ResourceBundle.EMPTY)));
    }

    @Test
    void productionRewardForQualifyingContributorsStartsNextRound() {
        GameState window = windowOf(accept(completeGrid(richWindowOf(3)), new ResolveRound(), ruleset).state(), 6);
        for (int seat = 0; seat < 4; seat++) {
            window = withCity(window, seat, 1, ResourceBundle.EMPTY);
        }
        GameState resolved = accept(window, new ResolveRound(), ruleset).state();
        assertEquals(bundle(0, 1, 0, 0, 0), resolved.player(0).extraProduction());
        assertEquals(bundle(0, 1, 0, 0, 0), resolved.player(1).extraProduction());
        assertEquals(ResourceBundle.EMPTY, resolved.player(2).extraProduction(), "not qualifying: no reward");
        assertEquals(ResourceBundle.EMPTY, resolved.player(0).holdings(), "no reward in the deadline round");

        GameState round7 = accept(resolved, new StartRound(), ruleset).state();
        for (int seat = 0; seat < 4; seat++) {
            PlayerState before = window.player(seat);
            ResourceBundle reward = seat <= 1 ? bundle(0, 1, 0, 0, 0) : ResourceBundle.EMPTY;
            assertEquals(Production.productionOf(before, 7, ruleset).plus(reward), round7.player(seat).holdings(),
                    "seat " + seat);
        }
    }
}
