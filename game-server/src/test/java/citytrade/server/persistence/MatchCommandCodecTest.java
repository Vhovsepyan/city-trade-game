package citytrade.server.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every {@link GameCommand} type must round-trip through {@link MatchCommandCodec}: {@link ReplayService}
 * rebuilds the exact command {@code GameRoom} sent to the engine, seat included, from what was stored.
 */
class MatchCommandCodecTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @MethodSource("everyCommandType")
    void encodeThenDecodeReturnsAnEqualCommand(GameCommand original) {
        MatchCommandCodec.EncodedCommand encoded = MatchCommandCodec.encode(original, mapper);
        GameCommand decoded = MatchCommandCodec.decode(encoded.commandType(), encoded.payload(), mapper);

        assertThat(decoded).isEqualTo(original);
    }

    @ParameterizedTest
    @MethodSource("everyCommandType")
    void encodedPayloadSurvivesATextRoundTripLikeAJsonbColumnWould(GameCommand original) {
        MatchCommandCodec.EncodedCommand encoded = MatchCommandCodec.encode(original, mapper);
        String asText = mapper.writeValueAsString(encoded.payload());
        GameCommand decoded = MatchCommandCodec.decode(encoded.commandType(), mapper.readTree(asText), mapper);

        assertThat(decoded).isEqualTo(original);
    }

    @org.junit.jupiter.api.Test
    void unknownCommandTypeIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> MatchCommandCodec.decode("NOT_A_REAL_TYPE", mapper.createObjectNode(), mapper));
    }

    static Stream<GameCommand> everyCommandType() {
        ResourceBundle bundle = new ResourceBundle(1, 2, 3, 4, 5);
        return Stream.of(
                new ChooseObjectives(1, List.of("obj-1", "obj-2")),
                new SetUpkeepPriority(2, List.of(Resource.FOOD, Resource.ENERGY)),
                new BuyFromMarket(0, Resource.MATERIALS, 4),
                new SellToMarket(3, Resource.TECHNOLOGY, 2),
                new UpgradeCity(1),
                new BuildBuilding(2, "workshop", Optional.of(Resource.ENERGY)),
                new BuildBuilding(2, "granary", Optional.empty()),
                new ProposeTrade(0, 1, bundle, ResourceBundle.EMPTY),
                new AcceptTrade(1, 7),
                new RejectTrade(1, 7),
                new CancelTrade(0, 7),
                new CounterTrade(1, 7, bundle, ResourceBundle.EMPTY),
                new ProposeContract(0, 0, 1, bundle, ResourceBundle.EMPTY, 5),
                new SignContract(1, 3),
                new BreakContract(1, 3),
                new CancelContractMutually(0, 3),
                new SetCrisisPolicy(2, CrisisPolicy.SKIP),
                new UseEventOption(3, Optional.of(Resource.FOOD)),
                new UseEventOption(3, Optional.empty()),
                new ContributeToProject(0, "project-a", bundle),
                new PlaceBid(2, "opportunity-1", 9),
                new StartRound(),
                new ResolveRound());
    }
}
