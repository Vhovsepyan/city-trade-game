package citytrade.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import citytrade.engine.CityType;
import citytrade.engine.ruleset.Ruleset;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SimulationMainTest {

    private static final String MIX = "baseline,trader,baseline,trader";

    private final Ruleset ruleset = TestRulesets.prototype();

    @TempDir
    Path out;

    private record Run(int exitCode, String stdout, String stderr) {
    }

    private Run run(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code = SimulationMain.run(args, new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
        return new Run(code, stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private Run runMix(Path directory, long seed) {
        return run("--ruleset", "prototype-001", "--rulesets-dir", TestRulesets.dir().toString(), "--games", "20",
                "--seed", String.valueOf(seed), "--bots", MIX, "--out", directory.toString());
    }

    private static String baseName(long seed) {
        return "prototype-001_baseline-trader-baseline-trader_seed" + seed + "_games20";
    }

    @Test
    void writesJsonSummaryAndCsvRows() throws IOException {
        Run result = runMix(out, 1);

        assertEquals(0, result.exitCode(), result.stderr());
        Path json = out.resolve(baseName(1) + ".json");
        Path csv = out.resolve(baseName(1) + ".csv");
        assertTrue(result.stdout().contains(json.toString()), result.stdout());

        JsonNode report = new ObjectMapper().readTree(json.toFile());
        assertEquals("prototype-001", report.get("ruleset").asText());
        assertEquals(20, report.get("games").asInt());
        assertEquals(0, report.get("rejections").asInt());
        assertEquals(List.of("baseline", "trader", "baseline", "trader"),
                new ObjectMapper().convertValue(report.get("botsBySeat"), List.class));
        double winRates = 0;
        for (CityType city : CityType.values()) {
            JsonNode cityReport = report.get("cities").get(city.name());
            winRates += cityReport.get("winRate").asDouble();
            assertEquals(20, cityReport.get("finalPrestige").get("count").asInt());
            assertTrue(cityReport.get("levelReach").has("2"), city.name());
            assertTrue(cityReport.get("levelReach").has("3"), city.name());
        }
        assertEquals(1.0, winRates, 0.01);
        assertEquals(80, report.get("finalPrestige").get("count").asInt());
        assertEquals(20, report.get("winningPrestige").get("count").asInt());
        assertEquals(ruleset.roundCount(), report.get("rounds").size());
        for (String part : List.of("EARLY", "MID", "LATE")) {
            assertTrue(report.get("demandByPhase").get(part).has("TECHNOLOGY"), part);
        }
        for (String section : List.of("economy", "contracts", "projects", "bots")) {
            assertTrue(report.has(section), section);
        }

        List<String> lines = Files.readAllLines(csv);
        assertEquals(1 + 20 * ruleset.playerCount(), lines.size());
        assertTrue(lines.getFirst().startsWith("seed,seat,city,bot,final_prestige,"), lines.getFirst());
        int columns = lines.getFirst().split(",", -1).length;
        lines.forEach(line -> assertEquals(columns, line.split(",", -1).length, line));
        assertTrue(lines.get(1).startsWith("1,0,"), lines.get(1));
    }

    @Test
    void sameArgumentsWriteTheSameFiles(@TempDir Path other) throws IOException {
        assertEquals(0, runMix(out, 5).exitCode());
        assertEquals(0, runMix(other, 5).exitCode());

        for (String suffix : List.of(".json", ".csv")) {
            assertEquals(Files.readString(out.resolve(baseName(5) + suffix)),
                    Files.readString(other.resolve(baseName(5) + suffix)), suffix);
        }
    }

    @Test
    void differentSeedsGiveDifferentGames() throws IOException {
        assertEquals(0, runMix(out, 1).exitCode());
        assertEquals(0, runMix(out, 100).exitCode());

        assertNotEquals(Files.readString(out.resolve(baseName(1) + ".csv")).lines().skip(1).toList(),
                Files.readString(out.resolve(baseName(100) + ".csv")).lines().skip(1).toList());
    }

    @Test
    void badArgumentsPrintUsageAndWriteNothing() throws IOException {
        Run result = run("--ruleset", "prototype-001", "--rulesets-dir", TestRulesets.dir().toString(),
                "--bots", "trader,trader", "--out", out.toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("--bots needs 1 or 4 bots"), result.stderr());
        assertTrue(result.stderr().contains("Usage:"), result.stderr());
        try (var files = Files.list(out)) {
            assertFalse(files.findAny().isPresent());
        }
    }

    @Test
    void missingRulesetIsAnError() {
        Run result = run("--ruleset", "prototype-999", "--rulesets-dir", TestRulesets.dir().toString(),
                "--out", out.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("prototype-999"), result.stderr());
    }
}
