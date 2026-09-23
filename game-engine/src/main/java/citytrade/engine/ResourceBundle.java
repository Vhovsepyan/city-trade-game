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

    public ResourceBundle withMoney(int amount) {
        return new ResourceBundle(food, energy, materials, technology, amount);
    }

    public ResourceBundle plus(ResourceBundle other) {
        return new ResourceBundle(food + other.food, energy + other.energy, materials + other.materials,
                technology + other.technology, money + other.money);
    }

    public ResourceBundle minus(ResourceBundle other) {
        return new ResourceBundle(food - other.food, energy - other.energy, materials - other.materials,
                technology - other.technology, money - other.money);
    }
}
