package citytrade.engine.trade;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.AcceptTrade;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.CancelTrade;
import citytrade.engine.command.CounterTrade;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.RejectTrade;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.StartRound;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.OfferCloseReason;
import citytrade.engine.state.TradeOffer;
import citytrade.engine.state.TradeOfferStatus;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TradingTest {

    private static final long SEED = 21;
    private static final int A = 0;
    private static final int B = 1;
    private static final int C = 2;
    private static final ResourceBundle A_HOLDS = new ResourceBundle(5, 0, 0, 0, 10);
    private static final ResourceBundle B_HOLDS = new ResourceBundle(0, 4, 0, 0, 10);
    private static final ResourceBundle TWO_FOOD = new ResourceBundle(2, 0, 0, 0, 0);
    private static final ResourceBundle ONE_ENERGY = new ResourceBundle(0, 1, 0, 0, 0);
    private static final int FIRST_ID = 1;

    private final Ruleset ruleset = TestRulesets.standard();

    /** Round 1 window; A has Food and Money, B has Energy and Money, C and D have only Money. */
    private GameState window() {
        GameState state = accept(readyForRoundOne(SEED, ruleset), new StartRound(), ruleset).state();
        state = withCity(state, A, 1, A_HOLDS);
        state = withCity(state, B, 1, B_HOLDS);
        state = withCity(state, C, 1, new ResourceBundle(0, 0, 0, 0, 10));
        return withCity(state, 3, 1, new ResourceBundle(0, 0, 0, 0, 10));
    }

    private GameState apply(GameState state, GameCommand... commands) {
        for (GameCommand command : commands) {
            state = accept(state, command, ruleset).state();
        }
        return state;
    }

    /** A offers B 2 Food for 1 Energy (offer id 1). */
    private GameState withOpenOffer() {
        return apply(window(), new ProposeTrade(A, B, TWO_FOOD, ONE_ENERGY));
    }

    private static TradeOffer offer(GameState state, int id) {
        return state.tradeOffer(id).orElseThrow();
    }

    // --- propose -------------------------------------------------------------------------------------------------

    @Test
    void proposeCreatesAnOpenOfferAndMovesNothing() {
        GameState before = window();
        GameResult.Accepted result = accept(before, new ProposeTrade(A, B, TWO_FOOD, ONE_ENERGY), ruleset);

        assertEquals(TradeOffer.open(FIRST_ID, Optional.empty(), A, B, TWO_FOOD, ONE_ENERGY, 1),
                offer(result.state(), FIRST_ID));
        assertEquals(FIRST_ID + 1, result.state().nextOfferId());
        assertEquals(before.players(), result.state().players(), "resources are not reserved");
        assertEquals(List.of(new DomainEvent.TradeProposed(FIRST_ID, Optional.empty(), A, B, TWO_FOOD, ONE_ENERGY)),
                result.events());
    }

    @Test
    void offerIdsCountUpInCreationOrder() {
        GameState state = apply(window(), new ProposeTrade(A, B, TWO_FOOD, ONE_ENERGY),
                new ProposeTrade(B, C, ONE_ENERGY, ResourceBundle.EMPTY), new ProposeTrade(A, C, TWO_FOOD, ResourceBundle.EMPTY));
        assertEquals(List.of(1, 2, 3), state.tradeOffers().stream().map(TradeOffer::id).toList());
        assertEquals(4, state.nextOfferId());
    }

    @Test
    void d12AnyBundleIncludingMoneyAndOneSidedGifts() {
        ResourceBundle mixed = new ResourceBundle(1, 0, 0, 0, 2);
        ResourceBundle money = new ResourceBundle(0, 0, 0, 0, 3);
        GameState state = apply(window(),
                new ProposeTrade(A, B, mixed, new ResourceBundle(0, 2, 0, 0, 0)),
                new ProposeTrade(A, B, A_HOLDS, ResourceBundle.EMPTY),
                new ProposeTrade(C, B, ResourceBundle.EMPTY, money));
        assertEquals(3, state.tradeOffers().size());
        state = apply(state, new AcceptTrade(B, 2));
        assertEquals(ResourceBundle.EMPTY, state.player(A).holdings(), "a gift with no limit");
        assertEquals(B_HOLDS.plus(A_HOLDS), state.player(B).holdings());
    }

    @Test
    void bothSidesEmptyIsRejected() {
        assertRejected(window(), new ProposeTrade(A, B, ResourceBundle.EMPTY, ResourceBundle.EMPTY), ruleset,
                RejectionCode.EMPTY_TRADE);
    }

    @Test
    void negativeAmountsAreRejected() {
        ResourceBundle negative = new ResourceBundle(0, 0, 0, 0, -1);
        assertRejected(window(), new ProposeTrade(A, B, negative, ONE_ENERGY), ruleset, RejectionCode.INVALID_QUANTITY);
        assertRejected(window(), new ProposeTrade(A, B, TWO_FOOD, negative), ruleset, RejectionCode.INVALID_QUANTITY);
    }

    @Test
    void tradingWithYourselfIsRejected() {
        assertRejected(window(), new ProposeTrade(A, A, TWO_FOOD, ONE_ENERGY), ruleset, RejectionCode.TRADE_WITH_SELF);
    }

    @Test
    void unknownSeatsAreRejected() {
        assertRejected(window(), new ProposeTrade(7, B, TWO_FOOD, ONE_ENERGY), ruleset, RejectionCode.UNKNOWN_PLAYER);
        assertRejected(window(), new ProposeTrade(A, 7, TWO_FOOD, ONE_ENERGY), ruleset, RejectionCode.UNKNOWN_PLAYER);
        assertRejected(window(), new ProposeTrade(A, -1, TWO_FOOD, ONE_ENERGY), ruleset, RejectionCode.UNKNOWN_PLAYER);
    }

    @Test
    void theProposerMustOwnWhatTheyOffer() {
        assertRejected(window(), new ProposeTrade(A, B, new ResourceBundle(6, 0, 0, 0, 0), ONE_ENERGY), ruleset,
                RejectionCode.INSUFFICIENT_RESOURCES);
        assertRejected(window(), new ProposeTrade(A, B, new ResourceBundle(0, 0, 0, 0, 11), ONE_ENERGY), ruleset,
                RejectionCode.INSUFFICIENT_RESOURCES);
    }

    @Test
    void theRequestedSideIsNotCheckedWhenProposing() {
        GameState state = apply(window(), new ProposeTrade(A, B, TWO_FOOD, new ResourceBundle(0, 9, 0, 0, 0)));
        assertTrue(offer(state, FIRST_ID).isOpen());
    }

    // --- accept --------------------------------------------------------------------------------------------------

    @Test
    void acceptExecutesBothSidesAtOnce() {
        GameResult.Accepted result = accept(withOpenOffer(), new AcceptTrade(B, FIRST_ID), ruleset);
        GameState state = result.state();

        assertEquals(A_HOLDS.minus(TWO_FOOD).plus(ONE_ENERGY), state.player(A).holdings());
        assertEquals(B_HOLDS.minus(ONE_ENERGY).plus(TWO_FOOD), state.player(B).holdings());
        assertEquals(TradeOfferStatus.ACCEPTED, offer(state, FIRST_ID).status());
        assertEquals(List.of(new DomainEvent.TradeExecuted(FIRST_ID, A, B, TWO_FOOD, ONE_ENERGY)), result.events());
    }

    @Test
    void receivedResourcesAreUsableInTheSameWindow() {
        GameState state = apply(withOpenOffer(), new AcceptTrade(B, FIRST_ID));
        int foodValue = Market.sellValue(state, Resource.FOOD, ruleset);
        // B had no Food before the trade and sells the received Food at once.
        state = apply(state, new SellToMarket(B, Resource.FOOD, 2));
        assertEquals(B_HOLDS.minus(ONE_ENERGY).withMoney(B_HOLDS.money() + 2 * foodValue), state.player(B).holdings());
    }

    @Test
    void receivedMoneyIsUsableInTheSameWindow() {
        GameState state = withCity(window(), C, 1, ResourceBundle.EMPTY);
        int price = Market.buyCost(state, Resource.FOOD, ruleset);
        state = apply(state, new ProposeTrade(A, C, new ResourceBundle(0, 0, 0, 0, price), ResourceBundle.EMPTY),
                new AcceptTrade(C, FIRST_ID), new BuyFromMarket(C, Resource.FOOD, 1));
        assertEquals(new ResourceBundle(1, 0, 0, 0, 0), state.player(C).holdings());
    }

    @Test
    void storageIsNotCheckedDuringTheWindow() {
        ResourceBundle huge = new ResourceBundle(100, 0, 0, 0, 0);
        GameState state = withCity(window(), A, 1, huge);
        state = apply(state, new ProposeTrade(A, B, huge, ResourceBundle.EMPTY), new AcceptTrade(B, FIRST_ID));
        assertEquals(100, state.player(B).holdings().food());
    }

    @Test
    void acceptInvalidatesWhenTheProposerNoLongerHasTheResources() {
        GameState state = apply(withOpenOffer(), new SellToMarket(A, Resource.FOOD, 4));
        GameResult.Accepted result = accept(state, new AcceptTrade(B, FIRST_ID), ruleset);

        assertEquals(TradeOfferStatus.INVALID, offer(result.state(), FIRST_ID).status());
        assertEquals(state.players(), result.state().players(), "nothing is traded");
        assertEquals(List.of(new DomainEvent.TradeInvalidated(FIRST_ID, A)), result.events());
        assertRejected(result.state(), new AcceptTrade(B, FIRST_ID), ruleset, RejectionCode.OFFER_NOT_OPEN);
    }

    @Test
    void acceptInvalidatesWhenTheRecipientDoesNotHaveTheRequestedResources() {
        GameState state = apply(window(), new ProposeTrade(A, B, TWO_FOOD, new ResourceBundle(0, 5, 0, 0, 0)));
        GameResult.Accepted result = accept(state, new AcceptTrade(B, FIRST_ID), ruleset);

        assertEquals(TradeOfferStatus.INVALID, offer(result.state(), FIRST_ID).status());
        assertEquals(state.players(), result.state().players());
        assertEquals(List.of(new DomainEvent.TradeInvalidated(FIRST_ID, B)), result.events());
    }

    @Test
    void acceptChecksMoneyToo() {
        GameState state = apply(window(), new ProposeTrade(A, B, new ResourceBundle(0, 0, 0, 0, 10), ONE_ENERGY),
                new BuyFromMarket(A, Resource.ENERGY, 1));
        GameResult.Accepted result = accept(state, new AcceptTrade(B, FIRST_ID), ruleset);
        assertEquals(TradeOfferStatus.INVALID, offer(result.state(), FIRST_ID).status());
        assertEquals(state.players(), result.state().players());
    }

    @Test
    void resourcesAreNotReservedSoASecondOfferOnTheSameFoodBecomesInvalid() {
        GameState state = apply(window(), new ProposeTrade(A, B, new ResourceBundle(4, 0, 0, 0, 0), ONE_ENERGY),
                new ProposeTrade(A, C, new ResourceBundle(4, 0, 0, 0, 0), ResourceBundle.EMPTY),
                new AcceptTrade(B, 1), new AcceptTrade(C, 2));
        assertEquals(TradeOfferStatus.ACCEPTED, offer(state, 1).status());
        assertEquals(TradeOfferStatus.INVALID, offer(state, 2).status());
        assertEquals(0, state.player(C).holdings().food());
    }

    // --- reject and cancel ---------------------------------------------------------------------------------------

    @Test
    void recipientRejects() {
        GameState before = withOpenOffer();
        GameResult.Accepted result = accept(before, new RejectTrade(B, FIRST_ID), ruleset);
        TradeOffer rejected = offer(result.state(), FIRST_ID);
        assertEquals(TradeOfferStatus.REJECTED, rejected.status());
        assertEquals(Optional.empty(), rejected.closeReason());
        assertEquals(before.players(), result.state().players());
        assertEquals(List.of(new DomainEvent.TradeRejected(FIRST_ID, Optional.empty())), result.events());
    }

    @Test
    void proposerCancels() {
        GameState before = withOpenOffer();
        GameResult.Accepted result = accept(before, new CancelTrade(A, FIRST_ID), ruleset);
        assertEquals(TradeOfferStatus.CANCELLED, offer(result.state(), FIRST_ID).status());
        assertEquals(before.players(), result.state().players());
        assertEquals(List.of(new DomainEvent.TradeCancelled(FIRST_ID)), result.events());
    }

    // --- who may do what -----------------------------------------------------------------------------------------

    @Test
    void onlyTheRecipientMayAcceptRejectOrCounter() {
        GameState state = withOpenOffer();
        for (int seat : new int[] {A, C, 3}) {
            assertRejected(state, new AcceptTrade(seat, FIRST_ID), ruleset, RejectionCode.NOT_OFFER_RECIPIENT);
            assertRejected(state, new RejectTrade(seat, FIRST_ID), ruleset, RejectionCode.NOT_OFFER_RECIPIENT);
            assertRejected(state, new CounterTrade(seat, FIRST_ID, ResourceBundle.EMPTY, TWO_FOOD), ruleset,
                    RejectionCode.NOT_OFFER_RECIPIENT);
        }
    }

    @Test
    void onlyTheProposerMayCancel() {
        GameState state = withOpenOffer();
        for (int seat : new int[] {B, C, 3}) {
            assertRejected(state, new CancelTrade(seat, FIRST_ID), ruleset, RejectionCode.NOT_OFFER_PROPOSER);
        }
    }

    @Test
    void unknownOffersAndSeatsAreRejected() {
        GameState state = withOpenOffer();
        int missing = 99;
        assertRejected(state, new AcceptTrade(B, missing), ruleset, RejectionCode.UNKNOWN_OFFER);
        assertRejected(state, new RejectTrade(B, missing), ruleset, RejectionCode.UNKNOWN_OFFER);
        assertRejected(state, new CancelTrade(A, missing), ruleset, RejectionCode.UNKNOWN_OFFER);
        assertRejected(state, new CounterTrade(B, missing, ONE_ENERGY, TWO_FOOD), ruleset, RejectionCode.UNKNOWN_OFFER);
        assertRejected(state, new AcceptTrade(7, FIRST_ID), ruleset, RejectionCode.UNKNOWN_PLAYER);
        assertRejected(state, new CancelTrade(-1, FIRST_ID), ruleset, RejectionCode.UNKNOWN_PLAYER);
    }

    @Test
    void theRoleIsCheckedBeforeTheStatus() {
        GameState state = apply(withOpenOffer(), new RejectTrade(B, FIRST_ID));
        assertRejected(state, new AcceptTrade(C, FIRST_ID), ruleset, RejectionCode.NOT_OFFER_RECIPIENT);
        assertRejected(state, new CancelTrade(C, FIRST_ID), ruleset, RejectionCode.NOT_OFFER_PROPOSER);
    }

    /** Every closed status is final: no command can accept, reject, cancel or counter it again. */
    @ParameterizedTest
    @EnumSource(value = TradeOfferStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    void noCommandChangesAClosedOffer(TradeOfferStatus closed) {
        GameState state = closeFirstOfferAs(closed);
        assertEquals(closed, offer(state, FIRST_ID).status());
        assertRejected(state, new AcceptTrade(B, FIRST_ID), ruleset, RejectionCode.OFFER_NOT_OPEN);
        assertRejected(state, new RejectTrade(B, FIRST_ID), ruleset, RejectionCode.OFFER_NOT_OPEN);
        assertRejected(state, new CancelTrade(A, FIRST_ID), ruleset, RejectionCode.OFFER_NOT_OPEN);
        assertRejected(state, new CounterTrade(B, FIRST_ID, ONE_ENERGY, TWO_FOOD), ruleset,
                RejectionCode.OFFER_NOT_OPEN);
    }

    /** Brings offer 1 (A -> B) into {@code status} through real commands; EXPIRED ends in the next window. */
    private GameState closeFirstOfferAs(TradeOfferStatus status) {
        UnaryOperator<GameState> close = switch (status) {
            case ACCEPTED -> s -> apply(s, new AcceptTrade(B, FIRST_ID));
            case REJECTED -> s -> apply(s, new RejectTrade(B, FIRST_ID));
            case CANCELLED -> s -> apply(s, new CancelTrade(A, FIRST_ID));
            case EXPIRED -> s -> apply(s, new ResolveRound(), new StartRound());
            case INVALID -> s -> apply(s, new SellToMarket(A, Resource.FOOD, 5), new AcceptTrade(B, FIRST_ID));
            case OPEN -> throw new IllegalArgumentException("not a closed status");
        };
        return close.apply(withOpenOffer());
    }

    // --- counteroffers -------------------------------------------------------------------------------------------

    @Test
    void counterRejectsTheOldOfferAndOpensALinkedOneInTheOtherDirection() {
        ResourceBundle threeFood = new ResourceBundle(3, 0, 0, 0, 0);
        GameResult.Accepted result = accept(withOpenOffer(), new CounterTrade(B, FIRST_ID, ONE_ENERGY, threeFood),
                ruleset);
        GameState state = result.state();

        TradeOffer old = offer(state, FIRST_ID);
        assertEquals(TradeOfferStatus.REJECTED, old.status());
        assertEquals(Optional.of(OfferCloseReason.COUNTEROFFER), old.closeReason());
        assertEquals(TradeOffer.open(2, Optional.of(FIRST_ID), B, A, ONE_ENERGY, threeFood, 1), offer(state, 2));
        assertEquals(List.of(
                new DomainEvent.TradeRejected(FIRST_ID, Optional.of(OfferCloseReason.COUNTEROFFER)),
                new DomainEvent.TradeProposed(2, Optional.of(FIRST_ID), B, A, ONE_ENERGY, threeFood)),
                result.events());

        state = apply(state, new AcceptTrade(A, 2));
        assertEquals(A_HOLDS.minus(threeFood).plus(ONE_ENERGY), state.player(A).holdings());
        assertEquals(B_HOLDS.minus(ONE_ENERGY).plus(threeFood), state.player(B).holdings());
    }

    @Test
    void countersCanChainAndEachPointsToItsParent() {
        GameState state = apply(withOpenOffer(),
                new CounterTrade(B, 1, ONE_ENERGY, new ResourceBundle(4, 0, 0, 0, 0)),
                new CounterTrade(A, 2, new ResourceBundle(3, 0, 0, 0, 0), ONE_ENERGY),
                new CounterTrade(B, 3, ONE_ENERGY, new ResourceBundle(3, 0, 0, 0, 1)));
        assertEquals(List.of(Optional.empty(), Optional.of(1), Optional.of(2), Optional.of(3)),
                state.tradeOffers().stream().map(TradeOffer::parentOfferId).toList());
        assertEquals(List.of(TradeOfferStatus.REJECTED, TradeOfferStatus.REJECTED, TradeOfferStatus.REJECTED,
                TradeOfferStatus.OPEN), state.tradeOffers().stream().map(TradeOffer::status).toList());
        assertEquals(A, offer(state, 4).recipientSeat());
    }

    @Test
    void anInvalidCounterIsRejectedAndTheOldOfferStaysOpen() {
        GameState state = withOpenOffer();
        assertRejected(state, new CounterTrade(B, FIRST_ID, ResourceBundle.EMPTY, ResourceBundle.EMPTY), ruleset,
                RejectionCode.EMPTY_TRADE);
        assertRejected(state, new CounterTrade(B, FIRST_ID, new ResourceBundle(0, 5, 0, 0, 0), TWO_FOOD), ruleset,
                RejectionCode.INSUFFICIENT_RESOURCES);
        assertRejected(state, new CounterTrade(B, FIRST_ID, new ResourceBundle(0, -1, 0, 0, 0), TWO_FOOD), ruleset,
                RejectionCode.INVALID_QUANTITY);
        assertTrue(offer(state, FIRST_ID).isOpen());
        assertEquals(1, state.tradeOffers().size());
    }

    // --- expiry and phases ---------------------------------------------------------------------------------------

    @Test
    void openOffersExpireAtResolutionAndClosedOnesStayAsTheyAre() {
        GameState state = apply(window(), new ProposeTrade(A, B, TWO_FOOD, ONE_ENERGY),
                new ProposeTrade(B, C, ONE_ENERGY, ResourceBundle.EMPTY), new ProposeTrade(C, A, ResourceBundle.EMPTY,
                        TWO_FOOD), new RejectTrade(A, 3));
        GameResult.Accepted result = accept(state, new ResolveRound(), ruleset);

        assertEquals(List.of(TradeOfferStatus.EXPIRED, TradeOfferStatus.EXPIRED, TradeOfferStatus.REJECTED),
                result.state().tradeOffers().stream().map(TradeOffer::status).toList());
        List<DomainEvent> expired = result.events().stream()
                .filter(event -> event instanceof DomainEvent.TradeExpired).toList();
        assertEquals(List.of(new DomainEvent.TradeExpired(1), new DomainEvent.TradeExpired(2)), expired);
    }

    @Test
    void anExpiredOfferCannotBeAcceptedNextRound() {
        GameState state = apply(withOpenOffer(), new ResolveRound(), new StartRound());
        assertRejected(state, new AcceptTrade(B, FIRST_ID), ruleset, RejectionCode.OFFER_NOT_OPEN);
        assertFalse(state.tradeOffers().stream().anyMatch(TradeOffer::isOpen));
    }

    @Test
    void tradeCommandsOnlyInTheWindow() {
        GameState open = withOpenOffer();
        GameState resolution = apply(open, new ResolveRound());
        GameState setup = readyForRoundOne(SEED, ruleset);
        for (GameState state : List.of(setup, resolution)) {
            assertRejected(state, new ProposeTrade(A, B, TWO_FOOD, ONE_ENERGY), ruleset, RejectionCode.INVALID_PHASE);
            assertRejected(state, new AcceptTrade(B, FIRST_ID), ruleset, RejectionCode.INVALID_PHASE);
            assertRejected(state, new RejectTrade(B, FIRST_ID), ruleset, RejectionCode.INVALID_PHASE);
            assertRejected(state, new CancelTrade(A, FIRST_ID), ruleset, RejectionCode.INVALID_PHASE);
            assertRejected(state, new CounterTrade(B, FIRST_ID, ONE_ENERGY, TWO_FOOD), ruleset,
                    RejectionCode.INVALID_PHASE);
        }
    }
}
