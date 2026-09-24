package citytrade.server.ws;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.state.CrisisPolicy;
import java.util.List;

/**
 * One record per {@code commandType} payload shape ({@code docs/PROTOCOL.md}), deliberately without a
 * {@code seat} field: the seat always comes from the session (Architecture 6.6), so a payload containing one
 * is simply an unknown property and is rejected by strict Jackson parsing, like any other unknown field.
 */
final class CommandPayloads {

    private CommandPayloads() {
    }

    record ChooseObjectivesPayload(List<String> keptObjectiveIds) {
    }

    record SetUpkeepPriorityPayload(List<Resource> order) {
    }

    record MarketPayload(Resource resource, int quantity) {
    }

    record BuildBuildingPayload(String buildingId, Resource chosenResource) {
    }

    record ProposeTradePayload(int recipientSeat, ResourceBundle offered, ResourceBundle requested) {
    }

    record OfferIdPayload(int offerId) {
    }

    record CounterTradePayload(int offerId, ResourceBundle offered, ResourceBundle requested) {
    }

    record ProposeContractPayload(int creditorSeat, int debtorSeat, ResourceBundle givenNow, ResourceBundle owed,
            int dueRound) {
    }

    record ContractIdPayload(int contractId) {
    }

    record CrisisPolicyPayload(CrisisPolicy policy) {
    }

    record EventOptionPayload(Resource chosenResource) {
    }

    record ContributeToProjectPayload(String projectId, ResourceBundle contribution) {
    }

    record PlaceBidPayload(String opportunityId, int amount) {
    }

    record ReadyPayload(boolean ready) {
    }

    /** {@code UPGRADE_CITY} and {@code SNAPSHOT_REQUEST} take no fields; any field is rejected as unknown. */
    record EmptyPayload() {
    }
}
