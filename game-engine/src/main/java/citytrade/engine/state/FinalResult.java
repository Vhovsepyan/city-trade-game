package citytrade.engine.state;

import java.util.List;

/**
 * The end of the game (step 4.8 of the last round).
 *
 * @param scores       one score per player, in seat order
 * @param winnerSeats  the winners in seat order; more than one = shared victory (Concept 40, tiebreaker 4)
 */
public record FinalResult(List<FinalScore> scores, List<Integer> winnerSeats) {

    public FinalResult {
        scores = List.copyOf(scores);
        winnerSeats = List.copyOf(winnerSeats);
    }

    public FinalScore scoreOf(int seat) {
        return scores.get(seat);
    }
}
