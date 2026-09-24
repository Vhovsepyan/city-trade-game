package citytrade.sim;

import citytrade.bots.Bot;
import citytrade.bots.BotGame;
import citytrade.engine.ruleset.Ruleset;
import java.util.ArrayList;
import java.util.List;

/** Plays many bot games in seed order and records their metrics. Same arguments = same records. */
public final class Simulation {

    private Simulation() {
    }

    /** Plays {@code games} games; game i uses seed {@code firstSeed + i} and {@code botsBySeat.get(seat)}. */
    public static List<GameRecord> run(Ruleset ruleset, int games, long firstSeed, List<BotProfile> botsBySeat) {
        if (botsBySeat.size() != ruleset.playerCount()) {
            throw new IllegalArgumentException(
                    "need " + ruleset.playerCount() + " bots, one per seat, but got " + botsBySeat.size());
        }
        List<GameRecord> records = new ArrayList<>(games);
        for (int i = 0; i < games; i++) {
            long seed = Math.addExact(firstSeed, i);
            List<Bot> bots = botsBySeat.stream().map(BotProfile::newBot).toList();
            GameMetricsCollector collector = new GameMetricsCollector(botsBySeat);
            BotGame.Result result = BotGame.play(seed, ruleset, bots, collector);
            records.add(collector.finish(seed, result));
        }
        return records;
    }
}
