package citytrade.engine;

/** Amounts of each resource plus Money: used for costs, production and project needs. */
public record ResourceBundle(int food, int energy, int materials, int technology, int money) {

    public int amountOf(Resource resource) {
        return switch (resource) {
            case FOOD -> food;
            case ENERGY -> energy;
            case MATERIALS -> materials;
            case TECHNOLOGY -> technology;
        };
    }
}
