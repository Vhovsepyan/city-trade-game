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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.CityType;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UseEventOption;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.RegionalOpportunity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Optional events 10 (Public Festival) and 11 (Research Breakthrough), Numbers Sheet 14 and D4. */
class EventOptionsTest {

    private static final ResourceBundle FESTIVAL_COST = new ResourceBundle(2, 0, 0, 0, 2);
    private static final ResourceBundle BREAKTHROUGH_COST = new ResourceBundle(0, 0, 0, 3, 0);

    private final Ruleset ruleset = TestRulesets.standard();

    private GameState festivalWindow() {
        return nextRoundWith(roundOneWindow(ruleset), card("PUBLIC_FESTIVAL"), ruleset).state();
    }

    private GameState breakthroughWindow() {
        return nextRoundWith(roundOneWindow(ruleset), card("RESEARCH_BREAKTHROUGH"), ruleset).state();
    }

    private static GameState resolveAndStartNext(GameState window, Ruleset ruleset) {
        return accept(accept(window, new ResolveRound(), ruleset).state(), new StartRound(), ruleset).state();
    }

    // --- 10 Public Festival ---

    @Test
    void festivalCostsTwoFoodAndTwoMoneyAndGivesOnePrestigeAtResolution() {
        GameState window = festivalWindow();
        int seat = seatOf(window, CityType.ENERGY);
        window = withCity(window, seat, 1, new ResourceBundle(2, 0, 0, 0, 2));

        GameResult.Accepted used = accept(window, new UseEventOption(seat), ruleset);
        assertEquals(List.of(new DomainEvent.EventOptionUsed(seat, "PUBLIC_FESTIVAL", FESTIVAL_COST, Optional.empty())),
                used.events());
        assertEquals(ResourceBundle.EMPTY, used.state().player(seat).holdings());
        assertEquals(0, used.state().player(seat).prestige(), "Prestige is added in step 4.4");

        GameResult.Accepted resolved = accept(used.state(), new ResolveRound(), ruleset);
        assertEquals(1, resolved.state().player(seat).prestige());
        assertTrue(resolved.events().contains(new DomainEvent.PrestigeGained(seat, 1)));
    }

    @Test
    void festivalCannotBePaidWithMoneyReservedByABid() {
        GameState window = festivalWindow();
        int seat = seatOf(window, CityType.ENERGY);
        // An opportunity card is revealed out of schedule, so a bid can reserve Money in this window.
        RegionalOpportunity opportunity = RegionalOpportunity.revealed(window.opportunityDeck().getFirst(), 2);
        window = withCity(window.withRevealedOpportunity(opportunity), seat, 1, new ResourceBundle(2, 0, 0, 0, 3));
        window = accept(window, new PlaceBid(seat, opportunity.id(), 2), ruleset).state();

        assertRejected(window, new UseEventOption(seat), ruleset, RejectionCode.INSUFFICIENT_MONEY);
        window = accept(window, new PlaceBid(seat, opportunity.id(), 1), ruleset).state();
        accept(window, new UseEventOption(seat), ruleset);
    }

    @Test
    void festivalOncePerPlayerButEveryPlayerMayUseIt() {
        GameState window = festivalWindow();
        int first = seatOf(window, CityType.ENERGY);
        int second = seatOf(window, CityType.INDUSTRIAL);
        window = withCity(window, first, 1, new ResourceBundle(4, 0, 0, 0, 4));
        window = withCity(window, second, 1, new ResourceBundle(2, 0, 0, 0, 2));

        GameState used = accept(window, new UseEventOption(first), ruleset).state();
        assertRejected(used, new UseEventOption(first), ruleset, RejectionCode.EVENT_OPTION_ALREADY_USED);
        used = accept(used, new UseEventOption(second), ruleset).state();

        GameState resolved = accept(used, new ResolveRound(), ruleset).state();
        assertEquals(1, resolved.player(first).prestige(), "used once, so +1 once");
        assertEquals(1, resolved.player(second).prestige());
    }

    @Test
    void festivalNeedsTheWholeCost() {
        GameState window = festivalWindow();
        int seat = seatOf(window, CityType.ENERGY);

        assertRejected(withCity(window, seat, 1, new ResourceBundle(1, 0, 0, 0, 5)), new UseEventOption(seat),
                ruleset, RejectionCode.INSUFFICIENT_RESOURCES);
        assertRejected(withCity(window, seat, 1, new ResourceBundle(5, 0, 0, 0, 1)), new UseEventOption(seat),
                ruleset, RejectionCode.INSUFFICIENT_MONEY);
    }

    @Test
    void festivalTakesNoResourceChoice() {
        GameState window = festivalWindow();
        int seat = seatOf(window, CityType.ENERGY);
        window = withCity(window, seat, 1, new ResourceBundle(2, 0, 0, 0, 2));

        assertRejected(window, new UseEventOption(seat, Optional.of(Resource.FOOD)), ruleset,
                RejectionCode.INVALID_EVENT_CHOICE);
    }

    @Test
    void festivalIsOnlyAvailableInItsOwnRound() {
        GameState window = festivalWindow();
        int seat = seatOf(window, CityType.ENERGY);
        GameState nextRound = withCity(resolveAndStartNext(window, ruleset), seat, 1, new ResourceBundle(5, 0, 0, 0, 5));

        assertRejected(nextRound, new UseEventOption(seat), ruleset, RejectionCode.NO_EVENT_OPTION);
    }

    @Test
    void eventsWithoutAnOptionAndRoundsWithoutAnEventRejectTheCommand() {
        GameState roundOne = withCity(roundOneWindow(ruleset), 0, 1, new ResourceBundle(9, 9, 9, 9, 9));
        assertRejected(roundOne, new UseEventOption(0), ruleset, RejectionCode.NO_EVENT_OPTION);

        GameState recession = withCity(nextRoundWith(roundOneWindow(ruleset), card("RECESSION"), ruleset).state(),
                0, 1, new ResourceBundle(9, 9, 9, 9, 9));
        assertRejected(recession, new UseEventOption(0), ruleset, RejectionCode.NO_EVENT_OPTION);
    }

    @Test
    void optionIsOnlyAllowedInTheWindowByAKnownSeat() {
        assertRejected(readyForRoundOne(SEED, ruleset), new UseEventOption(0), ruleset, RejectionCode.INVALID_PHASE);
        assertRejected(festivalWindow(), new UseEventOption(7), ruleset, RejectionCode.UNKNOWN_PLAYER);
    }

    // --- 11 Research Breakthrough ---

    @Test
    void breakthroughCostsThreeTechnologyAndAddsOneChosenProductionFromNextRoundOn() {
        GameState window = breakthroughWindow();
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 1, new ResourceBundle(0, 0, 0, 3, 0));

        GameResult.Accepted used = accept(window, new UseEventOption(seat, Optional.of(Resource.ENERGY)), ruleset);
        assertEquals(List.of(new DomainEvent.EventOptionUsed(seat, "RESEARCH_BREAKTHROUGH", BREAKTHROUGH_COST,
                Optional.of(Resource.ENERGY))), used.events());
        assertEquals(ResourceBundle.EMPTY, used.state().player(seat).holdings());
        assertEquals(new ResourceBundle(0, 1, 0, 0, 0), used.state().player(seat).extraProduction());

        // Level 1 production (3, 1, 1, 1, 2) + 1 Energy, in every later round (permanent).
        GameState round3 = resolveAndStartNext(used.state(), ruleset);
        assertEquals(new ResourceBundle(3, 2, 1, 1, 2), round3.player(seat).holdings());
        GameState round4 = resolveAndStartNext(round3, ruleset);
        assertEquals(new ResourceBundle(6, 4, 2, 2, 4), round4.player(seat).holdings());
    }

    @Test
    void breakthroughGivesNoPrestige() {
        GameState window = breakthroughWindow();
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 1, new ResourceBundle(0, 0, 0, 3, 0));
        GameState used = accept(window, new UseEventOption(seat, Optional.of(Resource.ENERGY)), ruleset).state();

        assertEquals(0, accept(used, new ResolveRound(), ruleset).state().player(seat).prestige());
    }

    @Test
    void breakthroughNeedsANonSpecialtyChoice() {
        GameState window = breakthroughWindow();
        int seat = seatOf(window, CityType.AGRICULTURAL);
        window = withCity(window, seat, 1, new ResourceBundle(0, 0, 0, 3, 0));

        assertRejected(window, new UseEventOption(seat), ruleset, RejectionCode.INVALID_EVENT_CHOICE);
        assertRejected(window, new UseEventOption(seat, Optional.of(Resource.FOOD)), ruleset,
                RejectionCode.INVALID_EVENT_CHOICE);
    }

    @Test
    void breakthroughOncePerPlayerAndNeedsTheWholeCost() {
        GameState window = breakthroughWindow();
        int seat = seatOf(window, CityType.AGRICULTURAL);

        assertRejected(withCity(window, seat, 1, new ResourceBundle(0, 0, 0, 2, 0)),
                new UseEventOption(seat, Optional.of(Resource.ENERGY)), ruleset, RejectionCode.INSUFFICIENT_RESOURCES);

        window = withCity(window, seat, 1, new ResourceBundle(0, 0, 0, 6, 0));
        GameState used = accept(window, new UseEventOption(seat, Optional.of(Resource.ENERGY)), ruleset).state();
        assertRejected(used, new UseEventOption(seat, Optional.of(Resource.MATERIALS)), ruleset,
                RejectionCode.EVENT_OPTION_ALREADY_USED);
    }
}
