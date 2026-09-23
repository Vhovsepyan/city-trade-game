package citytrade.engine.city;

import citytrade.engine.Payments;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.BuildBuilding;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.UpgradeCity;
import citytrade.engine.event.EventEffects;
import citytrade.engine.ruleset.BuildingEffect;
import citytrade.engine.ruleset.BuildingRules;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.BuiltBuilding;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.List;
import java.util.Optional;

/**
 * City levels and buildings, Numbers Sheet 6-7 and 10, D4. Upgrading and building happen in the window
 * and are paid at once (with this round's event cost discount, Numbers Sheet 14); new production, upkeep and building effects start next round, and the
 * Prestige is added in step 4.4 of the same round.
 */
public final class CityDevelopment {

    private CityDevelopment() {
    }

    public static GameResult upgrade(GameState state, UpgradeCity command, Ruleset ruleset) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        PlayerState player = state.player(command.seat());
        int newLevel = player.level() + 1;
        if (!ruleset.hasLevel(newLevel)) {
            return new GameResult.Rejected(RejectionCode.MAX_LEVEL_REACHED,
                    "city is already at the highest level " + player.level());
        }
        if (player.lastUpgradeRound() == state.round()) {
            return new GameResult.Rejected(RejectionCode.ALREADY_UPGRADED_THIS_ROUND,
                    "only one level per round; already upgraded in round " + state.round());
        }
        ResourceBundle cost = EventEffects.upgradeCost(state.activeEvent(), ruleset.level(newLevel).upgradeCost());
        Optional<GameResult.Rejected> unaffordable = Payments.checkAffordable(player.holdings(), cost);
        if (unaffordable.isPresent()) {
            return unaffordable.get();
        }
        PlayerState updated = player.withHoldings(player.holdings().minus(cost)).withLevel(newLevel, state.round());
        return new GameResult.Accepted(state.withPlayer(updated),
                List.of(new DomainEvent.CityUpgraded(command.seat(), newLevel, cost)));
    }

    public static GameResult build(GameState state, BuildBuilding command, Ruleset ruleset) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        Optional<BuildingRules> found = ruleset.building(command.buildingId());
        if (found.isEmpty()) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_BUILDING, "no building " + command.buildingId());
        }
        BuildingRules building = found.get();
        PlayerState player = state.player(command.seat());
        if (player.hasBuilt(building.id())) {
            return new GameResult.Rejected(RejectionCode.BUILDING_ALREADY_BUILT,
                    building.id() + " is already built; each building only once");
        }
        // The level counts as soon as it is reached, so an upgrade earlier in the same window unlocks buildings.
        if (player.level() < building.requiredLevel()) {
            return new GameResult.Rejected(RejectionCode.LEVEL_TOO_LOW,
                    building.id() + " needs level " + building.requiredLevel() + ", city is level " + player.level());
        }
        Optional<GameResult.Rejected> invalidChoice = checkChoice(player, building, command.chosenResource());
        if (invalidChoice.isPresent()) {
            return invalidChoice.get();
        }
        ResourceBundle cost = EventEffects.buildingCost(state.activeEvent(), building.cost());
        Optional<GameResult.Rejected> unaffordable = Payments.checkAffordable(player.holdings(), cost);
        if (unaffordable.isPresent()) {
            return unaffordable.get();
        }
        PlayerState updated = player.withHoldings(player.holdings().minus(cost))
                .withBuilding(new BuiltBuilding(building.id(), state.round(), command.chosenResource()));
        return new GameResult.Accepted(state.withPlayer(updated),
                List.of(new DomainEvent.BuildingBuilt(command.seat(), building.id(), cost)));
    }

    /** Step 4.4: Prestige for the level reached and the buildings built this round. */
    public static GameState awardPrestige(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        GameState next = state;
        for (PlayerState player : state.players()) {
            int gained = 0;
            if (player.lastUpgradeRound() == state.round()) {
                gained += ruleset.level(player.level()).reachPrestige();
            }
            for (BuiltBuilding built : player.buildings()) {
                if (built.roundBuilt() == state.round()) {
                    gained += ruleset.building(built.id()).orElseThrow().prestige();
                }
            }
            if (gained > 0) {
                next = next.withPlayer(player.withPrestige(player.prestige() + gained));
                events.add(new DomainEvent.PrestigeGained(player.seat(), gained));
            }
        }
        return next;
    }

    /** D4: a chosen-production building needs one non-specialty resource; other buildings take no choice. */
    private static Optional<GameResult.Rejected> checkChoice(PlayerState player, BuildingRules building,
            Optional<Resource> choice) {
        boolean needsChoice = building.effect() instanceof BuildingEffect.ChosenNonSpecialtyProduction;
        if (!needsChoice) {
            return choice.isEmpty() ? Optional.empty() : Optional.of(new GameResult.Rejected(
                    RejectionCode.INVALID_BUILDING_CHOICE, building.id() + " takes no resource choice"));
        }
        if (choice.isEmpty()) {
            return Optional.of(new GameResult.Rejected(RejectionCode.INVALID_BUILDING_CHOICE,
                    building.id() + " needs a chosen non-specialty resource"));
        }
        if (choice.get() == player.city().specialty()) {
            return Optional.of(new GameResult.Rejected(RejectionCode.INVALID_BUILDING_CHOICE,
                    building.id() + " cannot produce the city's specialty " + choice.get()));
        }
        return Optional.empty();
    }
}
