package citytrade.engine.event;

import citytrade.engine.Payments;
import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.UseEventOption;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.List;
import java.util.Optional;

/**
 * Optional event actions, Numbers Sheet 14 (Public Festival, Research Breakthrough) and D4. Paid during
 * the window of the event round, once per player. Festival Prestige is added in step 4.4; Breakthrough
 * production starts next round (this round's production is already done).
 */
public final class EventOptions {

    private EventOptions() {
    }

    public static GameResult use(GameState state, UseEventOption command) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        EventCard card = state.activeEvent().orElse(null);
        ResourceBundle cost;
        switch (card) {
            case EventCard.PrestigePurchase p -> cost = p.cost();
            case EventCard.ProductionPurchase p -> cost = p.cost();
            case null, default -> {
                return new GameResult.Rejected(RejectionCode.NO_EVENT_OPTION, "this round's event has no option");
            }
        }
        PlayerState player = state.player(command.seat());
        if (player.eventParticipation().hasUsedOption(card.id())) {
            return new GameResult.Rejected(RejectionCode.EVENT_OPTION_ALREADY_USED,
                    card.id() + " can be used once per player");
        }
        Optional<GameResult.Rejected> invalidChoice = checkChoice(player, card, command.chosenResource());
        if (invalidChoice.isPresent()) {
            return invalidChoice.get();
        }
        Optional<GameResult.Rejected> unaffordable = Payments.checkAffordable(player.holdings(), cost);
        if (unaffordable.isPresent()) {
            return unaffordable.get();
        }
        PlayerState updated = player.withHoldings(player.holdings().minus(cost))
                .withEventParticipation(player.eventParticipation().withOptionUsed(card.id()));
        if (card instanceof EventCard.ProductionPurchase p) {
            Resource chosen = command.chosenResource().orElseThrow();
            ResourceBundle extra = updated.extraProduction();
            updated = updated.withExtraProduction(
                    extra.with(chosen, extra.amountOf(chosen) + p.chosenNonSpecialtyProduction()));
        }
        return new GameResult.Accepted(state.withPlayer(updated), List.of(
                new DomainEvent.EventOptionUsed(command.seat(), card.id(), cost, command.chosenResource())));
    }

    /** Step 4.4: Prestige for each player who used this round's Prestige option. */
    public static GameState awardPrestige(GameState state, List<DomainEvent> events) {
        if (!(state.activeEvent().orElse(null) instanceof EventCard.PrestigePurchase card)) {
            return state;
        }
        GameState next = state;
        for (PlayerState player : state.players()) {
            if (player.eventParticipation().hasUsedOption(card.id())) {
                next = next.withPlayer(player.withPrestige(player.prestige() + card.prestige()));
                events.add(new DomainEvent.PrestigeGained(player.seat(), card.prestige()));
            }
        }
        return next;
    }

    /** D4: a production option needs one non-specialty resource; other options take no choice. */
    private static Optional<GameResult.Rejected> checkChoice(PlayerState player, EventCard card,
            Optional<Resource> choice) {
        if (!(card instanceof EventCard.ProductionPurchase)) {
            return choice.isEmpty() ? Optional.empty() : Optional.of(new GameResult.Rejected(
                    RejectionCode.INVALID_EVENT_CHOICE, card.id() + " takes no resource choice"));
        }
        if (choice.isEmpty()) {
            return Optional.of(new GameResult.Rejected(RejectionCode.INVALID_EVENT_CHOICE,
                    card.id() + " needs a chosen non-specialty resource"));
        }
        if (choice.get() == player.city().specialty()) {
            return Optional.of(new GameResult.Rejected(RejectionCode.INVALID_EVENT_CHOICE,
                    card.id() + " cannot add the city's specialty " + choice.get()));
        }
        return Optional.empty();
    }
}
