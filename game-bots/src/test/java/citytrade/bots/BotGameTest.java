package citytrade.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.GameEngine;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.AcceptTrade;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.StartRound;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.setup.GameSetup;
import citytrade.engine.state.GamePhase;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.TradeOffer;
import citytrade.engine.state.TradeOfferStatus;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BotGameTest {

    private final Ruleset ruleset = TestRulesets.prototype();

    private List<Bot> fourBaselineBots() {
        return Collections.nCopies(ruleset.playerCount(), new BaselineBot());
    }

    @Test
    void hundredGamesWithFourBaselineBotsFinishWithoutRejections() {
        for (long seed = 1; seed <= 100; seed++) {
            BotGame.Result result = BotGame.play(seed, ruleset, fourBaselineBots());

            assertEquals(List.of(), result.rejections(), "seed " + seed);
            assertEquals(GamePhase.FINISHED, result.finalState().phase(), "seed " + seed);
            assertEquals(ruleset.roundCount(), result.finalState().round(), "seed " + seed);
            assertTrue(result.finalState().finalResult().isPresent(), "seed " + seed);
            // The bots really play: cities grow and build, it is not a game of passes.
            assertTrue(result.commands().stream().anyMatch(UpgradeCity.class::isInstance), "seed " + seed);
            assertTrue(result.commands().stream().anyMatch(BuildBuilding.class::isInstance), "seed " + seed);
            for (PlayerState player : result.finalState().players()) {
                assertTrue(player.level() > 1, "seed " + seed + ", seat " + player.seat() + " never upgraded");
            }
        }
    }

    @Test
    void sameSeedPlaysTheSameGame() {
        BotGame.Result first = BotGame.play(7, ruleset, fourBaselineBots());
        BotGame.Result second = BotGame.play(7, ruleset, fourBaselineBots());

        assertEquals(first.commands(), second.commands());
        assertEquals(first.finalState(), second.finalState());
    }

    @Test
    void differentSeedsPlayDifferentGames() {
        BotGame.Result first = BotGame.play(1, ruleset, fourBaselineBots());
        BotGame.Result second = BotGame.play(2, ruleset, fourBaselineBots());

        assertTrue(!first.finalState().equals(second.finalState()));
    }

    @Test
    void hundredGamesWithFourTraderBotsFinishWithoutRejections() {
        List<Bot> traders = Collections.nCopies(ruleset.playerCount(), new TraderBot());
        for (long seed = 1; seed <= 100; seed++) {
            BotGame.Result result = BotGame.play(seed, ruleset, traders);

            assertEquals(List.of(), result.rejections(), "seed " + seed);
            assertEquals(GamePhase.FINISHED, result.finalState().phase(), "seed " + seed);
            assertTrue(result.commands().stream().anyMatch(ProposeTrade.class::isInstance), "seed " + seed);
            assertTrue(result.commands().stream().anyMatch(AcceptTrade.class::isInstance), "seed " + seed);
            assertTrue(result.commands().stream().anyMatch(PlaceBid.class::isInstance), "seed " + seed);
            for (PlayerState player : result.finalState().players()) {
                assertTrue(player.level() > 1, "seed " + seed + ", seat " + player.seat() + " never upgraded");
            }
        }
    }

    @Test
    void hundredGamesWithTradersAndBaselineBotsFinishWithoutRejections() {
        List<Bot> mix = List.of(new TraderBot(), new BaselineBot(), new TraderBot(), new BaselineBot());
        for (long seed = 1; seed <= 100; seed++) {
            BotGame.Result result = BotGame.play(seed, ruleset, mix);

            assertEquals(List.of(), result.rejections(), "seed " + seed);
            assertEquals(GamePhase.FINISHED, result.finalState().phase(), "seed " + seed);
        }
    }

    @Test
    void tradersPayMoreCrisesThanBaselineBots() {
        List<Bot> traders = Collections.nCopies(ruleset.playerCount(), new TraderBot());
        int paidByTraders = 0;
        int paidByBaseline = 0;
        for (long seed = 1; seed <= 100; seed++) {
            paidByTraders += paidCrises(BotGame.play(seed, ruleset, traders));
            paidByBaseline += paidCrises(BotGame.play(seed, ruleset, fourBaselineBots()));
        }

        assertTrue(paidByTraders > paidByBaseline, "traders " + paidByTraders + ", baseline " + paidByBaseline);
    }

    @Test
    void sameSeedPlaysTheSameTraderGame() {
        List<Bot> mix = List.of(new TraderBot(), new BaselineBot(), new TraderBot(), new TraderBot());
        BotGame.Result first = BotGame.play(11, ruleset, mix);
        BotGame.Result second = BotGame.play(11, ruleset, mix);

        assertEquals(first.commands(), second.commands());
        assertEquals(first.finalState(), second.finalState());
    }

    @Test
    void aSeatThatPassedIsAskedAgainAndAnswersALaterOffer() {
        // With these settings the trader in seat 0 has nothing to do in Round 1 and passes in the first pass.
        Bot quietTrader = new TraderBot(new TraderBot.Settings(100, 0, 0, 0));
        GameState start = GameSetup.create(5, ruleset);
        assertEquals(Optional.empty(), quietTrader.nextWindowCommand(
                playAllRoundOneUntilWindow(start, quietTrader), 0, ruleset));
        List<Bot> bots = List.of(quietTrader, new OneOfferPerRoundBot(), new PassingBot(), new PassingBot());

        BotGame.Result result = BotGame.play(5, ruleset, bots);

        TradeOffer first = result.finalState().tradeOffers().getFirst();
        assertEquals(1, first.proposerSeat());
        assertEquals(0, first.recipientSeat());
        assertEquals(1, first.roundCreated());
        assertEquals(TradeOfferStatus.ACCEPTED, first.status());
        assertTrue(result.commands().contains(new AcceptTrade(0, first.id())));
    }

    /** The Round 1 window of a new game where every seat keeps its first dealt objectives. */
    private GameState playAllRoundOneUntilWindow(GameState state, Bot bot) {
        GameState next = state;
        for (int seat = 0; seat < ruleset.playerCount(); seat++) {
            next = accepted(next, bot.chooseObjectives(next, seat, ruleset));
        }
        return accepted(next, new StartRound());
    }

    private GameState accepted(GameState state, GameCommand command) {
        GameResult result = GameEngine.apply(state, command, ruleset);
        return assertInstanceOf(GameResult.Accepted.class, result, () -> command + " -> " + result).state();
    }

    /** Never does anything in the window. */
    private static final class PassingBot implements Bot {

        @Override
        public ChooseObjectives chooseObjectives(GameState state, int seat, Ruleset ruleset) {
            return BotActions.keepFirstDealtObjectives(state, seat, ruleset);
        }

        @Override
        public Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset) {
            return Optional.empty();
        }
    }

    /** Once per round offers seat 0 one unit of its specialty for one unit of seat 0's specialty. */
    private static final class OneOfferPerRoundBot implements Bot {

        @Override
        public ChooseObjectives chooseObjectives(GameState state, int seat, Ruleset ruleset) {
            return BotActions.keepFirstDealtObjectives(state, seat, ruleset);
        }

        @Override
        public Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset) {
            boolean offered = state.tradeOffers().stream()
                    .anyMatch(offer -> offer.proposerSeat() == seat && offer.roundCreated() == state.round());
            if (offered) {
                return Optional.empty();
            }
            return Optional.of(new ProposeTrade(seat, 0,
                    ResourceBundle.EMPTY.with(state.player(seat).city().specialty(), 1),
                    ResourceBundle.EMPTY.with(state.player(0).city().specialty(), 1)));
        }
    }

    private static int paidCrises(BotGame.Result result) {
        return result.finalState().players().stream()
                .mapToInt(player -> player.eventParticipation().crisisPaidRounds().size())
                .sum();
    }

    @Test
    void needsOneBotPerSeat() {
        List<Bot> threeBots = Collections.nCopies(ruleset.playerCount() - 1, new BaselineBot());

        assertThrows(IllegalArgumentException.class, () -> BotGame.play(1, ruleset, threeBots));
    }
}
