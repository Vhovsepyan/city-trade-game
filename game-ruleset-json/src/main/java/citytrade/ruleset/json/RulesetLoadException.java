package citytrade.ruleset.json;

import java.io.Serial;

/** A ruleset file could not be read, parsed or validated. */
public class RulesetLoadException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RulesetLoadException(String message) {
        super(message);
    }

    public RulesetLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
