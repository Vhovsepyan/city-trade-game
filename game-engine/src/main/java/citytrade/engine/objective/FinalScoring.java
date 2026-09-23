package citytrade.engine.objective;

import citytrade.engine.command.DomainEvent;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.FinalResult;
import citytrade.engine.state.FinalScore;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Step 4.8 of the last round: reveal hidden objectives, final score = visible + hidden Prestige, winner
 * (Numbers Sheet 15, 18, 20; Concept 31, 40).
 */
public final class FinalScoring {

    /**
     * Concept 40, best first: more final Prestige, then higher city level, more completed hidden objectives,
     * fewer broken contracts. Players still equal share the victory. Money is never compared.
     */
    static final Comparator<FinalScore> RANKING = Comparator
            .comparingInt(FinalScore::finalPrestige).reversed()
            .thenComparing(Comparator.comparingInt(FinalScore::level).reversed())
            .thenComparing(Comparator.comparingInt((FinalScore score) -> score.completedObjectives().size()).reversed())
            .thenComparingInt(FinalScore::contractsBroken);

    private FinalScoring() {
    }

    /** Does nothing before the last round. */
    public static GameState scoreIfLastRound(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        if (state.round() < ruleset.roundCount()) {
            return state;
        }
        List<FinalScore> scores = new ArrayList<>();
        for (PlayerState player : state.players()) {
            FinalScore score = scoreOf(state, player, ruleset);
            scores.add(score);
            events.add(new DomainEvent.ObjectivesRevealed(player.seat(), score.keptObjectives(),
                    score.completedObjectives(), score.hiddenPrestige()));
        }
        FinalScore best = scores.stream().min(RANKING).orElseThrow();
        List<Integer> winners = scores.stream()
                .filter(score -> RANKING.compare(score, best) == 0)
                .map(FinalScore::seat)
                .toList();
        FinalResult result = new FinalResult(scores, winners);
        events.add(new DomainEvent.FinalScoresRevealed(result));
        return state.withFinalResult(result);
    }

    private static FinalScore scoreOf(GameState state, PlayerState player, Ruleset ruleset) {
        List<String> kept = player.keptObjectives().stream().map(ObjectiveCard::id).toList();
        List<String> completed = Objectives.completedObjectives(state, player.seat(), ruleset);
        int hidden = Math.multiplyExact(completed.size(), ruleset.objectives().completedPrestige());
        return new FinalScore(player.seat(), player.prestige(), kept, completed, hidden,
                Math.addExact(player.prestige(), hidden), player.level(), player.contractsBroken());
    }
}
