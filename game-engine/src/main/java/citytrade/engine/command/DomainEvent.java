package citytrade.engine.command;

/** Something that happened in the game, produced by an accepted command. */
public sealed interface DomainEvent {

    /** The player kept their objectives. Which cards stays hidden, so the event carries only the seat. */
    record ObjectivesChosen(int seat) implements DomainEvent {
    }

    /** The automatic and world update of {@code round} are done; the trade window is open. */
    record RoundStarted(int round) implements DomainEvent {
    }

    /** All resolution steps of {@code round} are done. */
    record RoundResolved(int round) implements DomainEvent {
    }

    /** The last round is resolved; no more commands are accepted. */
    record GameFinished() implements DomainEvent {
    }
}
