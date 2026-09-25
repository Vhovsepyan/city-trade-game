package citytrade.server.persistence;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.AcceptTrade;
import citytrade.engine.command.BreakContract;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.CancelContractMutually;
import citytrade.engine.command.CancelTrade;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.ContributeToProject;
import citytrade.engine.command.CounterTrade;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.ProposeContract;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.RejectTrade;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.SetCrisisPolicy;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.command.SignContract;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.command.UseEventOption;
import citytrade.engine.state.CrisisPolicy;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a {@link GameCommand} into a {@code (commandType, payload)} pair for {@link MatchLog} storage
 * (Architecture 7.3 {@code match_commands.command_type} / {@code payload}), and back. Unlike
 * {@code citytrade.server.ws.ClientCommands} (which always injects a placeholder seat, since the real seat
 * comes from the WebSocket session), every payload here carries the command's own real seat: this is the
 * exact command {@link citytrade.server.game.GameRoom} already sent to the engine, and {@link ReplayService}
 * must be able to rebuild it byte-for-byte.
 */
final class MatchCommandCodec {

    private MatchCommandCodec() {
    }

    record EncodedCommand(String commandType, JsonNode payload) {
    }

    static EncodedCommand encode(GameCommand command, ObjectMapper mapper) {
        return switch (command) {
            case ChooseObjectives c ->
                    encode(mapper, "CHOOSE_OBJECTIVES", new ChooseObjectivesPayload(c.seat(), c.keptObjectiveIds()));
            case SetUpkeepPriority c ->
                    encode(mapper, "SET_UPKEEP_PRIORITY", new SetUpkeepPriorityPayload(c.seat(), c.order()));
            case BuyFromMarket c ->
                    encode(mapper, "BUY_FROM_MARKET", new MarketPayload(c.seat(), c.resource(), c.quantity()));
            case SellToMarket c ->
                    encode(mapper, "SELL_TO_MARKET", new MarketPayload(c.seat(), c.resource(), c.quantity()));
            case UpgradeCity c -> encode(mapper, "UPGRADE_CITY", new SeatPayload(c.seat()));
            case BuildBuilding c -> encode(mapper, "BUILD_BUILDING",
                    new BuildBuildingPayload(c.seat(), c.buildingId(), c.chosenResource().orElse(null)));
            case ProposeTrade c -> encode(mapper, "PROPOSE_TRADE",
                    new ProposeTradePayload(c.seat(), c.recipientSeat(), c.offered(), c.requested()));
            case AcceptTrade c -> encode(mapper, "ACCEPT_TRADE", new OfferIdPayload(c.seat(), c.offerId()));
            case RejectTrade c -> encode(mapper, "REJECT_TRADE", new OfferIdPayload(c.seat(), c.offerId()));
            case CancelTrade c -> encode(mapper, "CANCEL_TRADE", new OfferIdPayload(c.seat(), c.offerId()));
            case CounterTrade c -> encode(mapper, "COUNTER_TRADE",
                    new CounterTradePayload(c.seat(), c.offerId(), c.offered(), c.requested()));
            case ProposeContract c -> encode(mapper, "PROPOSE_CONTRACT", new ProposeContractPayload(c.seat(),
                    c.creditorSeat(), c.debtorSeat(), c.givenNow(), c.owed(), c.dueRound()));
            case SignContract c -> encode(mapper, "SIGN_CONTRACT", new ContractIdPayload(c.seat(), c.contractId()));
            case BreakContract c -> encode(mapper, "BREAK_CONTRACT", new ContractIdPayload(c.seat(), c.contractId()));
            case CancelContractMutually c ->
                    encode(mapper, "CANCEL_CONTRACT_MUTUALLY", new ContractIdPayload(c.seat(), c.contractId()));
            case SetCrisisPolicy c -> encode(mapper, "SET_CRISIS_POLICY", new CrisisPolicyPayload(c.seat(), c.policy()));
            case UseEventOption c -> encode(mapper, "USE_EVENT_OPTION",
                    new EventOptionPayload(c.seat(), c.chosenResource().orElse(null)));
            case ContributeToProject c -> encode(mapper, "CONTRIBUTE_TO_PROJECT",
                    new ContributeToProjectPayload(c.seat(), c.projectId(), c.contribution()));
            case PlaceBid c ->
                    encode(mapper, "PLACE_BID", new PlaceBidPayload(c.seat(), c.opportunityId(), c.amount()));
            case StartRound ignored -> encode(mapper, "START_ROUND", new EmptyPayload());
            case ResolveRound ignored -> encode(mapper, "RESOLVE_ROUND", new EmptyPayload());
        };
    }

    static GameCommand decode(String commandType, JsonNode payload, ObjectMapper mapper) {
        try {
            return build(commandType, payload, mapper);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid stored payload for " + commandType + ": " + e.getMessage(), e);
        }
    }

    private static GameCommand build(String commandType, JsonNode payload, ObjectMapper mapper) {
        return switch (commandType) {
            case "CHOOSE_OBJECTIVES" -> {
                var p = mapper.treeToValue(payload, ChooseObjectivesPayload.class);
                yield new ChooseObjectives(p.seat(), p.keptObjectiveIds());
            }
            case "SET_UPKEEP_PRIORITY" -> {
                var p = mapper.treeToValue(payload, SetUpkeepPriorityPayload.class);
                yield new SetUpkeepPriority(p.seat(), p.order());
            }
            case "BUY_FROM_MARKET" -> {
                var p = mapper.treeToValue(payload, MarketPayload.class);
                yield new BuyFromMarket(p.seat(), p.resource(), p.quantity());
            }
            case "SELL_TO_MARKET" -> {
                var p = mapper.treeToValue(payload, MarketPayload.class);
                yield new SellToMarket(p.seat(), p.resource(), p.quantity());
            }
            case "UPGRADE_CITY" -> {
                var p = mapper.treeToValue(payload, SeatPayload.class);
                yield new UpgradeCity(p.seat());
            }
            case "BUILD_BUILDING" -> {
                var p = mapper.treeToValue(payload, BuildBuildingPayload.class);
                yield new BuildBuilding(p.seat(), p.buildingId(), Optional.ofNullable(p.chosenResource()));
            }
            case "PROPOSE_TRADE" -> {
                var p = mapper.treeToValue(payload, ProposeTradePayload.class);
                yield new ProposeTrade(p.seat(), p.recipientSeat(), p.offered(), p.requested());
            }
            case "ACCEPT_TRADE" -> {
                var p = mapper.treeToValue(payload, OfferIdPayload.class);
                yield new AcceptTrade(p.seat(), p.offerId());
            }
            case "REJECT_TRADE" -> {
                var p = mapper.treeToValue(payload, OfferIdPayload.class);
                yield new RejectTrade(p.seat(), p.offerId());
            }
            case "CANCEL_TRADE" -> {
                var p = mapper.treeToValue(payload, OfferIdPayload.class);
                yield new CancelTrade(p.seat(), p.offerId());
            }
            case "COUNTER_TRADE" -> {
                var p = mapper.treeToValue(payload, CounterTradePayload.class);
                yield new CounterTrade(p.seat(), p.offerId(), p.offered(), p.requested());
            }
            case "PROPOSE_CONTRACT" -> {
                var p = mapper.treeToValue(payload, ProposeContractPayload.class);
                yield new ProposeContract(p.seat(), p.creditorSeat(), p.debtorSeat(), p.givenNow(), p.owed(),
                        p.dueRound());
            }
            case "SIGN_CONTRACT" -> {
                var p = mapper.treeToValue(payload, ContractIdPayload.class);
                yield new SignContract(p.seat(), p.contractId());
            }
            case "BREAK_CONTRACT" -> {
                var p = mapper.treeToValue(payload, ContractIdPayload.class);
                yield new BreakContract(p.seat(), p.contractId());
            }
            case "CANCEL_CONTRACT_MUTUALLY" -> {
                var p = mapper.treeToValue(payload, ContractIdPayload.class);
                yield new CancelContractMutually(p.seat(), p.contractId());
            }
            case "SET_CRISIS_POLICY" -> {
                var p = mapper.treeToValue(payload, CrisisPolicyPayload.class);
                yield new SetCrisisPolicy(p.seat(), p.policy());
            }
            case "USE_EVENT_OPTION" -> {
                var p = mapper.treeToValue(payload, EventOptionPayload.class);
                yield new UseEventOption(p.seat(), Optional.ofNullable(p.chosenResource()));
            }
            case "CONTRIBUTE_TO_PROJECT" -> {
                var p = mapper.treeToValue(payload, ContributeToProjectPayload.class);
                yield new ContributeToProject(p.seat(), p.projectId(), p.contribution());
            }
            case "PLACE_BID" -> {
                var p = mapper.treeToValue(payload, PlaceBidPayload.class);
                yield new PlaceBid(p.seat(), p.opportunityId(), p.amount());
            }
            case "START_ROUND" -> new StartRound();
            case "RESOLVE_ROUND" -> new ResolveRound();
            default -> throw new IllegalArgumentException("unknown stored commandType: " + commandType);
        };
    }

    private static EncodedCommand encode(ObjectMapper mapper, String commandType, Object payload) {
        return new EncodedCommand(commandType, mapper.valueToTree(payload));
    }

    record ChooseObjectivesPayload(int seat, List<String> keptObjectiveIds) {
    }

    record SetUpkeepPriorityPayload(int seat, List<Resource> order) {
    }

    record MarketPayload(int seat, Resource resource, int quantity) {
    }

    record SeatPayload(int seat) {
    }

    record BuildBuildingPayload(int seat, String buildingId, Resource chosenResource) {
    }

    record ProposeTradePayload(int seat, int recipientSeat, ResourceBundle offered, ResourceBundle requested) {
    }

    record OfferIdPayload(int seat, int offerId) {
    }

    record CounterTradePayload(int seat, int offerId, ResourceBundle offered, ResourceBundle requested) {
    }

    record ProposeContractPayload(int seat, int creditorSeat, int debtorSeat, ResourceBundle givenNow,
            ResourceBundle owed, int dueRound) {
    }

    record ContractIdPayload(int seat, int contractId) {
    }

    record CrisisPolicyPayload(int seat, CrisisPolicy policy) {
    }

    record EventOptionPayload(int seat, Resource chosenResource) {
    }

    record ContributeToProjectPayload(int seat, String projectId, ResourceBundle contribution) {
    }

    record PlaceBidPayload(int seat, String opportunityId, int amount) {
    }

    record EmptyPayload() {
    }
}
