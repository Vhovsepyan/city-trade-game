package citytrade.server.view;

import static org.assertj.core.api.Assertions.assertThat;

import citytrade.engine.GameEngine;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.ContributeToProject;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.ProposeContract;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SignContract;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.state.OpportunityStatus;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.ProjectStatus;
import citytrade.engine.state.PublicProject;
import citytrade.engine.state.RegionalOpportunity;
import citytrade.ruleset.json.RulesetLoader;
import citytrade.server.room.RoomStatus;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * T21 accept criteria: for real, multi-round states (trades, a formal contract, secret bids, a project
 * contribution, a final result), every seat's serialized JSON view must physically not contain another
 * seat's private data, and must contain the public data and its own private data correctly.
 */
class ViewProjectorTest {

    private static final long SEED = 2026L;
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final RoomViewContext ROOM = new RoomViewContext(
            RoomStatus.ACTIVE, Instant.parse("2026-01-01T00:00:00Z"), Set.of(0, 2), Set.of(3), 7L);

    @Test
    void ownDataIsPresentAndCorrectForEverySeat() {
        Scripted game = Scripted.play();
        for (int seat = 0; seat < 4; seat++) {
            PlayerGameView view = ViewProjector.project(game.round1State, ROOM, seat);
            PlayerState player = game.round1State.player(seat);
            assertThat(view.seat()).isEqualTo(seat);
            assertThat(view.own().holdings()).isEqualTo(player.holdings());
            assertThat(view.own().dealtObjectives()).isEqualTo(player.dealtObjectives());
            assertThat(view.own().keptObjectives()).isEqualTo(player.keptObjectives());
        }
    }

    @Test
    void otherPlayersHoldingsAreStructurallyAbsentFromEverySeatsView() {
        Scripted game = Scripted.play();
        for (GameState state : game.states()) {
            for (int seat = 0; seat < 4; seat++) {
                JsonNode root = toJson(state, seat);
                JsonNode players = root.get("players");
                assertThat(players).hasSize(4);
                for (JsonNode player : players) {
                    // The public per-seat view never carries a "holdings" or objective field, for any seat,
                    // including the viewer's own entry: that data lives only under "own".
                    assertThat(player.has("holdings")).isFalse();
                    assertThat(player.has("money")).isFalse();
                    assertThat(player.has("dealtObjectives")).isFalse();
                    assertThat(player.has("keptObjectives")).isFalse();
                }
            }
        }
    }

    @Test
    void otherPlayersObjectiveIdsNeverAppearInAnotherSeatsJsonBeforeTheGameEnds() {
        // Hidden objectives are revealed to everyone as part of the FinalResult (step 4.8 of the last round,
        // Numbers Sheet 15/18); this test only covers rounds before that reveal.
        Scripted game = Scripted.play();
        for (GameState state : List.of(game.round1State, game.round3State)) {
            for (int seat = 0; seat < 4; seat++) {
                String json = toJsonString(state, seat);
                for (int other = 0; other < 4; other++) {
                    if (other == seat) {
                        continue;
                    }
                    for (ObjectiveCard card : state.player(other).dealtObjectives()) {
                        assertThat(json).doesNotContain(card.id());
                    }
                }
            }
        }
    }

    @Test
    void finalResultRevealsEveryPlayersKeptObjectivesToEverySeat() {
        // Numbers Sheet 15/18: hidden objectives score, and are revealed, at the end of the game.
        Scripted game = Scripted.play();
        for (int seat = 0; seat < 4; seat++) {
            String json = toJsonString(game.finalState, seat);
            for (int other = 0; other < 4; other++) {
                for (ObjectiveCard card : game.finalState.player(other).keptObjectives()) {
                    assertThat(json).contains(card.id());
                }
            }
        }
    }

    @Test
    void tradeOfferBetweenTwoSeatsIsVisibleOnlyToThoseTwoSeats() {
        Scripted game = Scripted.play();
        for (int seat = 0; seat < 4; seat++) {
            PlayerGameView view = ViewProjector.project(game.round1State, ROOM, seat);
            boolean hasIt = view.tradeOffers().stream().anyMatch(offer -> offer.id() == game.tradeOfferId);
            if (seat == 1 || seat == 2) {
                assertThat(hasIt).as("seat " + seat + " is a party to the offer").isTrue();
            } else {
                assertThat(hasIt).as("seat " + seat + " is not a party to the offer").isFalse();
            }
        }
    }

    @Test
    void ownBidIsVisibleOnlyToTheBiddingSeatAndOtherBidsAreStructurallyAbsent() {
        Scripted game = Scripted.play();
        for (int seat = 0; seat < 4; seat++) {
            PlayerGameView view = ViewProjector.project(game.round3State, ROOM, seat);
            OpportunityView opportunity = view.opportunities().stream()
                    .filter(o -> o.card().id().equals(game.opportunityId))
                    .findFirst()
                    .orElseThrow();
            int expectedOwnBid = switch (seat) {
                case 0 -> 1;
                case 1 -> 2;
                default -> 0;
            };
            assertThat(opportunity.ownBid()).isEqualTo(expectedOwnBid);

            JsonNode root = toJson(game.round3State, seat);
            JsonNode opportunityNode = findOpportunity(root, game.opportunityId);
            assertThat(opportunityNode.has("bids")).isFalse();
            assertThat(opportunityNode.has("bidsBySeat")).isFalse();
        }
    }

    @Test
    void formalContractsArePublicToEverySeat() {
        Scripted game = Scripted.play();
        for (int seat = 0; seat < 4; seat++) {
            PlayerGameView view = ViewProjector.project(game.round1State, ROOM, seat);
            assertThat(view.contracts().stream().anyMatch(c -> c.id() == game.contractId)).isTrue();
        }
    }

    @Test
    void publicProjectContributionsArePublicToEverySeat() {
        Scripted game = Scripted.play();
        for (int seat = 0; seat < 4; seat++) {
            PlayerGameView view = ViewProjector.project(game.round3State, ROOM, seat);
            PublicProject project = view.projects().stream()
                    .filter(p -> p.id().equals(game.projectId))
                    .findFirst()
                    .orElseThrow();
            assertThat(project.contributedBy(2).isEmpty()).isFalse();
        }
    }

    @Test
    void upcomingProjectsAreNotAnnouncedBeforeTheirWindowOpens() {
        Scripted game = Scripted.play();
        // round1State is before round 3, when the first project window opens.
        for (int seat = 0; seat < 4; seat++) {
            PlayerGameView view = ViewProjector.project(game.round1State, ROOM, seat);
            assertThat(view.projects()).allMatch(p -> p.status() != ProjectStatus.UPCOMING);
        }
    }

    @Test
    void finalResultIsAbsentUntilTheGameEndsAndPublicAfterward() {
        Scripted game = Scripted.play();
        for (int seat = 0; seat < 4; seat++) {
            assertThat(ViewProjector.project(game.round1State, ROOM, seat).finalResult()).isNull();
            assertThat(ViewProjector.project(game.round3State, ROOM, seat).finalResult()).isNull();
            PlayerGameView finalView = ViewProjector.project(game.finalState, ROOM, seat);
            assertThat(finalView.finalResult()).isNotNull();
            assertThat(finalView.phase()).isEqualTo(GamePhase.FINISHED);
        }
    }

    @Test
    void roomMetadataIsCarriedThroughWithoutTouchingGameState() {
        Scripted game = Scripted.play();
        PlayerGameView view = ViewProjector.project(game.round1State, ROOM, 0);
        assertThat(view.roomStatus()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(view.phaseEndsAt()).isEqualTo(ROOM.phaseEndsAt());
        assertThat(view.players().get(0).ready()).isTrue();
        assertThat(view.players().get(1).ready()).isFalse();
        assertThat(view.players().get(3).disconnected()).isTrue();
    }

    @Test
    void sameInputsAlwaysProduceAnEqualView() {
        Scripted game = Scripted.play();
        PlayerGameView first = ViewProjector.project(game.round3State, ROOM, 2);
        PlayerGameView second = ViewProjector.project(game.round3State, ROOM, 2);
        assertThat(first).isEqualTo(second);
    }

    private static JsonNode toJson(GameState state, int seat) {
        return JSON.readTree(toJsonString(state, seat));
    }

    private static String toJsonString(GameState state, int seat) {
        return JSON.writeValueAsString(ViewProjector.project(state, ROOM, seat));
    }

    private static JsonNode findOpportunity(JsonNode root, String opportunityId) {
        for (JsonNode node : root.get("opportunities")) {
            if (node.get("card").get("id").asString().equals(opportunityId)) {
                return node;
            }
        }
        throw new AssertionError("opportunity " + opportunityId + " not found in view JSON");
    }

    /** A small, deterministic, hand-scripted game covering trades, a contract, bids and a project contribution. */
    private static final class Scripted {
        private final GameState round1State;
        private final GameState round3State;
        private final GameState finalState;
        private final int tradeOfferId;
        private final int contractId;
        private final String opportunityId;
        private final String projectId;

        private Scripted(GameState round1State, GameState round3State, GameState finalState, int tradeOfferId,
                int contractId, String opportunityId, String projectId) {
            this.round1State = round1State;
            this.round3State = round3State;
            this.finalState = finalState;
            this.tradeOfferId = tradeOfferId;
            this.contractId = contractId;
            this.opportunityId = opportunityId;
            this.projectId = projectId;
        }

        List<GameState> states() {
            return List.of(round1State, round3State, finalState);
        }

        static Scripted play() {
            Ruleset ruleset = ruleset();
            GameState state = GameSetup.create(SEED, ruleset);
            for (int seat = 0; seat < 4; seat++) {
                state = apply(state, new ChooseObjectives(seat, keptObjectiveIds(state, ruleset, seat)), ruleset);
            }
            state = apply(state, new StartRound(), ruleset);

            ResourceBundle oneMoney = ResourceBundle.EMPTY.withMoney(1);
            state = apply(state, new ProposeTrade(1, 2, oneMoney, oneMoney), ruleset);
            int tradeOfferId = state.tradeOffers().getLast().id();

            state = apply(state, new ProposeContract(0, 0, 3, oneMoney, oneMoney, 4), ruleset);
            int contractId = state.contracts().getLast().id();
            state = apply(state, new SignContract(3, contractId), ruleset);

            GameState round1State = state;

            state = apply(state, new ResolveRound(), ruleset);
            state = apply(state, new StartRound(), ruleset);
            state = apply(state, new ResolveRound(), ruleset);
            state = apply(state, new StartRound(), ruleset); // round 3: opportunities and projects open

            RegionalOpportunity opportunity = state.opportunities().stream()
                    .filter(o -> o.status() == OpportunityStatus.OPEN)
                    .findFirst()
                    .orElseThrow();
            state = apply(state, new PlaceBid(0, opportunity.id(), 1), ruleset);
            state = apply(state, new PlaceBid(1, opportunity.id(), 2), ruleset);

            PublicProject project = state.projects().stream()
                    .filter(p -> p.status() == ProjectStatus.OPEN)
                    .findFirst()
                    .orElseThrow();
            ResourceBundle contribution = pickContribution(project, state.player(2));
            state = apply(state, new ContributeToProject(2, project.id(), contribution), ruleset);

            GameState round3State = state;

            state = apply(state, new ResolveRound(), ruleset);
            for (int round = 4; round <= ruleset.roundCount(); round++) {
                state = apply(state, new StartRound(), ruleset);
                state = apply(state, new ResolveRound(), ruleset);
            }
            GameState finalState = state;

            return new Scripted(round1State, round3State, finalState, tradeOfferId, contractId, opportunity.id(),
                    project.id());
        }

        private static ResourceBundle pickContribution(PublicProject project, PlayerState contributor) {
            ResourceBundle need = project.remainingNeed();
            if (need.money() > 0 && contributor.holdings().money() >= 1) {
                return ResourceBundle.EMPTY.withMoney(1);
            }
            for (Resource resource : Resource.values()) {
                if (need.amountOf(resource) > 0 && contributor.holdings().amountOf(resource) >= 1) {
                    return ResourceBundle.EMPTY.with(resource, 1);
                }
            }
            throw new IllegalStateException("no affordable contribution found for project " + project.id());
        }

        private static GameState apply(GameState state, GameCommand command, Ruleset ruleset) {
            GameResult result = GameEngine.apply(state, command, ruleset);
            if (!(result instanceof GameResult.Accepted accepted)) {
                throw new AssertionError("expected Accepted but was " + result + " for command " + command);
            }
            return accepted.state();
        }

        private static List<String> keptObjectiveIds(GameState state, Ruleset ruleset, int seat) {
            return state.player(seat).dealtObjectives().stream()
                    .limit(ruleset.objectives().keptPerPlayer())
                    .map(ObjectiveCard::id)
                    .toList();
        }

        private static Ruleset ruleset() {
            return RulesetLoader.load(Path.of(System.getProperty("rulesets.dir", "rulesets"), "prototype-002.json"));
        }
    }
}
