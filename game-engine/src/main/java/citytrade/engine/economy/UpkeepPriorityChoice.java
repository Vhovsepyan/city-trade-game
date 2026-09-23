package citytrade.engine.economy;

import citytrade.engine.Resource;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.SetUpkeepPriority;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.HashSet;
import java.util.List;

/** D1: handles {@link SetUpkeepPriority}. The order must name every allowed upkeep resource exactly once. */
public final class UpkeepPriorityChoice {

    private UpkeepPriorityChoice() {
    }

    public static GameResult apply(GameState state, SetUpkeepPriority command) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        PlayerState player = state.player(command.seat());
        List<Resource> allowed = Upkeep.allowedResources(player);
        List<Resource> order = command.order();
        if (order.size() != allowed.size() || !new HashSet<>(order).equals(new HashSet<>(allowed))) {
            return new GameResult.Rejected(RejectionCode.INVALID_UPKEEP_PRIORITY,
                    "order must list each of " + allowed + " exactly once, but was " + order);
        }
        GameState next = state.withPlayer(player.withUpkeepPriority(order));
        return new GameResult.Accepted(next, List.of(new DomainEvent.UpkeepPrioritySet(command.seat())));
    }
}
