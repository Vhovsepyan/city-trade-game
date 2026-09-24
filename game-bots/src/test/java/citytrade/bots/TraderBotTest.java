package citytrade.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.GameEngine;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.AcceptTrade;
import citytrade.engine.command.BuyFromMarket;
import citytrade.engine.command.CancelTrade;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.RejectTrade;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.EventWarning;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.RegionalOpportunity;
import citytrade.engine.state.TradeOfferStatus;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TraderBotTest {

    private static final int SEAT = 0;

    private final Ruleset ruleset = TestRulesets.prototype();
    private final TraderBot bot = new TraderBot();
    private GameState window;
    private Resource specialty;
    /** A non-specialty resource of the bot's city. */
    private Resource other;
    private int otherOwner;
    /** A third seat, neither the bot nor the owner of {@link #other}. */
    private int thirdSeat;

    /** The window of Round 1: no active event, all prices at the start step, the bot's city at level 1. */
    @BeforeEach
    void startRoundOne() {
        GameState state = GameSetup.create(3, ruleset);
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            state = accept(state, bot.chooseObjectives(state, seat, ruleset));
        }
        window = accept(state, new StartRound());
        assertEquals(GamePhase.WINDOW, window.phase());
        assertTrue(window.activeEvent().isEmpty());
        specialty = window.player(SEAT).city().specialty();
        other = Resource.values()[(specialty.ordinal() + 1) % Resource.values().length];
        otherOwner = ownerOf(other);
        thirdSeat = Arrays.stream(new int[] {1, 2, 3}).filter(seat -> seat != otherOwner).findFirst().orElseThrow();
    }

    @Test
    void acceptsAnEvenOfferPaidFromItsSpareResources() {
        GameState state = withHoldings(window, SEAT, ResourceBundle.EMPTY.with(specialty, 8));
        state = offerToBot(state, ResourceBundle.EMPTY.with(other, 1), ResourceBundle.EMPTY.with(specialty, 1));

        AcceptTrade answer = assertInstanceOf(AcceptTrade.class, next(state).orElseThrow());
        state = accept(state, answer);
        assertEquals(TradeOfferStatus.ACCEPTED, state.tradeOffers().getLast().status());
        assertEquals(1, state.player(SEAT).holdings().amountOf(other));
    }

    @Test
    void rejectsAnOfferWorthLessThanWhatItGives() {
        GameState state = withHoldings(window, SEAT, ResourceBundle.EMPTY.with(specialty, 8));
        state = offerToBot(state, ResourceBundle.EMPTY.with(other, 1), ResourceBundle.EMPTY.with(specialty, 2));

        assertInstanceOf(RejectTrade.class, next(state).orElseThrow());
    }

    @Test
    void rejectsAnOfferThatTakesWhatItNeedsForTheNextLevel() {
        int needed = levelTwoCost().amountOf(specialty);
        GameState state = withHoldings(window, SEAT, ResourceBundle.EMPTY.with(specialty, needed));
        state = offerToBot(state, ResourceBundle.EMPTY.with(other, 3), ResourceBundle.EMPTY.with(specialty, 1));

        assertInstanceOf(RejectTrade.class, next(state).orElseThrow());
    }

    @Test
    void rejectsAnOfferTheBotCannotPay() {
        GameState state = withHoldings(window, SEAT, ResourceBundle.EMPTY);
        state = offerToBot(state, ResourceBundle.EMPTY.with(other, 1), ResourceBundle.EMPTY.withMoney(1));

        assertInstanceOf(RejectTrade.class, next(state).orElseThrow());
    }

    @Test
    void aHigherThresholdRejectsAnEvenOffer() {
        TraderBot demanding = new TraderBot(new TraderBot.Settings(150, 3, 50, 50));
        GameState state = withHoldings(window, SEAT, ResourceBundle.EMPTY.with(specialty, 8));
        state = offerToBot(state, ResourceBundle.EMPTY.with(other, 1), ResourceBundle.EMPTY.with(specialty, 1));

        assertInstanceOf(RejectTrade.class, demanding.nextWindowCommand(state, SEAT, ruleset).orElseThrow());
        state = offerToBot(accept(state, new RejectTrade(SEAT, state.tradeOffers().getLast().id())),
                ResourceBundle.EMPTY.with(other, 3), ResourceBundle.EMPTY.with(specialty, 2));
        assertInstanceOf(AcceptTrade.class, demanding.nextWindowCommand(state, SEAT, ruleset).orElseThrow());
    }

    @Test
    void offersSpareSpecialtyForAMissingResourceToTheCityThatProducesIt() {
        GameState state = withHoldings(window, SEAT, levelTwoCost().with(specialty, 8).with(other, 2));
        int price = Market.buyCost(state, other, ruleset);
        assertEquals(price, Market.buyCost(state, specialty, ruleset), "Round 1 prices are all equal");

        ProposeTrade offer = assertInstanceOf(ProposeTrade.class, next(state).orElseThrow());

        assertEquals(new ProposeTrade(SEAT, otherOwner, ResourceBundle.EMPTY.with(specialty, 1),
                ResourceBundle.EMPTY.with(other, 1)), offer);
        state = withHoldings(accept(state, offer), otherOwner, ResourceBundle.EMPTY.with(other, 5));
        state = accept(state, new AcceptTrade(otherOwner, state.tradeOffers().getLast().id()));
        assertEquals(Optional.of(new UpgradeCity(SEAT)), next(state));
    }

    @Test
    void asksForFewerUnitsWhenItsSpareDoesNotPayForAll() {
        int neededSpecialty = levelTwoCost().amountOf(specialty);
        GameState state = withHoldings(window, SEAT,
                levelTwoCost().with(specialty, neededSpecialty + 1).with(other, 0));

        assertEquals(Optional.of(new ProposeTrade(SEAT, otherOwner, ResourceBundle.EMPTY.with(specialty, 1),
                ResourceBundle.EMPTY.with(other, 1))), next(state));
    }

    @Test
    void offersNothingWithoutSpareResources() {
        GameState state = withHoldings(window, SEAT, levelTwoCost().with(other, 0));

        assertEquals(Optional.empty(), next(state));
    }

    @Test
    void cancelsItsOwnOfferStillOpenAtItsNextTurn() {
        GameState state = withHoldings(window, SEAT, levelTwoCost().with(specialty, 8).with(other, 2));
        state = accept(state, next(state).orElseThrow());
        int offerId = state.tradeOffers().getLast().id();

        assertEquals(Optional.of(new CancelTrade(SEAT, offerId)), next(state));
    }

    @Test
    void asksEachSeatOnceThenUsesTheMarket() {
        TraderBot twoOffers = new TraderBot(new TraderBot.Settings(100, 2, 50, 50));
        GameState state = withHoldings(window, SEAT, levelTwoCost().with(specialty, 8).with(other, 2).withMoney(20));

        ProposeTrade first = assertInstanceOf(ProposeTrade.class,
                twoOffers.nextWindowCommand(state, SEAT, ruleset).orElseThrow());
        assertEquals(otherOwner, first.recipientSeat());
        state = accept(state, first);
        state = accept(state, new RejectTrade(otherOwner, state.tradeOffers().getLast().id()));

        ProposeTrade second = assertInstanceOf(ProposeTrade.class,
                twoOffers.nextWindowCommand(state, SEAT, ruleset).orElseThrow());
        assertEquals(thirdSeat, second.recipientSeat());
        state = accept(state, second);
        state = accept(state, new RejectTrade(thirdSeat, state.tradeOffers().getLast().id()));

        assertEquals(Optional.of(new BuyFromMarket(SEAT, other, 1)), twoOffers.nextWindowCommand(state, SEAT, ruleset));
    }

    @Test
    void keepsNoReserveWithoutAWarnedCrisis() {
        GameState levelTwo = atLevelTwo(window);
        EventCard notACrisis = ruleset.events().deck().stream()
                .filter(card -> card instanceof EventCard.NoMoneyIncome).findFirst().orElseThrow();

        assertEquals(ResourceBundle.EMPTY, TraderBot.crisisReserve(levelTwo, SEAT, ruleset));
        assertEquals(ResourceBundle.EMPTY, TraderBot.crisisReserve(warned(levelTwo, notACrisis), SEAT, ruleset));
        EventCard.ResourceCrisis crisis = resourceCrisisOn(other);
        assertEquals(ResourceBundle.EMPTY, TraderBot.crisisReserve(warned(window, crisis), SEAT, ruleset),
                "below the crisis level");
    }

    @Test
    void keepsTheResourceCrisisCostPlusOneUnitForUpkeep() {
        EventCard.ResourceCrisis onOther = resourceCrisisOn(other);
        EventCard.ResourceCrisis onSpecialty = resourceCrisisOn(specialty);
        GameState levelTwo = atLevelTwo(window);
        assertTrue(ruleset.level(2).upkeepResources() > 0);

        assertEquals(ResourceBundle.EMPTY.with(other, onOther.amount() + 1),
                TraderBot.crisisReserve(warned(levelTwo, onOther), SEAT, ruleset));
        assertEquals(ResourceBundle.EMPTY.with(specialty, onSpecialty.amount()),
                TraderBot.crisisReserve(warned(levelTwo, onSpecialty), SEAT, ruleset), "upkeep never takes the specialty");
    }

    @Test
    void keepsTwoOfTheNonSpecialtyResourcesHeldMostForTheNeutralCrisis() {
        EventCard.NonSpecialtyCrisis crisis = ruleset.events().deck().stream()
                .filter(EventCard.NonSpecialtyCrisis.class::isInstance).map(EventCard.NonSpecialtyCrisis.class::cast)
                .findFirst().orElseThrow();
        Resource[] nonSpecialty = Arrays.stream(Resource.values()).filter(r -> r != specialty).toArray(Resource[]::new);
        GameState state = withHoldings(warned(atLevelTwo(window), crisis), SEAT, ResourceBundle.EMPTY
                .with(nonSpecialty[0], 1).with(nonSpecialty[1], 5).with(nonSpecialty[2], 3).with(specialty, 9));

        ResourceBundle reserve = TraderBot.crisisReserve(state, SEAT, ruleset);

        assertEquals(2, crisis.differentResources());
        assertEquals(ResourceBundle.EMPTY.with(nonSpecialty[1], 2).with(nonSpecialty[2], 2), reserve);
    }

    @Test
    void doesNotUpgradeWithTheCrisisReserve() {
        EventCard.ResourceCrisis crisis = resourceCrisisOn(other);
        GameState state = warned(atLevelTwo(window), crisis);
        ResourceBundle levelThree = ruleset.level(3).upgradeCost();

        assertNotEquals(Optional.of(new UpgradeCity(SEAT)), next(withHoldings(state, SEAT, levelThree)));
        ResourceBundle withReserve = levelThree.plus(TraderBot.crisisReserve(state, SEAT, ruleset));
        assertEquals(Optional.of(new UpgradeCity(SEAT)), next(withHoldings(state, SEAT, withReserve)));
    }

    @Test
    void buysTheMissingCrisisReserve() {
        EventCard.ResourceCrisis crisis = resourceCrisisOn(other);
        GameState state = withHoldings(warned(atLevelTwo(window), crisis), SEAT, ResourceBundle.EMPTY.withMoney(20));
        int reserve = TraderBot.crisisReserve(state, SEAT, ruleset).amountOf(other);

        assertEquals(Optional.of(new BuyFromMarket(SEAT, other, reserve)), next(state));
        state = accept(state, next(state).orElseThrow());
        assertEquals(reserve, state.player(SEAT).holdings().amountOf(other));
    }

    @Test
    void bidsAtMostHalfTheRewardValueAndHalfItsSpareMoney() {
        GameState state = withOpenOpportunity(withHoldings(window, SEAT, ResourceBundle.EMPTY.withMoney(20)));
        RegionalOpportunity opportunity = state.opportunities().getFirst();
        int roundsLeft = ruleset.roundCount() - state.round();
        long rewardValue = opportunity.card().productionReward().money();
        for (Resource resource : Resource.values()) {
            rewardValue += (long) opportunity.card().productionReward().amountOf(resource)
                    * Market.buyCost(state, resource, ruleset);
        }
        int expected = (int) Math.min(rewardValue * roundsLeft / 2, 20 / 2);

        assertEquals(Optional.of(new PlaceBid(SEAT, opportunity.id(), expected)), next(state));
        state = accept(state, next(state).orElseThrow());
        assertEquals(Optional.empty(), next(state), "one bid per card and window");
    }

    @Test
    void bidsOnlyMoneyItDoesNotNeedForTheNextLevel() {
        GameState levelTwo = atLevelTwo(window);
        int needed = ruleset.level(3).upgradeCost().money();
        assertTrue(needed > 0);
        GameState state = withOpenOpportunity(withHoldings(levelTwo, SEAT, ResourceBundle.EMPTY.withMoney(needed + 2)));

        assertEquals(Optional.of(new PlaceBid(SEAT, state.opportunities().getFirst().id(), 1)), next(state));
        GameState noSpare = withHoldings(state, SEAT, ResourceBundle.EMPTY.withMoney(needed + 1));
        assertEquals(Optional.empty(), next(noSpare));
    }

    @Test
    void settingsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> new TraderBot.Settings(-1, 3, 50, 50));
        assertThrows(IllegalArgumentException.class, () -> new TraderBot.Settings(100, -1, 50, 50));
        assertThrows(IllegalArgumentException.class, () -> new TraderBot.Settings(100, 3, 101, 50));
        assertThrows(IllegalArgumentException.class, () -> new TraderBot.Settings(100, 3, 50, 101));
    }

    private Optional<GameCommand> next(GameState state) {
        return bot.nextWindowCommand(state, SEAT, ruleset);
    }

    private ResourceBundle levelTwoCost() {
        return ruleset.level(2).upgradeCost();
    }

    private int ownerOf(Resource resource) {
        return window.players().stream().filter(player -> player.city().specialty() == resource)
                .mapToInt(PlayerState::seat).findFirst().orElseThrow();
    }

    /** {@link #otherOwner} offers {@code offered} to the bot for {@code requested}, through the engine. */
    private GameState offerToBot(GameState state, ResourceBundle offered, ResourceBundle requested) {
        GameState stocked = withHoldings(state, otherOwner, offered);
        return accept(stocked, new ProposeTrade(otherOwner, SEAT, offered, requested));
    }

    private EventCard.ResourceCrisis resourceCrisisOn(Resource resource) {
        return ruleset.events().deck().stream()
                .filter(EventCard.ResourceCrisis.class::isInstance).map(EventCard.ResourceCrisis.class::cast)
                .filter(card -> card.resource() == resource).findFirst().orElseThrow();
    }

    /** The bot's city at level 2, upgraded before this round, so it may still upgrade now. */
    private static GameState atLevelTwo(GameState state) {
        return state.withPlayer(state.player(SEAT).withLevel(2, 0));
    }

    /** {@code card} warned for the next round, as step 2.2 would do. */
    private static GameState warned(GameState state, EventCard card) {
        return state.withEvents(state.eventDeck(), Optional.of(new EventWarning(state.round() + 1, card)),
                state.activeEvent());
    }

    /** The top opportunity card put face up, as step 2.3 would do (Round 1 has none yet). */
    private static GameState withOpenOpportunity(GameState state) {
        return state.withRevealedOpportunity(
                RegionalOpportunity.revealed(state.opportunityDeck().getFirst(), state.round()));
    }

    private static GameState withHoldings(GameState state, int seat, ResourceBundle holdings) {
        return state.withPlayer(state.player(seat).withHoldings(holdings));
    }

    private GameState accept(GameState state, GameCommand command) {
        GameResult result = GameEngine.apply(state, command, ruleset);
        return assertInstanceOf(GameResult.Accepted.class, result, () -> command + " -> " + result).state();
    }
}
