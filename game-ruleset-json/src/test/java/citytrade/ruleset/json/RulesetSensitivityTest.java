package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import citytrade.engine.CityType;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.state.FinalScore;
import citytrade.engine.state.GameState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * No balance numbers in Java: the same scripted game with one ruleset value changed gives exactly the result that
 * value predicts. A number hardcoded in the engine would keep the old result.
 */
class RulesetSensitivityTest {

    private final ScriptedGame.Played base = playWith(json -> { });

    @Test
    void objectivePrestigeComesFromTheRuleset() {
        ScriptedGame.Played changed = playWith(json -> ((ObjectNode) json.get("objectives")).put("completedPrestige", 5));

        for (int seat = 0; seat < base.finalState().players().size(); seat++) {
            FinalScore before = score(base, seat);
            FinalScore after = score(changed, seat);
            assertEquals(before.completedObjectives(), after.completedObjectives());
            assertEquals(before.visiblePrestige(), after.visiblePrestige());
            assertEquals(after.completedObjectives().size() * 5, after.hiddenPrestige());
        }
    }

    @Test
    void levelPrestigeComesFromTheRuleset() {
        // Every city reaches level 2 once in the script.
        ScriptedGame.Played changed = playWith(json -> ((ObjectNode) json.get("levels").get(1)).put("reachPrestige", 4));

        for (int seat = 0; seat < base.finalState().players().size(); seat++) {
            assertEquals(score(base, seat).visiblePrestige() + 3, score(changed, seat).visiblePrestige());
        }
    }

    @Test
    void buildingPrestigeComesFromTheRuleset() {
        // Only the Energy city builds a Civic Center (prestige 2 in prototype-001).
        ScriptedGame.Played changed = playWith(json -> ((ObjectNode) building(json, "CIVIC_CENTER")).put("prestige", 6));

        int energy = seatOf(base.finalState(), CityType.ENERGY);
        for (int seat = 0; seat < base.finalState().players().size(); seat++) {
            int expectedGain = seat == energy ? 4 : 0;
            assertEquals(score(base, seat).visiblePrestige() + expectedGain, score(changed, seat).visiblePrestige());
        }
    }

    @Test
    void contractBreakPenaltyComesFromTheRuleset() {
        ScriptedGame.Played changed = playWith(json -> ((ObjectNode) json.get("contracts")).put("breakPrestigePenalty", 3));

        List<DomainEvent.ContractBroken> before = events(base, DomainEvent.ContractBroken.class);
        List<DomainEvent.ContractBroken> after = events(changed, DomainEvent.ContractBroken.class);
        assertEquals(2, after.size());
        for (int i = 0; i < after.size(); i++) {
            assertEquals(before.get(i).prestigeLost() + 2, after.get(i).prestigeLost());
        }
    }

    @Test
    void marketStartPriceComesFromTheRuleset() {
        // Step C (buy 5, sell 2) instead of B (buy 4, sell 1): the first buy and sell of the game use it.
        ScriptedGame.Played changed = playWith(json -> ((ObjectNode) json.get("market")).put("startStep", "C"));

        assertEquals(4, events(base, DomainEvent.MarketBought.class).getFirst().totalCost());
        assertEquals(5, events(changed, DomainEvent.MarketBought.class).getFirst().totalCost());
        assertEquals(1, events(base, DomainEvent.MarketSold.class).getFirst().totalValue());
        assertEquals(2, events(changed, DomainEvent.MarketSold.class).getFirst().totalValue());
    }

    @Test
    void startingMoneyComesFromTheRuleset() {
        ScriptedGame.Played changed = playWith(json -> ((ObjectNode) json.get("starting")).put("money", 9));

        // The first state after setup (the first objective choice) shows the starting Money of every city.
        for (int seat = 0; seat < base.finalState().players().size(); seat++) {
            assertEquals(6, base.states().getFirst().player(seat).holdings().money());
            assertEquals(9, changed.states().getFirst().player(seat).holdings().money());
        }
        assertNotEquals(base.finalState(), changed.finalState());
    }

    private static ScriptedGame.Played playWith(Consumer<ObjectNode> change) {
        ObjectNode json = TestRulesets.prototypeJson();
        change.accept(json);
        return ScriptedGame.play(FullGameTest.SEED, RulesetLoader.parse(json.toString(), "changed prototype-001"));
    }

    private static JsonNode building(ObjectNode json, String id) {
        for (JsonNode building : json.get("buildings")) {
            if (building.get("id").asText().equals(id)) {
                return building;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static FinalScore score(ScriptedGame.Played played, int seat) {
        return played.finalState().finalResult().orElseThrow().scoreOf(seat);
    }

    private static <T extends DomainEvent> List<T> events(ScriptedGame.Played played, Class<T> type) {
        return played.events().stream().flatMap(List::stream).filter(type::isInstance).map(type::cast).toList();
    }

    private static int seatOf(GameState state, CityType city) {
        return state.players().stream().filter(p -> p.city() == city).findFirst().orElseThrow().seat();
    }
}
