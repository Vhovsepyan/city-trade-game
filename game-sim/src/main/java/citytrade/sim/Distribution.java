package citytrade.sim;

import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Summary of whole-number values such as final Prestige. Percentiles use the nearest-rank method, so every
 * value is one that really occurred.
 *
 * @param histogram value -> how often it occurred, in value order
 */
public record Distribution(int count, double mean, int min, int p25, int median, int p75, int max,
        SortedMap<Integer, Integer> histogram) {

    public Distribution {
        histogram = Collections.unmodifiableSortedMap(new TreeMap<>(histogram));
    }

    public static Distribution of(List<Integer> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("a distribution needs at least one value");
        }
        List<Integer> sorted = values.stream().sorted().toList();
        TreeMap<Integer, Integer> histogram = new TreeMap<>();
        long sum = 0;
        for (int value : sorted) {
            histogram.merge(value, 1, Integer::sum);
            sum += value;
        }
        return new Distribution(sorted.size(), Averages.round((double) sum / sorted.size()), sorted.getFirst(),
                nearestRank(sorted, 25), nearestRank(sorted, 50), nearestRank(sorted, 75), sorted.getLast(),
                histogram);
    }

    private static int nearestRank(List<Integer> sorted, int percent) {
        int rank = (int) Math.ceil(percent / 100.0 * sorted.size());
        return sorted.get(Math.max(rank, 1) - 1);
    }
}
