package citytrade.engine.objective;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SellToMarket;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.BuiltBuilding;
import citytrade.engine.state.ContractStatus;
import citytrade.engine.state.FormalContract;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.ProjectContribution;
import citytrade.engine.state.PublicProject;
import citytrade.engine.state.RegionalOpportunity;
import citytrade.engine.state.TradeOffer;
import citytrade.engine.state.TradeOfferStatus;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Numbers Sheet 15: every hidden objective has a pass and a fail case. */
class ObjectivesTest {

    private static final long SEED = 13;
    private static final ResourceBundle PLENTY = new ResourceBundle(9, 9, 9, 9, 9);

    private final Ruleset ruleset = TestRulesets.standard();

    private boolean completed(ObjectiveCard card, GameState state, int seat) {
        return Objectives.isCompleted(card, state, seat, ruleset);
    }

    private GameState start() {
        return readyForRoundOne(SEED, ruleset);
    }

    /** From any state before a StartRound: play on until the window of {@code round} is open. */
    private GameState windowOf(GameState state, int round) {
        GameState s = accept(state, new StartRound(), ruleset).state();
        while (s.round() < round) {
            s = accept(accept(s, new ResolveRound(), ruleset).state(), new StartRound(), ruleset).state();
        }
        return s;
    }

    private GameState resolve(GameState window) {
        return accept(window, new ResolveRound(), ruleset).state();
    }

    private static GameState withTrade(GameState state, int proposer, int recipient, ResourceBundle offered,
            ResourceBundle requested, TradeOfferStatus status) {
        TradeOffer offer = TradeOffer.open(state.nextOfferId(), Optional.empty(), proposer, recipient, offered,
                requested, 1);
        GameState next = state.withNewTradeOffer(offer);
        return status == TradeOfferStatus.OPEN ? next : next.withTradeOffer(offer.closedAs(status, Optional.empty()));
    }

    private static GameState withContract(GameState state, int creditor, int debtor, ContractStatus... path) {
        FormalContract contract = FormalContract.proposed(state.nextContractId(), creditor, creditor, debtor,
                new ResourceBundle(1, 0, 0, 0, 0), new ResourceBundle(0, 1, 0, 0, 0), 2, 3);
        for (ContractStatus status : path) {
            contract = contract.movedTo(status);
        }
        return state.withNewContract(contract);
    }

    private static GameState withContribution(GameState state, int projectIndex, int seat, int round,
            ResourceBundle given) {
        PublicProject project = state.projects().get(projectIndex);
        return state.withProject(project.withContribution(new ProjectContribution(seat, round, given)));
    }

    private static GameState update(GameState state, int seat, UnaryOperator<PlayerState> change) {
        return state.withPlayer(change.apply(state.player(seat)));
    }

    private static final ResourceBundle ONE_FOOD = new ResourceBundle(1, 0, 0, 0, 0);

    @Nested
    class ActiveTrader {
        private final ObjectiveCard card = new ObjectiveCard.ActiveTrader("ACTIVE_TRADER");

        @Test
        void passesWithACompletedTradeWithEachOpponentInEitherRole() {
            GameState s = withTrade(start(), 0, 1, ONE_FOOD, ONE_FOOD, TradeOfferStatus.ACCEPTED);
            s = withTrade(s, 2, 0, ONE_FOOD, ONE_FOOD, TradeOfferStatus.ACCEPTED);
            s = withTrade(s, 3, 0, ONE_FOOD, ONE_FOOD, TradeOfferStatus.ACCEPTED);

            assertTrue(completed(card, s, 0));
            assertFalse(completed(card, s, 1), "seat 1 traded only with seat 0");
        }

        @Test
        void failsWhenTheTradeWithOneOpponentWasNeverCarriedOut() {
            GameState s = withTrade(start(), 0, 1, ONE_FOOD, ONE_FOOD, TradeOfferStatus.ACCEPTED);
            s = withTrade(s, 0, 2, ONE_FOOD, ONE_FOOD, TradeOfferStatus.ACCEPTED);
            s = withTrade(s, 0, 3, ONE_FOOD, ONE_FOOD, TradeOfferStatus.REJECTED);
            s = withTrade(s, 3, 0, ONE_FOOD, ONE_FOOD, TradeOfferStatus.INVALID);
            s = withTrade(s, 0, 3, ONE_FOOD, ONE_FOOD, TradeOfferStatus.OPEN);

            assertFalse(completed(card, s, 0));
        }
    }

    @Nested
    class DiversifiedEconomy {
        private final ObjectiveCard card = new ObjectiveCard.DiversifiedEconomy("DIVERSIFIED_ECONOMY", 4);

        private GameState withBuildings(String... ids) {
            GameState s = start();
            for (String id : ids) {
                s = update(s, 0, p -> p.withBuilding(new BuiltBuilding(id, 2, Optional.empty())));
            }
            return s;
        }

        @Test
        void passesWithFourDifferentBuildings() {
            assertTrue(completed(card, withBuildings("WAREHOUSE", "WORKSHOP", "MARKET_HALL", "RESEARCH_LAB"), 0));
        }

        @Test
        void failsWithThreeBuildings() {
            assertFalse(completed(card, withBuildings("WAREHOUSE", "WORKSHOP", "MARKET_HALL"), 0));
        }
    }

    @Nested
    class ProjectPartner {
        private final ObjectiveCard card = new ObjectiveCard.ProjectPartner("PROJECT_PARTNER");

        @Test
        void passesWhenQualifyingInBothProjects() {
            // 3 resources = 6 points = the qualifying minimum; contributions to one project add up over rounds.
            GameState s = withContribution(start(), 0, 0, 3, new ResourceBundle(0, 2, 0, 0, 0));
            s = withContribution(s, 0, 0, 4, new ResourceBundle(0, 1, 0, 0, 0));
            s = withContribution(s, 1, 0, 7, new ResourceBundle(0, 0, 0, 0, 6));

            assertTrue(completed(card, s, 0));
        }

        @Test
        void failsWhenBelowTheMinimumInOneProject() {
            GameState s = withContribution(start(), 0, 0, 3, new ResourceBundle(0, 3, 0, 0, 0));
            s = withContribution(s, 1, 0, 7, new ResourceBundle(0, 2, 0, 0, 1));

            assertFalse(completed(card, s, 0), "5 points in Project B");
            assertFalse(completed(card, withContribution(start(), 0, 0, 3, new ResourceBundle(0, 3, 0, 0, 0)), 0),
                    "no contribution to Project B");
        }
    }

    @Nested
    class ContractPlayer {
        private final ObjectiveCard card = new ObjectiveCard.ContractPlayer("CONTRACT_PLAYER", 2);

        @Test
        void passesWithTwoFulfilledContractsAsEitherParty() {
            GameState s = withContract(start(), 0, 1, ContractStatus.ACTIVE, ContractStatus.FULFILLED);
            s = withContract(s, 2, 0, ContractStatus.ACTIVE, ContractStatus.FULFILLED);

            assertTrue(completed(card, s, 0));
        }

        @Test
        void failsWithOneFulfilledContract() {
            GameState s = withContract(start(), 0, 1, ContractStatus.ACTIVE, ContractStatus.FULFILLED);
            s = withContract(s, 2, 0, ContractStatus.ACTIVE, ContractStatus.CANCELLED);
            s = withContract(s, 0, 3, ContractStatus.ACTIVE);
            s = withContract(s, 0, 3, ContractStatus.EXPIRED);

            assertFalse(completed(card, s, 0));
        }

        @Test
        void failsAfterBreakingAContractEvenWithTwoFulfilled() {
            GameState s = withContract(start(), 0, 1, ContractStatus.ACTIVE, ContractStatus.FULFILLED);
            s = withContract(s, 2, 0, ContractStatus.ACTIVE, ContractStatus.FULFILLED);
            s = withContract(s, 3, 0, ContractStatus.ACTIVE, ContractStatus.BROKEN);
            s = update(s, 0, p -> p.withContractsBroken(1));
            s = withContract(s, 3, 1, ContractStatus.ACTIVE, ContractStatus.FULFILLED);
            s = withContract(s, 1, 3, ContractStatus.ACTIVE, ContractStatus.FULFILLED);

            assertFalse(completed(card, s, 0));
            assertTrue(completed(card, s, 3), "the creditor of the broken contract did not break it");
        }
    }

    @Nested
    class MarketIndependence {
        private final ObjectiveCard card = new ObjectiveCard.MarketIndependence("MARKET_INDEPENDENCE", 3);

        /** Plays rounds 1 to 5; seat 0 buys one Food in each of {@code buyRounds} (twice in each, to count once). */
        private GameState playBuyingIn(List<Integer> buyRounds) {
            GameState s = start();
            for (int round = 1; round <= 5; round++) {
                s = windowOf(s, round);
                s = withCity(s, 0, 1, PLENTY);
                if (buyRounds.contains(round)) {
                    s = accept(s, new BuyFromMarket(0, Resource.FOOD, 1), ruleset).state();
                    s = accept(s, new BuyFromMarket(0, Resource.ENERGY, 1), ruleset).state();
                }
                s = accept(s, new SellToMarket(0, Resource.MATERIALS, 1), ruleset).state();
                s = resolve(s);
            }
            return s;
        }

        @Test
        void passesWhenBuyingInThreeRoundsOnly() {
            GameState s = playBuyingIn(List.of(1, 3, 5));

            assertEquals(List.of(1, 3, 5), s.player(0).objectiveProgress().marketBuyRounds());
            assertTrue(completed(card, s, 0));
            assertTrue(completed(card, s, 1), "never buying (selling is allowed) passes");
        }

        @Test
        void failsWhenBuyingInFourRounds() {
            assertFalse(completed(card, playBuyingIn(List.of(1, 2, 3, 5)), 0));
        }
    }

    @Nested
    class RapidDevelopment {
        private final ObjectiveCard card = new ObjectiveCard.RapidDevelopment("RAPID_DEVELOPMENT", 3, 7);

        /** Seat 0 upgrades in {@code firstUpgrade} and {@code secondUpgrade}; play ends after round 8. */
        private GameState upgradeIn(int firstUpgrade, int secondUpgrade) {
            GameState s = start();
            for (int round = 1; round <= 8; round++) {
                s = windowOf(s, round);
                if (round == firstUpgrade || round == secondUpgrade) {
                    s = withCity(s, 0, s.player(0).level(), PLENTY);
                    s = accept(s, new UpgradeCity(0), ruleset).state();
                }
                s = resolve(s);
            }
            return s;
        }

        @Test
        void passesWhenLevelThreeIsReachedInRoundSeven() {
            GameState s = upgradeIn(2, 7);

            assertEquals(List.of(1, 2, 2, 2, 2, 2, 3, 3), s.player(0).objectiveProgress().levelAfterRound());
            assertTrue(completed(card, s, 0));
        }

        @Test
        void failsWhenLevelThreeIsReachedInRoundEight() {
            GameState s = upgradeIn(2, 8);

            assertEquals(3, s.player(0).level());
            assertFalse(completed(card, s, 0));
        }

        @Test
        void failsBeforeRoundSevenHasEnded() {
            GameState s = windowOf(start(), 7);
            s = update(s, 0, p -> p.withLevel(3, 7));

            assertFalse(completed(card, s, 0), "round 7 is not resolved yet");
            assertTrue(completed(card, resolve(s), 0));
        }
    }

    @Nested
    class SteadyCity {
        private final ObjectiveCard card = new ObjectiveCard.SteadyCity("STEADY_CITY");

        @Test
        void passesWhenNeverStrained() {
            assertTrue(completed(card, resolve(windowOf(start(), 3)), 0));
        }

        @Test
        void failsAfterBeingStrainedOnceEvenWhenTheFlagIsClearedLater() {
            GameState s = resolve(windowOf(start(), 1));
            s = update(s, 0, p -> p.withStrained(true, false));

            GameState later = resolve(windowOf(s, 3));

            assertFalse(later.player(0).strained(), "step 1.2 cleared the flag");
            assertFalse(completed(card, later, 0));
            assertTrue(completed(card, later, 1));
        }
    }

    @Nested
    class OpportunityWinner {
        private final ObjectiveCard card = new ObjectiveCard.OpportunityWinner("OPPORTUNITY_WINNER", 1);

        private GameState revealTop(GameState state, Optional<Integer> winner) {
            RegionalOpportunity revealed = RegionalOpportunity.revealed(state.opportunityDeck().getFirst(), 3);
            GameState next = state.withRevealedOpportunity(revealed);
            return winner.map(seat -> next.withOpportunity(revealed.wonBy(seat))).orElse(next);
        }

        @Test
        void passesAfterWinningABid() {
            assertTrue(completed(card, revealTop(start(), Optional.of(0)), 0));
        }

        @Test
        void failsWhenOthersWinOrNobodyWins() {
            GameState s = revealTop(revealTop(start(), Optional.of(1)), Optional.empty());

            assertFalse(completed(card, s, 0));
        }
    }

    @Nested
    class CrisisResponder {
        private final ObjectiveCard card = new ObjectiveCard.CrisisResponder("CRISIS_RESPONDER", 3);

        private GameState paidCrisesIn(int... rounds) {
            GameState s = start();
            for (int round : rounds) {
                s = update(s, 0, p -> p.withEventParticipation(p.eventParticipation().withCrisisPaid(round)));
            }
            return s;
        }

        @Test
        void passesAfterThreePaidCrises() {
            assertTrue(completed(card, paidCrisesIn(2, 5, 9), 0));
        }

        @Test
        void failsAfterTwoPaidCrises() {
            assertFalse(completed(card, paidCrisesIn(2, 5), 0));
        }
    }

    @Nested
    class BalancedStock {
        private final ObjectiveCard card = new ObjectiveCard.BalancedStock("BALANCED_STOCK", 5);

        @Test
        void passesAfterOneRoundResolutionWithFiveOfEachAndStaysPassed() {
            GameState s = withCity(windowOf(start(), 2), 0, 1, new ResourceBundle(5, 5, 5, 5, 0));
            s = resolve(s);
            assertTrue(completed(card, s, 0));

            GameState later = withCity(windowOf(s, 3), 0, 1, ResourceBundle.EMPTY);
            assertTrue(completed(card, resolve(later), 0), "one Round Resolution is enough");
        }

        @Test
        void failsWithOneResourceBelowFive() {
            GameState s = withCity(windowOf(start(), 2), 0, 1, new ResourceBundle(9, 9, 9, 4, 30));

            assertFalse(completed(card, resolve(s), 0), "Money does not count");
        }

        @Test
        void failsWhenTheStockIsHeldOnlyDuringTheWindow() {
            GameState s = withCity(windowOf(start(), 2), 0, 1, new ResourceBundle(5, 5, 5, 5, 0));
            assertFalse(completed(card, s, 0), "the window is not a Round Resolution");

            s = accept(s, new SellToMarket(0, Resource.FOOD, 1), ruleset).state();
            assertFalse(completed(card, resolve(s), 0));
        }
    }

    @Nested
    class PatientInvestor {
        private final ObjectiveCard card = new ObjectiveCard.PatientInvestor("PATIENT_INVESTOR", 3);

        @Test
        void passesWithContributionsInThreeDifferentRounds() {
            GameState s = withContribution(start(), 0, 0, 3, ONE_FOOD);
            s = withContribution(s, 0, 0, 5, ONE_FOOD);
            s = withContribution(s, 1, 0, 8, ONE_FOOD);

            assertTrue(completed(card, s, 0));
        }

        @Test
        void failsWithManyContributionsInTwoRounds() {
            GameState s = withContribution(start(), 0, 0, 3, ONE_FOOD);
            s = withContribution(s, 0, 0, 3, ONE_FOOD);
            s = withContribution(s, 0, 0, 5, ONE_FOOD);
            s = withContribution(s, 0, 1, 4, ONE_FOOD);

            assertFalse(completed(card, s, 0));
        }
    }

    @Nested
    class BigDeal {
        private final ObjectiveCard card = new ObjectiveCard.BigDeal("BIG_DEAL", 5);

        @Test
        void passesWhenGivingFiveResourcesInOneTradeAsRecipient() {
            GameState s = withTrade(start(), 1, 0, ONE_FOOD, new ResourceBundle(2, 3, 0, 0, 0),
                    TradeOfferStatus.ACCEPTED);

            assertTrue(completed(card, s, 0));
            assertFalse(completed(card, s, 1), "seat 1 gave one resource");
        }

        @Test
        void failsWhenMoneyMakesUpTheRestOrTheTradeWasNotCarriedOut() {
            GameState s = withTrade(start(), 0, 1, new ResourceBundle(1, 1, 1, 1, 10), ONE_FOOD,
                    TradeOfferStatus.ACCEPTED);
            s = withTrade(s, 0, 2, new ResourceBundle(5, 0, 0, 0, 0), ONE_FOOD, TradeOfferStatus.INVALID);
            s = withTrade(s, 0, 3, new ResourceBundle(5, 0, 0, 0, 0), ONE_FOOD, TradeOfferStatus.CANCELLED);

            assertFalse(completed(card, s, 0));
        }
    }

    @Test
    void onlyKeptObjectivesAreCounted() {
        GameState s = update(start(), 0, p -> p.withKeptObjectives(List.of(
                new ObjectiveCard.SteadyCity("STEADY"),
                new ObjectiveCard.DiversifiedEconomy("DIVERSE", 4))));

        assertEquals(List.of("STEADY"), Objectives.completedObjectives(s, 0, ruleset));
    }

    @Test
    void everyRoundResolutionIsRecorded() {
        GameState s = resolve(windowOf(start(), 4));

        for (PlayerState player : s.players()) {
            assertEquals(4, player.objectiveProgress().levelAfterRound().size());
        }
    }
}
