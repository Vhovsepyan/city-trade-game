package citytrade.server.view;

import static org.assertj.core.api.Assertions.assertThat;

import citytrade.engine.GameEngine;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.StartRound;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.FinalResult;
import citytrade.engine.state.GameState;
import citytrade.ruleset.json.RulesetLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * T21 accept criterion: a private domain event never reaches an uninvolved seat. Covers every
 * {@link DomainEvent} kind, both the private ones (owner-only, or trade-offer parties looked up from state)
 * and the public ones (broadcast to every seat), so a future new event kind fails to compile here first
 * (see {@code NoticeProjector}'s exhaustive switch) rather than silently leaking or under-notifying.
 */
class NoticeProjectorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final long SEED = 99L;

    @Test
    void privateEventsReachOnlyTheSeatsInvolved() {
        GameState state = scriptedStateWithOneOpenOffer();
        int offerId = state.tradeOffers().getFirst().id(); // proposer=1, recipient=2

        assertRecipients(state, new DomainEvent.ObjectivesChosen(2), Set.of(2));
        assertRecipients(state, new DomainEvent.UpkeepPrioritySet(1), Set.of(1));
        assertRecipients(state, new DomainEvent.ResourcesProduced(0, oneFood()), Set.of(0));
        assertRecipients(state, new DomainEvent.UpkeepPaid(0, oneFood(), 0), Set.of(0));
        assertRecipients(state, new DomainEvent.MarketBought(3, Resource.FOOD, 1, 2), Set.of(3));
        assertRecipients(state, new DomainEvent.MarketSold(3, Resource.FOOD, 1, 2), Set.of(3));
        assertRecipients(state, new DomainEvent.CityStrained(2), Set.of(2));
        assertRecipients(state, new DomainEvent.ExcessDiscarded(2, oneFood()), Set.of(2));
        assertRecipients(state, new DomainEvent.CrisisPolicySet(0), Set.of(0));
        assertRecipients(state, new DomainEvent.CrisisPaid(0, "EVENT", oneFood()), Set.of(0));
        assertRecipients(state, new DomainEvent.CrisisNotPaid(0, "EVENT", true), Set.of(0));
        assertRecipients(state, new DomainEvent.EventOptionUsed(1, "EVENT", oneFood(), Optional.empty()), Set.of(1));
        assertRecipients(state, new DomainEvent.BidPlaced(3, "OPP"), Set.of(3));

        assertRecipients(state,
                new DomainEvent.TradeProposed(offerId, Optional.empty(), 1, 2, oneFood(), oneFood()), Set.of(1, 2));
        assertRecipients(state, new DomainEvent.TradeExecuted(offerId, 1, 2, oneFood(), oneFood()), Set.of(1, 2));
        assertRecipients(state, new DomainEvent.TradeRejected(offerId, Optional.empty()), Set.of(1, 2));
        assertRecipients(state, new DomainEvent.TradeCancelled(offerId), Set.of(1, 2));
        assertRecipients(state, new DomainEvent.TradeExpired(offerId), Set.of(1, 2));
        assertRecipients(state, new DomainEvent.TradeInvalidated(offerId, 1), Set.of(1, 2));
    }

    @Test
    void aTradeEventForAnUninvolvedSeatIsNeverReceivedByThatSeat() {
        GameState state = scriptedStateWithOneOpenOffer();
        int offerId = state.tradeOffers().getFirst().id();
        List<Notice> notices = NoticeProjector.project(state,
                List.of(new DomainEvent.TradeProposed(offerId, Optional.empty(), 1, 2, oneFood(), oneFood())));
        Set<Integer> recipients = notices.stream().map(Notice::seat).collect(Collectors.toSet());
        assertThat(recipients).doesNotContain(0, 3);
    }

    @Test
    void aTradeEventForAnOfferThatNoLongerExistsReachesNobody() {
        GameState state = scriptedStateWithOneOpenOffer();
        List<Notice> notices = NoticeProjector.project(state, List.of(new DomainEvent.TradeCancelled(99999)));
        assertThat(notices).isEmpty();
    }

    @Test
    void publicEventsReachEveryPlayer() {
        GameState state = scriptedStateWithOneOpenOffer();
        Set<Integer> all = Set.of(0, 1, 2, 3);

        assertRecipients(state, new DomainEvent.MarketPriceMoved(Resource.FOOD, 0, 1), all);
        assertRecipients(state, new DomainEvent.CityUpgraded(0, 2, oneFood()), all);
        assertRecipients(state, new DomainEvent.BuildingBuilt(0, "WAREHOUSE", oneFood()), all);
        assertRecipients(state, new DomainEvent.PrestigeGained(0, 1), all);
        assertRecipients(state, new DomainEvent.ContractProposed(1, 0, 0, 1, oneFood(), oneFood(), 3), all);
        assertRecipients(state, new DomainEvent.ContractSigned(1, 0, 1, oneFood()), all);
        assertRecipients(state, new DomainEvent.ContractInvalidated(1, 0), all);
        assertRecipients(state, new DomainEvent.ContractExpired(1), all);
        assertRecipients(state, new DomainEvent.ContractFulfilled(1, 1, 0, oneFood()), all);
        assertRecipients(state, new DomainEvent.ContractBroken(1, 1, 0, true, oneFood(), 1, 1, 0), all);
        assertRecipients(state, new DomainEvent.ContractCancelRequested(1, 0), all);
        assertRecipients(state, new DomainEvent.ContractCancelled(1), all);
        assertRecipients(state, new DomainEvent.EventActivated(1, "EVENT"), all);
        assertRecipients(state, new DomainEvent.EventWarned(2, "EVENT"), all);
        assertRecipients(state, new DomainEvent.EventEnded("EVENT"), all);
        assertRecipients(state, new DomainEvent.ProjectOpened("PROJECT", 6), all);
        assertRecipients(state, new DomainEvent.ProjectContributed(0, "PROJECT", oneFood(), 2), all);
        assertRecipients(state, new DomainEvent.ProjectSucceeded("PROJECT", List.of(0, 1), oneFood()), all);
        assertRecipients(state, new DomainEvent.ProjectFailed("PROJECT", oneFood()), all);
        assertRecipients(state, new DomainEvent.OpportunityRevealed("OPP"), all);
        assertRecipients(state, new DomainEvent.OpportunityWon("OPP", 0, 3, oneFood()), all);
        assertRecipients(state, new DomainEvent.OpportunityNotWon("OPP", true), all);
        assertRecipients(state, new DomainEvent.OpportunityRemoved("OPP"), all);
        assertRecipients(state, new DomainEvent.RoundStarted(2), all);
        assertRecipients(state, new DomainEvent.RoundResolved(1), all);
        assertRecipients(state, new DomainEvent.ObjectivesRevealed(0, List.of("A"), List.of("A"), 2), all);
        assertRecipients(state, new DomainEvent.FinalScoresRevealed(new FinalResult(List.of(), List.of())), all);
        assertRecipients(state, new DomainEvent.GameFinished(), all);
    }

    @Test
    void theProjectorNeverMutatesItsInputs() {
        GameState state = scriptedStateWithOneOpenOffer();
        GameState before = state;
        NoticeProjector.project(state, List.of(new DomainEvent.RoundStarted(2)));
        assertThat(state).isEqualTo(before);
    }

    @Test
    void opportunityWonNoticeNeverCarriesThePricePaidToAnySeatIncludingTheWinner() {
        // T21 P0 fix: the winning bid is a secret (Concept section 37, OpportunityView has no such field
        // either), so the raw DomainEvent.pricePaid must never reach the wire, not even for the winner.
        GameState state = scriptedStateWithOneOpenOffer();
        int secretPricePaid = 4177;
        List<Notice> notices = NoticeProjector.project(state,
                List.of(new DomainEvent.OpportunityWon("OPP", 0, secretPricePaid, oneFood())));
        assertThat(notices).hasSize(4);
        for (Notice notice : notices) {
            assertThat(notice.event()).isInstanceOf(NoticeEvent.OpportunityWon.class);
            String json = JSON.writeValueAsString(notice);
            assertThat(json).as("seat " + notice.seat())
                    .doesNotContain("pricePaid")
                    .doesNotContain(String.valueOf(secretPricePaid))
                    .contains("OPP")
                    .contains("winnerSeat");
        }
    }

    private static void assertRecipients(GameState state, DomainEvent event, Set<Integer> expected) {
        List<Notice> notices = NoticeProjector.project(state, List.of(event));
        Set<Integer> actual = notices.stream().map(Notice::seat).collect(Collectors.toSet());
        assertThat(actual).as(event.toString()).isEqualTo(expected);
    }

    private static ResourceBundle oneFood() {
        return ResourceBundle.EMPTY.with(Resource.FOOD, 1);
    }

    private static GameState scriptedStateWithOneOpenOffer() {
        Ruleset ruleset = ruleset();
        GameState state = GameSetup.create(SEED, ruleset);
        for (int seat = 0; seat < 4; seat++) {
            state = apply(state, new ChooseObjectives(seat, keptObjectiveIds(state, ruleset, seat)), ruleset);
        }
        state = apply(state, new StartRound(), ruleset);
        state = apply(state, new ProposeTrade(1, 2, oneFood(), oneFood()), ruleset);
        return state;
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
