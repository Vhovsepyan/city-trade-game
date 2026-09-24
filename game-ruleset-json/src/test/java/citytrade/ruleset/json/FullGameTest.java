package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.Resource;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.economy.Storage;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.ContractStatus;
import citytrade.engine.state.FinalScore;
import citytrade.engine.state.FormalContract;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.state.OfferCloseReason;
import citytrade.engine.state.OpportunityStatus;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.ProjectStatus;
import citytrade.engine.state.PublicProject;
import citytrade.engine.state.RegionalOpportunity;
import citytrade.engine.state.TradeOffer;
import citytrade.engine.state.TradeOfferStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** A scripted 14-round game with the real prototype-001 ruleset: every command is accepted and ends consistently. */
class FullGameTest {

    static final long SEED = 2026;

    private final Ruleset ruleset = RulesetLoader.load(TestRulesets.PROTOTYPE_001);
    private final ScriptedGame.Played played = ScriptedGame.play(SEED, ruleset);
    private final GameState end = played.finalState();

    @Test
    void gameEndsAfterTheLastRound() {
        assertEquals(GamePhase.FINISHED, end.phase());
        assertEquals(ruleset.roundCount(), end.round());
        assertTrue(end.finalResult().isPresent());
        assertEquals(List.of(new DomainEvent.GameFinished()),
                played.events().getLast().subList(played.events().getLast().size() - 1,
                        played.events().getLast().size()));
    }

    @Test
    void scriptUsesEveryPlayerCommandAndBothRoundCommands() {
        Set<Class<?>> used = played.commands().stream().map(Object::getClass).collect(Collectors.toSet());
        assertEquals(Set.of(GameCommand.class.getPermittedSubclasses()), used);
    }

    @Test
    void tradeOffersReachEveryClosedStatusAndNoneStaysOpen() {
        Set<TradeOfferStatus> statuses = end.tradeOffers().stream().map(TradeOffer::status)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TradeOfferStatus.class)));
        assertEquals(EnumSet.of(TradeOfferStatus.ACCEPTED, TradeOfferStatus.REJECTED, TradeOfferStatus.CANCELLED,
                TradeOfferStatus.EXPIRED), statuses);
        TradeOffer countered = end.tradeOffers().stream()
                .filter(o -> o.closeReason().equals(Optional.of(OfferCloseReason.COUNTEROFFER))).findFirst().orElseThrow();
        TradeOffer counteroffer = end.tradeOffers().stream()
                .filter(o -> o.parentOfferId().equals(Optional.of(countered.id()))).findFirst().orElseThrow();
        assertEquals(TradeOfferStatus.REJECTED, countered.status());
        assertEquals(TradeOfferStatus.ACCEPTED, counteroffer.status());
    }

    @Test
    void contractsEndFulfilledBrokenCancelledOrExpiredAndNothingIsOwedAfterTheLastRound() {
        assertEquals(List.of(ContractStatus.FULFILLED, ContractStatus.BROKEN, ContractStatus.CANCELLED,
                ContractStatus.EXPIRED, ContractStatus.BROKEN),
                end.contracts().stream().map(FormalContract::status).toList());
        assertTrue(end.contracts().stream().allMatch(c -> c.dueRound() <= ruleset.roundCount()));
        List<Boolean> breaks = allEvents().stream().filter(DomainEvent.ContractBroken.class::isInstance)
                .map(e -> ((DomainEvent.ContractBroken) e).voluntary()).toList();
        assertEquals(List.of(true, false), breaks, "one voluntary break, one automatic break when due");
        int brokenCounters = end.players().stream().mapToInt(PlayerState::contractsBroken).sum();
        assertEquals(2, brokenCounters);
    }

    @Test
    void projectsAndOpportunitiesAreAllDecided() {
        assertEquals(List.of(ProjectStatus.SUCCEEDED, ProjectStatus.SUCCEEDED),
                end.projects().stream().map(PublicProject::status).toList());
        List<OpportunityStatus> opportunities = end.opportunities().stream().map(RegionalOpportunity::status).toList();
        assertEquals(List.of(OpportunityStatus.WON, OpportunityStatus.WON, OpportunityStatus.REMOVED,
                OpportunityStatus.WON, OpportunityStatus.REMOVED), opportunities);
        assertTrue(allEvents().contains(new DomainEvent.OpportunityNotWon(end.opportunities().get(1).id(), true)),
                "the second card was tied first and won later");
    }

    @Test
    void holdingsAreNeverNegativeAndStorageHoldsAfterEveryRoundResolution() {
        for (int i = 0; i < played.commands().size(); i++) {
            GameState state = played.states().get(i);
            for (PlayerState player : state.players()) {
                assertTrue(!player.holdings().hasNegativeAmount(), "command " + i + ": " + player);
                if (played.commands().get(i) instanceof ResolveRound) {
                    int limit = Storage.limitOf(player, state.round(), ruleset);
                    for (Resource resource : Resource.values()) {
                        assertTrue(player.holdings().amountOf(resource) <= limit,
                                "round " + state.round() + " " + player.city() + " " + resource);
                    }
                }
            }
        }
    }

    @Test
    void finalScoreIsVisiblePlusHiddenAndWinnersHaveTheMostPrestige() {
        List<FinalScore> scores = end.finalResult().orElseThrow().scores();
        for (FinalScore score : scores) {
            PlayerState player = end.player(score.seat());
            assertEquals(player.prestige(), score.visiblePrestige());
            assertEquals(player.keptObjectives().stream().map(card -> card.id()).toList(), score.keptObjectives());
            assertEquals(score.completedObjectives().size() * ruleset.objectives().completedPrestige(),
                    score.hiddenPrestige());
            assertEquals(score.visiblePrestige() + score.hiddenPrestige(), score.finalPrestige());
            assertEquals(player.level(), score.level());
            assertEquals(player.contractsBroken(), score.contractsBroken());
        }
        int best = scores.stream().mapToInt(FinalScore::finalPrestige).max().orElseThrow();
        assertTrue(end.finalResult().orElseThrow().winnerSeats().stream()
                .allMatch(seat -> scores.get(seat).finalPrestige() == best));
    }

    /** Regression snapshot of this seed and script; a change here means the rules or the ruleset changed. */
    @Test
    void finalScoresOfThisSeed() {
        List<FinalScore> scores = end.finalResult().orElseThrow().scores();
        assertEquals(List.of(15, 15, 11, 7), scores.stream().map(FinalScore::finalPrestige).toList());
        assertEquals(List.of(2, 3, 2, 2), scores.stream().map(FinalScore::level).toList());
        // Seats 0 and 1 tie on Prestige; the first tiebreaker (city level) decides.
        assertEquals(List.of(1), end.finalResult().orElseThrow().winnerSeats());
    }

    private List<DomainEvent> allEvents() {
        return played.events().stream().flatMap(List::stream).toList();
    }
}
