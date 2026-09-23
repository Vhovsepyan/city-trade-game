package citytrade.engine.economy;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.LevelRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.ruleset.StrainedRules;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;

/**
 * Step 1.2 (Strained penalty) and step 1.3 (production), Numbers Sheet 3-4 and 9.
 * Storage is not checked here: holdings may exceed the limit until step 4.5.
 */
public final class Production {

    private Production() {
    }

    /**
     * Step 1.2: a city that became Strained last round gets the penalty this round; the Strained
     * flag is cleared so it can be set again by this round's payments.
     */
    public static GameState applyStrainedPenalty(GameState state) {
        GameState next = state;
        for (PlayerState player : state.players()) {
            next = next.withPlayer(player.withStrained(false, player.strained()));
        }
        return next;
    }

    /** Step 1.3: every city receives the production of its level, minus an active Strained penalty. */
    public static GameState produce(GameState state, Ruleset ruleset) {
        GameState next = state;
        for (PlayerState player : state.players()) {
            ResourceBundle produced = productionOf(player, ruleset);
            next = next.withPlayer(player.withHoldings(player.holdings().plus(produced)));
        }
        return next;
    }

    /** What {@code player} produces this round. Production never goes below zero. */
    public static ResourceBundle productionOf(PlayerState player, Ruleset ruleset) {
        LevelRules level = ruleset.level(player.level());
        int specialty = level.specialtyProduction();
        int money = level.moneyProduction();
        if (player.strainedPenaltyActive()) {
            StrainedRules penalty = ruleset.strained();
            specialty = Math.max(0, specialty - penalty.specialtyProductionPenalty());
            money = Math.max(0, money - penalty.moneyIncomePenalty());
        }
        ResourceBundle produced = ResourceBundle.EMPTY.withMoney(money);
        for (Resource resource : Resource.values()) {
            int amount = resource == player.city().specialty() ? specialty : level.otherResourceProduction();
            produced = produced.with(resource, amount);
        }
        return produced;
    }
}
