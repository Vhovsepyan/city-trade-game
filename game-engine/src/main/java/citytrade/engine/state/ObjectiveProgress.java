package citytrade.engine.state;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Facts that hidden objectives need (Numbers Sheet 15) but the rest of the state does not keep: the market
 * activity is cleared every round, the Strained flag is cleared after its penalty, and holdings and level
 * only show the present.
 *
 * @param marketBuyRounds      the rounds in which the player bought from the global market, ascending, once each
 * @param everStrained         the city became Strained at least once
 * @param levelAfterRound      the city level after the Round Resolution of each round; index 0 = Round 1
 * @param bestMinimumStock     the highest value, over all Round Resolutions so far, of the smallest of the
 *                             player's F, E, M, T amounts; 0 before the first Round Resolution
 */
public record ObjectiveProgress(List<Integer> marketBuyRounds, boolean everStrained, List<Integer> levelAfterRound,
        int bestMinimumStock) {

    public static final ObjectiveProgress NONE = new ObjectiveProgress(List.of(), false, List.of(), 0);

    public ObjectiveProgress {
        marketBuyRounds = List.copyOf(marketBuyRounds);
        levelAfterRound = List.copyOf(levelAfterRound);
    }

    /** The player bought from the market in {@code round}; a round counts once however often they buy. */
    public ObjectiveProgress withMarketBuyIn(int round) {
        if (marketBuyRounds.contains(round)) {
            return this;
        }
        List<Integer> updated = new ArrayList<>(marketBuyRounds);
        updated.add(round);
        return new ObjectiveProgress(updated, everStrained, levelAfterRound, bestMinimumStock);
    }

    public ObjectiveProgress withStrained() {
        return new ObjectiveProgress(marketBuyRounds, true, levelAfterRound, bestMinimumStock);
    }

    /**
     * A Round Resolution ended (rounds are recorded in order): the city is at {@code level} and the smallest of
     * its F, E, M, T amounts is {@code minimumStock}.
     */
    public ObjectiveProgress withRoundEnd(int level, int minimumStock) {
        List<Integer> updated = new ArrayList<>(levelAfterRound);
        updated.add(level);
        return new ObjectiveProgress(marketBuyRounds, everStrained, updated, Math.max(bestMinimumStock, minimumStock));
    }

    /** The city level after the Round Resolution of {@code round}; empty if that round has not ended yet. */
    public OptionalInt levelAfter(int round) {
        if (round < 1 || round > levelAfterRound.size()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(levelAfterRound.get(round - 1));
    }
}
