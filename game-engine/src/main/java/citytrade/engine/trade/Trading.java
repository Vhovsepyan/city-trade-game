package citytrade.engine.trade;

import citytrade.engine.ResourceBundle;
import citytrade.engine.command.AcceptTrade;
import citytrade.engine.command.CancelTrade;
import citytrade.engine.command.CounterTrade;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.RejectTrade;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.state.GameState;
import citytrade.engine.state.OfferCloseReason;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.TradeOffer;
import citytrade.engine.state.TradeOfferStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Direct instant trades between two players (Concept 14-15, Architecture 5.1-5.4, D7, D8, D12).
 * The proposer may cancel; the recipient may accept, reject or counter; the system expires and invalidates.
 * Nothing is reserved while an offer is open, so accepting checks both sides against the current holdings.
 * An executed trade is binding and the received resources can be used at once; storage is only checked in 4.5.
 */
public final class Trading {

    private Trading() {
    }

    public static GameResult propose(GameState state, ProposeTrade command) {
        Optional<GameResult.Rejected> invalid = checkNewOffer(state, command.seat(), command.recipientSeat(),
                command.offered(), command.requested());
        if (invalid.isPresent()) {
            return invalid.get();
        }
        return addOffer(state, Optional.empty(), command.seat(), command.recipientSeat(), command.offered(),
                command.requested(), List.of());
    }

    public static GameResult accept(GameState state, AcceptTrade command) {
        Optional<GameResult.Rejected> invalid = checkRecipientAction(state, command.seat(), command.offerId());
        if (invalid.isPresent()) {
            return invalid.get();
        }
        TradeOffer offer = state.tradeOffer(command.offerId()).orElseThrow();
        PlayerState proposer = state.player(offer.proposerSeat());
        PlayerState recipient = state.player(offer.recipientSeat());
        // Money reserved by bids cannot be traded away (Numbers Sheet 17).
        boolean proposerHasIt = state.spendableHoldings(proposer.seat()).covers(offer.offered());
        boolean recipientHasIt = state.spendableHoldings(recipient.seat()).covers(offer.requested());
        if (!proposerHasIt || !recipientHasIt) {
            // Architecture 5.4: the trade does not happen and the offer is closed for good. Because the offer
            // changes, this is an accepted command with a TradeInvalidated event, not a Rejected result.
            int missingSeat = proposerHasIt ? recipient.seat() : proposer.seat();
            GameState next = state.withTradeOffer(offer.closedAs(TradeOfferStatus.INVALID, Optional.empty()));
            return new GameResult.Accepted(next, List.of(new DomainEvent.TradeInvalidated(offer.id(), missingSeat)));
        }
        GameState next = state
                .withPlayer(proposer.withHoldings(proposer.holdings().minus(offer.offered()).plus(offer.requested())))
                .withPlayer(recipient.withHoldings(recipient.holdings().minus(offer.requested()).plus(offer.offered())))
                .withTradeOffer(offer.closedAs(TradeOfferStatus.ACCEPTED, Optional.empty()));
        return new GameResult.Accepted(next, List.of(new DomainEvent.TradeExecuted(offer.id(), proposer.seat(),
                recipient.seat(), offer.offered(), offer.requested())));
    }

    public static GameResult reject(GameState state, RejectTrade command) {
        Optional<GameResult.Rejected> invalid = checkRecipientAction(state, command.seat(), command.offerId());
        if (invalid.isPresent()) {
            return invalid.get();
        }
        TradeOffer offer = state.tradeOffer(command.offerId()).orElseThrow();
        GameState next = state.withTradeOffer(offer.closedAs(TradeOfferStatus.REJECTED, Optional.empty()));
        return new GameResult.Accepted(next, List.of(new DomainEvent.TradeRejected(offer.id(), Optional.empty())));
    }

    public static GameResult cancel(GameState state, CancelTrade command) {
        Optional<GameResult.Rejected> invalid = checkOpenOffer(state, command.seat(), command.offerId(), true);
        if (invalid.isPresent()) {
            return invalid.get();
        }
        TradeOffer offer = state.tradeOffer(command.offerId()).orElseThrow();
        GameState next = state.withTradeOffer(offer.closedAs(TradeOfferStatus.CANCELLED, Optional.empty()));
        return new GameResult.Accepted(next, List.of(new DomainEvent.TradeCancelled(offer.id())));
    }

    /** One atomic command: the old offer becomes REJECTED (COUNTEROFFER) and the new one points back to it. */
    public static GameResult counter(GameState state, CounterTrade command) {
        Optional<GameResult.Rejected> invalid = checkRecipientAction(state, command.seat(), command.offerId());
        if (invalid.isPresent()) {
            return invalid.get();
        }
        TradeOffer old = state.tradeOffer(command.offerId()).orElseThrow();
        invalid = checkNewOffer(state, command.seat(), old.proposerSeat(), command.offered(), command.requested());
        if (invalid.isPresent()) {
            return invalid.get();
        }
        Optional<OfferCloseReason> reason = Optional.of(OfferCloseReason.COUNTEROFFER);
        GameState rejected = state.withTradeOffer(old.closedAs(TradeOfferStatus.REJECTED, reason));
        return addOffer(rejected, Optional.of(old.id()), command.seat(), old.proposerSeat(), command.offered(),
                command.requested(), List.of(new DomainEvent.TradeRejected(old.id(), reason)));
    }

    /** Step 4.1 (D8): every offer still open when the window ends expires. */
    public static GameState expireOpenOffers(GameState state, List<DomainEvent> events) {
        GameState next = state;
        for (TradeOffer offer : state.tradeOffers()) {
            if (offer.isOpen()) {
                next = next.withTradeOffer(offer.closedAs(TradeOfferStatus.EXPIRED, Optional.empty()));
                events.add(new DomainEvent.TradeExpired(offer.id()));
            }
        }
        return next;
    }

    private static GameResult addOffer(GameState state, Optional<Integer> parentOfferId, int proposerSeat,
            int recipientSeat, ResourceBundle offered, ResourceBundle requested, List<DomainEvent> earlierEvents) {
        TradeOffer offer = TradeOffer.open(state.nextOfferId(), parentOfferId, proposerSeat, recipientSeat, offered,
                requested, state.round());
        List<DomainEvent> events = new ArrayList<>(earlierEvents);
        events.add(new DomainEvent.TradeProposed(offer.id(), parentOfferId, proposerSeat, recipientSeat, offered,
                requested));
        return new GameResult.Accepted(state.withNewTradeOffer(offer), events);
    }

    /**
     * D12: any bundle of F, E, M, T and Money on each side, at least one side not empty. The proposer must own
     * what they offer when they offer it; it is not reserved and is checked again on accept.
     */
    private static Optional<GameResult.Rejected> checkNewOffer(GameState state, int proposerSeat, int recipientSeat,
            ResourceBundle offered, ResourceBundle requested) {
        if (!state.hasSeat(proposerSeat)) {
            return rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + proposerSeat);
        }
        if (!state.hasSeat(recipientSeat)) {
            return rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + recipientSeat);
        }
        if (proposerSeat == recipientSeat) {
            return rejected(RejectionCode.TRADE_WITH_SELF, "seat " + proposerSeat + " cannot trade with itself");
        }
        if (offered.hasNegativeAmount() || requested.hasNegativeAmount()) {
            return rejected(RejectionCode.INVALID_QUANTITY,
                    "amounts must not be negative: offered " + offered + ", requested " + requested);
        }
        if (offered.isEmpty() && requested.isEmpty()) {
            return rejected(RejectionCode.EMPTY_TRADE, "at least one side of a trade must not be empty");
        }
        if (!state.spendableHoldings(proposerSeat).covers(offered)) {
            return rejected(RejectionCode.INSUFFICIENT_RESOURCES, "offers " + offered + ", has unreserved "
                    + state.spendableHoldings(proposerSeat));
        }
        return Optional.empty();
    }

    /** Accept, reject and counter: only the recipient, only while the offer is open. */
    private static Optional<GameResult.Rejected> checkRecipientAction(GameState state, int seat, int offerId) {
        return checkOpenOffer(state, seat, offerId, false);
    }

    /** The role is checked before the status, so other players learn nothing about an offer they cannot see (D7). */
    private static Optional<GameResult.Rejected> checkOpenOffer(GameState state, int seat, int offerId,
            boolean asProposer) {
        if (!state.hasSeat(seat)) {
            return rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + seat);
        }
        Optional<TradeOffer> offer = state.tradeOffer(offerId);
        if (offer.isEmpty()) {
            return rejected(RejectionCode.UNKNOWN_OFFER, "no trade offer " + offerId);
        }
        if (asProposer && offer.get().proposerSeat() != seat) {
            return rejected(RejectionCode.NOT_OFFER_PROPOSER,
                    "only the proposer (seat " + offer.get().proposerSeat() + ") may cancel offer " + offerId);
        }
        if (!asProposer && offer.get().recipientSeat() != seat) {
            return rejected(RejectionCode.NOT_OFFER_RECIPIENT,
                    "only the recipient (seat " + offer.get().recipientSeat() + ") may answer offer " + offerId);
        }
        if (!offer.get().isOpen()) {
            return rejected(RejectionCode.OFFER_NOT_OPEN, "offer " + offerId + " is " + offer.get().status());
        }
        return Optional.empty();
    }

    private static Optional<GameResult.Rejected> rejected(RejectionCode code, String detail) {
        return Optional.of(new GameResult.Rejected(code, detail));
    }
}
