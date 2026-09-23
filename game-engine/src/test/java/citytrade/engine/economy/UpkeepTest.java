package citytrade.engine.economy;

import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.seatOf;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Step 1.4 on its own (production is not run here, so the holdings are exactly what the test sets). */
class UpkeepTest {

    private static final long SEED = 11;

    private final Ruleset ruleset = TestRulesets.standard();
    private final GameState ready = readyForRoundOne(SEED, ruleset);
    private final List<DomainEvent> events = new ArrayList<>();

    @Test
    void levelOneCityPaysNothing() {
        int seat = seatOf(ready, CityType.AGRICULTURAL);
        ResourceBundle holdings = new ResourceBundle(5, 5, 5, 5, 5);
        GameState state = withCity(ready, seat, 1, holdings);

        GameState after = Upkeep.pay(state, ruleset, events);

        assertEquals(holdings, after.player(seat).holdings());
        assertFalse(after.player(seat).strained());
        assertTrue(events.isEmpty());
    }

    @Test
    void levelTwoPaysOneOfTheNonSpecialtyResourceItHasMostOf() {
        int seat = seatOf(ready, CityType.AGRICULTURAL);
        GameState state = withCity(ready, seat, 2, new ResourceBundle(9, 1, 3, 2, 5));

        PlayerState after = Upkeep.pay(state, ruleset, events).player(seat);

        // Food (9) is the specialty and cannot be used; Materials (3) is the most of the rest.
        assertEquals(new ResourceBundle(9, 1, 2, 2, 5), after.holdings());
        assertFalse(after.strained());
        assertEquals(List.of(new DomainEvent.UpkeepPaid(seat, new ResourceBundle(0, 0, 1, 0, 0), 0)), events);
    }

    @Test
    void defaultOrderBreaksTiesFoodEnergyMaterialsTechnology() {
        int technology = seatOf(ready, CityType.TECHNOLOGY);
        int agricultural = seatOf(ready, CityType.AGRICULTURAL);
        GameState state = withCity(ready, technology, 2, new ResourceBundle(2, 2, 2, 0, 0));
        state = withCity(state, agricultural, 2, new ResourceBundle(0, 1, 2, 2, 0));

        GameState after = Upkeep.pay(state, ruleset, events);

        assertEquals(new ResourceBundle(1, 2, 2, 0, 0), after.player(technology).holdings());
        // Materials and Technology tie at 2: Materials comes first.
        assertEquals(new ResourceBundle(0, 1, 1, 2, 0), after.player(agricultural).holdings());
    }

    @Test
    void levelThreePaysTwoDifferentResourcesTheMostHeldFirst() {
        int seat = seatOf(ready, CityType.ENERGY);
        GameState state = withCity(ready, seat, 3, new ResourceBundle(6, 9, 2, 4, 0));

        PlayerState after = Upkeep.pay(state, ruleset, events).player(seat);

        assertEquals(new ResourceBundle(5, 9, 2, 3, 0), after.holdings());
        assertFalse(after.strained());
    }

    @Test
    void levelThreeNeverPaysTwoOfTheSameResource() {
        int seat = seatOf(ready, CityType.ENERGY);
        GameState state = withCity(ready, seat, 3, new ResourceBundle(6, 9, 0, 0, 0));

        PlayerState after = Upkeep.pay(state, ruleset, events).player(seat);

        // Only Food is available; paying 2 Food is not allowed. 1 Food is paid, 1 is missing.
        assertEquals(new ResourceBundle(5, 9, 0, 0, 0), after.holdings());
        assertTrue(after.strained());
        assertEquals(List.of(new DomainEvent.UpkeepPaid(seat, new ResourceBundle(1, 0, 0, 0, 0), 1),
                new DomainEvent.CityStrained(seat)), events);
    }

    @Test
    void neitherSpecialtyNorMoneyIsUsedEvenWhenNothingElseIsLeft() {
        int seat = seatOf(ready, CityType.INDUSTRIAL);
        ResourceBundle holdings = new ResourceBundle(0, 0, 10, 0, 50);
        GameState state = withCity(ready, seat, 2, holdings);

        PlayerState after = Upkeep.pay(state, ruleset, events).player(seat);

        assertEquals(holdings, after.holdings());
        assertTrue(after.strained());
        assertEquals(List.of(new DomainEvent.UpkeepPaid(seat, ResourceBundle.EMPTY, 1),
                new DomainEvent.CityStrained(seat)), events);
    }

    @Test
    void partialUpkeepIsStillPaidAndTheCityBecomesStrained() {
        int seat = seatOf(ready, CityType.TECHNOLOGY);
        GameState state = withCity(ready, seat, 3, new ResourceBundle(0, 0, 3, 8, 4));

        PlayerState after = Upkeep.pay(state, ruleset, events).player(seat);

        assertEquals(new ResourceBundle(0, 0, 2, 8, 4), after.holdings());
        assertTrue(after.strained());
        // Paying upkeep does not touch the penalty of this round.
        assertFalse(after.strainedPenaltyActive());
    }

    @Test
    void playerPriorityOrderIsUsedInsteadOfTheDefault() {
        int seat = seatOf(ready, CityType.AGRICULTURAL);
        GameState state = withCity(ready, seat, 3, new ResourceBundle(0, 5, 5, 1, 0));
        state = state.withPlayer(state.player(seat)
                .withUpkeepPriority(List.of(Resource.TECHNOLOGY, Resource.MATERIALS, Resource.ENERGY)));

        PlayerState after = Upkeep.pay(state, ruleset, events).player(seat);

        // Default would pay Energy + Materials; the priority pays Technology + Materials.
        assertEquals(new ResourceBundle(0, 5, 4, 0, 0), after.holdings());
    }

    @Test
    void priorityOrderSkipsResourcesTheCityDoesNotHave() {
        int seat = seatOf(ready, CityType.AGRICULTURAL);
        GameState state = withCity(ready, seat, 2, new ResourceBundle(0, 5, 5, 0, 0));
        state = state.withPlayer(state.player(seat)
                .withUpkeepPriority(List.of(Resource.TECHNOLOGY, Resource.MATERIALS, Resource.ENERGY)));

        PlayerState after = Upkeep.pay(state, ruleset, events).player(seat);

        assertEquals(new ResourceBundle(0, 5, 4, 0, 0), after.holdings());
        assertFalse(after.strained());
    }

    @Test
    void alreadyStrainedCityThatFailsUpkeepIsStrainedOnlyOnce() {
        int seat = seatOf(ready, CityType.INDUSTRIAL);
        GameState state = withCity(ready, seat, 2, new ResourceBundle(0, 0, 4, 0, 0));
        state = state.withPlayer(state.player(seat).withStrained(true, false));

        PlayerState after = Upkeep.pay(state, ruleset, events).player(seat);

        // A flag, not a counter: the next-round penalty is the same as for one failure.
        assertTrue(after.strained());
        PlayerState nextRound = Production.applyStrainedPenalty(state.withPlayer(after)).player(seat);
        // Level 2: specialty 4 - 2 and Money 3 - 1, not 4 - 4 and 3 - 2.
        assertEquals(new ResourceBundle(1, 1, 2, 1, 2), Production.productionOf(nextRound, 1, ruleset));
    }
}
