package citytrade.sim;

import citytrade.bots.BotGame;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.state.FinalResult;
import citytrade.engine.state.FinalScore;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Watches one bot game and turns its events into a {@link GameRecord}. It only reads events and states, so it
 * cannot change the game.
 */
final class GameMetricsCollector implements BotGame.Observer {

    /** Mutable totals of one seat while the game runs. */
    private static final class SeatTotals {
        final TreeMap<Integer, Integer> levelReachedRound = new TreeMap<>();
        final TreeSet<Integer> strainedRounds = new TreeSet<>();
        ResourceBundle produced = ResourceBundle.EMPTY;
        ResourceBundle discarded = ResourceBundle.EMPTY;
        int tradesExecuted;
        ResourceBundle tradedGiven = ResourceBundle.EMPTY;
        ResourceBundle tradedReceived = ResourceBundle.EMPTY;
        ResourceBundle marketBought = ResourceBundle.EMPTY;
        int marketMoneySpent;
        ResourceBundle marketSold = ResourceBundle.EMPTY;
        int marketMoneyReceived;
    }

    private final List<BotProfile> bots;
    private final List<SeatTotals> seats = new ArrayList<>();
    private final List<GameRecord.Round> rounds = new ArrayList<>();

    private int roundTrades;
    private ResourceBundle roundDemand = ResourceBundle.EMPTY;
    private final TreeSet<Integer> roundStrainedSeats = new TreeSet<>();

    private int contractsSigned;
    private int contractsFulfilled;
    private int contractsBrokenVoluntary;
    private int contractsBrokenAutomatic;
    private int contractsCancelled;
    private int projectsSucceeded;
    private int projectsFailed;

    GameMetricsCollector(List<BotProfile> bots) {
        this.bots = List.copyOf(bots);
        for (int seat = 0; seat < bots.size(); seat++) {
            seats.add(new SeatTotals());
        }
    }

    @Override
    public void accepted(GameCommand command, GameState stateAfter, List<DomainEvent> events) {
        int round = stateAfter.round();
        for (DomainEvent event : events) {
            record(event, round);
        }
        if (command instanceof ResolveRound) {
            closeRound(stateAfter);
        }
    }

    private void record(DomainEvent event, int round) {
        switch (event) {
            case DomainEvent.ResourcesProduced e -> seat(e.seat()).produced = seat(e.seat()).produced.plus(e.produced());
            case DomainEvent.ExcessDiscarded e ->
                    seat(e.seat()).discarded = seat(e.seat()).discarded.plus(e.discarded());
            case DomainEvent.CityUpgraded e -> {
                seat(e.seat()).levelReachedRound.put(e.newLevel(), round);
                demand(e.cost());
            }
            case DomainEvent.BuildingBuilt e -> demand(e.cost());
            case DomainEvent.UpkeepPaid e -> demand(e.paid());
            case DomainEvent.CrisisPaid e -> demand(e.paid());
            case DomainEvent.ProjectContributed e -> demand(e.given());
            case DomainEvent.EventOptionUsed e -> demand(e.cost());
            case DomainEvent.CityStrained e -> {
                seat(e.seat()).strainedRounds.add(round);
                roundStrainedSeats.add(e.seat());
            }
            case DomainEvent.TradeExecuted e -> {
                roundTrades++;
                SeatTotals proposer = seat(e.proposerSeat());
                SeatTotals recipient = seat(e.recipientSeat());
                proposer.tradesExecuted++;
                recipient.tradesExecuted++;
                proposer.tradedGiven = proposer.tradedGiven.plus(e.offered());
                proposer.tradedReceived = proposer.tradedReceived.plus(e.requested());
                recipient.tradedGiven = recipient.tradedGiven.plus(e.requested());
                recipient.tradedReceived = recipient.tradedReceived.plus(e.offered());
            }
            case DomainEvent.MarketBought e -> {
                SeatTotals totals = seat(e.seat());
                totals.marketBought = totals.marketBought.with(e.resource(),
                        totals.marketBought.amountOf(e.resource()) + e.quantity());
                totals.marketMoneySpent += e.totalCost();
            }
            case DomainEvent.MarketSold e -> {
                SeatTotals totals = seat(e.seat());
                totals.marketSold = totals.marketSold.with(e.resource(),
                        totals.marketSold.amountOf(e.resource()) + e.quantity());
                totals.marketMoneyReceived += e.totalValue();
            }
            case DomainEvent.ContractSigned e -> contractsSigned++;
            case DomainEvent.ContractFulfilled e -> contractsFulfilled++;
            case DomainEvent.ContractBroken e -> {
                if (e.voluntary()) {
                    contractsBrokenVoluntary++;
                } else {
                    contractsBrokenAutomatic++;
                }
            }
            case DomainEvent.ContractCancelled e -> contractsCancelled++;
            case DomainEvent.ProjectSucceeded e -> projectsSucceeded++;
            case DomainEvent.ProjectFailed e -> projectsFailed++;
            default -> {
                // Other events are not measured.
            }
        }
    }

    /** Demand counts resources only; Money paid for the same things is not a resource. */
    private void demand(ResourceBundle paid) {
        roundDemand = roundDemand.plus(paid.withMoney(0));
    }

    private void closeRound(GameState state) {
        ResourceBundle held = ResourceBundle.EMPTY;
        for (PlayerState player : state.players()) {
            held = held.plus(player.holdings());
        }
        Map<Resource, Integer> steps = new EnumMap<>(Resource.class);
        for (Resource resource : Resource.values()) {
            steps.put(resource, state.market().stepIndexOf(resource));
        }
        rounds.add(new GameRecord.Round(state.round(), roundTrades, roundDemand, held, steps,
                roundStrainedSeats.size()));
        roundTrades = 0;
        roundDemand = ResourceBundle.EMPTY;
        roundStrainedSeats.clear();
    }

    private SeatTotals seat(int seat) {
        return seats.get(seat);
    }

    /** The record of the finished game. */
    GameRecord finish(long seed, BotGame.Result result) {
        GameState state = result.finalState();
        FinalResult finalResult = state.finalResult()
                .orElseThrow(() -> new IllegalStateException("game with seed " + seed + " has no final result"));
        List<GameRecord.Seat> seatRecords = new ArrayList<>();
        for (PlayerState player : state.players()) {
            SeatTotals totals = seat(player.seat());
            FinalScore score = finalResult.scoreOf(player.seat());
            seatRecords.add(new GameRecord.Seat(player.seat(), player.city(), bots.get(player.seat()),
                    score.visiblePrestige(), score.hiddenPrestige(), score.finalPrestige(),
                    score.completedObjectives().size(), score.level(), totals.levelReachedRound,
                    totals.strainedRounds.size(), player.contractsBroken(), totals.produced, totals.discarded,
                    totals.tradesExecuted, totals.tradedGiven, totals.tradedReceived, totals.marketBought,
                    totals.marketMoneySpent, totals.marketSold, totals.marketMoneyReceived));
        }
        return new GameRecord(seed, seatRecords, rounds, finalResult.winnerSeats(), contractsSigned,
                contractsFulfilled, contractsBrokenVoluntary, contractsBrokenAutomatic, contractsCancelled,
                projectsSucceeded, projectsFailed, result.rejections().size());
    }
}
