package citytrade.ruleset.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.ruleset.Ruleset;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Parsing is strict: bad JSON shape fails with a clear message instead of silent defaults. */
class RulesetLoaderTest {

    @TempDir
    Path tempDir;

    private static RulesetLoadException parseFails(ObjectNode json) {
        return assertThrows(RulesetLoadException.class, () -> RulesetLoader.parse(json.toString(), "test-ruleset"));
    }

    @Test
    void loadsTheSameRulesetFromFileAndFromText() {
        Ruleset fromFile = RulesetLoader.load(TestRulesets.PROTOTYPE_001);
        Ruleset fromText = RulesetLoader.parse(TestRulesets.prototypeJson().toString(), "text");
        assertEquals(fromFile, fromText);
    }

    @Test
    void prototype002LoadsAndOnlyContainsApprovedBalanceChanges() {
        RulesetLoader.load(TestRulesets.PROTOTYPE_002);

        ObjectNode prototype001 = TestRulesets.prototypeJson();
        ObjectNode expectedPrototype002 = prototype001.deepCopy();
        expectedPrototype002.put("version", "prototype-002");
        ((ObjectNode) expectedPrototype002.withArray("levels").get(1).get("upgradeCost"))
                .put("food", 4).put("energy", 4).put("materials", 4).put("technology", 4);
        ((ObjectNode) expectedPrototype002.withArray("buildings").get(7).get("cost")).put("money", 10);
        ((ObjectNode) expectedPrototype002.withArray("buildings").get(4).get("cost"))
                .put("food", 1).put("energy", 2).put("materials", 1).put("technology", 1);

        assertEquals("prototype-001", prototype001.get("version").asText());
        assertEquals(3, prototype001.withArray("levels").get(1).get("upgradeCost").get("food").asInt());
        assertEquals(5, prototype001.withArray("buildings").get(7).get("cost").get("money").asInt());
        assertEquals(2, prototype001.withArray("buildings").get(4).get("cost").get("food").asInt());
        assertEquals(expectedPrototype002, TestRulesets.prototype002Json());
    }

    @Test
    void rejectsVersionThatDoesNotMatchFileName() throws IOException {
        Path file = tempDir.resolve("prototype-999.json");
        Files.writeString(file, TestRulesets.prototypeJson().toString(), StandardCharsets.UTF_8);
        RulesetValidationException exception = assertThrows(RulesetValidationException.class,
                () -> RulesetLoader.load(file));
        assertTrue(exception.errors().getFirst().contains("'prototype-001' does not match the file name"),
                exception::getMessage);
    }

    @Test
    void rejectsMissingFile() {
        RulesetLoadException exception = assertThrows(RulesetLoadException.class,
                () -> RulesetLoader.load(tempDir.resolve("missing.json")));
        assertTrue(exception.getMessage().startsWith("Cannot read ruleset file"), exception::getMessage);
    }

    @Test
    void rejectsMalformedJson() {
        RulesetLoadException exception = assertThrows(RulesetLoadException.class,
                () -> RulesetLoader.parse("{ \"version\": ", "test-ruleset"));
        assertTrue(exception.getMessage().startsWith("Cannot parse ruleset test-ruleset"), exception::getMessage);
    }

    @Test
    void rejectsEmptyText() {
        assertThrows(RulesetLoadException.class, () -> RulesetLoader.parse("", "test-ruleset"));
    }

    @Test
    void rejectsMissingValue() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) json.get("storage")).remove("resourceLimit");
        assertTrue(parseFails(json).getMessage().contains("resourceLimit"));
    }

    @Test
    void rejectsMissingSection() {
        ObjectNode json = TestRulesets.prototypeJson();
        json.remove("contracts");
        assertTrue(parseFails(json).getMessage().contains("contracts"));
    }

    @Test
    void rejectsNullValue() {
        ObjectNode json = TestRulesets.prototypeJson();
        json.putNull("market");
        assertTrue(parseFails(json).getMessage().contains("market"));
    }

    @Test
    void rejectsUnknownProperty() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) json.get("storage")).put("moneyLimit", 5);
        assertTrue(parseFails(json).getMessage().contains("moneyLimit"));
    }

    @Test
    void rejectsUnknownEventType() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) json.get("events").withArray("deck").get(0)).put("type", "METEOR");
        assertTrue(parseFails(json).getMessage().contains("METEOR"));
    }

    @Test
    void rejectsUnknownResource() {
        ObjectNode json = TestRulesets.prototypeJson();
        ((ObjectNode) json.get("events").withArray("deck").get(0)).put("resource", "WATER");
        assertTrue(parseFails(json).getMessage().contains("WATER"));
    }

    @Test
    void rejectsNumberAsText() {
        ObjectNode json = TestRulesets.prototypeJson();
        json.put("roundCount", "14");
        parseFails(json);
    }

    @Test
    void rejectsFractionalNumber() {
        ObjectNode json = TestRulesets.prototypeJson();
        json.put("roundCount", 14.5);
        parseFails(json);
    }

    @Test
    void rejectsDuplicateKey() {
        String json = TestRulesets.prototypeJson().toString().replaceFirst("\\{", "{\"roundCount\":13,");
        assertThrows(RulesetLoadException.class, () -> RulesetLoader.parse(json, "test-ruleset"));
    }
}
