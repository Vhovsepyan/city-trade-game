package citytrade.sim;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The command line of the simulation runner.
 *
 * @param ruleset     a ruleset name (looked up as {@code <rulesetsDir>/<name>.json}) or a path to a .json file
 * @param games       how many games to play
 * @param firstSeed   game i (0-based) is played with seed {@code firstSeed + i}
 * @param bots        one profile for every seat, or one per seat in seat order
 * @param rulesetsDir where ruleset names are looked up
 * @param outputDir   where the JSON and CSV files are written
 */
public record SimulationOptions(String ruleset, int games, long firstSeed, List<BotProfile> bots, Path rulesetsDir,
        Path outputDir) {

    public static final String USAGE = """
            Usage: --ruleset <name|file.json> [--games N] [--seed S] [--bots <bot>[,<bot>...]] \
            [--rulesets-dir DIR] [--out DIR]
              --ruleset       ruleset name (e.g. prototype-001) or path to a ruleset .json file (required)
              --games         number of games, at least 1 (default 100)
              --seed          seed of the first game; game i uses seed + i (default 1)
              --bots          one bot for all seats, or one per seat in seat order: baseline, trader \
            (default baseline)
              --rulesets-dir  directory with ruleset files (default rulesets)
              --out           output directory for the JSON and CSV files (default build/sim)""";

    private static final Set<String> KEYS = Set.of("--ruleset", "--games", "--seed", "--bots", "--rulesets-dir", "--out");

    public SimulationOptions {
        if (ruleset == null || ruleset.isBlank()) {
            throw new IllegalArgumentException("--ruleset is required");
        }
        if (games < 1) {
            throw new IllegalArgumentException("--games must be at least 1, but was " + games);
        }
        if (bots.isEmpty()) {
            throw new IllegalArgumentException("--bots needs at least one bot");
        }
        bots = List.copyOf(bots);
    }

    /** Parses {@code --key value} pairs; throws {@link IllegalArgumentException} with a readable message. */
    public static SimulationOptions parse(String[] args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("every option needs a value: " + String.join(" ", args));
        }
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String key = args[i];
            if (!KEYS.contains(key)) {
                throw new IllegalArgumentException("unknown option '" + key + "'");
            }
            if (values.put(key, args[i + 1]) != null) {
                throw new IllegalArgumentException("option " + key + " is given twice");
            }
        }
        return new SimulationOptions(
                values.get("--ruleset"),
                parseInt("--games", values.getOrDefault("--games", "100")),
                parseLong("--seed", values.getOrDefault("--seed", "1")),
                Arrays.stream(values.getOrDefault("--bots", "baseline").split(",", -1))
                        .map(BotProfile::fromCliName)
                        .toList(),
                Path.of(values.getOrDefault("--rulesets-dir", "rulesets")),
                Path.of(values.getOrDefault("--out", "build/sim")));
    }

    /** The ruleset file: {@code ruleset} itself if it names a .json file, else {@code <rulesetsDir>/<ruleset>.json}. */
    public Path rulesetFile() {
        return ruleset.endsWith(".json") ? Path.of(ruleset) : rulesetsDir.resolve(ruleset + ".json");
    }

    /** One profile per seat: a single profile fills every seat, otherwise the list must match the seat count. */
    public List<BotProfile> botsPerSeat(int playerCount) {
        if (bots.size() == 1) {
            return Collections.nCopies(playerCount, bots.getFirst());
        }
        if (bots.size() != playerCount) {
            throw new IllegalArgumentException("--bots needs 1 or " + playerCount + " bots, but got " + bots.size());
        }
        return bots;
    }

    private static int parseInt(String key, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number, but was '" + value + "'");
        }
    }

    private static long parseLong(String key, String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole number, but was '" + value + "'");
        }
    }
}
