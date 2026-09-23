package citytrade.engine;

/** The four fixed Version 1 cities (Concept 5). Each is strong at one specialty resource. */
public enum CityType {
    AGRICULTURAL(Resource.FOOD),
    INDUSTRIAL(Resource.MATERIALS),
    ENERGY(Resource.ENERGY),
    TECHNOLOGY(Resource.TECHNOLOGY);

    private final Resource specialty;

    CityType(Resource specialty) {
        this.specialty = specialty;
    }

    public Resource specialty() {
        return specialty;
    }
}
