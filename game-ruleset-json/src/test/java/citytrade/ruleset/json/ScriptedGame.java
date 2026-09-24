package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import citytrade.engine.CityType;
import citytrade.engine.GameEngine;
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
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
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
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.CrisisPolicy;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PublicProject;
import citytrade.engine.state.RegionalOpportunity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A fixed 14-round script for four players that uses every player command. Every command must be accepted.
 * Commands take ids (offers, contracts, projects, opportunities) from the current state instead of fixed numbers;
 * the log holds the concrete commands for replays. The amounts are tuned for {@link FullGameTest#SEED}.
 */
final class ScriptedGame {

    /** The played game: the final state, every command in order, and the events and the new state of each command. */
    record Played(GameState finalState, List<GameCommand> commands, List<List<DomainEvent>> events,
            List<GameState> states) {
    }

    private final Ruleset ruleset;
    private GameState state;
    private final List<GameCommand> commands = new ArrayList<>();
    private final List<List<DomainEvent>> events = new ArrayList<>();
    private final List<GameState> states = new ArrayList<>();

    private final int agri;
    private final int industry;
    private final int energy;
    private final int tech;

    private ScriptedGame(long seed, Ruleset ruleset) {
        this.ruleset = ruleset;
        this.state = GameSetup.create(seed, ruleset);
        this.agri = seatOf(CityType.AGRICULTURAL);
        this.industry = seatOf(CityType.INDUSTRIAL);
        this.energy = seatOf(CityType.ENERGY);
        this.tech = seatOf(CityType.TECHNOLOGY);
    }

    static Played play(long seed, Ruleset ruleset) {
        ScriptedGame game = new ScriptedGame(seed, ruleset);
        game.setup();
        for (int round = 1; round <= ruleset.roundCount(); round++) {
            game.run(new StartRound());
            game.window(round);
            game.run(new ResolveRound());
        }
        return game.played();
    }

    /** Plays {@code commands} from a new game with {@code seed}; every command must be accepted again. */
    static Played replay(long seed, Ruleset ruleset, List<GameCommand> commands) {
        ScriptedGame game = new ScriptedGame(seed, ruleset);
        commands.forEach(game::run);
        return game.played();
    }

    private Played played() {
        return new Played(state, List.copyOf(commands), List.copyOf(events), List.copyOf(states));
    }

    private void setup() {
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            var dealt = state.player(seat).dealtObjectives();
            run(new ChooseObjectives(seat, List.of(dealt.get(0).id(), dealt.get(1).id())));
        }
        run(new SetUpkeepPriority(tech, List.of(Resource.MATERIALS, Resource.ENERGY, Resource.FOOD)));
    }

    private void window(int round) {
        switch (round) {
            case 1 -> {
                swapSpecialties();
                // Offer life cycle: rejected, cancelled, countered + accepted, and one left open (expires at 4.1).
                int rejected = propose(industry, energy, bundle(0, 0, 1, 0, 0), bundle(0, 2, 0, 0, 0));
                run(new RejectTrade(energy, rejected));
                int cancelled = propose(tech, agri, bundle(0, 0, 0, 1, 0), bundle(1, 0, 0, 0, 0));
                run(new CancelTrade(tech, cancelled));
                int countered = propose(agri, tech, bundle(1, 0, 0, 0, 0), bundle(0, 0, 0, 2, 0));
                run(new CounterTrade(tech, countered, bundle(0, 0, 0, 1, 0), bundle(1, 0, 0, 0, 1)));
                run(new AcceptTrade(agri, state.nextOfferId() - 1));
                propose(energy, industry, bundle(0, 1, 0, 0, 0), bundle(0, 0, 1, 0, 0));
                run(new SellToMarket(agri, Resource.FOOD, 1));
                run(new BuyFromMarket(energy, Resource.FOOD, 1));
            }
            case 2 -> {
                run(new BuildBuilding(industry, "WORKSHOP", Optional.of(Resource.TECHNOLOGY)));
                run(new BuildBuilding(agri, "WAREHOUSE", Optional.empty()));
            }
            case 3 -> {
                upgradeAll();
                // A contract that is paid when due (step 1.5 of round 5).
                int fulfilled = proposeContract(agri, agri, industry, bundle(2, 0, 0, 0, 0), bundle(0, 0, 2, 0, 0), 5);
                run(new SignContract(industry, fulfilled));
                contribute(round);
                bid(round, energy, 2);
            }
            case 4 -> {
                swapSpecialties();
                // A contract the debtor breaks on purpose (compensation in Money, Prestige for the unpaid part).
                int broken = proposeContract(tech, tech, energy, bundle(0, 0, 0, 1, 0), bundle(0, 2, 0, 0, 0), 6);
                run(new SignContract(energy, broken));
                run(new BreakContract(energy, broken));
                // A contract both parties cancel.
                int cancelled = proposeContract(industry, industry, tech, bundle(0, 0, 1, 0, 0), bundle(0, 0, 0, 1, 0), 6);
                run(new SignContract(tech, cancelled));
                run(new CancelContractMutually(tech, cancelled));
                run(new CancelContractMutually(industry, cancelled));
                // A proposal nobody signs (expires at 4.1).
                proposeContract(agri, agri, energy, bundle(1, 0, 0, 0, 0), bundle(0, 1, 0, 0, 0), 6);
                contribute(round);
                crisisPolicyIfCrisisWarned(industry, CrisisPolicy.SKIP);
            }
            case 5 -> {
                swapSpecialties();
                contribute(round);
                // A tie: nobody wins, nobody pays, the card stays open.
                bid(round, agri, 1);
                bid(round, tech, 1);
            }
            case 6 -> {
                swapSpecialties();
                contribute(round);
                // The tied card from round 5 is won now; the losing bid is withdrawn with 0.
                bidOnOpenFrom(5, agri, 2);
                bidOnOpenFrom(5, tech, 0);
                build(tech, "SPECIALTY_COMPLEX");
            }
            case 7 -> {
                swapSpecialties();
                // Level 3 for the Technology city (Grand Landmark in round 11); missing Materials come from the market.
                run(new BuyFromMarket(tech, Resource.MATERIALS, 3));
                run(new UpgradeCity(tech));
                contribute(round);
                // A contract the debtor cannot pay when due (more Materials than the storage limit allows):
                // it breaks automatically in step 1.5 of round 9.
                int unpaid = proposeContract(agri, agri, tech, bundle(1, 0, 0, 0, 0), bundle(0, 0, 11, 0, 0), 9);
                run(new SignContract(tech, unpaid));
            }
            case 8 -> {
                swapSpecialties();
                contribute(round);
                run(new BuyFromMarket(industry, Resource.ENERGY, 2));
                build(energy, "CIVIC_CENTER");
            }
            case 9 -> {
                swapSpecialties();
                bid(round, industry, 3);
                build(agri, "RESEARCH_LAB");
            }
            case 10 -> {
                swapSpecialties();
                build(industry, "TRANSIT_NETWORK");
            }
            case 11 -> {
                swapSpecialties();
                build(tech, "GRAND_LANDMARK");
            }
            case 12 -> {
                swapSpecialties();
                run(new SellToMarket(energy, Resource.ENERGY, 3));
            }
            case 13 -> swapSpecialties();
            case 14 -> run(new SellToMarket(industry, Resource.MATERIALS, 2));
            default -> throw new IllegalStateException("no script for round " + round);
        }
        useEventOption();
    }

    /** Every city gives one unit of its specialty to each other city for one unit of theirs (six trades). */
    private void swapSpecialties() {
        int[] seats = {agri, industry, energy, tech};
        for (int i = 0; i < seats.length; i++) {
            for (int j = i + 1; j < seats.length; j++) {
                Resource given = state.player(seats[i]).city().specialty();
                Resource wanted = state.player(seats[j]).city().specialty();
                int offer = propose(seats[i], seats[j], ResourceBundle.EMPTY.with(given, 1),
                        ResourceBundle.EMPTY.with(wanted, 1));
                run(new AcceptTrade(seats[j], offer));
            }
        }
    }

    private void upgradeAll() {
        for (int seat : new int[] {agri, industry, energy, tech}) {
            run(new UpgradeCity(seat));
        }
    }

    private void build(int seat, String buildingId) {
        run(new BuildBuilding(seat, buildingId, Optional.empty()));
    }

    /** Each city gives 1 of every resource the open project still needs (in seat order), if it has it. */
    private void contribute(int round) {
        PublicProject project = state.projects().stream()
                .filter(p -> p.window().openRound() <= round && round <= p.window().deadlineRound())
                .findFirst().orElseThrow();
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            ResourceBundle need = state.project(project.id()).orElseThrow().remainingNeed();
            ResourceBundle give = ResourceBundle.EMPTY;
            for (Resource resource : Resource.values()) {
                if (need.amountOf(resource) > 0 && state.spendableHoldings(seat).amountOf(resource) > 0) {
                    give = give.with(resource, 1);
                }
            }
            int money = Math.min(Math.min(need.money(), 2), state.spendableHoldings(seat).money());
            give = give.withMoney(money);
            if (!give.isEmpty()) {
                run(new ContributeToProject(seat, project.id(), give));
            }
        }
    }

    /** A bid on the opportunity revealed in this round. */
    private void bid(int round, int seat, int amount) {
        bidOnOpenFrom(round, seat, amount);
    }

    private void bidOnOpenFrom(int appearedRound, int seat, int amount) {
        RegionalOpportunity opportunity = state.opportunities().stream()
                .filter(o -> o.appearedRound() == appearedRound).findFirst().orElseThrow();
        run(new PlaceBid(seat, opportunity.id(), amount));
    }

    /** The policy can only be set while a crisis is warned (D2). */
    private void crisisPolicyIfCrisisWarned(int seat, CrisisPolicy policy) {
        Optional<EventCard> warned = state.eventWarning().map(warning -> warning.card());
        if (warned.isPresent() && (warned.get() instanceof EventCard.ResourceCrisis
                || warned.get() instanceof EventCard.NonSpecialtyCrisis)) {
            run(new SetCrisisPolicy(seat, policy));
        }
    }

    /** Festival or Breakthrough: the Agricultural city uses the optional action when the card is active. */
    private void useEventOption() {
        Optional<EventCard> active = state.activeEvent();
        if (active.isPresent() && active.get() instanceof EventCard.PrestigePurchase) {
            run(new UseEventOption(agri, Optional.empty()));
        }
        if (active.isPresent() && active.get() instanceof EventCard.ProductionPurchase) {
            run(new UseEventOption(tech, Optional.of(Resource.FOOD)));
        }
    }

    private int propose(int from, int to, ResourceBundle offered, ResourceBundle requested) {
        int id = state.nextOfferId();
        run(new ProposeTrade(from, to, offered, requested));
        return id;
    }

    private int proposeContract(int seat, int creditor, int debtor, ResourceBundle givenNow, ResourceBundle owed,
            int dueRound) {
        int id = state.nextContractId();
        run(new ProposeContract(seat, creditor, debtor, givenNow, owed, dueRound));
        return id;
    }

    private void run(GameCommand command) {
        GameResult result = GameEngine.apply(state, command, ruleset);
        GameResult.Accepted accepted = assertInstanceOf(GameResult.Accepted.class, result,
                () -> "round " + state.round() + " " + state.phase() + ": " + command + " -> " + result + " holdings "
                        + state.players().stream().map(p -> p.city() + "=" + p.holdings()).toList());
        state = accepted.state();
        commands.add(command);
        events.add(accepted.events());
        states.add(state);
    }

    private int seatOf(CityType city) {
        return state.players().stream().filter(p -> p.city() == city).findFirst().orElseThrow().seat();
    }

    private static ResourceBundle bundle(int food, int energy, int materials, int technology, int money) {
        return new ResourceBundle(food, energy, materials, technology, money);
    }
}
