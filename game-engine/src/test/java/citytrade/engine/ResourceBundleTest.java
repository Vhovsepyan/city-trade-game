package citytrade.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void cappedByTakesTheSmallerAmountOfEach() {
        ResourceBundle owed = new ResourceBundle(4, 0, 2, 1, 5);
        assertEquals(new ResourceBundle(1, 0, 2, 0, 3), owed.cappedBy(new ResourceBundle(1, 7, 9, 0, 3)));
    }

    @Test
    void resourceUnitsCountFoodEnergyMaterialsAndTechnologyButNotMoney() {
        assertEquals(10, new ResourceBundle(1, 2, 3, 4, 50).resourceUnits());
        assertEquals(0, ResourceBundle.EMPTY.resourceUnits());
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

    @Test
    void arithmeticThrowsInsteadOfWrappingAround() {
        int max = Integer.MAX_VALUE;
        ResourceBundle one = new ResourceBundle(1, 1, 1, 1, 1);
        assertThrows(ArithmeticException.class, () -> new ResourceBundle(0, 0, 0, 0, max).plus(one));
        assertThrows(ArithmeticException.class, () -> new ResourceBundle(max, 0, 0, 0, 0).plus(one));
        assertThrows(ArithmeticException.class, () -> new ResourceBundle(0, 0, 0, Integer.MIN_VALUE, 0).minus(one));
        assertThrows(ArithmeticException.class, () -> new ResourceBundle(max, 1, 0, 0, 0).resourceUnits());
        assertThrows(ArithmeticException.class, () -> new ResourceBundle(0, 0, max, 1, 0).resourceUnits());
    }

    @Test
    void isEmptyOnlyWhenEveryFieldIsZero() {
        assertTrue(ResourceBundle.EMPTY.isEmpty());
        assertFalse(new ResourceBundle(0, 0, 0, 0, 1).isEmpty());
        assertFalse(new ResourceBundle(1, 0, 0, 0, 0).isEmpty());
    }

    @Test
    void hasNegativeAmountChecksEveryField() {
        assertFalse(new ResourceBundle(0, 1, 2, 3, 4).hasNegativeAmount());
        assertTrue(new ResourceBundle(-1, 0, 0, 0, 0).hasNegativeAmount());
        assertTrue(new ResourceBundle(0, -1, 0, 0, 0).hasNegativeAmount());
        assertTrue(new ResourceBundle(0, 0, -1, 0, 0).hasNegativeAmount());
        assertTrue(new ResourceBundle(0, 0, 0, -1, 0).hasNegativeAmount());
        assertTrue(new ResourceBundle(0, 0, 0, 0, -1).hasNegativeAmount());
    }

    @Test
    void coversNeedsEveryFieldAtLeastAsLarge() {
        ResourceBundle holdings = new ResourceBundle(2, 2, 2, 2, 2);
        assertTrue(holdings.covers(holdings));
        assertTrue(holdings.covers(ResourceBundle.EMPTY));
        assertFalse(holdings.covers(new ResourceBundle(3, 0, 0, 0, 0)));
        assertFalse(holdings.covers(new ResourceBundle(0, 3, 0, 0, 0)));
        assertFalse(holdings.covers(new ResourceBundle(0, 0, 3, 0, 0)));
        assertFalse(holdings.covers(new ResourceBundle(0, 0, 0, 3, 0)));
        assertFalse(holdings.covers(new ResourceBundle(0, 0, 0, 0, 3)));
    }
}
