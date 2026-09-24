package citytrade.bots;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.city.BuildingEffects;
import citytrade.engine.command.AcceptTrade;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.CancelTrade;
import citytrade.engine.command.ChooseObjectives;
import citytrade.engine.command.GameCommand;
import citytrade.engine.command.PlaceBid;
import citytrade.engine.command.ProposeTrade;
import citytrade.engine.command.RejectTrade;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.economy.Upkeep;
import citytrade.engine.event.EventEffects;
import citytrade.engine.market.Market;
import citytrade.engine.ruleset.BuildingRules;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.EventWarning;
import citytrade.engine.state.GameState;
import citytrade.engine.state.OpportunityStatus;
import citytrade.engine.state.PlayerState;
import citytrade.engine.state.RegionalOpportunity;
import citytrade.engine.state.TradeOffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Architecture 8.1 B. Like the baseline bot it grows its city (next level first, then buildings), but it also
 * <ul>
 *   <li>answers every offer made to it: accepts when what it gets is worth at least {@code acceptThresholdPercent}
 *       of what it gives, valued at the current market buy costs (Money = 1), and when it gives only
 *       resources it does not need itself; otherwise it rejects;</li>
 *   <li>offers its spare resources, specialty first, for a missing resource at equal market value, to the
 *       city with that specialty first, then to the others; at most one open offer and
 *       {@code maxOffersPerRound} offers per round, never twice to the same seat in one round. An offer still
 *       open at its next turn is cancelled: in {@link BotGame} every other seat had a turn to answer it;</li>
 *   <li>falls back to the market for the upgrade, like the baseline bot, when trading did not cover it;</li>
 *   <li>prepares for a warned crisis (Concept 21, D2): from the warning round on it keeps the crisis cost,
 *       plus one unit for the upkeep of step 1.4, out of trades and building, and buys it if missing. It keeps
 *       the default PAY policy;</li>
 *   <li>bids on open opportunities: at most {@code bidValuePercent} of the reward's market value over the
 *       remaining rounds, and at most {@code maxBidMoneyPercent} of the Money it does not need.</li>
 * </ul>
 * "Needs" are the cost of the next level (or, at the highest level, of the first building not yet built) plus
 * the crisis reserve. The bot only looks at what its player may see (D6, D7): its own holdings and offers,
 * city types, market prices, warned events and opportunities.
 */
public final class TraderBot implements Bot {

    /**
     * How the bot trades and bids. These are settings of this bot profile, not game rules.
     *
     * @param acceptThresholdPercent accept an offer when value received x 100 >= value given x this
     * @param maxOffersPerRound      trade offers the bot makes per round at most
     * @param bidValuePercent        share of the reward's market value over the remaining rounds it bids at most
     * @param maxBidMoneyPercent     share of its unneeded free Money it bids at most
     */
    public record Settings(int acceptThresholdPercent, int maxOffersPerRound, int bidValuePercent,
            int maxBidMoneyPercent) {

        public static final Settings DEFAULT = new Settings(100, 3, 50, 50);

        public Settings {
            if (acceptThresholdPercent < 0 || maxOffersPerRound < 0) {
                throw new IllegalArgumentException("settings must not be negative");
            }
            if (bidValuePercent < 0 || bidValuePercent > 100 || maxBidMoneyPercent < 0 || maxBidMoneyPercent > 100) {
                throw new IllegalArgumentException("bid percents must be between 0 and 100");
            }
        }
    }

    private final Settings settings;

    public TraderBot() {
        this(Settings.DEFAULT);
    }

    public TraderBot(Settings settings) {
        this.settings = settings;
    }

    @Override
    public ChooseObjectives chooseObjectives(GameState state, int seat, Ruleset ruleset) {
        return BotActions.keepFirstDealtObjectives(state, seat, ruleset);
    }

    @Override
    public Optional<GameCommand> nextWindowCommand(GameState state, int seat, Ruleset ruleset) {
        ResourceBundle reserve = crisisReserve(state, seat, ruleset);
        ResourceBundle needs = target(state, seat, ruleset).plus(reserve);
        return answerOffer(state, seat, needs, ruleset)
                .or(() -> cancelOwnOpenOffer(state, seat))
                .or(() -> development(state, seat, reserve, ruleset))
                .or(() -> offerForShortage(state, seat, needs, ruleset))
                .or(() -> marketPurchase(state, seat, reserve, ruleset))
                .or(() -> bid(state, seat, needs, ruleset))
                .or(() -> BotActions.sellExcess(state, seat, ruleset));
    }

    private Optional<GameCommand> answerOffer(GameState state, int seat, ResourceBundle needs, Ruleset ruleset) {
        for (TradeOffer offer : state.tradeOffers()) {
            if (offer.isOpen() && offer.recipientSeat() == seat) {
                return Optional.of(acceptable(state, seat, offer, needs, ruleset)
                        ? new AcceptTrade(seat, offer.id())
                        : new RejectTrade(seat, offer.id()));
            }
        }
        return Optional.empty();
    }

    private boolean acceptable(GameState state, int seat, TradeOffer offer, ResourceBundle needs, Ruleset ruleset) {
        if (!spare(state, seat, needs).covers(offer.requested())) {
            return false;
        }
        return value(state, offer.offered(), ruleset) * 100
                >= value(state, offer.requested(), ruleset) * settings.acceptThresholdPercent();
    }

    private static Optional<GameCommand> cancelOwnOpenOffer(GameState state, int seat) {
        return state.tradeOffers().stream()
                .filter(offer -> offer.isOpen() && offer.proposerSeat() == seat)
                .findFirst()
                .map(offer -> new CancelTrade(seat, offer.id()));
    }

    /** Upgrade or build like the baseline bot, but never with the crisis reserve. */
    private static Optional<GameCommand> development(GameState state, int seat, ResourceBundle reserve,
            Ruleset ruleset) {
        PlayerState player = state.player(seat);
        ResourceBundle usable = state.spendableHoldings(seat).minus(reserve);
        Optional<ResourceBundle> upgrade = upgradeCostThisRound(state, seat, ruleset);
        if (upgrade.isPresent()) {
            return usable.covers(upgrade.get()) ? Optional.of(new UpgradeCity(seat)) : Optional.empty();
        }
        for (BuildingRules building : ruleset.buildings()) {
            ResourceBundle cost = EventEffects.buildingCost(state.activeEvent(), building.cost());
            if (!player.hasBuilt(building.id()) && player.level() >= building.requiredLevel() && usable.covers(cost)) {
                return Optional.of(new BuildBuilding(seat, building.id(), BotActions.choiceFor(player, building)));
            }
        }
        return Optional.empty();
    }

    private Optional<GameCommand> offerForShortage(GameState state, int seat, ResourceBundle needs,
            Ruleset ruleset) {
        List<TradeOffer> madeThisRound = state.tradeOffers().stream()
                .filter(offer -> offer.proposerSeat() == seat && offer.roundCreated() == state.round())
                .toList();
        if (madeThisRound.size() >= settings.maxOffersPerRound()) {
            return Optional.empty();
        }
        ResourceBundle free = state.spendableHoldings(seat);
        ResourceBundle spare = spare(state, seat, needs);
        for (Resource wanted : Resource.values()) {
            int missing = needs.amountOf(wanted) - free.amountOf(wanted);
            if (missing <= 0) {
                continue;
            }
            for (int recipient : recipientsFor(state, seat, wanted)) {
                boolean alreadyAsked = madeThisRound.stream().anyMatch(offer -> offer.recipientSeat() == recipient);
                if (!alreadyAsked) {
                    Optional<GameCommand> offer = offerFor(state, seat, recipient, wanted, missing, spare, ruleset);
                    if (offer.isPresent()) {
                        return offer;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** The city with {@code wanted} as specialty first, then the other seats in seat order. */
    private static List<Integer> recipientsFor(GameState state, int seat, Resource wanted) {
        List<Integer> seats = new ArrayList<>();
        for (PlayerState other : state.players()) {
            if (other.seat() != seat) {
                seats.add(other.seat());
            }
        }
        seats.sort(Comparator.comparing((Integer other) -> state.player(other).city().specialty() != wanted));
        return seats;
    }

    /**
     * Asks for as many of the {@code missing} units as its spare resources can pay at market value, down to one.
     * Pays with the specialty first, then the other resources in enum order, never with {@code wanted} itself.
     */
    private static Optional<GameCommand> offerFor(GameState state, int seat, int recipient, Resource wanted,
            int missing, ResourceBundle spare, Ruleset ruleset) {
        Resource specialty = state.player(seat).city().specialty();
        List<Resource> payOrder = new ArrayList<>(List.of(Resource.values()));
        payOrder.sort(Comparator.comparing((Resource resource) -> resource != specialty));
        payOrder.remove(wanted);
        for (int quantity = missing; quantity >= 1; quantity--) {
            long wantedValue = (long) quantity * Market.buyCost(state, wanted, ruleset);
            ResourceBundle offered = ResourceBundle.EMPTY;
            long offeredValue = 0;
            for (Resource resource : payOrder) {
                int units = 0;
                while (offeredValue < wantedValue && units < spare.amountOf(resource)) {
                    units++;
                    offeredValue += Market.buyCost(state, resource, ruleset);
                }
                offered = offered.with(resource, units);
            }
            if (offeredValue >= wantedValue) {
                return Optional.of(new ProposeTrade(seat, recipient, offered,
                        ResourceBundle.EMPTY.with(wanted, quantity)));
            }
        }
        return Optional.empty();
    }

    /**
     * The market as fallback: the whole upgrade plus the crisis reserve if it can be bought this round,
     * otherwise just the missing crisis reserve.
     */
    private static Optional<GameCommand> marketPurchase(GameState state, int seat, ResourceBundle reserve,
            Ruleset ruleset) {
        ResourceBundle free = state.spendableHoldings(seat);
        Optional<ResourceBundle> upgrade = upgradeCostThisRound(state, seat, ruleset);
        if (upgrade.isPresent()) {
            Optional<GameCommand> purchase = BotActions.marketPurchaseFor(state, seat, upgrade.get(),
                    free.minus(reserve), ruleset).map(GameCommand.class::cast);
            if (purchase.isPresent()) {
                return purchase;
            }
        }
        return BotActions.marketPurchaseFor(state, seat, reserve, free, ruleset).map(GameCommand.class::cast);
    }

    private Optional<GameCommand> bid(GameState state, int seat, ResourceBundle needs, Ruleset ruleset) {
        int roundsLeft = ruleset.roundCount() - state.round();
        long budget = Math.max(0L, state.spendableHoldings(seat).money() - needs.money())
                * settings.maxBidMoneyPercent() / 100;
        for (RegionalOpportunity opportunity : state.opportunities()) {
            if (opportunity.status() != OpportunityStatus.OPEN || opportunity.bidOf(seat) > 0) {
                continue;
            }
            long worth = value(state, opportunity.card().productionReward(), ruleset) * roundsLeft
                    * settings.bidValuePercent() / 100;
            long amount = Math.min(worth, budget);
            if (amount >= 1) {
                return Optional.of(new PlaceBid(seat, opportunity.id(), (int) amount));
            }
        }
        return Optional.empty();
    }

    /** The upgrade cost if the city can still move up a level in this round. */
    private static Optional<ResourceBundle> upgradeCostThisRound(GameState state, int seat, Ruleset ruleset) {
        PlayerState player = state.player(seat);
        int nextLevel = player.level() + 1;
        if (!ruleset.hasLevel(nextLevel) || player.lastUpgradeRound() == state.round()) {
            return Optional.empty();
        }
        return Optional.of(EventEffects.upgradeCost(state.activeEvent(), ruleset.level(nextLevel).upgradeCost()));
    }

    /** What the bot saves for: the next level, or at the highest level the first building not yet built. */
    private static ResourceBundle target(GameState state, int seat, Ruleset ruleset) {
        PlayerState player = state.player(seat);
        int nextLevel = player.level() + 1;
        if (ruleset.hasLevel(nextLevel)) {
            return EventEffects.upgradeCost(state.activeEvent(), ruleset.level(nextLevel).upgradeCost());
        }
        return ruleset.buildings().stream()
                .filter(building -> !player.hasBuilt(building.id()) && player.level() >= building.requiredLevel())
                .findFirst()
                .map(building -> EventEffects.buildingCost(state.activeEvent(), building.cost()))
                .orElse(ResourceBundle.EMPTY);
    }

    /**
     * What the city must still hold after this window to pay the crisis warned for the next round in step 2.1.
     * Step 1.4 comes first and may take one unit of each non-specialty resource, so one extra unit of those is
     * kept when upkeep is due. For the neutral crisis the non-specialty resources held most are kept (D1 order).
     */
    static ResourceBundle crisisReserve(GameState state, int seat, Ruleset ruleset) {
        PlayerState player = state.player(seat);
        Optional<EventWarning> warning = state.eventWarning();
        if (warning.isEmpty() || warning.get().round() != state.round() + 1
                || player.level() < ruleset.events().crisisMinLevel()) {
            return ResourceBundle.EMPTY;
        }
        int upkeepDue = ruleset.level(player.level()).upkeepResources()
                - BuildingEffects.upkeepReduction(player, state.round() + 1, ruleset);
        int upkeepUnit = upkeepDue > 0 ? 1 : 0;
        Resource specialty = player.city().specialty();
        return switch (warning.get().card()) {
            case EventCard.ResourceCrisis crisis -> ResourceBundle.EMPTY.with(crisis.resource(),
                    crisis.amount() + (crisis.resource() == specialty ? 0 : upkeepUnit));
            case EventCard.NonSpecialtyCrisis crisis -> {
                List<Resource> heldMost = new ArrayList<>(Upkeep.allowedResources(player));
                heldMost.sort(Comparator.comparingInt((Resource r) -> player.holdings().amountOf(r)).reversed());
                ResourceBundle reserve = ResourceBundle.EMPTY;
                for (Resource resource : heldMost.subList(0, Math.min(crisis.differentResources(), heldMost.size()))) {
                    reserve = reserve.with(resource, 1 + upkeepUnit);
                }
                yield reserve;
            }
            default -> ResourceBundle.EMPTY;
        };
    }

    /** Free holdings above the needs, per resource and Money; never negative. */
    private static ResourceBundle spare(GameState state, int seat, ResourceBundle needs) {
        ResourceBundle left = state.spendableHoldings(seat).minus(needs);
        ResourceBundle spare = left.withMoney(Math.max(0, left.money()));
        for (Resource resource : Resource.values()) {
            spare = spare.with(resource, Math.max(0, left.amountOf(resource)));
        }
        return spare;
    }

    /** Market value of a bundle: each unit at its current buy cost, Money at face value. */
    private static long value(GameState state, ResourceBundle bundle, Ruleset ruleset) {
        long total = bundle.money();
        for (Resource resource : Resource.values()) {
            total += (long) bundle.amountOf(resource) * Market.buyCost(state, resource, ruleset);
        }
        return total;
    }
}
