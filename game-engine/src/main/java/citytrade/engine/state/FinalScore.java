package citytrade.engine.state;

import java.util.List;

/**
 * One player's score after Round 14 (Numbers Sheet 18, Concept 31, 40).
 *
 * @param seat                  the player
 * @param visiblePrestige       Prestige earned during the game
 * @param keptObjectives        ids of the hidden objectives the player kept, now revealed
 * @param completedObjectives   ids of the kept objectives that are completed
 * @param hiddenPrestige        Prestige for the completed objectives
 * @param finalPrestige         visible + hidden Prestige
 * @param level                 city level at the end (first tiebreaker)
 * @param contractsBroken       the public Contracts Broken counter at the end (third tiebreaker)
 */
public record FinalScore(int seat, int visiblePrestige, List<String> keptObjectives, List<String> completedObjectives,
        int hiddenPrestige, int finalPrestige, int level, int contractsBroken) {

    public FinalScore {
        keptObjectives = List.copyOf(keptObjectives);
        completedObjectives = List.copyOf(completedObjectives);
    }
}
