package citytrade.engine.state;

/** D2: what a city does when the warned crisis hits it. */
public enum CrisisPolicy {
    /** Pay the crisis in full if possible, otherwise become Strained. The default. */
    PAY,
    /** Do not pay; become Strained. */
    SKIP
}
