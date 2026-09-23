package citytrade.engine.economy;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.city.BuildingEffects;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Step 1.4, Numbers Sheet 8-9 and D1. Upkeep is paid with one unit each of DIFFERENT non-specialty
 * resources; never with the specialty and never with Money. What can be paid is paid; if anything is
 * missing the city becomes Strained.
 */
public final class Upkeep {

    private Upkeep() {
    }

    public static GameState pay(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        GameState next = state;
        for (PlayerState player : state.players()) {
            int due = Math.max(0, ruleset.level(player.level()).upkeepResources()
                    - BuildingEffects.upkeepReduction(player, state.round(), ruleset));
            if (due == 0) {
                continue;
            }
            ResourceBundle paid = payment(player, due);
            int missing = due - totalResources(paid);
            PlayerState updated = player.withHoldings(player.holdings().minus(paid));
            events.add(new DomainEvent.UpkeepPaid(player.seat(), paid, missing));
            if (missing > 0) {
                updated = updated.withStrained(true, updated.strainedPenaltyActive());
                events.add(new DomainEvent.CityStrained(player.seat()));
            }
            next = next.withPlayer(updated);
        }
        return next;
    }

    /**
     * The resources {@code player} pays for {@code due} upkeep: at most one unit of each allowed resource,
     * in the D1 order. Also used for the neutral crisis (D2). Fewer than {@code due} units if not held.
     */
    public static ResourceBundle payment(PlayerState player, int due) {
        ResourceBundle paid = ResourceBundle.EMPTY;
        int count = 0;
        for (Resource resource : paymentOrder(player)) {
            if (count == due) {
                break;
            }
            if (player.holdings().amountOf(resource) > 0) {
                paid = paid.with(resource, 1);
                count++;
            }
        }
        return paid;
    }

    /**
     * D1: the player's own order if set; otherwise the allowed resources the player has most of first,
     * ties in the fixed order Food, Energy, Materials, Technology (the enum order; the sort is stable).
     */
    static List<Resource> paymentOrder(PlayerState player) {
        List<Resource> allowed = allowedResources(player);
        if (!player.upkeepPriority().isEmpty()) {
            // SetUpkeepPriority already rejects the specialty; the filter keeps "never the specialty" local here.
            return player.upkeepPriority().stream().filter(allowed::contains).toList();
        }
        List<Resource> order = new ArrayList<>(allowed);
        order.sort(Comparator.comparingInt((Resource r) -> player.holdings().amountOf(r)).reversed());
        return order;
    }

    /** Every resource except the city's specialty, in enum order. */
    public static List<Resource> allowedResources(PlayerState player) {
        return Arrays.stream(Resource.values()).filter(r -> r != player.city().specialty()).toList();
    }

    private static int totalResources(ResourceBundle bundle) {
        return Arrays.stream(Resource.values()).mapToInt(bundle::amountOf).sum();
    }
}
