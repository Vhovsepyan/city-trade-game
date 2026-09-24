package citytrade.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.bots.BotGame;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.FinalScore;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The collector's record of a real bot game agrees with the game's own events and states. */
class GameMetricsCollectorTest {

    private static final long SEED = 11;

    private final Ruleset ruleset = TestRulesets.prototype();

    /** Every event of the game with the round it happened in, and the state after every ResolveRound. */
    private record Log(List<DomainEvent> events, List<Integer> eventRounds, List<GameState> roundEndStates) {
    }

    @ParameterizedTest
    @EnumSource(BotProfile.class)
    void recordAgreesWithTheGame(BotProfile profile) {
        List<BotProfile> bots = Collections.nCopies(ruleset.playerCount(), profile);
        GameMetricsCollector collector = new GameMetricsCollector(bots);
        Log log = new Log(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        BotGame.Result result = BotGame.play(SEED, ruleset, bots.stream().map(BotProfile::newBot).toList(),
                (command, stateAfter, events) -> {
                    collector.accepted(command, stateAfter, events);
                    events.forEach(event -> {
                        log.events().add(event);
                        log.eventRounds().add(stateAfter.round());
                    });
                    if (command instanceof ResolveRound) {
                        log.roundEndStates().add(stateAfter);
                    }
                });

        GameRecord record = collector.finish(SEED, result);

        GameState end = result.finalState();
        assertEquals(SEED, record.seed());
        assertEquals(end.finalResult().orElseThrow().winnerSeats(), record.winnerSeats());
        assertEquals(result.rejections().size(), record.rejections());
        for (PlayerState player : end.players()) {
            GameRecord.Seat seat = record.seats().get(player.seat());
            FinalScore score = end.finalResult().orElseThrow().scoreOf(player.seat());
            assertEquals(player.city(), seat.city());
            assertEquals(profile, seat.bot());
            assertEquals(score.finalPrestige(), seat.finalPrestige());
            assertEquals(score.visiblePrestige(), seat.visiblePrestige());
            assertEquals(score.hiddenPrestige(), seat.hiddenPrestige());
            assertEquals(score.completedObjectives().size(), seat.completedObjectives());
            assertEquals(player.level(), seat.finalLevel());
            assertEquals(player.contractsBroken(), seat.contractsBroken());
            assertEquals(levelRounds(log, player.seat()), seat.levelReachedRound());
            assertEquals(produced(log, player.seat()), seat.produced());
            assertEquals(marketBoughtUnits(log, player.seat()), seat.marketBought().resourceUnits());
            assertEquals(player.objectiveProgress().everStrained(), seat.strainedRounds() > 0);
        }
        // Every trade has a giver and a receiver: all given = all received.
        ResourceBundle given = record.seats().stream().map(GameRecord.Seat::tradedGiven)
                .reduce(ResourceBundle.EMPTY, ResourceBundle::plus);
        ResourceBundle received = record.seats().stream().map(GameRecord.Seat::tradedReceived)
                .reduce(ResourceBundle.EMPTY, ResourceBundle::plus);
        assertEquals(given, received);
        long trades = log.events().stream().filter(DomainEvent.TradeExecuted.class::isInstance).count();
        assertEquals(2 * trades, record.seats().stream().mapToInt(GameRecord.Seat::tradesExecuted).sum());
        assertEquals(trades, record.rounds().stream().mapToInt(GameRecord.Round::tradesExecuted).sum());
        if (profile == BotProfile.TRADER) {
            assertTrue(trades > 0, "traders trade");
        }

        assertEquals(ruleset.roundCount(), record.rounds().size());
        for (int i = 0; i < record.rounds().size(); i++) {
            GameRecord.Round round = record.rounds().get(i);
            GameState state = log.roundEndStates().get(i);
            assertEquals(i + 1, round.round());
            assertEquals(state.players().stream().map(PlayerState::holdings)
                    .reduce(ResourceBundle.EMPTY, ResourceBundle::plus), round.heldAtEnd());
            for (Resource resource : Resource.values()) {
                assertEquals(state.market().stepIndexOf(resource), round.marketStepAtEnd().get(resource));
            }
            assertEquals(0, round.demand().money(), "demand counts resources only");
        }
        assertEquals(count(log, DomainEvent.ProjectSucceeded.class), record.projectsSucceeded());
        assertEquals(count(log, DomainEvent.ProjectFailed.class), record.projectsFailed());
        assertEquals(count(log, DomainEvent.ContractSigned.class), record.contractsSigned());
    }

    private static TreeMap<Integer, Integer> levelRounds(Log log, int seat) {
        TreeMap<Integer, Integer> rounds = new TreeMap<>();
        for (int i = 0; i < log.events().size(); i++) {
            if (log.events().get(i) instanceof DomainEvent.CityUpgraded upgraded && upgraded.seat() == seat) {
                rounds.put(upgraded.newLevel(), log.eventRounds().get(i));
            }
        }
        return rounds;
    }

    private static ResourceBundle produced(Log log, int seat) {
        return log.events().stream()
                .filter(event -> event instanceof DomainEvent.ResourcesProduced produced && produced.seat() == seat)
                .map(event -> ((DomainEvent.ResourcesProduced) event).produced())
                .reduce(ResourceBundle.EMPTY, ResourceBundle::plus);
    }

    private static int marketBoughtUnits(Log log, int seat) {
        return log.events().stream()
                .filter(event -> event instanceof DomainEvent.MarketBought bought && bought.seat() == seat)
                .mapToInt(event -> ((DomainEvent.MarketBought) event).quantity())
                .sum();
    }

    private static int count(Log log, Class<? extends DomainEvent> type) {
        return (int) log.events().stream().filter(type::isInstance).count();
    }
}
