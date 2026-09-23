package citytrade.engine.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import citytrade.engine.ResourceBundle;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContractStatusTest {

    private static Set<ContractStatus> allowedFrom(ContractStatus from) {
        Set<ContractStatus> allowed = EnumSet.noneOf(ContractStatus.class);
        for (ContractStatus target : ContractStatus.values()) {
            if (from.canMoveTo(target)) {
                allowed.add(target);
            }
        }
        return allowed;
    }

    @Test
    void aProposalCanBeSignedExpireOrBecomeInvalid() {
        assertEquals(EnumSet.of(ContractStatus.ACTIVE, ContractStatus.EXPIRED, ContractStatus.INVALID),
                allowedFrom(ContractStatus.PROPOSED));
    }

    @Test
    void anActiveContractCanBeFulfilledBrokenOrCancelled() {
        assertEquals(EnumSet.of(ContractStatus.FULFILLED, ContractStatus.BROKEN, ContractStatus.CANCELLED),
                allowedFrom(ContractStatus.ACTIVE));
    }

    @ParameterizedTest
    @EnumSource(value = ContractStatus.class, names = {"FULFILLED", "BROKEN", "CANCELLED", "EXPIRED", "INVALID"})
    void finalStatusesNeverChange(ContractStatus status) {
        assertEquals(Set.of(), allowedFrom(status));
    }

    @Test
    void aForbiddenTransitionThrows() {
        FormalContract proposed = FormalContract.proposed(1, 0, 0, 1, ResourceBundle.EMPTY, ResourceBundle.EMPTY, 1, 2);
        assertThrows(IllegalStateException.class, () -> proposed.movedTo(ContractStatus.FULFILLED));
        FormalContract broken = proposed.movedTo(ContractStatus.ACTIVE).movedTo(ContractStatus.BROKEN);
        assertThrows(IllegalStateException.class, () -> broken.movedTo(ContractStatus.CANCELLED));
    }
}
