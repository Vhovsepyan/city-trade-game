package citytrade.engine.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One player's part in the event cards (Numbers Sheet 14, D2).
 *
 * @param crisisPolicy     D2: the policy for the warned crisis; back to PAY after the next event round starts
 * @param crisisPaidRounds the rounds in which the city paid a crisis in full (each earns Crisis Prestige)
 * @param usedOptions      ids of the optional events (Festival, Breakthrough) the player has used; once each
 */
public record EventParticipation(CrisisPolicy crisisPolicy, List<Integer> crisisPaidRounds, List<String> usedOptions) {

    public static final EventParticipation NONE = new EventParticipation(CrisisPolicy.PAY, List.of(), List.of());

    public EventParticipation {
        Objects.requireNonNull(crisisPolicy);
        crisisPaidRounds = List.copyOf(crisisPaidRounds);
        usedOptions = List.copyOf(usedOptions);
    }

    public EventParticipation withCrisisPolicy(CrisisPolicy policy) {
        return new EventParticipation(policy, crisisPaidRounds, usedOptions);
    }

    public EventParticipation withCrisisPaid(int round) {
        List<Integer> updated = new ArrayList<>(crisisPaidRounds);
        updated.add(round);
        return new EventParticipation(crisisPolicy, updated, usedOptions);
    }

    public EventParticipation withOptionUsed(String eventId) {
        List<String> updated = new ArrayList<>(usedOptions);
        updated.add(eventId);
        return new EventParticipation(crisisPolicy, crisisPaidRounds, updated);
    }

    public boolean paidCrisisIn(int round) {
        return crisisPaidRounds.contains(round);
    }

    public boolean hasUsedOption(String eventId) {
        return usedOptions.contains(eventId);
    }
}
