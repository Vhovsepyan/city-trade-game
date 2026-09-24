package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.round.RoundStep;
import citytrade.engine.ruleset.Ruleset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The Numbers Sheet "ROUND ORDER" seen from the outside: in the scripted full game, the events of every
 * {@link StartRound} and {@link ResolveRound} appear in step order. {@code RoundFlowTest} checks the order of the
 * step calls; this test checks that each rule really runs in its own step.
 */
class RoundOrderTest {

    private final Ruleset ruleset = RulesetLoader.load(TestRulesets.PROTOTYPE_001);
    private final ScriptedGame.Played played = ScriptedGame.play(FullGameTest.SEED, ruleset);

    @Test
    void eventsOfEveryRoundCommandFollowTheRoundOrder() {
        for (int i = 0; i < played.commands().size(); i++) {
            GameCommand command = played.commands().get(i);
            if (!(command instanceof StartRound || command instanceof ResolveRound)) {
                continue;
            }
            List<RoundStep> steps = new ArrayList<>();
            played.events().get(i).forEach(event -> stepOf(event).ifPresent(steps::add));
            List<RoundStep> sorted = steps.stream().sorted().toList();
            assertEquals(sorted, steps, "command " + i + " " + command + ": " + played.events().get(i));
        }
    }

    @Test
    void roundStartedAndRoundResolvedComeAfterAllSteps() {
        for (int i = 0; i < played.commands().size(); i++) {
            List<DomainEvent> events = played.events().get(i);
            if (played.commands().get(i) instanceof StartRound) {
                assertInstanceOf(DomainEvent.RoundStarted.class, events.getLast());
            }
            if (played.commands().get(i) instanceof ResolveRound) {
                boolean last = i == played.commands().size() - 1;
                DomainEvent resolved = last ? events.get(events.size() - 2) : events.getLast();
                assertInstanceOf(DomainEvent.RoundResolved.class, resolved);
            }
        }
    }

    /** Guards against a vacuous pass: the script really produces events in these steps. */
    @Test
    void scriptProducesEventsInMostSteps() {
        Set<RoundStep> seen = EnumSet.noneOf(RoundStep.class);
        played.events().forEach(events -> events.forEach(event -> stepOf(event).ifPresent(seen::add)));
        // 1.2 and 1.3 change state without an event of their own.
        Set<RoundStep> expected = EnumSet.allOf(RoundStep.class);
        expected.removeAll(Set.of(RoundStep.STRAINED_PENALTY, RoundStep.PRODUCTION));
        assertEquals(expected, seen);
    }

    /** The one step that emits each event; empty for events that several steps or player commands emit. */
    private static Optional<RoundStep> stepOf(DomainEvent event) {
        RoundStep step = switch (event) {
            case DomainEvent.EventActivated e -> RoundStep.EVENT_BECOMES_ACTIVE;
            case DomainEvent.UpkeepPaid e -> RoundStep.UPKEEP;
            case DomainEvent.ContractFulfilled e -> RoundStep.CONTRACT_OBLIGATIONS;
            case DomainEvent.ContractBroken e when !e.voluntary() -> RoundStep.CONTRACT_OBLIGATIONS;
            case DomainEvent.CrisisPaid e -> RoundStep.CRISIS_PAYMENTS;
            case DomainEvent.CrisisNotPaid e -> RoundStep.CRISIS_PAYMENTS;
            case DomainEvent.EventWarned e -> RoundStep.NEXT_EVENT_WARNING;
            case DomainEvent.ProjectOpened e -> RoundStep.NEW_PROJECT_OR_OPPORTUNITY;
            case DomainEvent.OpportunityRevealed e -> RoundStep.NEW_PROJECT_OR_OPPORTUNITY;
            case DomainEvent.TradeExpired e -> RoundStep.TRADE_OFFERS_EXPIRE;
            case DomainEvent.ContractExpired e -> RoundStep.TRADE_OFFERS_EXPIRE;
            case DomainEvent.OpportunityWon e -> RoundStep.BIDS_RESOLVE;
            case DomainEvent.OpportunityNotWon e -> RoundStep.BIDS_RESOLVE;
            case DomainEvent.OpportunityRemoved e -> RoundStep.BIDS_RESOLVE;
            case DomainEvent.ProjectSucceeded e -> RoundStep.PROJECT_DEADLINE;
            case DomainEvent.ProjectFailed e -> RoundStep.PROJECT_DEADLINE;
            case DomainEvent.PrestigeGained e -> RoundStep.PRESTIGE;
            case DomainEvent.ExcessDiscarded e -> RoundStep.STORAGE_LIMITS;
            case DomainEvent.MarketPriceMoved e -> RoundStep.MARKET_PRICES_MOVE;
            case DomainEvent.EventEnded e -> RoundStep.TEMPORARY_EVENT_EFFECTS_END;
            case DomainEvent.ObjectivesRevealed e -> RoundStep.FINAL_SCORING;
            case DomainEvent.FinalScoresRevealed e -> RoundStep.FINAL_SCORING;
            default -> null;
        };
        return Optional.ofNullable(step);
    }
}
