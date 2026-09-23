package citytrade.engine;

import citytrade.engine.city.CityDevelopment;
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
import citytrade.engine.command.GameResult;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.ProposeContract;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.RejectTrade;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.SetCrisisPolicy;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.command.SignContract;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.command.UseEventOption;
import citytrade.engine.contract.Contracts;
import citytrade.engine.economy.UpkeepPriorityChoice;
import citytrade.engine.event.Crises;
import citytrade.engine.event.EventOptions;
import citytrade.engine.market.Market;
import citytrade.engine.opportunity.Opportunities;
import citytrade.engine.project.Projects;
import citytrade.engine.round.RoundFlow;
import citytrade.engine.round.RoundSteps;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.ObjectiveChoice;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.trade.Trading;

/**
 * The single entry point of the rules: old state + command -> new state and events, or a rejection
 * with the state unchanged. Pure function: no I/O, no clock, randomness only from {@link GameState#random()}.
 */
public final class GameEngine {

    private static final RoundFlow ROUND_FLOW = new RoundFlow(RoundSteps.INSTANCE);

    private GameEngine() {
    }

    public static GameResult apply(GameState state, GameCommand command, Ruleset ruleset) {
        return switch (command) {
            case ChooseObjectives choose -> state.phase() == GamePhase.SETUP
                    ? ObjectiveChoice.apply(state, choose, ruleset)
                    : invalidPhase(command, state);
            // Upkeep is paid in StartRound, so the order is set before Round 1 or in a window for later rounds.
            case SetUpkeepPriority priority -> state.phase() == GamePhase.SETUP || state.phase() == GamePhase.WINDOW
                    ? UpkeepPriorityChoice.apply(state, priority)
                    : invalidPhase(command, state);
            case BuyFromMarket buy -> state.phase() == GamePhase.WINDOW
                    ? Market.buy(state, buy, ruleset)
                    : invalidPhase(command, state);
            case SellToMarket sell -> state.phase() == GamePhase.WINDOW
                    ? Market.sell(state, sell, ruleset)
                    : invalidPhase(command, state);
            case UpgradeCity upgrade -> state.phase() == GamePhase.WINDOW
                    ? CityDevelopment.upgrade(state, upgrade, ruleset)
                    : invalidPhase(command, state);
            case BuildBuilding build -> state.phase() == GamePhase.WINDOW
                    ? CityDevelopment.build(state, build, ruleset)
                    : invalidPhase(command, state);
            case ProposeTrade propose -> state.phase() == GamePhase.WINDOW
                    ? Trading.propose(state, propose)
                    : invalidPhase(command, state);
            case AcceptTrade acceptTrade -> state.phase() == GamePhase.WINDOW
                    ? Trading.accept(state, acceptTrade)
                    : invalidPhase(command, state);
            case RejectTrade reject -> state.phase() == GamePhase.WINDOW
                    ? Trading.reject(state, reject)
                    : invalidPhase(command, state);
            case CancelTrade cancel -> state.phase() == GamePhase.WINDOW
                    ? Trading.cancel(state, cancel)
                    : invalidPhase(command, state);
            case CounterTrade counter -> state.phase() == GamePhase.WINDOW
                    ? Trading.counter(state, counter)
                    : invalidPhase(command, state);
            case ProposeContract propose -> state.phase() == GamePhase.WINDOW
                    ? Contracts.propose(state, propose, ruleset)
                    : invalidPhase(command, state);
            case SignContract sign -> state.phase() == GamePhase.WINDOW
                    ? Contracts.sign(state, sign)
                    : invalidPhase(command, state);
            case BreakContract breakContract -> state.phase() == GamePhase.WINDOW
                    ? Contracts.breakVoluntarily(state, breakContract, ruleset)
                    : invalidPhase(command, state);
            case CancelContractMutually cancel -> state.phase() == GamePhase.WINDOW
                    ? Contracts.cancelMutually(state, cancel)
                    : invalidPhase(command, state);
            case SetCrisisPolicy policy -> state.phase() == GamePhase.WINDOW
                    ? Crises.setPolicy(state, policy)
                    : invalidPhase(command, state);
            case UseEventOption option -> state.phase() == GamePhase.WINDOW
                    ? EventOptions.use(state, option)
                    : invalidPhase(command, state);
            case ContributeToProject contribute -> state.phase() == GamePhase.WINDOW
                    ? Projects.contribute(state, contribute, ruleset)
                    : invalidPhase(command, state);
            case PlaceBid bid -> state.phase() == GamePhase.WINDOW
                    ? Opportunities.placeBid(state, bid)
                    : invalidPhase(command, state);
            case StartRound _ -> ROUND_FLOW.startRound(state, ruleset);
            case ResolveRound _ -> ROUND_FLOW.resolveRound(state, ruleset);
        };
    }

    private static GameResult invalidPhase(GameCommand command, GameState state) {
        return new GameResult.Rejected(RejectionCode.INVALID_PHASE,
                command.getClass().getSimpleName() + " is not allowed in phase " + state.phase());
    }
}
