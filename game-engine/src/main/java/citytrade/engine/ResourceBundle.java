package citytrade.engine;

/** Amounts of each resource plus Money: used for costs, production and project needs. */
public record ResourceBundle(int food, int energy, int materials, int technology, int money) {

    public static final ResourceBundle EMPTY = new ResourceBundle(0, 0, 0, 0, 0);

    public int amountOf(Resource resource) {
        return switch (resource) {
            case FOOD -> food;
            case ENERGY -> energy;
            case MATERIALS -> materials;
            case TECHNOLOGY -> technology;
        };
    }

    /** The same bundle with {@code resource} set to {@code amount}. */
    public ResourceBundle with(Resource resource, int amount) {
        return switch (resource) {
            case FOOD -> new ResourceBundle(amount, energy, materials, technology, money);
            case ENERGY -> new ResourceBundle(food, amount, materials, technology, money);
            case MATERIALS -> new ResourceBundle(food, energy, amount, technology, money);
            case TECHNOLOGY -> new ResourceBundle(food, energy, materials, amount, money);
        };
    }

    public boolean isEmpty() {
        return equals(EMPTY);
    }

    public boolean hasNegativeAmount() {
        return food < 0 || energy < 0 || materials < 0 || technology < 0 || money < 0;
    }

    /** True if every amount here is at least the matching amount in {@code other}. */
    public boolean covers(ResourceBundle other) {
        return food >= other.food && energy >= other.energy && materials >= other.materials
                && technology >= other.technology && money >= other.money;
    }

    /** Each amount lowered to the matching amount in {@code limit}: the part of this bundle {@code limit} can pay. */
    public ResourceBundle cappedBy(ResourceBundle limit) {
        return new ResourceBundle(Math.min(food, limit.food), Math.min(energy, limit.energy),
                Math.min(materials, limit.materials), Math.min(technology, limit.technology),
                Math.min(money, limit.money));
    }

    /** Units of F, E, M and T together; Money is not counted. Throws on int overflow instead of wrapping. */
    public int resourceUnits() {
        return Math.addExact(Math.addExact(food, energy), Math.addExact(materials, technology));
    }

    public ResourceBundle withMoney(int amount) {
        return new ResourceBundle(food, energy, materials, technology, amount);
    }

    /** Throws on int overflow: a wrapped amount would silently move resources the wrong way. */
    public ResourceBundle plus(ResourceBundle other) {
        return new ResourceBundle(Math.addExact(food, other.food), Math.addExact(energy, other.energy),
                Math.addExact(materials, other.materials), Math.addExact(technology, other.technology),
                Math.addExact(money, other.money));
    }

    /** Throws on int overflow, like {@link #plus}. */
    public ResourceBundle minus(ResourceBundle other) {
        return new ResourceBundle(Math.subtractExact(food, other.food), Math.subtractExact(energy, other.energy),
                Math.subtractExact(materials, other.materials), Math.subtractExact(technology, other.technology),
                Math.subtractExact(money, other.money));
    }
}
