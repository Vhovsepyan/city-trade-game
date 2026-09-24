package citytrade.engine.opportunity;

import static citytrade.engine.TestGames.accept;
import static citytrade.engine.TestGames.assertRejected;
import static citytrade.engine.TestGames.readyForRoundOne;
import static citytrade.engine.TestGames.withCity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.TestRulesets;
import citytrade.engine.command.AcceptTrade;
import citytrade.engine.command.BreakContract;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.ContributeToProject;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.ProposeContract;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.ResolveRound;
import citytrade.engine.command.SignContract;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.economy.Production;
import citytrade.engine.ruleset.OpportunityRules;
import citytrade.engine.ruleset.OpportunityRules.OpportunityCard;
import citytrade.engine.ruleset.ProjectRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.ContractStatus;
import citytrade.engine.state.GameState;
import citytrade.engine.state.OpportunityStatus;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.RegionalOpportunity;
import citytrade.engine.state.SecretBid;
import citytrade.engine.state.TradeOfferStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Regional opportunities and secret bids, Numbers Sheet 17 and 20, Concept 24-25. */
class OpportunitiesTest {

    private static final long SEED = 11;
    private static final String SOLAR = "SOLAR_FIELD";
    private static final String VALLEY = "FERTILE_VALLEY";
    private static final String ZONE = "INDUSTRIAL_ZONE";
    private static final ResourceBundle SOLAR_REWARD = bundle(0, 1, 0, 0, 0);

    private final Ruleset ruleset = TestRulesets.withProjects(
            TestRulesets.withOpportunities(TestRulesets.standard(),
                    new OpportunityRules(List.of(3, 5, 7, 9, 11), TestRulesets.prototypeOpportunities())),
            new ProjectRules(List.of(new ProjectRules.ProjectWindow(3, 6), new ProjectRules.ProjectWindow(7, 10)),
                    2, 1, 6, 3, 2, 1, TestRulesets.prototypeProjects()));

    private static ResourceBundle bundle(int food, int energy, int materials, int technology, int money) {
        return new ResourceBundle(food, energy, materials, technology, money);
    }

    private static ResourceBundle money(int amount) {
        return ResourceBundle.EMPTY.withMoney(amount);
    }

    /** A game before Round 1 whose opportunity deck is in prototype order: Solar Field, Fertile Valley, ... */
    private GameState fixedDeck() {
        GameState s = readyForRoundOne(SEED, ruleset);
        List<OpportunityCard> deck = ruleset.opportunities().cards();
        return new GameState(s.rulesetVersion(), s.round(), s.phase(), s.random(), s.players(), s.market(),
                s.eventDeck(), s.eventWarning(), s.activeEvent(), s.projects(), deck, s.opportunities(),
                s.tradeOffers(), s.nextOfferId(), s.contracts(), s.nextContractId(), s.finalResult());
    }

    /** From any state before a StartRound: play on until the window of {@code round} is open. */
    private GameState windowOf(GameState state, int round) {
        GameState s = accept(state, new StartRound(), ruleset).state();
        while (s.round() < round) {
            s = accept(accept(s, new ResolveRound(), ruleset).state(), new StartRound(), ruleset).state();
        }
        return s;
    }

    /** The window of {@code round}; every player is level 1 and holds exactly {@code holdings}. */
    private GameState windowWith(int round, ResourceBundle holdings) {
        GameState s = windowOf(fixedDeck(), round);
        for (int seat = 0; seat < 4; seat++) {
            s = withCity(s, seat, 1, holdings);
        }
        return s;
    }

    private GameState bid(GameState state, int seat, String opportunityId, int amount) {
        return accept(state, new PlaceBid(seat, opportunityId, amount), ruleset).state();
    }

    private GameResult.Accepted resolve(GameState state) {
        return accept(state, new ResolveRound(), ruleset);
    }

    // --- appearance ---

    @Test
    void topCardIsRevealedInStepTwoThreeOfEachAppearanceRound() {
        GameState round2 = windowOf(fixedDeck(), 2);
        assertEquals(List.of(), round2.opportunities());
        assertRejected(withCity(round2, 0, 1, money(9)), new PlaceBid(0, SOLAR, 1), ruleset,
                RejectionCode.UNKNOWN_OPPORTUNITY);

        GameResult.Accepted round3 = accept(resolve(round2).state(), new StartRound(), ruleset);
        assertTrue(round3.events().contains(new DomainEvent.OpportunityRevealed(SOLAR)));
        assertEquals(List.of(RegionalOpportunity.revealed(ruleset.opportunities().cards().get(0), 3)),
                round3.state().opportunities());
        assertEquals(4, round3.state().opportunityDeck().size());

        GameState round4 = windowOf(resolve(round3.state()).state(), 4);
        assertEquals(1, round4.opportunities().size(), "no card in Round 4");

        GameResult.Accepted round5 = accept(resolve(round4).state(), new StartRound(), ruleset);
        assertTrue(round5.events().contains(new DomainEvent.OpportunityRevealed(VALLEY)));
        assertEquals(List.of(SOLAR, VALLEY), round5.state().opportunities().stream().map(RegionalOpportunity::id).toList());
        assertTrue(round5.state().opportunities().stream().allMatch(o -> o.status() == OpportunityStatus.OPEN),
                "several cards can be open at the same time");
    }

    @Test
    void allFiveCardsAppearInTheirRounds() {
        GameState round11 = windowOf(fixedDeck(), 11);
        assertEquals(List.of(3, 5, 7, 9, 11),
                round11.opportunities().stream().map(RegionalOpportunity::appearedRound).toList());
        assertEquals(List.of(), round11.opportunityDeck());
    }

    // --- placing, changing, withdrawing ---

    @Test
    void bidReservesMoneyWithoutTakingIt() {
        GameResult.Accepted placed = accept(windowWith(3, money(6)), new PlaceBid(0, SOLAR, 4), ruleset);

        assertEquals(List.of(new DomainEvent.BidPlaced(0, SOLAR)), placed.events(), "the amount stays secret");
        GameState s = placed.state();
        assertEquals(money(6), s.player(0).holdings(), "Money is only reserved");
        assertEquals(4, s.reservedMoney(0));
        assertEquals(money(2), s.spendableHoldings(0));
        assertEquals(List.of(new SecretBid(0, 4)), s.opportunity(SOLAR).orElseThrow().bids());
    }

    @Test
    void bidMayUseAllMoneyButNotMore() {
        GameState window = windowWith(3, money(6));
        accept(window, new PlaceBid(0, SOLAR, 6), ruleset);
        assertRejected(window, new PlaceBid(0, SOLAR, 7), ruleset, RejectionCode.INSUFFICIENT_MONEY);
    }

    @Test
    void totalOfActiveBidsCannotExceedMoney() {
        GameState window = bid(windowWith(5, money(6)), 0, SOLAR, 4);

        assertRejected(window, new PlaceBid(0, VALLEY, 3), ruleset, RejectionCode.INSUFFICIENT_MONEY);
        GameState both = bid(window, 0, VALLEY, 2);
        assertEquals(6, both.reservedMoney(0));
        assertEquals(0, both.spendableHoldings(0).money());
    }

    @Test
    void changingABidReplacesItAndFreesTheOldAmount() {
        GameState window = bid(windowWith(5, money(6)), 0, SOLAR, 4);
        window = bid(window, 0, VALLEY, 2);

        GameState lowered = bid(window, 0, SOLAR, 1);
        assertEquals(List.of(new SecretBid(0, 1)), lowered.opportunity(SOLAR).orElseThrow().bids());
        assertEquals(3, lowered.reservedMoney(0));

        assertRejected(lowered, new PlaceBid(0, SOLAR, 5), ruleset, RejectionCode.INSUFFICIENT_MONEY);
        GameState raised = bid(lowered, 0, SOLAR, 4);
        assertEquals(6, raised.reservedMoney(0), "the old bid on the same card counts as free");
    }

    @Test
    void zeroWithdrawsTheBidAndReleasesTheMoney() {
        GameState window = bid(windowWith(3, money(6)), 0, SOLAR, 4);
        GameResult.Accepted withdrawn = accept(window, new PlaceBid(0, SOLAR, 0), ruleset);

        assertEquals(List.of(new DomainEvent.BidPlaced(0, SOLAR)), withdrawn.events());
        assertEquals(List.of(), withdrawn.state().opportunity(SOLAR).orElseThrow().bids());
        assertEquals(0, withdrawn.state().reservedMoney(0));
        accept(withdrawn.state(), new BuyFromMarket(0, Resource.FOOD, 1), ruleset);
    }

    @Test
    void zeroIsAPassWithoutAnEarlierBid() {
        GameResult.Accepted passed = accept(windowWith(3, ResourceBundle.EMPTY), new PlaceBid(0, SOLAR, 0), ruleset);
        assertEquals(List.of(), passed.state().opportunity(SOLAR).orElseThrow().bids());
    }

    @Test
    void invalidBidsAreRejected() {
        GameState window = windowWith(3, money(6));
        assertRejected(window, new PlaceBid(4, SOLAR, 1), ruleset, RejectionCode.UNKNOWN_PLAYER);
        assertRejected(window, new PlaceBid(0, VALLEY, 1), ruleset, RejectionCode.UNKNOWN_OPPORTUNITY);
        assertRejected(window, new PlaceBid(0, "NO_SUCH_CARD", 1), ruleset, RejectionCode.UNKNOWN_OPPORTUNITY);
        assertRejected(window, new PlaceBid(0, SOLAR, -1), ruleset, RejectionCode.INVALID_QUANTITY);
    }

    @Test
    void bidsOnlyInTheWindow() {
        GameState resolved = resolve(bid(windowWith(3, money(6)), 0, SOLAR, 1)).state();
        assertRejected(resolved, new PlaceBid(0, SOLAR, 1), ruleset, RejectionCode.INVALID_PHASE);
    }

    @Test
    void wonCardTakesNoMoreBids() {
        GameState resolved = resolve(bid(windowWith(3, money(6)), 0, SOLAR, 2)).state();
        GameState round4 = withCity(accept(resolved, new StartRound(), ruleset).state(), 1, 1, money(6));
        assertRejected(round4, new PlaceBid(1, SOLAR, 3), ruleset, RejectionCode.OPPORTUNITY_NOT_OPEN);
    }

    // --- resolution ---

    @Test
    void highestBidWinsAndOnlyTheWinnerPays() {
        GameState window = windowWith(3, money(6));
        window = bid(window, 0, SOLAR, 3);
        window = bid(window, 1, SOLAR, 5);
        window = bid(window, 2, SOLAR, 0);

        GameResult.Accepted resolved = resolve(window);
        assertTrue(resolved.events().contains(new DomainEvent.OpportunityWon(SOLAR, 1, 5, SOLAR_REWARD)));
        GameState s = resolved.state();
        assertEquals(money(1), s.player(1).holdings());
        for (int seat : new int[] {0, 2, 3}) {
            assertEquals(money(6), s.player(seat).holdings(), "seat " + seat + " pays nothing");
            assertEquals(ResourceBundle.EMPTY, s.player(seat).extraProduction());
        }
        RegionalOpportunity card = s.opportunity(SOLAR).orElseThrow();
        assertEquals(OpportunityStatus.WON, card.status());
        assertEquals(Optional.of(1), card.winnerSeat());
        assertEquals(List.of(), card.bids());
        for (int seat = 0; seat < 4; seat++) {
            assertEquals(0, s.reservedMoney(seat), "every reservation is released");
        }
    }

    @Test
    void singleBidOfOneWins() {
        GameResult.Accepted resolved = resolve(bid(windowWith(3, money(6)), 2, SOLAR, 1));
        assertTrue(resolved.events().contains(new DomainEvent.OpportunityWon(SOLAR, 2, 1, SOLAR_REWARD)));
        assertEquals(money(5), resolved.state().player(2).holdings());
    }

    @Test
    void winnerGetsTheProductionRewardFromTheNextRound() {
        GameState window = bid(windowWith(3, money(4)), 0, SOLAR, 4);
        GameState resolved = resolve(window).state();
        assertEquals(SOLAR_REWARD, resolved.player(0).extraProduction());
        assertEquals(ResourceBundle.EMPTY, resolved.player(0).holdings(), "no reward in the round it is won");

        GameState round4 = accept(resolved, new StartRound(), ruleset).state();
        for (int seat = 0; seat < 4; seat++) {
            PlayerState before = resolved.player(seat).withExtraProduction(ResourceBundle.EMPTY)
                    .withHoldings(ResourceBundle.EMPTY);
            ResourceBundle reward = seat == 0 ? SOLAR_REWARD : ResourceBundle.EMPTY;
            ResourceBundle kept = seat == 0 ? ResourceBundle.EMPTY : money(4);
            assertEquals(Production.productionOf(before, 4, ruleset).plus(reward).plus(kept),
                    round4.player(seat).holdings(), "seat " + seat);
        }
    }

    @Test
    void tiedHighestBidMeansNobodyWinsNobodyPaysAndTheCardStays() {
        // Concept 24 example: bids 5, 5, 3, 0 -> nobody wins; the player who bid 3 does NOT win.
        GameState window = windowWith(3, money(6));
        window = bid(window, 0, SOLAR, 5);
        window = bid(window, 1, SOLAR, 5);
        window = bid(window, 2, SOLAR, 3);
        window = bid(window, 3, SOLAR, 0);

        GameResult.Accepted resolved = resolve(window);
        assertTrue(resolved.events().contains(new DomainEvent.OpportunityNotWon(SOLAR, true)));
        assertFalse(resolved.events().stream().anyMatch(e -> e instanceof DomainEvent.OpportunityWon));
        GameState s = resolved.state();
        for (int seat = 0; seat < 4; seat++) {
            assertEquals(money(6), s.player(seat).holdings(), "seat " + seat + " pays nothing");
            assertEquals(ResourceBundle.EMPTY, s.player(seat).extraProduction());
            assertEquals(0, s.reservedMoney(seat));
        }
        RegionalOpportunity card = s.opportunity(SOLAR).orElseThrow();
        assertEquals(OpportunityStatus.OPEN, card.status());
        assertEquals(List.of(), card.bids(), "players bid again next round");

        // Next round the card takes new bids and can be won.
        GameState round4 = withCity(accept(s, new StartRound(), ruleset).state(), 2, 1, money(6));
        GameResult.Accepted again = resolve(bid(round4, 2, SOLAR, 3));
        assertTrue(again.events().contains(new DomainEvent.OpportunityWon(SOLAR, 2, 3, SOLAR_REWARD)));
    }

    @Test
    void cardWithoutBidsStaysOpen() {
        GameResult.Accepted resolved = resolve(windowWith(3, money(6)));
        assertTrue(resolved.events().contains(new DomainEvent.OpportunityNotWon(SOLAR, false)));
        assertEquals(OpportunityStatus.OPEN, resolved.state().opportunity(SOLAR).orElseThrow().status());
    }

    @Test
    void cardsNeverWonAreRemovedAfterTheLastRound() {
        GameState round13 = windowWith(13, money(6));
        GameState resolved13 = resolve(round13).state();
        assertTrue(resolved13.opportunities().stream().allMatch(o -> o.status() == OpportunityStatus.OPEN),
                "before the last round an unwon card stays open");

        GameState round14 = withCity(accept(resolved13, new StartRound(), ruleset).state(), 0, 1, money(6));
        round14 = withCity(round14, 1, 1, money(6));
        round14 = bid(round14, 0, SOLAR, 3);
        round14 = bid(round14, 1, SOLAR, 3);
        round14 = bid(round14, 0, ZONE, 2);

        GameResult.Accepted resolved = resolve(round14);
        assertTrue(resolved.events().contains(new DomainEvent.OpportunityNotWon(SOLAR, true)));
        assertTrue(resolved.events().contains(new DomainEvent.OpportunityRemoved(SOLAR)), "tied in Round 14");
        assertTrue(resolved.events().contains(new DomainEvent.OpportunityNotWon(VALLEY, false)));
        assertTrue(resolved.events().contains(new DomainEvent.OpportunityRemoved(VALLEY)), "no bids in Round 14");
        assertTrue(resolved.events().contains(
                new DomainEvent.OpportunityWon(ZONE, 0, 2, bundle(0, 0, 1, 0, 0))));
        assertFalse(resolved.events().contains(new DomainEvent.OpportunityRemoved(ZONE)));

        GameState end = resolved.state();
        assertEquals(OpportunityStatus.WON, end.opportunity(ZONE).orElseThrow().status());
        assertEquals(4, end.opportunities().stream().filter(o -> o.status() == OpportunityStatus.REMOVED).count());
        assertEquals(money(4), end.player(0).holdings());
        assertEquals(money(6), end.player(1).holdings());
        assertEquals(0, end.reservedMoney(0));
    }

    @Test
    void eachOpenCardIsResolvedWithItsOwnBids() {
        GameState window = windowWith(5, money(6));
        window = bid(window, 0, SOLAR, 3);
        window = bid(window, 0, VALLEY, 3);
        window = bid(window, 1, SOLAR, 3);
        window = bid(window, 2, VALLEY, 2);

        GameResult.Accepted resolved = resolve(window);
        assertTrue(resolved.events().contains(new DomainEvent.OpportunityNotWon(SOLAR, true)));
        assertTrue(resolved.events().contains(
                new DomainEvent.OpportunityWon(VALLEY, 0, 3, bundle(1, 0, 0, 0, 0))));
        assertEquals(money(3), resolved.state().player(0).holdings(), "the tied bid is not paid");
        assertEquals(money(6), resolved.state().player(2).holdings());
    }

    @Test
    void arrivalOrderOfBidsDoesNotMatter() {
        GameState window = windowWith(5, money(6));
        List<GameCommand> commands = List.of(
                new PlaceBid(0, SOLAR, 4), new PlaceBid(1, SOLAR, 4), new PlaceBid(2, SOLAR, 2),
                new PlaceBid(3, VALLEY, 5), new PlaceBid(1, VALLEY, 2), new PlaceBid(2, VALLEY, 0));

        GameState reference = null;
        GameState referenceResolved = null;
        for (List<GameCommand> order : permutations(commands)) {
            GameState s = window;
            for (GameCommand command : order) {
                s = accept(s, command, ruleset).state();
            }
            GameState resolved = resolve(s).state();
            if (reference == null) {
                reference = s;
                referenceResolved = resolved;
            }
            assertEquals(reference, s, "state after bids, order " + order);
            assertEquals(referenceResolved, resolved, "state after resolution, order " + order);
        }
        assertEquals(OpportunityStatus.OPEN, referenceResolved.opportunity(SOLAR).orElseThrow().status());
        assertEquals(Optional.of(3), referenceResolved.opportunity(VALLEY).orElseThrow().winnerSeat());
        assertEquals(money(1), referenceResolved.player(3).holdings());
    }

    private static <T> List<List<T>> permutations(List<T> items) {
        if (items.isEmpty()) {
            return List.of(List.of());
        }
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            List<T> rest = new ArrayList<>(items);
            T first = rest.remove(i);
            for (List<T> tail : permutations(rest)) {
                List<T> permutation = new ArrayList<>();
                permutation.add(first);
                permutation.addAll(tail);
                result.add(permutation);
            }
        }
        return result;
    }

    // --- reserved Money cannot be spent on anything else ---

    @Test
    void reservedMoneyCannotBuyFromTheMarket() {
        // Market buy price at the start step is 4 Money per unit.
        GameState window = bid(windowWith(3, money(6)), 0, SOLAR, 3);
        assertRejected(window, new BuyFromMarket(0, Resource.FOOD, 1), ruleset, RejectionCode.INSUFFICIENT_MONEY);
        accept(bid(window, 0, SOLAR, 2), new BuyFromMarket(0, Resource.FOOD, 1), ruleset);
    }

    @Test
    void reservedMoneyCannotBeOfferedOrTraded() {
        GameState window = windowWith(3, bundle(2, 2, 2, 2, 6));
        GameState offered = accept(window, new ProposeTrade(0, 1, money(4), bundle(1, 0, 0, 0, 0)), ruleset).state();
        GameState reserved = bid(offered, 0, SOLAR, 3);

        assertRejected(reserved, new ProposeTrade(0, 1, money(4), ResourceBundle.EMPTY), ruleset,
                RejectionCode.INSUFFICIENT_RESOURCES);
        GameResult.Accepted accepted = accept(reserved, new AcceptTrade(1, 1), ruleset);
        assertTrue(accepted.events().contains(new DomainEvent.TradeInvalidated(1, 0)));
        assertEquals(TradeOfferStatus.INVALID, accepted.state().tradeOffer(1).orElseThrow().status());

        // The recipient's reserved Money is protected in the same way.
        GameState asks = accept(window, new ProposeTrade(0, 1, bundle(1, 0, 0, 0, 0), money(4)), ruleset).state();
        GameResult.Accepted recipientShort = accept(bid(asks, 1, SOLAR, 3), new AcceptTrade(1, 1), ruleset);
        assertTrue(recipientShort.events().contains(new DomainEvent.TradeInvalidated(1, 1)));
    }

    @Test
    void reservedMoneyCannotBeGivenInAContract() {
        GameState window = bid(windowWith(3, bundle(2, 2, 2, 2, 6)), 0, SOLAR, 3);
        assertRejected(window, new ProposeContract(0, 0, 1, money(4), bundle(1, 0, 0, 0, 0), 4), ruleset,
                RejectionCode.INSUFFICIENT_RESOURCES);

        // Debtor proposes, creditor bids before signing: the proposal becomes INVALID.
        GameState proposed = accept(windowWith(3, bundle(2, 2, 2, 2, 6)),
                new ProposeContract(1, 0, 1, money(4), bundle(1, 0, 0, 0, 0), 4), ruleset).state();
        GameResult.Accepted signed = accept(bid(proposed, 0, SOLAR, 3), new SignContract(0, 1), ruleset);
        assertEquals(ContractStatus.INVALID, signed.state().contract(1).orElseThrow().status());
        assertEquals(bundle(2, 2, 2, 2, 6), signed.state().player(0).holdings());
    }

    // --- D18: voluntary contract break while bids reserve Money ---

    /** Round 3 window: seat 1 owes seat 0 {@code owed} in Round 4 (contract 1) and holds {@code debtorMoney}. */
    private GameState activeContract(int debtorMoney, ResourceBundle owed) {
        GameState window = withCity(windowWith(3, bundle(2, 2, 2, 2, 6)), 1, 1, bundle(2, 2, 2, 2, debtorMoney));
        GameState proposed = accept(window, new ProposeContract(0, 0, 1, bundle(1, 0, 0, 0, 0), owed, 4),
                ruleset).state();
        return accept(proposed, new SignContract(1, 1), ruleset).state();
    }

    private static DomainEvent.ContractBroken brokenEvent(List<DomainEvent> events) {
        return events.stream()
                .filter(e -> e instanceof DomainEvent.ContractBroken)
                .map(DomainEvent.ContractBroken.class::cast)
                .findFirst().orElseThrow();
    }

    @Test
    void d18VoluntaryBreakIsRejectedWhenBidsLeaveTooLittleFreeMoney() {
        // Owed 2 Money -> compensation 2 x 2 = 4 Money. Seat 1 holds 6 Money.
        GameState active = activeContract(6, money(2));
        assertRejected(bid(active, 1, SOLAR, 5), new BreakContract(1, 1), ruleset,
                RejectionCode.INSUFFICIENT_FREE_MONEY);
        assertRejected(bid(active, 1, SOLAR, 3), new BreakContract(1, 1), ruleset,
                RejectionCode.INSUFFICIENT_FREE_MONEY);
    }

    @Test
    void d18VoluntaryBreakIsAllowedAfterLoweringTheBid() {
        GameState lowered = bid(bid(activeContract(6, money(2)), 1, SOLAR, 5), 1, SOLAR, 2);

        GameResult.Accepted broken = accept(lowered, new BreakContract(1, 1), ruleset);
        DomainEvent.ContractBroken event = brokenEvent(broken.events());
        assertEquals(4, event.compensationOwed());
        assertEquals(4, event.compensationPaid(), "free Money covers the whole compensation");
        assertEquals(1, event.prestigeLost(), "only the fixed break penalty");
        assertEquals(ContractStatus.BROKEN, broken.state().contract(1).orElseThrow().status());
        assertEquals(2, broken.state().player(1).holdings().money(), "the bid stays reserved and payable");
        assertEquals(2, broken.state().reservedMoney(1));
    }

    @Test
    void d18VoluntaryBreakIsAllowedAfterWithdrawingTheBid() {
        GameState withdrawn = bid(bid(activeContract(6, money(2)), 1, SOLAR, 5), 1, SOLAR, 0);

        GameResult.Accepted broken = accept(withdrawn, new BreakContract(1, 1), ruleset);
        assertEquals(4, brokenEvent(broken.events()).compensationPaid());
        assertEquals(2, broken.state().player(1).holdings().money());
    }

    @Test
    void d18WithoutBidsABreakWithTooLittleMoneyIsStillAllowed() {
        // Numbers Sheet 13 as written: 1 Money paid, the unpaid 3 cost ceil(3 / 2) = 2 Prestige, plus the penalty 1.
        GameState active = activeContract(1, money(2));
        int prestigeBefore = active.player(1).prestige();

        GameResult.Accepted broken = accept(active, new BreakContract(1, 1), ruleset);
        DomainEvent.ContractBroken event = brokenEvent(broken.events());
        assertEquals(4, event.compensationOwed());
        assertEquals(1, event.compensationPaid());
        assertEquals(3, event.prestigeLost());
        assertEquals(prestigeBefore - 3, broken.state().player(1).prestige());
        assertEquals(0, broken.state().player(1).holdings().money());
    }

    @Test
    void d18DoesNotChangeTheAutomaticBreakInStepOneFive() {
        // 20 Food owed -> compensation 40, far more than seat 1 can pay; the voluntary break is blocked by the bid.
        GameState active = bid(activeContract(6, bundle(20, 0, 0, 0, 0)), 1, SOLAR, 5);
        assertRejected(active, new BreakContract(1, 1), ruleset, RejectionCode.INSUFFICIENT_FREE_MONEY);

        GameResult.Accepted round4 = accept(resolve(active).state(), new StartRound(), ruleset);
        DomainEvent.ContractBroken event = brokenEvent(round4.events());
        assertFalse(event.voluntary());
        assertTrue(event.delivered().food() > 0, "the automatic break delivers what the debtor has (D3)");
        assertEquals(2 * (20 - event.delivered().food()), event.compensationOwed());
        assertTrue(event.compensationPaid() < event.compensationOwed(), "the debtor pays what it can, no rejection");
        assertEquals(ContractStatus.BROKEN, round4.state().contract(1).orElseThrow().status());
    }

    @Test
    void reservedMoneyCannotBeContributedToAProject() {
        GameState window = bid(windowWith(3, bundle(2, 2, 2, 2, 6)), 0, SOLAR, 5);
        String project = window.projects().stream().filter(p -> p.card().needs().money() > 0)
                .filter(p -> p.window().openRound() == 3).findFirst().orElseThrow().id();
        assertRejected(window, new ContributeToProject(0, project, money(2)), ruleset,
                RejectionCode.INSUFFICIENT_MONEY);
        accept(window, new ContributeToProject(0, project, money(1)), ruleset);
    }

    @Test
    void reservedMoneyCannotPayForBuildingsOrLevels() {
        GameState window = bid(windowWith(3, bundle(9, 9, 9, 9, 6)), 0, SOLAR, 5);
        assertRejected(window, new BuildBuilding(0, "MARKET_HALL", Optional.empty()), ruleset,
                RejectionCode.INSUFFICIENT_MONEY);
        accept(bid(window, 0, SOLAR, 4), new BuildBuilding(0, "MARKET_HALL", Optional.empty()), ruleset);

        GameState level2 = withCity(window, 0, 2, bundle(9, 9, 9, 9, 6));
        assertRejected(level2, new UpgradeCity(0), ruleset, RejectionCode.INSUFFICIENT_MONEY);
        accept(bid(level2, 0, SOLAR, 2), new UpgradeCity(0), ruleset);
    }

    // --- setup ---

    @Test
    void setupNeedsAnOpportunityCardForEveryAppearanceRound() {
        Ruleset tooFew = TestRulesets.withOpportunities(ruleset,
                new OpportunityRules(List.of(3, 5, 7), TestRulesets.prototypeOpportunities().subList(0, 2)));
        assertThrows(IllegalArgumentException.class, () -> GameSetup.create(SEED, tooFew));
    }

    @Test
    void cardStillInTheDeckTakesNoBids() {
        GameState window = windowWith(5, money(6));
        assertRejected(window, new PlaceBid(0, ZONE, 1), ruleset, RejectionCode.UNKNOWN_OPPORTUNITY);
    }
}
