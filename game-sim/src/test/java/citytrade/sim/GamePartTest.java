package citytrade.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GamePartTest {

    @Test
    void fourteenRoundsSplitIntoFiveFiveAndFour() {
        List<GamePart> parts = new ArrayList<>();
        for (int round = 1; round <= 14; round++) {
            parts.add(GamePart.of(round, 14));
        }

        assertEquals(List.of(GamePart.EARLY, GamePart.EARLY, GamePart.EARLY, GamePart.EARLY, GamePart.EARLY,
                GamePart.MID, GamePart.MID, GamePart.MID, GamePart.MID, GamePart.MID,
                GamePart.LATE, GamePart.LATE, GamePart.LATE, GamePart.LATE), parts);
    }

    @Test
    void roundsOutsideTheGameAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> GamePart.of(0, 14));
        assertThrows(IllegalArgumentException.class, () -> GamePart.of(15, 14));
    }
}
