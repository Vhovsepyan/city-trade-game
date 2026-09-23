package citytrade.engine.event;

import citytrade.engine.ResourceBundle;
import citytrade.engine.command.DomainEvent;
import citytrade.engine.command.GameResult;
import citytrade.engine.command.RejectionCode;
import citytrade.engine.command.SetCrisisPolicy;
import citytrade.engine.economy.Upkeep;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.Ruleset;
import citytrade.engine.state.CrisisPolicy;
import citytrade.engine.state.GameState;
import citytrade.engine.state.PlayerState;
import java.util.List;
import java.util.Optional;

/**
 * Crisis events, Numbers Sheet 9 and 14, D2. In step 2.1 every city at the crisis level pays the whole
 * crisis cost or nothing: PAY policy and enough resources = paid (Crisis Prestige in step 4.4), otherwise
 * Strained. Strained is a flag, so a city already Strained from upkeep is Strained once (Watch List 3).
 */
public final class Crises {

    private Crises() {
    }

    /** D2: only in the window of the round in which a crisis is warned. */
    public static GameResult setPolicy(GameState state, SetCrisisPolicy command) {
        if (!state.hasSeat(command.seat())) {
            return new GameResult.Rejected(RejectionCode.UNKNOWN_PLAYER, "no player at seat " + command.seat());
        }
        boolean crisisWarned = state.eventWarning().map(warning -> EventEffects.isCrisis(warning.card()))
                .orElse(false);
        if (!crisisWarned) {
            return new GameResult.Rejected(RejectionCode.NO_CRISIS_WARNED, "the warned event is not a crisis");
        }
        PlayerState player = state.player(command.seat());
        PlayerState updated = player.withEventParticipation(
                player.eventParticipation().withCrisisPolicy(command.policy()));
        return new GameResult.Accepted(state.withPlayer(updated),
                List.of(new DomainEvent.CrisisPolicySet(command.seat())));
    }

    /**
     * Step 2.1. Afterwards every policy is back to the default PAY: a policy belongs to the warned event,
     * which is now active.
     */
    public static GameState pay(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        if (state.activeEvent().isEmpty()) {
            return state;
        }
        EventCard card = state.activeEvent().get();
        GameState next = state;
        for (PlayerState player : state.players()) {
            PlayerState updated = player;
            if (EventEffects.isCrisis(card) && player.level() >= ruleset.events().crisisMinLevel()) {
                updated = resolveCrisis(player, card, state.round(), events);
            }
            updated = updated.withEventParticipation(updated.eventParticipation().withCrisisPolicy(CrisisPolicy.PAY));
            next = next.withPlayer(updated);
        }
        return next;
    }

    /** Step 4.4: Crisis Prestige for each city that paid this round's crisis in full. */
    public static GameState awardPrestige(GameState state, Ruleset ruleset, List<DomainEvent> events) {
        GameState next = state;
        for (PlayerState player : state.players()) {
            if (player.eventParticipation().paidCrisisIn(state.round())) {
                int gained = ruleset.events().crisisPrestige();
                next = next.withPlayer(player.withPrestige(player.prestige() + gained));
                events.add(new DomainEvent.PrestigeGained(player.seat(), gained));
            }
        }
        return next;
    }

    private static PlayerState resolveCrisis(PlayerState player, EventCard card, int round,
            List<DomainEvent> events) {
        boolean skipped = player.eventParticipation().crisisPolicy() == CrisisPolicy.SKIP;
        Optional<ResourceBundle> payment = skipped ? Optional.empty() : fullPayment(player, card);
        if (payment.isPresent()) {
            events.add(new DomainEvent.CrisisPaid(player.seat(), card.id(), payment.get()));
            return player.withHoldings(player.holdings().minus(payment.get()))
                    .withEventParticipation(player.eventParticipation().withCrisisPaid(round));
        }
        events.add(new DomainEvent.CrisisNotPaid(player.seat(), card.id(), skipped));
        events.add(new DomainEvent.CityStrained(player.seat()));
        return player.withStrained(true, player.strainedPenaltyActive());
    }

    /** The whole crisis cost if the player holds it; no partial payment (Numbers Sheet 9). */
    private static Optional<ResourceBundle> fullPayment(PlayerState player, EventCard card) {
        return switch (card) {
            case EventCard.ResourceCrisis c -> {
                ResourceBundle cost = ResourceBundle.EMPTY.with(c.resource(), c.amount());
                yield player.holdings().covers(cost) ? Optional.of(cost) : Optional.empty();
            }
            // D2: the different non-specialty resources are picked in the D1 upkeep order, from what the city holds.
            case EventCard.NonSpecialtyCrisis c -> {
                ResourceBundle picked = Upkeep.payment(player, c.differentResources());
                yield picked.resourceUnits() == c.differentResources() ? Optional.of(picked) : Optional.empty();
            }
            default -> throw new IllegalArgumentException(card.id() + " is not a crisis");
        };
    }
}
