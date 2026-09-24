package citytrade.sim;

import static citytrade.sim.Averages.mean;
import static citytrade.sim.Averages.perResource;
import static citytrade.sim.Averages.ratio;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ruleset.LevelRules;
import citytrade.engine.ruleset.Ruleset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * The summary of a simulation run (Architecture 8.2, plus win rate by city and the Prestige distribution).
 * Averages "per game" are over all games; averages of a city are over the games that city played (all of them).
 *
 * @param botsBySeat        the bot profile of every seat, in seat order
 * @param rejections        bot commands the engine rejected over all games (should be 0)
 * @param sharedVictoryRate share of games with more than one winner
 * @param finalPrestige     final Prestige of every city in every game
 * @param winningPrestige   the winner's final Prestige, once per game
 * @param demandByPhase     resources paid per game in the early / mid / late part of the game (see {@link GamePart})
 */
public record SimulationReport(String ruleset, int games, long firstSeed, List<String> botsBySeat, int rejections,
        double sharedVictoryRate, Map<CityType, CityReport> cities, Map<String, BotReport> bots,
        Distribution finalPrestige, Distribution winningPrestige, EconomyReport economy, ContractReport contracts,
        ProjectReport projects, Map<GamePart, Map<Resource, Double>> demandByPhase, List<RoundReport> rounds) {

    public SimulationReport {
        botsBySeat = List.copyOf(botsBySeat);
        rounds = List.copyOf(rounds);
    }

    /**
     * A city over all games. A shared victory counts as 1/(number of winners) of a win, so the win rates of all
     * cities add up to 1.
     *
     * @param levelReach level -> how often and when the city reached it
     */
    public record CityReport(double winRate, Distribution finalPrestige, double avgVisiblePrestige,
            double avgHiddenPrestige, double avgCompletedObjectives, double avgFinalLevel,
            Map<Integer, LevelReach> levelReach, double avgStrainedRounds, Map<Resource, Double> avgProduced,
            double avgMoneyProduced, Map<Resource, Double> avgDiscarded, double avgTrades,
            double avgResourcesTradedAway, double avgResourcesTradedIn, double avgMarketUnitsBought,
            double avgMarketUnitsSold, double avgContractsBroken) {
    }

    /**
     * @param reachedRate  share of games in which the city reached the level
     * @param averageRound average round it was reached in, over those games; null if never reached
     */
    public record LevelReach(double reachedRate, Double averageRound) {
    }

    /** A bot profile over all seats it played. Win rate counts shared victories like {@link CityReport}. */
    public record BotReport(int seatsPlayed, double winRate, double avgFinalPrestige) {
    }

    /** Economy totals of all cities together, averaged per game. */
    public record EconomyReport(Map<Resource, Double> producedPerGame, double moneyProducedPerGame,
            double tradesPerGame, double resourcesTradedPerGame, double moneyTradedPerGame,
            Map<Resource, Double> discardedPerGame, Map<Resource, Double> marketBoughtPerGame,
            Map<Resource, Double> marketSoldPerGame, double marketMoneySpentPerGame,
            double marketMoneyReceivedPerGame) {
    }

    /** Formal contracts per game; {@code gamesWithContractsRate} = share of games with at least one signed. */
    public record ContractReport(double signedPerGame, double fulfilledPerGame, double brokenVoluntaryPerGame,
            double brokenAutomaticPerGame, double cancelledPerGame, double gamesWithContractsRate) {
    }

    /** Public projects over all games; {@code completionRate} is null if no project reached its deadline. */
    public record ProjectReport(int succeeded, int failed, Double completionRate) {
    }

    /**
     * One round averaged over all games; resource scarcity shows in the holdings and market steps.
     *
     * @param avgHeldPerCity   what one city holds on average after Round Resolution
     * @param avgMarketStep    average market price step index after Round Resolution (higher = dearer)
     */
    public record RoundReport(int round, double tradesPerGame, Map<Resource, Double> demandPerGame,
            Map<Resource, Double> avgHeldPerCity, double avgMoneyHeldPerCity, Map<Resource, Double> avgMarketStep,
            double strainedCitiesPerGame) {
    }

    /** Builds the report from the records of all games of one run. */
    public static SimulationReport of(Ruleset ruleset, long firstSeed, List<BotProfile> botsBySeat,
            List<GameRecord> games) {
        if (games.isEmpty()) {
            throw new IllegalArgumentException("a report needs at least one game");
        }
        int count = games.size();
        List<GameRecord.Seat> seats = games.stream().flatMap(game -> game.seats().stream()).toList();

        Map<CityType, CityReport> cities = new EnumMap<>(CityType.class);
        for (CityType city : CityType.values()) {
            cities.put(city, cityReport(ruleset, games, city));
        }
        Map<String, BotReport> bots = new LinkedHashMap<>();
        for (BotProfile profile : BotProfile.values()) {
            if (botsBySeat.contains(profile)) {
                bots.put(profile.cliName(), botReport(games, profile));
            }
        }

        return new SimulationReport(ruleset.version(), count, firstSeed,
                botsBySeat.stream().map(BotProfile::cliName).toList(),
                games.stream().mapToInt(GameRecord::rejections).sum(),
                ratio(games.stream().filter(game -> game.winnerSeats().size() > 1).count(), count),
                cities, bots,
                Distribution.of(seats.stream().map(GameRecord.Seat::finalPrestige).toList()),
                Distribution.of(games.stream().map(SimulationReport::winningPrestige).toList()),
                economy(games, seats),
                contracts(games),
                projects(games),
                demandByPhase(ruleset, games),
                rounds(ruleset, games));
    }

    private static CityReport cityReport(Ruleset ruleset, List<GameRecord> games, CityType city) {
        List<GameRecord.Seat> seats = new ArrayList<>();
        double wins = 0;
        for (GameRecord game : games) {
            for (GameRecord.Seat seat : game.seats()) {
                if (seat.city() == city) {
                    seats.add(seat);
                    if (game.winnerSeats().contains(seat.seat())) {
                        wins += 1.0 / game.winnerSeats().size();
                    }
                }
            }
        }
        int played = seats.size();
        Map<Integer, LevelReach> levelReach = new LinkedHashMap<>();
        int startLevel = ruleset.levels().stream().mapToInt(LevelRules::level).min().orElseThrow();
        ruleset.levels().stream().mapToInt(LevelRules::level).filter(level -> level > startLevel).sorted()
                .forEach(level -> levelReach.put(level, levelReach(seats, level)));
        return new CityReport(ratio(wins, played),
                Distribution.of(seats.stream().map(GameRecord.Seat::finalPrestige).toList()),
                mean(seats, GameRecord.Seat::visiblePrestige),
                mean(seats, GameRecord.Seat::hiddenPrestige),
                mean(seats, GameRecord.Seat::completedObjectives),
                mean(seats, GameRecord.Seat::finalLevel),
                levelReach,
                mean(seats, GameRecord.Seat::strainedRounds),
                perResource(seats, GameRecord.Seat::produced, played),
                mean(seats, seat -> seat.produced().money()),
                perResource(seats, GameRecord.Seat::discarded, played),
                mean(seats, GameRecord.Seat::tradesExecuted),
                mean(seats, seat -> seat.tradedGiven().resourceUnits()),
                mean(seats, seat -> seat.tradedReceived().resourceUnits()),
                mean(seats, seat -> seat.marketBought().resourceUnits()),
                mean(seats, seat -> seat.marketSold().resourceUnits()),
                mean(seats, GameRecord.Seat::contractsBroken));
    }

    private static LevelReach levelReach(List<GameRecord.Seat> seats, int level) {
        List<Integer> roundsReached = seats.stream()
                .filter(seat -> seat.levelReachedRound().containsKey(level))
                .map(seat -> seat.levelReachedRound().get(level))
                .toList();
        Double averageRound = roundsReached.isEmpty() ? null : mean(roundsReached, Integer::doubleValue);
        return new LevelReach(ratio(roundsReached.size(), seats.size()), averageRound);
    }

    private static BotReport botReport(List<GameRecord> games, BotProfile profile) {
        List<GameRecord.Seat> seats = new ArrayList<>();
        double wins = 0;
        for (GameRecord game : games) {
            for (GameRecord.Seat seat : game.seats()) {
                if (seat.bot() == profile) {
                    seats.add(seat);
                    if (game.winnerSeats().contains(seat.seat())) {
                        wins += 1.0 / game.winnerSeats().size();
                    }
                }
            }
        }
        return new BotReport(seats.size(), ratio(wins, seats.size()), mean(seats, GameRecord.Seat::finalPrestige));
    }

    private static int winningPrestige(GameRecord game) {
        return game.seats().get(game.winnerSeats().getFirst()).finalPrestige();
    }

    private static EconomyReport economy(List<GameRecord> games, List<GameRecord.Seat> seats) {
        int count = games.size();
        return new EconomyReport(
                perResource(seats, GameRecord.Seat::produced, count),
                ratio(sum(seats, seat -> seat.produced().money()), count),
                // Every trade has two parties, so each seat's count is halved to count the trade once.
                ratio(sum(seats, GameRecord.Seat::tradesExecuted) / 2.0, count),
                ratio(sum(seats, seat -> seat.tradedGiven().resourceUnits()), count),
                ratio(sum(seats, seat -> seat.tradedGiven().money()), count),
                perResource(seats, GameRecord.Seat::discarded, count),
                perResource(seats, GameRecord.Seat::marketBought, count),
                perResource(seats, GameRecord.Seat::marketSold, count),
                ratio(sum(seats, GameRecord.Seat::marketMoneySpent), count),
                ratio(sum(seats, GameRecord.Seat::marketMoneyReceived), count));
    }

    private static ContractReport contracts(List<GameRecord> games) {
        int count = games.size();
        return new ContractReport(
                ratio(sum(games, GameRecord::contractsSigned), count),
                ratio(sum(games, GameRecord::contractsFulfilled), count),
                ratio(sum(games, GameRecord::contractsBrokenVoluntary), count),
                ratio(sum(games, GameRecord::contractsBrokenAutomatic), count),
                ratio(sum(games, GameRecord::contractsCancelled), count),
                ratio(games.stream().filter(game -> game.contractsSigned() > 0).count(), count));
    }

    private static ProjectReport projects(List<GameRecord> games) {
        int succeeded = sum(games, GameRecord::projectsSucceeded);
        int failed = sum(games, GameRecord::projectsFailed);
        Double rate = succeeded + failed == 0 ? null : ratio(succeeded, succeeded + failed);
        return new ProjectReport(succeeded, failed, rate);
    }

    private static Map<GamePart, Map<Resource, Double>> demandByPhase(Ruleset ruleset, List<GameRecord> games) {
        Map<GamePart, Map<Resource, Double>> byPart = new EnumMap<>(GamePart.class);
        for (GamePart part : GamePart.values()) {
            List<GameRecord.Round> rounds = games.stream()
                    .flatMap(game -> game.rounds().stream())
                    .filter(round -> GamePart.of(round.round(), ruleset.roundCount()) == part)
                    .toList();
            byPart.put(part, perResource(rounds, GameRecord.Round::demand, games.size()));
        }
        return byPart;
    }

    private static List<RoundReport> rounds(Ruleset ruleset, List<GameRecord> games) {
        int count = games.size();
        int cities = ruleset.playerCount() * count;
        List<RoundReport> reports = new ArrayList<>();
        for (int round = 1; round <= ruleset.roundCount(); round++) {
            int index = round - 1;
            List<GameRecord.Round> rounds = games.stream().map(game -> game.rounds().get(index)).toList();
            Map<Resource, Double> steps = new EnumMap<>(Resource.class);
            for (Resource resource : Resource.values()) {
                steps.put(resource, ratio(sum(rounds, r -> r.marketStepAtEnd().get(resource)), count));
            }
            reports.add(new RoundReport(round,
                    ratio(sum(rounds, GameRecord.Round::tradesExecuted), count),
                    perResource(rounds, GameRecord.Round::demand, count),
                    perResource(rounds, GameRecord.Round::heldAtEnd, cities),
                    ratio(sum(rounds, r -> r.heldAtEnd().money()), cities),
                    steps,
                    ratio(sum(rounds, GameRecord.Round::strainedCities), count)));
        }
        return reports;
    }

    private static <T> int sum(List<T> items, ToIntFunction<T> value) {
        return items.stream().mapToInt(value).sum();
    }
}
