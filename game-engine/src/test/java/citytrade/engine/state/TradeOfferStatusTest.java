package citytrade.engine.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.ResourceBundle;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Architecture 5.1: only OPEN -> ACCEPTED/REJECTED/CANCELLED/EXPIRED/INVALID; every other transition fails. */
class TradeOfferStatusTest {

    private static TradeOffer offerIn(TradeOfferStatus status) {
        return new TradeOffer(1, Optional.empty(), 0, 1, new ResourceBundle(1, 0, 0, 0, 0), ResourceBundle.EMPTY, 1,
                status, Optional.empty());
    }

    @ParameterizedTest
    @EnumSource(value = TradeOfferStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    void openMayMoveToEveryClosedStatus(TradeOfferStatus target) {
        assertTrue(TradeOfferStatus.OPEN.canMoveTo(target));
        TradeOffer closed = offerIn(TradeOfferStatus.OPEN).closedAs(target, Optional.empty());
        assertEquals(target, closed.status());
        assertFalse(closed.isOpen());
    }

    @Test
    void openMayNotMoveToOpen() {
        assertFalse(TradeOfferStatus.OPEN.canMoveTo(TradeOfferStatus.OPEN));
        assertThrows(IllegalStateException.class,
                () -> offerIn(TradeOfferStatus.OPEN).closedAs(TradeOfferStatus.OPEN, Optional.empty()));
    }

    @ParameterizedTest
    @EnumSource(value = TradeOfferStatus.class, names = "OPEN", mode = EnumSource.Mode.EXCLUDE)
    void aClosedStatusNeverChanges(TradeOfferStatus closed) {
        for (TradeOfferStatus target : TradeOfferStatus.values()) {
            assertFalse(closed.canMoveTo(target), closed + " -> " + target);
            assertThrows(IllegalStateException.class, () -> offerIn(closed).closedAs(target, Optional.empty()),
                    closed + " -> " + target);
        }
    }

    @Test
    void closingKeepsTheOfferDataAndStoresTheReason() {
        TradeOffer open = offerIn(TradeOfferStatus.OPEN);
        TradeOffer rejected = open.closedAs(TradeOfferStatus.REJECTED, Optional.of(OfferCloseReason.COUNTEROFFER));
        assertEquals(new TradeOffer(open.id(), open.parentOfferId(), open.proposerSeat(), open.recipientSeat(),
                open.offered(), open.requested(), open.roundCreated(), TradeOfferStatus.REJECTED,
                Optional.of(OfferCloseReason.COUNTEROFFER)), rejected);
    }
}
