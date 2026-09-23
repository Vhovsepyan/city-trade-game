package citytrade.ruleset.json;

import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.BuildingEffect;
import citytrade.engine.ruleset.BuildingRules;
import citytrade.engine.ruleset.ContractRules;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.EventRules;
import citytrade.engine.ruleset.LevelRules;
import citytrade.engine.ruleset.MarketRules;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.ObjectiveRules;
import citytrade.engine.ruleset.OpportunityRules;
import citytrade.engine.ruleset.ProjectRules;
import citytrade.engine.ruleset.Ruleset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Checks a parsed ruleset for values the engine cannot work with.
 * Returns every problem found (not only the first), each with the path of the bad value.
 */
final class RulesetValidator {

    // Product decision D5 (docs/TASKS.md): deal 3 hidden objectives, keep 2. Not a tunable balance value.
    static final int D5_OBJECTIVES_DEALT = 3;
    static final int D5_OBJECTIVES_KEPT = 2;

    private final Ruleset ruleset;
    private final List<String> errors = new ArrayList<>();

    private RulesetValidator(Ruleset ruleset) {
        this.ruleset = ruleset;
    }

    static List<String> validate(Ruleset ruleset) {
        RulesetValidator validator = new RulesetValidator(ruleset);
        validator.validateAll();
        return List.copyOf(validator.errors);
    }

    private void validateAll() {
        if (ruleset.version().isBlank()) {
            error("version", "must not be blank");
        }
        atLeast("roundCount", ruleset.roundCount(), 1);
        atLeast("playerCount", ruleset.playerCount(), 1);

        nonNegative("starting.specialtyResource", ruleset.starting().specialtyResource());
        nonNegative("starting.otherResource", ruleset.starting().otherResource());
        nonNegative("starting.money", ruleset.starting().money());

        validateLevels();
        nonNegative("storage.resourceLimit", ruleset.storage().resourceLimit());
        nonNegative("strained.specialtyProductionPenalty", ruleset.strained().specialtyProductionPenalty());
        nonNegative("strained.moneyIncomePenalty", ruleset.strained().moneyIncomePenalty());
        validateBuildings();
        validateMarket();
        validateContracts();
        validateEvents();
        validateObjectives();
        validateProjects();
        validateOpportunities();
    }

    private void validateLevels() {
        List<LevelRules> levels = ruleset.levels();
        if (levels.isEmpty()) {
            error("levels", "must not be empty");
        }
        for (int i = 0; i < levels.size(); i++) {
            LevelRules level = levels.get(i);
            String path = "levels[" + i + "]";
            if (level.level() != i + 1) {
                error(path + ".level", "must be " + (i + 1) + " (levels are listed in order from 1), but is " + level.level());
            }
            nonNegative(path + ".specialtyProduction", level.specialtyProduction());
            nonNegative(path + ".otherResourceProduction", level.otherResourceProduction());
            nonNegative(path + ".moneyProduction", level.moneyProduction());
            nonNegative(path + ".upgradeCost", level.upgradeCost());
            nonNegative(path + ".upkeepResources", level.upkeepResources());
            nonNegative(path + ".reachPrestige", level.reachPrestige());
        }
    }

    private void validateBuildings() {
        List<BuildingRules> buildings = ruleset.buildings();
        uniqueIds("buildings", buildings, BuildingRules::id);
        for (int i = 0; i < buildings.size(); i++) {
            BuildingRules building = buildings.get(i);
            String path = "buildings[" + i + "] (" + building.id() + ")";
            existingLevel(path + ".requiredLevel", building.requiredLevel());
            nonNegative(path + ".cost", building.cost());
            nonNegative(path + ".prestige", building.prestige());
            String effectPath = path + ".effect";
            switch (building.effect()) {
                case BuildingEffect.StorageBonus e -> nonNegative(effectPath + ".amount", e.amount());
                case BuildingEffect.ChosenNonSpecialtyProduction e -> nonNegative(effectPath + ".amount", e.amount());
                case BuildingEffect.SpecialtyProduction e -> nonNegative(effectPath + ".amount", e.amount());
                case BuildingEffect.ProductionBonus e -> nonNegative(effectPath + ".production", e.production());
                case BuildingEffect.UpkeepReduction e -> {
                    existingLevel(effectPath + ".cityLevel", e.cityLevel());
                    nonNegative(effectPath + ".amount", e.amount());
                }
                case BuildingEffect.NoEffect e -> {
                }
            }
        }
    }

    private void validateMarket() {
        MarketRules market = ruleset.market();
        List<MarketRules.PriceStep> steps = market.steps();
        if (steps.isEmpty()) {
            error("market.steps", "must not be empty");
        }
        uniqueIds("market.steps", steps, MarketRules.PriceStep::name);
        for (int i = 0; i < steps.size(); i++) {
            MarketRules.PriceStep step = steps.get(i);
            String path = "market.steps[" + i + "] (" + step.name() + ")";
            nonNegative(path + ".sellValue", step.sellValue());
            // The market must never buy for as much as it sells, or players could create Money for free.
            if (step.sellValue() >= step.buyCost()) {
                error(path, "sellValue " + step.sellValue() + " (market buys) must be lower than buyCost "
                        + step.buyCost() + " (market sells)");
            }
        }
        if (steps.stream().noneMatch(step -> step.name().equals(market.startStep()))) {
            error("market.startStep", "'" + market.startStep() + "' is not one of the market steps");
        }
        atLeast("market.priceMoveThreshold", market.priceMoveThreshold(), 1);
        nonNegative("market.maxBuyPerResourcePerRound", market.maxBuyPerResourcePerRound());
    }

    private void validateContracts() {
        ContractRules contracts = ruleset.contracts();
        atLeast("contracts.maxDurationRounds", contracts.maxDurationRounds(), 1);
        nonNegative("contracts.compensationPerUnpaidResource", contracts.compensationPerUnpaidResource());
        nonNegative("contracts.compensationPerUnpaidMoney", contracts.compensationPerUnpaidMoney());
        // Used as a divisor for the Prestige penalty.
        atLeast("contracts.unpaidCompensationMoneyPerPrestige", contracts.unpaidCompensationMoneyPerPrestige(), 1);
        nonNegative("contracts.breakPrestigePenalty", contracts.breakPrestigePenalty());
    }

    private void validateEvents() {
        EventRules events = ruleset.events();
        rounds("events.eventRounds", events.eventRounds());
        if (events.deck().size() != events.eventRounds().size()) {
            error("events.deck", "has " + events.deck().size() + " cards but there are "
                    + events.eventRounds().size() + " event rounds (must be equal)");
        }
        existingLevel("events.crisisMinLevel", events.crisisMinLevel());
        nonNegative("events.crisisPrestige", events.crisisPrestige());
        uniqueIds("events.deck", events.deck(), EventCard::id);
        for (int i = 0; i < events.deck().size(); i++) {
            EventCard card = events.deck().get(i);
            String path = "events.deck[" + i + "] (" + card.id() + ")";
            switch (card) {
                case EventCard.ResourceCrisis c -> nonNegative(path + ".amount", c.amount());
                case EventCard.NonSpecialtyCrisis c -> nonNegative(path + ".differentResources", c.differentResources());
                case EventCard.BuildingCostDiscount c -> nonNegative(path + ".discount", c.discount());
                case EventCard.UpgradeCostDiscount c -> nonNegative(path + ".discount", c.discount());
                case EventCard.NoMoneyIncome c -> {
                }
                case EventCard.MarketPriceShift c -> nonNegative(path + ".minSellValue", c.minSellValue());
                case EventCard.PrestigePurchase c -> {
                    nonNegative(path + ".cost", c.cost());
                    nonNegative(path + ".prestige", c.prestige());
                }
                case EventCard.ProductionPurchase c -> {
                    nonNegative(path + ".cost", c.cost());
                    nonNegative(path + ".chosenNonSpecialtyProduction", c.chosenNonSpecialtyProduction());
                }
                case EventCard.ProductionBoost c -> nonNegative(path + ".production", c.production());
            }
        }
    }

    private void validateObjectives() {
        ObjectiveRules objectives = ruleset.objectives();
        if (objectives.dealtPerPlayer() != D5_OBJECTIVES_DEALT) {
            error("objectives.dealtPerPlayer", "must be " + D5_OBJECTIVES_DEALT + " (decision D5), but is "
                    + objectives.dealtPerPlayer());
        }
        if (objectives.keptPerPlayer() != D5_OBJECTIVES_KEPT) {
            error("objectives.keptPerPlayer", "must be " + D5_OBJECTIVES_KEPT + " (decision D5), but is "
                    + objectives.keptPerPlayer());
        }
        int needed = objectives.dealtPerPlayer() * ruleset.playerCount();
        if (objectives.deck().size() < needed) {
            error("objectives.deck", "has " + objectives.deck().size() + " cards but dealing "
                    + objectives.dealtPerPlayer() + " to each of " + ruleset.playerCount()
                    + " players needs at least " + needed);
        }
        nonNegative("objectives.completedPrestige", objectives.completedPrestige());
        uniqueIds("objectives.deck", objectives.deck(), ObjectiveCard::id);
        for (int i = 0; i < objectives.deck().size(); i++) {
            ObjectiveCard card = objectives.deck().get(i);
            String path = "objectives.deck[" + i + "] (" + card.id() + ")";
            switch (card) {
                case ObjectiveCard.ActiveTrader c -> {
                }
                case ObjectiveCard.DiversifiedEconomy c -> nonNegative(path + ".minBuildings", c.minBuildings());
                case ObjectiveCard.ProjectPartner c -> {
                }
                case ObjectiveCard.ContractPlayer c ->
                        nonNegative(path + ".minCompletedContracts", c.minCompletedContracts());
                case ObjectiveCard.MarketIndependence c -> nonNegative(path + ".maxBuyRounds", c.maxBuyRounds());
                case ObjectiveCard.RapidDevelopment c -> {
                    existingLevel(path + ".level", c.level());
                    round(path + ".byRound", c.byRound());
                }
                case ObjectiveCard.SteadyCity c -> {
                }
                case ObjectiveCard.OpportunityWinner c -> nonNegative(path + ".minWins", c.minWins());
                case ObjectiveCard.CrisisResponder c -> nonNegative(path + ".minCrisesPaid", c.minCrisesPaid());
                case ObjectiveCard.BalancedStock c -> nonNegative(path + ".minEachResource", c.minEachResource());
                case ObjectiveCard.PatientInvestor c ->
                        nonNegative(path + ".minContributionRounds", c.minContributionRounds());
                case ObjectiveCard.BigDeal c -> nonNegative(path + ".minResourcesGiven", c.minResourcesGiven());
            }
        }
    }

    private void validateProjects() {
        ProjectRules projects = ruleset.projects();
        for (int i = 0; i < projects.windows().size(); i++) {
            ProjectRules.ProjectWindow window = projects.windows().get(i);
            String path = "projects.windows[" + i + "]";
            round(path + ".openRound", window.openRound());
            round(path + ".deadlineRound", window.deadlineRound());
            if (window.openRound() > window.deadlineRound()) {
                error(path, "openRound " + window.openRound() + " must not be after deadlineRound "
                        + window.deadlineRound());
            }
        }
        if (projects.cards().size() < projects.windows().size()) {
            error("projects.cards", "has " + projects.cards().size() + " cards but there are "
                    + projects.windows().size() + " project windows");
        }
        nonNegative("projects.pointsPerResource", projects.pointsPerResource());
        nonNegative("projects.pointsPerMoney", projects.pointsPerMoney());
        nonNegative("projects.minQualifyingPoints", projects.minQualifyingPoints());
        nonNegative("projects.largestContributorPrestige", projects.largestContributorPrestige());
        nonNegative("projects.tiedLargestPrestige", projects.tiedLargestPrestige());
        nonNegative("projects.otherQualifyingPrestige", projects.otherQualifyingPrestige());
        uniqueIds("projects.cards", projects.cards(), ProjectRules.ProjectCard::id);
        for (int i = 0; i < projects.cards().size(); i++) {
            ProjectRules.ProjectCard card = projects.cards().get(i);
            String path = "projects.cards[" + i + "] (" + card.id() + ")";
            nonNegative(path + ".needs", card.needs());
            nonNegative(path + ".productionReward", card.productionReward());
        }
    }

    private void validateOpportunities() {
        OpportunityRules opportunities = ruleset.opportunities();
        rounds("opportunities.appearanceRounds", opportunities.appearanceRounds());
        if (opportunities.cards().size() < opportunities.appearanceRounds().size()) {
            error("opportunities.cards", "has " + opportunities.cards().size() + " cards but there are "
                    + opportunities.appearanceRounds().size() + " appearance rounds");
        }
        uniqueIds("opportunities.cards", opportunities.cards(), OpportunityRules.OpportunityCard::id);
        for (int i = 0; i < opportunities.cards().size(); i++) {
            OpportunityRules.OpportunityCard card = opportunities.cards().get(i);
            nonNegative("opportunities.cards[" + i + "] (" + card.id() + ").productionReward", card.productionReward());
        }
    }

    /** Rounds must be inside the game and strictly increasing (so no round is listed twice). */
    private void rounds(String path, List<Integer> rounds) {
        for (int i = 0; i < rounds.size(); i++) {
            round(path + "[" + i + "]", rounds.get(i));
            if (i > 0 && rounds.get(i) <= rounds.get(i - 1)) {
                error(path, "must be strictly increasing, but " + rounds.get(i) + " follows " + rounds.get(i - 1));
            }
        }
    }

    private void round(String path, int round) {
        if (round < 1 || round > ruleset.roundCount()) {
            error(path, "round " + round + " is outside the game (1-" + ruleset.roundCount() + ")");
        }
    }

    private void existingLevel(String path, int level) {
        if (level < 1 || level > ruleset.levels().size()) {
            error(path, "level " + level + " does not exist (1-" + ruleset.levels().size() + ")");
        }
    }

    private <T> void uniqueIds(String path, List<T> items, Function<T, String> id) {
        Set<String> seen = new HashSet<>();
        for (T item : items) {
            String value = id.apply(item);
            if (value.isBlank()) {
                error(path, "contains a blank id");
            } else if (!seen.add(value)) {
                error(path, "id '" + value + "' is used more than once");
            }
        }
    }

    private void nonNegative(String path, ResourceBundle bundle) {
        nonNegative(path + ".food", bundle.food());
        nonNegative(path + ".energy", bundle.energy());
        nonNegative(path + ".materials", bundle.materials());
        nonNegative(path + ".technology", bundle.technology());
        nonNegative(path + ".money", bundle.money());
    }

    private void nonNegative(String path, int value) {
        atLeast(path, value, 0);
    }

    private void atLeast(String path, int value, int minimum) {
        if (value < minimum) {
            error(path, "must be at least " + minimum + ", but is " + value);
        }
    }

    private void error(String path, String message) {
        errors.add(path + ": " + message);
    }
}
