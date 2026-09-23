package citytrade.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResourceBundleTest {

    @Test
    void amountOfReturnsTheMatchingField() {
        ResourceBundle bundle = new ResourceBundle(1, 2, 3, 4, 5);
        assertEquals(1, bundle.amountOf(Resource.FOOD));
        assertEquals(2, bundle.amountOf(Resource.ENERGY));
        assertEquals(3, bundle.amountOf(Resource.MATERIALS));
        assertEquals(4, bundle.amountOf(Resource.TECHNOLOGY));
    }
}
