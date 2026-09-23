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

    @Test
    void withReplacesOnlyTheGivenResource() {
        ResourceBundle bundle = new ResourceBundle(1, 2, 3, 4, 5);
        assertEquals(new ResourceBundle(9, 2, 3, 4, 5), bundle.with(Resource.FOOD, 9));
        assertEquals(new ResourceBundle(1, 9, 3, 4, 5), bundle.with(Resource.ENERGY, 9));
        assertEquals(new ResourceBundle(1, 2, 9, 4, 5), bundle.with(Resource.MATERIALS, 9));
        assertEquals(new ResourceBundle(1, 2, 3, 9, 5), bundle.with(Resource.TECHNOLOGY, 9));
        assertEquals(new ResourceBundle(1, 2, 3, 4, 9), bundle.withMoney(9));
    }

    @Test
    void plusAndMinusWorkOnEveryField() {
        ResourceBundle a = new ResourceBundle(5, 6, 7, 8, 9);
        ResourceBundle b = new ResourceBundle(1, 2, 3, 4, 5);
        assertEquals(new ResourceBundle(6, 8, 10, 12, 14), a.plus(b));
        assertEquals(new ResourceBundle(4, 4, 4, 4, 4), a.minus(b));
    }
}
