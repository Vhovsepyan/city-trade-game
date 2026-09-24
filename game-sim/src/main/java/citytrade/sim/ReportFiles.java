package citytrade.sim;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import citytrade.engine.ruleset.LevelRules;
import citytrade.engine.ruleset.Ruleset;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Output of a run: the summary as JSON and one CSV row per city per game (raw data for spreadsheets).
 * No time stamps or other run-dependent values, so the same run always writes the same files.
 */
public final class ReportFiles {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .build();

    private ReportFiles() {
    }

    /** The paths of the written files. */
    public record Written(Path json, Path csv) {
    }

    /** Base file name, e.g. {@code prototype-001_baseline-baseline-trader-trader_seed1_games1000}. */
    public static String baseName(SimulationReport report) {
        return report.ruleset() + "_" + String.join("-", report.botsBySeat()) + "_seed" + report.firstSeed()
                + "_games" + report.games();
    }

    public static Written write(Path directory, Ruleset ruleset, SimulationReport report, List<GameRecord> games) {
        String base = baseName(report);
        Path json = directory.resolve(base + ".json");
        Path csv = directory.resolve(base + ".csv");
        try {
            Files.createDirectories(directory);
            Files.writeString(json, toJson(report), StandardCharsets.UTF_8);
            Files.writeString(csv, toCsv(ruleset, games), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write simulation output to " + directory, e);
        }
        return new Written(json, csv);
    }

    public static String toJson(SimulationReport report) {
        try {
            return JSON.writeValueAsString(report) + "\n";
        } catch (JacksonException e) {
            throw new IllegalStateException("cannot write the report as JSON", e);
        }
    }

    public static String toCsv(Ruleset ruleset, List<GameRecord> games) {
        List<Integer> levels = levelsAboveStart(ruleset);
        List<String> header = new ArrayList<>(List.of("seed", "seat", "city", "bot", "final_prestige",
                "visible_prestige", "hidden_prestige", "completed_objectives", "winner", "winners", "final_level"));
        levels.forEach(level -> header.add("level" + level + "_round"));
        header.addAll(List.of("strained_rounds", "contracts_broken"));
        header.addAll(resourceColumns("produced", true));
        header.addAll(resourceColumns("discarded", false));
        header.addAll(List.of("trades", "resources_traded_away", "resources_traded_in", "market_units_bought",
                "market_money_spent", "market_units_sold", "market_money_received", "game_rejections"));

        StringBuilder csv = new StringBuilder(String.join(",", header)).append('\n');
        for (GameRecord game : games) {
            for (GameRecord.Seat seat : game.seats()) {
                List<Object> row = new ArrayList<>(List.of(game.seed(), seat.seat(), seat.city(), seat.bot().cliName(),
                        seat.finalPrestige(), seat.visiblePrestige(), seat.hiddenPrestige(),
                        seat.completedObjectives(), game.winnerSeats().contains(seat.seat()),
                        game.winnerSeats().size(), seat.finalLevel()));
                levels.forEach(level -> row.add(seat.levelReachedRound().containsKey(level)
                        ? seat.levelReachedRound().get(level) : ""));
                row.add(seat.strainedRounds());
                row.add(seat.contractsBroken());
                row.addAll(resourceValues(seat.produced(), true));
                row.addAll(resourceValues(seat.discarded(), false));
                row.addAll(List.of(seat.tradesExecuted(), seat.tradedGiven().resourceUnits(),
                        seat.tradedReceived().resourceUnits(), seat.marketBought().resourceUnits(),
                        seat.marketMoneySpent(), seat.marketSold().resourceUnits(), seat.marketMoneyReceived(),
                        game.rejections()));
                csv.append(row.stream().map(String::valueOf).collect(Collectors.joining(","))).append('\n');
            }
        }
        return csv.toString();
    }

    private static List<Integer> levelsAboveStart(Ruleset ruleset) {
        int start = ruleset.levels().stream().mapToInt(LevelRules::level).min().orElseThrow();
        return ruleset.levels().stream().map(LevelRules::level).filter(level -> level > start).sorted().toList();
    }

    private static List<String> resourceColumns(String prefix, boolean withMoney) {
        List<String> columns = new ArrayList<>();
        for (Resource resource : Resource.values()) {
            columns.add(prefix + "_" + resource.name().toLowerCase(Locale.ROOT));
        }
        if (withMoney) {
            columns.add(prefix + "_money");
        }
        return columns;
    }

    private static List<Object> resourceValues(ResourceBundle bundle, boolean withMoney) {
        List<Object> values = new ArrayList<>();
        for (Resource resource : Resource.values()) {
            values.add(bundle.amountOf(resource));
        }
        if (withMoney) {
            values.add(bundle.money());
        }
        return values;
    }
}
