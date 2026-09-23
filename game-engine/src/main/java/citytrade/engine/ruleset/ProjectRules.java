package citytrade.engine.ruleset;

import citytrade.engine.ResourceBundle;
import java.util.List;

/**
 * Numbers Sheet 16: public projects. One card is drawn for each window (D9: contribution points).
 *
 * @param windows                    open rounds of each project, in order (A, B)
 * @param pointsPerResource          contribution points for 1 resource
 * @param pointsPerMoney             contribution points for 1 Money
 * @param minQualifyingPoints        points needed to be a qualifying contributor
 * @param largestContributorPrestige Prestige for the single largest contributor
 * @param tiedLargestPrestige        Prestige for each contributor tied for largest
 * @param otherQualifyingPrestige    Prestige for every other qualifying contributor
 * @param cards                      all project cards; the unused ones are put away unseen
 */
public record ProjectRules(
        List<ProjectWindow> windows,
        int pointsPerResource,
        int pointsPerMoney,
        int minQualifyingPoints,
        int largestContributorPrestige,
        int tiedLargestPrestige,
        int otherQualifyingPrestige,
        List<ProjectCard> cards) {

    public ProjectRules {
        windows = List.copyOf(windows);
        cards = List.copyOf(cards);
    }

    /** A project is open from {@code openRound} to {@code deadlineRound} (resolved in that round's Round Resolution). */
    public record ProjectWindow(int openRound, int deadlineRound) {
    }

    /**
     * @param needs            total the project needs; only these resource types (and Money) can be given
     * @param productionReward extra production for each qualifying contributor after success
     */
    public record ProjectCard(String id, ResourceBundle needs, ResourceBundle productionReward) {
    }
}
