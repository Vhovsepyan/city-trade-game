package citytrade.ruleset.json;

import citytrade.engine.ruleset.BuildingEffect;
import citytrade.engine.ruleset.EventCard;
import citytrade.engine.ruleset.ObjectiveCard;
import citytrade.engine.ruleset.Ruleset;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Reads a ruleset JSON file into an engine {@link Ruleset} and validates it. */
public final class RulesetLoader {

    private static final String FILE_SUFFIX = ".json";

    // Strict on purpose: a missing, null, unknown or mistyped value is an error, never a silent default.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .addMixIn(BuildingEffect.class, JsonTypeMixins.BuildingEffectMixin.class)
            .addMixIn(EventCard.class, JsonTypeMixins.EventCardMixin.class)
            .addMixIn(ObjectiveCard.class, JsonTypeMixins.ObjectiveCardMixin.class)
            .build();

    private RulesetLoader() {
    }

    /** Loads a ruleset file. Its "version" must match the file name (prototype-001.json -> prototype-001). */
    public static Ruleset load(Path file) {
        String json;
        try {
            json = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RulesetLoadException("Cannot read ruleset file " + file + ": " + e.getMessage(), e);
        }
        Ruleset ruleset = parse(json, file.toString());
        String fileName = file.getFileName().toString();
        String expectedVersion = fileName.endsWith(FILE_SUFFIX)
                ? fileName.substring(0, fileName.length() - FILE_SUFFIX.length())
                : fileName;
        if (!ruleset.version().equals(expectedVersion)) {
            throw new RulesetValidationException(file.toString(), List.of(
                    "version: '" + ruleset.version() + "' does not match the file name '" + fileName + "'"));
        }
        return ruleset;
    }

    /** Parses and validates ruleset JSON text. {@code source} is only used in error messages. */
    public static Ruleset parse(String json, String source) {
        Ruleset ruleset;
        try {
            ruleset = MAPPER.readValue(json, Ruleset.class);
        } catch (JacksonException e) {
            throw new RulesetLoadException("Cannot parse ruleset " + source + ": " + e.getMessage(), e);
        }
        if (ruleset == null) {
            throw new RulesetLoadException("Cannot parse ruleset " + source + ": the file is empty or null");
        }
        List<String> errors = RulesetValidator.validate(ruleset);
        if (!errors.isEmpty()) {
            throw new RulesetValidationException(source, errors);
        }
        return ruleset;
    }
}
