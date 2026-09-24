package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Each validation rule has at least one broken ruleset that must be rejected with a clear error. */
class RulesetValidationTest {

    private static List<String> errorsOf(ObjectNode json) {
        RulesetValidationException exception = assertThrows(RulesetValidationException.class,
                () -> RulesetLoader.parse(json.toString(), "test-ruleset"));
        return exception.errors();
    }

    private static void assertSingleError(ObjectNode json, String expectedStart) {
        List<String> errors = errorsOf(json);
        assertEquals(1, errors.size(), () -> "expected one error, got " + errors);
        assertTrue(errors.getFirst().startsWith(expectedStart),
                () -> "expected error starting with '" + expectedStart + "', got " + errors);
    }

    private static ObjectNode section(ObjectNode json, String name) {
        return (ObjectNode) json.get(name);
    }

    private static ObjectNode element(ObjectNode json, String arrayName, int index) {
        return (ObjectNode) json.withArray(arrayName).get(index);
    }

    private static ObjectNode element(ObjectNode json, String sectionName, String arrayName, int index) {
        return (ObjectNode) section(json, sectionName).withArray(arrayName).get(index);
    }

    @Test
    void prototypeIsValid() {
        RulesetLoader.parse(TestRulesets.prototypeJson().toString(), "prototype-001");
    }

    // --- market: buy price < sell price (market's view) for every step ---

    @Test
    void rejectsStepWhereMarketBuysForSameAsItSells() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "market", "steps", 2).put("sellValue", 5);
        assertSingleError(json, "market.steps[2] (C): sellValue 5 (market buys) must be lower than buyCost 5");
    }

    @Test
    void rejectsStepWhereMarketBuysForMoreThanItSells() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "market", "steps", 0).put("buyCost", 0);
        assertSingleError(json, "market.steps[0] (A): sellValue 1 (market buys) must be lower than buyCost 0");
    }

    @Test
    void rejectsUnknownMarketStartStep() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "market").put("startStep", "E");
        assertSingleError(json, "market.startStep: 'E' is not one of the market steps");
    }

    @Test
    void rejectsZeroPriceMoveThreshold() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "market").put("priceMoveThreshold", 0);
        assertSingleError(json, "market.priceMoveThreshold: must be at least 1");
    }

    @Test
    void rejectsPriceAboveMaximum() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "market", "steps", 0).put("buyCost", 100_001);
        assertSingleError(json, "market.steps[0] (A).buyCost: must be at most 100000, but is 100001");
    }

    @Test
    void rejectsCostAboveMaximum() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) element(json, "levels", 1).get("upgradeCost")).put("food", 100_001);
        assertSingleError(json, "levels[1].upgradeCost.food: must be at most 100000, but is 100001");
    }

    @Test
    void rejectsProductionAboveMaximum() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "levels", 0).put("specialtyProduction", 100_001);
        assertSingleError(json, "levels[0].specialtyProduction: must be at most 100000, but is 100001");
    }

    @Test
    void rejectsPrestigeAboveMaximum() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "objectives").put("completedPrestige", 100_001);
        assertSingleError(json, "objectives.completedPrestige: must be at most 100000, but is 100001");
    }

    @Test
    void rejectsLimitAboveMaximum() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "market").put("maxBuyPerResourcePerRound", 100_001);
        assertSingleError(json, "market.maxBuyPerResourcePerRound: must be at most 100000, but is 100001");
    }

    @Test
    void rejectsStorageAboveMaximum() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "storage").put("resourceLimit", 100_001);
        assertSingleError(json, "storage.resourceLimit: must be at most 100000, but is 100001");
    }

    // --- objectives: count >= dealt per player x player count ---

    @Test
    void rejectsTooFewObjectivesForAllPlayers() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "objectives").withArray("deck").remove(11);
        assertSingleError(json,
                "objectives.deck: has 11 cards but dealing 3 to each of 4 players needs at least 12");
    }

    @Test
    void rejectsTooFewObjectivesForThreePerPlayerEvenWithASmallerDeal() {
        // A smaller deal must not make a small deck valid: D5 fixes the deal at 3 per player.
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "objectives").put("dealtPerPlayer", 2);
        ArrayNode deck = section(json, "objectives").withArray("deck");
        while (deck.size() > 8) {
            deck.remove(deck.size() - 1);
        }
        assertSingleError(json, "objectives.dealtPerPlayer: must be 3 (decision D5), but is 2");
    }

    @Test
    void rejectsObjectiveDealOtherThanD5() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "objectives").put("dealtPerPlayer", 4);
        assertEquals(List.of(
                "objectives.dealtPerPlayer: must be 3 (decision D5), but is 4",
                "objectives.deck: has 12 cards but dealing 4 to each of 4 players needs at least 16"),
                errorsOf(json));
    }

    @Test
    void rejectsObjectiveKeepOtherThanD5() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "objectives").put("keptPerPlayer", 3);
        assertSingleError(json, "objectives.keptPerPlayer: must be 2 (decision D5), but is 3");
    }

    // --- events: card count matches event rounds, rounds inside the game ---

    @Test
    void rejectsEventDeckSmallerThanEventRounds() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "events").withArray("deck").remove(0);
        assertSingleError(json, "events.deck: has 11 cards but there are 12 event rounds");
    }

    @Test
    void rejectsMoreEventRoundsThanCards() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "events").withArray("eventRounds").add(14);
        assertSingleError(json, "events.deck: has 12 cards but there are 13 event rounds");
    }

    @Test
    void rejectsEventRoundOutsideTheGame() {
        ObjectNode json = TestRulesets.prototypeJson();
        ArrayNode rounds = section(json, "events").withArray("eventRounds");
        rounds.set(11, 15);
        assertSingleError(json, "events.eventRounds[11]: round 15 is outside the game (1-14)");
    }

    @Test
    void rejectsDuplicateEventRound() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "events").withArray("eventRounds").set(1, 2);
        assertSingleError(json, "events.eventRounds: must be strictly increasing, but 2 follows 2");
    }

    // --- projects: windows inside the round count ---

    @Test
    void rejectsProjectDeadlineAfterLastRound() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "projects", "windows", 1).put("deadlineRound", 15);
        assertSingleError(json, "projects.windows[1].deadlineRound: round 15 is outside the game (1-14)");
    }

    @Test
    void rejectsProjectOpeningBeforeRoundOne() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "projects", "windows", 0).put("openRound", 0);
        assertSingleError(json, "projects.windows[0].openRound: round 0 is outside the game (1-14)");
    }

    @Test
    void rejectsProjectWindowThatClosesBeforeItOpens() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "projects", "windows", 0).put("openRound", 7);
        assertSingleError(json, "projects.windows[0]: openRound 7 must not be after deadlineRound 6");
    }

    @Test
    void rejectsFewerProjectCardsThanWindows() {
        ObjectNode json = TestRulesets.prototypeJson();
        ArrayNode cards = section(json, "projects").withArray("cards");
        cards.remove(2);
        cards.remove(1);
        assertSingleError(json, "projects.cards: has 1 cards but there are 2 project windows");
    }

    // --- opportunities ---

    @Test
    void rejectsOpportunityRoundOutsideTheGame() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "opportunities").withArray("appearanceRounds").set(4, 20);
        assertSingleError(json, "opportunities.appearanceRounds[4]: round 20 is outside the game (1-14)");
    }

    @Test
    void rejectsFewerOpportunityCardsThanAppearanceRounds() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "opportunities").withArray("cards").remove(0);
        assertSingleError(json, "opportunities.cards: has 4 cards but there are 5 appearance rounds");
    }

    // --- no negative costs or production values ---

    @Test
    void rejectsNegativeLevelCost() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) element(json, "levels", 1).get("upgradeCost")).put("energy", -1);
        assertSingleError(json, "levels[1].upgradeCost.energy: must be at least 0, but is -1");
    }

    @Test
    void rejectsNegativeLevelProduction() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "levels", 0).put("specialtyProduction", -3);
        assertSingleError(json, "levels[0].specialtyProduction: must be at least 0, but is -3");
    }

    @Test
    void rejectsNegativeBuildingCost() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) element(json, "buildings", 7).get("cost")).put("money", -5);
        assertSingleError(json, "buildings[7] (GRAND_LANDMARK).cost.money: must be at least 0, but is -5");
    }

    @Test
    void rejectsNegativeBuildingProductionEffect() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) element(json, "buildings", 2).get("effect").get("production")).put("money", -1);
        assertSingleError(json, "buildings[2] (MARKET_HALL).effect.production.money: must be at least 0, but is -1");
    }

    @Test
    void rejectsNegativeEventCost() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) element(json, "events", "deck", 9).get("cost")).put("food", -2);
        assertSingleError(json, "events.deck[9] (PUBLIC_FESTIVAL).cost.food: must be at least 0, but is -2");
    }

    @Test
    void rejectsNegativeCrisisAmount() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "events", "deck", 0).put("amount", -2);
        assertSingleError(json, "events.deck[0] (DROUGHT).amount: must be at least 0, but is -2");
    }

    @Test
    void rejectsNegativeProjectNeeds() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) element(json, "projects", "cards", 0).get("needs")).put("materials", -6);
        assertSingleError(json, "projects.cards[0] (REGIONAL_POWER_GRID).needs.materials: must be at least 0, but is -6");
    }

    @Test
    void rejectsNegativeOpportunityProduction() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) element(json, "opportunities", "cards", 4).get("productionReward")).put("money", -2);
        assertSingleError(json,
                "opportunities.cards[4] (COMMERCIAL_HUB).productionReward.money: must be at least 0, but is -2");
    }

    @Test
    void rejectsNegativeStartingMoney() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "starting").put("money", -1);
        assertSingleError(json, "starting.money: must be at least 0, but is -1");
    }

    // --- structure: levels, ids, references ---

    @Test
    void rejectsLevelsOutOfOrder() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "levels", 2).put("level", 4);
        assertSingleError(json, "levels[2].level: must be 3");
    }

    @Test
    void rejectsBuildingForLevelThatDoesNotExist() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "buildings", 7).put("requiredLevel", 4);
        assertSingleError(json, "buildings[7] (GRAND_LANDMARK).requiredLevel: level 4 does not exist (1-3)");
    }

    @Test
    void rejectsDuplicateBuildingId() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "buildings", 1).put("id", "WAREHOUSE");
        assertSingleError(json, "buildings: id 'WAREHOUSE' is used more than once");
    }

    @Test
    void rejectsDuplicateEventId() {
        ObjectNode json = TestRulesets.prototypeJson();
        element(json, "events", "deck", 1).put("id", "DROUGHT");
        assertSingleError(json, "events.deck: id 'DROUGHT' is used more than once");
    }

    @Test
    void rejectsZeroContractDurationAndZeroPrestigeDivisor() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "contracts").put("maxDurationRounds", 0);
        section(json, "contracts").put("unpaidCompensationMoneyPerPrestige", 0);
        assertEquals(List.of(
                "contracts.maxDurationRounds: must be at least 1, but is 0",
                "contracts.unpaidCompensationMoneyPerPrestige: must be at least 1, but is 0"), errorsOf(json));
    }

    @Test
    void reportsAllProblemsAtOnce() {
        ObjectNode json = TestRulesets.prototypeJson();
        section(json, "storage").put("resourceLimit", -1);
        section(json, "strained").put("moneyIncomePenalty", -1);
        section(json, "events").withArray("deck").remove(0);
        RulesetValidationException exception = assertThrows(RulesetValidationException.class,
                () -> RulesetLoader.parse(json.toString(), "test-ruleset"));
        assertEquals(3, exception.errors().size(), exception.errors()::toString);
        assertTrue(exception.getMessage().startsWith("Invalid ruleset test-ruleset:"));
        assertTrue(exception.getMessage().contains("storage.resourceLimit: must be at least 0, but is -1"));
    }
}
