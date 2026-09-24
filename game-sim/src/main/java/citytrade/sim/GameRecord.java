package citytrade.sim;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The raw metrics of one simulated game (Architecture 8.2), collected from the engine's events.
 *
 * @param seed                      the game's seed
 * @param seats                     one entry per seat, in seat order
 * @param rounds                    one entry per round, in round order
 * @param winnerSeats               the winners; more than one = shared victory
 * @param contractsSigned           formal contracts that became ACTIVE
 * @param contractsFulfilled        obligations paid in full when due
 * @param contractsBrokenVoluntary  contracts the debtor broke on purpose
 * @param contractsBrokenAutomatic  contracts broken because the debtor could not pay when due
 * @param contractsCancelled        contracts both parties cancelled
 * @param projectsSucceeded         public projects complete at their deadline
 * @param projectsFailed            public projects not complete at their deadline
 * @param rejections                bot commands the engine rejected
 */
public record GameRecord(long seed, List<Seat> seats, List<Round> rounds, List<Integer> winnerSeats,
        int contractsSigned, int contractsFulfilled, int contractsBrokenVoluntary, int contractsBrokenAutomatic,
        int contractsCancelled, int projectsSucceeded, int projectsFailed, int rejections) {

    public GameRecord {
        seats = List.copyOf(seats);
        rounds = List.copyOf(rounds);
        winnerSeats = List.copyOf(winnerSeats);
    }

    /**
     * One city's game.
     *
     * @param levelReachedRound    level -> the round the city reached it (levels never reached are missing)
     * @param strainedRounds       rounds in which the city became Strained at least once
     * @param produced             everything produced in step 1.3 over the game (resources and Money)
     * @param discarded            resources discarded by the storage limit (step 4.5)
     * @param tradesExecuted       instant trades the city was a party of
     * @param tradedGiven          what the city gave in instant trades
     * @param tradedReceived       what the city received in instant trades
     * @param marketBought         units bought from the market (Money field unused, see marketMoneySpent)
     * @param marketSold           units sold to the market (Money field unused, see marketMoneyReceived)
     */
    public record Seat(int seat, CityType city, BotProfile bot, int visiblePrestige, int hiddenPrestige,
            int finalPrestige, int completedObjectives, int finalLevel, SortedMap<Integer, Integer> levelReachedRound,
            int strainedRounds, int contractsBroken, ResourceBundle produced, ResourceBundle discarded,
            int tradesExecuted, ResourceBundle tradedGiven, ResourceBundle tradedReceived, ResourceBundle marketBought,
            int marketMoneySpent, ResourceBundle marketSold, int marketMoneyReceived) {

        public Seat {
            levelReachedRound = Collections.unmodifiableSortedMap(new TreeMap<>(levelReachedRound));
        }
    }

    /**
     * One round of the game.
     *
     * @param tradesExecuted   instant trades executed in the round
     * @param demand           resources paid for levels, buildings, upkeep, crises, projects and event options
     * @param heldAtEnd        what all cities together hold after Round Resolution
     * @param marketStepAtEnd  each resource's market price step index after Round Resolution (next round's price)
     * @param strainedCities   cities that became Strained in the round
     */
    public record Round(int round, int tradesExecuted, ResourceBundle demand, ResourceBundle heldAtEnd,
            Map<Resource, Integer> marketStepAtEnd, int strainedCities) {

        public Round {
            marketStepAtEnd = Collections.unmodifiableMap(new EnumMap<>(marketStepAtEnd));
        }
    }
}
