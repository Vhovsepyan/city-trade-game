package citytrade.sim;

import citytrade.engine.Resource;
import citytrade.engine.ResourceBundle;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/** Small helpers for report averages. Values are rounded to 3 decimals so the output stays readable. */
final class Averages {

    private Averages() {
    }

    static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    static double ratio(double total, int count) {
        return count == 0 ? 0.0 : round(total / count);
    }

    static <T> double mean(Collection<T> items, ToDoubleFunction<T> value) {
        return ratio(items.stream().mapToDouble(value).sum(), items.size());
    }

    /** The average of {@code bundle} per resource (F, E, M, T; Money is left out) over {@code count}. */
    static <T> Map<Resource, Double> perResource(Collection<T> items, Function<T, ResourceBundle> bundle, int count) {
        Map<Resource, Double> averages = new EnumMap<>(Resource.class);
        for (Resource resource : Resource.values()) {
            double total = items.stream().mapToDouble(item -> bundle.apply(item).amountOf(resource)).sum();
            averages.put(resource, ratio(total, count));
        }
        return averages;
    }
}
