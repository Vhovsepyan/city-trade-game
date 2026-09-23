package citytrade.engine.economy;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.city.BuildingEffects;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.List;

/**
 * Step 4.5, Numbers Sheet 5: F, E, M, T above the storage limit are discarded. This is the only place
 * the limit is enforced, so holdings may exceed it during the window. Money has no limit.
 */
public final class Storage {

    private Storage() {
    }

    public static GameState discardExcess(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        GameState next = state;
        for (PlayerState player : state.players()) {
            int limit = limitOf(player, state.round(), ruleset);
            ResourceBundle excess = ResourceBundle.EMPTY;
            for (Resource resource : Resource.values()) {
                excess = excess.with(resource, Math.max(0, player.holdings().amountOf(resource) - limit));
            }
            if (!excess.equals(ResourceBundle.EMPTY)) {
                next = next.withPlayer(player.withHoldings(player.holdings().minus(excess)));
                events.add(new DomainEvent.ExcessDiscarded(player.seat(), excess));
            }
        }
        return next;
    }

    /** The limit for each of F, E, M, T in {@code round}: the base limit plus active storage buildings. */
    public static int limitOf(PlayerState player, int round, Ruleset ruleset) {
        return ruleset.storage().resourceLimit() + BuildingEffects.storageBonus(player, round, ruleset);
    }
}
