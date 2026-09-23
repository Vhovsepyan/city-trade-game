package citytrade.engine.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.CityType;
import citytrade.engine.GameRandom;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.ruleset.StartingRules;
import citytrade.engine.state.EventWarning;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class GameSetupTest {

    private final Ruleset ruleset = TestRulesets.standard();

    @Test
    void sameSeedGivesIdenticalState() {
        assertEquals(GameSetup.create(12345, ruleset), GameSetup.create(12345, ruleset));
    }

    @Test
    void differentSeedsGiveDifferentDecks() {
        GameState first = GameSetup.create(1, ruleset);
        GameState second = GameSetup.create(2, ruleset);
        assertNotEquals(dealtObjectiveOrder(first), dealtObjectiveOrder(second));
        assertNotEquals(eventOrder(first), eventOrder(second));
        assertNotEquals(first.opportunityDeck(), second.opportunityDeck());
    }

    @Test
    void decksVaryAcrossManySeeds() {
        Set<List<EventCard>> eventOrders = new HashSet<>();
        Set<List<?>> projectDraws = new HashSet<>();
        for (long seed = 0; seed < 50; seed++) {
            GameState state = GameSetup.create(seed, ruleset);
            eventOrders.add(eventOrder(state));
            projectDraws.add(state.projects());
        }
        assertEquals(50, eventOrders.size());
        assertEquals(6, projectDraws.size(), "all ordered pairs of 2 out of 3 projects appear");
    }

    @Test
    void everyCityIsAssignedToExactlyOneSeat() {
        GameState state = GameSetup.create(9, ruleset);
        assertEquals(4, state.players().size());
        Set<CityType> cities = EnumSet.noneOf(CityType.class);
        for (int seat = 0; seat < 4; seat++) {
            assertEquals(seat, state.player(seat).seat());
            cities.add(state.player(seat).city());
        }
        assertEquals(EnumSet.allOf(CityType.class), cities);
    }

    @Test
    void cityAssignmentDependsOnTheSeed() {
        Set<CityType> citiesAtSeatZero = EnumSet.noneOf(CityType.class);
        for (long seed = 0; seed < 50; seed++) {
            citiesAtSeatZero.add(GameSetup.create(seed, ruleset).player(0).city());
        }
        assertEquals(EnumSet.allOf(CityType.class), citiesAtSeatZero);
    }

    @Test
    void everyCityStartsWithSpecialtyFourOthersOneAndSixMoney() {
        GameState state = GameSetup.create(5, ruleset);
        for (PlayerState player : state.players()) {
            ResourceBundle holdings = player.holdings();
            for (Resource resource : Resource.values()) {
                int expected = resource == player.city().specialty() ? 4 : 1;
                assertEquals(expected, holdings.amountOf(resource), player.city() + " " + resource);
            }
            assertEquals(6, holdings.money());
            assertEquals(1, player.level());
        }
    }

    @Test
    void agriculturalCityMatchesTheNumbersSheetExample() {
        PlayerState agricultural = GameSetup.create(5, ruleset).players().stream()
                .filter(player -> player.city() == CityType.AGRICULTURAL)
                .findFirst().orElseThrow();
        assertEquals(new ResourceBundle(4, 1, 1, 1, 6), agricultural.holdings());
    }

    @Test
    void startingValuesComeFromTheRuleset() {
        Ruleset changed = new Ruleset(ruleset.version(), ruleset.roundCount(), ruleset.playerCount(),
                new StartingRules(7, 2, 9), ruleset.levels(), ruleset.storage(),
                ruleset.strained(), ruleset.buildings(), ruleset.market(), ruleset.contracts(), ruleset.events(),
                ruleset.objectives(), ruleset.projects(), ruleset.opportunities());
        PlayerState player = GameSetup.create(5, changed).player(0);
        assertEquals(7, player.holdings().amountOf(player.city().specialty()));
        assertEquals(9, player.holdings().money());
    }

    @Test
    void eachPlayerIsDealtThreeDifferentObjectivesAndAllTwelveAreUsed() {
        GameState state = GameSetup.create(3, ruleset);
        Set<String> dealtIds = new HashSet<>();
        for (PlayerState player : state.players()) {
            assertEquals(3, player.dealtObjectives().size());
            assertTrue(player.keptObjectives().isEmpty());
            player.dealtObjectives().forEach(card -> dealtIds.add(card.id()));
        }
        assertEquals(12, dealtIds.size());
        assertEquals(ruleset.objectives().deck().stream().map(ObjectiveCard::id).collect(Collectors.toSet()), dealtIds);
        assertFalse(state.allObjectivesChosen());
    }

    @Test
    void topEventCardIsTheWarningForTheFirstEventRound() {
        GameState state = GameSetup.create(3, ruleset);
        EventWarning warning = state.eventWarning().orElseThrow();
        assertEquals(2, warning.round());
        assertEquals(11, state.eventDeck().size());
        assertFalse(state.eventDeck().contains(warning.card()));
        assertEquals(new HashSet<>(ruleset.events().deck()), new HashSet<>(eventOrder(state)));
    }

    @Test
    void twoOfThreeProjectsAreDrawnInWindowOrder() {
        GameState state = GameSetup.create(3, ruleset);
        assertEquals(2, state.projects().size());
        assertNotEquals(state.projects().get(0), state.projects().get(1));
        assertTrue(ruleset.projects().cards().containsAll(state.projects()));
    }

    @Test
    void opportunityDeckHoldsAllFiveCardsShuffled() {
        GameState state = GameSetup.create(3, ruleset);
        assertEquals(5, state.opportunityDeck().size());
        assertEquals(new HashSet<>(ruleset.opportunities().cards()), new HashSet<>(state.opportunityDeck()));
    }

    @Test
    void everyResourceStartsAtMarketStepB() {
        GameState state = GameSetup.create(3, ruleset);
        for (Resource resource : Resource.values()) {
            int index = state.market().stepIndexOf(resource);
            assertEquals("B", ruleset.market().steps().get(index).name());
        }
    }

    @Test
    void randomStateMovesOnAfterSetupDraws() {
        GameState state = GameSetup.create(3, ruleset);
        assertNotEquals(GameRandom.seeded(3), state.random());
        assertEquals(ruleset.version(), state.rulesetVersion());
    }

    @Test
    void rejectsARulesetWhosePlayerCountDoesNotMatchTheFourCities() {
        assertThrows(IllegalArgumentException.class, () -> GameSetup.create(1, TestRulesets.withPlayerCount(3)));
    }

    private static List<ObjectiveCard> dealtObjectiveOrder(GameState state) {
        List<ObjectiveCard> order = new ArrayList<>();
        state.players().forEach(player -> order.addAll(player.dealtObjectives()));
        return order;
    }

    private static List<EventCard> eventOrder(GameState state) {
        List<EventCard> order = new ArrayList<>();
        state.eventWarning().ifPresent(warning -> order.add(warning.card()));
        order.addAll(state.eventDeck());
        return order;
    }
}
