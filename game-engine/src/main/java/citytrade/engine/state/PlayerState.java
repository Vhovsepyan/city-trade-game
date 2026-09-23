package citytrade.engine.state;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.ObjectiveCard;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One player's city. Hidden data (holdings, objectives) is kept here too; who may see what is
 * decided outside the engine (D6).
 *
 * @param seat                    seat 0-3 (D11)
 * @param holdings                F, E, M, T and Money the player owns
 * @param lastUpgradeRound        the round the city last went up a level; 0 = never (one level per round)
 * @param buildings               the buildings the player owns, in build order
 * @param prestige                visible Prestige (hidden objectives are scored only at the end)
 * @param dealtObjectives         the hidden objective cards dealt at setup
 * @param keptObjectives          the dealt cards the player keeps; empty until {@code ChooseObjectives}
 * @param strained                a mandatory payment failed this round; the penalty applies next round
 *                                (Numbers Sheet 9). A flag, not a counter: it does not stack.
 * @param strainedPenaltyActive   the Strained penalty applies to this round's production (set in step 1.2)
 * @param upkeepPriority          D1: the player's upkeep payment order (all non-specialty resources);
 *                                empty = default order (most held first)
 * @param marketThisRound         units bought from / sold to the market this round (cleared in step 4.6)
 * @param contractsBroken         the public "Contracts Broken" counter (Concept 17)
 * @param eventParticipation      crisis policy, paid crises and used optional events (Numbers Sheet 14, D2)
 * @param extraProduction         permanent production added every round from step 1.3 on (Research Breakthrough,
 *                                public project success rewards)
 * @param objectiveProgress       game-long facts the hidden objectives are checked against (Numbers Sheet 15)
 */
public record PlayerState(
        int seat,
        CityType city,
        ResourceBundle holdings,
        int level,
        int lastUpgradeRound,
        List<BuiltBuilding> buildings,
        int prestige,
        List<ObjectiveCard> dealtObjectives,
        List<ObjectiveCard> keptObjectives,
        boolean strained,
        boolean strainedPenaltyActive,
        List<Resource> upkeepPriority,
        MarketActivity marketThisRound,
        int contractsBroken,
        EventParticipation eventParticipation,
        ResourceBundle extraProduction,
        ObjectiveProgress objectiveProgress) {

    public PlayerState {
        buildings = List.copyOf(buildings);
        dealtObjectives = List.copyOf(dealtObjectives);
        keptObjectives = List.copyOf(keptObjectives);
        upkeepPriority = List.copyOf(upkeepPriority);
        Objects.requireNonNull(marketThisRound);
        Objects.requireNonNull(eventParticipation);
        Objects.requireNonNull(extraProduction);
        Objects.requireNonNull(objectiveProgress);
    }

    /** A new city at setup: no buildings, no Prestige, not Strained, default upkeep order, objectives not chosen. */
    public static PlayerState starting(int seat, CityType city, ResourceBundle holdings, int level,
            List<ObjectiveCard> dealtObjectives) {
        return new PlayerState(seat, city, holdings, level, 0, List.of(), 0, dealtObjectives, List.of(), false, false,
                List.of(), MarketActivity.NONE, 0, EventParticipation.NONE, ResourceBundle.EMPTY, ObjectiveProgress.NONE);
    }

    public boolean hasChosenObjectives() {
        return !keptObjectives.isEmpty();
    }

    public boolean hasBuilt(String buildingId) {
        return buildings.stream().anyMatch(building -> building.id().equals(buildingId));
    }

    /** The buildings whose effects apply in {@code round}. */
    public List<BuiltBuilding> activeBuildings(int round) {
        return buildings.stream().filter(building -> building.isActiveIn(round)).toList();
    }

    public PlayerState withKeptObjectives(List<ObjectiveCard> kept) {
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, buildings, prestige, dealtObjectives,
                kept, strained, strainedPenaltyActive, upkeepPriority, marketThisRound, contractsBroken,
                eventParticipation, extraProduction, objectiveProgress);
    }

    public PlayerState withHoldings(ResourceBundle newHoldings) {
        return new PlayerState(seat, city, newHoldings, level, lastUpgradeRound, buildings, prestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, upkeepPriority, marketThisRound, contractsBroken,
                eventParticipation, extraProduction, objectiveProgress);
    }

    /** The city went up to {@code newLevel} in {@code round}. */
    public PlayerState withLevel(int newLevel, int round) {
        return new PlayerState(seat, city, holdings, newLevel, round, buildings, prestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, upkeepPriority, marketThisRound, contractsBroken,
                eventParticipation, extraProduction, objectiveProgress);
    }

    public PlayerState withBuilding(BuiltBuilding building) {
        List<BuiltBuilding> updated = new ArrayList<>(buildings);
        updated.add(building);
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, updated, prestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, upkeepPriority, marketThisRound, contractsBroken,
                eventParticipation, extraProduction, objectiveProgress);
    }

    public PlayerState withPrestige(int newPrestige) {
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, buildings, newPrestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, upkeepPriority, marketThisRound, contractsBroken,
                eventParticipation, extraProduction, objectiveProgress);
    }

    /**
     * Sets the Strained flags. Becoming Strained is also remembered for good (Steady City objective), because
     * the flag itself is cleared after its penalty.
     */
    public PlayerState withStrained(boolean newStrained, boolean newPenaltyActive) {
        ObjectiveProgress progress = newStrained ? objectiveProgress.withStrained() : objectiveProgress;
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, buildings, prestige, dealtObjectives,
                keptObjectives, newStrained, newPenaltyActive, upkeepPriority, marketThisRound, contractsBroken,
                eventParticipation, extraProduction, progress);
    }

    public PlayerState withUpkeepPriority(List<Resource> order) {
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, buildings, prestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, order, marketThisRound, contractsBroken,
                eventParticipation, extraProduction, objectiveProgress);
    }

    public PlayerState withMarketThisRound(MarketActivity activity) {
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, buildings, prestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, upkeepPriority, activity, contractsBroken,
                eventParticipation, extraProduction, objectiveProgress);
    }

    public PlayerState withContractsBroken(int count) {
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, buildings, prestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, upkeepPriority, marketThisRound, count,
                eventParticipation, extraProduction, objectiveProgress);
    }

    public PlayerState withEventParticipation(EventParticipation participation) {
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, buildings, prestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, upkeepPriority, marketThisRound, contractsBroken,
                participation, extraProduction, objectiveProgress);
    }

    public PlayerState withExtraProduction(ResourceBundle production) {
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, buildings, prestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, upkeepPriority, marketThisRound, contractsBroken,
                eventParticipation, production, objectiveProgress);
    }

    public PlayerState withObjectiveProgress(ObjectiveProgress progress) {
        return new PlayerState(seat, city, holdings, level, lastUpgradeRound, buildings, prestige, dealtObjectives,
                keptObjectives, strained, strainedPenaltyActive, upkeepPriority, marketThisRound, contractsBroken,
                eventParticipation, extraProduction, progress);
    }
}
