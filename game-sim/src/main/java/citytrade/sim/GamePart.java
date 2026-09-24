package citytrade.sim;

/**
 * Early / mid / late part of a game, for metrics only (not a game rule). The rounds are split into three
 * nearly equal parts; with 14 rounds: EARLY 1-5, MID 6-10, LATE 11-14.
 */
public enum GamePart {
    EARLY,
    MID,
    LATE;

    public static GamePart of(int round, int roundCount) {
        if (round < 1 || round > roundCount) {
            throw new IllegalArgumentException("round " + round + " is not in 1.." + roundCount);
        }
        return values()[(round - 1) * values().length / roundCount];
    }
}
