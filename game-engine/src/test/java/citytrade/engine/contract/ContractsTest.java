package citytrade.engine.contract;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.GameEngine;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.BreakContract;
import citytrade.engine.command.CancelContractMutually;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.ProposeContract;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.SignContract;
import citytrade.engine.command.StartRound;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.ContractRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.ContractStatus;
import citytrade.engine.state.FormalContract;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractsTest {

    private static final long SEED = 33;
    private static final int A = 0;
    private static final int B = 1;
    private static final int C = 2;
    private static final int FIRST_ID = 1;
    private static final ResourceBundle A_HOLDS = new ResourceBundle(0, 5, 0, 0, 10);
    private static final ResourceBundle B_HOLDS = new ResourceBundle(6, 0, 0, 0, 10);
    private static final ResourceBundle TWO_ENERGY = new ResourceBundle(0, 2, 0, 0, 0);
    private static final ResourceBundle THREE_FOOD = new ResourceBundle(3, 0, 0, 0, 0);

    private final Ruleset ruleset = TestRulesets.standard();

    /** Round 1 window; A (creditor in most tests) has Energy and Money, B (debtor) has Food and Money. */
    private GameState window() {
        GameState state = accept(readyForRoundOne(SEED, ruleset), new StartRound(), ruleset).state();
        state = withCity(state, A, 1, A_HOLDS);
        state = withCity(state, B, 1, B_HOLDS);
        state = withCity(state, C, 1, new ResourceBundle(0, 0, 0, 0, 10));
        return withCity(state, 3, 1, new ResourceBundle(0, 0, 0, 0, 10));
    }

    private GameState windowOfRound(int round) {
        return window().withRoundAndPhase(round, GamePhase.WINDOW);
    }

    private GameState apply(GameState state, GameCommand... commands) {
        for (GameCommand command : commands) {
            state = accept(state, command, ruleset).state();
        }
        return state;
    }

    /** A gives 2 Energy now, B owes 3 Food in {@code dueRound}; proposed by A. */
    private static ProposeContract energyForFood(int dueRound) {
        return new ProposeContract(A, A, B, TWO_ENERGY, THREE_FOOD, dueRound);
    }

    /** Contract 1 signed in the round 1 window: A gave 2 Energy, B owes 3 Food in round 2. */
    private GameState withActiveContract() {
        return apply(window(), energyForFood(2), new SignContract(B, FIRST_ID));
    }

    /** {@code state} at step 1.5 of {@code round}, with B holding {@code debtorHolds}. */
    private static GameState atObligationStep(GameState state, int round, ResourceBundle debtorHolds) {
        return withCity(state.withRoundAndPhase(round, GamePhase.AUTOMATIC), B, 1, debtorHolds);
    }

    private static FormalContract contract(GameState state, int id) {
        return state.contract(id).orElseThrow();
    }

    private static Ruleset withContracts(Ruleset base, ContractRules contracts) {
        return new Ruleset(base.version(), base.roundCount(), base.playerCount(), base.starting(), base.levels(),
                base.storage(), base.strained(), base.buildings(), base.market(), contracts, base.events(),
                base.objectives(), base.projects(), base.opportunities());
    }

    // --- propose -------------------------------------------------------------------------------------------------

    @Test
    void proposeCreatesAProposedContractAndMovesNothing() {
        GameState before = window();
        GameResult.Accepted result = accept(before, energyForFood(3), ruleset);

        assertEquals(FormalContract.proposed(FIRST_ID, A, A, B, TWO_ENERGY, THREE_FOOD, 1, 3),
                contract(result.state(), FIRST_ID));
        assertEquals(FIRST_ID + 1, result.state().nextContractId());
        assertEquals(before.players(), result.state().players());
        assertEquals(List.of(new DomainEvent.ContractProposed(FIRST_ID, A, A, B, TWO_ENERGY, THREE_FOOD, 3)),
                result.events());
    }

    @Test
    void contractIdsCountUpInCreationOrder() {
        GameState state = apply(window(), energyForFood(2), energyForFood(3),
                new ProposeContract(C, C, A, new ResourceBundle(0, 0, 0, 0, 1), TWO_ENERGY, 2));
        assertEquals(List.of(1, 2, 3), state.contracts().stream().map(FormalContract::id).toList());
        assertEquals(4, state.nextContractId());
    }

    @Test
    void theDebtorMayProposeAndTheCreditorsHoldingsAreNotCheckedYet() {
        // B cannot see A's holdings (D6), so the proposal must not reveal them.
        ResourceBundle moreThanAHas = new ResourceBundle(0, 9, 0, 0, 0);
        GameState state = apply(window(), new ProposeContract(B, A, B, moreThanAHas, THREE_FOOD, 2));
        assertEquals(ContractStatus.PROPOSED, contract(state, FIRST_ID).status());
        assertEquals(A, contract(state, FIRST_ID).signerSeat());
    }

    @Test
    void aCreditorWhoProposesMustOwnTheBundle() {
        assertRejected(window(), new ProposeContract(A, A, B, new ResourceBundle(0, 6, 0, 0, 0), THREE_FOOD, 2),
                ruleset, RejectionCode.INSUFFICIENT_RESOURCES);
        assertRejected(window(), new ProposeContract(A, A, B, new ResourceBundle(0, 0, 0, 0, 11), THREE_FOOD, 2),
                ruleset, RejectionCode.INSUFFICIENT_RESOURCES);
    }

    @Test
    void theDebtorsAbilityToPayLaterIsNotChecked() {
        GameState state = apply(window(), new ProposeContract(A, A, B, TWO_ENERGY, new ResourceBundle(0, 0, 9, 0, 0), 2));
        assertEquals(ContractStatus.PROPOSED, contract(state, FIRST_ID).status());
    }

    @Test
    void d10BothBundlesMustBeNonEmpty() {
        assertRejected(window(), new ProposeContract(A, A, B, ResourceBundle.EMPTY, THREE_FOOD, 2), ruleset,
                RejectionCode.EMPTY_CONTRACT);
        assertRejected(window(), new ProposeContract(A, A, B, TWO_ENERGY, ResourceBundle.EMPTY, 2), ruleset,
                RejectionCode.EMPTY_CONTRACT);
    }

    @Test
    void moneyMayBeGivenAndOwed() {
        ResourceBundle money = new ResourceBundle(0, 0, 0, 0, 4);
        GameState state = apply(window(), new ProposeContract(A, A, B, money, new ResourceBundle(1, 0, 0, 0, 5), 2));
        assertEquals(ContractStatus.PROPOSED, contract(state, FIRST_ID).status());
    }

    @Test
    void negativeAmountsAreRejected() {
        ResourceBundle negative = new ResourceBundle(0, 0, 0, 0, -1);
        assertRejected(window(), new ProposeContract(A, A, B, negative, THREE_FOOD, 2), ruleset,
                RejectionCode.INVALID_QUANTITY);
        assertRejected(window(), new ProposeContract(A, A, B, TWO_ENERGY, negative, 2), ruleset,
                RejectionCode.INVALID_QUANTITY);
    }

    @Test
    void anObligationWhoseCompensationDoesNotFitIsRejected() {
        int max = Integer.MAX_VALUE;
        // 2$ per unpaid unit: MAX Food, (MAX/2 + 1) Food, units that only overflow when summed, MAX Money.
        for (ResourceBundle tooLarge : List.of(new ResourceBundle(max, 0, 0, 0, 0),
                new ResourceBundle(max / 2 + 1, 0, 0, 0, 0), new ResourceBundle(max, 1, 0, 0, 0),
                new ResourceBundle(0, 0, 0, 0, max / 2 + 1), new ResourceBundle(max / 4 + 1, 0, 0, 0, max / 4 + 1))) {
            assertRejected(window(), new ProposeContract(A, A, B, TWO_ENERGY, tooLarge, 2), ruleset,
                    RejectionCode.INVALID_QUANTITY);
        }
    }

    @Test
    void theLargestObligationThatFitsIsSettledWithoutWrappingAround() {
        ResourceBundle hugeFood = new ResourceBundle(Integer.MAX_VALUE / 2, 0, 0, 0, 0);
        GameState state = apply(window(), new ProposeContract(A, A, B, TWO_ENERGY, hugeFood, 2),
                new SignContract(B, FIRST_ID));
        state = atObligationStep(state, 2, new ResourceBundle(0, 0, 0, 0, 3));
        ResourceBundle creditorBefore = state.player(A).holdings();
        List<DomainEvent> events = new ArrayList<>();
        GameState next = Contracts.settleDueObligations(state, ruleset, events);

        int owed = Integer.MAX_VALUE - 1;
        assertEquals(creditorBefore.plus(new ResourceBundle(0, 0, 0, 0, 3)), next.player(A).holdings());
        assertEquals(ResourceBundle.EMPTY, next.player(B).holdings());
        assertEquals(-(Math.ceilDiv(owed - 3, 2) + 1), next.player(B).prestige());
        assertEquals(List.of(new DomainEvent.ContractBroken(FIRST_ID, B, A, false, ResourceBundle.EMPTY, owed, 3,
                Math.ceilDiv(owed - 3, 2) + 1)), events);
    }

    @Test
    void aContractWithYourselfIsRejected() {
        assertRejected(window(), new ProposeContract(A, A, A, TWO_ENERGY, THREE_FOOD, 2), ruleset,
                RejectionCode.CONTRACT_WITH_SELF);
    }

    @Test
    void theProposerMustBeAParty() {
        assertRejected(window(), new ProposeContract(C, A, B, TWO_ENERGY, THREE_FOOD, 2), ruleset,
                RejectionCode.NOT_CONTRACT_PARTY);
    }

    @Test
    void unknownSeatsAreRejected() {
        assertRejected(window(), new ProposeContract(7, A, B, TWO_ENERGY, THREE_FOOD, 2), ruleset,
                RejectionCode.UNKNOWN_PLAYER);
        assertRejected(window(), new ProposeContract(A, A, -1, TWO_ENERGY, THREE_FOOD, 2), ruleset,
                RejectionCode.UNKNOWN_PLAYER);
        assertRejected(window(), new ProposeContract(A, 4, A, TWO_ENERGY, THREE_FOOD, 2), ruleset,
                RejectionCode.UNKNOWN_PLAYER);
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void dueRoundUpToMaxDurationIsAllowed(int dueRound) {
        GameState state = apply(window(), energyForFood(dueRound));
        assertEquals(dueRound, contract(state, FIRST_ID).dueRound());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 5, 6})
    void dueRoundOutsideTheMaxDurationIsRejected(int dueRound) {
        assertRejected(window(), energyForFood(dueRound), ruleset, RejectionCode.INVALID_DUE_ROUND);
    }

    @Test
    void maxDurationComesFromTheRuleset() {
        Ruleset shorter = withContracts(ruleset, new ContractRules(1, 2, 2, 2, 1));
        assertEquals(GameResult.Accepted.class, GameEngine.apply(window(), energyForFood(2), shorter).getClass());
        assertRejected(window(), energyForFood(3), shorter, RejectionCode.INVALID_DUE_ROUND);
    }

    @Test
    void noObligationMayBeDueAfterTheLastRound() {
        // Concept 16: Round 12 -> due 13 or 14; Round 13 -> due 14; Round 14 -> no contract at all.
        apply(windowOfRound(12), energyForFood(13), energyForFood(14));
        assertRejected(windowOfRound(12), energyForFood(15), ruleset, RejectionCode.CONTRACT_AFTER_GAME_END);
        apply(windowOfRound(13), energyForFood(14));
        assertRejected(windowOfRound(13), energyForFood(15), ruleset, RejectionCode.CONTRACT_AFTER_GAME_END);
        assertRejected(windowOfRound(13), energyForFood(16), ruleset, RejectionCode.CONTRACT_AFTER_GAME_END);
        assertRejected(windowOfRound(14), energyForFood(14), ruleset, RejectionCode.CONTRACT_AFTER_GAME_END);
        assertRejected(windowOfRound(14), energyForFood(15), ruleset, RejectionCode.CONTRACT_AFTER_GAME_END);
    }

    @Test
    void contractCommandsAreOnlyAllowedInTheWindow() {
        GameState setup = readyForRoundOne(SEED, ruleset);
        assertRejected(setup, energyForFood(2), ruleset, RejectionCode.INVALID_PHASE);
        GameState active = withActiveContract();
        GameState resolution = accept(active, new ResolveRound(), ruleset).state();
        assertRejected(resolution, energyForFood(3), ruleset, RejectionCode.INVALID_PHASE);
        assertRejected(resolution, new SignContract(B, FIRST_ID), ruleset, RejectionCode.INVALID_PHASE);
        assertRejected(resolution, new BreakContract(B, FIRST_ID), ruleset, RejectionCode.INVALID_PHASE);
        assertRejected(resolution, new CancelContractMutually(A, FIRST_ID), ruleset, RejectionCode.INVALID_PHASE);
    }

    // --- sign ----------------------------------------------------------------------------------------------------

    @Test
    void signingMovesTheGivenBundleToTheDebtorAtOnce() {
        GameState proposed = apply(window(), energyForFood(2));
        GameResult.Accepted result = accept(proposed, new SignContract(B, FIRST_ID), ruleset);

        assertEquals(A_HOLDS.minus(TWO_ENERGY), result.state().player(A).holdings());
        assertEquals(B_HOLDS.plus(TWO_ENERGY), result.state().player(B).holdings());
        assertEquals(ContractStatus.ACTIVE, contract(result.state(), FIRST_ID).status());
        assertEquals(List.of(new DomainEvent.ContractSigned(FIRST_ID, A, B, TWO_ENERGY)), result.events());
    }

    @Test
    void theCreditorSignsWhenTheDebtorProposed() {
        GameState state = apply(window(), new ProposeContract(B, A, B, TWO_ENERGY, THREE_FOOD, 2),
                new SignContract(A, FIRST_ID));
        assertEquals(ContractStatus.ACTIVE, contract(state, FIRST_ID).status());
        assertEquals(B_HOLDS.plus(TWO_ENERGY), state.player(B).holdings());
    }

    @Test
    void receivedResourcesAreUsableInTheSameWindow() {
        GameState state = withActiveContract();
        int energyValue = Market.sellValue(state, Resource.ENERGY, ruleset);
        state = apply(state, new SellToMarket(B, Resource.ENERGY, 2));
        assertEquals(B_HOLDS.withMoney(B_HOLDS.money() + 2 * energyValue), state.player(B).holdings());
    }

    @Test
    void onlyThePartnerMaySign() {
        GameState proposed = apply(window(), energyForFood(2));
        assertRejected(proposed, new SignContract(A, FIRST_ID), ruleset, RejectionCode.NOT_CONTRACT_SIGNER);
        assertRejected(proposed, new SignContract(C, FIRST_ID), ruleset, RejectionCode.NOT_CONTRACT_SIGNER);
    }

    @Test
    void signingAnUnknownContractOrASignedOneIsRejected() {
        assertRejected(window(), new SignContract(B, FIRST_ID), ruleset, RejectionCode.UNKNOWN_CONTRACT);
        assertRejected(withActiveContract(), new SignContract(B, FIRST_ID), ruleset,
                RejectionCode.CONTRACT_NOT_PROPOSED);
        assertRejected(window(), new SignContract(9, FIRST_ID), ruleset, RejectionCode.UNKNOWN_PLAYER);
    }

    @Test
    void signingInvalidatesTheContractWhenTheCreditorNoLongerHasTheBundle() {
        GameState state = apply(window(), energyForFood(2), new SellToMarket(A, Resource.ENERGY, 4));
        GameResult.Accepted result = accept(state, new SignContract(B, FIRST_ID), ruleset);

        assertEquals(ContractStatus.INVALID, contract(result.state(), FIRST_ID).status());
        assertEquals(state.players(), result.state().players(), "nothing moves");
        assertEquals(List.of(new DomainEvent.ContractInvalidated(FIRST_ID, A)), result.events());
        assertRejected(result.state(), new SignContract(B, FIRST_ID), ruleset, RejectionCode.CONTRACT_NOT_PROPOSED);
    }

    // --- expiry of unsigned proposals ----------------------------------------------------------------------------

    @Test
    void d16UnsignedProposalsExpireAtResolutionAndSignedOnesStayActive() {
        GameState state = apply(withActiveContract(), energyForFood(3));
        GameResult.Accepted result = accept(state, new ResolveRound(), ruleset);

        assertEquals(ContractStatus.ACTIVE, contract(result.state(), 1).status());
        assertEquals(ContractStatus.EXPIRED, contract(result.state(), 2).status());
        assertTrue(result.events().contains(new DomainEvent.ContractExpired(2)));
        GameState nextWindow = accept(result.state(), new StartRound(), ruleset).state();
        assertRejected(nextWindow, new SignContract(B, 2), ruleset, RejectionCode.CONTRACT_NOT_PROPOSED);
    }

    // --- step 1.5: obligations due -------------------------------------------------------------------------------

    @Test
    void d3AnObligationIsPaidAutomaticallyWhenTheDebtorHasIt() {
        GameState state = atObligationStep(withActiveContract(), 2, new ResourceBundle(5, 0, 0, 0, 1));
        PlayerState creditor = state.player(A);
        List<DomainEvent> events = new ArrayList<>();
        GameState next = Contracts.settleDueObligations(state, ruleset, events);

        assertEquals(new ResourceBundle(2, 0, 0, 0, 1), next.player(B).holdings());
        assertEquals(creditor.holdings().plus(THREE_FOOD), next.player(A).holdings());
        assertEquals(ContractStatus.FULFILLED, contract(next, FIRST_ID).status());
        assertEquals(0, next.player(B).contractsBroken());
        assertEquals(0, next.player(B).prestige());
        assertEquals(List.of(new DomainEvent.ContractFulfilled(FIRST_ID, B, A, THREE_FOOD)), events);
    }

    @Test
    void anObligationIsNotTouchedBeforeItsDueRound() {
        GameState state = apply(window(), energyForFood(3), new SignContract(B, FIRST_ID));
        GameState atRoundTwo = atObligationStep(state, 2, B_HOLDS);
        List<DomainEvent> events = new ArrayList<>();
        assertEquals(atRoundTwo, Contracts.settleDueObligations(atRoundTwo, ruleset, events));
        assertEquals(List.of(), events);
    }

    @Test
    void numbersSheetExample() {
        // Numbers Sheet 13: B promised 4 Food in Round 7, has only 1 Food and 3$.
        ResourceBundle fourFood = new ResourceBundle(4, 0, 0, 0, 0);
        GameState state = apply(windowOfRound(6), new ProposeContract(A, A, B, TWO_ENERGY, fourFood, 7),
                new SignContract(B, FIRST_ID));
        state = atObligationStep(state, 7, new ResourceBundle(1, 0, 0, 0, 3));
        state = state.withPlayer(state.player(B).withPrestige(5));
        ResourceBundle creditorBefore = state.player(A).holdings();
        List<DomainEvent> events = new ArrayList<>();
        GameState next = Contracts.settleDueObligations(state, ruleset, events);

        // 1 Food paid, 3 Food unpaid -> 6$ owed; 3$ paid, 3$ unpaid -> -2P; -1P for breaking.
        assertEquals(creditorBefore.plus(new ResourceBundle(1, 0, 0, 0, 3)), next.player(A).holdings());
        assertEquals(ResourceBundle.EMPTY, next.player(B).holdings());
        assertEquals(5 - 3, next.player(B).prestige());
        assertEquals(1, next.player(B).contractsBroken());
        assertEquals(0, next.player(A).prestige(), "the victim does not get the Prestige");
        assertEquals(ContractStatus.BROKEN, contract(next, FIRST_ID).status());
        assertEquals(List.of(new DomainEvent.ContractBroken(FIRST_ID, B, A, false,
                new ResourceBundle(1, 0, 0, 0, 0), 6, 3, 3)), events);
    }

    @Test
    void anUnpaidMoneyObligationCostsTwiceTheUnpaidMoney() {
        ResourceBundle fiveMoney = new ResourceBundle(0, 0, 0, 0, 5);
        GameState state = apply(window(), new ProposeContract(A, A, B, TWO_ENERGY, fiveMoney, 2),
                new SignContract(B, FIRST_ID));
        state = atObligationStep(state, 2, new ResourceBundle(1, 0, 0, 0, 2));
        List<DomainEvent> events = new ArrayList<>();
        GameState next = Contracts.settleDueObligations(state, ruleset, events);

        // 2$ delivered, 3$ unpaid -> 6$ compensation, nothing left to pay it -> -3P, -1P for breaking.
        assertEquals(new ResourceBundle(1, 0, 0, 0, 0), next.player(B).holdings());
        assertEquals(-4, next.player(B).prestige());
        assertEquals(List.of(new DomainEvent.ContractBroken(FIRST_ID, B, A, false,
                new ResourceBundle(0, 0, 0, 0, 2), 6, 0, 4)), events);
    }

    @Test
    void compensationPaidInFullCostsOnlyTheBreakPenalty() {
        GameState state = atObligationStep(withActiveContract(), 2, new ResourceBundle(0, 0, 0, 0, 10));
        List<DomainEvent> events = new ArrayList<>();
        GameState next = Contracts.settleDueObligations(state, ruleset, events);

        assertEquals(new ResourceBundle(0, 0, 0, 0, 4), next.player(B).holdings());
        assertEquals(-1, next.player(B).prestige());
        assertEquals(List.of(new DomainEvent.ContractBroken(FIRST_ID, B, A, false, ResourceBundle.EMPTY, 6, 6, 1)),
                events);
    }

    @Test
    void unpaidCompensationPrestigeIsRoundedUp() {
        // 3 Food unpaid -> 6$; B has 5$ -> 1$ unpaid -> 1P (rounded up) + 1P.
        GameState state = atObligationStep(withActiveContract(), 2, new ResourceBundle(0, 0, 0, 0, 5));
        GameState next = Contracts.settleDueObligations(state, ruleset, new ArrayList<>());
        assertEquals(-2, next.player(B).prestige());
        assertEquals(ResourceBundle.EMPTY, next.player(B).holdings());
    }

    @Test
    void d15ContractsOfOneDebtorAreSettledInCreationOrder() {
        ResourceBundle twoFood = new ResourceBundle(2, 0, 0, 0, 0);
        GameState state = apply(window(), new ProposeContract(A, A, B, TWO_ENERGY, twoFood, 2),
                new SignContract(B, 1), new ProposeContract(C, C, B, new ResourceBundle(0, 0, 0, 0, 1), twoFood, 2),
                new SignContract(B, 2));
        state = atObligationStep(state, 2, new ResourceBundle(3, 0, 0, 0, 0));
        GameState next = Contracts.settleDueObligations(state, ruleset, new ArrayList<>());

        assertEquals(ContractStatus.FULFILLED, contract(next, 1).status());
        assertEquals(ContractStatus.BROKEN, contract(next, 2).status());
        assertEquals(1, next.player(C).holdings().food(), "the second creditor gets what is left");
    }

    @Test
    void d15ALaterContractIsStillPaidInFullWhenAnOlderOneBreaks() {
        ResourceBundle oneMaterial = new ResourceBundle(0, 0, 1, 0, 0);
        GameState state = apply(window(), new ProposeContract(A, A, B, TWO_ENERGY, THREE_FOOD, 2),
                new SignContract(B, 1), new ProposeContract(C, C, B, new ResourceBundle(0, 0, 0, 0, 1), oneMaterial, 2),
                new SignContract(B, 2));
        state = atObligationStep(state, 2, new ResourceBundle(1, 0, 1, 0, 0));
        int creditorMaterials = state.player(C).holdings().materials();
        GameState next = Contracts.settleDueObligations(state, ruleset, new ArrayList<>());

        assertEquals(ContractStatus.BROKEN, contract(next, 1).status());
        assertEquals(ContractStatus.FULFILLED, contract(next, 2).status());
        assertEquals(creditorMaterials + 1, next.player(C).holdings().materials());
        assertEquals(ResourceBundle.EMPTY, next.player(B).holdings());
    }

    @Test
    void penaltiesComeFromTheRuleset() {
        Ruleset harsh = withContracts(ruleset, new ContractRules(3, 3, 1, 4, 2));
        GameState state = atObligationStep(withActiveContract(), 2, new ResourceBundle(1, 0, 0, 0, 2));
        List<DomainEvent> events = new ArrayList<>();
        GameState next = Contracts.settleDueObligations(state, harsh, events);

        // 2 Food unpaid x 3$ = 6$; 2$ paid, 4$ unpaid -> 1P per 4$ = 1P; +2P for breaking.
        assertEquals(-3, next.player(B).prestige());
        assertEquals(List.of(new DomainEvent.ContractBroken(FIRST_ID, B, A, false,
                new ResourceBundle(1, 0, 0, 0, 0), 6, 2, 3)), events);
    }

    @Test
    void step15RunsInStartRoundOfTheDueRound() {
        GameState active = withActiveContract();
        // The same state with the contract gone shows what production and upkeep alone do.
        GameState without = active.withContract(contract(active, FIRST_ID).movedTo(ContractStatus.CANCELLED));
        GameState resolved = accept(active, new ResolveRound(), ruleset).state();
        assertEquals(ContractStatus.ACTIVE, contract(resolved, FIRST_ID).status(), "not due in round 1");

        GameResult.Accepted result = accept(resolved, new StartRound(), ruleset);
        GameState expected = accept(accept(without, new ResolveRound(), ruleset).state(), new StartRound(), ruleset)
                .state();
        assertEquals(ContractStatus.FULFILLED, contract(result.state(), FIRST_ID).status());
        assertEquals(expected.player(A).holdings().plus(THREE_FOOD), result.state().player(A).holdings());
        assertEquals(expected.player(B).holdings().minus(THREE_FOOD), result.state().player(B).holdings());
        assertTrue(result.events().contains(new DomainEvent.ContractFulfilled(FIRST_ID, B, A, THREE_FOOD)));
    }

    @Test
    void aContractDueInTheLastRoundIsSettledAndNothingIsLeftAfterTheGame() {
        GameState state = window();
        for (int round = 1; round <= ruleset.roundCount(); round++) {
            if (round == 11) {
                state = apply(state, energyForFood(14), new SignContract(B, FIRST_ID));
            }
            state = accept(state, new ResolveRound(), ruleset).state();
            if (round < ruleset.roundCount()) {
                state = accept(state, new StartRound(), ruleset).state();
            }
            if (round == 12) {
                assertEquals(13, state.round());
                assertEquals(ContractStatus.ACTIVE, contract(state, FIRST_ID).status(), "still active in round 13");
            }
            if (round == 13) {
                assertEquals(14, state.round());
                assertTrue(contract(state, FIRST_ID).status() != ContractStatus.ACTIVE, "settled in round 14, 1.5");
            }
        }
        assertEquals(GamePhase.FINISHED, state.phase());
        ContractStatus settled = contract(state, FIRST_ID).status();
        assertTrue(settled == ContractStatus.FULFILLED || settled == ContractStatus.BROKEN, settled.toString());
    }

    // --- voluntary break -----------------------------------------------------------------------------------------

    @Test
    void theDebtorMayBreakOnPurposeAndDeliversNothing() {
        GameState state = withActiveContract();
        GameResult.Accepted result = accept(state, new BreakContract(B, FIRST_ID), ruleset);
        GameState next = result.state();

        // 3 Food unpaid -> 6$ compensation, B can pay it; -1P for breaking.
        assertEquals(state.player(B).holdings().minus(new ResourceBundle(0, 0, 0, 0, 6)), next.player(B).holdings());
        assertEquals(state.player(A).holdings().plus(new ResourceBundle(0, 0, 0, 0, 6)), next.player(A).holdings());
        assertEquals(-1, next.player(B).prestige());
        assertEquals(1, next.player(B).contractsBroken());
        assertEquals(ContractStatus.BROKEN, contract(next, FIRST_ID).status());
        assertEquals(List.of(new DomainEvent.ContractBroken(FIRST_ID, B, A, true, ResourceBundle.EMPTY, 6, 6, 1)),
                result.events());

        // Nothing is due any more in round 2.
        GameState due = atObligationStep(next, 2, B_HOLDS);
        assertEquals(due, Contracts.settleDueObligations(due, ruleset, new ArrayList<>()));
    }

    @Test
    void aVoluntaryBreakWithoutMoneyCostsPrestige() {
        GameState state = withCity(withActiveContract(), B, 1, new ResourceBundle(9, 0, 0, 0, 1));
        GameState next = apply(state, new BreakContract(B, FIRST_ID));
        // 6$ owed, 1$ paid, 5$ unpaid -> 3P (rounded up) + 1P. The Food is kept: nothing is delivered.
        assertEquals(new ResourceBundle(9, 0, 0, 0, 0), next.player(B).holdings());
        assertEquals(-4, next.player(B).prestige());
    }

    @Test
    void theContractsBrokenCounterAddsUp() {
        GameState state = apply(window(), energyForFood(2), new SignContract(B, 1), energyForFood(3),
                new SignContract(B, 2), new BreakContract(B, 1), new BreakContract(B, 2));
        assertEquals(2, state.player(B).contractsBroken());
        assertEquals(0, state.player(A).contractsBroken());
    }

    @Test
    void onlyTheDebtorMayBreak() {
        assertRejected(withActiveContract(), new BreakContract(A, FIRST_ID), ruleset,
                RejectionCode.NOT_CONTRACT_DEBTOR);
        assertRejected(withActiveContract(), new BreakContract(C, FIRST_ID), ruleset,
                RejectionCode.NOT_CONTRACT_DEBTOR);
    }

    @Test
    void onlyActiveContractsCanBeBroken() {
        assertRejected(window(), new BreakContract(B, FIRST_ID), ruleset, RejectionCode.UNKNOWN_CONTRACT);
        assertRejected(apply(window(), energyForFood(2)), new BreakContract(B, FIRST_ID), ruleset,
                RejectionCode.CONTRACT_NOT_ACTIVE);
        GameState broken = apply(withActiveContract(), new BreakContract(B, FIRST_ID));
        assertRejected(broken, new BreakContract(B, FIRST_ID), ruleset, RejectionCode.CONTRACT_NOT_ACTIVE);
    }

    // --- mutual cancellation -------------------------------------------------------------------------------------

    @Test
    void bothPartiesAgreeingCancelsWithoutPenalty() {
        GameState active = withActiveContract();
        GameResult.Accepted first = accept(active, new CancelContractMutually(B, FIRST_ID), ruleset);
        assertEquals(ContractStatus.ACTIVE, contract(first.state(), FIRST_ID).status());
        assertEquals(Optional.of(B), contract(first.state(), FIRST_ID).cancelRequestedBy());
        assertEquals(List.of(new DomainEvent.ContractCancelRequested(FIRST_ID, B)), first.events());

        GameResult.Accepted second = accept(first.state(), new CancelContractMutually(A, FIRST_ID), ruleset);
        assertEquals(ContractStatus.CANCELLED, contract(second.state(), FIRST_ID).status());
        assertEquals(active.players(), second.state().players(), "no penalty, nothing moves back");
        assertEquals(List.of(new DomainEvent.ContractCancelled(FIRST_ID)), second.events());

        GameState due = atObligationStep(second.state(), 2, B_HOLDS);
        assertEquals(due, Contracts.settleDueObligations(due, ruleset, new ArrayList<>()), "nothing is due");
    }

    @Test
    void theCreditorMayAskFirst() {
        GameState state = apply(withActiveContract(), new CancelContractMutually(A, FIRST_ID),
                new CancelContractMutually(B, FIRST_ID));
        assertEquals(ContractStatus.CANCELLED, contract(state, FIRST_ID).status());
    }

    @Test
    void oneSideAloneCannotCancel() {
        GameState asked = apply(withActiveContract(), new CancelContractMutually(B, FIRST_ID));
        assertRejected(asked, new CancelContractMutually(B, FIRST_ID), ruleset,
                RejectionCode.CANCEL_ALREADY_REQUESTED);
        assertRejected(asked, new CancelContractMutually(C, FIRST_ID), ruleset, RejectionCode.NOT_CONTRACT_PARTY);
    }

    @Test
    void anAgreementToCancelDoesNotStopTheObligationFromBeingDue() {
        GameState asked = apply(withActiveContract(), new CancelContractMutually(A, FIRST_ID));
        GameState due = atObligationStep(asked, 2, B_HOLDS);
        GameState next = Contracts.settleDueObligations(due, ruleset, new ArrayList<>());
        assertEquals(ContractStatus.FULFILLED, contract(next, FIRST_ID).status());
        assertRejected(next.withRoundAndPhase(2, GamePhase.WINDOW), new CancelContractMutually(B, FIRST_ID), ruleset,
                RejectionCode.CONTRACT_NOT_ACTIVE);
    }

    @Test
    void onlyActiveContractsCanBeCancelled() {
        assertRejected(window(), new CancelContractMutually(A, FIRST_ID), ruleset, RejectionCode.UNKNOWN_CONTRACT);
        assertRejected(apply(window(), energyForFood(2)), new CancelContractMutually(A, FIRST_ID), ruleset,
                RejectionCode.CONTRACT_NOT_ACTIVE);
    }
}
