package citytrade.engine.event;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.seatOf;
import static citytrade.engine.TestGames.withCity;
import static citytrade.engine.event.EventTestSupport.SEED;
import static citytrade.engine.event.EventTestSupport.card;
import static citytrade.engine.event.EventTestSupport.nextRoundWith;
import static citytrade.engine.event.EventTestSupport.roundOneWindow;
import static citytrade.engine.event.EventTestSupport.warn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SetCrisisPolicy;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.command.StartRound;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.LevelRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.CrisisPolicy;
import citytrade.engine.state.GameState;
import citytrade.engine.state.MarketPrices;
import citytrade.engine.state.PlayerState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Crisis events 1-5 (Numbers Sheet 9, 14) and the crisis policy (D2), played through the engine. */
class CrisesTest {

    private final Ruleset ruleset = TestRulesets.standard();

    /** Level 2 production gives 1 of each other resource, so upkeep never fails; this ruleset removes it. */
    private final Ruleset noOtherProduction = TestRulesets.withLevels(ruleset, List.of(
            new LevelRules(1, 3, 1, 2, ResourceBundle.EMPTY, 0, 0),
            new LevelRules(2, 4, 0, 3, new ResourceBundle(3, 3, 3, 3, 0), 1, 1),
            new LevelRules(3, 5, 0, 4, new ResourceBundle(4, 4, 4, 4, 4), 2, 2)));

    @ParameterizedTest
    @ValueSource(strings = {"DROUGHT", "ENERGY_SHORTAGE", "SUPPLY_SHOCK", "EPIDEMIC"})
    void levelTwoCityPaysAResourceCrisisInFullAndGetsCrisisPrestigeAtResolution(String id) {
        EventCard.ResourceCrisis crisis = (EventCard.ResourceCrisis) card(id);
        GameState window = roundOneWindow(ruleset);
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 2, new ResourceBundle(5, 5, 5, 5, 0));

        GameResult.Accepted round2 = nextRoundWith(window, crisis, ruleset);

        // Production (4, 1, 1, 1, 3) and upkeep 1 Energy (most held, first in tie order) come before the crisis.
        ResourceBundle cost = ResourceBundle.EMPTY.with(crisis.resource(), 2);
        PlayerState player = round2.state().player(seat);
        assertEquals(new ResourceBundle(9, 5, 6, 6, 3).minus(cost), player.holdings());
        assertTrue(round2.events().contains(new DomainEvent.CrisisPaid(seat, id, cost)));
        assertFalse(player.strained());
        assertEquals(0, player.prestige(), "Crisis Prestige is added in step 4.4, not in 2.1");

        GameResult.Accepted resolved = accept(round2.state(), new ResolveRound(), ruleset);
        assertEquals(1, resolved.state().player(seat).prestige());
        assertTrue(resolved.events().contains(new DomainEvent.PrestigeGained(seat, 1)));
        assertEquals(List.of(2), resolved.state().player(seat).eventParticipation().crisisPaidRounds());
    }

    @Test
    void cityThatCannotPayInFullPaysNothingAndBecomesStrainedWithoutPrestige() {
        GameState window = roundOneWindow(ruleset);
        int seat = seatOf(window, CityType.ENERGY);
        window = withCity(window, seat, 2, new ResourceBundle(1, 0, 0, 0, 0));

        GameResult.Accepted round2 = nextRoundWith(window, card("DROUGHT"), ruleset);

        // Production (1, 4, 1, 1, 3); upkeep pays 1 Food (most held). 1 Food left < 2: no partial payment.
        PlayerState player = round2.state().player(seat);
        assertEquals(new ResourceBundle(1, 4, 1, 1, 3), player.holdings());
        assertTrue(player.strained());
        assertTrue(round2.events().contains(new DomainEvent.CrisisNotPaid(seat, "DROUGHT", false)));
        assertTrue(round2.events().contains(new DomainEvent.CityStrained(seat)));

        GameState resolved = accept(round2.state(), new ResolveRound(), ruleset).state();
        assertEquals(0, resolved.player(seat).prestige());
        assertTrue(resolved.player(seat).eventParticipation().crisisPaidRounds().isEmpty());
    }

    @Test
    void strainedFromACrisisGivesThePenaltyNextRound() {
        GameState window = roundOneWindow(ruleset);
        int seat = seatOf(window, CityType.ENERGY);
        window = withCity(window, seat, 2, ResourceBundle.EMPTY);

        GameState round2 = nextRoundWith(window, card("DROUGHT"), ruleset).state();
        ResourceBundle before = round2.player(seat).holdings();
        GameState round3 = accept(accept(round2, new ResolveRound(), ruleset).state(), new StartRound(), ruleset)
                .state();

        PlayerState player = round3.player(seat);
        assertTrue(player.strainedPenaltyActive());
        // Energy 4 - 2 and Money 3 - 1; the other resources are produced (1 each) and upkeep takes 1.
        assertEquals(before.energy() + 2, player.holdings().energy());
        assertEquals(before.money() + 2, player.holdings().money());
    }

    @Test
    void levelOneCitiesAreNotAffectedByCrises() {
        GameState window = roundOneWindow(ruleset);
        int seat = seatOf(window, CityType.ENERGY);
        window = withCity(window, seat, 1, ResourceBundle.EMPTY);

        GameResult.Accepted round2 = nextRoundWith(window, card("DROUGHT"), ruleset);

        PlayerState player = round2.state().player(seat);
        assertEquals(new ResourceBundle(1, 3, 1, 1, 2), player.holdings(), "only Level 1 production, no payment");
        assertFalse(player.strained());
        assertTrue(round2.events().stream().noneMatch(e -> e instanceof DomainEvent.CrisisNotPaid n && n.seat() == seat));
        assertTrue(round2.events().stream().noneMatch(e -> e instanceof DomainEvent.CrisisPaid p && p.seat() == seat));
    }

    @Test
    void levelThreeCitiesPayCrisesToo() {
        GameState window = roundOneWindow(ruleset);
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 3, new ResourceBundle(0, 5, 5, 5, 0));

        GameResult.Accepted round2 = nextRoundWith(window, card("EPIDEMIC"), ruleset);

        assertTrue(round2.events().contains(
                new DomainEvent.CrisisPaid(seat, "EPIDEMIC", new ResourceBundle(0, 0, 0, 2, 0))));
    }

    @Test
    void infrastructureFailurePaysTwoDifferentNonSpecialtyResourcesInTheDefaultUpkeepOrder() {
        GameState window = roundOneWindow(ruleset);
        int seat = seatOf(window, CityType.TECHNOLOGY);
        window = withCity(window, seat, 2, new ResourceBundle(0, 0, 3, 9, 0));

        GameResult.Accepted round2 = nextRoundWith(window, card("INFRASTRUCTURE_FAILURE"), ruleset);

        // Production (1, 1, 1, 4, 3) -> (1, 1, 4, 13, 3); upkeep pays Materials (most held) -> (1, 1, 3, 13, 3).
        // Crisis: most held first, never the specialty Technology: Materials, then Food (ties: F before E).
        PlayerState player = round2.state().player(seat);
        assertEquals(new ResourceBundle(0, 1, 2, 13, 3), player.holdings());
        assertTrue(round2.events().contains(
                new DomainEvent.CrisisPaid(seat, "INFRASTRUCTURE_FAILURE", new ResourceBundle(1, 0, 1, 0, 0))));
    }

    @Test
    void infrastructureFailureUsesThePlayersUpkeepPriority() {
        GameState window = roundOneWindow(ruleset);
        int seat = seatOf(window, CityType.TECHNOLOGY);
        window = withCity(window, seat, 2, new ResourceBundle(0, 0, 3, 9, 0));
        window = accept(window, new SetUpkeepPriority(seat,
                List.of(Resource.ENERGY, Resource.FOOD, Resource.MATERIALS)), ruleset).state();

        GameResult.Accepted round2 = nextRoundWith(window, card("INFRASTRUCTURE_FAILURE"), ruleset);

        // Production -> (1, 1, 4, 13, 3); upkeep pays Energy -> (1, 0, 4, 13, 3); crisis: Energy none, Food, Materials.
        assertTrue(round2.events().contains(
                new DomainEvent.CrisisPaid(seat, "INFRASTRUCTURE_FAILURE", new ResourceBundle(1, 0, 1, 0, 0))));
        assertEquals(new ResourceBundle(0, 0, 3, 13, 3), round2.state().player(seat).holdings());
    }

    @Test
    void infrastructureFailureWithOnlyOneKindOfNonSpecialtyResourceCannotBePaid() {
        GameState window = roundOneWindow(noOtherProduction);
        int seat = seatOf(window, CityType.TECHNOLOGY);
        window = withCity(window, seat, 2, new ResourceBundle(0, 5, 0, 0, 0));

        GameResult.Accepted round2 = nextRoundWith(window, card("INFRASTRUCTURE_FAILURE"), noOtherProduction);

        // Upkeep pays 1 Energy; 4 Energy and 4 Technology (specialty) are left, but only one allowed kind.
        PlayerState player = round2.state().player(seat);
        assertEquals(new ResourceBundle(0, 4, 0, 4, 3), player.holdings(), "no partial payment");
        assertTrue(player.strained());
        assertTrue(round2.events().contains(new DomainEvent.CrisisNotPaid(seat, "INFRASTRUCTURE_FAILURE", false)));
    }

    @Test
    void defaultPolicyIsPay() {
        GameState state = readyForRoundOne(SEED, ruleset);
        for (PlayerState player : state.players()) {
            assertEquals(CrisisPolicy.PAY, player.eventParticipation().crisisPolicy());
        }
    }

    @Test
    void skipPolicyPaysNothingAndMakesTheCityStrainedEvenIfItCouldPay() {
        GameState window = warn(roundOneWindow(ruleset), card("DROUGHT"));
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 2, new ResourceBundle(5, 5, 5, 5, 0));
        GameResult.Accepted set = accept(window, new SetCrisisPolicy(seat, CrisisPolicy.SKIP), ruleset);
        assertEquals(List.of(new DomainEvent.CrisisPolicySet(seat)), set.events());

        GameResult.Accepted round2 = nextRoundWith(set.state(), card("DROUGHT"), ruleset);

        PlayerState player = round2.state().player(seat);
        assertEquals(new ResourceBundle(9, 5, 6, 6, 3), player.holdings());
        assertTrue(player.strained());
        assertTrue(round2.events().contains(new DomainEvent.CrisisNotPaid(seat, "DROUGHT", true)));
        GameState resolved = accept(round2.state(), new ResolveRound(), ruleset).state();
        assertEquals(0, resolved.player(seat).prestige());
    }

    @Test
    void policyCanBeChangedBackToPayDuringTheWindow() {
        GameState window = warn(roundOneWindow(ruleset), card("DROUGHT"));
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 2, new ResourceBundle(5, 5, 5, 5, 0));
        window = accept(window, new SetCrisisPolicy(seat, CrisisPolicy.SKIP), ruleset).state();
        window = accept(window, new SetCrisisPolicy(seat, CrisisPolicy.PAY), ruleset).state();

        GameResult.Accepted round2 = nextRoundWith(window, card("DROUGHT"), ruleset);

        assertTrue(round2.events().contains(
                new DomainEvent.CrisisPaid(seat, "DROUGHT", new ResourceBundle(2, 0, 0, 0, 0))));
    }

    @Test
    void policyGoesBackToPayAfterTheCrisis() {
        GameState window = warn(roundOneWindow(ruleset), card("DROUGHT"));
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 2, new ResourceBundle(5, 5, 5, 5, 0));
        window = accept(window, new SetCrisisPolicy(seat, CrisisPolicy.SKIP), ruleset).state();

        GameState round2 = nextRoundWith(window, card("DROUGHT"), ruleset).state();
        assertEquals(CrisisPolicy.PAY, round2.player(seat).eventParticipation().crisisPolicy());

        GameResult.Accepted round3 = nextRoundWith(round2, card("EPIDEMIC"), ruleset);
        assertTrue(round3.events().contains(
                new DomainEvent.CrisisPaid(seat, "EPIDEMIC", new ResourceBundle(0, 0, 0, 2, 0))));
    }

    @Test
    void policyCanOnlyBeSetWhileACrisisIsWarned() {
        GameState window = warn(roundOneWindow(ruleset), card("RECESSION"));
        assertRejected(window, new SetCrisisPolicy(0, CrisisPolicy.SKIP), ruleset, RejectionCode.NO_CRISIS_WARNED);

        GameState noWarning = window.withEvents(window.eventDeck(), Optional.empty(), window.activeEvent());
        assertRejected(noWarning, new SetCrisisPolicy(0, CrisisPolicy.SKIP), ruleset, RejectionCode.NO_CRISIS_WARNED);
    }

    @Test
    void policyCanOnlyBeSetInTheWindowByAKnownSeat() {
        GameState setup = readyForRoundOne(SEED, ruleset);
        GameState crisisWarned = warn(setup, card("DROUGHT"));
        assertRejected(crisisWarned, new SetCrisisPolicy(0, CrisisPolicy.SKIP), ruleset, RejectionCode.INVALID_PHASE);

        GameState window = warn(roundOneWindow(ruleset), card("DROUGHT"));
        assertRejected(window, new SetCrisisPolicy(4, CrisisPolicy.SKIP), ruleset, RejectionCode.UNKNOWN_PLAYER);
    }

    /**
     * Watch List item 3, recorded as it is (do not "fix"): a city that already failed upkeep can skip a crisis
     * in the same round with no extra penalty. Strained is a flag, so the penalty next round is applied once.
     */
    @Test
    void cityStrainedFromUpkeepThatSkipsACrisisIsStrainedOnlyOnce() {
        GameState window = warn(roundOneWindow(noOtherProduction), card("SUPPLY_SHOCK"));
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 2, ResourceBundle.EMPTY);
        window = accept(window, new SetCrisisPolicy(seat, CrisisPolicy.SKIP), noOtherProduction).state();

        // Round 2: production is Food and Money only, so upkeep fails; then the crisis is skipped.
        GameResult.Accepted round2 = nextRoundWith(window, card("SUPPLY_SHOCK"), noOtherProduction);
        PlayerState player = round2.state().player(seat);
        assertTrue(round2.events().contains(new DomainEvent.UpkeepPaid(seat, ResourceBundle.EMPTY, 1)));
        assertTrue(round2.events().contains(new DomainEvent.CrisisNotPaid(seat, "SUPPLY_SHOCK", true)));
        assertTrue(player.strained());
        assertEquals(0, player.prestige());

        // Round 3: one penalty only: Food 4 - 2 (not 4 - 4), Money 3 - 1 (not 3 - 2).
        ResourceBundle before = player.holdings();
        GameState round3 = accept(accept(round2.state(), new ResolveRound(), noOtherProduction).state(),
                new StartRound(), noOtherProduction).state();
        ResourceBundle after = round3.player(seat).holdings();
        assertEquals(before.food() + 2, after.food());
        assertEquals(before.money() + 2, after.money());
    }

    @Test
    void resourceCrisisRaisesThatMarketPriceOneStepForTheEventRoundOnly() {
        GameState round2 = nextRoundWith(roundOneWindow(ruleset), card("DROUGHT"), ruleset).state();

        assertEquals(5, Market.buyCost(round2, Resource.FOOD, ruleset), "step B -> C");
        assertEquals(2, Market.sellValue(round2, Resource.FOOD, ruleset));
        assertEquals(4, Market.buyCost(round2, Resource.ENERGY, ruleset), "other resources stay at B");
        assertEquals(1, round2.market().stepIndexOf(Resource.FOOD), "the stored step does not move");

        GameState resolved = accept(round2, new ResolveRound(), ruleset).state();
        assertEquals(4, Market.buyCost(resolved, Resource.FOOD, ruleset), "back to B after the event round");
    }

    @Test
    void crisisPriceStepStaysWithinTheHighestStep() {
        GameState window = roundOneWindow(ruleset);
        window = window.withMarket(MarketPrices.allAt(3));
        GameState round2 = nextRoundWith(window, card("EPIDEMIC"), ruleset).state();

        assertEquals(6, Market.buyCost(round2, Resource.TECHNOLOGY, ruleset), "already at D, stays D");
        assertEquals(3, Market.sellValue(round2, Resource.TECHNOLOGY, ruleset));
    }
}
