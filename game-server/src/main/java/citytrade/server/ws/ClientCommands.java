package citytrade.server.ws;

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
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.SetCrisisPolicy;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.command.SignContract;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.command.UseEventOption;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Turns one {@code (commandType, payload)} pair into the matching engine {@link GameCommand} (Architecture
 * 6.9). Every command is built with seat 0: {@code GameRoom.submitPlayerCommand} always replaces it with the
 * caller's authenticated seat before the engine ever sees it (T20's seat-injection rule), so the placeholder
 * here is never trusted and a payload can never claim another seat by putting one in the JSON.
 */
final class ClientCommands {

    private static final int PLACEHOLDER_SEAT = 0;

    private ClientCommands() {
    }

    static GameCommand toCommand(String commandType, JsonNode payload, ObjectMapper mapper) {
        JsonNode node = payload == null ? emptyObject(mapper) : payload;
        try {
            return build(commandType, node, mapper);
        } catch (UnsupportedCommandTypeException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidPayloadException("invalid payload for " + commandType + ": " + e.getMessage(), e);
        }
    }

    private static GameCommand build(String commandType, JsonNode node, ObjectMapper mapper) {
        return switch (commandType) {
            case "CHOOSE_OBJECTIVES" -> {
                var p = mapper.treeToValue(node, CommandPayloads.ChooseObjectivesPayload.class);
                yield new ChooseObjectives(PLACEHOLDER_SEAT, p.keptObjectiveIds());
            }
            case "SET_UPKEEP_PRIORITY" -> {
                var p = mapper.treeToValue(node, CommandPayloads.SetUpkeepPriorityPayload.class);
                yield new SetUpkeepPriority(PLACEHOLDER_SEAT, p.order());
            }
            case "BUY_FROM_MARKET" -> {
                var p = mapper.treeToValue(node, CommandPayloads.MarketPayload.class);
                yield new BuyFromMarket(PLACEHOLDER_SEAT, p.resource(), p.quantity());
            }
            case "SELL_TO_MARKET" -> {
                var p = mapper.treeToValue(node, CommandPayloads.MarketPayload.class);
                yield new SellToMarket(PLACEHOLDER_SEAT, p.resource(), p.quantity());
            }
            case "UPGRADE_CITY" -> {
                mapper.treeToValue(node, CommandPayloads.EmptyPayload.class);
                yield new UpgradeCity(PLACEHOLDER_SEAT);
            }
            case "BUILD_BUILDING" -> {
                var p = mapper.treeToValue(node, CommandPayloads.BuildBuildingPayload.class);
                yield new BuildBuilding(PLACEHOLDER_SEAT, p.buildingId(), Optional.ofNullable(p.chosenResource()));
            }
            case "PROPOSE_TRADE" -> {
                var p = mapper.treeToValue(node, CommandPayloads.ProposeTradePayload.class);
                yield new ProposeTrade(PLACEHOLDER_SEAT, p.recipientSeat(), p.offered(), p.requested());
            }
            case "ACCEPT_TRADE" -> {
                var p = mapper.treeToValue(node, CommandPayloads.OfferIdPayload.class);
                yield new AcceptTrade(PLACEHOLDER_SEAT, p.offerId());
            }
            case "REJECT_TRADE" -> {
                var p = mapper.treeToValue(node, CommandPayloads.OfferIdPayload.class);
                yield new RejectTrade(PLACEHOLDER_SEAT, p.offerId());
            }
            case "CANCEL_TRADE" -> {
                var p = mapper.treeToValue(node, CommandPayloads.OfferIdPayload.class);
                yield new CancelTrade(PLACEHOLDER_SEAT, p.offerId());
            }
            case "COUNTER_TRADE" -> {
                var p = mapper.treeToValue(node, CommandPayloads.CounterTradePayload.class);
                yield new CounterTrade(PLACEHOLDER_SEAT, p.offerId(), p.offered(), p.requested());
            }
            case "PROPOSE_CONTRACT" -> {
                var p = mapper.treeToValue(node, CommandPayloads.ProposeContractPayload.class);
                yield new ProposeContract(PLACEHOLDER_SEAT, p.creditorSeat(), p.debtorSeat(), p.givenNow(),
                        p.owed(), p.dueRound());
            }
            case "SIGN_CONTRACT" -> {
                var p = mapper.treeToValue(node, CommandPayloads.ContractIdPayload.class);
                yield new SignContract(PLACEHOLDER_SEAT, p.contractId());
            }
            case "BREAK_CONTRACT" -> {
                var p = mapper.treeToValue(node, CommandPayloads.ContractIdPayload.class);
                yield new BreakContract(PLACEHOLDER_SEAT, p.contractId());
            }
            case "CANCEL_CONTRACT_MUTUALLY" -> {
                var p = mapper.treeToValue(node, CommandPayloads.ContractIdPayload.class);
                yield new CancelContractMutually(PLACEHOLDER_SEAT, p.contractId());
            }
            case "SET_CRISIS_POLICY" -> {
                var p = mapper.treeToValue(node, CommandPayloads.CrisisPolicyPayload.class);
                yield new SetCrisisPolicy(PLACEHOLDER_SEAT, p.policy());
            }
            case "USE_EVENT_OPTION" -> {
                var p = mapper.treeToValue(node, CommandPayloads.EventOptionPayload.class);
                yield new UseEventOption(PLACEHOLDER_SEAT, Optional.ofNullable(p.chosenResource()));
            }
            case "CONTRIBUTE_TO_PROJECT" -> {
                var p = mapper.treeToValue(node, CommandPayloads.ContributeToProjectPayload.class);
                yield new ContributeToProject(PLACEHOLDER_SEAT, p.projectId(), p.contribution());
            }
            case "PLACE_BID" -> {
                var p = mapper.treeToValue(node, CommandPayloads.PlaceBidPayload.class);
                yield new PlaceBid(PLACEHOLDER_SEAT, p.opportunityId(), p.amount());
            }
            default -> throw new UnsupportedCommandTypeException(commandType);
        };
    }

    private static ObjectNode emptyObject(ObjectMapper mapper) {
        return mapper.createObjectNode();
    }
}
