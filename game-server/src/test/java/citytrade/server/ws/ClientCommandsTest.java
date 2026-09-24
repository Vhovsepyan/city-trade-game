package citytrade.server.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.UpgradeCity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** T22: payload parsing must reject unknown fields (in particular "seat") and unsupported commandTypes. */
class ClientCommandsTest {

    // Same configuration as GameWebSocketHandler's own mapper: strict about unknown fields.
    private static final ObjectMapper JSON =
            JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    @Test
    void buildsAPlayerCommandWithThePlaceholderSeatAlways0() {
        GameCommand command = ClientCommands.toCommand("BUY_FROM_MARKET",
                node("{\"resource\":\"FOOD\",\"quantity\":2}"), JSON);
        assertThat(command).isEqualTo(new BuyFromMarket(0, Resource.FOOD, 2));
    }

    @Test
    void parsesANestedResourceBundlePayload() {
        GameCommand command = ClientCommands.toCommand("PROPOSE_TRADE", node("""
                {"recipientSeat":1,
                 "offered":{"food":2,"energy":0,"materials":0,"technology":0,"money":0},
                 "requested":{"food":0,"energy":0,"materials":0,"technology":1,"money":0}}
                """), JSON);
        assertThat(command).isEqualTo(new ProposeTrade(0, 1,
                new ResourceBundle(2, 0, 0, 0, 0), new ResourceBundle(0, 0, 0, 1, 0)));
    }

    @Test
    void aCommandWithoutFieldsAcceptsAMissingPayload() {
        GameCommand command = ClientCommands.toCommand("UPGRADE_CITY", null, JSON);
        assertThat(command).isEqualTo(new UpgradeCity(0));
    }

    @Test
    void aPayloadWithASeatFieldIsRejected() {
        assertThatThrownBy(() -> ClientCommands.toCommand("BUY_FROM_MARKET",
                node("{\"resource\":\"FOOD\",\"quantity\":2,\"seat\":3}"), JSON))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void anUpgradeCityPayloadWithAnUnknownFieldIsRejected() {
        assertThatThrownBy(() -> ClientCommands.toCommand("UPGRADE_CITY", node("{\"seat\":1}"), JSON))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void anUnknownFieldInAnyPayloadIsRejected() {
        assertThatThrownBy(() -> ClientCommands.toCommand("CHOOSE_OBJECTIVES",
                node("{\"keptObjectiveIds\":[\"A\",\"B\"],\"extra\":true}"), JSON))
                .isInstanceOf(InvalidPayloadException.class);
    }

    @Test
    void anUnknownCommandTypeIsRejected() {
        assertThatThrownBy(() -> ClientCommands.toCommand("DO_SOMETHING_ELSE", node("{}"), JSON))
                .isInstanceOf(UnsupportedCommandTypeException.class);
    }

    @Test
    void chooseObjectivesParsesTheIdList() {
        GameCommand command = ClientCommands.toCommand("CHOOSE_OBJECTIVES",
                node("{\"keptObjectiveIds\":[\"A\",\"B\"]}"), JSON);
        assertThat(command).isEqualTo(new ChooseObjectives(0, java.util.List.of("A", "B")));
    }

    private static JsonNode node(String json) {
        return JSON.readTree(json);
    }
}
