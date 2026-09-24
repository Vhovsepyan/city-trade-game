package citytrade.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.Ruleset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Aggregation on two hand-made game records, so every expected number can be checked by hand. */
class SimulationReportTest {

    private static final List<BotProfile> BOTS =
            List.of(BotProfile.TRADER, BotProfile.BASELINE, BotProfile.TRADER, BotProfile.BASELINE);

    private final Ruleset ruleset = TestRulesets.prototype();

    /** Game 1: Agricultural wins alone. Game 2: Technology and Energy share the victory. */
    private final List<GameRecord> games = List.of(
            new GameRecord(1, List.of(
                    seat(0, CityType.AGRICULTURAL, 18, Map.of(2, 1, 3, 4), 0, 1),
                    seat(1, CityType.INDUSTRIAL, 15, Map.of(2, 2), 2, 1),
                    seat(2, CityType.ENERGY, 12, Map.of(2, 2), 0, 0),
                    seat(3, CityType.TECHNOLOGY, 10, Map.of(2, 2), 0, 0)),
                    rounds(1), List.of(0), 2, 1, 0, 1, 0, 1, 1, 0),
            new GameRecord(2, List.of(
                    seat(0, CityType.TECHNOLOGY, 17, Map.of(2, 3), 1, 0),
                    seat(1, CityType.ENERGY, 17, Map.of(2, 3), 0, 0),
                    seat(2, CityType.AGRICULTURAL, 14, Map.of(2, 5), 0, 0),
                    seat(3, CityType.INDUSTRIAL, 9, Map.of(), 3, 0)),
                    rounds(2), List.of(0, 1), 0, 0, 0, 0, 0, 0, 0, 3));

    private static GameRecord.Seat seat(int seat, CityType city, int finalPrestige, Map<Integer, Integer> levels,
            int strainedRounds, int trades) {
        ResourceBundle produced = new ResourceBundle(10, 20, 30, 40, 50);
        return new GameRecord.Seat(seat, city, BOTS.get(seat), finalPrestige - 2, 2, finalPrestige, 1,
                1 + levels.size(), new TreeMap<>(levels), strainedRounds, 0, produced,
                new ResourceBundle(1, 0, 0, 0, 0), trades, new ResourceBundle(2, 0, 0, 0, 1),
                new ResourceBundle(0, 2, 0, 0, 1), new ResourceBundle(0, 0, 3, 0, 0), 6,
                ResourceBundle.EMPTY, 0);
    }

    /** 14 rounds; round r needs r Technology; all cities hold 8 Technology; every market step is {@code step}. */
    private static List<GameRecord.Round> rounds(int step) {
        List<GameRecord.Round> rounds = new ArrayList<>();
        Map<Resource, Integer> steps = new EnumMap<>(Resource.class);
        for (Resource resource : Resource.values()) {
            steps.put(resource, step);
        }
        for (int round = 1; round <= 14; round++) {
            rounds.add(new GameRecord.Round(round, round == 3 ? 1 : 0,
                    ResourceBundle.EMPTY.with(Resource.TECHNOLOGY, round), new ResourceBundle(0, 0, 0, 8, 40), steps,
                    round == 5 ? 2 : 0));
        }
        return rounds;
    }

    private SimulationReport report() {
        return SimulationReport.of(ruleset, 1, BOTS, games);
    }

    @Test
    void winRateByCityCountsASharedVictoryAsAPartOfAWin() {
        SimulationReport report = report();

        assertEquals(0.5, report.cities().get(CityType.AGRICULTURAL).winRate());
        assertEquals(0.25, report.cities().get(CityType.TECHNOLOGY).winRate());
        assertEquals(0.25, report.cities().get(CityType.ENERGY).winRate());
        assertEquals(0.0, report.cities().get(CityType.INDUSTRIAL).winRate());
        assertEquals(0.5, report.sharedVictoryRate());
        assertEquals(1.5 / 4, report.bots().get("trader").winRate());
        assertEquals(0.5 / 4, report.bots().get("baseline").winRate());
        assertEquals(4, report.bots().get("trader").seatsPlayed());
        assertEquals(15.25, report.bots().get("trader").avgFinalPrestige());
    }

    @Test
    void prestigeDistributionCoversEveryCityAndTheWinner() {
        SimulationReport report = report();

        assertEquals(8, report.finalPrestige().count());
        assertEquals(new TreeMap<>(Map.of(9, 1, 10, 1, 12, 1, 14, 1, 15, 1, 17, 2, 18, 1)),
                report.finalPrestige().histogram());
        assertEquals(List.of(17, 18), List.of(report.winningPrestige().min(), report.winningPrestige().max()));
        SimulationReport.CityReport agricultural = report.cities().get(CityType.AGRICULTURAL);
        assertEquals(16.0, agricultural.finalPrestige().mean());
        assertEquals(14.0, agricultural.avgVisiblePrestige());
        assertEquals(2.0, agricultural.avgHiddenPrestige());
    }

    @Test
    void levelReachGivesRateAndAverageRound() {
        SimulationReport report = report();

        SimulationReport.CityReport agricultural = report.cities().get(CityType.AGRICULTURAL);
        assertEquals(new SimulationReport.LevelReach(1.0, 3.0), agricultural.levelReach().get(2));
        assertEquals(new SimulationReport.LevelReach(0.5, 4.0), agricultural.levelReach().get(3));
        SimulationReport.CityReport industrial = report.cities().get(CityType.INDUSTRIAL);
        assertEquals(new SimulationReport.LevelReach(0.5, 2.0), industrial.levelReach().get(2));
        assertEquals(0.0, industrial.levelReach().get(3).reachedRate());
        assertNull(industrial.levelReach().get(3).averageRound());
        assertEquals(List.of(2, 3), List.copyOf(industrial.levelReach().keySet()));
        assertEquals(2.5, industrial.avgStrainedRounds());
    }

    @Test
    void economyContractsAndProjectsAreAveragedPerGame() {
        SimulationReport report = report();

        assertEquals(40.0, report.economy().producedPerGame().get(Resource.FOOD));
        assertEquals(200.0, report.economy().moneyProducedPerGame());
        // Two seats of game 1 were in the same trade.
        assertEquals(0.5, report.economy().tradesPerGame());
        assertEquals(8.0, report.economy().resourcesTradedPerGame());
        assertEquals(4.0, report.economy().discardedPerGame().get(Resource.FOOD));
        assertEquals(12.0, report.economy().marketBoughtPerGame().get(Resource.MATERIALS));
        assertEquals(24.0, report.economy().marketMoneySpentPerGame());
        assertEquals(1.0, report.contracts().signedPerGame());
        assertEquals(0.5, report.contracts().fulfilledPerGame());
        assertEquals(0.5, report.contracts().brokenAutomaticPerGame());
        assertEquals(0.5, report.contracts().gamesWithContractsRate());
        assertEquals(new SimulationReport.ProjectReport(1, 1, 0.5), report.projects());
        assertEquals(3, report.rejections());
    }

    @Test
    void roundsShowDemandByPhaseAndScarcity() {
        SimulationReport report = report();

        assertEquals(15.0, report.demandByPhase().get(GamePart.EARLY).get(Resource.TECHNOLOGY));
        assertEquals(40.0, report.demandByPhase().get(GamePart.MID).get(Resource.TECHNOLOGY));
        assertEquals(50.0, report.demandByPhase().get(GamePart.LATE).get(Resource.TECHNOLOGY));
        assertEquals(0.0, report.demandByPhase().get(GamePart.LATE).get(Resource.FOOD));
        assertEquals(14, report.rounds().size());
        SimulationReport.RoundReport round3 = report.rounds().get(2);
        assertEquals(3, round3.round());
        assertEquals(1.0, round3.tradesPerGame());
        assertEquals(3.0, round3.demandPerGame().get(Resource.TECHNOLOGY));
        assertEquals(2.0, round3.avgHeldPerCity().get(Resource.TECHNOLOGY));
        assertEquals(10.0, round3.avgMoneyHeldPerCity());
        assertEquals(1.5, round3.avgMarketStep().get(Resource.ENERGY));
        assertEquals(2.0, report.rounds().get(4).strainedCitiesPerGame());
    }

    @Test
    void completionRateIsEmptyWithoutResolvedProjects() {
        SimulationReport report = SimulationReport.of(ruleset, 2, BOTS, List.of(games.get(1)));

        assertEquals(new SimulationReport.ProjectReport(0, 0, null), report.projects());
    }

    @Test
    void needsAtLeastOneGame() {
        assertThrows(IllegalArgumentException.class, () -> SimulationReport.of(ruleset, 1, BOTS, List.of()));
    }
}
