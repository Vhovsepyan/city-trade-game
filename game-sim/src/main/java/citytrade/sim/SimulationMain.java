package citytrade.sim;

import citytrade.engine.CityType;
import citytrade.engine.ruleset.Ruleset;
import citytrade.ruleset.json.RulesetLoader;
import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command line entry: {@code ./gradlew :game-sim:run --args="--ruleset prototype-001 --games 1000 --seed 1"}.
 * Writes the JSON summary and the CSV rows, and prints a short overview.
 */
public final class SimulationMain {

    private SimulationMain() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /** Runs the simulation; returns 0 on success, 2 for bad arguments, 1 for other failures. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        SimulationOptions options;
        Ruleset ruleset;
        List<BotProfile> bots;
        try {
            options = SimulationOptions.parse(args);
            ruleset = RulesetLoader.load(options.rulesetFile());
            bots = options.botsPerSeat(ruleset.playerCount());
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            err.println(SimulationOptions.USAGE);
            return 2;
        } catch (RuntimeException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
        try {
            List<GameRecord> games = Simulation.run(ruleset, options.games(), options.firstSeed(), bots);
            SimulationReport report = SimulationReport.of(ruleset, options.firstSeed(), bots, games);
            ReportFiles.Written written = ReportFiles.write(options.outputDir(), ruleset, report, games);
            printOverview(report, out);
            out.println("JSON: " + written.json());
            out.println("CSV:  " + written.csv());
            return 0;
        } catch (RuntimeException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static void printOverview(SimulationReport report, PrintStream out) {
        out.printf(Locale.ROOT, "%s: %d games from seed %d, bots %s, rejections %d%n", report.ruleset(),
                report.games(), report.firstSeed(), report.botsBySeat(), report.rejections());
        out.printf(Locale.ROOT, "%-13s %8s %12s %8s%n", "city", "win rate", "avg Prestige", "median");
        for (Map.Entry<CityType, SimulationReport.CityReport> entry : report.cities().entrySet()) {
            SimulationReport.CityReport city = entry.getValue();
            out.printf(Locale.ROOT, "%-13s %8.3f %12.3f %8d%n", entry.getKey(), city.winRate(),
                    city.finalPrestige().mean(), city.finalPrestige().median());
        }
        out.printf(Locale.ROOT, "winning Prestige: mean %.3f, min %d, max %d; shared victories %.3f%n",
                report.winningPrestige().mean(), report.winningPrestige().min(), report.winningPrestige().max(),
                report.sharedVictoryRate());
    }
}
