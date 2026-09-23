package citytrade.engine.state;

import citytrade.engine.Resource;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Current market price step of each resource, as an index into {@code MarketRules.steps()}.
 * Stored in an EnumMap so iteration order is always F, E, M, T.
 */
public record MarketPrices(Map<Resource, Integer> stepIndexByResource) {

    public MarketPrices {
        EnumMap<Resource, Integer> copy = new EnumMap<>(Resource.class);
        copy.putAll(stepIndexByResource);
        for (Resource resource : Resource.values()) {
            if (!copy.containsKey(resource)) {
                throw new IllegalArgumentException("missing market step for " + resource);
            }
        }
        stepIndexByResource = Collections.unmodifiableMap(copy);
    }

    public static MarketPrices allAt(int stepIndex) {
        EnumMap<Resource, Integer> steps = new EnumMap<>(Resource.class);
        for (Resource resource : Resource.values()) {
            steps.put(resource, stepIndex);
        }
        return new MarketPrices(steps);
    }

    public int stepIndexOf(Resource resource) {
        return stepIndexByResource.get(resource);
    }

    public MarketPrices withStepIndex(Resource resource, int stepIndex) {
        EnumMap<Resource, Integer> steps = new EnumMap<>(stepIndexByResource);
        steps.put(resource, stepIndex);
        return new MarketPrices(steps);
    }
}
