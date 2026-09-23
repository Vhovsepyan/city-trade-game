package citytrade.ruleset.json;

import java.io.Serial;
import java.util.List;

/** The ruleset was parsed but breaks one or more validation rules. */
public class RulesetValidationException extends RulesetLoadException {

    @Serial
    private static final long serialVersionUID = 1L;

    // Never serialized in practice; the message also contains every error.
    private final transient List<String> errors;

    public RulesetValidationException(String source, List<String> errors) {
        super("Invalid ruleset " + source + ":\n  - " + String.join("\n  - ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
